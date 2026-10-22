// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"encoding/xml"
	"errors"
	"fmt"
	"math"
	"os"
	"path/filepath"
	"slices"
	"strconv"
)

type mapRegion struct {
	restartID  int32
	special    bool
	xs, ys     []int32
	xMin, xMax int32
	yMin, yMax int32
	zMin, zMax int32
}

func (r mapRegion) contains(x, y, z int32) bool {
	if !r.special {
		return r.xMin <= x && x <= r.xMax && r.yMin <= y && y <= r.yMax
	}
	if z != -1 && (z < r.zMin || r.zMax < z) {
		return false
	}
	if x < r.xMin || r.xMax < x || y < r.yMin || r.yMax < y {
		return false
	}
	cn := 0
	for i := 0; i < len(r.xs)-1; i++ {
		if (r.ys[i] <= y && r.ys[i+1] > y) || (r.ys[i] > y && r.ys[i+1] <= y) {
			vt := float64(int64(y)-int64(r.ys[i])) / float64(int64(r.ys[i+1])-int64(r.ys[i]))
			if float64(x) < float64(r.xs[i])+vt*float64(int64(r.xs[i+1])-int64(r.xs[i])) {
				cn++
			}
		}
	}
	return cn&1 == 1
}

type Restart struct {
	ID              int32
	Normal, Chaotic []Point
}

// RestartAt reports only an explicitly authored region. There is no fallback,
// inferred race redirection or automatic use by the running world.
func (c *Catalog) RestartAt(x, y, z int32) (Restart, bool) {
	for _, special := range []bool{true, false} {
		for _, region := range c.mapRegions {
			if region.special == special && region.contains(x, y, z) {
				v, ok := c.restarts[region.restartID]
				v.Normal = slices.Clone(v.Normal)
				v.Chaotic = slices.Clone(v.Chaotic)
				return v, ok
			}
		}
	}
	return Restart{}, false
}
func (c *Catalog) loadMapRegions(dir string) error {
	c.restarts = map[int32]Restart{}
	files, err := filepath.Glob(filepath.Join(dir, "*.xml"))
	if err != nil {
		return err
	}
	var special, areas []mapRegion
	num := func(n xmlNode, key string) (int32, error) {
		text, ok := n.attr(key)
		if !ok {
			return 0, fmt.Errorf("map region without %s", key)
		}
		value, err := strconv.ParseInt(text, 10, 32)
		return int32(value), err
	}
	for _, file := range files {
		raw, err := os.ReadFile(file)
		if err != nil {
			return err
		}
		var root xmlNode
		if err := xml.Unmarshal(raw, &root); err != nil {
			return err
		}
		if root.XMLName.Local != "mapregion" {
			return errors.New("expected mapregion XML")
		}
		for _, section := range root.Children {
			for _, node := range section.Children {
				id, err := num(node, "restartId")
				if err != nil {
					return err
				}
				switch node.XMLName.Local {
				case "restartpoint":
					if _, dup := c.restarts[id]; dup {
						return errors.New("duplicate restart point")
					}
					restart := Restart{ID: id}
					for _, point := range node.Children {
						if point.XMLName.Local != "normal" && point.XMLName.Local != "chaotic" {
							return errors.New("unsupported restart redirection")
						}
						x, e1 := num(point, "X")
						y, e2 := num(point, "Y")
						z, e3 := num(point, "Z")
						if err := errors.Join(e1, e2, e3); err != nil {
							return err
						}
						if point.XMLName.Local == "normal" {
							restart.Normal = append(restart.Normal, Point{x, y, z})
						} else {
							restart.Chaotic = append(restart.Chaotic, Point{x, y, z})
						}
					}
					if len(restart.Normal) == 0 {
						return errors.New("restart without normal points")
					}
					c.restarts[id] = restart
				case "region":
					region := mapRegion{restartID: id, special: true, xMin: math.MaxInt32, xMax: math.MinInt32, yMin: math.MaxInt32, yMax: math.MinInt32}
					region.zMin, err = num(node, "zMin")
					if err != nil {
						return err
					}
					region.zMax, err = num(node, "zMax")
					if err != nil {
						return err
					}
					if region.zMin > region.zMax {
						return errors.New("invalid map region height")
					}
					for _, point := range node.Children {
						if point.XMLName.Local != "point" {
							return errors.New("unsupported per-race restart")
						}
						x, e1 := num(point, "X")
						y, e2 := num(point, "Y")
						if err := errors.Join(e1, e2); err != nil {
							return err
						}
						region.xs = append(region.xs, x)
						region.ys = append(region.ys, y)
						region.xMin = min(region.xMin, x)
						region.xMax = max(region.xMax, x)
						region.yMin = min(region.yMin, y)
						region.yMax = max(region.yMax, y)
					}
					if len(region.xs) < 3 {
						return errors.New("map polygon needs three points")
					}
					region.xs = append(region.xs, region.xs[0])
					region.ys = append(region.ys, region.ys[0])
					special = append(special, region)
				case "restartarea":
					for _, point := range node.Children {
						x, e1 := num(point, "X")
						y, e2 := num(point, "Y")
						if err := errors.Join(e1, e2); err != nil {
							return err
						}
						xmin := (int64(x)-15)*32768 - 164608
						ymin := (int64(y)-10)*32768 - 262144
						if xmin < math.MinInt32 || xmin+32768 > math.MaxInt32 || ymin < math.MinInt32 || ymin+32768 > math.MaxInt32 {
							return errors.New("map square out of coordinate range")
						}
						areas = append(areas, mapRegion{restartID: id, xMin: int32(xmin), xMax: int32(xmin + 32768), yMin: int32(ymin), yMax: int32(ymin + 32768)})
					}
				default:
					return errors.New("unsupported map region node")
				}
			}
		}
	}
	c.mapRegions = append(special, areas...)
	for _, region := range c.mapRegions {
		if _, ok := c.restarts[region.restartID]; !ok {
			return errors.New("map region without restart point")
		}
	}
	return nil
}
