// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package bridge

import (
	"context"
	"encoding/json"
	"errors"
	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"net"
	"os"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

type endpoint struct {
	codec *profile.Codec
	conn  net.Conn
	t     *testing.T
}

func (e endpoint) read() wire.Record {
	rs, err := e.codec.Read(e.conn)
	if err != nil {
		e.t.Error("endpoint read", err)
		return wire.Record{}
	}
	if len(rs) != 1 {
		e.t.Error("endpoint frame count")
		return wire.Record{}
	}
	return rs[0]
}
func (e endpoint) send(rs ...wire.Record) bool {
	if err := e.codec.Write(e.conn, rs); err != nil {
		e.t.Error("endpoint write", err)
		return false
	}
	return true
}
func opened(epoch uint64) wire.Record {
	var limits []wire.Field
	for n, v := range profile.AdvertisedLimits() {
		limits = append(limits, wire.U(n, true, v))
	}
	return wire.Record{Type: profile.Opened, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 7), wire.Data(3, true, "test-world"), wire.U(4, true, epoch), wire.U(5, true, 0), wire.Obj(6, true, limits), wire.U(7, true, 1), wire.Data(8, true, profile.Name)}}
}
func rawFact(seq, epoch, kind uint64, extra ...wire.Field) wire.Record {
	return wire.Record{Type: wire.Fact, Fields: append([]wire.Field{wire.U(1, true, kind), wire.U(2, true, seq), wire.S(3, true, int64(seq)), wire.U(4, true, epoch), wire.Data(5, true, factDomain(kind)), wire.U(6, true, 1)}, extra...)}
}
func cp(phase, epoch, ready uint64) wire.Record {
	return wire.Record{Type: wire.Checkpoint, Fields: []wire.Field{wire.U(1, true, phase), wire.U(2, true, epoch), wire.U(3, true, ready)}}
}
func (e endpoint) handshake(epoch, seq uint64, cached ...wire.Record) {
	request := e.read()
	want := profile.Open
	if epoch > 1 {
		want = profile.Resume
	}
	if request.Type != want {
		e.t.Errorf("first record %d", request.Type)
		return
	}
	e.send(cp(1, epoch, 0), rawFact(seq, epoch, 1, wire.S(64, true, 7), wire.U(19, true, 1)), cp(2, epoch, 1))
	if len(cached) > 0 {
		e.send(cached...)
	}
	e.send(opened(epoch))
}
func (e endpoint) close() {
	r := e.read()
	if r.Type != profile.Close {
		e.t.Errorf("expected Close, got %d", r.Type)
		return
	}
	e.send(wire.Record{Type: wire.Bye, Fields: []wire.Field{wire.U(1, true, 0)}})
}
func testConfig(t *testing.T, scripts ...func(endpoint)) (Config, *atomic.Int32, func()) {
	t.Helper()
	var index atomic.Int32
	var wg sync.WaitGroup
	codec, err := profile.NewProfileCodec()
	if err != nil {
		t.Fatal(err)
	}
	cfg := Config{Secret: [32]byte{1}, DialContext: func(ctx context.Context, network, address string) (net.Conn, error) {
		i := int(index.Add(1)) - 1
		if i >= len(scripts) {
			return nil, errors.New("unexpected dial")
		}
		a, b := net.Pipe()
		wg.Add(1)
		go func() {
			defer wg.Done()
			defer b.Close()
			b.SetDeadline(time.Now().Add(3 * time.Second))
			scripts[i](endpoint{codec, b, t})
		}()
		return a, nil
	}}
	return cfg, &index, func() { wg.Wait() }
}
func openTest(t *testing.T, cfg Config) *Client {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	c, s, e := Open(ctx, cfg, Identity{"guest", "Guest"})
	if e != nil {
		t.Fatal(e)
	}
	if !s.Ready || s.GatewayEpoch != 1 || s.FenceSeq != 1 || len(s.Facts) != 1 {
		t.Fatal("initial snapshot")
	}
	return c
}
func nextUpdate(t *testing.T, c *Client) Update {
	t.Helper()
	select {
	case u, ok := <-c.Updates():
		if !ok {
			t.Fatal("updates closed")
		}
		return u
	case <-time.After(2 * time.Second):
		t.Fatal("missing update")
		return Update{}
	}
}
func sent(n, verb uint64) wire.Record {
	return wire.Record{Type: wire.Result, Fields: []wire.Field{wire.U(1, true, n), wire.U(2, true, 1), wire.U(3, true, 0), wire.U(5, true, verb), wire.U(6, true, verb), wire.U(7, true, 2)}}
}
func walk(key uint64) Command {
	return Command{Key: key, Verb: 1, Payload: []wire.Field{wire.At(11, true, 1, 2, 3)}}
}

func TestHandoffProofAndOrder(t *testing.T) {
	release := make(chan struct{})
	received := make(chan struct{})
	cfg, _, wait := testConfig(t, func(e endpoint) {
		e.handshake(1, 1)
		r := e.read()
		if r.Type != wire.Intent || profile.U(r, 1) != 1 || profile.U(r, 3) != 1 {
			t.Error("intent numbering")
		}
		e.send(rawFact(2, 1, 2, wire.S(64, true, 7)))
		close(received)
		<-release
		e.send(sent(1, 1))
		e.close()
	})
	c := openTest(t, cfg)
	n, e := c.Write(context.Background(), walk(100))
	if e != nil || n != 1 {
		t.Fatal(n, e)
	}
	<-received
	select {
	case <-c.Updates():
		t.Fatal("fact escaped before receipt")
	default:
	}
	close(release)
	u := nextUpdate(t, c)
	if u.Kind != LiveBatch || len(u.Batch.Records) != 1 || len(u.Batch.Receipts) != 1 || u.Batch.Receipts[0].Key != 100 || u.Batch.Receipts[0].Stage != wire.StageSent || len(u.Batch.ReceivedAt) != 1 {
		t.Fatal("proof batch", u)
	}
	if e = c.Close(); e != nil {
		t.Fatal(e)
	}
	if e = c.Close(); e != nil {
		t.Fatal("idempotent close", e)
	}
	wait()
}
func TestResumeIsCheckpointNotReplay(t *testing.T) {
	handoff := make(chan struct{})
	cfg, dials, wait := testConfig(t,
		func(e endpoint) { e.handshake(1, 1); e.read(); <-handoff },
		func(e endpoint) {
			e.handshake(2, 2, sent(1, 1))
			r := e.read()
			if r.Type != wire.Intent || profile.U(r, 1) != 2 || profile.U(r, 3) != 2 {
				t.Error("recovery reexecuted or reset numbering")
			}
			e.send(sent(2, 1))
			e.close()
		},
	)
	c := openTest(t, cfg)
	if n, e := c.Write(context.Background(), walk(9)); n != 1 || e != nil {
		t.Fatal(n, e)
	}
	close(handoff)
	if u := nextUpdate(t, c); u.Kind != Detached {
		t.Fatal(u)
	}
	if n, e := c.Write(context.Background(), walk(10)); n != 0 || e == nil {
		t.Fatal("detached write")
	}
	rec, e := c.Resume(context.Background())
	if e != nil {
		t.Fatal(e)
	}
	if rec.Snapshot.GatewayEpoch != 2 || len(rec.Receipts) != 1 || !rec.Receipts[0].Replay || rec.Receipts[0].Key != 9 {
		t.Fatal("recovery", rec)
	}
	if n, e := c.Write(context.Background(), walk(10)); n != 2 || e != nil {
		t.Fatal(n, e)
	}
	u := nextUpdate(t, c)
	if len(u.Batch.Receipts) != 1 || u.Batch.Receipts[0].Replay {
		t.Fatal(u)
	}
	if e = c.Close(); e != nil {
		t.Fatal(e)
	}
	wait()
	if dials.Load() != 2 {
		t.Fatal("automatic retry")
	}
}
func TestLostResumeConfirmationFailsClosed(t *testing.T) {
	cfg, dials, wait := testConfig(t, func(e endpoint) { e.handshake(1, 1) }, func(e endpoint) {
		if e.read().Type != profile.Resume {
			t.Error("resume")
		}
	})
	c := openTest(t, cfg)
	if u := nextUpdate(t, c); u.Kind != Detached {
		t.Fatal(u)
	}
	_, e := c.Resume(context.Background())
	var f *Error
	if !errors.As(e, &f) || f.Kind != RemoteCloseUnconfirmed {
		t.Fatal(e)
	}
	if _, e = c.Resume(context.Background()); e == nil {
		t.Fatal("guessed next epoch")
	}
	c.Close()
	wait()
	if dials.Load() != 2 {
		t.Fatal("extra dial")
	}
}
func TestParallelWriterRejectedBeforeRegistration(t *testing.T) {
	allowRead := make(chan struct{})
	cfg, _, wait := testConfig(t, func(e endpoint) { e.handshake(1, 1); <-allowRead; e.read(); e.close() })
	c := openTest(t, cfg)
	done := make(chan response, 1)
	go func() { n, e := c.Write(context.Background(), walk(1)); done <- response{n: n, err: e} }()
	deadline := time.Now().Add(time.Second)
	for !c.writeBusy.Load() && time.Now().Before(deadline) {
		time.Sleep(time.Millisecond)
	}
	if n, e := c.Write(context.Background(), walk(2)); n != 0 || e == nil {
		t.Fatal("second writer admitted", n, e)
	}
	close(allowRead)
	r := <-done
	if r.n != 1 || r.err != nil {
		t.Fatal(r)
	}
	c.Close()
	wait()
}
func TestCloseInterruptsPartialWrite(t *testing.T) {
	release := make(chan struct{})
	cfg, _, wait := testConfig(t, func(e endpoint) { e.handshake(1, 1); <-release })
	c := openTest(t, cfg)
	done := make(chan response, 1)
	go func() { n, e := c.Write(context.Background(), walk(1)); done <- response{n: n, err: e} }()
	deadline := time.Now().Add(time.Second)
	for !c.writeBusy.Load() && time.Now().Before(deadline) {
		time.Sleep(time.Millisecond)
	}
	start := time.Now()
	e := c.Close()
	if time.Since(start) > time.Second || e == nil {
		t.Fatal("close budget or false remote success", e)
	}
	r := <-done
	if r.err == nil {
		t.Fatal("blocked write not interrupted")
	}
	close(release)
	wait()
}
func TestCanceledWritePreservesRegisteredNumber(t *testing.T) {
	release := make(chan struct{})
	cfg, _, wait := testConfig(t, func(e endpoint) { e.handshake(1, 1); <-release })
	c := openTest(t, cfg)
	ctx, cancel := context.WithTimeout(context.Background(), 25*time.Millisecond)
	defer cancel()
	n, e := c.Write(ctx, walk(1))
	if n != 1 || e == nil {
		t.Fatal("ambiguous write lost number", n, e)
	}
	if u := nextUpdate(t, c); u.Kind != Detached {
		t.Fatal(u)
	}
	c.Close()
	close(release)
	wait()
}
func TestBadSecondRecordIsAtomic(t *testing.T) {
	cfg, _, wait := testConfig(t, func(e endpoint) { e.handshake(1, 1); e.send(rawFact(2, 1, 2), rawFact(3, 2, 2)); e.close() })
	c := openTest(t, cfg)
	for u := range c.Updates() {
		if u.Kind == LiveBatch {
			t.Fatal("partially applied bad frame")
		}
	}
	c.Close()
	wait()
}
func TestCommandBoundsAndMatchingReceipt(t *testing.T) {
	for _, cmd := range []Command{{Key: 1, Verb: 11}, walk(0), {Key: 1, Verb: 7, Payload: []wire.Field{wire.S(14, true, 1), wire.S(16, true, 0), wire.S(66, true, 3)}}} {
		if validateCommand(cmd) == nil {
			t.Fatal("invalid command")
		}
	}
	payload := make([]wire.Field, 129)
	if _, ok := measured(payload, 0); ok {
		t.Fatal("field cap")
	}
	c := Command{Key: 1, Verb: 7, Payload: []wire.Field{wire.S(14, true, 1), wire.S(16, true, 2), wire.S(66, true, 1060)}}
	r := Receipt{Requested: 7, Effective: 7, Stage: wire.StageDone, Effects: []wire.Value{{Fields: []wire.Field{wire.U(1, true, 1), wire.S(2, true, 1060), wire.S(3, true, 70), wire.S(4, true, 2)}}, {Fields: []wire.Field{wire.U(1, true, 2), wire.S(4, true, -13)}}}}
	if e := checkReceipt(r, c); e != nil {
		t.Fatal(e)
	}
	r.Effects[0].Fields[3] = wire.S(4, true, 3)
	if checkReceipt(r, c) == nil {
		t.Fatal("wrong count receipt accepted")
	}
}
func TestConfigurationAndProposalAge(t *testing.T) {
	for _, address := range []string{"localhost:9016", "0.0.0.0:9016", "127.0.0.1:0", "127.0.0.1:65536", "[::1]:9016"} {
		_, e := normalize(Config{Endpoint: address, Secret: [32]byte{1}}, Identity{"guest", "Guest"})
		if e == nil {
			t.Fatal(address)
		}
	}
	origin := time.Now()
	received := origin.Add(20 * time.Second)
	expiry, ok := ProposalExpiry(origin, received, 1000)
	if !ok || !expiry.Equal(origin.Add(61*time.Second)) {
		t.Fatal(expiry)
	}
	if _, ok = ProposalExpiry(origin, received, 1<<63-1); ok {
		t.Fatal("overflow")
	}
}
func TestPortContracts(t *testing.T) {
	var file struct {
		Version   int
		Scenarios []struct {
			ID, Kind                      string
			Verb, Stage, Reason, Delivery uint64
			BridgeReason                  *uint64
			Effects                       *[]struct {
				Kind                                         uint64
				Item, Object, Delta, Skill, Old, New, Target int64
			}
		}
	}
	data, e := os.ReadFile("testdata/contracts.json")
	if e != nil {
		t.Fatal(e)
	}
	if e = json.Unmarshal(data, &file); e != nil {
		t.Fatal(e)
	}
	if file.Version != 1 || len(file.Scenarios) == 0 {
		t.Fatal("contracts")
	}
	for _, scenario := range file.Scenarios {
		t.Run(scenario.ID, func(t *testing.T) {
			cfg, _, wait := testConfig(t, func(ep endpoint) {
				ep.handshake(1, 1)
				request := ep.read()
				result := sent(profile.U(request, 1), profile.U(request, 2))
				result.Fields[1] = wire.U(2, true, scenario.Stage)
				result.Fields[2] = wire.U(3, true, scenario.Reason)
				result.Fields[5] = wire.U(7, true, scenario.Delivery)
				if scenario.BridgeReason != nil {
					result.Fields = append(result.Fields, wire.Data(64, true, profile.Namespace), wire.U(65, true, *scenario.BridgeReason))
				}
				if scenario.Effects != nil {
					var effects []wire.Value
					for _, effect := range *scenario.Effects {
						fields := []wire.Field{wire.U(1, true, effect.Kind)}
						switch effect.Kind {
						case 1:
							fields = append(fields, wire.S(2, true, effect.Item), wire.S(3, true, effect.Object), wire.S(4, true, effect.Delta))
						case 2:
							fields = append(fields, wire.S(4, true, effect.Delta))
						case 3:
							fields = append(fields, wire.S(5, true, effect.Skill), wire.S(6, true, effect.Old), wire.S(7, true, effect.New))
						case 5:
							fields = append(fields, wire.S(8, true, effect.Target))
						}
						effects = append(effects, wire.Value{Fields: fields})
					}
					result.Fields = append(result.Fields, wire.Items(66, true, effects))
				}
				ep.send(result)
				ep.close()
			})
			c := openTest(t, cfg)
			cmd := walk(1)
			if scenario.Verb != 0 {
				cmd.Verb = scenario.Verb
				switch cmd.Verb {
				case 2:
					cmd.Payload = []wire.Field{wire.S(64, true, 7)}
				case 5:
					cmd.Payload = []wire.Field{wire.S(64, true, 7)}
				case 6:
					cmd.Payload = []wire.Field{wire.S(64, true, 7), wire.Data(68, true, "choice")}
				case 7:
					cmd.Payload = []wire.Field{wire.S(14, true, 1), wire.S(16, true, 2), wire.S(66, true, 1060)}
				case 8:
					cmd.Payload = []wire.Field{wire.S(14, true, 1), wire.S(16, true, 2), wire.S(64, true, 70), wire.S(66, true, 1060)}
				case 9:
					cmd.Payload = nil
				case 12:
					cmd.Payload = []wire.Field{wire.S(64, true, 7), wire.Obj(18, true, []wire.Field{wire.S(1, true, 1), wire.S(2, true, 1)})}
				case 13:
					cmd.Payload = []wire.Field{wire.S(14, true, 0), wire.Obj(18, true, []wire.Field{wire.S(1, true, 1), wire.S(2, true, 1)})}
				}
			}
			if _, e = c.Write(context.Background(), cmd); e != nil {
				t.Fatal(e)
			}
			u := nextUpdate(t, c)
			if len(u.Batch.Receipts) != 1 || uint64(u.Batch.Receipts[0].Stage) != scenario.Stage || uint64(u.Batch.Receipts[0].Reason) != scenario.Reason || uint64(u.Batch.Receipts[0].Delivery) != scenario.Delivery {
				t.Fatal("raw receipt contract", u)
			}
			receipt := u.Batch.Receipts[0]
			if len(u.Batch.Records) != 0 || receipt.Key != 1 || receipt.N != 1 || receipt.GatewayEpoch != 1 || receipt.Replay {
				t.Fatal("receipt fabricated state or identity")
			}
			if (scenario.Effects != nil) != receipt.EffectsPresent {
				t.Fatal("effects presence")
			}
			if scenario.Effects != nil && len(*scenario.Effects) != len(receipt.Effects) {
				t.Fatal("effects count")
			}
			c.Close()
			wait()
		})
	}
}
