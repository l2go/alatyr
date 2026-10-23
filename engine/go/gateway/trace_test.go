// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"encoding/json"
	"strings"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestDebugSummaryNeverCopiesParticipantText(t *testing.T) {
	session, err := proto.New(proto.Config{Results: 4, Pending: 1, IntentBytes: 256, EvidenceMS: 10, QueueMS: 10})
	if err != nil {
		t.Fatal(err)
	}
	var captured []Summary
	g := &Gateway{cfg: Config{Now: func() time.Time { return time.Time{} }, Debug: true, Diagnostic: func(v Summary) { captured = append(captured, v) }}}
	world := &game{gate: g, id: 1, attempts: session}
	c := &connection{id: 2}
	secret := "principal-password-private-action"
	hello := record(wire.Hello, wire.U(1, true, 1), wire.U(2, true, 1), wire.U(4, true, uint64(wire.ParticipantPerson)), wire.U(5, true, uint64(wire.ModeAct)), wire.Data(6, false, secret))
	world.trace(c, hello, 100, true)
	outcome := record(wire.Result, wire.U(1, true, 1), wire.U(2, true, uint64(wire.StageRefused)), wire.U(3, true, uint64(wire.ReasonInvalid)), wire.Obj(4, false, []wire.Field{wire.Data(1, true, secret), wire.U(2, true, uint64(wire.ProvenanceSystem))}))
	world.trace(c, outcome, 100, false)
	raw, err := json.Marshal(captured)
	if err != nil || strings.Contains(string(raw), secret) || len(captured) != 2 || !captured[0].Debug || !captured[0].Redacted {
		t.Fatal(string(raw), err)
	}
}
