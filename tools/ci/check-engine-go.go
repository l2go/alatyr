// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright 2026 l2go

// check-engine-go checks module boundaries and public content without importing
// the engine. Run it by filename with GOWORK=off.
package main

import (
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
const modulePrefix = "github.com/l2go/alatyr/engine/go/"
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

type policy struct {
	Version     int            `json:"version"`
	Modules     []modulePolicy `json:"modules"`
	Denied      []denyRule     `json:"denied"`
	FuzzTargets []fuzzTarget   `json:"fuzz_targets"`
}

var moduleNames = []string{"protocol", "gateway", "bridge", "world"}
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
	fuzz := flag.Bool("fuzz", false, "run the configured codec fuzz targets")
	flag.Parse()
	if flag.NArg() != 0 {
		fmt.Fprintln(os.Stderr, "policy: unexpected arguments")
		os.Exit(1)
	}
	err := run(*root, *policyPath, *fuzz, os.Stdout)
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
	if p.Version != 1 || len(p.Modules) != len(moduleNames) || len(p.Denied) != 12 {
		return errors.New("policy: invalid version or required lists")
	}
	for i, name := range moduleNames {
		m := p.Modules[i]
		license := "AGPL-3.0-or-later"
		if name == "protocol" {
			license = "Apache-2.0"
		}
		if m.Path != "engine/go/"+name || m.Module != modulePrefix+name || m.Package != name ||
			m.License != license || (m.Phase != "scaffold" && m.Phase != "active") {
			return errors.New("policy: invalid module boundary")
		}
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
	for _, m := range p.Modules {
		if m.Package == "protocol" || m.Package == "world" {
			if err := checkProtocolDeps(root, m); err != nil {
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
	if json.Unmarshal(data, &work) != nil || work.Go != goVersion || work.Toolchain != "go"+goVersion ||
		len(work.Replace) != 0 || len(work.Use) != len(moduleNames)+1 {
		return errors.New("I-1: workspace boundary or version")
	}
	expected := map[string]bool{"tools/world": true, "engine/go/protocol": true, "engine/go/gateway": true, "engine/go/bridge": true, "engine/go/world": true}
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
		Require   []json.RawMessage
		Replace   []json.RawMessage
		Exclude   []json.RawMessage
		Tool      []json.RawMessage
	}
	if json.Unmarshal(data, &mod) != nil || mod.Module.Path != m.Module || mod.Go != goVersion || (mod.Toolchain != "" && mod.Toolchain != "go"+goVersion) {
		return errors.New("I-1: module path or version")
	}
	if (m.Package == "protocol" || m.Package == "world" || m.Phase == "scaffold") &&
		len(mod.Require)+len(mod.Replace)+len(mod.Exclude)+len(mod.Tool) != 0 {
		return errors.New("I-3: forbidden module dependency directive")
	}
	for _, name := range []string{"doc.go", "README.md", "LICENSE"} {
		info, err := os.Stat(filepath.Join(dir, name))
		if err != nil || !info.Mode().IsRegular() || info.Size() == 0 {
			return errors.New("I-1: missing module documentation or license")
		}
	}
	license, _ := os.ReadFile(filepath.Join(dir, "LICENSE"))
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
	if m.Phase == "scaffold" {
		entries, err := os.ReadDir(dir)
		if err != nil || len(entries) != 4 {
			return errors.New("I-2: scaffold contains extra files")
		}
		for _, entry := range entries {
			if entry.IsDir() || !slices.Contains([]string{"doc.go", "README.md", "LICENSE", "go.mod"}, entry.Name()) {
				return errors.New("I-2: scaffold contains extra files")
			}
		}
		data, _ := os.ReadFile(filepath.Join(dir, "doc.go"))
		file, err := parser.ParseFile(token.NewFileSet(), "doc.go", data, parser.ParseComments)
		if err != nil || file.Name.Name != m.Package || len(file.Decls) != 0 || len(file.Imports) != 0 {
			return errors.New("I-2: scaffold must contain only a package and comments")
		}
	}
	return filepath.WalkDir(dir, func(path string, entry fs.DirEntry, err error) error {
		if err != nil {
			return errors.New("I-1: cannot read module source")
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

func checkProtocolDeps(root string, m modulePolicy) error {
	dir := filepath.Join(root, m.Path)
	err := filepath.WalkDir(dir, func(path string, entry fs.DirEntry, err error) error {
		if err != nil {
			return errors.New("I-3: cannot read stdlib-only module source")
		}
		if entry.IsDir() || !strings.HasSuffix(path, ".go") {
			return nil
		}
		data, err := os.ReadFile(path)
		if err != nil {
			return errors.New("I-3: cannot read stdlib-only module source")
		}
		file, err := parser.ParseFile(token.NewFileSet(), "source.go", data, parser.ImportsOnly)
		if err != nil {
			return errors.New("I-3: invalid stdlib-only module source")
		}
		for _, imp := range file.Imports {
			path, err := strconv.Unquote(imp.Path.Value)
			if err != nil || !allowedImport(path, m.Module, dir) {
				return errors.New("I-3: stdlib-only module imports outside its own packages and standard library")
			}
		}
		return nil
	})
	if err != nil {
		return err
	}
	data, err := goCommand(dir, filepath.Join(root, "go.work"), "list", "-deps", "-test", "-json", "./...")
	if err != nil {
		return errors.New("I-3: cannot resolve stdlib-only module dependency graph")
	}
	return checkDependencyJSON(data, m.Module, dir)
}

func allowedImport(path, module, dir string) bool {
	if path == module || strings.HasPrefix(path, module+"/") {
		return fs.ValidPath(path) && !strings.Contains(path, "\\")
	}
	if path == "C" || !fs.ValidPath(path) || strings.Contains(path, "\\") {
		return false
	}
	pkg, err := build.Default.Import(path, dir, build.FindOnly)
	return err == nil && pkg.Goroot && !strings.HasPrefix(path, "vendor/") &&
		!strings.Contains(filepath.ToSlash(pkg.Dir), "/src/vendor/")
}

func checkDependencyJSON(data []byte, module, dir string) error {
	dec := json.NewDecoder(bytes.NewReader(data))
	count := 0
	for {
		var pkg struct {
			Standard   bool
			Dir        string
			ImportPath string
			Error      json.RawMessage
			DepsErrors []json.RawMessage
			Module     *struct {
				Path    string
				Replace json.RawMessage
			}
		}
		err := dec.Decode(&pkg)
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil || len(pkg.Error) > 0 || len(pkg.DepsErrors) > 0 {
			return errors.New("I-3: invalid dependency graph")
		}
		count++
		if pkg.Standard {
			continue
		}
		importPath, _, _ := strings.Cut(pkg.ImportPath, " [")
		rel, err := filepath.Rel(dir, pkg.Dir)
		if pkg.Module == nil || pkg.Module.Path != module || len(pkg.Module.Replace) > 0 ||
			err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) ||
			!(importPath == module || strings.HasPrefix(importPath, module+"/") ||
				importPath == module+".test" || importPath == module+"_test") {
			return errors.New("I-3: external package in stdlib-only module dependency graph")
		}
	}
	if count == 0 {
		return errors.New("I-3: empty dependency graph")
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
