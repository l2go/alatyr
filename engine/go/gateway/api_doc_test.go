// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"go/ast"
	"go/parser"
	"go/token"
	"strings"
	"testing"
)

func TestPolicyAndSummaryFieldsHaveDocumentation(t *testing.T) {
	f, err := parser.ParseFile(token.NewFileSet(), "model.go", nil, parser.ParseComments)
	if err != nil {
		t.Fatal(err)
	}
	found := 0
	ast.Inspect(f, func(n ast.Node) bool {
		spec, ok := n.(*ast.TypeSpec)
		if !ok || (spec.Name.Name != "Config" && spec.Name.Name != "Action" && spec.Name.Name != "Summary") {
			return true
		}
		found++
		st := spec.Type.(*ast.StructType)
		for _, field := range st.Fields.List {
			for _, name := range field.Names {
				if field.Doc == nil || !strings.HasPrefix(field.Doc.Text(), name.Name+" ") {
					t.Errorf("%s.%s needs its own field documentation", spec.Name.Name, name.Name)
				}
			}
		}
		return false
	})
	if found != 3 {
		t.Fatalf("documented structs = %d, want 3", found)
	}
}
