// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"bytes"
	"context"
	"errors"
	"log/slog"
	"reflect"
	"strconv"
	"strings"
	"testing"
	"testing/synctest"
	"time"
)

func huntPlayers(t *testing.T, w *World, ctx context.Context, count int) []Entry {
	t.Helper()
	var out []Entry
	for i := range count {
		char := syntheticCharacter(int32(10 + i*10))
		char.X = 300
		char.Vitality = 20000
		out = append(out, enterTest(t, w, ctx, char))
	}
	return out
}

func huntMonster(t *testing.T, w *World, ctx context.Context) Ref {
	t.Helper()
	id := objectOf(t, w, ctx, 9101)
	var ref Ref
	if err := w.do(ctx, func(s *state) {
		n := s.npcs[id]
		n.tmpl.MaxHP, n.hp = 100, 100
		n.tmpl.RewardExp, n.tmpl.RewardSP = 100, 20
		ref = n.ref()
	}); err != nil {
		t.Fatal(err)
	}
	return ref
}

func harmTest(t *testing.T, w *World, ctx context.Context, from, to Ref, amount float64) {
	t.Helper()
	if err := w.Harm(ctx, from, to, amount); err != nil {
		t.Fatal(err)
	}
}

func testCoins(c Character) int64 {
	var n int64
	for _, it := range c.Items {
		if it.TemplateID == 9002 {
			n += it.Count
		}
	}
	return n
}

func TestHuntCreditsOnlyRemovedHPAndKeepsLostNeutralShares(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		var log bytes.Buffer
		logger := slog.New(slog.NewTextHandler(&log, &slog.HandlerOptions{Level: slog.LevelDebug}))
		w, _, ctx := startTestWorld(t, cat, WithLogger(logger))
		p := huntPlayers(t, w, ctx, 2)
		n := huntMonster(t, w, ctx)
		harmTest(t, w, ctx, p[0].Self.Entity, n, 30)
		if err := w.do(ctx, func(s *state) { s.npcs[n.ID].hate = nil }); err != nil {
			t.Fatal(err)
		}
		harmTest(t, w, ctx, Ref{}, n, 20)
		harmTest(t, w, ctx, p[1].Self.Entity, n, 10000)
		a, _ := w.Snapshot(ctx, 10)
		b, _ := w.Snapshot(ctx, 20)
		if a.Char.Exp != 30 || a.Char.SP != 26 || b.Char.Exp != 50 || b.Char.SP != 30 || a.Char.Vitality != 20000 || b.Char.Vitality != 20000 || testCoins(a.Char) != 1 || testCoins(b.Char) != 1 {
			t.Fatalf("clamped HP shares/x1: %+v/%+v", a.Char, b.Char)
		}
		if err := w.do(ctx, func(s *state) {
			h := s.npcs[n.ID].hunt
			if h.sides[10].damage != 30*hpUnits || h.sides[0].damage != 20*hpUnits || h.sides[20].damage != 50*hpUnits {
				t.Error("HP ledger included overkill or forgot neutral harm")
			}
			w.calculateRewards(s, s.npcs[n.ID], s.players[20])
		}); err != nil {
			t.Fatal(err)
		}
		after, _ := w.Snapshot(ctx, 10)
		if !reflect.DeepEqual(a.Char.Items, after.Char.Items) || after.Char.Exp != a.Char.Exp {
			t.Fatal("same death paid twice")
		}
		if strings.Count(log.String(), `msg="hunt settlement"`) != 1 {
			t.Fatalf("expected one bounded DEBUG settlement line: %s", log.String())
		}
	})
}

func TestHuntFirstContributionFixesPartyWithoutDoubleSharing(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat)
		p := huntPlayers(t, w, ctx, 3)
		n := huntMonster(t, w, ctx)
		harmTest(t, w, ctx, p[0].Self.Entity, n, 10)
		if _, err := w.SetParty(ctx, 0, []Ref{p[0].Self.Entity, p[1].Self.Entity, p[2].Self.Entity}, LootFinder); err != nil {
			t.Fatal(err)
		}
		harmTest(t, w, ctx, p[1].Self.Entity, n, 90)
		for i, want := range []int64{10, 45, 45} {
			self, err := w.Snapshot(ctx, int32(10+10*i))
			if err != nil || self.Char.Exp != want {
				t.Fatalf("member %d XP=%d/%v, want %d", i, self.Char.Exp, err, want)
			}
			if i > 0 && (self.Char.SP != 29 || testCoins(self.Char) != 1) {
				t.Fatalf("party share applied twice or lost: %+v", self.Char)
			}
		}
	})
}

func TestHuntLeavingPartyRetainsTheFirstContributionSide(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat)
		p := huntPlayers(t, w, ctx, 2)
		group, err := w.SetParty(ctx, 0, []Ref{p[0].Self.Entity, p[1].Self.Entity}, LootFinder)
		if err != nil {
			t.Fatal(err)
		}
		n := huntMonster(t, w, ctx)
		harmTest(t, w, ctx, p[0].Self.Entity, n, 60)
		if _, err := w.SetParty(ctx, group, []Ref{p[1].Self.Entity}, LootFinder); err != nil {
			t.Fatal(err)
		}
		harmTest(t, w, ctx, p[0].Self.Entity, n, 40)
		for _, id := range []int32{10, 20} {
			self, _ := w.Snapshot(ctx, id)
			if self.Char.Exp != 50 || self.Char.SP != 30 || testCoins(self.Char) != 1 {
				t.Fatalf("changed membership rewrote first contribution: %+v", self.Char)
			}
		}
	})
}

func TestHuntRecipientsAtDeathAndDropLevelUseAllContributors(t *testing.T) {
	for _, branch := range []string{"dead", "far", "deep-blue"} {
		t.Run(branch, func(t *testing.T) {
			cat := syntheticCatalog(t)
			synctest.Test(t, func(t *testing.T) {
				w, _, ctx := startTestWorld(t, cat)
				p := huntPlayers(t, w, ctx, 2)
				n := huntMonster(t, w, ctx)
				if branch == "deep-blue" {
					if err := w.do(ctx, func(s *state) { s.players[10].char.Level = 20; s.players[10].stats.Level = 20 }); err != nil {
						t.Fatal(err)
					}
				}
				harmTest(t, w, ctx, p[0].Self.Entity, n, 60)
				if branch == "dead" {
					harmTest(t, w, ctx, Ref{}, p[0].Self.Entity, 10000)
				} else {
					if err := w.Teleport(ctx, 10, Point{X: 5000}, false); err != nil {
						t.Fatal(err)
					}
					if err := w.Appear(ctx, 10); err != nil {
						t.Fatal(err)
					}
				}
				harmTest(t, w, ctx, p[1].Self.Entity, n, 40)
				self, _ := w.Snapshot(ctx, 20)
				if self.Char.Exp != 40 || self.Char.SP != 28 {
					t.Fatalf("ineligible side was removed from XP denominator: %+v", self.Char)
				}
				if branch == "deep-blue" && testCoins(self.Char) != 0 {
					t.Fatal("drop penalty used only the finisher's level")
				}
				lost, _ := w.Snapshot(ctx, 10)
				if lost.Char.Exp != 0 {
					t.Fatal("dead/distant contributor received XP")
				}
			})
		})
	}
}

func TestHuntReturnHomeAndRespawnResetCredit(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat)
		p := huntPlayers(t, w, ctx, 2)
		n := huntMonster(t, w, ctx)
		harmTest(t, w, ctx, p[0].Self.Entity, n, 30)
		if err := w.do(ctx, func(s *state) {
			mob := s.npcs[n.ID]
			mob.hp = 100
			mob.hunt.returned = true
			mob.resetHuntIfHome()
			mob.addHate(10, 100000, 100000)
		}); err != nil {
			t.Fatal(err)
		}
		harmTest(t, w, ctx, p[1].Self.Entity, n, 10000)
		a, _ := w.Snapshot(ctx, 10)
		b, _ := w.Snapshot(ctx, 20)
		if a.Char.Exp != 0 || b.Char.Exp != 100 {
			t.Fatalf("full-HP return/hate leaked reward: %d/%d", a.Char.Exp, b.Char.Exp)
		}
		for _, entry := range p {
			drainEvents(entry.Events)
		}
		time.Sleep(20 * time.Second)
		synctest.Wait()
		var fresh Ref
		if err := w.do(ctx, func(s *state) {
			for _, mob := range s.npcs {
				if mob.mob() {
					fresh = mob.ref()
					if mob.hunt.life < 2 || len(mob.hunt.sides) != 0 {
						t.Error("new life inherited credit")
					}
				}
			}
		}); err != nil {
			t.Fatal(err)
		}
		if fresh == n || fresh == (Ref{}) || !errors.Is(w.Harm(ctx, p[1].Self.Entity, n, 1), ErrHarm) {
			t.Fatal("old life remained a valid harm target")
		}
	})
}

func TestHuntDisconnectReturnsHomeBeforeReset(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat)
		p := huntPlayers(t, w, ctx, 1)
		n := huntMonster(t, w, ctx)
		harmTest(t, w, ctx, p[0].Self.Entity, n, 30)
		if err := w.Leave(ctx, p[0].Self.Entity); err != nil {
			t.Fatal(err)
		}
		time.Sleep(100 * time.Second)
		synctest.Wait()
		if err := w.do(ctx, func(s *state) {
			mob := s.npcs[n.ID]
			if mob.hp != 100 || len(mob.hunt.sides) != 0 || mob.hunt.returned || mob.aiTask {
				t.Errorf("disconnected encounter did not finish its home/full-HP reset: hp=%g credit=%d returned=%t thinking=%t", mob.hp, len(mob.hunt.sides), mob.hunt.returned, mob.aiTask)
			}
		}); err != nil {
			t.Fatal(err)
		}
		char := syntheticCharacter(20)
		char.X = 300
		fresh := enterTest(t, w, ctx, char)
		harmTest(t, w, ctx, fresh.Self.Entity, n, 1000000)
		self, err := w.Snapshot(ctx, 20)
		if err != nil || self.Char.Exp != 100 {
			t.Fatalf("old disconnected credit survived reset: %+v/%v", self.Char, err)
		}
	})
}

func TestHuntRecipientRadiusBoundaryAndRejoinedCharacter(t *testing.T) {
	for _, branch := range []string{"at-radius", "outside-radius", "rejoined"} {
		t.Run(branch, func(t *testing.T) {
			cat := syntheticCatalog(t)
			synctest.Test(t, func(t *testing.T) {
				w, _, ctx := startTestWorld(t, cat)
				players := huntPlayers(t, w, ctx, 2)
				n := huntMonster(t, w, ctx)
				harmTest(t, w, ctx, players[0].Self.Entity, n, 60)
				if branch == "rejoined" {
					if err := w.Leave(ctx, players[0].Self.Entity); err != nil {
						t.Fatal(err)
					}
					c := syntheticCharacter(10)
					c.X = 300
					fresh := enterTest(t, w, ctx, c)
					if fresh.Self.Entity == players[0].Self.Entity {
						t.Fatal("rejoined body reused an incarnation")
					}
				} else {
					distance := int32(1600)
					if branch == "outside-radius" {
						distance++
					}
					if err := w.Teleport(ctx, 10, Point{X: 300 + distance}, false); err != nil {
						t.Fatal(err)
					}
					if err := w.Appear(ctx, 10); err != nil {
						t.Fatal(err)
					}
				}
				harmTest(t, w, ctx, players[1].Self.Entity, n, 40)
				self, err := w.Snapshot(ctx, 10)
				want := int64(60)
				if branch == "outside-radius" {
					want = 0
				}
				if err != nil || self.Char.Exp != want {
					t.Fatalf("recipient %s XP=%d/%v, want %d", branch, self.Char.Exp, err, want)
				}
			})
		})
	}
}

func TestHuntPartyCoinCursorAndPermanentItemOwnership(t *testing.T) {
	cat := syntheticCatalogWithCoolTime(t, 0, map[string]string{"npc_template_id": "9101", "item_template_id": "9001", "category": "1", "min_count": "1", "max_count": "1", "chance": "1000000"})
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat)
		p := huntPlayers(t, w, ctx, 4)
		group, err := w.SetParty(ctx, 0, []Ref{p[0].Self.Entity, p[1].Self.Entity, p[2].Self.Entity}, LootRoundRobin)
		if err != nil {
			t.Fatal(err)
		}
		n := huntMonster(t, w, ctx)
		harmTest(t, w, ctx, p[0].Self.Entity, n, 10000)
		var item Ref
		if err := w.do(ctx, func(s *state) {
			if s.parties[group].coinCursor != 2 {
				t.Error("party remainder cursor did not advance")
			}
			for _, g := range s.ground {
				if g.templateID == 9001 {
					item = g.ref()
					if g.owner != 20 || !g.ownerUntil.IsZero() {
						t.Error("round-robin ownership is not permanent")
					}
				}
			}
		}); err != nil {
			t.Fatal(err)
		}
		if item == (Ref{}) {
			t.Fatal("one table roll produced no item")
		}
		for _, entry := range p {
			drainEvents(entry.Events)
		}
		time.Sleep(20 * time.Second)
		synctest.Wait()
		if err := w.do(ctx, func(s *state) {
			before := len(s.players[40].char.Items)
			w.doPickup(s, s.players[40], item.ID)
			if s.ground[item.ID] == nil || len(s.players[40].char.Items) != before {
				t.Error("foreign pickup after 15 seconds stole hunt reward")
			}
			w.doPickup(s, s.players[20], item.ID)
			if s.ground[item.ID] != nil {
				t.Error("assigned owner could not collect item")
			}
		}); err != nil {
			t.Fatal(err)
		}
	})
}

func TestHuntNeutralFinisherRetainsHighestAttackerDropPenalty(t *testing.T) {
	for _, high := range []bool{false, true} {
		t.Run(strconv.FormatBool(high), func(t *testing.T) {
			cat := syntheticCatalog(t)
			synctest.Test(t, func(t *testing.T) {
				w, _, ctx := startTestWorld(t, cat)
				p := huntPlayers(t, w, ctx, 2)
				n := huntMonster(t, w, ctx)
				if high {
					if err := w.do(ctx, func(s *state) { s.players[10].char.Level = 20; s.players[10].stats.Level = 20 }); err != nil {
						t.Fatal(err)
					}
				}
				harmTest(t, w, ctx, p[0].Self.Entity, n, 60)
				harmTest(t, w, ctx, p[1].Self.Entity, n, 30)
				harmTest(t, w, ctx, Ref{}, n, 10000)
				a, err := w.Snapshot(ctx, 10)
				if err != nil {
					t.Fatal(err)
				}
				b, err := w.Snapshot(ctx, 20)
				if err != nil {
					t.Fatal(err)
				}
				coins := testCoins(a.Char) + testCoins(b.Char)
				if high && coins != 0 || !high && coins != 2 {
					t.Fatalf("neutral finisher high=%t: coins=%d", high, coins)
				}
			})
		})
	}
}
