// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright 2026 l2go

// check-engine-go checks module boundaries and public content without importing
// the engine. Run it by filename with GOWORK=off.
package main

import (
	"bufio"
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"go/ast"
	"go/build"
	"go/parser"
	"go/token"
	"io"
	"io/fs"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"time"
	"unicode"
	"unicode/utf8"
)

const goVersion = "1.27.1"
const toolchainVersion = "go1.27.3"
const serverModule = "github.com/l2go/alatyr/engine/go"
const modulePrefix = serverModule + "/"
const protocolVersion = "v0.0.0-20261023084050-3b52c8d267a0"
const apacheSHA256 = "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30"

type modulePolicy struct {
	Path    string `json:"path"`
	Module  string `json:"module"`
	Package string `json:"package"`
	License string `json:"license"`
	Phase   string `json:"phase"`
}

type denyRule struct {
	ID     string `json:"id"`
	Length int    `json:"length"`
	SHA256 string `json:"sha256"`
}

type fuzzTarget struct {
	Package string `json:"package"`
	Name    string `json:"name"`
}

type packagePolicy struct {
	Source          string   `json:"source"`
	Imports         []string `json:"imports"`
	TestImports     []string `json:"test_imports,omitempty"`
	ExternalImports []string `json:"external_imports,omitempty"`
}

type policy struct {
	ExternalModules map[string]string `json:"external"`
	Packages        []packagePolicy   `json:"packages"`
	Version         int               `json:"version"`
	Modules         []modulePolicy    `json:"modules"`
	Denied          []denyRule        `json:"denied"`
	FuzzTargets     []fuzzTarget      `json:"fuzz_targets"`
}

var externalModules = map[string]string{
	"github.com/jackc/pgx/v5":         "v5.11.0",
	"github.com/pressly/goose/v3":     "v3.29.0",
	"github.com/jackc/pgpassfile":     "v1.0.0",
	"github.com/jackc/pgservicefile":  "v0.0.0-20240606120523-5a60cdf6a761",
	"github.com/jackc/puddle/v2":      "v2.2.3",
	"github.com/mfridman/interpolate": "v0.0.2",
	"github.com/sethvargo/go-retry":   "v0.5.0",
	"go.uber.org/multierr":            "v1.11.0",
	"golang.org/x/sync":               "v0.24.0",
	"golang.org/x/text":               "v0.43.0",
}

var moduleNames = []string{"protocol", "", "server"}
var windowsProfile = regexp.MustCompile(`(?i)[a-z]:[\\/](users|documents and settings)[\\/]`)
var fuzzName = regexp.MustCompile(`^Fuzz[A-Z0-9_][A-Za-z0-9_]*$`)

// These are already public names from the server or standard library. Private
// names belong only in the digest policy, never in this source or test fixtures.
var codecMarkers = []string{
	"gamecrypt", "blowfishkeygen", "authlogin", "nc_opcodes", "internal/nc",
	"crypto/rc4", "golang.org/x/crypto/blowfish",
}

func main() {
	root := flag.String("root", ".", "repository root")
	policyPath := flag.String("policy", "tools/ci/engine-go-policy.json", "policy relative to root")
	postgresResults := flag.String("postgres-results", "", "verify required PostgreSQL test JSON")
	fuzz := flag.Bool("fuzz", false, "run the configured codec fuzz targets")
	flag.Parse()
	if flag.NArg() != 0 {
		fmt.Fprintln(os.Stderr, "policy: unexpected arguments")
		os.Exit(1)
	}
	var err error
	if *postgresResults != "" {
		file, openErr := os.Open(*postgresResults)
		if openErr != nil {
			err = errors.New("postgres: cannot read results")
		} else {
			list, listErr := os.Open(filepath.Join(*root, "engine/go/server/postgres-tests.txt"))
			if listErr != nil {
				err = errors.New("postgres: cannot read required tests")
			} else {
				required, loadErr := loadPostgresTests(list)
				err = errors.Join(loadErr, list.Close())
				if err == nil {
					err = checkPostgresResults(file, required)
				}
			}
			err = errors.Join(err, file.Close())
		}
	} else {
		err = run(*root, *policyPath, *fuzz, os.Stdout)
	}
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func run(root, policyPath string, fuzz bool, out io.Writer) error {
	root, err := filepath.Abs(root)
	if err != nil {
		return errors.New("policy: invalid root")
	}
	root, err = filepath.EvalSymlinks(root)
	if err != nil {
		return errors.New("policy: cannot resolve root")
	}
	if !fs.ValidPath(filepath.ToSlash(policyPath)) {
		return errors.New("policy: invalid policy path")
	}
	p, err := loadPolicy(filepath.Join(root, policyPath))
	if err != nil {
		return err
	}
	if err := checkTree(root, p); err != nil {
		return err
	}
	if fuzz {
		return runFuzz(root, p, out)
	}
	fmt.Fprintln(out, "Go component structure, licenses, imports, and public content passed.")
	return nil
}

func loadPolicy(path string) (policy, error) {
	var p policy
	data, err := os.ReadFile(path)
	if err != nil {
		return p, errors.New("policy: cannot read policy")
	}
	dec := json.NewDecoder(bytes.NewReader(data))
	dec.DisallowUnknownFields()
	if err := dec.Decode(&p); err != nil {
		return p, errors.New("policy: invalid JSON")
	}
	var extra any
	if !errors.Is(dec.Decode(&extra), io.EOF) {
		return p, errors.New("policy: trailing JSON")
	}
	return p, validatePolicy(p)
}

func validatePolicy(p policy) error {
	if len(p.ExternalModules) != len(externalModules) {
		return errors.New("policy: external module set")
	}
	for path, version := range externalModules {
		if p.ExternalModules[path] != version {
			return errors.New("policy: external module pin")
		}
	}
	if p.Version != 3 || len(p.Modules) != len(moduleNames) || len(p.Denied) != 12 {
		return errors.New("policy: invalid version or required lists")
	}
	for i, name := range moduleNames {
		m := p.Modules[i]
		license := "AGPL-3.0-or-later"
		if name == "protocol" {
			license = "Apache-2.0"
		}
		path := "engine/go"
		module := serverModule
		if name != "" {
			path += "/" + name
			module += "/" + name
		}
		if m.Path != path || m.Module != module || m.Package != name ||
			m.License != license || (m.Phase != "scaffold" && m.Phase != "active") || name != "protocol" && m.Phase != "active" {
			return errors.New("policy: invalid module boundary")
		}
	}
	selectors := make(map[string]bool)
	for _, row := range p.Packages {
		if !validSelector(row.Source) || selectors[row.Source] || len(row.Imports) == 0 {
			return errors.New("policy: invalid package boundary")
		}
		for selector := range selectors {
			if matches(selector, strings.TrimSuffix(row.Source, "/**")) || matches(row.Source, strings.TrimSuffix(selector, "/**")) {
				return errors.New("policy: overlapping package boundaries")
			}
		}
		selectors[row.Source] = true
		if len(row.ExternalImports) > 0 && row.Source != "server/internal/pgstore" && row.Source != "server/internal/migrate" && row.Source != "server/internal/pgtest" {
			return errors.New("policy: external imports outside application")
		}
		for _, list := range [][]string{row.Imports, row.TestImports, row.ExternalImports} {
			seen := make(map[string]bool)
			for _, selector := range list {
				if !validSelector(selector) || seen[selector] {
					return errors.New("policy: invalid import selector")
				}
				seen[selector] = true
			}
		}
	}
	if len(p.Packages) == 0 {
		return errors.New("policy: missing package boundaries")
	}
	seen := make(map[string]bool)
	for i, d := range p.Denied {
		digest, err := hex.DecodeString(d.SHA256)
		if d.ID != fmt.Sprintf("P%02d", i+1) || d.Length < 1 || d.Length > 128 ||
			err != nil || len(digest) != sha256.Size || d.SHA256 != strings.ToLower(d.SHA256) || seen[d.SHA256] {
			return errors.New("policy: invalid or duplicate denied digest")
		}
		seen[d.SHA256] = true
	}
	if len(p.FuzzTargets) > 4 || (p.Modules[0].Phase == "active" && len(p.FuzzTargets) == 0) ||
		(p.Modules[0].Phase == "scaffold" && len(p.FuzzTargets) != 0) {
		return errors.New("policy: fuzz targets do not match protocol phase")
	}
	seen = make(map[string]bool)
	for _, target := range p.FuzzTargets {
		key := target.Package + ":" + target.Name
		if !fs.ValidPath(target.Package) || strings.Contains(target.Package, "\\") ||
			!fuzzName.MatchString(target.Name) || seen[key] {
			return errors.New("policy: invalid or duplicate fuzz target")
		}
		seen[key] = true
	}
	return nil
}

func checkTree(root string, p policy) error {
	if err := checkRuntime(root); err != nil {
		return err
	}
	// Scan paths before any command can diagnose one of them. No tool output or
	// source text is included in an error, even for invalid imports or syntax.
	for _, scope := range []string{"engine/go", "docs/engine-go"} {
		if err := scanScope(root, scope, p.Denied); err != nil {
			return err
		}
	}
	if err := checkWorkspace(root); err != nil {
		return err
	}
	expectedMods := make(map[string]bool)
	for _, m := range p.Modules {
		expectedMods[filepath.Join(root, m.Path, "go.mod")] = true
		if err := checkModule(root, m); err != nil {
			return err
		}
	}
	err := filepath.WalkDir(filepath.Join(root, "engine/go"), func(path string, entry fs.DirEntry, err error) error {
		if err != nil {
			return errors.New("I-1: cannot read module tree")
		}
		if (entry.IsDir() && entry.Name() == "vendor") ||
			(!entry.IsDir() && entry.Name() == "go.mod" && !expectedMods[path]) {
			return errors.New("I-1: unexpected nested module or vendor directory")
		}
		return nil
	})
	if err != nil {
		return err
	}
	if err := checkSources(root, p); err != nil {
		return err
	}
	for _, workspace := range []string{filepath.Join(root, "go.work"), "off"} {
		for _, m := range p.Modules {
			data, err := goCommand(filepath.Join(root, m.Path), workspace, "list", "-deps", "-test", "-json", "./...")
			if err != nil {
				return errors.New("I-3: cannot resolve dependency graph")
			}
			if err := checkDependencyJSON(data, p, graphContext{root, m.Module, workspace != "off"}); err != nil {
				return err
			}
		}
	}
	return nil
}

func scanScope(root, scope string, denied []denyRule) error {
	index := 0
	return filepath.WalkDir(filepath.Join(root, scope), func(path string, entry fs.DirEntry, err error) error {
		index++
		fail := func(rule string) error { return fmt.Errorf("I-4: entry %d violates %s", index, rule) }
		if err != nil {
			return fail("read")
		}
		rel, err := filepath.Rel(root, path)
		if err != nil {
			return fail("path")
		}
		if rule := contentRule([]byte(filepath.ToSlash(rel)), denied); rule != "" {
			return fail(rule)
		}
		if entry.Type()&os.ModeSymlink != 0 {
			return fail("symlink")
		}
		if entry.IsDir() {
			return nil
		}
		if !entry.Type().IsRegular() {
			return fail("file-type")
		}
		data, err := os.ReadFile(path)
		if err != nil {
			return fail("read")
		}
		if rule := contentRule(data, denied); rule != "" {
			return fail(rule)
		}
		if strings.HasSuffix(path, ".go") {
			file, err := parser.ParseFile(token.NewFileSet(), "source.go", data, 0)
			if err != nil {
				return fail("Go-syntax")
			}
			var violation string
			ast.Inspect(file, func(node ast.Node) bool {
				literal, ok := node.(*ast.BasicLit)
				if ok && (literal.Kind == token.STRING || literal.Kind == token.CHAR) {
					value, err := strconv.Unquote(literal.Value)
					if err != nil {
						violation = "Go-literal"
					} else if rule := contentRule([]byte(value), denied); rule != "" {
						violation = rule
					}
				}
				return violation == ""
			})
			if violation != "" {
				return fail(violation)
			}
		}
		return nil
	})
}

func contentRule(data []byte, denied []denyRule) string {
	if !utf8.Valid(data) || bytes.IndexByte(data, 0) >= 0 {
		return "text-encoding"
	}
	for _, r := range string(data) {
		if unicode.Is(unicode.Cyrillic, r) {
			return "Cyrillic"
		}
	}
	lower := asciiLower(data)
	for _, marker := range codecMarkers {
		if bytes.Contains(lower, []byte(marker)) {
			return "client-codec"
		}
	}
	for _, marker := range []string{"/users/", "~/private/", "~/igrobelka/"} {
		if bytes.Contains(lower, []byte(marker)) {
			return "local-path"
		}
	}
	if windowsProfile.Match(data) {
		return "local-path"
	}
	// Hash a window once for each length. Both token boundaries exclude ASCII
	// letters/digits; punctuation, slashes and hyphens remain valid boundaries.
	byLength := make(map[int]map[string]string)
	for _, d := range denied {
		if byLength[d.Length] == nil {
			byLength[d.Length] = make(map[string]string)
		}
		byLength[d.Length][d.SHA256] = d.ID
	}
	lengths := make([]int, 0, len(byLength))
	for length := range byLength {
		lengths = append(lengths, length)
	}
	slices.Sort(lengths)
	for _, length := range lengths {
		for i := 0; i+length <= len(lower); i++ {
			if (i > 0 && asciiAlnum(lower[i-1])) || (i+length < len(lower) && asciiAlnum(lower[i+length])) {
				continue
			}
			digest := sha256.Sum256(lower[i : i+length])
			if id := byLength[length][hex.EncodeToString(digest[:])]; id != "" {
				return id
			}
		}
	}
	return ""
}

func asciiAlnum(b byte) bool { return b >= 'a' && b <= 'z' || b >= '0' && b <= '9' }

func asciiLower(data []byte) []byte {
	result := bytes.Clone(data)
	for i, b := range result {
		if b >= 'A' && b <= 'Z' {
			result[i] += 'a' - 'A'
		}
	}
	return result
}

// checkRuntime prevents retired runtime/build entrypoints from returning.
func checkRuntime(root string) error {
	for _, path := range []string{"engine/java", "pom.xml", ".mvn", "mvnw", "mvnw.cmd", "tools/ci/bridge-integration.sh", "tools/ci/bridge-integration.sql", "engine/go/gateway/backend/java_integration_test.go", "tools/ci/coverage-summary.py", "deploy/measure", "deploy/probe"} {
		if _, err := os.Lstat(filepath.Join(root, filepath.FromSlash(path))); !errors.Is(err, os.ErrNotExist) {
			return fmt.Errorf("runtime: retired path exists or cannot be inspected: %s", path)
		}
	}
	return nil
}

func checkWorkspace(root string) error {
	data, err := goCommand(root, "off", "work", "edit", "-json", filepath.Join(root, "go.work"))
	if err != nil {
		return errors.New("I-1: cannot read workspace")
	}
	var work struct {
		Go        string
		Toolchain string
		Use       []struct{ DiskPath string }
		Replace   []json.RawMessage
	}
	if json.Unmarshal(data, &work) != nil || work.Go != goVersion || work.Toolchain != toolchainVersion ||
		len(work.Replace) != 0 || len(work.Use) != 4 {
		return errors.New("I-1: workspace boundary or version")
	}
	expected := map[string]bool{"tools/world": true, "engine/go/protocol": true, "engine/go": true, "engine/go/server": true}
	for _, use := range work.Use {
		path := filepath.ToSlash(filepath.Clean(use.DiskPath))
		if filepath.IsAbs(use.DiskPath) || !expected[path] {
			return errors.New("I-1: workspace module list")
		}
		delete(expected, path)
	}
	return nil
}

func checkModule(root string, m modulePolicy) error {
	dir := filepath.Join(root, m.Path)
	data, err := goCommand(dir, "off", "mod", "edit", "-json")
	if err != nil {
		return errors.New("I-1: cannot read module")
	}
	var mod struct {
		Module    struct{ Path string }
		Go        string
		Toolchain string
		Require   []struct {
			Path, Version string
			Indirect      bool
		}
		Replace []struct {
			Old, New struct{ Path, Version string }
		}
		Exclude []json.RawMessage
		Tool    []json.RawMessage
	}
	if json.Unmarshal(data, &mod) != nil || mod.Module.Path != m.Module || mod.Go != goVersion || (mod.Toolchain != "" && mod.Toolchain != "go"+goVersion) {
		return errors.New("I-1: module path or version")
	}
	if len(mod.Exclude)+len(mod.Tool) != 0 || m.Package != "server" && len(mod.Replace) != 0 {
		return errors.New("I-3: forbidden module dependency directive")
	}
	if m.Package == "server" {
		protocolDirect, err := applicationImports(dir, modulePrefix+"protocol")
		if err != nil {
			return err
		}
		if len(mod.Replace) != 1 || mod.Replace[0].Old.Path != serverModule || mod.Replace[0].Old.Version != "" || mod.Replace[0].New.Path != "../" || mod.Replace[0].New.Version != "" {
			return errors.New("I-3: application must replace only its parent")
		}
		expected := map[string]string{serverModule: "v0.0.0", modulePrefix + "protocol": protocolVersion}
		for path, version := range externalModules {
			expected[path] = version
		}
		if len(mod.Require) != len(expected) {
			return errors.New("I-3: application dependency set")
		}
		for _, req := range mod.Require {
			direct := req.Path == serverModule || req.Path == modulePrefix+"protocol" && protocolDirect || req.Path == "github.com/jackc/pgx/v5" || req.Path == "github.com/pressly/goose/v3"
			if expected[req.Path] != req.Version || req.Indirect == direct {
				return errors.New("I-3: unpinned application dependency")
			}
		}
	} else if m.Package == "protocol" {
		if len(mod.Require) != 0 {
			return errors.New("I-3: forbidden protocol dependency")
		}
	} else if len(mod.Require) != 1 || mod.Require[0].Path != modulePrefix+"protocol" ||
		mod.Require[0].Version != protocolVersion || mod.Require[0].Indirect {
		return errors.New("I-3: unpinned server dependency")
	}
	components := []string{""}
	if m.Package == "" {
		components = append(components, "gateway", "bridge", "world", "worldbridge")
	}
	for _, component := range components {
		componentDir := filepath.Join(dir, component)
		names := []string{"README.md", "LICENSE"}
		if m.Package != "" && m.Package != "server" || component != "" {
			names = append(names, "doc.go")
		}
		for _, name := range names {
			info, err := os.Stat(filepath.Join(componentDir, name))
			if err != nil || !info.Mode().IsRegular() || info.Size() == 0 {
				return errors.New("I-1: missing module documentation or license")
			}
		}
		license, err := os.ReadFile(filepath.Join(componentDir, "LICENSE"))
		if err != nil {
			return errors.New("I-1: cannot read license")
		}
		if m.Package == "protocol" {
			digest := sha256.Sum256(license)
			if hex.EncodeToString(digest[:]) != apacheSHA256 {
				return errors.New("I-1: Apache license text differs")
			}
		} else {
			agpl, err := os.ReadFile(filepath.Join(root, "tools/world/LICENSE"))
			if err != nil || len(agpl) == 0 || !bytes.Equal(license, agpl) {
				return errors.New("I-1: AGPL license text differs")
			}
		}
	}
	if m.Package == "protocol" && m.Phase == "scaffold" {
		entries, err := os.ReadDir(dir)
		if err != nil || len(entries) != 4 {
			return errors.New("I-2: scaffold contains extra files")
		}
		for _, entry := range entries {
			if entry.IsDir() || !slices.Contains([]string{"doc.go", "README.md", "LICENSE", "go.mod"}, entry.Name()) {
				return errors.New("I-2: scaffold contains extra files")
			}
		}
		data, err := os.ReadFile(filepath.Join(dir, "doc.go"))
		if err != nil {
			return errors.New("I-2: cannot read scaffold documentation")
		}
		file, err := parser.ParseFile(token.NewFileSet(), "doc.go", data, parser.ParseComments)
		if err != nil || file.Name.Name != m.Package || len(file.Decls) != 0 || len(file.Imports) != 0 {
			return errors.New("I-2: scaffold must contain only a package and comments")
		}
	}
	return filepath.WalkDir(dir, func(path string, entry fs.DirEntry, err error) error {
		if err != nil {
			return errors.New("I-1: cannot read module source")
		}
		if entry.IsDir() && path != dir && m.Package == "" && path == filepath.Join(dir, "protocol") {
			return filepath.SkipDir
		}
		if !entry.IsDir() && strings.HasSuffix(path, ".go") {
			data, err := os.ReadFile(path)
			prefix := "// SPDX-License-Identifier: " + m.License + "\n// Copyright 2026 l2go\n\n"
			if err != nil || !bytes.HasPrefix(data, []byte(prefix)) {
				return errors.New("I-1: missing source license header")
			}
		}
		return nil
	})
}

func applicationImports(dir, module string) (bool, error) {
	direct := false
	err := filepath.WalkDir(dir, func(path string, entry fs.DirEntry, err error) error {
		if err != nil {
			return errors.New("I-3: cannot read application imports")
		}
		if entry.IsDir() || !strings.HasSuffix(path, ".go") {
			return nil
		}
		file, err := parser.ParseFile(token.NewFileSet(), path, nil, parser.ImportsOnly)
		if err != nil {
			return errors.New("I-3: invalid application imports")
		}
		for _, imp := range file.Imports {
			value, err := strconv.Unquote(imp.Path.Value)
			if err != nil {
				return errors.New("I-3: invalid application import")
			}
			if matches(module+"/**", value) {
				direct = true
			}
		}
		return nil
	})
	return direct, err
}

// Selectors name exact package directories, or a root and all descendants.
func validSelector(s string) bool {
	path := strings.TrimSuffix(s, "/**")
	return fs.ValidPath(path) && path != "." && !strings.ContainsAny(path, `\*`)
}

func matches(selector, path string) bool {
	root := strings.TrimSuffix(selector, "/**")
	return path == root || strings.HasSuffix(selector, "/**") && strings.HasPrefix(path, root+"/")
}

func packageRule(p policy, path string) *packagePolicy {
	for i := range p.Packages {
		if matches(p.Packages[i].Source, path) {
			return &p.Packages[i]
		}
	}
	return nil
}

func allowedEdge(p policy, from, to string, test bool) bool {
	row := packageRule(p, from)
	if row == nil || packageRule(p, to) == nil {
		return false
	}
	for _, selector := range row.Imports {
		if matches(selector, to) {
			return true
		}
	}
	if test {
		for _, selector := range row.TestImports {
			if matches(selector, to) {
				return true
			}
		}
	}
	return false
}

func externalImport(p policy, from, to string) bool {
	row := packageRule(p, from)
	if row == nil {
		return false
	}
	for _, selector := range row.ExternalImports {
		if matches(selector, to) {
			for module := range p.ExternalModules {
				if matches(module+"/**", to) {
					return true
				}
			}
		}
	}
	return false
}

func standardImport(path, dir string) bool {
	if path == "C" || !fs.ValidPath(path) || strings.Contains(path, `\`) {
		return false
	}
	pkg, err := build.Default.Import(path, dir, build.FindOnly)
	return err == nil && pkg.Goroot && !strings.HasPrefix(path, "vendor/") &&
		!strings.Contains(filepath.ToSlash(pkg.Dir), "/src/vendor/")
}

// checkSources reads every Go file, including tests and inactive build tags.
func checkSources(root string, p policy) error {
	dir := filepath.Join(root, "engine/go")
	return filepath.WalkDir(dir, func(path string, entry fs.DirEntry, err error) error {
		if err != nil {
			return errors.New("I-3: cannot read package source")
		}
		if entry.IsDir() || !strings.HasSuffix(path, ".go") {
			return nil
		}
		rel, err := filepath.Rel(dir, filepath.Dir(path))
		if err != nil {
			return errors.New("I-3: invalid package directory")
		}
		from := filepath.ToSlash(rel)
		if packageRule(p, from) == nil {
			return errors.New("I-3: unknown package directory")
		}
		if strings.HasPrefix(from, "server/") {
			if info, err := os.Stat(filepath.Join(filepath.Dir(path), "doc.go")); err != nil || !info.Mode().IsRegular() || info.Size() == 0 {
				return errors.New("I-1: missing application package documentation")
			}
		}
		file, err := parser.ParseFile(token.NewFileSet(), path, nil, 0)
		if err != nil {
			return errors.New("I-3: invalid package source")
		}
		if err := checkMigrationAPI(file); err != nil {
			return err
		}
		for _, imp := range file.Imports {
			to, err := strconv.Unquote(imp.Path.Value)
			if err != nil {
				return errors.New("I-3: invalid import")
			}
			if strings.HasPrefix(to, modulePrefix) && fs.ValidPath(to) && !strings.Contains(to, `\`) {
				if allowedEdge(p, from, strings.TrimPrefix(to, modulePrefix), strings.HasSuffix(path, "_test.go")) {
					continue
				}
			} else if externalImport(p, from, to) {
				continue
			} else if standardImport(to, dir) {
				continue
			}
			return errors.New("I-3: import outside package boundary")
		}
		return nil
	})
}

// checkMigrationAPI keeps goose on its PostgreSQL Provider API. Global helpers,
// driver registries and dot imports would bypass per-provider configuration.
func checkMigrationAPI(file *ast.File) error {
	alias := ""
	for _, imp := range file.Imports {
		path, err := strconv.Unquote(imp.Path.Value)
		if err != nil {
			return errors.New("I-3: invalid migration import")
		}
		if strings.Contains(path, ".") && imp.Name != nil && imp.Name.Name == "." {
			return errors.New("I-3: external dot import")
		}
		if path == "github.com/pressly/goose/v3" {
			alias = "goose"
			if imp.Name != nil {
				alias = imp.Name.Name
			}
			if alias == "_" {
				return errors.New("I-3: goose side-effect import")
			}
		}
	}
	if alias == "" {
		return nil
	}
	var failure error
	allowed := map[string]bool{"NewProvider": true, "DialectPostgres": true, "WithTableName": true, "WithDisableGlobalRegistry": true, "WithSlog": true}
	ast.Inspect(file, func(node ast.Node) bool {
		if selector, ok := node.(*ast.SelectorExpr); ok {
			if id, ok := selector.X.(*ast.Ident); ok && id.Name == alias && !allowed[selector.Sel.Name] {
				failure = errors.New("I-3: goose API outside PostgreSQL provider")
			}
		}
		call, ok := node.(*ast.CallExpr)
		if !ok {
			return true
		}
		selector, ok := call.Fun.(*ast.SelectorExpr)
		if !ok {
			return true
		}
		id, ok := selector.X.(*ast.Ident)
		if !ok || id.Name != alias || selector.Sel.Name != "NewProvider" {
			return true
		}
		postgres := false
		registry := false
		if len(call.Args) > 0 {
			if dialect, ok := call.Args[0].(*ast.SelectorExpr); ok {
				if id, ok := dialect.X.(*ast.Ident); ok && id.Name == alias && dialect.Sel.Name == "DialectPostgres" {
					postgres = true
				}
			}
		}
		for _, arg := range call.Args {
			option, ok := arg.(*ast.CallExpr)
			if !ok || len(option.Args) != 1 {
				continue
			}
			f, ok := option.Fun.(*ast.SelectorExpr)
			if !ok || f.Sel.Name != "WithDisableGlobalRegistry" {
				continue
			}
			id, ok := f.X.(*ast.Ident)
			if !ok || id.Name != alias {
				continue
			}
			value, ok := option.Args[0].(*ast.Ident)
			if ok && value.Name == "true" {
				registry = true
			}
		}
		if !postgres || !registry {
			failure = errors.New("I-3: goose provider must use PostgreSQL without globals")
		}
		return true
	})
	return failure
}

type listedPackage struct {
	Standard                           bool
	Dir, ImportPath                    string
	Imports, TestImports, XTestImports []string
	Error                              json.RawMessage   `json:",omitempty"`
	DepsErrors                         []json.RawMessage `json:",omitempty"`
	Module                             *struct {
		Path, Version, Dir string
		Main               bool
		Replace            json.RawMessage `json:",omitempty"`
	}
}

// packagePath removes only go list's generated test identities.
func packagePath(path string) string {
	base, _, variant := strings.Cut(path, " [")
	if variant {
		return strings.TrimSuffix(base, "_test")
	}
	return strings.TrimSuffix(base, ".test")
}

type graphContext struct {
	Root, Main string
	Workspace  bool
}

func checkDependencyJSON(data []byte, p policy, modes ...graphContext) error {
	dec := json.NewDecoder(bytes.NewReader(data))
	var packages []listedPackage
	standard := make(map[string]bool)
	for {
		var pkg listedPackage
		err := dec.Decode(&pkg)
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil || len(pkg.Error)+len(pkg.DepsErrors) != 0 {
			return errors.New("I-3: invalid dependency graph")
		}
		packages = append(packages, pkg)
		if pkg.Standard {
			standard[pkg.ImportPath] = true
		}
	}
	if len(packages) == 0 {
		return errors.New("I-3: empty dependency graph")
	}
	for _, pkg := range packages {
		if pkg.Standard {
			continue
		}
		path := packagePath(pkg.ImportPath)
		rel, ok := strings.CutPrefix(path, modulePrefix)
		if !ok {
			if pkg.Module == nil || p.ExternalModules[pkg.Module.Path] != pkg.Module.Version || len(pkg.Module.Replace) != 0 || !matches(pkg.Module.Path+"/**", path) || pkg.Module.Dir == "" || filepath.Clean(pkg.Dir) != filepath.Join(pkg.Module.Dir, filepath.FromSlash(strings.TrimPrefix(path, pkg.Module.Path))) {
				return errors.New("I-3: unexpected external provider")
			}
			continue
		}
		if packageRule(p, rel) == nil || pkg.Module == nil {
			return errors.New("I-3: unknown or substituted dependency")
		}
		module := serverModule
		if matches("server/**", rel) {
			module += "/server"
		}
		if matches("protocol/**", rel) {
			module += "/protocol"
		}
		if pkg.Module.Path != module {
			return errors.New("I-3: unexpected package provider")
		}
		if len(modes) == 1 {
			mode := modes[0]
			local := module == serverModule || module == modulePrefix+"server" || mode.Workspace || mode.Main == module
			if local {
				expected := filepath.Join(mode.Root, "engine/go")
				if module != serverModule {
					expected = filepath.Join(expected, strings.TrimPrefix(module, modulePrefix))
				}
				if filepath.Clean(pkg.Module.Dir) != filepath.Clean(expected) {
					return errors.New("I-3: package provider outside checkout")
				}
			}
			if mode.Workspace {
				if len(pkg.Module.Replace) != 0 || pkg.Module.Version != "" {
					return errors.New("I-3: substituted workspace provider")
				}
			} else if mode.Main == modulePrefix+"server" && module == serverModule {
				var replacement struct{ Path, Dir string }
				if pkg.Module.Version != "v0.0.0" || json.Unmarshal(pkg.Module.Replace, &replacement) != nil || replacement.Path != "../" || filepath.Clean(replacement.Dir) != filepath.Join(mode.Root, "engine/go") {
					return errors.New("I-3: application parent replacement")
				}
			} else if len(pkg.Module.Replace) != 0 || module == mode.Main && pkg.Module.Version != "" {
				return errors.New("I-3: substituted package provider")
			}
		} else if len(pkg.Module.Replace) != 0 {
			return errors.New("I-3: substituted dependency")
		}
		suffix := strings.TrimPrefix(path, module)
		if pkg.Module.Dir == "" || filepath.Clean(pkg.Dir) != filepath.Join(pkg.Module.Dir, filepath.FromSlash(suffix)) {
			return errors.New("I-3: package directory outside provider")
		}
		from := rel
		for _, list := range []struct {
			imports []string
			test    bool
		}{
			{pkg.Imports, pkg.ImportPath != path}, {pkg.TestImports, true}, {pkg.XTestImports, true},
		} {
			for _, imp := range list.imports {
				if standard[imp] || standardImport(imp, pkg.Dir) {
					continue
				}
				to := strings.TrimPrefix(packagePath(imp), modulePrefix)
				if !allowedEdge(p, from, to, list.test) && !externalImport(p, from, packagePath(imp)) {
					return errors.New("I-3: graph import outside package boundary")
				}
			}
		}
	}
	return nil
}

func runFuzz(root string, p policy, out io.Writer) error {
	if p.Modules[0].Phase == "scaffold" {
		fmt.Fprintln(out, "Protocol scaffold: no fuzz targets; codec fuzzing is not active.")
		return nil
	}
	dir := filepath.Join(root, "engine/go/protocol")
	// Discover every target before running any. A missing target cannot silently
	// turn a supposedly active fuzz gate into a successful no-op.
	for _, target := range p.FuzzTargets {
		if err := findFuzzTarget(dir, target); err != nil {
			return err
		}
	}
	for _, target := range p.FuzzTargets {
		_, err := goCommand(dir, "off", "test", "-run=^$", "-fuzz=^"+regexp.QuoteMeta(target.Name)+"$",
			"-fuzztime=10s", "-parallel=1", "-timeout=2m", "./"+target.Package)
		if err != nil {
			return errors.New("fuzz: target failed")
		}
		fmt.Fprintf(out, "Fuzz target %s/%s passed.\n", target.Package, target.Name)
	}
	return nil
}

func findFuzzTarget(dir string, target fuzzTarget) error {
	data, err := goCommand(dir, "off", "test", "-run=^$", "-list=^Fuzz", "-timeout=2m", "./"+target.Package)
	if err != nil {
		return errors.New("fuzz: cannot list targets")
	}
	for _, line := range strings.Split(string(data), "\n") {
		if strings.TrimSpace(line) == target.Name {
			return nil
		}
	}
	return errors.New("fuzz: configured target does not exist")
}

func goCommand(dir, workspace string, args ...string) ([]byte, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()
	cmd := exec.CommandContext(ctx, "go", args...)
	cmd.Dir = dir
	cmd.Env = replaceEnv(os.Environ(), map[string]string{
		"GOWORK": workspace, "GOTOOLCHAIN": "local", "GOMAXPROCS": "2",
	})
	// Keep stderr separate, and never print it: source paths, import strings and
	// compiler snippets can themselves be the private material being rejected.
	return cmd.Output()
}

func replaceEnv(env []string, values map[string]string) []string {
	result := make([]string, 0, len(env)+len(values))
	for _, entry := range env {
		key, _, _ := strings.Cut(entry, "=")
		if _, replaced := values[key]; !replaced {
			result = append(result, entry)
		}
	}
	for key, value := range values {
		result = append(result, key+"="+value)
	}
	return result
}

type postgresTest struct{ Package, Test string }

func loadPostgresTests(reader io.Reader) ([]postgresTest, error) {
	scanner := bufio.NewScanner(reader)
	var required []postgresTest
	seen := make(map[postgresTest]bool)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		fields := strings.Fields(line)
		if len(fields) != 2 || !strings.HasPrefix(fields[0], modulePrefix+"server/") || !fs.ValidPath(fields[0]) || !regexp.MustCompile(`^Test[A-Z0-9_][A-Za-z0-9_]*$`).MatchString(fields[1]) {
			return nil, errors.New("postgres: invalid required test")
		}
		test := postgresTest{fields[0], fields[1]}
		if seen[test] {
			return nil, errors.New("postgres: duplicate required test")
		}
		seen[test] = true
		required = append(required, test)
	}
	if scanner.Err() != nil || len(required) == 0 {
		return nil, errors.New("postgres: empty or unreadable required tests")
	}
	return required, nil
}

func checkPostgresResults(reader io.Reader, required []postgresTest) error {
	if len(required) == 0 {
		return errors.New("postgres: empty required tests")
	}
	dec := json.NewDecoder(reader)
	passed := make(map[string]int)
	started := make(map[string]int)
	for {
		var event struct{ Action, Package, Test string }
		err := dec.Decode(&event)
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			return errors.New("postgres: invalid test JSON")
		}
		if event.Action == "fail" {
			return errors.New("postgres: test failed")
		}
		for _, required := range required {
			if event.Package != required.Package || event.Test != required.Test {
				continue
			}
			key := event.Package + "/" + event.Test
			switch event.Action {
			case "skip":
				return errors.New("postgres: required test skipped")
			case "run":
				started[key]++
			case "pass":
				passed[key]++
			}
		}
	}
	for _, required := range required {
		key := required.Package + "/" + required.Test
		if started[key] != 1 || passed[key] != 1 {
			return errors.New("postgres: required tests did not execute exactly once")
		}
	}
	return nil
}
