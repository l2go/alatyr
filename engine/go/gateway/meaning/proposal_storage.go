// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"unsafe"
)

// StorageBytes accounts owned saved-action storage, including slice capacity.
// This is allocation accounting, not an RSS measurement or a wire meaning.
func (p *Proposals) StorageBytes() int {
	n := 2 * p.config.Windows * int(unsafe.Sizeof(proposalWindow{}))
	for _, w := range p.windows {
		n += cap(w.lines)*int(unsafe.Sizeof(Line{})) + cap(w.goods)*int(unsafe.Sizeof(Good{})) + cap(w.offers)*8
		for _, v := range w.lines {
			n += len(v.Label) + len(v.Say)
		}
		for _, v := range w.goods {
			n += len(v.Name)
		}
	}
	return n
}
