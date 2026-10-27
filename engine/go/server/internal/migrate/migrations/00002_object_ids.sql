-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright 2026 l2go
-- +goose Up
CREATE TABLE world.go_object_id (
 singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton), high_water bigint NOT NULL CHECK (high_water BETWEEN 268435455 AND 2147483647)
);
INSERT INTO world.go_object_id(singleton,high_water)
 SELECT true,greatest(268435455,coalesce((SELECT max(id) FROM world.player),0),coalesce((SELECT max(id) FROM world.item),0));
