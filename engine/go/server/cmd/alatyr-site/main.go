// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"html/template"
	"io/fs"
	"os"
	"path"
	"path/filepath"
	"slices"
	"strings"

	"github.com/l2go/alatyr/engine/go/gateway/meaning"
)

// base is the site's path on its host.
const base = "/alatyr/"

// Note is one engineering note, listed from site/notes.json.
type Note struct {
	Slug    string `json:"slug"`
	Title   string `json:"title"`
	Summary string `json:"summary"`
	Date    string `json:"date"`
	Cover   string `json:"cover"`
	// Archive marks a note of the Java years, kept as published.
	Archive bool `json:"archive,omitempty"`
}

// Data is what every page template receives.
type Data struct {
	Path     string
	Notes    []Note
	Note     Note
	Releases []Release
	Verbs    []meaning.Verb
	Kinds    []meaning.Kind
}

func main() {
	root := flag.String("root", ".", "repository root")
	out := flag.String("out", "_site", "output directory")
	flag.Parse()
	if err := build(*root, *out); err != nil {
		fmt.Fprintln(os.Stderr, "alatyr-site:", err)
		os.Exit(1)
	}
}

func build(root, out string) error {
	site := filepath.Join(root, "site")
	if err := os.RemoveAll(out); err != nil {
		return err
	}
	if err := os.CopyFS(out, os.DirFS(filepath.Join(site, "static"))); err != nil {
		return err
	}
	data := Data{Verbs: meaning.Verbs(), Kinds: meaning.Kinds()}
	raw, err := os.ReadFile(filepath.Join(site, "notes.json"))
	if err != nil {
		return err
	}
	if err := json.Unmarshal(raw, &data.Notes); err != nil {
		return fmt.Errorf("notes.json: %w", err)
	}
	changelog, err := os.ReadFile(filepath.Join(root, "CHANGELOG.md"))
	if err != nil {
		return err
	}
	if data.Releases, err = parseChangelog(string(changelog)); err != nil {
		return err
	}
	layout := filepath.Join(site, "layout.html")
	var routes []string
	pages, err := filepath.Glob(filepath.Join(site, "pages", "*.html"))
	if err != nil {
		return err
	}
	for _, page := range pages {
		name := strings.TrimSuffix(filepath.Base(page), ".html")
		route := name + "/"
		if name == "index" {
			route = ""
		}
		d := data
		d.Path = base + route
		if err := render(out, route, d, layout, page); err != nil {
			return err
		}
		routes = append(routes, route)
	}
	for _, n := range data.Notes {
		route := "notes/" + n.Slug + "/"
		routes = append(routes, route)
		if n.Archive {
			if _, err := os.Stat(filepath.Join(out, route, "index.html")); err != nil {
				return fmt.Errorf("archived note %s: %w", n.Slug, err)
			}
			continue
		}
		d := data
		d.Path, d.Note = base+route, n
		if err := render(out, route, d, layout, filepath.Join(site, "note.html"), filepath.Join(site, "notes", n.Slug+".html")); err != nil {
			return err
		}
	}
	routes = append(routes, "java-years/")
	if err := writeWorld(filepath.Join(root, "world"), filepath.Join(out, "live", "world.zip")); err != nil {
		return err
	}
	return writeSitemap(filepath.Join(out, "sitemap.xml"), routes)
}

func render(out, route string, d Data, files ...string) error {
	t, err := template.New("layout.html").ParseFiles(files...)
	if err != nil {
		return err
	}
	var buf bytes.Buffer
	if err := t.ExecuteTemplate(&buf, "layout.html", d); err != nil {
		return fmt.Errorf("%s: %w", files[len(files)-1], err)
	}
	dir := filepath.Join(out, filepath.FromSlash(route))
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return err
	}
	return os.WriteFile(filepath.Join(dir, "index.html"), buf.Bytes(), 0o644)
}

// writeWorld packs the rendered world the demo loads: its passport, license,
// catalog and data. The authoring sources stay out.
func writeWorld(world, dst string) error {
	if err := os.MkdirAll(filepath.Dir(dst), 0o755); err != nil {
		return err
	}
	var buf bytes.Buffer
	zw := zip.NewWriter(&buf)
	fsys := os.DirFS(world)
	err := fs.WalkDir(fsys, ".", func(name string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		top, _, _ := strings.Cut(name, "/")
		keep := name == "." || slices.Contains([]string{"world.properties", "LICENSE", "catalog", "data"}, top)
		if !keep {
			if d.IsDir() {
				return fs.SkipDir
			}
			return nil
		}
		if d.IsDir() {
			return nil
		}
		raw, err := fs.ReadFile(fsys, name)
		if err != nil {
			return err
		}
		w, err := zw.Create(name)
		if err != nil {
			return err
		}
		_, err = w.Write(raw)
		return err
	})
	if err != nil {
		return err
	}
	if err := zw.Close(); err != nil {
		return err
	}
	return os.WriteFile(dst, buf.Bytes(), 0o644)
}

func writeSitemap(dst string, routes []string) error {
	slices.Sort(routes)
	var b strings.Builder
	b.WriteString(`<?xml version="1.0" encoding="UTF-8"?>` + "\n" + `<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">` + "\n")
	for _, r := range routes {
		fmt.Fprintf(&b, "  <url><loc>https://l2go.github.io%s</loc></url>\n", path.Join(base, r)+suffix(r))
	}
	b.WriteString("</urlset>\n")
	return os.WriteFile(dst, []byte(b.String()), 0o644)
}

func suffix(route string) string {
	if route == "" || strings.HasSuffix(route, "/") {
		return "/"
	}
	return ""
}
