// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"log/slog"
	"slices"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/world/itemlog"
)

func TestScheduledWork(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w := New(nil, nil, nil, FreshSettings, itemlog.Discard, NoGround{}, slog.New(slog.DiscardHandler))
		ctx, cancel := context.WithCancel(t.Context())
		defer cancel()
		go w.Run(ctx)
		var got []string
		start := time.Now()
		w.do(ctx, func(s *state) {
			s.after(15*time.Second, func(*state) { got = append(got, "respawn") })
			s.after(8500*time.Millisecond, func(s *state) {
				got = append(got, "decay")
				s.after(15*time.Second, func(*state) { got = append(got, "after decay") })
			})
			s.after(8500*time.Millisecond, func(*state) { got = append(got, "same time") })
		})
		synctest.Sleep(9 * time.Second)
		w.do(ctx, func(*state) { got = append(got, "command") })
		synctest.Sleep(30 * time.Second)
		w.do(ctx, func(*state) {})
		want := []string{"decay", "same time", "command", "respawn", "after decay"}
		if !slices.Equal(got, want) {
			t.Fatalf("order %v, want %v", got, want)
		}
		if el := time.Since(start); el != 39*time.Second {
			t.Fatalf("virtual time %v", el)
		}
	})
}
