// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package hunt

import (
	"crypto/sha256"
	"encoding/binary"
	"math/bits"
)

// Random is an NPC life's counter-based reward stream, independent of combat.
// The zero value is the stream for the empty world and life zero.
type Random struct {
	World string
	Life  uint64
	draw  uint64
}

// Uint64 returns the next SHA-256/SplitMix64 vector. Arithmetic wraps modulo 2^64.
func (r *Random) Uint64() uint64 {
	b := binary.BigEndian.AppendUint32(nil, uint32(len(r.World)))
	b = append(b, r.World...)
	b = binary.BigEndian.AppendUint32(b, 4)
	b = append(b, "hunt"...)
	b = binary.BigEndian.AppendUint64(b, r.Life)
	b = binary.BigEndian.AppendUint64(b, r.draw)
	hash := sha256.Sum256(b)
	z := binary.BigEndian.Uint64(hash[:8]) + (r.draw+1)*0x9e3779b97f4a7c15
	r.draw++
	z = (z ^ (z >> 30)) * 0xbf58476d1ce4e5b9
	z = (z ^ (z >> 27)) * 0x94d049bb133111eb
	return z ^ (z >> 31)
}

// Below maps a draw into [0, bound) using the unsigned high product.
// A zero bound returns zero without consuming randomness.
func (r *Random) Below(bound uint64) uint64 {
	if bound == 0 {
		return 0
	}
	hi, _ := bits.Mul64(r.Uint64(), bound)
	return hi
}
