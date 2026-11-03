// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gatewaytest_test

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

type transcript struct {
	frames   []string
	handoffs []backend.Handoff
}

type oracle struct {
	Digest   string
	Records  []string
	Handoffs []backend.Handoff
}

func checkOracle(t *testing.T, key string, got transcript) {
	t.Helper()
	sum := sha256.Sum256([]byte(strings.Join(got.frames, "\n")))
	want := oracle{Digest: hex.EncodeToString(sum[:]), Handoffs: got.handoffs}
	codec, err := meaning.NewCodec(gatewaytest.Config())
	if err != nil {
		t.Fatal(err)
	}
	for _, frame := range got.frames {
		if strings.HasPrefix(frame, "peer:") {
			want.Records = append(want.Records, frame)
			continue
		}
		b, err := hex.DecodeString(frame)
		if err != nil {
			t.Fatal(err)
		}
		rs, err := codec.Decode(b)
		if err != nil {
			t.Fatal(err)
		}
		r := rs[0]
		want.Records = append(want.Records, fmt.Sprintf("%d:%d:%d:%d", r.Type, gatewaytest.U(r, 1), gatewaytest.U(r, 2), gatewaytest.U(r, 32)))
	}
	if os.Getenv("GATEWAY_PRINT_ORACLE") == "1" {
		b, err := json.Marshal(struct {
			Key    string
			Oracle oracle
		}{key, want})
		if err != nil {
			t.Fatal(err)
		}
		t.Log("GATEWAY_ORACLE " + string(b))
		return
	}
	b, err := os.ReadFile(filepath.Join("testdata", "contracts.json"))
	if err != nil {
		t.Fatal(err)
	}
	var all map[string]oracle
	if err = json.Unmarshal(b, &all); err != nil {
		t.Fatal(err)
	}
	frozen, ok := all[key]
	if !ok {
		t.Fatalf("missing gateway oracle %s", key)
	}
	if frozen.Digest != want.Digest || !slices.Equal(frozen.Records, want.Records) || !slices.Equal(frozen.Handoffs, want.Handoffs) {
		t.Fatalf("gateway transcript changed: %+v, want %+v", want, frozen)
	}
}

func both(t *testing.T, run func(*rig)) {
	t.Helper()
	runBoth(t, run, true)
}

// New contract scenarios use explicit assertions and fixture/Client parity.
// Frozen historical entries are changed only for the retired refusal rule.
func runBoth(t *testing.T, run func(*rig), frozen bool) {
	t.Helper()
	var want transcript
	for _, client := range []bool{false, true} {
		t.Run(map[bool]string{false: "fixture", true: "Client"}[client], func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				r := newRig(t, client, meaning.Source{Dialogue: true, Pickup: true, Learning: true, Return: true})
				defer func() {
					if err := r.g.Close(); err != nil {
						t.Error(err)
					}
					r.p.Close()
				}()
				run(r)
				r.sync()
				codec, err := meaning.NewCodec(r.c)
				if err != nil {
					t.Fatal(err)
				}
				var got transcript
				// Preserve each participant's receive order, including retired
				// scopes and observers. Registration order is deterministic;
				// there is no invented total order across independent sockets.
				for i, peer := range r.peers {
					peer.Drain()
					if i != 0 {
						got.frames = append(got.frames, fmt.Sprintf("peer:%d", i))
					}
					for _, rec := range peer.Records {
						if rec.Type == wire.Welcome {
							// Game-session IDs are opaque per-construction values.
							rec.Fields = slices.Clone(rec.Fields)
							for i, f := range rec.Fields {
								if f.Number == 4 {
									rec.Fields[i] = wire.U(4, true, 1)
								}
							}
						}
						b, err := codec.Encode([]wire.Record{rec})
						if err != nil {
							t.Fatal(err)
						}
						got.frames = append(got.frames, hex.EncodeToString(b))
					}
				}
				r.mu.Lock()
				got.handoffs = slices.Clone(r.handoffs)
				r.mu.Unlock()
				if !client {
					if frozen {
						checkOracle(t, strings.TrimSuffix(t.Name(), "/fixture"), got)
					}
					want = got
				} else {
					if !slices.Equal(got.frames, want.frames) {
						for i := range min(len(got.frames), len(want.frames)) {
							if got.frames[i] != want.frames[i] {
								t.Errorf("record %d\ngot  %s\nwant %s", i, got.frames[i], want.frames[i])
								break
							}
						}
						if len(got.frames) != len(want.frames) {
							t.Errorf("record count %d want %d", len(got.frames), len(want.frames))
						}
					}
					if !slices.Equal(got.handoffs, want.handoffs) {
						t.Errorf("handoffs %+v want %+v", got.handoffs, want.handoffs)
					}
				}
			})
		})
	}
}

func (r *rig) expect(n, requested, effective uint64, stage wire.Stage, reason wire.Reason, delivery wire.Delivery, causes int) {
	r.t.Helper()
	r.p.Result(n, stage)
	r.sync()
	terminals, causal := 0, 0
	for _, rec := range r.p.Records {
		if rec.Type == wire.Result && gatewaytest.U(rec, 1) == n {
			st := wire.Stage(gatewaytest.U(rec, 2))
			if st != wire.StageSent || stage == wire.StageSent {
				terminals++
			}
			if st == stage && (gatewaytest.U(rec, 3) != uint64(reason) || gatewaytest.U(rec, 5) != requested || gatewaytest.U(rec, 6) != effective || gatewaytest.U(rec, 7) != uint64(delivery)) {
				r.t.Fatalf("unexpected result %+v", rec)
			}
		}
		if rec.Type == wire.Fact && gatewaytest.U(rec, 32) == n {
			causal++
		}
	}
	if terminals != 1 || causal != causes {
		r.t.Fatalf("terminal/cause=%d/%d want 1/%d", terminals, causal, causes)
	}
}

func TestG01Dialogue(t *testing.T) {
	for _, confirmed := range []bool{false, true} {
		t.Run(map[bool]string{false: "helper", true: "confirmed"}[confirmed], func(t *testing.T) {
			both(t, func(r *rig) {
				if confirmed {
					r.emit(meaning.Event{Kind: meaning.Targeted, ID: 1, Target: 2})
				}
				r.p.Send(r.intent(1, 11, r.ref(2)))
				r.sync()
				if !confirmed {
					log := r.log()
					if len(log) != 1 || log[0].Command.Verb != 2 {
						r.t.Fatalf("helper %+v", log)
					}
					r.reply(log[0], wire.StageDone, wire.ReasonNone, meaning.Event{Kind: meaning.Targeted, ID: 1, Target: 2}, targetEffect(2))
				}
				r.p.Result(1, wire.StageSent)
				log := r.log()
				want := 1
				if !confirmed {
					want = 2
				}
				if len(log) != want || log[len(log)-1].Command.Verb != 5 {
					r.t.Fatalf("effect %+v", log)
				}
				r.reply(log[len(log)-1], wire.StageDone, wire.ReasonNone, meaning.Event{Kind: meaning.Spoke, ID: 2, Text: "Reply", Complete: true, Lines: []meaning.Line{{Label: "Next", Say: "test-next"}}})
				r.expect(1, 11, 5, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
				// The original live speech must precede its terminal Result.
				factIdx, resultIdx := -1, -1
				for i, rec := range r.p.Records {
					if rec.Type == wire.Fact && gatewaytest.U(rec, 32) == 1 {
						factIdx = i
					}
					if rec.Type == wire.Result && gatewaytest.U(rec, 2) == uint64(wire.StageDone) {
						resultIdx = i
					}
				}
				if factIdx < 0 || factIdx >= resultIdx {
					r.t.Fatal("facts must precede result")
				}
				for _, rec := range r.p.Records {
					b, _ := r.pCodec().Encode([]wire.Record{rec})
					if bytes.Contains(b, []byte("test-next")) {
						r.t.Fatal("saved action escaped")
					}
				}
			})
		})
	}
}

func (r *rig) pCodec() *meaning.Codec {
	c, err := meaning.NewCodec(r.c)
	if err != nil {
		r.t.Fatal(err)
	}
	return c
}

func TestG03SinglePickupAndDuplicate(t *testing.T) {
	both(t, func(r *rig) {
		ref := r.ref(9)
		ref.Number = 20
		req := r.intent(1, 4, ref)
		r.p.Send(req)
		r.p.Result(1, wire.StageSent)
		log := r.log()
		if len(log) != 1 || log[0].Command.Verb != 4 {
			r.t.Fatalf("pickup writes %+v", log)
		}
		r.reply(log[0], wire.StageDone, wire.ReasonNone, meaning.Event{Kind: meaning.Took, ID: 1, Target: 9}, itemEffect())
		r.expect(1, 4, 4, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		before := len(r.p.Records)
		r.p.Send(req)
		r.sync()
		if len(r.log()) != 1 {
			r.t.Fatal("duplicate wrote")
		}
		if len(r.p.Records) != before+1 || gatewaytest.U(r.p.Records[before], 2) != uint64(wire.StageDone) {
			r.t.Fatal("duplicate must replay only result")
		}
	})
}

func TestG07LateRefusal(t *testing.T) {
	both(t, func(r *rig) {
		r.p.Send(r.intent(1, 5, r.ref(2)))
		r.p.Result(1, wire.StageSent)
		log := r.log()
		r.reply(log[0], wire.StageRefused, wire.ReasonWorld, meaning.Event{})
		r.expect(1, 5, 5, wire.StageRefused, wire.ReasonWorld, wire.DeliveryNone, 0)
		before := len(r.p.Records)
		// A retained duplicate receipt is diagnostic only on both implementations.
		v := r.receipt(log[0], wire.StageRefused, wire.ReasonWorld)
		v.Replay = true
		r.send(bridge.Batch{Receipts: []bridge.Receipt{v}})
		if len(r.p.Records) != before {
			r.t.Fatal("late receipt rewrote terminal")
		}
	})
}

func TestG02HelperFailures(t *testing.T) {
	for _, kind := range []string{"refused", "quiet", "error", "target-only", "receipt-only"} {
		t.Run(kind, func(t *testing.T) {
			both(t, func(r *rig) {
				r.p.Send(r.intent(1, 11, r.ref(2)))
				r.sync()
				log := r.log()
				if len(log) != 1 || log[0].Command.Verb != 2 {
					r.t.Fatal(log)
				}
				reason := wire.ReasonTargetUnconfirmed
				switch kind {
				case "refused":
					r.reply(log[0], wire.StageRefused, wire.ReasonWorld, meaning.Event{})
				case "quiet":
					r.reply(log[0], wire.StageUnknown, wire.ReasonTimeout, meaning.Event{})
					r.advance(1500 * time.Millisecond)
				case "error":
					r.reply(log[0], wire.StageUnknown, wire.ReasonBackendError, meaning.Event{})
					reason = wire.ReasonBackendError
				case "target-only":
					r.reply(log[0], wire.StageUnknown, wire.ReasonTimeout, meaning.Event{Kind: meaning.Targeted, ID: 1, Target: 2})
				case "receipt-only":
					r.reply(log[0], wire.StageDone, wire.ReasonNone, meaning.Event{}, targetEffect(2))
					r.advance(1500 * time.Millisecond)
				}
				r.expect(1, 11, 5, wire.StageUnknown, reason, wire.DeliveryPartial, 0)
				if len(r.log()) != 1 {
					r.t.Fatal("effect escaped helper")
				}
			})
		})
	}
}
