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
	"net/http"
	"os"
	"os/signal"
	"strings"
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
	probe := flag.String("healthcheck", "", "check an HTTP health endpoint and exit")
	flag.Parse()
	if *probe != "" {
		if *path != "" || *seed != "" || flag.NArg() != 0 {
			return errors.New("healthcheck cannot be combined with server options")
		}
		return healthcheck(*probe)
	}
	if *path == "" || flag.NArg() != 0 {
		return errors.New("usage: alatyr-server -config file [-import-character file]")
	}
	var cfg Config
	if err := decode(*path, &cfg); err != nil {
		return err
	}
	dsn, err := databaseURL()
	if err != nil {
		return err
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

// databaseURL keeps mounted credentials out of the container's environment and
// diagnostics. Explicitly choosing both sources is an error, never precedence.
func databaseURL() (string, error) {
	value, path := os.Getenv("ALATYR_DATABASE_URL"), os.Getenv("ALATYR_DATABASE_URL_FILE")
	if value != "" && path != "" {
		return "", errors.New("configure only one database URL source")
	}
	if path != "" {
		f, err := os.Open(path)
		if err != nil {
			return "", errors.New("database URL file unavailable")
		}
		data, readErr := io.ReadAll(io.LimitReader(f, 16385))
		closeErr := f.Close()
		if readErr != nil || closeErr != nil || len(data) > 16384 {
			return "", errors.New("invalid database URL file")
		}
		value = strings.TrimSpace(string(data))
	}
	if value == "" {
		return "", errors.New("ALATYR_DATABASE_URL or ALATYR_DATABASE_URL_FILE is required")
	}
	return value, nil
}

func healthcheck(address string) error {
	client := &http.Client{Timeout: 2 * time.Second}
	response, err := client.Get(address)
	if err != nil {
		return errors.New("healthcheck: endpoint unavailable")
	}
	closeErr := response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return errors.New("healthcheck: not ready")
	}
	return closeErr
}
