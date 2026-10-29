// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"slices"
	"strings"
	"testing"
)

func syntheticPolicy(t *testing.T) policy {
	t.Helper()
	_, file, _, _ := runtime.Caller(0)
	p, err := loadPolicy(filepath.Join(filepath.Dir(file), "engine-go-policy.json"))
	if err != nil {
		t.Fatal(err)
	}
	p.Modules[0].Phase = "scaffold"
	p.FuzzTargets = nil
	p.Denied = nil
	for i := 1; i <= 12; i++ {
		word := fmt.Sprintf("fixture-term-%02d", i)
		if i == 1 {
			word = "fixture-marker"
		}
		digest := sha256.Sum256([]byte(word))
		p.Denied = append(p.Denied, denyRule{fmt.Sprintf("P%02d", i), len(word), hex.EncodeToString(digest[:])})
	}
	return p
}

func fixture(t *testing.T) (string, policy) {
	t.Helper()
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("cannot locate test sources")
	}
	repository := filepath.Clean(filepath.Join(filepath.Dir(file), "../.."))
	root := t.TempDir()
	// On macOS a temporary directory may be reached through a /var symlink.
	// Go workspace matching uses absolute directory prefixes, so use one spelling.
	root, err := filepath.EvalSymlinks(root)
	if err != nil {
		t.Fatal(err)
	}
	files := []string{"go.work", "tools/world/go.mod", "tools/world/LICENSE", "docs/engine-go/README.md", "engine/go/go.mod", "engine/go/LICENSE", "engine/go/README.md", "engine/go/server/go.mod", "engine/go/server/go.sum", "engine/go/server/LICENSE", "engine/go/server/README.md", "engine/go/protocol/go.mod", "engine/go/protocol/README.md", "engine/go/protocol/LICENSE"}
	for _, name := range []string{"protocol", "bridge", "world", "worldbridge", "gateway", "gateway/meaning", "gateway/backend", "gateway/fixture", "gateway/gatewaytest"} {
		license := "AGPL-3.0-or-later"
		if name == "protocol" {
			license = "Apache-2.0"
		}
		writeFixture(t, root, "engine/go/"+name+"/doc.go", source(license, "package "+filepath.Base(name)+"\n"))
	}
	for _, component := range []string{"server/internal/pgstore", "server/internal/migrate", "server/internal/pgtest", "server/cmd/alatyr-server"} {
		name := filepath.Base(component)
		if strings.HasPrefix(component, "server/cmd/") {
			name = "main"
		}
		body := "package " + name + "\n"
		if name == "main" {
			body += "import _ " + fmt.Sprintf("%q", modulePrefix+"protocol") + "\n"
		}
		writeFixture(t, root, "engine/go/"+component+"/doc.go", source("AGPL-3.0-or-later", body))
	}
	for _, component := range []string{"gateway", "bridge", "world", "worldbridge"} {
		for _, name := range []string{"README.md", "LICENSE"} {
			files = append(files, "engine/go/"+component+"/"+name)
		}
	}
	for _, file := range files {
		data, err := os.ReadFile(filepath.Join(repository, file))
		if err != nil {
			t.Fatal("cannot read fixture source")
		}
		writeFixture(t, root, file, string(data))
	}
	return root, syntheticPolicy(t)
}

func writeFixture(t *testing.T, root, path, data string) {
	t.Helper()
	path = filepath.Join(root, filepath.FromSlash(path))
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(data), 0o644); err != nil {
		t.Fatal(err)
	}
}

func source(license, body string) string {
	return "// SPDX-License-Identifier: " + license + "\n// Copyright 2026 l2go\n\n" + body
}

func active(p *policy) {
	p.Modules[0].Phase = "active"
	p.FuzzTargets = []fuzzTarget{{".", "FuzzDecode"}}
}

func expectRule(t *testing.T, err error, rule string) {
	t.Helper()
	if err == nil || !strings.Contains(err.Error(), rule) {
		t.Fatalf("wanted %s rejection, got %v", rule, err)
	}
}

func TestScaffoldAndFuzzStatus(t *testing.T) {
	root, p := fixture(t)
	if err := validatePolicy(p); err != nil {
		t.Fatal(err)
	}
	if err := checkTree(root, p); err != nil {
		t.Fatal(err)
	}
	var out bytes.Buffer
	if err := runFuzz(root, p, &out); err != nil {
		t.Fatal(err)
	}
	if out.String() != "Protocol scaffold: no fuzz targets; codec fuzzing is not active.\n" {
		t.Fatal("inactive codec fuzzing must be explicit")
	}
}

func TestStructuralBoundaries(t *testing.T) {
	for _, mutation := range []string{"missing-use", "module-path", "nested-module", "functional-code", "license", "header", "vendor", "parent-license", "package-header", "old-module"} {
		t.Run(mutation, func(t *testing.T) {
			root, p := fixture(t)
			rule := "I-1"
			switch mutation {
			case "vendor":
				writeFixture(t, root, "engine/go/bridge/vendor/example/doc.go", "package example\n")
			case "parent-license":
				writeFixture(t, root, "engine/go/LICENSE", "not the license\n")
			case "package-header":
				writeFixture(t, root, "engine/go/world/doc.go", "package world\n")
			case "old-module":
				writeFixture(t, root, "engine/go/bridge/go.mod", "module "+modulePrefix+"bridge\ngo "+goVersion+"\n")
			case "missing-use":
				writeFixture(t, root, "go.work", "go "+goVersion+"\ntoolchain "+toolchainVersion+"\nuse ./tools/world\n")
			case "module-path":
				writeFixture(t, root, "engine/go/go.mod", "module example.org/other\ngo 1.27.1\ntoolchain go1.27.1\n")
			case "nested-module":
				writeFixture(t, root, "engine/go/extra/go.mod", "module example.org/extra\ngo 1.27.1\n")
			case "functional-code":
				writeFixture(t, root, "engine/go/protocol/doc.go", source("Apache-2.0", "package protocol\nfunc Start() {}\n"))
				rule = "I-2"
			case "license":
				writeFixture(t, root, "engine/go/protocol/LICENSE", "not the license\n")
			case "header":
				writeFixture(t, root, "engine/go/bridge/doc.go", "package bridge\n")
			}
			expectRule(t, checkTree(root, p), rule)
		})
	}
}

func TestWorkspaceToolchainIsSeparateFromModuleMinimum(t *testing.T) {
	for _, tc := range []struct {
		name, minimum, toolchain string
		wantOK                   bool
	}{
		{"pinned-patch", goVersion, toolchainVersion, true},
		{"old-patch", goVersion, "go" + goVersion, false},
		{"missing", goVersion, "", false},
		{"raised-minimum", "1.27.3", toolchainVersion, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			root, _ := fixture(t)
			path := filepath.Join(root, "go.work")
			data, err := os.ReadFile(path)
			if err != nil {
				t.Fatal(err)
			}
			updated := strings.Replace(string(data), "toolchain "+toolchainVersion,
				"toolchain "+tc.toolchain, 1)
			updated = strings.Replace(updated, "go "+goVersion+"\n", "go "+tc.minimum+"\n", 1)
			if tc.toolchain == "" {
				updated = strings.Replace(updated, "toolchain \n", "", 1)
			}
			writeFixture(t, root, "go.work", updated)
			err = checkWorkspace(root)
			if tc.wantOK {
				if err != nil {
					t.Fatal(err)
				}
				return
			}
			expectRule(t, err, "I-1")
		})
	}
}

func TestProtocolImportsAcrossBuildConstraints(t *testing.T) {
	tests := []struct{ file, body string }{
		{"bad.go", "package protocol\nimport _ \"" + modulePrefix + "gateway\"\n"},
		{"bad_test.go", "package protocol_test\nimport _ \"" + modulePrefix + "bridge\"\n"},
		{"tagged.go", "//go:build never_enabled\n\npackage protocol\nimport _ \"example.org/foreign\"\n"},
		{"bad_plan9.go", "package protocol\nimport _ \"github.com/l2go/alatyr/tools/world\"\n"},
		{"escaped.go", "package protocol\nimport _ \"example.org/for\\x65ign\"\n"},
		{"bad.go", "package protocol\nimport \"C\"\n"},
	}
	for i, test := range tests {
		t.Run(fmt.Sprint(i), func(t *testing.T) {
			root, p := fixture(t)
			active(&p)
			writeFixture(t, root, "engine/go/protocol/"+test.file, source("Apache-2.0", test.body))
			expectRule(t, checkTree(root, p), "I-3")
		})
	}
}

func TestProtocolModuleDirectives(t *testing.T) {
	for _, directive := range []string{
		"require example.org/foreign v1.0.0",
		"replace example.org/foreign => ../gateway",
		"exclude example.org/foreign v1.0.0",
		"tool example.org/foreign/cmd/tool",
	} {
		t.Run(strings.Fields(directive)[0], func(t *testing.T) {
			root, p := fixture(t)
			active(&p)
			writeFixture(t, root, "engine/go/protocol/go.mod", "module "+modulePrefix+"protocol\ngo 1.27.1\ntoolchain go1.27.1\n"+directive+"\n")
			expectRule(t, checkTree(root, p), "I-3")
		})
	}
}

func TestProtocolOwnSubpackagesAndTests(t *testing.T) {
	root, p := fixture(t)
	active(&p)
	writeFixture(t, root, "engine/go/protocol/wire/wire.go", source("Apache-2.0", "package wire\nimport \"encoding/binary\"\nvar Endian = binary.BigEndian\n"))
	writeFixture(t, root, "engine/go/protocol/doc_test.go", source("Apache-2.0", "package protocol_test\nimport (\n\"testing\"\n_ \""+modulePrefix+"protocol/wire\"\n)\nfunc TestWire(t *testing.T) {}\n"))
	if err := checkTree(root, p); err != nil {
		t.Fatal(err)
	}
}

func TestStandardLibraryClassification(t *testing.T) {
	for path, want := range map[string]bool{
		"encoding/binary": true, "unsafe": true, "syscall/js": true,
		modulePrefix + "protocol": true, modulePrefix + "protocol/wire": true,
		modulePrefix + "protocol-other": false, "C": false,
		"missingstdlib": false, "./local": false, "../relative": false,
		"example.org/foreign": false, "vendor/golang.org/x/net/http2": false,
	} {
		if got := (strings.HasPrefix(path, modulePrefix+"protocol/") || path == modulePrefix+"protocol" || standardImport(path, t.TempDir())); got != want {
			t.Errorf("classification mismatch for %q: got %t", path, got)
		}
	}
}

func TestPublicContentRules(t *testing.T) {
	p := syntheticPolicy(t)
	for _, test := range []struct{ value, want string }{
		{"ordinary English documentation", ""},
		{"FIXTURE-MARKER", "P01"},
		{"path/fixture-marker/module", "P01"},
		{"fixture-marker-web", "P01"},
		{"xfixture-markery", ""},
		{"GameCrypt", "client-codec"},
		{"crypto/rc4", "client-codec"},
		{"/Users/example/document", "local-path"},
		{`C:\Users\example\document`, "local-path"},
		{string(rune(0x0410)), "Cyrillic"},
		{string([]byte{0xff}), "text-encoding"},
		{"a\x00b", "text-encoding"},
	} {
		if got := contentRule([]byte(test.value), p.Denied); got != test.want {
			t.Errorf("content rule: wanted %s, got %s", test.want, got)
		}
	}
}

func TestScannerCoversFilesNamesAndDecodedLiterals(t *testing.T) {
	for _, test := range []struct{ path, body, rule string }{
		{"docs/engine-go/README.md", "fixture-marker", "P01"},
		{"engine/go/protocol/fixture-marker.md", "English", "P01"},
		{"engine/go/protocol/.hidden", "fixture-marker", "P01"},
		{"engine/go/protocol/testdata/sample", "fixture-marker", "P01"},
		{"engine/go/protocol/escaped.go", "package protocol\nconst s = \"\\x66ixture-marker\"\n", "P01"},
		{"engine/go/protocol/escaped.go", "package protocol\nconst s = \"\\u0410\"\n", "Cyrillic"},
		{"engine/go/protocol/escaped.go", "package protocol\nconst s = '\\u0410'\n", "Cyrillic"},
	} {
		t.Run(test.rule+filepath.Ext(test.path), func(t *testing.T) {
			root, p := fixture(t)
			writeFixture(t, root, test.path, test.body)
			err := scanScope(root, strings.Split(test.path, "/")[0]+"/"+strings.Split(test.path, "/")[1], p.Denied)
			expectRule(t, err, test.rule)
			if strings.Contains(err.Error(), "fixture-marker") || strings.Contains(err.Error(), root) {
				t.Fatal("diagnostic disclosed source content or a path")
			}
		})
	}
}

func TestScannerScopeAndSymlinks(t *testing.T) {
	root, p := fixture(t)
	writeFixture(t, root, "world/src/fixture-marker.txt", "fixture-marker")
	writeFixture(t, root, "tools/world/testdata/fixture-marker.txt", "fixture-marker")
	if err := checkTree(root, p); err != nil {
		t.Fatalf("scanner escaped its declared scope: %v", err)
	}
	if err := os.Symlink(filepath.Join(root, "tools/world/LICENSE"), filepath.Join(root, "engine/go/protocol/linked")); err != nil {
		t.Fatal(err)
	}
	expectRule(t, checkTree(root, p), "symlink")
}

func TestPolicyFailsClosed(t *testing.T) {
	for _, mutation := range []string{"empty-digests", "duplicate-digest", "bad-length", "active-empty", "scaffold-fuzz", "duplicate-target", "outside-target", "many-targets", "missing-packages", "duplicate-package", "overlap-package", "invalid-source", "invalid-import", "duplicate-import", "parent-scaffold"} {
		t.Run(mutation, func(t *testing.T) {
			p := syntheticPolicy(t)
			switch mutation {
			case "missing-packages":
				p.Packages = nil
			case "duplicate-package":
				p.Packages = append(p.Packages, p.Packages[0])
			case "overlap-package":
				p.Packages = append(p.Packages, packagePolicy{Source: "protocol/wire", Imports: []string{"protocol/**"}})
			case "invalid-source":
				p.Packages[0].Source = "../protocol"
			case "invalid-import":
				p.Packages[0].Imports = []string{"protocol/*"}
			case "duplicate-import":
				p.Packages[0].Imports = []string{"protocol/**", "protocol/**"}
			case "parent-scaffold":
				p.Modules[1].Phase = "scaffold"
			case "empty-digests":
				p.Denied = nil
			case "duplicate-digest":
				p.Denied[1].SHA256 = p.Denied[0].SHA256
			case "bad-length":
				p.Denied[0].Length = 0
			case "active-empty":
				p.Modules[0].Phase = "active"
			case "scaffold-fuzz":
				p.FuzzTargets = []fuzzTarget{{".", "FuzzDecode"}}
			case "duplicate-target":
				active(&p)
				p.FuzzTargets = append(p.FuzzTargets, p.FuzzTargets[0])
			case "outside-target":
				active(&p)
				p.FuzzTargets[0].Package = "../gateway"
			case "many-targets":
				active(&p)
				for i := range 4 {
					p.FuzzTargets = append(p.FuzzTargets, fuzzTarget{".", fmt.Sprintf("FuzzOther%d", i)})
				}
			}
			expectRule(t, validatePolicy(p), "policy:")
		})
	}
	root := t.TempDir()
	for _, value := range []string{"{}", "{} {}", "not JSON", `{"unknown":true}`} {
		writeFixture(t, root, "policy.json", value)
		_, err := loadPolicy(filepath.Join(root, "policy.json"))
		expectRule(t, err, "policy:")
	}
	_, err := loadPolicy(filepath.Join(root, "missing.json"))
	expectRule(t, err, "policy:")
}

func TestDependencyGraphRejectsExternalAndErrors(t *testing.T) {
	p := syntheticPolicy(t)
	for _, value := range []string{
		"", "invalid", `{"Error":{"Err":"bad"}}`,
		`{"DepsErrors":[{"Err":"bad"}]}`, `{"ImportPath":"external","Module":{"Path":"example.org/external"}}`,
	} {
		expectRule(t, checkDependencyJSON([]byte(value), p), "I-3")
	}
}

func TestMissingFuzzTargetFails(t *testing.T) {
	dir := t.TempDir()
	writeFixture(t, dir, "go.mod", "module example.org/fixture\ngo 1.27.1\n")
	writeFixture(t, dir, "doc.go", "package fixture\n")
	expectRule(t, findFuzzTarget(dir, fuzzTarget{".", "FuzzMissing"}), "does not exist")
}

func TestRunAndPolicyPath(t *testing.T) {
	root, p := fixture(t)
	data, err := json.Marshal(p)
	if err != nil {
		t.Fatal(err)
	}
	writeFixture(t, root, "tools/ci/engine-go-policy.json", string(data))
	var out bytes.Buffer
	if err := run(root, "tools/ci/engine-go-policy.json", false, &out); err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out.String(), "passed") {
		t.Fatal("missing success output")
	}
	expectRule(t, run(root, "../outside.json", false, &out), "policy:")
}

// These expectations are independent of the policy data: every matrix cell is
// exercised for both production and test files, including forbidden directions.
func TestPackageDependencyMatrix(t *testing.T) {
	rows := []struct {
		from           string
		imports, tests []string
	}{
		{"protocol", []string{"protocol"}, nil},
		{"world", []string{"world"}, nil},
		{"bridge", []string{"bridge", "protocol"}, nil},
		{"gateway", []string{"gateway", "protocol"}, nil},
		{"gateway/meaning", []string{"gateway", "gateway/meaning", "protocol"}, nil},
		{"gateway/backend", []string{"gateway", "gateway/meaning", "gateway/backend", "bridge", "protocol"}, []string{"gateway/fixture", "gateway/gatewaytest"}},
		{"gateway/fixture", []string{"gateway/fixture", "bridge", "protocol"}, nil},
		{"gateway/gatewaytest", []string{"gateway", "gateway/meaning", "gateway/backend", "gateway/fixture", "gateway/gatewaytest", "bridge", "protocol"}, nil},
		{"worldbridge", []string{"worldbridge", "world", "bridge", "protocol"}, nil},
	}
	root, p := fixture(t)
	for _, row := range rows {
		for _, target := range rows {
			for _, test := range []bool{false, true} {
				t.Run(fmt.Sprintf("%s_to_%s_test=%t", row.from, target.from, test), func(t *testing.T) {
					want := slices.Contains(row.imports, target.from) || test && slices.Contains(row.tests, target.from)
					if got := allowedEdge(p, row.from, target.from, test); got != want {
						t.Fatalf("matrix: got %t, want %t", got, want)
					}
					file := "probe.go"
					if test {
						file = "probe_test.go"
					}
					path := "engine/go/" + row.from + "/" + file
					license := "AGPL-3.0-or-later"
					if row.from == "protocol" {
						license = "Apache-2.0"
					}
					writeFixture(t, root, path, source(license, "package "+filepath.Base(row.from)+"\nimport _ \""+modulePrefix+target.from+"\"\n"))
					err := checkSources(root, p)
					if removeErr := os.Remove(filepath.Join(root, path)); removeErr != nil {
						t.Fatal(removeErr)
					}
					if want && err != nil {
						t.Fatal(err)
					}
					if !want {
						expectRule(t, err, "I-3")
					}
				})
			}
		}
	}
}

func TestPackageSelectorsAndUnknownDirectories(t *testing.T) {
	_, p := fixture(t)
	for _, tc := range []struct {
		from, to string
		want     bool
	}{
		{"protocol/wire", "protocol/proto", true},
		{"world/catalog", "world/memstore", true},
		{"world/catalog", "world/storage", false},
		{"bridge/profile", "bridge/internal/transport", true},
		{"worldbridge/adapter", "world/catalog", true},
		{"gateway/backend", "bridge/profile", true},
		{"gateway/gatewaytest", "gateway/backend/x", false},
		{"gateway/backend/x", "protocol/wire", false},
		{"gateway/unknown", "gateway", false},
		{"foo", "protocol", false},
		{".", "protocol", false},
		{"world", "worldish", false},
		{"protocol", "protocol-other", false},
	} {
		t.Run(tc.from+"_to_"+tc.to, func(t *testing.T) {
			if got := allowedEdge(p, tc.from, tc.to, false); got != tc.want {
				t.Fatalf("got %t, want %t", got, tc.want)
			}
		})
	}
	for _, dir := range []string{"gateway/backend/x", "gateway/unknown", "foo", "."} {
		t.Run(dir, func(t *testing.T) {
			root, p := fixture(t)
			writeFixture(t, root, "engine/go/"+dir+"/doc.go", source("AGPL-3.0-or-later", "package probe\n"))
			expectRule(t, checkSources(root, p), "unknown package")
		})
	}
}

func TestPackageImportsAcrossBuildConstraints(t *testing.T) {
	for _, tc := range []struct{ file, target string }{
		{"tagged.go", "example.org/foreign"},
		{"probe_plan9.go", modulePrefix + "bridge"},
		{"probe_test.go", modulePrefix + "gateway/gatewaytest"},
		{"escaped.go", "example.org/for\\x65ign"},
		{"invalid.go", modulePrefix + "world/../bridge"},
		{"cgo.go", "C"},
		{"relative.go", "../world"},
		{"vendor.go", "vendor/golang.org/x/net/http2"},
	} {
		t.Run(tc.file, func(t *testing.T) {
			root, p := fixture(t)
			writeFixture(t, root, "engine/go/gateway/meaning/"+tc.file,
				source("AGPL-3.0-or-later", "//go:build never_enabled\n\npackage meaning\nimport _ \""+tc.target+"\"\n"))
			expectRule(t, checkSources(root, p), "I-3")
		})
	}
}

func TestServerModuleDirectives(t *testing.T) {
	for _, directive := range []string{
		"require example.org/foreign v1.0.0",
		"replace " + modulePrefix + "protocol => ./protocol",
		"exclude " + modulePrefix + "protocol " + protocolVersion,
		"tool example.org/foreign/cmd/tool",
	} {
		t.Run(strings.Fields(directive)[0], func(t *testing.T) {
			root, p := fixture(t)
			file := "engine/go/go.mod"
			b, err := os.ReadFile(filepath.Join(root, file))
			if err != nil {
				t.Fatal(err)
			}
			writeFixture(t, root, file, string(b)+directive+"\n")
			expectRule(t, checkModule(root, p.Modules[1]), "I-3")
		})
	}
	for _, requirement := range []string{"", modulePrefix + "protocol v0.0.0-20261023084051-3b52c8d267a0", modulePrefix + "protocol " + protocolVersion + " // indirect", modulePrefix + "bridge v0.0.0-20261023154142-3ca081d3eba3"} {
		t.Run(requirement, func(t *testing.T) {
			root, p := fixture(t)
			directive := ""
			if requirement != "" {
				directive = "require " + requirement + "\n"
			}
			writeFixture(t, root, "engine/go/go.mod", "module "+serverModule+"\ngo "+goVersion+"\n"+directive)
			expectRule(t, checkModule(root, p.Modules[1]), "I-3")
		})
	}
}

func TestPackageProviders(t *testing.T) {
	p := syntheticPolicy(t)
	dir := t.TempDir()
	for _, path := range []string{"protocol/wire", "bridge/profile", "world/catalog", "gateway/backend", "worldbridge"} {
		t.Run(path, func(t *testing.T) {
			module := serverModule
			suffix := path
			if strings.HasPrefix(path, "protocol/") {
				module += "/protocol"
				suffix = strings.TrimPrefix(path, "protocol/")
			}
			pkg := listedPackage{ImportPath: modulePrefix + path, Dir: filepath.Join(dir, suffix)}
			pkg.Module = &struct {
				Path, Version, Dir string
				Main               bool
				Replace            json.RawMessage `json:",omitempty"`
			}{Path: module, Version: protocolVersion, Dir: dir, Main: true}
			check := func(want bool) {
				t.Helper()
				b, err := json.Marshal(pkg)
				if err != nil {
					t.Fatal(err)
				}
				err = checkDependencyJSON(b, p)
				if want && err != nil {
					t.Fatal(err)
				}
				if !want {
					expectRule(t, err, "I-3")
				}
			}
			check(true)
			pkg.Module.Path = modulePrefix + "bridge"
			check(false)
			pkg.Module.Path = module
			pkg.Module.Replace = json.RawMessage(`{"Path":"example.org/substitute"}`)
			check(false)
			pkg.Module.Replace = nil
			pkg.Dir = filepath.Dir(dir)
			check(false)
			pkg.Dir = filepath.Join(dir, suffix)
			if module == modulePrefix+"protocol" {
				pkg.Module.Main = false
				pkg.Module.Version = "v1.0.0"
				check(true)
			}
		})
	}
}

func TestResolvedGraphDependencyDirections(t *testing.T) {
	p := syntheticPolicy(t)
	dir := t.TempDir()
	for _, test := range []bool{false, true} {
		for _, to := range []string{"bridge", "world", "worldbridge", "gateway/fixture", "gateway/gatewaytest", "gateway/backend/x"} {
			t.Run(fmt.Sprintf("%s_test=%t", to, test), func(t *testing.T) {
				from := modulePrefix + "gateway/backend"
				if test {
					from += " [" + modulePrefix + "gateway/backend.test]"
				}
				value := map[string]any{"ImportPath": from, "Dir": filepath.Join(dir, "gateway/backend"), "Module": map[string]any{"Path": serverModule, "Dir": dir, "Main": true}, "Imports": []string{modulePrefix + to}}
				b, err := json.Marshal(value)
				if err != nil {
					t.Fatal(err)
				}
				err = checkDependencyJSON(b, p)
				want := to == "bridge" || test && (to == "gateway/fixture" || to == "gateway/gatewaytest")
				if want && err != nil {
					t.Fatal(err)
				}
				if !want {
					expectRule(t, err, "I-3")
				}
			})
		}
	}
}

func TestApplicationModuleBoundaries(t *testing.T) {
	for _, tc := range []struct{ name, old, replacement string }{
		{"missing-replace", "replace " + serverModule + " => ../", ""},
		{"wrong-target", "=> ../", "=> ../../"},
		{"uncanonical-parent", "=> ../", "=> .."},
		{"parent-pseudoversion", serverModule + " v0.0.0", serverModule + " " + protocolVersion},
		{"protocol-newer", modulePrefix + "protocol " + protocolVersion, modulePrefix + "protocol v0.0.0-20261023084051-3b52c8d267a0"},
		{"transitive-direct", "github.com/jackc/puddle/v2 v2.2.3 // indirect", "github.com/jackc/puddle/v2 v2.2.3"},
		{"absolute-target", "=> ../", "=> /tmp/other"},
		{"versioned-target", "=> ../", "=> example.org/other v1.0.0"},
		{"wrong-module", "replace " + serverModule + " =>", "replace " + modulePrefix + "protocol =>"},
		{"extra-replace", "", "replace example.org/other => ../\n"},
		{"exclude", "", "exclude example.org/other v1.0.0\n"},
		{"tool", "", "tool example.org/other/cmd/run\n"},
		{"unpinned-pgx", "github.com/jackc/pgx/v5 v5.11.0", "github.com/jackc/pgx/v5 v5.10.0"},
		{"indirect-pgx", "github.com/jackc/pgx/v5 v5.11.0", "github.com/jackc/pgx/v5 v5.11.0 // indirect"},
		{"unpinned-goose", "github.com/pressly/goose/v3 v3.29.0", "github.com/pressly/goose/v3 v3.28.0"},
		{"unknown-dependency", "", "require example.org/other v1.0.0\n"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			root, p := fixture(t)
			file := "engine/go/server/go.mod"
			data, err := os.ReadFile(filepath.Join(root, file))
			if err != nil {
				t.Fatal(err)
			}
			s := string(data)
			if tc.old == "" {
				s += tc.replacement
			} else {
				s = strings.Replace(s, tc.old, tc.replacement, 1)
			}
			writeFixture(t, root, file, s)
			expectRule(t, checkModule(root, p.Modules[2]), "I-3")
		})
	}
}

func TestExternalSourceBoundaries(t *testing.T) {
	for _, from := range []string{"world", "world/catalog", "gateway", "gateway/backend", "bridge", "worldbridge", "protocol/wire", "server/cmd/alatyr-server"} {
		for _, target := range []string{"github.com/jackc/pgx/v5", "github.com/pressly/goose/v3", modulePrefix + "server/internal/pgstore"} {
			if from == "server/cmd/alatyr-server" && target == modulePrefix+"server/internal/pgstore" {
				continue
			}
			t.Run(from+"_"+target, func(t *testing.T) {
				root, p := fixture(t)
				writeFixture(t, root, "engine/go/"+from+"/probe_plan9_test.go", source("AGPL-3.0-or-later", "//go:build never_enabled\n\npackage "+"probe"+"\nimport _ "+fmt.Sprintf("%q", target)+"\n"))
				expectRule(t, checkSources(root, p), "I-3")
			})
		}
	}
	for _, target := range []string{"github.com/pressly/goose/v3/cmd/goose", "github.com/pressly/goose/v3/database", "github.com/lib/pq", "modernc.org/sqlite", "github.com/go-sql-driver/mysql"} {
		t.Run(target, func(t *testing.T) {
			root, p := fixture(t)
			writeFixture(t, root, "engine/go/server/internal/pgstore/probe.go", source("AGPL-3.0-or-later", "package pgstore\nimport _ "+fmt.Sprintf("%q", target)+"\n"))
			expectRule(t, checkSources(root, p), "I-3")
		})
	}
}

func TestExternalGraphPinsAndDirections(t *testing.T) {
	p := syntheticPolicy(t)
	dir := t.TempDir()
	for _, tc := range []struct{ name, path, version, replace string }{
		{"wrong-version", "github.com/jackc/pgx/v5", "v5.10.0", ""},
		{"unknown", "example.org/external", "v1.0.0", ""},
		{"substitution", "github.com/jackc/pgx/v5", "v5.11.0", `{"Path":"../"}`},
	} {
		t.Run(tc.name, func(t *testing.T) {
			value := map[string]any{"ImportPath": tc.path, "Dir": dir, "Module": map[string]any{"Path": tc.path, "Dir": dir, "Version": tc.version}}
			if tc.replace != "" {
				value["Module"].(map[string]any)["Replace"] = json.RawMessage(tc.replace)
			}
			data, err := json.Marshal(value)
			if err != nil {
				t.Fatal(err)
			}
			expectRule(t, checkDependencyJSON(data, p), "I-3")
		})
	}
	for _, from := range []string{"world", "gateway", "worldbridge", "protocol/wire"} {
		for _, target := range []string{"github.com/jackc/pgx/v5", modulePrefix + "server/internal/pgstore"} {
			t.Run(from+"_"+target, func(t *testing.T) {
				module := serverModule
				suffix := from
				if strings.HasPrefix(from, "protocol/") {
					module += "/protocol"
					suffix = strings.TrimPrefix(from, "protocol/")
				}
				value := map[string]any{"ImportPath": modulePrefix + from, "Dir": filepath.Join(dir, suffix), "Module": map[string]any{"Path": module, "Dir": dir, "Main": true}, "Imports": []string{target}}
				data, err := json.Marshal(value)
				if err != nil {
					t.Fatal(err)
				}
				expectRule(t, checkDependencyJSON(data, p), "I-3")
			})
		}
	}
}

func TestGooseProviderOnlyAPI(t *testing.T) {
	for _, body := range []string{
		`import "github.com/pressly/goose/v3"; func f(){ goose.Up(nil,".") }`,
		`import "github.com/pressly/goose/v3"; var dialect=goose.DialectMySQL`,
		`import "github.com/pressly/goose/v3"; func f(){ goose.NewProvider(goose.DialectPostgres,nil,nil) }`,
		`import "github.com/pressly/goose/v3"; func f(){ goose.NewProvider(goose.DialectPostgres,nil,nil,goose.WithDisableGlobalRegistry(false)) }`,
		`import "github.com/pressly/goose/v3"; func f(){ goose.NewProvider("postgres",nil,nil,goose.WithDisableGlobalRegistry(true)) }`,
		`import _ "github.com/pressly/goose/v3"`,
		`import . "github.com/pressly/goose/v3"`,
	} {
		root, p := fixture(t)
		writeFixture(t, root, "engine/go/server/internal/migrate/probe.go", source("AGPL-3.0-or-later", "package migrate\n"+body+"\n"))
		expectRule(t, checkSources(root, p), "I-3")
	}
	root, p := fixture(t)
	writeFixture(t, root, "engine/go/server/internal/migrate/probe.go", source("AGPL-3.0-or-later", `package migrate
 import g "github.com/pressly/goose/v3"
 func f(){g.NewProvider(g.DialectPostgres,nil,nil,g.WithDisableGlobalRegistry(true))}
 `))
	if err := checkSources(root, p); err != nil {
		t.Fatal(err)
	}
}

func TestApplicationPolicyAndWorkspace(t *testing.T) {
	for _, kind := range []string{"application-scaffold", "external-core", "unknown-pin", "changed-pin"} {
		t.Run(kind, func(t *testing.T) {
			p := syntheticPolicy(t)
			switch kind {
			case "application-scaffold":
				p.Modules[2].Phase = "scaffold"
			case "external-core":
				p.Packages[1].ExternalImports = []string{"github.com/jackc/pgx/v5/**"}
			case "unknown-pin":
				p.ExternalModules["example.org/unknown"] = "v1.0.0"
			case "changed-pin":
				p.ExternalModules["github.com/jackc/pgx/v5"] = "v5.10.0"
			}
			expectRule(t, validatePolicy(p), "policy:")
		})
	}
	root, _ := fixture(t)
	data, err := os.ReadFile(filepath.Join(root, "go.work"))
	if err != nil {
		t.Fatal(err)
	}
	writeFixture(t, root, "go.work", strings.Replace(string(data), "\t./engine/go/server\n", "", 1))
	expectRule(t, checkWorkspace(root), "I-1")
}

func TestPostgresResultGateRejectsMissingOrSkippedTests(t *testing.T) {
	var events []map[string]string
	for _, required := range requiredPostgresTests(t) {
		events = append(events, map[string]string{"Action": "run", "Package": required.Package, "Test": required.Test}, map[string]string{"Action": "pass", "Package": required.Package, "Test": required.Test})
	}
	encode := func(events []map[string]string) []byte {
		t.Helper()
		var out bytes.Buffer
		for _, event := range events {
			if err := json.NewEncoder(&out).Encode(event); err != nil {
				t.Fatal(err)
			}
		}
		return out.Bytes()
	}
	if err := checkPostgresResults(bytes.NewReader(encode(events)), requiredPostgresTests(t)); err != nil {
		t.Fatal(err)
	}
	for _, kind := range []string{"zero", "missing", "skip", "duplicate", "fail", "wrong-package", "only-subtest", "pass-without-run", "invalid"} {
		t.Run(kind, func(t *testing.T) {
			data := encode(events)
			var changed []map[string]string
			if err := json.Unmarshal([]byte("["+strings.ReplaceAll(strings.TrimSpace(string(data)), "}\n{", "},{")+"]"), &changed); err != nil {
				t.Fatal(err)
			}
			switch kind {
			case "zero":
				changed = nil
			case "missing":
				changed = changed[2:]
			case "skip":
				changed[1]["Action"] = "skip"
			case "duplicate":
				changed = append(changed, changed[1])
			case "fail":
				changed[1]["Action"] = "fail"
			case "wrong-package":
				changed[1]["Package"] = "example.org/other"
			case "only-subtest":
				changed[1]["Test"] += "/subtest"
			case "pass-without-run":
				changed = changed[1:]
			}
			if kind == "invalid" {
				data = []byte("not JSON")
			} else {
				data = encode(changed)
			}
			expectRule(t, checkPostgresResults(bytes.NewReader(data), requiredPostgresTests(t)), "postgres:")
		})
	}
}

func TestApplicationProvidersAndFiles(t *testing.T) {
	for _, kind := range []string{"missing-license", "wrong-license", "missing-readme", "missing-package-doc", "unknown-package", "parent-provider", "outside-parent-replacement"} {
		t.Run(kind, func(t *testing.T) {
			root, p := fixture(t)
			switch kind {
			case "missing-license", "missing-readme":
				name := "LICENSE"
				if kind == "missing-readme" {
					name = "README.md"
				}
				if err := os.Remove(filepath.Join(root, "engine/go/server", name)); err != nil {
					t.Fatal(err)
				}
				expectRule(t, checkModule(root, p.Modules[2]), "I-1")
			case "wrong-license":
				writeFixture(t, root, "engine/go/server/LICENSE", "different license\n")
				expectRule(t, checkModule(root, p.Modules[2]), "I-1")
			case "missing-package-doc":
				if err := os.Remove(filepath.Join(root, "engine/go/server/internal/pgstore/doc.go")); err != nil {
					t.Fatal(err)
				}
				writeFixture(t, root, "engine/go/server/internal/pgstore/store.go", source("AGPL-3.0-or-later", "package pgstore\n"))
				expectRule(t, checkSources(root, p), "I-1")
			case "unknown-package":
				writeFixture(t, root, "engine/go/server/internal/unknown/doc.go", source("AGPL-3.0-or-later", "package unknown\n"))
				expectRule(t, checkSources(root, p), "I-3")
			case "parent-provider":
				value := map[string]any{"ImportPath": modulePrefix + "server/internal/pgstore", "Dir": filepath.Join(root, "server/internal/pgstore"), "Module": map[string]any{"Path": serverModule, "Dir": root, "Main": true}}
				data, err := json.Marshal(value)
				if err != nil {
					t.Fatal(err)
				}
				expectRule(t, checkDependencyJSON(data, p), "I-3")
			case "outside-parent-replacement":
				outside := t.TempDir()
				value := map[string]any{"ImportPath": modulePrefix + "world", "Dir": filepath.Join(outside, "world"), "Module": map[string]any{"Path": serverModule, "Dir": outside, "Replace": map[string]any{"Path": "../", "Dir": outside}}}
				data, err := json.Marshal(value)
				if err != nil {
					t.Fatal(err)
				}
				expectRule(t, checkDependencyJSON(data, p, graphContext{root, modulePrefix + "server", false}), "I-3")
			}
		})
	}
}

func requiredPostgresTests(t *testing.T) []postgresTest {
	t.Helper()
	_, file, _, _ := runtime.Caller(0)
	data, err := os.ReadFile(filepath.Join(filepath.Dir(file), "../../engine/go/server/postgres-tests.txt"))
	if err != nil {
		t.Fatal(err)
	}
	list, err := loadPostgresTests(bytes.NewReader(data))
	if err != nil {
		t.Fatal(err)
	}
	return list
}

func TestPostgresManifestRejectsEmptyAndInvalidLists(t *testing.T) {
	for _, data := range []string{"", "# only a comment\n", "example.org/other TestOne", "github.com/l2go/alatyr/engine/go/server/internal/pgstore TestOne extra", "github.com/l2go/alatyr/engine/go/server/internal/pgstore TestOne/subtest", "github.com/l2go/alatyr/engine/go/server/internal/pgstore TestOne\ngithub.com/l2go/alatyr/engine/go/server/internal/pgstore TestOne\n"} {
		if _, err := loadPostgresTests(strings.NewReader(data)); err == nil {
			t.Fatalf("invalid test manifest accepted: %q", data)
		}
	}
	if err := checkPostgresResults(strings.NewReader(""), nil); err == nil {
		t.Fatal("empty manifest accepted")
	}
}

func TestGraphModesUseProviderPathAndDirectory(t *testing.T) {
	p := syntheticPolicy(t)
	root := t.TempDir()
	parent := filepath.Join(root, "engine/go")
	makePackage := func(module, path, dir, version string, main bool, replace any) map[string]any {
		provider := map[string]any{"Path": module, "Dir": dir, "Version": version, "Main": main}
		if replace != nil {
			provider["Replace"] = replace
		}
		return map[string]any{"ImportPath": path, "Dir": filepath.Join(dir, strings.TrimPrefix(path, module)), "Module": provider}
	}
	check := func(pkg map[string]any, mode graphContext, want bool) {
		t.Helper()
		data, err := json.Marshal(pkg)
		if err != nil {
			t.Fatal(err)
		}
		err = checkDependencyJSON(data, p, mode)
		if want && err != nil {
			t.Fatal(err)
		}
		if !want {
			expectRule(t, err, "I-3")
		}
	}
	workspace := graphContext{root, modulePrefix + "server", true}
	for _, main := range []bool{false, true} {
		for _, module := range []string{serverModule, modulePrefix + "protocol", modulePrefix + "server"} {
			dir := parent
			rel := "world"
			if module != serverModule {
				dir = filepath.Join(parent, strings.TrimPrefix(module, modulePrefix))
				rel = "wire"
				if module == modulePrefix+"server" {
					rel = "internal/pgstore"
				}
			}
			check(makePackage(module, module+"/"+rel, dir, "", main, nil), workspace, true)
		}
	}
	off := graphContext{root, modulePrefix + "server", false}
	valid := map[string]any{"Path": "../", "Dir": parent}
	check(makePackage(serverModule, modulePrefix+"world", parent, "v0.0.0", false, valid), off, true)
	for _, kind := range []string{"missing-replace", "wrong-version", "wrong-directory", "wrong-target", "workspace-replace", "workspace-version"} {
		t.Run(kind, func(t *testing.T) {
			version := "v0.0.0"
			dir := parent
			replacement := any(valid)
			mode := off
			switch kind {
			case "missing-replace":
				replacement = nil
			case "wrong-version":
				version = protocolVersion
			case "wrong-directory":
				dir = t.TempDir()
			case "wrong-target":
				replacement = map[string]any{"Path": "..", "Dir": parent}
			case "workspace-replace":
				version = ""
				mode = workspace
			case "workspace-version":
				replacement = nil
				mode = workspace
			}
			check(makePackage(serverModule, modulePrefix+"world", dir, version, true, replacement), mode, false)
		})
	}
	// In workspace a pin does not appear in graph metadata. The module-file gate
	// remains responsible for the protocol requirement, including indirect form.
	protocolDir := filepath.Join(parent, "protocol")
	check(makePackage(modulePrefix+"protocol", modulePrefix+"protocol/wire", protocolDir, "", true, nil), workspace, true)
}

func TestApplicationMatrixSpecificExternalEdges(t *testing.T) {
	for _, tc := range []struct{ from, target string }{
		{"server/internal/migrate", "github.com/jackc/pgx/v5"},
		{"server/internal/pgstore", "github.com/jackc/pgx/v5/stdlib"},
		{"server/internal/pgtest", "github.com/pressly/goose/v3"},
		{"server/internal/pgtest", modulePrefix + "server/internal/migrate"},
	} {
		root, p := fixture(t)
		writeFixture(t, root, "engine/go/"+tc.from+"/probe.go", source("AGPL-3.0-or-later", "package probe\nimport _ "+fmt.Sprintf("%q", tc.target)+"\n"))
		expectRule(t, checkSources(root, p), "I-3")
	}
	root, p := fixture(t)
	writeFixture(t, root, "engine/go/server/internal/pgstore/probe.go", "// SPDX-License-Identifier: AGPL-3.0-or-later\npackage pgstore\n")
	expectRule(t, checkModule(root, p.Modules[2]), "I-1")
}

func TestApplicationProtocolIndirectMatchesSources(t *testing.T) {
	root, p := fixture(t)
	writeFixture(t, root, "engine/go/server/cmd/alatyr-server/doc.go", source("AGPL-3.0-or-later", "package main\n"))
	file := "engine/go/server/go.mod"
	data, err := os.ReadFile(filepath.Join(root, file))
	if err != nil {
		t.Fatal(err)
	}
	indirect := strings.Replace(string(data), modulePrefix+"protocol "+protocolVersion, modulePrefix+"protocol "+protocolVersion+" // indirect", 1)
	writeFixture(t, root, file, indirect)
	if err := checkModule(root, p.Modules[2]); err != nil {
		t.Fatal(err)
	}
	without := strings.Replace(indirect, modulePrefix+"protocol "+protocolVersion+" // indirect", "", 1)
	writeFixture(t, root, file, without)
	expectRule(t, checkModule(root, p.Modules[2]), "I-3")
}

func TestWorldTestCompositionBoundary(t *testing.T) {
	p := syntheticPolicy(t)
	for _, from := range []string{"protocol", "world", "bridge", "gateway", "gateway/meaning", "gateway/backend", "gateway/fixture", "gateway/gatewaytest", "worldbridge"} {
		t.Run(from, func(t *testing.T) {
			for _, test := range []bool{false, true} {
				if allowedEdge(p, from, "worldtest", test) {
					t.Fatalf("%s acquired composition dependency", from)
				}
			}
		})
	}
	for _, to := range []string{"worldtest", "world/catalog", "worldbridge", "gateway", "gateway/backend", "gateway/gatewaytest", "bridge", "protocol/wire"} {
		if !allowedEdge(p, "worldtest", to, true) {
			t.Fatalf("composition cannot import %s", to)
		}
	}
	for _, to := range []string{"worldtest/unknown", "gateway/backend/x", "foreign"} {
		if allowedEdge(p, "worldtest", to, true) {
			t.Fatalf("composition imported unknown %s", to)
		}
	}
	root, p := fixture(t)
	writeFixture(t, root, "engine/go/worldtest/probe.go", source("AGPL-3.0-or-later", "package worldtest\nimport _ \"example.org/foreign\"\n"))
	expectRule(t, checkSources(root, p), "I-3")
}

func TestHuntCalculationBoundary(t *testing.T) {
	p := syntheticPolicy(t)
	for _, to := range []string{"world", "world/catalog", "worldbridge", "gateway", "bridge", "protocol/wire"} {
		for _, test := range []bool{false, true} {
			if allowedEdge(p, "world/hunt", to, test) {
				t.Fatalf("pure hunt calculation imported %s", to)
			}
		}
	}
	if !allowedEdge(p, "world", "world/hunt", false) {
		t.Fatal("world cannot use its reward calculation")
	}
	root, p := fixture(t)
	writeFixture(t, root, "engine/go/world/hunt/probe.go", source("AGPL-3.0-or-later", "package hunt\nimport _ \"github.com/l2go/alatyr/engine/go/world\"\n"))
	expectRule(t, checkSources(root, p), "I-3")
}

func TestRetiredRuntimeCannotReturn(t *testing.T) {
	for _, path := range []string{"engine/java/source.txt", "pom.xml", ".mvn/settings.xml", "mvnw", "mvnw.cmd", "tools/ci/bridge-integration.sh", "tools/ci/bridge-integration.sql", "tools/ci/java-fixture/source.txt", "engine/go/gateway/backend/java_integration_test.go", "tools/ci/coverage-summary.py", "deploy/measure/start.sh", "deploy/probe/start.sh"} {
		t.Run(path, func(t *testing.T) {
			root := t.TempDir()
			if err := checkRuntime(root); err != nil {
				t.Fatal(err)
			}
			writeFixture(t, root, path, "retired")
			expectRule(t, checkRuntime(root), "runtime:")
		})
	}
}

// The host acceptance test may reuse the participant driver. Production and
// storage packages must never depend on that test composition.
func TestHostParticipantDriverIsTestOnly(t *testing.T) {
	const host = "server/cmd/alatyr-server"
	const driver = "gateway/gatewaytest"
	p := syntheticPolicy(t)
	if !allowedEdge(p, host, driver, true) {
		t.Fatal("host test cannot use participant driver")
	}
	for _, tc := range []struct {
		from string
		test bool
	}{
		{host, false}, {"server/internal/pgstore", false}, {"server/internal/pgstore", true},
		{"server/internal/migrate", true}, {"world", true}, {"worldbridge", true},
	} {
		t.Run(fmt.Sprintf("%s/test=%t", tc.from, tc.test), func(t *testing.T) {
			if allowedEdge(p, tc.from, driver, tc.test) {
				t.Fatal("participant test driver escaped host tests")
			}
			root, p := fixture(t)
			file := "probe.go"
			if tc.test {
				file = "probe_test.go"
			}
			writeFixture(t, root, "engine/go/"+tc.from+"/"+file, source("AGPL-3.0-or-later", "package probe\nimport _ "+fmt.Sprintf("%q", modulePrefix+driver)+"\n"))
			expectRule(t, checkSources(root, p), "I-3")
		})
	}
}
