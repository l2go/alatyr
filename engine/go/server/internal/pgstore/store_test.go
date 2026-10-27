// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package pgstore

import (
	"context"
	"errors"
	"fmt"
	"github.com/l2go/alatyr/engine/go/server/internal/migrate"
	"github.com/l2go/alatyr/engine/go/server/internal/pgtest"
	"os"
	"path/filepath"
	"reflect"
	"slices"
	"strings"
	"testing"

	"github.com/jackc/pgx/v5"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func database(t *testing.T) string { t.Helper(); return pgtest.Database(t) }

func validatedOpen(ctx context.Context, dsn string) (*Store, error) {
	s, err := Open(ctx, dsn)
	if err != nil {
		return nil, err
	}
	if err := migrate.Apply(ctx, dsn); err != nil {
		return nil, errors.Join(err, s.Close())
	}
	return s, nil
}

func openTest(t *testing.T, dsn string) *Store {
	t.Helper()
	s, err := validatedOpen(t.Context(), dsn)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := s.Close(); err != nil {
			t.Error(err)
		}
	})
	return s
}
func character() world.Character {
	return world.Character{ObjectID: 1, Account: "example", Name: "Walker", ClassID: 0, BaseClass: 0, Race: 0, Level: 2, SP: 20,
		MaxHP: 100, CurHP: 50, MaxMP: 30, CurMP: 20, MaxCP: 20, CurCP: 10, X: -71057, Y: 257883, Z: -3104,
		NameColor: 0xffffff, TitleColor: 0xffff77,
		Items:  []world.Item{{ObjectID: 2, TemplateID: 999999, Count: 7, Slot: -1}},
		Skills: []world.Skill{{ID: 999999, Level: 1}}, Shortcuts: []world.Shortcut{{Page: 0, Slot: 0, Type: 1, ID: 2, Level: 0}},
		QuestVars: []world.QuestVar{{Quest: "authored", Name: "progress", Value: "started"}}}
}
func loadTest(t *testing.T, s *Store) world.Character {
	t.Helper()
	chars, err := s.Characters(t.Context(), "example")
	if err != nil || len(chars) != 1 {
		t.Fatalf("characters: got %v/%v", chars, err)
	}
	return chars[0]
}
func execTest(t *testing.T, c *pgx.Conn, sql string, args ...any) {
	t.Helper()
	if _, err := c.Exec(t.Context(), sql, args...); err != nil {
		t.Fatal(err)
	}
}

func TestPostgresSnapshotAndLease(t *testing.T) {
	dsn := database(t)
	s := openTest(t, dsn)
	c := character()
	if err := s.PutCharacter(t.Context(), c); err != nil {
		t.Fatal(err)
	}
	if got := loadTest(t, s); !reflect.DeepEqual(got, c) {
		t.Fatalf("snapshot got %+v, want %+v", got, c)
	}
	if second, err := validatedOpen(t.Context(), dsn); err == nil {
		second.Close()
		t.Fatal("second server acquired ownership")
	}
	var count int
	if err := s.conn.QueryRow(t.Context(), "SELECT count(*) FROM pg_tables WHERE schemaname='world'").Scan(&count); err != nil || count != 10 {
		t.Fatalf("new world tables got %d/%v, want 10", count, err)
	}
	first, last, err := s.ReserveIDs(t.Context())
	if err != nil || last-first != 1048575 {
		t.Fatalf("range: %d..%d/%v", first, last, err)
	}
	if err := s.Close(); err != nil {
		t.Fatal(err)
	}
	next := openTest(t, dsn)
	nextFirst, _, err := next.ReserveIDs(t.Context())
	if err != nil || nextFirst != last+1 {
		t.Fatalf("restart range: got %d/%v, want %d", nextFirst, err, last+1)
	}
	if got := loadTest(t, next); !reflect.DeepEqual(got, c) {
		t.Fatalf("restart lost state: %+v", got)
	}
}

func TestLearningTransactionRollbackAndIdempotentRetry(t *testing.T) {
	s := openTest(t, database(t))
	c := character()
	if err := s.PutCharacter(t.Context(), c); err != nil {
		t.Fatal(err)
	}
	execTest(t, s.conn, `CREATE FUNCTION reject_skill() RETURNS trigger LANGUAGE plpgsql AS $$BEGIN RAISE EXCEPTION 'injected skill write failure'; END$$;
 CREATE TRIGGER reject_skill BEFORE INSERT OR UPDATE ON player_skill FOR EACH ROW EXECUTE FUNCTION reject_skill()`)
	learnt := c.Clone()
	learnt.SP = 17
	learnt.Skills = append(learnt.Skills, world.Skill{ID: 301, Level: 1})
	if err := s.PutLearning(t.Context(), learnt); err == nil {
		t.Fatal("injected failure was ignored")
	}
	if got := loadTest(t, s); !reflect.DeepEqual(got, c) {
		t.Fatalf("partial N1 commit: %+v", got)
	}
	execTest(t, s.conn, "DROP TRIGGER reject_skill ON player_skill")
	for range 2 {
		if err := s.PutLearning(t.Context(), learnt); err != nil {
			t.Fatal(err)
		}
	}
	got := loadTest(t, s)
	if got.SP != 17 || !slices.Contains(got.Skills, world.Skill{ID: 301, Level: 1}) || len(got.Skills) != 2 {
		t.Fatalf("retry charged twice or lost skills: %+v", got)
	}
}

func TestSnapshotRollbackAndOwnership(t *testing.T) {
	s := openTest(t, database(t))
	c := character()
	if err := s.PutCharacter(t.Context(), c); err != nil {
		t.Fatal(err)
	}
	for _, tc := range []string{"quest-failure", "item-conflict", "account-conflict"} {
		t.Run(tc, func(t *testing.T) {
			changed := c.Clone()
			changed.SP = 1
			changed.X = 42
			switch tc {
			case "quest-failure":
				changed.QuestVars[0].Value = strings.Repeat("x", 256)
			case "item-conflict":
				execTest(t, s.conn, "INSERT INTO item(id,item_template_id,owner_player_id,location,count) VALUES (3,999999,1,'WAREHOUSE',1)")
				changed.Items = append(changed.Items, world.Item{ObjectID: 3, TemplateID: 999999, Slot: -1, Count: 1})
			case "account-conflict":
				changed.Account = "other"
			}
			if err := s.PutCharacter(t.Context(), changed); err == nil {
				t.Fatal("invalid snapshot committed")
			}
			if got := loadTest(t, s); !reflect.DeepEqual(got, c) {
				t.Fatalf("rollback changed player: %+v", got)
			}
		})
	}
	ctx, cancel := context.WithCancel(t.Context())
	cancel()
	if err := s.Ping(ctx); err == nil {
		t.Fatal("canceled operation reached database")
	}
	if err := s.Ping(t.Context()); err != nil {
		t.Fatal(err)
	}
}

func flyway(t *testing.T, dsn string) *pgx.Conn {
	t.Helper()
	c, err := pgx.Connect(t.Context(), dsn)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := c.Close(context.Background()); err != nil {
			t.Error(err)
		}
	})
	execTest(t, c, "CREATE SCHEMA world; SET search_path=world,public")
	files, err := filepath.Glob("../../../../java/game/src/main/resources/db/world/V*.sql")
	if err != nil || len(files) != 10 {
		t.Fatalf("baseline files: got %d/%v, want 10", len(files), err)
	}
	execTest(t, c, `CREATE TABLE flyway_schema_history(installed_rank integer PRIMARY KEY,version varchar(50),type varchar(20),success boolean)`)
	for i, file := range files {
		data, err := os.ReadFile(file)
		if err != nil {
			t.Fatal(err)
		}
		execTest(t, c, string(data))
		execTest(t, c, "INSERT INTO flyway_schema_history VALUES ($1,$2,'SQL',true)", i+1, fmt.Sprintf("1.%02d", i+1))
	}
	return c
}

func TestGooseBaselinesFlywayWithoutDataLoss(t *testing.T) {
	dsn := database(t)
	legacy := flyway(t, dsn)
	execTest(t, legacy, `INSERT INTO player(id,account_name,name,race_id,active_class_id,base_class_id,level,sp,title,karma) VALUES(1,'example','Walker',0,0,0,2,20,'Original',7);
 INSERT INTO item(id,item_template_id,owner_player_id,location,count,enchant_level,location_slot) VALUES(2,999999,1,'INVENTORY',7,9,42),(3,999998,1,'WAREHOUSE',2,4,17);
 INSERT INTO player_shortcut VALUES(1,0,0,0,1,300000004,0),(1,0,0,1,2,2000000000,1);
 INSERT INTO player_skill VALUES(1,0,999999,1),(1,1,999998,3);
 INSERT INTO player_quest_variable VALUES(1,'legacy','flag','preserved');
 INSERT INTO clan(id,name,leader_player_id,crest_id,large_crest_id,alliance_crest_id) VALUES(7,'Legacy',1,300000001,300000002,300000003)`)
	var before string
	if err := legacy.QueryRow(t.Context(), "SELECT row_to_json(p)::text FROM player p WHERE id=1").Scan(&before); err != nil {
		t.Fatal(err)
	}
	s := openTest(t, dsn)
	var after string
	if err := legacy.QueryRow(t.Context(), "SELECT row_to_json(p)::text FROM player p WHERE id=1").Scan(&after); err != nil || after != before {
		t.Fatalf("baseline altered player: %v", err)
	}
	ids, err := s.UsedIDs(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	for _, id := range []int32{1, 2, 3, 7, 300000001, 300000002, 300000003, 300000004} {
		if !slices.Contains(ids, id) {
			t.Fatalf("persisted object identity %d not reserved", id)
		}
	}
	first, _, err := s.ReserveIDs(t.Context())
	if slices.Contains(ids, 2000000000) {
		t.Fatal("skill shortcut treated as object identity")
	}
	if err != nil || first != 300000005 {
		t.Fatalf("range collides with legacy identities: %d/%v", first, err)
	}
	c := loadTest(t, s)
	if len(c.Items) != 1 || c.Items[0].TemplateID != 999999 {
		t.Fatalf("unknown inventory lost: %+v", c.Items)
	}
	if err := s.PutCharacter(t.Context(), c); err != nil {
		t.Fatal(err)
	}
	var preserved bool
	if err := legacy.QueryRow(t.Context(), `SELECT
  (SELECT title='Original' AND karma=7 FROM player WHERE id=1)
  AND (SELECT enchant_level=9 AND location_slot=42 FROM item WHERE id=2)
  AND (SELECT enchant_level=4 AND location_slot=17 AND location='WAREHOUSE' FROM item WHERE id=3)
  AND EXISTS(SELECT 1 FROM player_skill WHERE player_id=1 AND class_index=1 AND skill_id=999998)
  AND (SELECT count(*)=10 FROM flyway_schema_history)`).Scan(&preserved); err != nil || !preserved {
		t.Fatalf("legacy data changed: %v/%v", preserved, err)
	}
	if err := s.Close(); err != nil {
		t.Fatal(err)
	}
	// Folding must also run for a database with an existing goose history.
	execTest(t, legacy, "UPDATE player_shortcut SET target_id=600000000 WHERE shortcut_type_id=1")
	reopened := openTest(t, dsn)
	first, _, err = reopened.ReserveIDs(t.Context())
	if err != nil || first != 600000001 {
		t.Fatalf("existing goose shortcut high water: %d/%v", first, err)
	}
}

func TestGooseRejectsUnrecognizedHistory(t *testing.T) {
	for _, kind := range []string{"missing", "future", "failed", "incomplete-schema"} {
		t.Run(kind, func(t *testing.T) {
			dsn := database(t)
			c := flyway(t, dsn)
			switch kind {
			case "missing":
				execTest(t, c, "DELETE FROM flyway_schema_history WHERE installed_rank=10")
			case "future":
				execTest(t, c, "INSERT INTO flyway_schema_history VALUES (11,'1.11','SQL',true)")
			case "failed":
				execTest(t, c, "UPDATE flyway_schema_history SET success=false WHERE installed_rank=10")
			case "incomplete-schema":
				execTest(t, c, "DROP TABLE player_shortcut")
			}
			if s, err := validatedOpen(t.Context(), dsn); err == nil {
				s.Close()
				t.Fatal("unsafe baseline accepted")
			}
			var untouched bool
			if err := c.QueryRow(t.Context(), "SELECT to_regclass('world.goose_db_version') IS NULL").Scan(&untouched); err != nil || !untouched {
				t.Fatalf("failed baseline changed history: %v/%v", untouched, err)
			}
		})
	}
}

func TestWaitingOperationHonorsCancellation(t *testing.T) {
	s := &Store{gate: make(chan struct{}, 1)}
	s.gate <- struct{}{}
	ctx, cancel := context.WithCancel(t.Context())
	cancel()
	if err := s.acquire(ctx); err == nil {
		t.Fatal("canceled waiter acquired store")
	}
	if len(s.gate) != 1 {
		t.Fatal("canceled waiter released another owner's gate")
	}
}

func TestWorldLearningUsesPostgresAtomicPort(t *testing.T) {
	s := openTest(t, database(t))
	cat, err := catalog.Load("../../../../../world")
	if err != nil {
		t.Fatal(err)
	}
	c := character()
	c.Items = nil
	c.Skills = nil
	if err := s.PutCharacter(t.Context(), c); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()
	w := world.New(cat, s, world.WithReservedIDs([]int32{1}))
	done := make(chan error, 1)
	go func() { done <- w.Run(ctx) }()
	defer func() {
		cancel()
		if err := <-done; err != nil {
			t.Error(err)
		}
	}()
	entry, err := w.Enter(ctx, c)
	if err != nil {
		t.Fatal(err)
	}
	scene, err := w.Capture(ctx, entry.Self.Entity)
	if err != nil {
		t.Fatal(err)
	}
	var trainer int32
	for _, o := range scene.Objects {
		if npc, ok := o.State.(world.NPCSeen); ok && cat.CanTeach(npc.NPC.Template.ID, c.ClassID) {
			trainer = o.Entity.ID
		}
	}
	if trainer == 0 {
		t.Fatal("authored trainer missing")
	}
	for range 2 {
		if err := w.Action(ctx, 1, trainer); err != nil {
			t.Fatal(err)
		}
	}
	if err := w.Bypass(ctx, 1, fmt.Sprintf("npc_%d_SkillList", trainer)); err != nil {
		t.Fatal(err)
	}
	offers := cat.AvailableSkills(c.ClassID, c.Level, func(int32) int32 { return 0 })
	if len(offers) == 0 {
		t.Fatal("authored skill missing")
	}
	offer := offers[0]
	cost, ok := cat.SkillCost(c.ClassID, offer.SkillID, offer.Level, c.Level)
	if !ok {
		t.Fatal("authored skill cost missing")
	}
	if err := w.Learn(ctx, 1, offer.SkillID, offer.Level); err != nil {
		t.Fatal(err)
	}
	persisted := loadTest(t, s)
	if persisted.SP != c.SP-cost || !slices.Contains(persisted.Skills, world.Skill{ID: offer.SkillID, Level: offer.Level}) {
		t.Fatalf("durable learning: %+v", persisted)
	}
	if err := w.Leave(ctx, entry.Self.Entity); err != nil {
		t.Fatal(err)
	}
}

func TestGooseRejectsFutureHistory(t *testing.T) {
	dsn := database(t)
	s := openTest(t, dsn)
	execTest(t, s.conn, "INSERT INTO goose_db_version(version_id,is_applied) VALUES (3,true)")
	if err := s.Close(); err != nil {
		t.Fatal(err)
	}
	if future, err := validatedOpen(t.Context(), dsn); err == nil {
		future.Close()
		t.Fatal("future goose version accepted")
	}
}

func TestPostgresCitextCharacterIdentity(t *testing.T) {
	s := openTest(t, database(t))
	c := character()
	c.Account = "Example"
	c.Name = "Walker"
	if err := s.PutCharacter(t.Context(), c); err != nil {
		t.Fatal(err)
	}
	for _, binding := range [][2]string{{"example", "walker"}, {"EXAMPLE", "WALKER"}, {"Example", "Walker"}} {
		got, err := s.Character(t.Context(), binding[0], binding[1])
		if err != nil || !reflect.DeepEqual(got, c) {
			t.Fatalf("canonical identity: %+v/%v", got, err)
		}
	}
	for _, binding := range [][2]string{{"other", "Walker"}, {"Example", "other"}} {
		if _, err := s.Character(t.Context(), binding[0], binding[1]); !errors.Is(err, pgx.ErrNoRows) {
			t.Fatalf("foreign binding: %v", err)
		}
	}
}

func TestInventoryOrderAndEquipmentTransitions(t *testing.T) {
	s := openTest(t, database(t))
	c := character()
	if err := s.PutCharacter(t.Context(), c); err != nil {
		t.Fatal(err)
	}
	execTest(t, s.conn, "UPDATE item SET location_slot=42 WHERE id=2")
	for range 2 {
		if err := s.PutCharacter(t.Context(), c); err != nil {
			t.Fatal(err)
		}
	}
	var location string
	var slot int
	check := func(wantLocation string, wantSlot int) {
		t.Helper()
		err := s.conn.QueryRow(t.Context(), "SELECT location::text,location_slot FROM item WHERE id=2").Scan(&location, &slot)
		if err != nil || location != wantLocation || slot != wantSlot {
			t.Fatalf("location=%s/%d: %v", location, slot, err)
		}
	}
	check("INVENTORY", 42)
	c.Items[0].Slot = 7
	if err := s.PutCharacter(t.Context(), c); err != nil {
		t.Fatal(err)
	}
	check("PAPERDOLL", 7)
	c.Items[0].Slot = -1
	if err := s.PutCharacter(t.Context(), c); err != nil {
		t.Fatal(err)
	}
	check("INVENTORY", 0)
}
