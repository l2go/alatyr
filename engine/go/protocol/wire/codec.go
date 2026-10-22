// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package wire

import (
	"cmp"
	"crypto/sha256"
	"encoding/binary"
	"errors"
	"io"
	"math"
	"slices"
	"unicode/utf8"
)

// Codec reads and writes frames of one Registry within fixed Limits. A Codec
// is immutable after New and safe for concurrent use.
type Codec struct {
	limits   Limits
	registry Registry
}

// New returns a Codec for limits l and registry r. It copies r, so later
// changes to r do not affect the Codec. New returns ErrConfig when a limit is
// not positive or the registry is malformed.
func New(l Limits, r Registry) (*Codec, error) {
	if l.FrameBytes < 1 || l.StringBytes < 1 || l.ListItems < 1 || l.Depth < 1 || l.Records < 1 || l.Fields < 1 || l.IdentifierBytes < 1 || l.WorldBytes < 1 || l.ClientBytes < 1 {
		return nil, ErrConfig
	}
	// Own the schema: a later caller mutation must not change a negotiated codec.
	var copySchema func(Schema, int) (Schema, error)
	var copySpec func(Spec, int) (Spec, error)
	copySpec = func(s Spec, depth int) (Spec, error) {
		if s.Shape > Fixed || depth > l.Depth+1 || s.MaxBytes < 0 || s.Bits < 0 || s.Bits > 64 {
			return Spec{}, ErrConfig
		}
		s.Enum = slices.Clone(s.Enum)
		if s.Shape == Object {
			var err error
			s.Object, err = copySchema(s.Object, depth+1)
			if err != nil {
				return Spec{}, err
			}
		}
		if s.Shape == List {
			if s.Element == nil {
				return Spec{}, ErrConfig
			}
			el, err := copySpec(*s.Element, depth+1)
			if err != nil {
				return Spec{}, err
			}
			s.Element = &el
		}
		return s, nil
	}
	copySchema = func(src Schema, depth int) (Schema, error) {
		out := make(Schema, len(src))
		for n, s := range src {
			if n == 0 || n > 1<<28-1 {
				return nil, ErrConfig
			}
			v, err := copySpec(s, depth)
			if err != nil {
				return nil, err
			}
			out[n] = v
		}
		return out, nil
	}
	own := make(Registry, len(r))
	for typ, s := range r {
		if typ == 0 {
			return nil, ErrConfig
		}
		v, err := copySchema(s, 0)
		if err != nil {
			return nil, err
		}
		own[typ] = v
	}
	return &Codec{l, own}, nil
}

func readU(b []byte, pos *int) (uint64, error) {
	var n uint64
	for i := range 10 {
		if *pos >= len(b) {
			return 0, ErrFrame
		}
		v := b[*pos]
		*pos++
		if i == 9 && v > 1 {
			return 0, ErrFrame
		}
		n |= uint64(v&127) << (i * 7)
		if v < 128 {
			if i > 0 && v == 0 {
				return 0, ErrFrame
			}
			return n, nil
		}
	}
	return 0, ErrFrame
}
func take(b []byte, pos *int) ([]byte, error) {
	n, err := readU(b, pos)
	if err != nil || n > uint64(len(b)-*pos) {
		return nil, ErrFrame
	}
	start := *pos
	*pos += int(n)
	return b[start:*pos], nil
}
func wireType(s Shape) Type {
	switch s {
	case UInt, Bool:
		return Unsigned
	case Int:
		return Signed
	case Fixed:
		return Fixed64
	default:
		return LengthDelimited
	}
}

// Decode returns the records of frame body b. It validates the whole frame
// before returning any record and runs no callbacks. Unknown odd record kinds
// are skipped; an unknown even kind returns ErrUnsupported.
func (c *Codec) Decode(b []byte) ([]Record, error) {
	if len(b) == 0 || len(b) > c.limits.FrameBytes {
		return nil, ErrFrame
	}
	var records []Record
	pos, count := 0, 0
	for pos < len(b) {
		start := pos
		count++
		if count > c.limits.Records {
			return nil, ErrFrame
		}
		typ, err := readU(b, &pos)
		if err != nil {
			return nil, ErrFrame
		}
		body, err := take(b, &pos)
		if err != nil {
			return nil, err
		}
		s, known := c.registry[typ]
		if !known {
			if typ&1 == 0 {
				return nil, ErrUnsupported
			}
			continue
		}
		fields, unsupported, err := c.fields(body, s, 0)
		if err != nil {
			if errors.Is(err, ErrUnsupported) {
				return nil, &UnsupportedRecord{Type: typ}
			}
			if missing, ok := errors.AsType[*missingRequired](err); ok {
				if typ == Intent && unsupported && missing.depth == 0 {
					return nil, ErrUnsupported
				}
				return nil, ErrFrame
			}
			return nil, err
		}
		if unsupported && typ != Fact && typ != Intent {
			return nil, &UnsupportedRecord{Type: typ}
		}
		record := Record{Type: typ, Fields: fields, Unsupported: unsupported, EncodedBytes: pos - start}
		if unsupported && typ == Intent {
			record.UnsupportedHash = sha256.Sum256(canonicalOpaque(body, s))
		}
		if err := validateEnvelope(record); err != nil {
			return nil, err
		}
		records = append(records, record)
	}
	return records, nil
}

func (c *Codec) fields(b []byte, schema Schema, depth int) ([]Field, bool, error) {
	var out []Field
	seen := make(map[uint32]bool)
	pos := 0
	unsupported := false
	for pos < len(b) {
		key, err := readU(b, &pos)
		n := key >> 4
		t := Type(key & 7)
		essential := key&8 != 0
		if err != nil || n == 0 || n > 1<<28-1 || t > Fixed64 || seen[uint32(n)] || len(seen) >= c.limits.Fields {
			return nil, false, ErrFrame
		}
		seen[uint32(n)] = true
		var raw []byte
		var v Value
		switch t {
		case Unsigned, Signed:
			x, err := readU(b, &pos)
			if err != nil {
				return nil, false, err
			}
			if t == Signed {
				v.S = int64(x>>1) ^ -int64(x&1)
			} else {
				v.U = x
			}
		case LengthDelimited:
			raw, err = take(b, &pos)
			if err != nil {
				return nil, false, err
			}
		case Fixed64:
			if len(b)-pos < 8 {
				return nil, false, ErrFrame
			}
			copy(v.Fixed[:], b[pos:pos+8])
			pos += 8
		}
		s, known := schema[uint32(n)]
		if !known {
			unsupported = unsupported || essential
			continue
		}
		if t != wireType(s.Shape) || s.Essential != essential {
			return nil, false, ErrFrame
		}
		flag, err := c.value(&v, raw, s, depth)
		if err != nil {
			return nil, unsupported || flag, err
		}
		unsupported = unsupported || flag
		out = append(out, Field{uint32(n), essential, t, v})
	}
	for n, s := range schema {
		if s.Required && !seen[n] {
			return nil, unsupported, &missingRequired{depth}
		}
	}
	return out, unsupported, nil
}

func (c *Codec) value(v *Value, raw []byte, s Spec, depth int) (bool, error) {
	switch s.Shape {
	case UInt, Bool:
		if s.Nonzero && v.U == 0 {
			return false, ErrFrame
		}
		if s.Shape == Bool && v.U > 1 {
			return false, ErrFrame
		}
		if s.Bits > 0 && s.Bits < 64 && v.U>>s.Bits != 0 {
			return false, ErrFrame
		}
		if s.Enum != nil && !slices.Contains(s.Enum, v.U) {
			return false, ErrUnsupported
		}
	case Int:
		if s.Bits > 0 && s.Bits < 64 {
			lim := int64(1) << (s.Bits - 1)
			if v.S < -lim || v.S >= lim {
				return false, ErrFrame
			}
		}
	case String, Identifier, Language, Bytes:
		limit := c.limits.StringBytes
		if s.Shape == Bytes {
			limit = c.limits.FrameBytes
		}
		if (s.Shape == Identifier || s.Shape == Language) && s.MaxBytes == 0 {
			limit = min(limit, c.limits.IdentifierBytes)
		}
		if s.MaxBytes > 0 {
			limit = min(limit, s.MaxBytes)
		}
		if len(raw) > limit {
			return false, ErrFrame
		}
		if s.Shape != Bytes && !utf8.Valid(raw) {
			return false, ErrFrame
		}
		if (s.Shape == Identifier || s.Shape == Language) && slices.Contains(raw, byte(0)) {
			return false, ErrFrame
		}
		if s.Shape == Language && !languageTag(raw) {
			return false, ErrFrame
		}
		v.Data = slices.Clone(raw)
	case Position:
		p := 0
		for i := range v.XYZ {
			u, err := readU(raw, &p)
			if err != nil {
				return false, err
			}
			x := int64(u>>1) ^ -int64(u&1)
			if x < math.MinInt32 || x > math.MaxInt32 {
				return false, ErrFrame
			}
			v.XYZ[i] = int32(x)
		}
		if p != len(raw) {
			return false, ErrFrame
		}
	case Object:
		if depth+1 > c.limits.Depth {
			return false, ErrFrame
		}
		f, unknown, err := c.fields(raw, s.Object, depth+1)
		v.Fields = f
		return unknown, err
	case List:
		if depth+1 > c.limits.Depth {
			return false, ErrFrame
		}
		v.Items = make([]Value, 0)
		pos := 0
		unknown := false
		for pos < len(raw) {
			if len(v.Items) >= c.limits.ListItems {
				return false, ErrFrame
			}
			element, err := take(raw, &pos)
			if err != nil {
				return false, err
			}
			var item Value
			el := *s.Element
			level := depth + 1
			if el.Shape == UInt || el.Shape == Bool || el.Shape == Int {
				p := 0
				u, err := readU(element, &p)
				if err != nil || p != len(element) {
					return false, ErrFrame
				}
				if el.Shape == Int {
					item.S = int64(u>>1) ^ -int64(u&1)
				} else {
					item.U = u
				}
			}
			flag, err := c.value(&item, element, el, level)
			if err != nil {
				return false, err
			}
			unknown = unknown || flag
			v.Items = append(v.Items, item)
		}
		return unknown, nil
	}
	return false, nil
}

// Read reads one length-prefixed frame and rejects its length before allocating.
func (c *Codec) Read(r io.Reader) ([]Record, error) {
	var prefix [10]byte
	size := 0
	for {
		if size == len(prefix) {
			return nil, ErrFrame
		}
		_, err := io.ReadFull(r, prefix[size:size+1])
		if err != nil {
			if size == 0 && errors.Is(err, io.EOF) {
				return nil, io.EOF
			}
			return nil, ErrFrame
		}
		size++
		if prefix[size-1] < 128 {
			break
		}
	}
	p := 0
	n, err := readU(prefix[:size], &p)
	if err != nil || n == 0 || n > uint64(c.limits.FrameBytes) {
		return nil, ErrFrame
	}
	b := make([]byte, int(n))
	if _, err = io.ReadFull(r, b); err != nil {
		return nil, ErrFrame
	}
	return c.Decode(b)
}

func appendU(b []byte, n uint64) []byte { return binary.AppendUvarint(b, n) }
func appendS(b []byte, n int64) []byte  { return appendU(b, uint64(n<<1)^uint64(n>>63)) }

func (c *Codec) appendBounded(b, more []byte) ([]byte, error) {
	if len(more) > c.limits.FrameBytes-len(b) {
		return nil, ErrFrame
	}
	return append(b, more...), nil
}

// Encode returns the frame body for records, without the length prefix. The
// result is decoded again before it is returned, so Encode never produces a
// frame its own Decode would reject.
func (c *Codec) Encode(records []Record) ([]byte, error) {
	if len(records) == 0 || len(records) > c.limits.Records {
		return nil, ErrFrame
	}
	var out []byte
	for _, r := range records {
		s, ok := c.registry[r.Type]
		if !ok || r.Unsupported {
			return nil, ErrUnsupported
		}
		b, err := c.encodeFields(r.Fields, s, 0)
		if err != nil {
			return nil, err
		}
		header := appendU(appendU(nil, r.Type), uint64(len(b)))
		out, err = c.appendBounded(out, header)
		if err != nil {
			return nil, err
		}
		out, err = c.appendBounded(out, b)
		if err != nil {
			return nil, err
		}
	}
	// Use the same validator on both directions; no malformed value is emitted.
	if _, err := c.Decode(out); err != nil {
		return nil, err
	}
	return out, nil
}

func (c *Codec) encodeFields(fields []Field, schema Schema, depth int) ([]byte, error) {
	if len(fields) > c.limits.Fields {
		return nil, ErrFrame
	}
	fields = slices.Clone(fields)
	slices.SortFunc(fields, func(a, b Field) int {
		if a.Number < b.Number {
			return -1
		}
		if a.Number > b.Number {
			return 1
		}
		return 0
	})
	var out []byte
	for _, f := range fields {
		if f.Number == 0 || f.Number > 1<<28-1 || f.Type > Fixed64 {
			return nil, ErrFrame
		}
		s, known := schema[f.Number]
		if !known {
			if f.Essential {
				return nil, ErrUnsupported
			}
			s = Spec{Shape: Bytes}
			switch f.Type {
			case Unsigned:
				s.Shape = UInt
			case Signed:
				s.Shape = Int
			case Fixed64:
				s.Shape = Fixed
			}
		}
		key := uint64(f.Number)<<4 | uint64(f.Type)
		if f.Essential {
			key |= 8
		}
		b := appendU(nil, key)
		switch f.Type {
		case Unsigned:
			b = appendU(b, f.Value.U)
		case Signed:
			b = appendS(b, f.Value.S)
		case Fixed64:
			b = append(b, f.Value.Fixed[:]...)
		case LengthDelimited:
			raw, err := c.encodeValue(f.Value, s, depth)
			if err != nil {
				return nil, err
			}
			b = appendU(b, uint64(len(raw)))
			b, err = c.appendBounded(b, raw)
			if err != nil {
				return nil, err
			}
		}
		var err error
		out, err = c.appendBounded(out, b)
		if err != nil {
			return nil, err
		}
	}
	return out, nil
}

func (c *Codec) encodeValue(v Value, s Spec, depth int) ([]byte, error) {
	switch s.Shape {
	case Object:
		if depth+1 > c.limits.Depth {
			return nil, ErrFrame
		}
		return c.encodeFields(v.Fields, s.Object, depth+1)
	case List:
		if depth+1 > c.limits.Depth || len(v.Items) > c.limits.ListItems || s.Element == nil {
			return nil, ErrFrame
		}
		var out []byte
		for _, item := range v.Items {
			var raw []byte
			var err error
			switch s.Element.Shape {
			case UInt, Bool:
				raw = appendU(nil, item.U)
			case Int:
				raw = appendS(nil, item.S)
			default:
				raw, err = c.encodeValue(item, *s.Element, depth+1)
			}
			if err != nil {
				return nil, err
			}
			out, err = c.appendBounded(out, appendU(nil, uint64(len(raw))))
			if err != nil {
				return nil, err
			}
			out, err = c.appendBounded(out, raw)
			if err != nil {
				return nil, err
			}
		}
		return out, nil
	case Position:
		var out []byte
		for _, x := range v.XYZ {
			out = appendS(out, int64(x))
		}
		return out, nil
	default:
		if len(v.Data) > c.limits.FrameBytes {
			return nil, ErrFrame
		}
		return v.Data, nil
	}
}

// Write encodes records and writes them to w as one length-prefixed frame.
func (c *Codec) Write(w io.Writer, records []Record) error {
	b, err := c.Encode(records)
	if err != nil {
		return err
	}
	header := appendU(nil, uint64(len(b)))
	for _, p := range [][]byte{header, b} {
		for len(p) > 0 {
			n, err := w.Write(p)
			if err != nil {
				return err
			}
			if n <= 0 || n > len(p) {
				return io.ErrShortWrite
			}
			p = p[n:]
		}
	}
	return nil
}

// canonicalOpaque canonicalizes schema-known object order while keeping unknown
// values opaque. Only a structurally validated, bounded record enters here.
func canonicalOpaque(body []byte, schema Schema) []byte {
	type part struct {
		number uint64
		bytes  []byte
	}
	var parts []part
	pos := 0
	for pos < len(body) {
		start := pos
		key, _ := readU(body, &pos)
		var normalized []byte
		switch Type(key & 7) {
		case Unsigned, Signed:
			_, _ = readU(body, &pos) // validated by Decode before this call
		case Fixed64:
			pos += 8
		case LengthDelimited:
			raw, _ := take(body, &pos)
			spec, known := schema[uint32(key>>4)]
			if known && spec.Shape == Object {
				normalized = canonicalOpaque(raw, spec.Object)
			}
			if known && spec.Shape == List && spec.Element.Shape == Object {
				normalized = []byte{}
				at := 0
				for at < len(raw) {
					element, _ := take(raw, &at)
					element = canonicalOpaque(element, spec.Element.Object)
					normalized = appendU(normalized, uint64(len(element)))
					normalized = append(normalized, element...)
				}
			}
		}
		chunk := body[start:pos]
		if normalized != nil {
			chunk = appendU(nil, key)
			chunk = appendU(chunk, uint64(len(normalized)))
			chunk = append(chunk, normalized...)
		}
		parts = append(parts, part{key >> 4, chunk})
	}
	slices.SortFunc(parts, func(a, b part) int { return cmp.Compare(a.number, b.number) })
	out := make([]byte, 0, len(body))
	for _, p := range parts {
		out = append(out, p.bytes...)
	}
	return out
}
