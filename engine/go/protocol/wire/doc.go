// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

// Package wire implements bounded, extensible binary frames.
//
// A frame is a sequence of records. A record is a kind and numbered fields;
// each field carries its wire Type and an essential bit. A receiver skips an
// unknown optional field but rejects an unknown essential one, so new meaning
// can be added without breaking old receivers. Every size, count and depth is
// bounded by the Limits of a Codec.
package wire
