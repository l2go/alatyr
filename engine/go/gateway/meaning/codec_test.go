// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"reflect"
	"slices"
	"strings"
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

type vector struct{ ID, Hex, Expected string }
type vectorFile struct {
	SourceCommit string   `json:"source_commit"`
	SourceSHA    string   `json:"source_sha256"`
	Vectors      []vector `json:"vectors"`
}

func vectors(t testing.TB) vectorFile {
	t.Helper()
	b, e := os.ReadFile(filepath.Join(protocolModuleDir(t), "testdata", "vectors.json"))
	if e != nil {
		t.Fatal(e)
	}
	sum := sha256.Sum256(b)
	if hex.EncodeToString(sum[:]) != "cf2c431fec2ec7bd01850eaba2a682af75015a6beceb552a0df5024039ba5545" {
		t.Fatal("public historical fixture changed")
	}
	var v vectorFile
	if e = json.Unmarshal(b, &v); e != nil {
		t.Fatal(e)
	}
	return v
}
func codec(t testing.TB) *Codec {
	t.Helper()
	c, e := NewCodec(testConfig())
	if e != nil {
		t.Fatal(e)
	}
	return c
}
func TestFrozenVectorsV01ThroughV28(t *testing.T) {
	all := vectors(t)
	if all.SourceCommit != "f7bee7224da7f6f0e980f35bc6f7b43b2e7d4415" || len(all.Vectors) != 28 {
		t.Fatal("vector provenance changed")
	}
	c := codec(t)
	for _, v := range all.Vectors {
		t.Run(v.ID, func(t *testing.T) {
			b, e := hex.DecodeString(strings.ReplaceAll(v.Hex, " ", ""))
			if e != nil {
				t.Fatal(e)
			}
			var got []wire.Record
			if v.ID == "V17" || v.ID == "V21" {
				got, e = c.Read(bytes.NewReader(b))
			} else {
				got, e = c.Decode(b)
			}
			expectedError := error(nil)
			switch v.ID {
			case "V11":
				expectedError = wire.ErrUnsupported
			case "V12", "V13", "V18", "V19", "V20", "V21":
				expectedError = wire.ErrFrame
			}
			if !errors.Is(e, expectedError) {
				t.Fatalf("%s: %v", v.Expected, e)
			}
			if e != nil {
				if got != nil {
					t.Fatal("partial frame")
				}
				return
			}
			if v.ID == "V10" {
				if len(got) != 0 {
					t.Fatal(got)
				}
				return
			}
			if len(got) != 1 {
				t.Fatal(got)
			}
			r := got[0]
			if v.ID == "V06" {
				if !r.Unsupported {
					t.Fatal("essential field was discarded")
				}
				return
			}
			if r.Unsupported {
				t.Fatal("unexpected unsupported record")
			}
			if v.ID != "V05" && v.ID != "V17" {
				encoded, e := c.Encode(got)
				if e != nil || !bytes.Equal(encoded, b) {
					t.Fatalf("canonical bytes changed: %x, %v", encoded, e)
				}
			}
			switch v.ID {
			case "V01":
				if r.Type != wire.Ping || getU(r.Fields, 1) != 0 {
					t.Fatal(r)
				}
			case "V02", "V17":
				if r.Type != wire.Hello || getU(r.Fields, 1) != 1 || getU(r.Fields, 2) != 1 || getString(r.Fields, 3) == "" || wire.Participant(getU(r.Fields, 4)) != wire.ParticipantPerson || wire.Mode(getU(r.Fields, 5)) != wire.ModeAct {
					t.Fatal(r)
				}
			case "V03", "V04", "V05":
				d, e := EventValue(r)
				if e != nil || d.Event.Kind != Known {
					t.Fatal(d, e)
				}
				if v.ID == "V04" {
					if d.Event.Skills == nil || len(d.Event.Skills) != 0 || !d.Event.Complete {
						t.Fatal(d)
					}
				} else if d.Event.Skills != nil {
					t.Fatal(d)
				}
			case "V07":
				p, reason := PlanIntent(r, Environment{Alive: true})
				if reason != wire.ReasonNone || p.Command != (Command{Kind: Walk}) {
					t.Fatal(p, reason)
				}
			case "V08":
				if getU(r.Fields, 1) != 1 || wire.Stage(getU(r.Fields, 2)) != wire.StageSent || getU(r.Fields, 3) != 0 || getU(r.Fields, 5) != 11 || getU(r.Fields, 6) != 5 || getU(r.Fields, 7) != 2 {
					t.Fatal(r)
				}
			case "V09":
				if r.Type != wire.Control || wire.ControlOp(getU(r.Fields, 1)) != wire.ControlGranted || getU(r.Fields, 2) != 1 || getU(r.Fields, 3) != 1 {
					t.Fatal(r)
				}
			case "V14", "V15":
				d, e := EventValue(r)
				if e != nil || d.Event.Stats == nil {
					t.Fatal(d, e)
				}
				if v.ID == "V14" {
					if d.Event.Stats.HP == nil || *d.Event.Stats.HP != 0 || d.Event.Stats.MP != nil {
						t.Fatal(d)
					}
				} else if d.Event.Stats.XP == nil || *d.Event.Stats.XP != 5000000000 {
					t.Fatal(d)
				}
			case "V16":
				if r.Type != wire.Bye || getU(r.Fields, 1) != 0 {
					t.Fatal(r)
				}
			case "V22":
				if getRef(r.Fields, 10) != (Ref{7, 1}) || getU(r.Fields, 2) != 2 {
					t.Fatal(r)
				}
			case "V23":
				d, e := EventValue(r)
				if e != nil || d.Event.Text != "Hi" || len(d.Event.Lines) != 1 || d.Event.Lines[0] != (Line{Label: "Go"}) || d.Context.Dialog != 1 || d.Context.Options[0].ID != 1 {
					t.Fatal(d, e)
				}
			case "V24":
				d, e := EventValue(r)
				if e != nil || !reflect.DeepEqual(d.Event.Goods, []Good{{ID: 3, Count: 1, Object: 2, Price: 10}}) || !d.Event.Complete || d.Event.List != 1 || d.Context.Offers[0] != 1 {
					t.Fatal(d, e)
				}
			case "V25":
				d, e := EventValue(r)
				if e != nil || !reflect.DeepEqual(d.Event.Skills, []Skill{{ID: 3, Level: 1}}) || !d.Event.Complete {
					t.Fatal(d, e)
				}
			case "V26":
				d, e := EventValue(r)
				if e != nil || !reflect.DeepEqual(d.Event.Goods, []Good{{Object: 7, ID: 1060, Count: 1, Price: 10}}) {
					t.Fatal(d, e)
				}
			case "V27":
				d, e := EventValue(r)
				if e != nil || !reflect.DeepEqual(d.Event.Quests, []QuestState{{ID: 1, Step: 2, Name: "Q", State: "active"}}) || !d.Event.Complete {
					t.Fatal(d, e)
				}
			case "V28":
				d, e := EventValue(r)
				if e != nil || !reflect.DeepEqual(d.Event.Entries, []Entry{{MS: 0, Category: "loot", Text: "Gold"}}) {
					t.Fatal(d, e)
				}
			}
		})
	}
}
func TestAllEventKindsRoundTripValues(t *testing.T) {
	c := codec(t)
	for _, kind := range Kinds() {
		t.Run(kind.Name, func(t *testing.T) {
			ev := Event{MS: 123, Kind: kind.Name, ID: 7, Role: "unfamiliar", Name: "A figure", X: -100, Y: 200, Z: -300, ToX: 42, ToY: -43, ToZ: 44, Speed: 120, Target: 9, Amount: 17, Hit: true, Mine: true, Text: "Plain " + string([]byte{0}) + " text", List: 5, Coin: Number(0), Stats: &Stats{HP: Number(0), MaxHP: Number(120), MP: nil, MaxMP: Number(32), Level: Number(9), XP: Number(5000000000), SP: Number(6000000000)}, Category: "state", CombatUntil: 777, Complete: true}
			ctx := FactContext{Seq: 1, Epoch: 1, Ref: Ref{7, 1}, Target: Ref{9, 2}}
			switch kind.Name {
			case Spoke:
				ev.Lines = []Line{{Label: "Answer", Say: ""}}
				ctx.Dialog = 1
				ctx.Options = []Option{{1, "Answer"}}
			case Shop, Offer, Practice:
				ev.Goods = []Good{{Object: 7, ID: 30, Count: 2, Price: 10, Name: "Row", Change: 2}}
				ctx.Offers = []uint64{123}
			case Dropped, Carried, Inventory:
				ev.Goods = []Good{{Object: 8, ID: 30, Count: 5000000000, Price: 10, Name: "Supply", Change: 2}}
			case Quests:
				ev.Quests = []QuestState{{ID: 3, Step: 2, Name: "A quest", State: "active"}}
			case Known:
				ev.Skills = []Skill{{ID: 3, Level: 2, Passive: true, Disabled: false}}
			case History:
				ev.Entries = []Entry{{MS: 90, Category: "loot", Text: "A supply"}}
			}
			r, e := EventRecord(ev, ctx)
			if e != nil {
				t.Fatal(e)
			}
			b, e := c.Encode([]wire.Record{r})
			if e != nil {
				t.Fatal(e)
			}
			records, e := c.Decode(b)
			if e != nil {
				t.Fatal(e)
			}
			value, e := EventValue(records[0])
			if e != nil || !reflect.DeepEqual(ev, value.Event) {
				t.Fatalf("%v\nwant %#v\n got %#v", e, ev, value.Event)
			}
		})
	}
}
func TestNilEmptyAndKnownZero(t *testing.T) {
	c := codec(t)
	for _, base := range []Event{
		{Kind: Carried}, {Kind: Carried, Goods: []Good{}}, {Kind: Vitals, Stats: &Stats{}}, {Kind: Vitals, Stats: &Stats{HP: Number(0)}},
		{Kind: Known, Skills: []Skill{}}, {Kind: Quests, Quests: []QuestState{}}, {Kind: History, Entries: []Entry{}}, {Kind: Spoke, Lines: []Line{}}, {Kind: Practice, Goods: []Good{}},
	} {
		ctx := FactContext{Seq: 1, Epoch: 1}
		if base.Lines != nil {
			ctx.Dialog = 1
			ctx.Options = []Option{}
		}
		if base.Kind == Practice {
			ctx.Offers = []uint64{}
		}
		r, e := EventRecord(base, ctx)
		if e != nil {
			t.Fatal(e)
		}
		b, e := c.Encode([]wire.Record{r})
		if e != nil {
			t.Fatal(e)
		}
		records, e := c.Decode(b)
		if e != nil {
			t.Fatal(e)
		}
		d, e := EventValue(records[0])
		if e != nil || !reflect.DeepEqual(base, d.Event) {
			t.Fatalf("%s: %v %#v %#v", base.Kind, e, base, d.Event)
		}
	}
}
func TestRegistryMatchesFrozenDictionary(t *testing.T) {
	if Meaning != "alatyr/1" || Namespace != "x.alatyr" || WireVersion != 1 {
		t.Fatal("dictionary identity changed")
	}
	wantKinds := []string{"appeared/objects", "moved/objects", "stopped/objects", "corrected/objects", "struck/objects", "died/objects", "revived/objects", "dropped/objects", "took/objects", "gone/objects", "spoke/dialogue", "shop/trade", "offer/trade", "carried/inventory", "inventory/inventory", "vitals/self", "reward/self", "notice/dialogue", "history/history", "quest/quests", "quests/quests", "practice/skills", "known/skills", "targeted/objects"}
	wantVerbs := []string{"walk/walk", "aim/aim", "strike/strike", "take/take", "talk/talk", "choose/say", "buy/buy", "sell/sell", "rise/rise", "focus/focus", "engage/engage", "cast/cast", "learn/learn", "use-item/use-item"}
	if len(Kinds()) != len(wantKinds) || len(Verbs()) != len(wantVerbs) {
		t.Fatal("incomplete registry")
	}
	for i, k := range Kinds() {
		if k.Code != uint64(i+1) || k.Name+"/"+k.Domain != wantKinds[i] {
			t.Fatal("kind changed", k)
		}
	}
	for i, v := range Verbs() {
		if v.Code != uint64(i+1) || v.Name+"/"+v.Command != wantVerbs[i] {
			t.Fatal("verb changed", v)
		}
	}
	kinds := Kinds()
	kinds[0].Name = "changed"
	verbs := Verbs()
	verbs[0].Name = "changed"
	if Kinds()[0].Name != Appeared || Verbs()[0].Name != Walk {
		t.Fatal("registry exposed mutable table")
	}
	r := Registry(testConfig())
	fields := []uint32{}
	for n := range r[wire.Fact] {
		fields = append(fields, n)
	}
	slices.Sort(fields)
	expected := []uint32{1, 2, 3, 4, 5, 6}
	for n := uint32(10); n <= 34; n++ {
		expected = append(expected, n)
	}
	// Historical fields stay fixed; appearance is one optional addition.
	expected = append(expected, wire.AppearanceField)
	appearance := r[wire.Fact][wire.AppearanceField]
	if appearance.Required || appearance.Essential || appearance.Shape != wire.Object {
		t.Fatal("appearance must remain optional")
	}
	if !reflect.DeepEqual(fields, expected) {
		t.Fatal(fields)
	}
	for n := uint32(1); n <= 34; n++ {
		s, ok := r[wire.Welcome][7].Object[n]
		if !ok || !s.Required || !s.Essential || s.Shape != wire.UInt {
			t.Fatal("limits registry", n)
		}
	}
}

func FuzzDecodeMeaning(f *testing.F) {
	c := codec(f)
	for _, branch := range []wire.Branch{wire.BranchUnknown, wire.BranchMystic} {
		r, err := EventRecord(Event{Kind: Appeared, ID: 1, Role: RolePlayer, Appearance: &Appearance{Race: wire.RaceHuman, Sex: wire.SexFemale, Branch: branch}}, FactContext{Seq: 1, Epoch: 1})
		if err != nil {
			f.Fatal(err)
		}
		b, err := c.Encode([]wire.Record{r})
		if err != nil {
			f.Fatal(err)
		}
		f.Add(b)
	}
	for _, v := range vectors(f).Vectors {
		b, e := hex.DecodeString(strings.ReplaceAll(v.Hex, " ", ""))
		if e != nil {
			f.Fatal(e)
		}
		f.Add(b)
	}
	f.Fuzz(func(t *testing.T, b []byte) {
		if len(b) > testConfig().Wire.FrameBytes {
			return
		}
		stream, streamErr := c.Read(bytes.NewReader(b))
		if streamErr != nil && stream != nil {
			t.Fatal("partial stream frame")
		}
		records, e := c.Decode(b)
		if e != nil {
			if records != nil {
				t.Fatal("partial frame")
			}
			return
		}
		if len(records) > testConfig().Wire.Records {
			t.Fatal("unbounded record count")
		}
		for _, r := range records {
			if len(r.Fields) > testConfig().Wire.Fields {
				t.Fatal("unbounded fields")
			}
		}
		if len(records) > 0 {
			encoded, e := c.Encode(records)
			unsupported := false
			for _, r := range records {
				unsupported = unsupported || r.Unsupported
			}
			if !unsupported && e != nil {
				t.Fatal("valid frame cannot be encoded", e)
			}
			if e == nil {
				if len(encoded) > testConfig().Wire.FrameBytes {
					t.Fatal("unbounded encoding")
				}
				if _, e = c.Decode(encoded); e != nil {
					t.Fatal("writer emitted undecodable frame", e)
				}
			}
		}
	})
}
