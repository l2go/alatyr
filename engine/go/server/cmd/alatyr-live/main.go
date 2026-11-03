// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

//go:build js && wasm

package main

import (
	"archive/zip"
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"syscall/js"
	"time"

	"github.com/l2go/alatyr/engine/go/server/live"
)

// The page talks to the world through one global object:
//
//	alatyr.start(worldZip Uint8Array, onUpdate(identity, json)) -> error text
//	alatyr.act(identity, actionJSON) -> error text
//	alatyr.agent(on bool)
//
// An empty error text means success.
func main() {
	var (
		world    *live.World
		sessions = map[string]*live.Session{}
		stop     context.CancelFunc
	)
	api := map[string]any{
		"start": js.FuncOf(func(_ js.Value, args []js.Value) any {
			if world != nil {
				return "already started"
			}
			if len(args) != 2 {
				return "start needs the world archive and a callback"
			}
			raw := make([]byte, args[0].Length())
			js.CopyBytesToGo(raw, args[0])
			fsys, err := zip.NewReader(bytes.NewReader(raw), int64(len(raw)))
			if err != nil {
				return err.Error()
			}
			if world, err = live.Start(fsys); err != nil {
				world = nil
				return err.Error()
			}
			callback := args[1]
			for _, id := range []string{live.Visitor, live.Agent} {
				s, err := world.Join(context.Background(), id, func(u live.Update) {
					if b, err := json.Marshal(u); err == nil {
						callback.Invoke(id, string(b))
					}
				})
				if err != nil {
					return err.Error()
				}
				sessions[id] = s
			}
			return ""
		}),
		"act": js.FuncOf(func(_ js.Value, args []js.Value) any {
			if len(args) != 2 {
				return "act needs an identity and an action"
			}
			s, ok := sessions[args[0].String()]
			if !ok {
				return "unknown participant"
			}
			var a live.Action
			if err := json.Unmarshal([]byte(args[1].String()), &a); err != nil {
				return err.Error()
			}
			// Writing blocks on the in-memory stream; the page must not.
			go func() {
				if err := s.Do(a); err != nil && !errors.Is(err, live.ErrNotReady) {
					js.Global().Get("console").Call("warn", err.Error())
				}
			}()
			return ""
		}),
		"agent": js.FuncOf(func(_ js.Value, args []js.Value) any {
			on := len(args) == 1 && args[0].Bool()
			if stop != nil {
				stop()
				stop = nil
			}
			if s, ok := sessions[live.Agent]; ok && on {
				var ctx context.Context
				ctx, stop = context.WithCancel(context.Background())
				go live.RunAgent(ctx, s, 1200*time.Millisecond)
			}
			return nil
		}),
	}
	js.Global().Set("alatyr", js.ValueOf(api))
	select {}
}
