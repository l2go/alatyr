// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestDatabaseURLSources(t *testing.T) {
	t.Setenv("ALATYR_DATABASE_URL", "")
	t.Setenv("ALATYR_DATABASE_URL_FILE", "")
	file := filepath.Join(t.TempDir(), "database_url")
	for _, tc := range []struct {
		name, value, fileContent string
		useFile, wantError       bool
	}{
		{name: "missing", wantError: true},
		{name: "environment", value: "postgres://user:secret@db/alatyr"},
		{name: "file", fileContent: "postgres://user:secret@db/alatyr\n", useFile: true},
		{name: "empty-file", useFile: true, wantError: true},
		{name: "oversized-file", useFile: true, fileContent: strings.Repeat("x", 16385), wantError: true},
		{name: "ambiguous", value: "postgres://user:secret@db/alatyr", useFile: true, fileContent: "other", wantError: true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			t.Setenv("ALATYR_DATABASE_URL", tc.value)
			t.Setenv("ALATYR_DATABASE_URL_FILE", "")
			if tc.useFile {
				if err := os.WriteFile(file, []byte(tc.fileContent), 0600); err != nil {
					t.Fatal(err)
				}
				t.Setenv("ALATYR_DATABASE_URL_FILE", file)
			}
			got, err := databaseURL()
			if (err != nil) != tc.wantError {
				t.Fatalf("error=%v, wantError=%v", err, tc.wantError)
			}
			if !tc.wantError && got != "postgres://user:secret@db/alatyr" {
				t.Fatal("URL source did not resolve")
			}
			if err != nil && (strings.Contains(err.Error(), "secret") || strings.Contains(err.Error(), file)) {
				t.Fatal("URL diagnostics disclosed credentials or file")
			}
		})
	}
}

func TestHealthcheckReadiness(t *testing.T) {
	for _, code := range []int{200, 503} {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(code) }))
		err := healthcheck(server.URL)
		server.Close()
		if (err == nil) != (code == 200) {
			t.Fatalf("health status %d: %v", code, err)
		}
		if err := healthcheck(server.URL); err == nil {
			t.Fatal("closed endpoint healthy")
		}
	}
}

func TestContainerHTTPRequiresExplicitProxyTrust(t *testing.T) {
	cfg := configTest()
	cfg.HTTPAddress = "0.0.0.0:8080"
	cfg.ProxyHosts = []string{"play.example"}
	cfg.ProxyOrigins = []string{"https://play.example"}
	cfg.ProxyPeers = []string{"192.0.2.1"}
	if _, err := cfg.validate(); err != nil {
		t.Fatal(err)
	}
	cfg.ProxyPeers = nil
	if _, err := cfg.validate(); err == nil {
		t.Fatal("wildcard HTTP without trusted peer accepted")
	}
	cfg.ProxyPeers = []string{"192.0.2.1"}
	cfg.StreamAddress = "0.0.0.0:8081"
	if _, err := cfg.validate(); err == nil {
		t.Fatal("public plaintext stream accepted")
	}
}
