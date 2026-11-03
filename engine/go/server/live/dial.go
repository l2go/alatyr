// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package live

import (
	"context"
	"errors"
	"net"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/server/goworld"
)

// Dial connects a participant to a server's stream endpoint. token is the
// participant's bearer token, sent as the first line; agent says whether the
// participant introduces itself as an agent rather than a person. watch is
// as for Join. The caller must Close the Session.
func Dial(ctx context.Context, address, token string, agent bool, watch func(Update)) (*Session, error) {
	var d net.Dialer
	conn, err := d.DialContext(ctx, "tcp", address)
	if err != nil {
		return nil, err
	}
	if _, err := conn.Write([]byte(token + "\n")); err != nil {
		return nil, errors.Join(err, conn.Close())
	}
	mc := goworld.Limits(1)
	codec, err := meaning.NewCodec(mc)
	if err != nil {
		return nil, errors.Join(err, conn.Close())
	}
	kind := wire.ParticipantPerson
	if agent {
		kind = wire.ParticipantAgent
	}
	return newSession(codec, &gateway.Stream{Conn: conn, Limit: mc.Wire.FrameBytes}, kind, watch)
}
