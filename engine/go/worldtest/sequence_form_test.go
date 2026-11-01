// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func TestSequenceFormOracleRejectsMutations(t *testing.T) {
	scene := world.Scene{Self: world.Self{Entity: world.Ref{ID: 1, Gen: 1}}, Objects: []world.Observation{{Entity: world.Ref{ID: 7, Gen: 1}, State: world.NPCSeen{NPC: world.NPCView{Entity: world.Ref{ID: 7, Gen: 1}, ObjectID: 7, Template: catalog.NPCTemplate{ID: 20120, Form: "field_wolf"}}}}}}
	for _, name := range []string{"match", "missing", "mismatch", "unset", "player", "drop", "moved", "departed"} {
		t.Run(name, func(t *testing.T) {
			local := scene
			role := "creature"
			kind := uint64(kindAppeared)
			id := int64(7)
			token := "field_wolf"
			include := true
			switch name {
			case "missing":
				include = false
			case "mismatch":
				token = "field_spider"
			case "unset":
				local.Objects[0].State = world.NPCSeen{NPC: world.NPCView{Entity: world.Ref{ID: 7, Gen: 1}, ObjectID: 7, Template: catalog.NPCTemplate{ID: 20120}}}
				include = false
			case "player":
				role = "player"
				id = 1
			case "drop":
				role = "drop"
				id = 9
			case "moved":
				kind = kindMoved
			case "departed":
				id = 8
				token = "field_spider"
			}
			r := wire.Record{Type: wire.Fact, Fields: []wire.Field{wire.U(1, true, kind), wire.Obj(10, true, []wire.Field{wire.S(1, true, id), wire.U(2, true, 1)}), wire.Data(11, true, role)}}
			if include {
				r.Fields = append(r.Fields, wire.Data(wire.FormField, false, token))
			}
			err := checkNPCForms([]wire.Record{r}, local)
			wantOK := name == "match" || name == "unset" || name == "departed"
			if (err == nil) != wantOK {
				t.Fatalf("%s: %v", name, err)
			}
		})
	}
}
