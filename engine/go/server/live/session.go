// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package live

import (
	"cmp"
	"errors"
	"fmt"
	"slices"
	"sync"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

const (
	participantPerson = wire.ParticipantPerson
	participantAgent  = wire.ParticipantAgent
	// The meadow floor; the authored world is flat here.
	floorZ = -3104
)

// ErrNotReady means the session has no control yet or the target is not seen.
var ErrNotReady = errors.New("live: not ready")

// Update is what a session reports: its current view, one log entry, or,
// last, that the session ended and why.
type Update struct {
	View  *View  `json:"view,omitempty"`
	Log   *Entry `json:"log,omitempty"`
	Ended string `json:"ended,omitempty"`
}

// View is the participant's current picture of the world.
type View struct {
	Ready   bool     `json:"ready"`
	Self    Object   `json:"self"`
	Objects []Object `json:"objects"`
	Dialog  *Dialog  `json:"dialog,omitempty"`
}

// Object is one seen object. ToX and ToY are its destination while it moves
// at Speed; Pace is the speed its description gives.
type Object struct {
	ID    int32  `json:"id"`
	Role  string `json:"role"`
	Name  string `json:"name"`
	X     int32  `json:"x"`
	Y     int32  `json:"y"`
	ToX   int32  `json:"toX"`
	ToY   int32  `json:"toY"`
	Speed int32  `json:"speed"`
	Pace  int32  `json:"-"`
	Dead  bool   `json:"dead,omitempty"`
	HP    int64  `json:"hp"`
	MaxHP int64  `json:"maxHp"`
}

// Dialog is the page an NPC shows and the choices it offers.
type Dialog struct {
	Speaker string   `json:"speaker"`
	Lines   []string `json:"lines"`
	Options []Option `json:"options"`
}

// Option is one dialogue choice.
type Option struct {
	ID    uint64 `json:"id"`
	Label string `json:"label"`
}

// Entry is one line of the protocol log. Kind is intent, result or fact. N is
// the attempt an intent or result belongs to, Cause the attempt a fact proves.
type Entry struct {
	Kind   string `json:"kind"`
	N      uint64 `json:"n,omitempty"`
	Cause  uint64 `json:"cause,omitempty"`
	Stage  string `json:"stage,omitempty"`
	Reason string `json:"reason,omitempty"`
	Text   string `json:"text"`
}

// Action is one thing the participant asks the world to do.
type Action struct {
	Verb   string `json:"verb"`
	Target int32  `json:"target,omitempty"`
	X      int32  `json:"x,omitempty"`
	Y      int32  `json:"y,omitempty"`
	Option uint64 `json:"option,omitempty"`
}

type tracked struct {
	ref meaning.Ref
	Object
}

// Session is one alatyr/1 participant over a stream.
type Session struct {
	codec  *meaning.Codec
	stream *gateway.Stream
	watch  func(Update)
	done   chan struct{}

	// sendMu orders every frame the session writes: an intent's number is
	// taken, logged and written as one step, so numbers leave in order.
	sendMu sync.Mutex
	// watchMu delivers one update at a time, from any goroutine.
	watchMu sync.Mutex

	mu    sync.Mutex
	err   error
	ready bool
	// epoch is the projection being shown; a checkpoint starts a new one.
	epoch   uint64
	control uint64
	seq     uint64
	self    Object
	objects map[int32]*tracked
	dialog  meaning.DecodedEvent
	pending map[uint64]string
}

func newSession(codec *meaning.Codec, stream *gateway.Stream, kind wire.Participant, watch func(Update)) (*Session, error) {
	s := &Session{codec: codec, stream: stream, watch: watch, done: make(chan struct{}), objects: map[int32]*tracked{}, pending: map[uint64]string{}}
	hello := wire.Record{Type: wire.Hello, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 1), wire.Data(3, true, meaning.Meaning), wire.U(4, true, uint64(kind)), wire.U(5, true, uint64(wire.ModeAct))}}
	if err := s.write(hello); err != nil {
		return nil, errors.Join(err, stream.Close())
	}
	go s.read()
	return s, nil
}

func (s *Session) write(r wire.Record) error {
	s.sendMu.Lock()
	defer s.sendMu.Unlock()
	return s.send(r)
}

// send writes one frame; the caller holds sendMu.
func (s *Session) send(r wire.Record) error {
	buf, err := s.codec.Encode([]wire.Record{r})
	if err != nil {
		return err
	}
	return s.stream.Write(buf, time.Now().Add(5*time.Second))
}

func (s *Session) read() {
	defer close(s.done)
	for {
		buf, err := s.stream.Read(time.Now().Add(time.Minute))
		if err == nil {
			var rs []wire.Record
			if rs, err = s.codec.Decode(buf); err == nil {
				err = s.consume(rs)
			}
		}
		if err != nil {
			s.mu.Lock()
			s.err, s.ready, s.control = err, false, 0
			s.mu.Unlock()
			s.emit(Update{Ended: err.Error()})
			return
		}
	}
}

// Done is closed when the session ends; Err then says why.
func (s *Session) Done() <-chan struct{} { return s.done }

// Err returns why the session ended, or nil while it runs.
func (s *Session) Err() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.err
}

// Close ends the session's stream and waits for its reader.
func (s *Session) Close() error {
	err := s.stream.Close()
	<-s.done
	return err
}

func (s *Session) emit(u Update) {
	if s.watch == nil {
		return
	}
	s.watchMu.Lock()
	defer s.watchMu.Unlock()
	s.watch(u)
}

func (s *Session) consume(rs []wire.Record) error {
	var logs []Entry
	changed, pong := false, uint64(0)
	needPong := false
	s.mu.Lock()
	for _, r := range rs {
		switch r.Type {
		case wire.Bye:
			s.mu.Unlock()
			return fmt.Errorf("live: bye %d", fieldU(r, 1))
		case wire.Checkpoint:
			// A checkpoint replaces the picture: nothing of the old one
			// survives Begin, and the new one is ready only at its End.
			switch epoch := fieldU(r, 2); wire.CheckpointPhase(fieldU(r, 1)) {
			case wire.CheckpointBegin:
				s.epoch, s.ready = epoch, false
				s.self, s.objects, s.dialog = Object{}, map[int32]*tracked{}, meaning.DecodedEvent{}
				changed = true
			case wire.CheckpointEnd:
				s.ready, changed = epoch == s.epoch && fieldU(r, 3) == 1, true
			}
		case wire.Ping:
			needPong, pong = true, fieldU(r, 1)
		case wire.Control:
			// A grant names the next attempt number; a reconnected session
			// continues the participant's numbering from there.
			switch wire.ControlOp(fieldU(r, 1)) {
			case wire.ControlGranted:
				s.control, s.seq = fieldU(r, 2), max(fieldU(r, 3), 1)-1
			case wire.ControlLost:
				s.control = 0
			}
			changed = true
		case wire.Result:
			if e, ok := s.result(r); ok {
				logs = append(logs, e)
			}
		case wire.Fact:
			ev, err := meaning.EventValue(r)
			if err != nil || ev.Context.Epoch != s.epoch {
				continue
			}
			if e, ok := s.absorb(ev); ok {
				logs = append(logs, e)
			}
			changed = true
		}
	}
	var view *View
	if changed {
		view = s.view()
	}
	s.mu.Unlock()
	if needPong {
		if err := s.write(wire.Record{Type: wire.Pong, Fields: []wire.Field{wire.U(1, true, pong)}}); err != nil {
			return err
		}
	}
	for i := range logs {
		s.emit(Update{Log: &logs[i]})
	}
	if view != nil {
		s.emit(Update{View: view})
	}
	return nil
}

func (s *Session) result(r wire.Record) (Entry, bool) {
	n := fieldU(r, 1)
	verbName, open := s.pending[n]
	if !open {
		return Entry{}, false
	}
	stage, reason := wire.Stage(fieldU(r, 2)), wire.Reason(fieldU(r, 3))
	verb, _ := meaning.VerbByCode(fieldU(r, 6))
	if stage != wire.StageSent || !verb.Waiting {
		delete(s.pending, n)
	}
	e := Entry{Kind: "result", N: n, Stage: stageName(stage), Text: fmt.Sprintf("#%d %s: %s", n, verbName, stageName(stage))}
	if reason != wire.ReasonNone {
		e.Reason = reasonName(reason)
		e.Text += " (" + e.Reason + ")"
	}
	return e, true
}

// absorb updates the view from one fact and returns a log entry for facts
// worth reading: everything except movement and refreshes.
func (s *Session) absorb(ev meaning.DecodedEvent) (Entry, bool) {
	e := ev.Event
	o := s.object(e)
	switch e.Kind {
	case meaning.Appeared, meaning.Dropped:
		// The checkpoint describes the world as found; only later arrivals
		// are news.
		logged := s.ready || e.Mine
		if o != nil {
			o.Role, o.X, o.Y, o.ToX, o.ToY, o.Speed = e.Role, e.X, e.Y, e.X, e.Y, 0
			if e.Speed != 0 {
				o.Pace = e.Speed
			}
			if e.Name != "" {
				o.Name = e.Name
			}
			if !e.Mine {
				s.objects[e.ID].ref = ev.Context.Ref
			}
		}
		if !logged {
			return Entry{}, false
		}
	case meaning.Gone:
		name := s.name(e.ID)
		delete(s.objects, e.ID)
		return s.entry(ev, name+" is out of sight"), !e.Mine
	case meaning.Took:
		delete(s.objects, e.Target)
	case meaning.Moved:
		// A move carries no speed of its own; the description gave it.
		if o != nil {
			o.X, o.Y, o.ToX, o.ToY, o.Speed = e.X, e.Y, e.ToX, e.ToY, cmp.Or(e.Speed, o.Pace)
		}
		return Entry{}, false
	case meaning.Stopped, meaning.Corrected:
		if o != nil {
			o.X, o.Y, o.ToX, o.ToY, o.Speed = e.X, e.Y, e.X, e.Y, 0
		}
		return Entry{}, false
	case meaning.Vitals:
		if o != nil && e.Stats != nil {
			if e.Stats.HP != nil {
				o.HP = *e.Stats.HP
			}
			if e.Stats.MaxHP != nil {
				o.MaxHP = *e.Stats.MaxHP
			}
		}
		return Entry{}, false
	case meaning.Died:
		if o != nil {
			o.Dead = true
		}
	case meaning.Revived:
		if o != nil {
			o.Dead = false
		}
	case meaning.Spoke:
		s.dialog = ev
	}
	text, ok := describe(e, s.name)
	if !ok {
		return Entry{}, false
	}
	return s.entry(ev, text), true
}

func (s *Session) entry(ev meaning.DecodedEvent, text string) Entry {
	e := Entry{Kind: "fact", Text: text}
	if ev.Context.Cause != nil {
		e.Cause = *ev.Context.Cause
	}
	return e
}

// object returns the tracked object a fact is about, creating it on
// appearance; the participant's own facts update its self.
func (s *Session) object(e meaning.Event) *Object {
	if e.Mine {
		if e.Name != "" {
			s.self.Name = e.Name
		}
		s.self.ID, s.self.Role = e.ID, meaning.RolePlayer
		return &s.self
	}
	if e.ID == s.self.ID && s.self.ID != 0 {
		return &s.self
	}
	t, ok := s.objects[e.ID]
	if !ok {
		if e.Kind != meaning.Appeared && e.Kind != meaning.Dropped {
			return nil
		}
		t = &tracked{Object: Object{ID: e.ID}}
		s.objects[e.ID] = t
	}
	return &t.Object
}

func (s *Session) name(id int32) string {
	if id == s.self.ID {
		return "you"
	}
	if t, ok := s.objects[id]; ok && t.Name != "" {
		return t.Name
	}
	return fmt.Sprintf("object %d", id)
}

func (s *Session) view() *View {
	v := &View{Ready: s.ready, Self: s.self, Objects: make([]Object, 0, len(s.objects))}
	for _, t := range s.objects {
		v.Objects = append(v.Objects, t.Object)
	}
	slices.SortFunc(v.Objects, func(a, b Object) int { return int(a.ID) - int(b.ID) })
	if d := s.dialog; d.Context.Dialog != 0 {
		page := &Dialog{Speaker: s.name(d.Event.ID)}
		for _, l := range d.Event.Lines {
			page.Lines = append(page.Lines, l.Label)
		}
		for _, o := range d.Context.Options {
			page.Options = append(page.Options, Option{ID: o.ID, Label: o.Label})
		}
		v.Dialog = page
	}
	return v
}

// View returns the participant's current picture of the world.
func (s *Session) View() View {
	s.mu.Lock()
	defer s.mu.Unlock()
	return *s.view()
}

// Do sends one action as the next numbered intent and logs it. Calls may
// come from several goroutines; each takes its number, logs its intent and
// writes it as one step, so intents leave in number order. It returns
// ErrNotReady while the participant has no control or no ready picture, or
// when the target is not seen. watch must not call Do.
func (s *Session) Do(a Action) error {
	s.sendMu.Lock()
	defer s.sendMu.Unlock()
	s.mu.Lock()
	rec, text, n, err := s.intent(a)
	if err == nil {
		// The number advances only with a frame sent: the gateway closes a
		// session that skips one.
		s.seq = n
		s.pending[n] = a.Verb
	}
	s.mu.Unlock()
	if err != nil {
		return err
	}
	// The intent is logged before it is written, so its result cannot be
	// reported first.
	s.emit(Update{Log: &Entry{Kind: "intent", N: n, Text: fmt.Sprintf("#%d %s", n, text)}})
	if err := s.send(rec); err != nil {
		// A stream that cannot take a frame is lost: the participant stops
		// acting, and closing the stream ends the reader, which reports it.
		s.mu.Lock()
		s.ready, s.control = false, 0
		delete(s.pending, n)
		s.mu.Unlock()
		return errors.Join(err, s.stream.Close())
	}
	return nil
}

// intent builds the record for a; the caller holds mu.
func (s *Session) intent(a Action) (wire.Record, string, uint64, error) {
	if !s.ready || s.control == 0 {
		return wire.Record{}, "", 0, ErrNotReady
	}
	cmd := meaning.Command{Kind: a.Verb}
	ctx := meaning.CommandContext{Control: s.control, N: s.seq + 1}
	text := a.Verb
	switch a.Verb {
	case meaning.Walk:
		cmd.X, cmd.Y, cmd.Z = a.X, a.Y, floorZ
		text = fmt.Sprintf("walk to %d, %d", a.X, a.Y)
	case meaning.Aim, meaning.Talk, meaning.Strike, meaning.Engage, meaning.Focus, meaning.Cast, meaning.Take:
		t, ok := s.objects[a.Target]
		if !ok {
			return wire.Record{}, "", 0, ErrNotReady
		}
		cmd.Target = t.ref.ID
		if a.Verb == meaning.Take {
			ctx.Item = t.ref
		} else {
			ctx.Target = t.ref
		}
		if a.Verb == meaning.Cast {
			cmd.Item, ctx.SkillLevel = 3, 1
		}
		text = a.Verb + " " + t.Name
	case meaning.Say:
		if s.dialog.Context.Dialog == 0 {
			return wire.Record{}, "", 0, ErrNotReady
		}
		ctx.Dialog, ctx.Option = s.dialog.Context.Dialog, a.Option
		for _, o := range s.dialog.Context.Options {
			if o.ID == a.Option {
				text = "say “" + o.Label + "”"
			}
		}
	}
	rec, err := meaning.CommandRecord(cmd, ctx)
	return rec, text, ctx.N, err
}

func fieldU(r wire.Record, n uint32) uint64 {
	v, _ := wire.Find(r.Fields, n)
	return v.U
}

// Pending returns how many of the participant's attempts await a result.
func (s *Session) Pending() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return len(s.pending)
}
