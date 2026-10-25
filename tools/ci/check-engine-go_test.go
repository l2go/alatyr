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
	files := []string{"go.work", "tools/world/go.mod", "tools/world/LICENSE", "docs/engine-go/README.md", "engine/go/go.mod", "engine/go/LICENSE", "engine/go/README.md", "engine/go/protocol/go.mod", "engine/go/protocol/README.md", "engine/go/protocol/LICENSE"}
	for _, name := range []string{"protocol", "bridge", "world", "worldbridge", "gateway", "gateway/meaning", "gateway/backend", "gateway/fixture", "gateway/gatewaytest"} {
		license := "AGPL-3.0-or-later"
		if name == "protocol" {
			license = "Apache-2.0"
		}
		writeFixture(t, root, "engine/go/"+name+"/doc.go", source(license, "package "+filepath.Base(name)+"\n"))
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
		{"world/catalog", "world/storage", true},
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
				check(false)
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
