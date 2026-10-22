// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package profile

import (
	"errors"
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestMultiRecordErrorCategory(t *testing.T) {
	// Only the fixture encoder knows type 1000; the profile decoder must reject it.
	registry := Registry()
	registry[1000] = wire.Schema{}
	raw, err := wire.New(Limits(), registry)
	if err != nil {
		t.Fatal(err)
	}
	missingPair := result(3, 16, 0, nil, nil, false)
	missingPair.Fields = append(missingPair.Fields, wire.Data(64, true, Namespace))
	codec := newCodec(t)

	for _, tc := range []struct {
		name    string
		records []wire.Record
		want    error
	}{
		{"namespace without pair", []wire.Record{missingPair}, wire.ErrFrame},
		{"same result then unknown even record", []wire.Record{missingPair, {Type: 1000}}, wire.ErrUnsupported},
	} {
		t.Run(tc.name, func(t *testing.T) {
			frame, err := raw.Encode(tc.records)
			if err != nil {
				t.Fatal("fixture encoder:", err)
			}
			records, err := codec.Decode(frame)
			if !errors.Is(err, tc.want) || records != nil {
				t.Fatalf("category/atomicity: records=%v error=%v; want nil records and %v", records, err, tc.want)
			}
		})
	}
}
