// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package wire

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"io"
	"os"
	"strings"
	"testing"
)

// The bridge/1 fixtures are Apache-2.0 conformance data under this module's LICENSE.
// This test checks the shared file only. The core does not decode that profile.
func TestBridgeFixtureIntegrity(t *testing.T) {
	const path = "../testdata/bridge-v1/vectors.json"
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(data)
	const wantSHA = "de3d5c23c4250ee1eceaafe942f5714338c1b79043aa713ece9912dd0d9f22bc"
	if len(data) != 3269 || hex.EncodeToString(sum[:]) != wantSHA {
		t.Fatalf("fixture bytes changed: %d bytes", len(data))
	}

	dec := json.NewDecoder(bytes.NewReader(data))
	dec.DisallowUnknownFields()
	var fixture struct {
		Version int    `json:"version"`
		Profile string `json:"profile"`
		License string `json:"license"`
		Vectors []struct {
			ID        string `json:"id"`
			Transport string `json:"transport"`
			Hex       string `json:"hex"`
			Expected  string `json:"expected"`
		} `json:"vectors"`
	}
	if err := dec.Decode(&fixture); err != nil {
		t.Fatal(err)
	}
	var extra any
	if err := dec.Decode(&extra); err != io.EOF {
		t.Fatal("fixture has trailing data")
	}
	if fixture.Version != 1 || fixture.Profile != "bridge/1" || fixture.License != "Apache-2.0" {
		t.Fatal("fixture header changed")
	}

	want := [...]struct{ id, transport, expected, digest string }{
		{"BV01", "frame", "ok", "84578cbde18e9dbd54e43d04f7e864aaf95c318621684cba6e98308a08ccc363"},
		{"BV02", "frame", "ok", "5fe588dbd5ef08505da13073d4f40bb7098ea6b1f7e9a8b59d7c08086c97487d"},
		{"BV03", "frame", "ok", "f1d77a2e76a78472e807068c77c554fd12015ad63e5e657aab30b1ccf7d6fcb2"},
		{"BV04", "frame", "ok", "40281c2035a896b9b3e87710549db8c99ecdf19e68d791e3c71acd1d5e9a0354"},
		{"BV05", "frame", "ok", "915ced4a33da32f93758c54c711185068e05d7575869ea3d457ce1ce5e86a983"},
		{"BV06", "frame", "ok", "2eed051f0c88afd26001109470cbbb581a18f970e8dcfd91ec51d99d459f8842"},
		{"BV07", "frame", "ok", "662c272b4ee37d8974964b96996fd965db363ddede00f715df9dedc3b0b7a463"},
		{"BV08", "frame", "frame", "064f2156b6f8cb11d1dc3e5434b8e8e849c49c467b32fd091751d5e57c393b10"},
		{"BV09", "stream", "ok", "39f293671ee0e9abc2227a11c22f2c75a46b3eb6e7ab2799324c2b68c524c8e8"},
		{"BV10", "frame", "unsupported", "871269896d3d39596d9ff423e3c462336a96adee308fe1ba34830d0587a01b5d"},
		{"BV11", "frame", "ok", "4a6edddd686470bb1e20b1a3f67d7b6ed2accd25c4fc5374fd83ff43c67636bf"},
		{"BV12", "frame", "ok", "5a4a920a56ebe087e5540bab149d4508c834aa6fbb90fd66913089aa4e722b31"},
		{"BV13", "frame", "frame", "2971be652d98a0fec6ce7b8a5bf70196cd19b7696bc5f6dcd6f9ee08ad4c04c2"},
		{"BV14", "frame", "frame", "bbab44a4343b07277b2dc118b350a37e1a6ad97daa7c2839a2d75a06e6f4b242"},
		{"BV15", "frame", "frame", "02b855e30ba887630433e81cbdd29b3f947110f555756da3261c9d703c3b456d"},
		{"BV16", "frame", "ok", "f4998c2a2a9607591b660ecff5e8680f2f4f60e79f205e0f7f0fbb2771560179"},
	}
	if len(fixture.Vectors) != len(want) {
		t.Fatalf("fixture count: got %d, want %d", len(fixture.Vectors), len(want))
	}
	seen := make(map[string]bool, len(want))
	for i, vector := range fixture.Vectors {
		if seen[vector.ID] {
			t.Fatalf("duplicate fixture %s", vector.ID)
		}
		seen[vector.ID] = true
		if vector.ID != want[i].id || vector.Transport != want[i].transport || vector.Expected != want[i].expected {
			t.Fatalf("fixture %d: id, transport, or category changed", i)
		}
		raw, err := hex.DecodeString(strings.ReplaceAll(vector.Hex, " ", ""))
		if err != nil {
			t.Fatalf("%s: invalid hex: %v", vector.ID, err)
		}
		digest := sha256.Sum256(raw)
		if hex.EncodeToString(digest[:]) != want[i].digest {
			t.Fatalf("%s: input bytes changed", vector.ID)
		}
	}
}
