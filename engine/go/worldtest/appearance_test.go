// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"context"
	"encoding/csv"
	"fmt"
	"os"
	"path/filepath"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/worldbridge"
)

// The shipped world authors one fighter template. Add detached test templates
// with explicit mage metadata and absent metadata; never infer kind from IDs.
func appearanceCatalog(t *testing.T) *catalog.Catalog {
	t.Helper()
	root := t.TempDir()
	if err := os.CopyFS(root, os.DirFS(filepath.Join("..", "..", "..", "world"))); err != nil {
		t.Fatal(err)
	}
	for _, name := range []string{"player_template.csv", "level_up_gain.csv"} {
		path := filepath.Join(root, "catalog", name)
		f, err := os.Open(path)
		if err != nil {
			t.Fatal(err)
		}
		rows, err := csv.NewReader(f).ReadAll()
		closeErr := f.Close()
		if err != nil || closeErr != nil {
			t.Fatalf("read %s: %v/%v", name, err, closeErr)
		}
		for _, id := range []string{"10", "11"} {
			row := append([]string(nil), rows[1]...)
			row[0] = id
			rows = append(rows, row)
		}
		f, err = os.Create(path)
		if err != nil {
			t.Fatal(err)
		}
		w := csv.NewWriter(f)
		w.WriteAll(rows)
		err = w.Error()
		closeErr = f.Close()
		if err != nil || closeErr != nil {
			t.Fatalf("write %s: %v/%v", name, err, closeErr)
		}
	}
	f, err := os.OpenFile(filepath.Join(root, "world.properties"), os.O_APPEND|os.O_WRONLY, 0600)
	if err != nil {
		t.Fatal(err)
	}
	_, err = f.WriteString("\nworld.class.10.kind=mage\n")
	closeErr := f.Close()
	if err != nil || closeErr != nil {
		t.Fatalf("class metadata: %v/%v", err, closeErr)
	}
	c, err := catalog.Load(root)
	if err != nil {
		t.Fatal(err)
	}
	return c
}

func assertPlayerAppearance(t *testing.T, records []wire.Record, id int32, want meaning.Appearance, kinds ...string) {
	t.Helper()
	for _, kind := range kinds {
		found := false
		for _, record := range records {
			if record.Type != wire.Fact {
				continue
			}
			d, err := meaning.EventValue(record)
			if err != nil {
				t.Fatal(err)
			}
			if d.Event.ID != id || d.Event.Kind != kind {
				continue
			}
			// Partial health reports carry no new appearance; a full self
			// description must supply at least one matching appearance below.
			if kind == meaning.Vitals && d.Event.Appearance == nil {
				continue
			}
			if d.Event.Appearance == nil || *d.Event.Appearance != want {
				t.Fatalf("%s/%d appearance=%+v want %+v", kind, id, d.Event.Appearance, want)
			}
			v, present := wire.Find(record.Fields, wire.AppearanceField)
			if !present || len(v.Fields) != 3 {
				t.Fatal("appearance missing or exposed extra metadata")
			}
			found = true
		}
		if !found {
			t.Fatalf("missing %s appearance for %d", kind, id)
		}
	}
}

func TestG12WorldAppearanceMatrix(t *testing.T) {
	cat := appearanceCatalog(t)
	races := []wire.Race{wire.RaceHuman, wire.RaceElf, wire.RaceDarkElf, wire.RaceKruvan, wire.RaceDwarf}
	for race, wantRace := range races {
		for _, female := range []bool{false, true} {
			for _, class := range []struct {
				id     int32
				branch wire.Branch
			}{{0, wire.BranchFighter}, {10, wire.BranchMystic}, {11, wire.BranchUnknown}} {
				t.Run(fmt.Sprintf("%d/%t/%d", race, female, class.id), func(t *testing.T) {
					synctest.Test(t, func(t *testing.T) {
						char := readyCharacter()
						char.Race = int32(race)
						char.Female = female
						char.ClassID = class.id
						want := meaning.Appearance{Race: wantRace, Sex: wire.SexMale, Branch: class.branch}
						if female {
							want.Sex = wire.SexFemale
						}
						r := newRig(t, cat, char, allSources(), nil)
						assertPlayerAppearance(t, r.p.Records, char.ObjectID, want, meaning.Appeared, meaning.Vitals)
						before := len(r.p.Records)
						if err := r.w.SetRunning(t.Context(), char.ObjectID, false); err != nil {
							t.Fatal(err)
						}
						r.sync()
						assertPlayerAppearance(t, r.p.Records[before:], char.ObjectID, want, meaning.Appeared)
						for _, record := range r.p.Records {
							if record.Type != wire.Fact {
								continue
							}
							d, err := meaning.EventValue(record)
							if err != nil {
								t.Fatal(err)
							}
							if d.Event.Kind == meaning.Appeared && d.Event.Role != meaning.RolePlayer && d.Event.Appearance != nil {
								t.Fatal("NPC received player appearance")
							}
						}
					})
				})
			}
		}
	}
}

func TestWorldAppearanceVisibilityAndResume(t *testing.T) {
	cat := appearanceCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		char := readyCharacter()
		char.Race = 3
		char.Female = true
		char.ClassID = 10
		want := meaning.Appearance{Race: wire.RaceKruvan, Sex: wire.SexFemale, Branch: wire.BranchMystic}
		r := newRig(t, cat, char, allSources(), nil)
		before := len(r.p.Records)
		r.p.Send(r.intent(1, verbUseItem, wire.S(21, true, 11)))
		r.expect(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		r.advance(200 * time.Millisecond)
		assertPlayerAppearance(t, r.p.Records[before:], char.ObjectID, want, meaning.Appeared, meaning.Vitals)
		visitor := readyCharacter()
		visitor.ObjectID = 2
		visitor.Account = "visitor"
		visitor.Name = "Visitor"
		visitor.Items = nil
		visitor.Skills = nil
		visitor.Race = 4
		origin := time.Now()
		raw, snap, err := worldbridge.Open(t.Context(), worldbridge.Config{World: r.w, Catalog: cat, Load: func(context.Context, bridge.Identity) (world.Character, error) { return visitor, nil }}, bridge.Identity{Account: visitor.Account, Character: visitor.Name})
		if err != nil {
			t.Fatal(err)
		}
		session, scope, state, err := backend.New(raw, snap, backend.Config{Meaning: r.c, Origin: origin})
		if err != nil {
			t.Fatal(err)
		}
		g := gatewaytest.New(t, r.c, func(context.Context, string) (gateway.World, gateway.State, error) { return scope, state, nil }, origin)
		peer := gatewaytest.Join(t, g, r.c, "one", wire.ModeObserve, false)
		t.Cleanup(func() {
			if err := g.Close(); err != nil {
				t.Error(err)
			}
			peer.Close()
			if err := session.Close(); err != nil {
				t.Error(err)
			}
		})
		r.sync()
		peer.Drain()
		// The visitor's initial snapshot sees the first player; the first player
		// receives the visitor through the live PlayerSeen notification.
		assertPlayerAppearance(t, peer.Records, char.ObjectID, want, meaning.Appeared)
		assertPlayerAppearance(t, r.p.Records, visitor.ObjectID, meaning.Appearance{Race: wire.RaceDwarf, Sex: wire.SexMale, Branch: wire.BranchFighter}, meaning.Appeared)
		owner := r.port.Port.(*worldbridge.Client).Owner()
		if _, _, err := r.w.Subscribe(t.Context(), owner); err != nil {
			t.Fatal(err)
		}
		r.sync()
		fresh, freshState, err := r.session.Resume(t.Context())
		if err != nil {
			t.Fatal(err)
		}
		r.mu.Lock()
		r.scope, r.state = fresh, freshState
		r.mu.Unlock()
		r.p = gatewaytest.Join(t, r.g, r.c, "one", wire.ModeAct, true)
		r.sync()
		assertPlayerAppearance(t, r.p.Records, char.ObjectID, want, meaning.Appeared, meaning.Vitals)
		assertPlayerAppearance(t, r.p.Records, visitor.ObjectID, meaning.Appearance{Race: wire.RaceDwarf, Sex: wire.SexMale, Branch: wire.BranchFighter}, meaning.Appeared)
		if r.port.Port.(*worldbridge.Client).Owner() != owner {
			t.Fatal("Resume changed incarnation")
		}
	})
}
