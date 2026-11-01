// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"fmt"

	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

// checkNPCForms compares visible NPC appearance facts with the captured
// template. It does not invent a label from a name, role or template id.
func checkNPCForms(records []wire.Record, scene world.Scene) error {
	npcs := map[int32]world.NPCView{}
	visible := map[int32]bool{scene.Self.Entity.ID: true}
	for _, o := range scene.Objects {
		visible[o.Entity.ID] = true
		if n, ok := o.State.(world.NPCSeen); ok {
			npcs[n.NPC.Entity.ID] = n.NPC
		}
	}
	for _, r := range records {
		if r.Type != wire.Fact {
			continue
		}
		kind := gatewaytest.U(r, 1)
		role, _ := wire.Find(r.Fields, 11)
		ref, _ := wire.Find(r.Fields, 10)
		id, _ := wire.Find(ref.Fields, 1)
		form, present := wire.Find(r.Fields, wire.FormField)
		roleName := string(role.Data)
		if roleName == "player" || roleName == "drop" {
			if present {
				return fmt.Errorf("form on %s %d", roleName, id.S)
			}
			continue
		}
		if kind != kindAppeared {
			if present {
				return fmt.Errorf("form on non-appearance fact %d", kind)
			}
			continue
		}
		npc, isNPC := npcs[int32(id.S)]
		if !isNPC {
			if !visible[int32(id.S)] {
				continue // A departed creature's historical form was checked while it was visible.
			}
			if present {
				return fmt.Errorf("form on non-NPC %d", id.S)
			}
			continue
		}
		want := npc.Template.Form
		if want == "" {
			if present {
				return fmt.Errorf("npc %d has a form field for an unset template", id.S)
			}
			continue
		}
		if !present || string(form.Data) != want {
			return fmt.Errorf("npc %d form = %q, template %q", id.S, form.Data, want)
		}
	}
	return nil
}
