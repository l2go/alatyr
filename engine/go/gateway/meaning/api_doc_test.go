// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"go/ast"
	"go/parser"
	"go/token"
	"path/filepath"
	"strings"
	"testing"
)

func TestExportedAPIDocumentation(t *testing.T) {
	files, err := filepath.Glob("*.go")
	if err != nil {
		t.Fatal(err)
	}
	check := func(name string, doc *ast.CommentGroup) {
		if ast.IsExported(name) && (doc == nil || !strings.HasPrefix(doc.Text(), name+" ")) {
			t.Errorf("%s needs declaration documentation", name)
		}
	}
	for _, path := range files {
		if strings.HasSuffix(path, "_test.go") {
			continue
		}
		f, err := parser.ParseFile(token.NewFileSet(), path, nil, parser.ParseComments)
		if err != nil {
			t.Fatal(err)
		}
		for _, decl := range f.Decls {
			switch d := decl.(type) {
			case *ast.FuncDecl:
				check(d.Name.Name, d.Doc)
			case *ast.GenDecl:
				for _, spec := range d.Specs {
					doc := d.Doc
					switch s := spec.(type) {
					case *ast.TypeSpec:
						if s.Doc != nil {
							doc = s.Doc
						}
						check(s.Name.Name, doc)
						if st, ok := s.Type.(*ast.StructType); ok && ast.IsExported(s.Name.Name) {
							for _, field := range st.Fields.List {
								for _, name := range field.Names {
									check(name.Name, field.Doc)
								}
							}
						}
					case *ast.ValueSpec:
						if s.Doc != nil {
							doc = s.Doc
						}
						for _, name := range s.Names {
							check(name.Name, doc)
						}
					}
				}
			}
		}
	}
	// The four changed contracts document both failure and the runtime policy.
	f, err := parser.ParseFile(token.NewFileSet(), "state.go", nil, parser.ParseComments)
	if err != nil {
		t.Fatal(err)
	}
	for _, name := range []string{"Start", "Confirmed", "Witness", "CandidateValid"} {
		found := false
		for _, decl := range f.Decls {
			if d, ok := decl.(*ast.FuncDecl); ok && d.Name.Name == name {
				found = true
				text := d.Doc.Text()
				for _, term := range []string{"ErrValue", "runtime closes", "internal reason", "logging", "error text"} {
					if !strings.Contains(text, term) {
						t.Errorf("%s documentation omits %q", name, term)
					}
				}
			}
		}
		if !found {
			t.Errorf("missing %s", name)
		}
	}
}
