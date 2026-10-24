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
	"strings"
	"testing"
)

func syntheticPolicy() policy {
	p := policy{Version: 1, FuzzTargets: []fuzzTarget{}}
	for _, name := range moduleNames {
		license := "AGPL-3.0-or-later"
		if name == "protocol" {
			license = "Apache-2.0"
		}
		p.Modules = append(p.Modules, modulePolicy{"engine/go/" + name, modulePrefix + name, name, license, "scaffold"})
	}
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
	_, source, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("cannot locate test sources")
	}
	repository := filepath.Clean(filepath.Join(filepath.Dir(source), "../.."))
	root := t.TempDir()
	// On macOS a temporary directory may be reached through a /var symlink.
	// Go workspace matching uses absolute directory prefixes, so use one spelling.
	root, err := filepath.EvalSymlinks(root)
	if err != nil {
		t.Fatal(err)
	}
	files := []string{"go.work", "tools/world/go.mod", "tools/world/LICENSE", "docs/engine-go/README.md"}
	for _, name := range moduleNames {
		// This fixture models scaffolds even when the real module has become active.
		// Do not inherit production dependency directives into the synthetic tree.
		writeFixture(t, root, "engine/go/"+name+"/go.mod", "module "+modulePrefix+name+"\n\ngo "+goVersion+"\n")
		for _, file := range []string{"doc.go", "README.md", "LICENSE"} {
			files = append(files, "engine/go/"+name+"/"+file)
		}
	}
	for _, file := range files {
		data, err := os.ReadFile(filepath.Join(repository, file))
		if err != nil {
			t.Fatal("cannot read fixture source")
		}
		writeFixture(t, root, file, string(data))
	}
	return root, syntheticPolicy()
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

func activeModule(t *testing.T, p *policy, name string) modulePolicy {
	t.Helper()
	for i := range p.Modules {
		if p.Modules[i].Package == name {
			p.Modules[i].Phase = "active"
			return p.Modules[i]
		}
	}
	t.Fatalf("missing module %s", name)
	return modulePolicy{}
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
	for _, mutation := range []string{"missing-use", "module-path", "nested-module", "functional-code", "license", "header"} {
		t.Run(mutation, func(t *testing.T) {
			root, p := fixture(t)
			rule := "I-1"
			switch mutation {
			case "missing-use":
				writeFixture(t, root, "go.work", "go "+goVersion+"\ntoolchain "+toolchainVersion+"\nuse ./tools/world\n")
			case "module-path":
				writeFixture(t, root, "engine/go/gateway/go.mod", "module example.org/other\ngo 1.27.1\ntoolchain go1.27.1\n")
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
		if got := allowedImport(path, modulePrefix+"protocol", t.TempDir()); got != want {
			t.Errorf("classification mismatch for %q: got %t", path, got)
		}
	}
}

func TestPublicContentRules(t *testing.T) {
	p := syntheticPolicy()
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
	for _, mutation := range []string{"empty-digests", "duplicate-digest", "bad-length", "active-empty", "scaffold-fuzz", "duplicate-target", "outside-target", "many-targets"} {
		t.Run(mutation, func(t *testing.T) {
			p := syntheticPolicy()
			switch mutation {
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
	dir := t.TempDir()
	for _, value := range []string{
		`{"Standard":true,"ImportPath":"fmt"}`,
		fmt.Sprintf(`{"Dir":%q,"ImportPath":%q,"Module":{"Path":%q}}`, dir, modulePrefix+"protocol", modulePrefix+"protocol"),
	} {
		if err := checkDependencyJSON([]byte(value), modulePrefix+"protocol", dir); err != nil {
			t.Fatal(err)
		}
	}
	for _, value := range []string{
		"", "invalid", `{"Error":{"Err":"bad"}}`,
		`{"DepsErrors":[{"Err":"bad"}]}`, `{"ImportPath":"external","Module":{"Path":"example.org/external"}}`,
	} {
		expectRule(t, checkDependencyJSON([]byte(value), modulePrefix+"protocol", dir), "I-3")
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

func TestScaffoldDependencyStillRejected(t *testing.T) {
	for _, name := range moduleNames {
		t.Run(name, func(t *testing.T) {
			root, p := fixture(t)
			writeFixture(t, root, "engine/go/"+name+"/go.mod", "module "+modulePrefix+name+"\n\ngo "+goVersion+"\nrequire example.org/foreign v1.0.0\n")
			expectRule(t, checkTree(root, p), "I-3")
		})
	}
}

// The active world must retain the same stdlib-only boundary as the protocol,
// including tests and files excluded by the current platform or build tags.
func TestWorldImportsAcrossBuildConstraints(t *testing.T) {
	for _, test := range []struct{ file, body string }{
		{"bad.go", "package world\nimport _ \"" + modulePrefix + "bridge\"\n"},
		{"adapter.go", "package world\nimport _ \"" + modulePrefix + "worldbridge\"\n"},
		{"bad_test.go", "package world_test\nimport _ \"example.org/foreign\"\n"},
		{"tagged.go", "//go:build never_enabled\n\npackage world\nimport _ \"example.org/foreign\"\n"},
		{"bad_plan9.go", "package world\nimport _ \"" + modulePrefix + "protocol\"\n"},
		{"cgo.go", "package world\nimport \"C\"\n"},
	} {
		t.Run(test.file, func(t *testing.T) {
			root, p := fixture(t)
			activeModule(t, &p, "world")
			writeFixture(t, root, "engine/go/world/"+test.file, source("AGPL-3.0-or-later", test.body))
			expectRule(t, checkTree(root, p), "I-3")
		})
	}
}
func TestWorldModuleDirectives(t *testing.T) {
	for _, directive := range []string{"require example.org/foreign v1.0.0", "replace example.org/foreign => ../bridge", "exclude example.org/foreign v1.0.0", "tool example.org/foreign/cmd/tool"} {
		t.Run(strings.Fields(directive)[0], func(t *testing.T) {
			root, p := fixture(t)
			activeModule(t, &p, "world")
			writeFixture(t, root, "engine/go/world/go.mod", "module "+modulePrefix+"world\ngo "+goVersion+"\n"+directive+"\n")
			expectRule(t, checkTree(root, p), "I-3")
		})
	}
}
func TestWorldOwnSubpackages(t *testing.T) {
	root, p := fixture(t)
	activeModule(t, &p, "world")
	writeFixture(t, root, "engine/go/world/catalog/catalog.go", source("AGPL-3.0-or-later", "package catalog\nimport \"encoding/csv\"\nvar NewReader=csv.NewReader\n"))
	writeFixture(t, root, "engine/go/world/doc_test.go", source("AGPL-3.0-or-later", "package world_test\nimport _ \""+modulePrefix+"world/catalog\"\n"))
	if err := checkTree(root, p); err != nil {
		t.Fatal(err)
	}
}

func adapterModule(t *testing.T, root string, p *policy) modulePolicy {
	t.Helper()
	m := activeModule(t, p, "worldbridge")
	writeFixture(t, root, "engine/go/worldbridge/go.mod", "module "+modulePrefix+"worldbridge\n\ngo "+goVersion+"\nrequire (\n"+
		modulePrefix+"bridge v0.0.0-20261023154142-3ca081d3eba3\n"+
		modulePrefix+"protocol v0.0.0-20261023084050-3b52c8d267a0\n"+
		modulePrefix+"world v0.0.0-20261023214742-fbe46cd27dca\n)\n")
	return m
}

func TestWorldBridgeDependencyDirection(t *testing.T) {
	for _, tc := range []struct {
		name, body string
		accepted   bool
	}{
		{"own and world", "package worldbridge\nimport ( _ \"" + modulePrefix + "world\"; _ \"" + modulePrefix + "bridge\"; _ \"" + modulePrefix + "protocol\" )\n", true},
		{"gateway", "package worldbridge\nimport _ \"" + modulePrefix + "gateway\"\n", false},
		{"tagged foreign", "//go:build never_enabled\n\npackage worldbridge\nimport _ \"example.org/foreign\"\n", false},
		{"tagged invalid path", "//go:build never_enabled\n\npackage worldbridge\nimport _ \"" + modulePrefix + "world/../bridge\"\n", false},
		{"test foreign", "package worldbridge_test\nimport _ \"example.org/foreign\"\n", false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			root, p := fixture(t)
			m := adapterModule(t, root, &p)
			name := "probe.go"
			if tc.name == "test foreign" {
				name = "probe_test.go"
			}
			writeFixture(t, root, "engine/go/worldbridge/"+name, source("AGPL-3.0-or-later", tc.body))
			err := checkWorldBridgeDeps(root, m)
			if tc.accepted {
				if err != nil {
					t.Fatal(err)
				}
				return
			}
			expectRule(t, err, "I-3")
		})
	}
}

func TestWorldBridgeDependencyPins(t *testing.T) {
	for _, directive := range []string{
		"replace " + modulePrefix + "world => ../world",
		"exclude " + modulePrefix + "world v0.0.0-20261023214742-fbe46cd27dca",
		"tool example.org/foreign/cmd/tool",
	} {
		t.Run(strings.Fields(directive)[0], func(t *testing.T) {
			root, p := fixture(t)
			m := adapterModule(t, root, &p)
			path := filepath.Join(root, "engine/go/worldbridge/go.mod")
			data, err := os.ReadFile(path)
			if err != nil {
				t.Fatal(err)
			}
			writeFixture(t, root, "engine/go/worldbridge/go.mod", string(data)+directive+"\n")
			expectRule(t, checkWorldBridgeDeps(root, m), "I-3")
		})
	}
}

func gatewayModule(t *testing.T, root string, p *policy) modulePolicy {
	t.Helper()
	m := activeModule(t, p, "gateway")
	writeFixture(t, root, "engine/go/gateway/go.mod", "module "+modulePrefix+"gateway\n\ngo "+goVersion+"\nrequire (\n"+modulePrefix+"bridge v0.0.0-20261023154142-3ca081d3eba3\n"+modulePrefix+"protocol v0.0.0-20261023084050-3b52c8d267a0\n)\n")
	return m
}

func TestGatewayDependencyDirection(t *testing.T) {
	for _, tc := range []struct {
		path, body string
		accepted   bool
	}{
		{"root.go", "package gateway\nimport _ \"" + modulePrefix + "protocol\"\n", true},
		{"meaning/probe.go", "package meaning\nimport _ \"" + modulePrefix + "gateway\"\n", true},
		{"backend/probe.go", "package backend\nimport _ \"" + modulePrefix + "bridge\"\n", true},
		{"root.go", "package gateway\nimport _ \"" + modulePrefix + "bridge\"\n", false},
		{"meaning/probe.go", "package meaning\nimport _ \"" + modulePrefix + "bridge\"\n", false},
		{"meaning/probe.go", "package meaning\nimport _ \"" + modulePrefix + "gateway/fixture\"\n", false},
		{"backend/probe.go", "package backend\nimport _ \"" + modulePrefix + "gateway/gatewaytest\"\n", false},
		{"backend/probe.go", "package backend\nimport _ \"" + modulePrefix + "gateway/fixture\"\n", false},
		{"fixture/probe.go", "package fixture\nimport _ \"" + modulePrefix + "gateway/meaning\"\n", false},
		{"backend/probe.go", "package backend\nimport _ \"" + modulePrefix + "world\"\n", false},
		{"backend/probe_test.go", "package backend\nimport _ \"example.org/foreign\"\n", false},
		{"tagged.go", "//go:build never_enabled\n\npackage gateway\nimport _ \"" + modulePrefix + "gateway/meaning\"\n", false},
	} {
		t.Run(tc.path+fmt.Sprint(tc.accepted), func(t *testing.T) {
			root, p := fixture(t)
			m := gatewayModule(t, root, &p)
			writeFixture(t, root, "engine/go/gateway/"+tc.path, source("AGPL-3.0-or-later", tc.body))
			err := checkGatewayDeps(root, m)
			if tc.accepted {
				if err != nil {
					t.Fatal(err)
				}
			} else {
				expectRule(t, err, "I-3")
			}
		})
	}
}

func TestGatewayDependencyPins(t *testing.T) {
	for _, directive := range []string{"replace " + modulePrefix + "bridge => ../bridge", "exclude " + modulePrefix + "protocol v0.0.0-20261023084050-3b52c8d267a0", "tool example.org/foreign/cmd/tool", "require example.org/foreign v1.0.0"} {
		t.Run(strings.Fields(directive)[0], func(t *testing.T) {
			root, p := fixture(t)
			m := gatewayModule(t, root, &p)
			path := filepath.Join(root, "engine/go/gateway/go.mod")
			b, err := os.ReadFile(path)
			if err != nil {
				t.Fatal(err)
			}
			writeFixture(t, root, "engine/go/gateway/go.mod", string(b)+directive+"\n")
			expectRule(t, checkGatewayDeps(root, m), "I-3")
		})
	}
}
