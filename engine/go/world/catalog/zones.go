// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"encoding/xml"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

type zone struct {
	peace, pvp bool
	shapes     []zoneShape
}
type zoneShape struct {
	kind         string
	exclude      bool
	hasZ         bool
	zMin, zMax   int32
	x, y         []int32
	radiusSq     float64
	innerSq, out float64
}

func (zn zone) contains(x, y, z int32) bool {
	in := false
	for _, s := range zn.shapes {
		if !s.contains(x, y, z) {
			continue
		}
		if s.exclude {
			return false
		}
		in = true
	}
	return in
}

func (s zoneShape) contains(x, y, z int32) bool {
	if s.hasZ && (z < s.zMin || z > s.zMax) {
		return false
	}
	switch s.kind {
	case "Poly":
		inside := false
		for i, j := 0, len(s.x)-1; i < len(s.x); j, i = i, i+1 {
			if ((s.y[i] <= y && y < s.y[j]) || (s.y[j] <= y && y < s.y[i])) &&
				float64(x) < (float64(s.x[j])-float64(s.x[i]))*(float64(y)-float64(s.y[i]))/(float64(s.y[j])-float64(s.y[i]))+float64(s.x[i]) {
				inside = !inside
			}
		}
		return inside
	case "Rect":
		return x >= s.x[0] && x <= s.x[1] && y >= s.y[0] && y <= s.y[1]
	case "Cylinder":
		dx, dy := float64(x)-float64(s.x[0]), float64(y)-float64(s.y[0])
		return dx*dx+dy*dy <= s.radiusSq
	case "ExCylinder":
		dx, dy := float64(x)-float64(s.x[0]), float64(y)-float64(s.y[0])
		d := dx*dx + dy*dy
		return d <= s.out && d >= s.innerSq
	}
	return false
}
func (c *Catalog) loadZones(dir string) error {
	files, err := filepath.Glob(filepath.Join(dir, "*.xml"))
	if err != nil {
		return err
	}
	if len(files) == 0 {
		return fmt.Errorf("no zone XML in %s", dir)
	}
	for _, f := range files {
		raw, err := os.ReadFile(f)
		if err != nil {
			return err
		}
		var list xmlNode
		if err := xml.Unmarshal(raw, &list); err != nil {
			return fmt.Errorf("%s: %w", filepath.Base(f), err)
		}
		for _, n := range list.Children {
			if n.XMLName.Local != "zone" {
				continue
			}
			zn, keep, err := parseZone(n)
			if err != nil {
				return err
			}
			if keep {
				c.zones = append(c.zones, zn)
			}
		}
	}
	return nil
}

func parseZone(n xmlNode) (zone, bool, error) {
	if v, ok := n.attr("enabled"); ok && !javaBool(v) {
		return zone{}, false, nil
	}
	typ, _ := n.attr("type")
	var zn zone
	switch typ {
	case "Town":
		zn.peace = true
	case "Stadium", "Jail":
		zn.pvp = true
	}
	for _, ch := range n.Children {
		switch ch.XMLName.Local {
		case "settings":
			pvp, ok := ch.attr("pvp")
			if !ok {
				continue
			}
			switch strings.ToUpper(pvp) {
			case "PEACE":
				zn.peace = true
			case "ARENA":
				zn.pvp = true
			case "GENERAL":
			default:
				return zone{}, false, fmt.Errorf("pvp %q", pvp)
			}
		case "shape":
			s, err := parseShape(ch)
			if err != nil {
				return zone{}, false, err
			}
			zn.shapes = append(zn.shapes, s)
		}
	}
	if len(zn.shapes) == 0 {
		return zone{}, false, errors.New("no shape")
	}
	return zn, zn.peace || zn.pvp, nil
}
func parseShape(n xmlNode) (zoneShape, error) {
	num := func(name string) (int32, bool, error) {
		v, ok := n.attr(name)
		if !ok {
			return 0, false, nil
		}
		i, err := strconv.ParseInt(strings.TrimSpace(v), 10, 32)
		return int32(i), true, err
	}
	var s zoneShape
	s.kind, _ = n.attr("type")
	if v, ok := n.attr("exclude"); ok {
		s.exclude = javaBool(v)
	}
	zMin, okMin, err := num("zMin")
	if err != nil {
		return s, err
	}
	zMax, okMax, err := num("zMax")
	if err != nil {
		return s, err
	}
	if okMin != okMax || okMin && zMin > zMax {
		return s, errors.New("invalid zone height bounds")
	}
	s.hasZ, s.zMin, s.zMax = okMin && okMax, zMin, zMax
	for _, p := range n.Children {
		if p.XMLName.Local != "point" {
			continue
		}
		xs, _ := p.attr("x")
		ys, _ := p.attr("y")
		x, err := strconv.ParseInt(strings.TrimSpace(xs), 10, 32)
		if err != nil {
			return s, err
		}
		y, err := strconv.ParseInt(strings.TrimSpace(ys), 10, 32)
		if err != nil {
			return s, err
		}
		s.x, s.y = append(s.x, int32(x)), append(s.y, int32(y))
	}
	switch s.kind {
	case "Poly":
		if len(s.x) < 3 {
			return s, errors.New("a polygon of fewer than 3 points")
		}
	case "Rect":
		if len(s.x) != 2 {
			return s, errors.New("a rectangle of other than 2 points")
		}
		s.x = []int32{min(s.x[0], s.x[1]), max(s.x[0], s.x[1])}
		s.y = []int32{min(s.y[0], s.y[1]), max(s.y[0], s.y[1])}
	case "Cylinder":
		r, ok, err := num("radius")
		if err != nil || !ok || r <= 0 || len(s.x) != 1 {
			return s, errors.New("a cylinder without one centre and a radius")
		}
		s.radiusSq = float64(r) * float64(r)
	case "ExCylinder":
		in, okIn, err1 := num("innerRadius")
		out, okOut, err2 := num("outerRadius")
		if err1 != nil || err2 != nil || !okIn || !okOut || len(s.x) != 1 {
			return s, errors.New("an ex-cylinder without one centre and radii")
		}
		if in < 0 || out <= in {
			return s, errors.New("invalid annulus radii")
		}
		s.innerSq, s.out = float64(in)*float64(in), float64(out)*float64(out)
	default:
		return s, fmt.Errorf("shape %q", s.kind)
	}
	return s, nil
}

type ZoneState struct{ Peace, PvP bool }

func (c *Catalog) ZoneAt(x, y, z int32) ZoneState {
	var state ZoneState
	for _, zone := range c.zones {
		if zone.contains(x, y, z) {
			state.Peace = state.Peace || zone.peace
			state.PvP = state.PvP || zone.pvp
		}
	}
	if state.Peace {
		state.PvP = false
	}
	return state
}
