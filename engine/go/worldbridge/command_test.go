// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"errors"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

func TestPrepareCapturesTargetSkillDeadlineAndAffordance(t *testing.T) {
	_, s := fixture()
	s = lessonScene(s)
	c := command(12)
	ttl := uint64(50)
	c.QueueTTLMS = &ttl
	p, err := prepare(c, s, s.End.Time)
	if err != nil {
		t.Fatal(err)
	}
	c.Payload[1].Value.Fields[0].Value.S = 999
	ttl = 100
	if p.request.Target != s.Objects[0].Entity || p.request.Skill != (world.Skill{ID: 301, Level: 1}) || p.request.Deadline != s.End.Time+world.Time(50*time.Millisecond) || *p.command.QueueTTLMS != 50 {
		t.Fatalf("got %+v, want original captured values", p)
	}
	p = preparedTest(t, 13, s)
	if p.request.Deadline != s.End.Time+world.Time(5*time.Second) {
		t.Fatal("default raw port queue deadline was lost")
	}
	if p.request.AffordanceID != 11 || p.cost != 10 || p.request.Target != s.Dialogue.Speaker {
		t.Fatalf("got %+v, want issued lesson", p)
	}
}
func TestPrepareRejectsUnissuedAndExpiredActions(t *testing.T) {
	for _, tc := range []struct {
		name   string
		mutate func(*world.Scene, *bridge.Command)
	}{
		{"unissued", func(s *world.Scene, c *bridge.Command) { s.Dialogue.Affordances = nil }},
		{"expired", func(s *world.Scene, c *bridge.Command) { s.End.Time = s.Dialogue.Affordances[0].Expires }},
		{"different offered action", func(s *world.Scene, c *bridge.Command) {
			s.Dialogue.HTML = strings.Replace(s.Dialogue.HTML, "</body>", "<a action=\"bypass -h npc_2_Other\">Other</a></body>", 1)
			c.Payload[1] = wire.Data(68, true, "npc_2_Other")
		}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			_, s := fixture()
			s = choiceScene(s)
			c := command(6)
			tc.mutate(&s, &c)
			p, err := prepare(c, s, s.End.Time)
			if err != nil {
				t.Fatal(err)
			}
			if p.refusal != world.ReasonStale || p.request.AffordanceID != 0 {
				t.Fatalf("got %+v, want stale without authority", p)
			}
		})
	}
}
func TestUnsupportedMechanicsRefuseWithoutExecution(t *testing.T) {
	for _, tc := range []struct {
		name string
		verb uint64
	}{
		{"buy", 7}, {"sell", 8}, {"rise", 9}, {"other training type", 13},
	} {
		t.Run(tc.name, func(t *testing.T) {
			tr, s := fixture()
			s = lessonScene(s)
			c := command(tc.verb)
			if tc.verb == 13 {
				c.Payload[0] = wire.S(14, true, 4)
			}
			p, err := prepare(c, s, s.End.Time)
			if err != nil {
				t.Fatal(err)
			}
			if p.refusal != world.ReasonUnavailable {
				t.Fatalf("got %+v, want unavailable", p)
			}
			r := receiptTest(t, tr, p, world.Commit{}, nil)
			if r.Stage != wire.StageRefused || r.Delivery != wire.DeliveryNone || r.BridgeReason == nil || *r.BridgeReason != 18 {
				t.Fatalf("got %+v, want unavailable before delivery", r)
			}
		})
	}
}
func TestCommandBoundsAndShape(t *testing.T) {
	for _, tc := range []struct {
		name   string
		mutate func(*bridge.Command)
		want   error
	}{
		{"duplicate", func(c *bridge.Command) { c.Payload = append(c.Payload, c.Payload[0]) }, errCommand},
		{"missing", func(c *bridge.Command) { c.Payload = nil }, errCommand},
		{"unknown field", func(c *bridge.Command) { c.Payload = append(c.Payload, wire.S(99, true, 1)) }, errCommand},
		{"type", func(c *bridge.Command) { c.Payload[0] = wire.Data(64, true, "2") }, errCommand},
		{"too large", func(c *bridge.Command) { c.Payload[1].Value.Data = []byte(strings.Repeat("x", maxPayloadBytes+1)) }, errCapacity},
		{"nested", func(c *bridge.Command) {
			c.Payload[0].Value.Fields = []wire.Field{wire.Obj(1, true, []wire.Field{wire.S(1, true, 1)})}
		}, errCommand},
	} {
		t.Run(tc.name, func(t *testing.T) {
			c := command(6)
			tc.mutate(&c)
			_, err := normalize(c)
			if !errors.Is(err, tc.want) {
				t.Fatalf("got %v, want %v", err, tc.want)
			}
		})
	}
}
func FuzzNormalizeCommand(f *testing.F) {
	f.Add([]byte("npc_2_SkillList"))
	f.Add([]byte{0})
	f.Fuzz(func(t *testing.T, data []byte) {
		c := command(6)
		c.Payload[1].Value.Data = data
		if len(data) != 0 {
			switch data[0] % 7 {
			case 1:
				c.Payload = append(c.Payload, c.Payload[0])
			case 2:
				c.Payload[0].Number = uint32(data[0])
			case 3:
				c.Payload[0] = wire.Data(64, true, string(data))
			case 4:
				c.Payload[0].Value.Fields = []wire.Field{wire.Data(1, true, string(data))}
			case 5:
				c.Verb = uint64(data[0])
			case 6:
				ttl := uint64(data[0]) * 1000
				c.QueueTTLMS = &ttl
			}
		}
		got, err := normalize(c)
		if err == nil {
			again, err := normalize(got)
			if err != nil || !reflect.DeepEqual(got, again) {
				t.Fatalf("normalized command failed to normalize again: %v", err)
			}
		}
	})
}
