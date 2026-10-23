// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"unsafe"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// BackendStorageBytes conservatively accounts retained projection/table data.
// Map structural peaks survive deletion/relocation: clear does not promise
// release of a grown Go map. This is not an RSS sampler or a runtime limit.
func (s *State) BackendStorageBytes() int {
	lengths := [9]int{len(s.actors), len(s.motion), len(s.dead), len(s.vitals), len(s.known), len(s.inventory), len(s.quests), len(s.retained), s.observed.Len()}
	sizes := [9]int{int(unsafe.Sizeof(Event{})), int(unsafe.Sizeof(Event{})), 1, 8, int(unsafe.Sizeof(Skill{})), int(unsafe.Sizeof(Good{})), int(unsafe.Sizeof(QuestState{})), int(unsafe.Sizeof(wire.Record{})), int(unsafe.Sizeof(Observed{}))}
	n := 4096
	for i, length := range lengths {
		if length > s.backendPeaks[i] {
			s.backendPeaks[i] = length
		}
		n += 2 * s.backendPeaks[i] * (sizes[i] + 48)
	}
	for _, e := range s.actors {
		n += eventStorage(e)
	}
	for _, e := range s.motion {
		n += eventStorage(e)
	}
	for _, v := range s.vitals {
		if v != nil {
			n += int(unsafe.Sizeof(*v)) + 7*8
		}
	}
	for _, v := range s.inventory {
		n += len(v.Name)
	}
	for _, v := range s.quests {
		n += len(v.Name) + len(v.State)
	}
	for key, v := range s.retained {
		n += len(key) + fieldStorage(v.Fields)
	}
	n += s.proposals.StorageBytes()
	n += cap(s.plans) * int(unsafe.Sizeof((*planned)(nil)))
	for _, p := range s.plans {
		if p != nil {
			n += int(unsafe.Sizeof(*p)) + fieldStorage(p.record.Fields) + fieldStorage(p.riseProof.Fields)
			n += len(p.plan.Command.Text)
		}
	}
	return n
}

func eventStorage(e Event) int {
	n := len(e.Kind) + len(e.Role) + len(e.Name) + len(e.Text) + len(e.Category)
	n += cap(e.Lines)*int(unsafe.Sizeof(Line{})) + cap(e.Goods)*int(unsafe.Sizeof(Good{})) + cap(e.Entries)*int(unsafe.Sizeof(Entry{})) + cap(e.Quests)*int(unsafe.Sizeof(QuestState{})) + cap(e.Skills)*int(unsafe.Sizeof(Skill{}))
	for _, v := range e.Lines {
		n += len(v.Label) + len(v.Say)
	}
	for _, v := range e.Goods {
		n += len(v.Name)
	}
	for _, v := range e.Entries {
		n += len(v.Category) + len(v.Text)
	}
	for _, v := range e.Quests {
		n += len(v.Name) + len(v.State)
	}
	if e.Stats != nil {
		n += int(unsafe.Sizeof(*e.Stats)) + 7*8
	}
	return n
}
func fieldStorage(fields []wire.Field) int {
	n := cap(fields) * int(unsafe.Sizeof(wire.Field{}))
	for _, f := range fields {
		n += valueStorage(f.Value)
	}
	return n
}
func valueStorage(v wire.Value) int {
	n := cap(v.Data) + fieldStorage(v.Fields) + cap(v.Items)*int(unsafe.Sizeof(wire.Value{}))
	for _, item := range v.Items {
		n += valueStorage(item)
	}
	return n
}
