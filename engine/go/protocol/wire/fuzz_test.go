// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package wire

import (
	"bytes"
	"errors"
	"io"
	"reflect"
	"testing"
)

func FuzzDecodeFrame(f *testing.F) {
	l := testLimits()
	c, err := New(l, Envelopes(Schema{1: Required(UInt)}, l))
	if err != nil {
		f.Fatal(err)
	}
	for _, v := range loadFrozenVectors(f) {
		f.Add(v.id == "V17" || v.id == "V21", v.data)
	}
	for _, stream := range []bool{false, true} {
		for _, data := range [][]byte{
			{}, {128}, {255, 255, 255, 255, 255, 255, 255, 255, 255, 2},
			{1, 4, 24, 0, 24, 1}, {17, 1, 255}, {18, 0},
			{1, 5, 24, 0, 160, 1, 0}, {1, 5, 24, 0, 168, 1, 0},
		} {
			f.Add(stream, data)
		}
	}
	f.Fuzz(func(t *testing.T, stream bool, data []byte) {
		decode := func() ([]Record, error) {
			if stream {
				return c.Read(bytes.NewReader(data))
			}
			return c.Decode(data)
		}
		records, err := decode()
		again, againErr := decode()
		if !sameDecodeError(err, againErr) || !reflect.DeepEqual(records, again) {
			t.Fatal("nondeterministic frame parsing")
		}
		if err != nil {
			if records != nil {
				t.Fatal("partial frame returned on error")
			}
			if !errors.Is(err, ErrFrame) && !errors.Is(err, ErrUnsupported) &&
				!(errors.Is(err, io.EOF) && stream && len(data) == 0) {
				t.Fatalf("unexpected parser error: %v", err)
			}
			return
		}
		if len(records) == 0 {
			return // A frame may contain only skipped optional records.
		}
		for _, r := range records {
			if r.Unsupported {
				return // An essential profile extension is not an encodable core value.
			}
		}
		canonical, err := c.Encode(records)
		if err != nil {
			t.Fatalf("encode decoded core values: %v", err)
		}
		roundTrip, err := c.Decode(canonical)
		if err != nil {
			t.Fatalf("decode canonical frame: %v", err)
		}
		encodedAgain, err := c.Encode(roundTrip)
		if err != nil || !bytes.Equal(canonical, encodedAgain) {
			t.Fatalf("canonical encoding is not stable: %v", err)
		}
	})
}

// Unsupported errors carry envelope metadata in independently allocated wrappers.
// Compare that metadata rather than the identity of the two error pointers.
func sameDecodeError(a, b error) bool {
	var left, right *UnsupportedRecord
	leftOK, rightOK := errors.As(a, &left), errors.As(b, &right)
	if leftOK || rightOK {
		return leftOK && rightOK && left.Type == right.Type &&
			errors.Is(a, ErrUnsupported) && errors.Is(b, ErrUnsupported)
	}
	return errors.Is(a, b)
}

func TestFuzzErrorEquivalence(t *testing.T) {
	for _, tc := range []struct {
		name string
		a, b error
		same bool
	}{
		{"success", nil, nil, true},
		{"frame", ErrFrame, ErrFrame, true},
		{"fresh wrappers", &UnsupportedRecord{Type: Ping}, &UnsupportedRecord{Type: Ping}, true},
		{"different record", &UnsupportedRecord{Type: Ping}, &UnsupportedRecord{Type: Bye}, false},
		{"different class", &UnsupportedRecord{Type: Ping}, ErrFrame, false},
		{"missing metadata", &UnsupportedRecord{Type: Ping}, ErrUnsupported, false},
		{"missing error", nil, &UnsupportedRecord{Type: Ping}, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if sameDecodeError(tc.a, tc.b) != tc.same || sameDecodeError(tc.b, tc.a) != tc.same {
				t.Fatal("error class or envelope metadata was not preserved")
			}
		})
	}
}
