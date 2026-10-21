// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package wire

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"testing"
)

type frozenVector struct {
	id   string
	data []byte
}

// loadFrozenVectors verifies the historical inputs before tests or fuzz seeds use them.
func loadFrozenVectors(t testing.TB) []frozenVector {
	t.Helper()
	data, err := os.ReadFile("../testdata/vectors.json")
	if err != nil {
		t.Fatal(err)
	}
	var fixture struct{ Vectors []struct{ ID, Hex string } }
	if err := json.Unmarshal(data, &fixture); err != nil {
		t.Fatal(err)
	}
	want := [...]struct{ id, digest string }{
		{"V01", "a81032f4c7cb2ff28379c1faa67ae37ba8be55fd6518ad99dc9bb1fdb0a516ce"},
		{"V02", "3b2c0aa68d31604f1bbda71c4b662708eb3f48501c7e5bac8205ee5e29bd2179"},
		{"V03", "12d66414a297c93c861c39473e2c0e1759fcdcee15292d1f5eb68ec11718b537"},
		{"V04", "38ebc48abf73634c3293fb9253e7bcbb764a11e13e38511ab6eb11206440d837"},
		{"V05", "835ee63c60744aeb310539361811c665b4c761d066c92c5c5a1399121b219d21"},
		{"V06", "214a26567645fe0e51af38dc62865c95d48473eacb116fc704943dcfdf9cde53"},
		{"V07", "2d34f240584a03e7364662a597ef94be2a0c64a6efc88391a03cc2e725751319"},
		{"V08", "52c5d10d1300cb24f097ac7d5f54b45730a0ca105b2c0a5f310ed34483f0dfc7"},
		{"V09", "7fa47851e551640f781278842f19cb7bf862177879b83858449f21d597746b9f"},
		{"V10", "bd8c50a917d0fe59aca384afb277f06a04e1582a13148cbf91ba3fe8a1bda0ac"},
		{"V11", "08da7c45cb204377e7e42249cda5713fa865116ddbb4cb5a1949b2e5b438a6ab"},
		{"V12", "486934517ff4aa8f7fcb7c761e088977c51ee146f5fc581670610e4d20d529b5"},
		{"V13", "d1b2e85831fd79f9cb8488db7a3ef485dcb6ba24b8c9c1da4d3d5d295e153ec1"},
		{"V14", "d4b2d6a7faede382c5f2295bdf7c31fa945ee379e19984ed5c04fdbff350cf60"},
		{"V15", "93b75ef2b6c777f9a4e037572576a8d085a5ac67676bf4657914b932d0cdc2a7"},
		{"V16", "b407f1b7448a23216a035718459eef8a42533d61969a3b9ea65d06ebcc648757"},
		{"V17", "7c05bbfb05bd9324ba7d0a58fdffcdffb284ee5c05e2cc416fbedc8fbbb3d364"},
		{"V18", "218f6dc0a0bab83a9f388bf376c447731428d68d420f3fc93230a670e2ec3404"},
		{"V19", "cb116f45656dc80588a048e486c51083eb1641526ac7cfe3cc8c4d1f460ef38a"},
		{"V20", "9a7ba7565a7d68d30dfed7ce6b39a2871c5ba5e024c8db45dfe9312affca21d6"},
		{"V21", "a8e773690eb56edd1780e1b85524dfbaca2b1f56cd9bdc4ee098b45204cfb11e"},
		{"V22", "75850912ad0edc1de570350997c6bd77715303fc66262fd10d833716baee8052"},
		{"V23", "af36abae946444fba12ec90e658a2d18870d8d7aaf2d8bb45f4cd4d147d1c310"},
		{"V24", "1b0eb10f8931d229cf836fac019536dba2f0b3000b7ba79c71a5187a4a515412"},
		{"V25", "e341461d9ef1b3d06e8f106eea7b6debc2c2b5a9aa4c5c6bec10bf589b17b897"},
		{"V26", "e43372baea8874416bb49fb3813f6d653026b8737b80d719d8b37e810bcb0d5b"},
		{"V27", "f11e750376b2a8aaffeb014b81bfdb0dab43c4bd4cce6215135cd94a762742c6"},
		{"V28", "f13b24b3bab46b41d2b6e5c03695d9de0e5d0ee961b1218a4ece04286688cc04"},
	}
	if len(fixture.Vectors) != len(want) {
		t.Fatalf("historical input count: got %d, want %d", len(fixture.Vectors), len(want))
	}
	seen := make(map[string]bool, len(want))
	vectors := make([]frozenVector, 0, len(want))
	for i, v := range fixture.Vectors {
		if seen[v.ID] {
			t.Fatalf("duplicate historical input %s", v.ID)
		}
		seen[v.ID] = true
		if v.ID != want[i].id {
			t.Fatalf("historical input order at %d: got %s, want %s", i, v.ID, want[i].id)
		}
		input, err := hex.DecodeString(strings.ReplaceAll(v.Hex, " ", ""))
		if err != nil {
			t.Fatalf("%s: invalid hex: %v", v.ID, err)
		}
		if digest := fmt.Sprintf("%x", sha256.Sum256(input)); digest != want[i].digest {
			t.Fatalf("%s: historical input bytes changed", v.ID)
		}
		vectors = append(vectors, frozenVector{id: v.ID, data: input})
	}
	return vectors
}

func TestFrozenVectorIntegrity(t *testing.T) {
	loadFrozenVectors(t)
}
