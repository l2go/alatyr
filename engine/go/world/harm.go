// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"errors"
	"math"
)

// ErrHarm reports invalid trusted-host harm or a retired source/target.
var ErrHarm = errors.New("world: invalid harm")

// Harm applies trusted host harm through the normal HP, death and reward path.
// A zero source is neutral environmental harm. This method grants no participant
// authority and is never selected by a raw wire verb.
func (w *World) Harm(ctx context.Context, source, target Ref, amount float64) error {
	var result error
	err := w.do(ctx, func(s *state) {
		a, ok := s.actor(target.ID)
		if !ok || a.base().ref() != target || amount <= 0 || math.IsNaN(amount) || math.IsInf(amount, 0) || amount > math.MaxInt32 {
			result = ErrHarm
			return
		}
		var attacker actor
		if source != (Ref{}) {
			attacker, ok = s.actor(source.ID)
			if !ok || attacker.base().ref() != source {
				result = ErrHarm
				return
			}
		}
		w.reduceHP(s, a, amount, attacker)
	})
	if err != nil {
		return err
	}
	return result
}
