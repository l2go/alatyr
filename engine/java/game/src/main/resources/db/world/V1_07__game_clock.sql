-- State that 2.0 kept in files (plan 4.6), part 1: the game clock, formerly
-- data/serial/clock.dat. The Olympiad state no longer falls back to
-- config/olympiad.properties.

CREATE TABLE game_clock (
	id smallint PRIMARY KEY DEFAULT 0 CHECK (id = 0),
	game_time_millis bigint NOT NULL
);
COMMENT ON TABLE game_clock IS 'In-game calendar time (GameTimeManager), saved every in-game minute when SaveDate is on. At most one row (id 0); without it the game starts at 5 June 1281, 23:45.';
COMMENT ON COLUMN game_clock.game_time_millis IS 'Game time as milliseconds since 1970 (negative: game years are around 1281), read back into a GregorianCalendar in the JVM time zone like the former clock.dat.';

COMMENT ON TABLE olympiad_state IS 'State of the Grand Olympiad. At most one row (id 0); without it the Olympiad starts at cycle 1 in the competition period.';
