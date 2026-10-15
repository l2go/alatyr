-- State that 2.0 kept in files (plan 4.6), part 3: the arena and fishing
-- leaderboards, formerly data/arena.dat and data/fish.dat.

CREATE TABLE leaderboard_entry (
	board text NOT NULL CHECK (board IN ('arena', 'fishing')),
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	position integer NOT NULL CHECK (position >= 0),
	player_name text NOT NULL,
	wins integer NOT NULL CHECK (wins >= 0),
	losses integer NOT NULL CHECK (losses >= 0),
	PRIMARY KEY (board, player_id)
);
CREATE INDEX leaderboard_entry_player_id_idx ON leaderboard_entry (player_id);
COMMENT ON TABLE leaderboard_entry IS 'Players of the current round of the arena (ArenaManager) and fishing (FishermanManager) leaderboards. The game replaces a board on every save and empties it when it rewards the winner.';
COMMENT ON COLUMN leaderboard_entry.board IS 'Leaderboard: arena or fishing.';
COMMENT ON COLUMN leaderboard_entry.player_id IS 'Ranked player.';
COMMENT ON COLUMN leaderboard_entry.position IS 'Order in which the player joined the round; it decides between equal scores.';
COMMENT ON COLUMN leaderboard_entry.player_name IS 'Name the board shows, as it was when the player last scored.';
COMMENT ON COLUMN leaderboard_entry.wins IS 'Arena: players killed. Fishing: fish caught.';
COMMENT ON COLUMN leaderboard_entry.losses IS 'Arena: deaths. Fishing: fish that escaped.';
