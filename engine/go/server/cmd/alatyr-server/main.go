// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Command alatyr-server serves authenticated participants on the authored Go world.
package main

import (
	"context"
	"encoding/json"
	"errors"
	"flag"
	"io"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/l2go/alatyr/engine/go/server/internal/migrate"
	"github.com/l2go/alatyr/engine/go/server/internal/pgstore"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func main() {
	if err := run(); err != nil {
		slog.Error("server stopped", "error", err)
		os.Exit(1)
	}
}

func decode(path string, out any) (result error) {
	f, err := os.Open(path)
	if err != nil {
		return err
	}
	defer func() { result = errors.Join(result, f.Close()) }()
	info, err := f.Stat()
	if err != nil {
		return err
	}
	if info.Size() > 1048576 {
		return errors.New("configuration exceeds one MiB")
	}
	dec := json.NewDecoder(io.LimitReader(f, 1048577))
	dec.DisallowUnknownFields()
	if err := dec.Decode(out); err != nil {
		return errors.New("invalid JSON configuration")
	}
	var extra any
	if !errors.Is(dec.Decode(&extra), io.EOF) {
		return errors.New("trailing JSON configuration")
	}
	return nil
}

func run() error {
	path := flag.String("config", "", "server configuration file")
	seed := flag.String("import-character", "", "import a trusted character JSON file and exit")
	flag.Parse()
	if *path == "" || flag.NArg() != 0 {
		return errors.New("usage: alatyr-server -config file [-import-character file]")
	}
	var cfg Config
	if err := decode(*path, &cfg); err != nil {
		return err
	}
	dsn := os.Getenv("ALATYR_DATABASE_URL")
	if dsn == "" {
		return errors.New("ALATYR_DATABASE_URL is required")
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	startup, cancel := context.WithTimeout(ctx, 30*time.Second)
	store, err := pgstore.Open(startup, dsn)
	if err == nil {
		err = migrate.Apply(startup, dsn)
	}
	cancel()
	if err != nil {
		if store != nil {
			err = errors.Join(err, store.Close())
		}
		return err
	}
	if *seed != "" {
		var c world.Character
		err := decode(*seed, &c)
		if err == nil {
			op, cancel := context.WithTimeout(ctx, 10*time.Second)
			err = store.PutCharacter(op, c)
			cancel()
		}
		return errors.Join(err, store.Close())
	}
	cat, err := catalog.Load(cfg.WorldPath)
	if err == nil {
		err = Run(ctx, cfg, cat, store)
	}
	return errors.Join(err, store.Close())
}
