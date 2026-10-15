-- World state outside players, items, clans and residences:
-- Seven Signs (players, festival results, the cycle status), the Olympiad
-- (cycle state, noble statistics and the end-of-month snapshot), heroes,
-- married couples, grand boss and raid boss state, Hellbound, the lottery,
-- the online player record, the community board (forum, topic, post),
-- the admin-configured PvP events (CTF, TvT, DM, VIP), scheduled server
-- tasks, the GM audit log, player restrictions, global quest variables,
-- the changelog page and automatic announcements.
--
-- Boss spawn definitions live in the catalog (catalog.raid_boss_spawn,
-- catalog.grand_boss_spawn); this file keeps only what changes while the
-- server runs. Catalog references carry no foreign key.

-- Seven Signs ---------------------------------------------------------------

CREATE TABLE seven_signs_player (
	player_id integer PRIMARY KEY REFERENCES player ON DELETE CASCADE,
	cabal text CHECK (cabal IN ('dawn', 'dusk')),
	seal smallint NOT NULL DEFAULT 0 CHECK (seal BETWEEN 0 AND 3),
	red_stone_count integer NOT NULL DEFAULT 0 CHECK (red_stone_count >= 0),
	green_stone_count integer NOT NULL DEFAULT 0 CHECK (green_stone_count >= 0),
	blue_stone_count integer NOT NULL DEFAULT 0 CHECK (blue_stone_count >= 0),
	ancient_adena_amount bigint NOT NULL DEFAULT 0 CHECK (ancient_adena_amount >= 0),
	contribution_score bigint NOT NULL DEFAULT 0 CHECK (contribution_score >= 0)
) WITH (fillfactor = 80);
COMMENT ON TABLE seven_signs_player IS 'A player''s participation in the current Seven Signs cycle. The row is created when the player joins a cabal and reset at the start of each cycle.';
COMMENT ON COLUMN seven_signs_player.player_id IS 'Participating player.';
COMMENT ON COLUMN seven_signs_player.cabal IS 'Chosen cabal, as SevenSigns.getCabalShortName spells it; NULL after the cycle reset, before the player chooses again.';
COMMENT ON COLUMN seven_signs_player.seal IS 'Seal the player voted for: 0 none (after reset), 1 avarice, 2 gnosis, 3 strife (SevenSigns.SEAL_* constants).';
COMMENT ON COLUMN seven_signs_player.red_stone_count IS 'Red seal stones contributed in this cycle.';
COMMENT ON COLUMN seven_signs_player.green_stone_count IS 'Green seal stones contributed in this cycle.';
COMMENT ON COLUMN seven_signs_player.blue_stone_count IS 'Blue seal stones contributed in this cycle.';
COMMENT ON COLUMN seven_signs_player.ancient_adena_amount IS 'Ancient adena the player can still collect for the contributed stones.';
COMMENT ON COLUMN seven_signs_player.contribution_score IS 'Contribution points of the player in this cycle.';

CREATE TABLE seven_signs_festival (
	festival_level smallint NOT NULL CHECK (festival_level BETWEEN 0 AND 4),
	cabal text NOT NULL CHECK (cabal IN ('dawn', 'dusk')),
	cycle integer NOT NULL CHECK (cycle >= 0),
	scored_at timestamptz,
	score integer NOT NULL DEFAULT 0 CHECK (score >= 0),
	member_names text NOT NULL DEFAULT '',
	PRIMARY KEY (festival_level, cabal, cycle)
);
COMMENT ON TABLE seven_signs_festival IS 'Best result of each Festival of Darkness level, per cabal and festival cycle.';
COMMENT ON COLUMN seven_signs_festival.festival_level IS 'Festival level: 0 up to level 31, 1 up to 42, 2 up to 53, 3 up to 64, 4 no level limit (SevenSignsFestival.FESTIVAL_LEVEL_* constants).';
COMMENT ON COLUMN seven_signs_festival.cabal IS 'Cabal the result belongs to, as SevenSigns.getCabalShortName spells it.';
COMMENT ON COLUMN seven_signs_festival.cycle IS 'Festival cycle number (seven_signs_status.festival_cycle).';
COMMENT ON COLUMN seven_signs_festival.scored_at IS 'Moment the best score was set; NULL while nobody has scored in this cycle.';
COMMENT ON COLUMN seven_signs_festival.score IS 'Best festival score.';
COMMENT ON COLUMN seven_signs_festival.member_names IS 'Comma-separated names of the party members who set the best score; empty while nobody has scored.';

CREATE TABLE seven_signs_status (
	id smallint PRIMARY KEY DEFAULT 0 CHECK (id = 0),
	current_cycle integer NOT NULL DEFAULT 1 CHECK (current_cycle >= 1),
	festival_cycle integer NOT NULL DEFAULT 1 CHECK (festival_cycle >= 0),
	active_period smallint NOT NULL DEFAULT 1 CHECK (active_period BETWEEN 0 AND 3),
	previous_winner_cabal smallint NOT NULL DEFAULT 0 CHECK (previous_winner_cabal BETWEEN 0 AND 2),
	dawn_stone_score bigint NOT NULL DEFAULT 0 CHECK (dawn_stone_score >= 0),
	dawn_festival_score integer NOT NULL DEFAULT 0 CHECK (dawn_festival_score >= 0),
	dusk_stone_score bigint NOT NULL DEFAULT 0 CHECK (dusk_stone_score >= 0),
	dusk_festival_score integer NOT NULL DEFAULT 0 CHECK (dusk_festival_score >= 0),
	avarice_owner_cabal smallint NOT NULL DEFAULT 0 CHECK (avarice_owner_cabal BETWEEN 0 AND 2),
	gnosis_owner_cabal smallint NOT NULL DEFAULT 0 CHECK (gnosis_owner_cabal BETWEEN 0 AND 2),
	strife_owner_cabal smallint NOT NULL DEFAULT 0 CHECK (strife_owner_cabal BETWEEN 0 AND 2),
	avarice_dawn_score integer NOT NULL DEFAULT 0 CHECK (avarice_dawn_score >= 0),
	gnosis_dawn_score integer NOT NULL DEFAULT 0 CHECK (gnosis_dawn_score >= 0),
	strife_dawn_score integer NOT NULL DEFAULT 0 CHECK (strife_dawn_score >= 0),
	avarice_dusk_score integer NOT NULL DEFAULT 0 CHECK (avarice_dusk_score >= 0),
	gnosis_dusk_score integer NOT NULL DEFAULT 0 CHECK (gnosis_dusk_score >= 0),
	strife_dusk_score integer NOT NULL DEFAULT 0 CHECK (strife_dusk_score >= 0),
	accumulated_bonus0 integer NOT NULL DEFAULT 0 CHECK (accumulated_bonus0 >= 0),
	accumulated_bonus1 integer NOT NULL DEFAULT 0 CHECK (accumulated_bonus1 >= 0),
	accumulated_bonus2 integer NOT NULL DEFAULT 0 CHECK (accumulated_bonus2 >= 0),
	accumulated_bonus3 integer NOT NULL DEFAULT 0 CHECK (accumulated_bonus3 >= 0),
	accumulated_bonus4 integer NOT NULL DEFAULT 0 CHECK (accumulated_bonus4 >= 0)
);
COMMENT ON TABLE seven_signs_status IS 'State of the Seven Signs competition. Exactly one row (id 0). Cabal codes: 0 none, 1 dusk, 2 dawn (SevenSigns.CABAL_* constants).';
COMMENT ON COLUMN seven_signs_status.current_cycle IS 'Seven Signs cycle number, starting at 1.';
COMMENT ON COLUMN seven_signs_status.festival_cycle IS 'Festival of Darkness cycle number; seven_signs_festival.cycle refers to it.';
COMMENT ON COLUMN seven_signs_status.active_period IS 'Current period: 0 recruiting, 1 competition, 2 results, 3 seal validation (SevenSigns.PERIOD_* constants).';
COMMENT ON COLUMN seven_signs_status.previous_winner_cabal IS 'Cabal that won the previous cycle (cabal code).';
COMMENT ON COLUMN seven_signs_status.dawn_stone_score IS 'Seal stone points contributed by Dawn in this cycle.';
COMMENT ON COLUMN seven_signs_status.dawn_festival_score IS 'Festival points of Dawn in this cycle.';
COMMENT ON COLUMN seven_signs_status.dusk_stone_score IS 'Seal stone points contributed by Dusk in this cycle.';
COMMENT ON COLUMN seven_signs_status.dusk_festival_score IS 'Festival points of Dusk in this cycle.';
COMMENT ON COLUMN seven_signs_status.avarice_owner_cabal IS 'Cabal that owns the Seal of Avarice (cabal code; 0 nobody).';
COMMENT ON COLUMN seven_signs_status.gnosis_owner_cabal IS 'Cabal that owns the Seal of Gnosis (cabal code; 0 nobody).';
COMMENT ON COLUMN seven_signs_status.strife_owner_cabal IS 'Cabal that owns the Seal of Strife (cabal code; 0 nobody).';
COMMENT ON COLUMN seven_signs_status.avarice_dawn_score IS 'Dawn members who voted for the Seal of Avarice.';
COMMENT ON COLUMN seven_signs_status.gnosis_dawn_score IS 'Dawn members who voted for the Seal of Gnosis.';
COMMENT ON COLUMN seven_signs_status.strife_dawn_score IS 'Dawn members who voted for the Seal of Strife.';
COMMENT ON COLUMN seven_signs_status.avarice_dusk_score IS 'Dusk members who voted for the Seal of Avarice.';
COMMENT ON COLUMN seven_signs_status.gnosis_dusk_score IS 'Dusk members who voted for the Seal of Gnosis.';
COMMENT ON COLUMN seven_signs_status.strife_dusk_score IS 'Dusk members who voted for the Seal of Strife.';
COMMENT ON COLUMN seven_signs_status.accumulated_bonus0 IS 'Seal stone bonus accumulated for festival level 0 (up to level 31). The code builds the name from the festival id.';
COMMENT ON COLUMN seven_signs_status.accumulated_bonus1 IS 'Seal stone bonus accumulated for festival level 1 (up to level 42).';
COMMENT ON COLUMN seven_signs_status.accumulated_bonus2 IS 'Seal stone bonus accumulated for festival level 2 (up to level 53).';
COMMENT ON COLUMN seven_signs_status.accumulated_bonus3 IS 'Seal stone bonus accumulated for festival level 3 (up to level 64).';
COMMENT ON COLUMN seven_signs_status.accumulated_bonus4 IS 'Seal stone bonus accumulated for festival level 4 (no level limit).';

INSERT INTO seven_signs_status (id) VALUES (0);

INSERT INTO seven_signs_festival (festival_level, cabal, cycle) VALUES
	(0, 'dawn', 1),
	(0, 'dusk', 1),
	(1, 'dawn', 1),
	(1, 'dusk', 1),
	(2, 'dawn', 1),
	(2, 'dusk', 1),
	(3, 'dawn', 1),
	(3, 'dusk', 1),
	(4, 'dawn', 1),
	(4, 'dusk', 1);

-- Olympiad and heroes -------------------------------------------------------

CREATE TABLE olympiad_state (
	id smallint PRIMARY KEY DEFAULT 0 CHECK (id = 0),
	current_cycle integer NOT NULL DEFAULT 1 CHECK (current_cycle >= 0),
	period smallint NOT NULL DEFAULT 0 CHECK (period IN (0, 1)),
	competition_end_at timestamptz,
	validation_end_at timestamptz,
	next_weekly_change_at timestamptz
);
COMMENT ON TABLE olympiad_state IS 'State of the Grand Olympiad. At most one row (id 0); without it the game reads config/olympiad.properties.';
COMMENT ON COLUMN olympiad_state.current_cycle IS 'Olympiad cycle (month) number.';
COMMENT ON COLUMN olympiad_state.period IS 'Current period: 0 competition, 1 hero validation.';
COMMENT ON COLUMN olympiad_state.competition_end_at IS 'End of the competition period; NULL when not scheduled yet.';
COMMENT ON COLUMN olympiad_state.validation_end_at IS 'End of the hero validation period; NULL when not scheduled yet.';
COMMENT ON COLUMN olympiad_state.next_weekly_change_at IS 'Moment of the next weekly point bonus; NULL when not scheduled yet.';

CREATE TABLE olympiad_noble (
	player_id integer PRIMARY KEY REFERENCES player ON DELETE CASCADE,
	class_id smallint NOT NULL REFERENCES player_class,
	olympiad_points integer NOT NULL DEFAULT 0,
	competitions_done integer NOT NULL DEFAULT 0 CHECK (competitions_done >= 0),
	competitions_won integer NOT NULL DEFAULT 0 CHECK (competitions_won >= 0),
	competitions_lost integer NOT NULL DEFAULT 0 CHECK (competitions_lost >= 0),
	competitions_drawn integer NOT NULL DEFAULT 0 CHECK (competitions_drawn >= 0)
) WITH (fillfactor = 80);
COMMENT ON TABLE olympiad_noble IS 'Olympiad statistics of a noble in the current cycle.';
COMMENT ON COLUMN olympiad_noble.player_id IS 'Noble player.';
COMMENT ON COLUMN olympiad_noble.class_id IS 'Base class the noble competes with.';
COMMENT ON COLUMN olympiad_noble.olympiad_points IS 'Olympiad points; the game does not keep them from going below zero.';
COMMENT ON COLUMN olympiad_noble.competitions_done IS 'Matches played.';
COMMENT ON COLUMN olympiad_noble.competitions_won IS 'Matches won.';
COMMENT ON COLUMN olympiad_noble.competitions_lost IS 'Matches lost.';
COMMENT ON COLUMN olympiad_noble.competitions_drawn IS 'Matches drawn.';
CREATE INDEX olympiad_noble_class_id_idx ON olympiad_noble (class_id);

-- Same columns in the same order as olympiad_noble: the monthly snapshot is
-- INSERT INTO olympiad_noble_month_end SELECT * FROM olympiad_noble.
CREATE TABLE olympiad_noble_month_end (
	player_id integer PRIMARY KEY REFERENCES player ON DELETE CASCADE,
	class_id smallint NOT NULL REFERENCES player_class,
	olympiad_points integer NOT NULL DEFAULT 0,
	competitions_done integer NOT NULL DEFAULT 0 CHECK (competitions_done >= 0),
	competitions_won integer NOT NULL DEFAULT 0 CHECK (competitions_won >= 0),
	competitions_lost integer NOT NULL DEFAULT 0 CHECK (competitions_lost >= 0),
	competitions_drawn integer NOT NULL DEFAULT 0 CHECK (competitions_drawn >= 0)
);
COMMENT ON TABLE olympiad_noble_month_end IS 'Copy of olympiad_noble taken at the end of the last Olympiad cycle; ranks and hero candidates are read from it.';
COMMENT ON COLUMN olympiad_noble_month_end.player_id IS 'Noble player.';
COMMENT ON COLUMN olympiad_noble_month_end.class_id IS 'Base class the noble competed with.';
COMMENT ON COLUMN olympiad_noble_month_end.olympiad_points IS 'Olympiad points at the end of the cycle.';
COMMENT ON COLUMN olympiad_noble_month_end.competitions_done IS 'Matches played.';
COMMENT ON COLUMN olympiad_noble_month_end.competitions_won IS 'Matches won.';
COMMENT ON COLUMN olympiad_noble_month_end.competitions_lost IS 'Matches lost.';
COMMENT ON COLUMN olympiad_noble_month_end.competitions_drawn IS 'Matches drawn.';
CREATE INDEX olympiad_noble_month_end_class_id_idx ON olympiad_noble_month_end (class_id);

CREATE TABLE hero (
	player_id integer PRIMARY KEY REFERENCES player ON DELETE CASCADE,
	class_id smallint NOT NULL REFERENCES player_class,
	hero_count integer NOT NULL DEFAULT 0 CHECK (hero_count >= 0),
	is_current boolean NOT NULL DEFAULT false
);
COMMENT ON TABLE hero IS 'Players who have been Olympiad heroes.';
COMMENT ON COLUMN hero.player_id IS 'Hero player.';
COMMENT ON COLUMN hero.class_id IS 'Class the player became hero with.';
COMMENT ON COLUMN hero.hero_count IS 'How many times the player has been hero.';
COMMENT ON COLUMN hero.is_current IS 'True for the heroes of the current Olympiad period.';
CREATE INDEX hero_class_id_idx ON hero (class_id);

-- Couples -------------------------------------------------------------------

CREATE TABLE couple (
	id integer PRIMARY KEY,
	player1_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	player2_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	is_married boolean NOT NULL DEFAULT false,
	engaged_at timestamptz NOT NULL,
	married_at timestamptz
);
COMMENT ON TABLE couple IS 'Engaged or married pairs of players (wedding mod). The id comes from the IdFactory object id space.';
COMMENT ON COLUMN couple.player1_id IS 'Player who proposed.';
COMMENT ON COLUMN couple.player2_id IS 'Player who accepted.';
COMMENT ON COLUMN couple.is_married IS 'True after the wedding ceremony; false while only engaged.';
COMMENT ON COLUMN couple.engaged_at IS 'Moment of the engagement.';
COMMENT ON COLUMN couple.married_at IS 'Moment of the wedding; NULL while only engaged.';
CREATE INDEX couple_player1_id_idx ON couple (player1_id);
CREATE INDEX couple_player2_id_idx ON couple (player2_id);

-- Bosses --------------------------------------------------------------------

CREATE TABLE grand_boss_state (
	npc_template_id integer PRIMARY KEY,
	state text CHECK (state IN ('NOTSPAWN', 'ALIVE', 'DEAD', 'INTERVAL')),
	respawn_at timestamptz,
	current_hp double precision CHECK (current_hp >= 0),
	current_mp double precision CHECK (current_mp >= 0),
	CHECK ((current_hp IS NULL) = (current_mp IS NULL))
);
COMMENT ON TABLE grand_boss_state IS 'Runtime state of grand bosses. Script-managed bosses (Antharas, Valakas, ...) use state; bosses spawned by GrandBossSpawnManager (spawn in catalog.grand_boss_spawn) use current_hp and current_mp.';
COMMENT ON COLUMN grand_boss_state.npc_template_id IS 'Boss NPC template (catalog.npc_template.id).';
COMMENT ON COLUMN grand_boss_state.state IS 'Script state, the name of the Java enum GrandBossState.StateEnum; NULL for bosses spawned by GrandBossSpawnManager.';
COMMENT ON COLUMN grand_boss_state.respawn_at IS 'Moment the boss may appear again; NULL when it is not waiting for a respawn.';
COMMENT ON COLUMN grand_boss_state.current_hp IS 'HP of the living boss at the last save; NULL for script-managed bosses.';
COMMENT ON COLUMN grand_boss_state.current_mp IS 'MP of the living boss at the last save; NULL for script-managed bosses.';

INSERT INTO grand_boss_state (npc_template_id, state) VALUES
	(29019, 'NOTSPAWN'),
	(29020, 'NOTSPAWN'),
	(29028, 'NOTSPAWN'),
	(29045, 'NOTSPAWN'),
	(29062, 'NOTSPAWN'),
	(29065, 'NOTSPAWN'),
	(29099, 'NOTSPAWN');

CREATE TABLE raid_boss_state (
	npc_template_id integer PRIMARY KEY,
	respawn_at timestamptz,
	current_hp double precision NOT NULL CHECK (current_hp >= 0),
	current_mp double precision NOT NULL CHECK (current_mp >= 0)
);
COMMENT ON TABLE raid_boss_state IS 'Runtime state of raid bosses; the spawn itself is catalog.raid_boss_spawn. No row means the boss spawns at full HP and MP.';
COMMENT ON COLUMN raid_boss_state.npc_template_id IS 'Boss NPC template (catalog.npc_template.id).';
COMMENT ON COLUMN raid_boss_state.respawn_at IS 'Moment the killed boss respawns; NULL while the boss is alive.';
COMMENT ON COLUMN raid_boss_state.current_hp IS 'HP of the living boss at the last save.';
COMMENT ON COLUMN raid_boss_state.current_mp IS 'MP of the living boss at the last save.';

-- Hellbound -----------------------------------------------------------------

CREATE TABLE hellbound_variable (
	name text PRIMARY KEY CHECK (name IN ('trust_points', 'warpgates_energy', 'warpgatesLastcheck')),
	value text NOT NULL
);
COMMENT ON TABLE hellbound_variable IS 'Hellbound island progress, one row per variable (HellboundManager).';
COMMENT ON COLUMN hellbound_variable.name IS 'Variable name as HellboundManager writes it.';
COMMENT ON COLUMN hellbound_variable.value IS 'Value as text: trust_points and warpgates_energy are integers, warpgatesLastcheck is epoch milliseconds.';

-- Lottery and online record -------------------------------------------------

CREATE TABLE lottery_round (
	id integer PRIMARY KEY CHECK (id >= 1),
	end_at timestamptz NOT NULL,
	is_finished boolean NOT NULL DEFAULT false,
	prize bigint NOT NULL DEFAULT 0,
	next_prize bigint NOT NULL DEFAULT 0,
	winning_mask_low integer NOT NULL DEFAULT 0,
	winning_mask_high integer NOT NULL DEFAULT 0,
	first_prize bigint NOT NULL DEFAULT 0,
	second_prize bigint NOT NULL DEFAULT 0,
	third_prize bigint NOT NULL DEFAULT 0
);
COMMENT ON TABLE lottery_round IS 'Rounds of the adena lottery (Lottery). The id is the round number the game shows.';
COMMENT ON COLUMN lottery_round.end_at IS 'Moment the round draws its numbers.';
COMMENT ON COLUMN lottery_round.is_finished IS 'True once the numbers are drawn and the prizes computed.';
COMMENT ON COLUMN lottery_round.prize IS 'Prize pool of the round in adena.';
COMMENT ON COLUMN lottery_round.next_prize IS 'Prize pool carried over to the next round in adena; equals prize until the draw.';
COMMENT ON COLUMN lottery_round.winning_mask_low IS 'Bit mask of the winning numbers 1 to 16, compared with the ticket enchant level; 0 before the draw.';
COMMENT ON COLUMN lottery_round.winning_mask_high IS 'Bit mask of the winning numbers 17 to 20, compared with the ticket custom_type2; 0 before the draw.';
COMMENT ON COLUMN lottery_round.first_prize IS 'Adena paid per ticket with all five numbers; 0 before the draw.';
COMMENT ON COLUMN lottery_round.second_prize IS 'Adena paid per ticket with four numbers; 0 before the draw.';
COMMENT ON COLUMN lottery_round.third_prize IS 'Adena paid per ticket with three numbers; 0 before the draw.';

CREATE TABLE online_record (
	id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	player_count integer NOT NULL CHECK (player_count >= 0),
	recorded_on date NOT NULL DEFAULT CURRENT_DATE
);
COMMENT ON TABLE online_record IS 'History of the maximum number of players online; a row is added whenever the record is broken (RecordTable).';
COMMENT ON COLUMN online_record.player_count IS 'Players online when the record was set.';
COMMENT ON COLUMN online_record.recorded_on IS 'Day the record was set.';

INSERT INTO online_record (player_count) VALUES (0);

-- Community board -----------------------------------------------------------

CREATE TABLE forum (
	id integer PRIMARY KEY,
	name text NOT NULL,
	parent_forum_id integer REFERENCES forum ON DELETE CASCADE,
	kind smallint NOT NULL CHECK (kind BETWEEN 0 AND 4),
	access smallint NOT NULL CHECK (access BETWEEN 0 AND 3),
	owner_player_id integer REFERENCES player ON DELETE CASCADE,
	owner_clan_id integer REFERENCES clan ON DELETE CASCADE,
	CHECK (owner_player_id IS NULL OR owner_clan_id IS NULL)
);
COMMENT ON TABLE forum IS 'Community board forums: four roots and one child forum per clan (clan forum), per player (mail) or per account (memo). The id is assigned by ForumsBBSManager.';
COMMENT ON COLUMN forum.name IS 'Forum name: the root name, or the clan, player or account name of a child forum.';
COMMENT ON COLUMN forum.parent_forum_id IS 'Parent forum; NULL for a root forum.';
COMMENT ON COLUMN forum.kind IS 'Forum kind: 0 root, 1 normal, 2 clan, 3 memo, 4 mail (Forum.ROOT ... Forum.MAIL).';
COMMENT ON COLUMN forum.access IS 'Who may read it: 0 invisible, 1 all, 2 clan members only, 3 owner only (Forum.INVISIBLE ... Forum.OWNERONLY).';
COMMENT ON COLUMN forum.owner_player_id IS 'Owning player of a mail or memo forum; NULL for root and clan forums.';
COMMENT ON COLUMN forum.owner_clan_id IS 'Owning clan of a clan forum; NULL for other forums.';
CREATE INDEX forum_parent_forum_id_idx ON forum (parent_forum_id);
CREATE INDEX forum_owner_player_id_idx ON forum (owner_player_id);
CREATE INDEX forum_owner_clan_id_idx ON forum (owner_clan_id);

INSERT INTO forum (id, name, parent_forum_id, kind, access, owner_player_id, owner_clan_id) VALUES
	(1, 'NormalRoot', NULL, 0, 1, NULL, NULL),
	(2, 'ClanRoot', NULL, 0, 0, NULL, NULL),
	(3, 'MemoRoot', NULL, 0, 0, NULL, NULL),
	(4, 'MailRoot', NULL, 0, 0, NULL, NULL);

CREATE TABLE forum_topic (
	forum_id integer NOT NULL REFERENCES forum ON DELETE CASCADE,
	topic_number integer NOT NULL CHECK (topic_number >= 1),
	name text NOT NULL,
	created_at timestamptz NOT NULL,
	author_name text NOT NULL,
	author_player_id integer REFERENCES player ON DELETE SET NULL,
	kind smallint NOT NULL CHECK (kind IN (0, 1)),
	PRIMARY KEY (forum_id, topic_number)
);
COMMENT ON TABLE forum_topic IS 'Topics of a community board forum. Topic numbers count up per forum (TopicBBSManager.getMaxID).';
COMMENT ON COLUMN forum_topic.forum_id IS 'Forum the topic is in.';
COMMENT ON COLUMN forum_topic.topic_number IS 'Topic number inside the forum, starting at 1.';
COMMENT ON COLUMN forum_topic.name IS 'Topic title.';
COMMENT ON COLUMN forum_topic.created_at IS 'Moment the topic was created.';
COMMENT ON COLUMN forum_topic.author_name IS 'Name of the author at the time of writing.';
COMMENT ON COLUMN forum_topic.author_player_id IS 'Author; NULL after the author was deleted.';
COMMENT ON COLUMN forum_topic.kind IS 'Topic kind: 0 normal, 1 memo (Topic.MORMAL, Topic.MEMO).';
CREATE INDEX forum_topic_author_player_id_idx ON forum_topic (author_player_id);

CREATE TABLE forum_post (
	forum_id integer NOT NULL,
	topic_number integer NOT NULL,
	post_number integer NOT NULL CHECK (post_number >= 0),
	author_name text NOT NULL,
	author_player_id integer REFERENCES player ON DELETE SET NULL,
	posted_at timestamptz NOT NULL,
	body text NOT NULL,
	PRIMARY KEY (forum_id, topic_number, post_number),
	FOREIGN KEY (forum_id, topic_number) REFERENCES forum_topic ON DELETE CASCADE
);
COMMENT ON TABLE forum_post IS 'Posts of a community board topic, in post_number order.';
COMMENT ON COLUMN forum_post.forum_id IS 'Forum of the topic.';
COMMENT ON COLUMN forum_post.topic_number IS 'Topic the post belongs to (forum_topic.topic_number).';
COMMENT ON COLUMN forum_post.post_number IS 'Post number inside the topic, starting at 0 for the opening post.';
COMMENT ON COLUMN forum_post.author_name IS 'Name of the author at the time of writing.';
COMMENT ON COLUMN forum_post.author_player_id IS 'Author; NULL after the author was deleted.';
COMMENT ON COLUMN forum_post.posted_at IS 'Moment the post was written.';
COMMENT ON COLUMN forum_post.body IS 'Post text.';
CREATE INDEX forum_post_author_player_id_idx ON forum_post (author_player_id);

-- PvP events configured by admins -------------------------------------------

CREATE TABLE ctf_event (
	id smallint PRIMARY KEY DEFAULT 0 CHECK (id = 0),
	name text NOT NULL DEFAULT '',
	description text NOT NULL DEFAULT '',
	joining_location_name text NOT NULL DEFAULT '',
	min_level smallint NOT NULL DEFAULT 0 CHECK (min_level >= 0),
	max_level smallint NOT NULL DEFAULT 0 CHECK (max_level >= 0),
	npc_template_id integer NOT NULL DEFAULT 0,
	npc_x integer NOT NULL DEFAULT 0,
	npc_y integer NOT NULL DEFAULT 0,
	npc_z integer NOT NULL DEFAULT 0,
	npc_heading integer NOT NULL DEFAULT 0,
	reward_item_template_id integer NOT NULL DEFAULT 0,
	reward_count integer NOT NULL DEFAULT 0 CHECK (reward_count >= 0),
	join_duration_s integer NOT NULL DEFAULT 0 CHECK (join_duration_s >= 0),
	event_duration_s integer NOT NULL DEFAULT 0 CHECK (event_duration_s >= 0),
	min_players integer NOT NULL DEFAULT 0 CHECK (min_players >= 0),
	max_players integer NOT NULL DEFAULT 0 CHECK (max_players >= 0)
);
COMMENT ON TABLE ctf_event IS 'Capture the Flag event settings saved by a GM (class CTF). At most one row (id 0); the GM save replaces it.';
COMMENT ON COLUMN ctf_event.name IS 'Event name shown to players.';
COMMENT ON COLUMN ctf_event.description IS 'Event description shown to players.';
COMMENT ON COLUMN ctf_event.joining_location_name IS 'Name of the place where players register, shown in announcements.';
COMMENT ON COLUMN ctf_event.min_level IS 'Lowest player level allowed to join.';
COMMENT ON COLUMN ctf_event.max_level IS 'Highest player level allowed to join.';
COMMENT ON COLUMN ctf_event.npc_template_id IS 'Registration NPC template (catalog.npc_template.id); 0 when not configured.';
COMMENT ON COLUMN ctf_event.npc_x IS 'X of the registration NPC.';
COMMENT ON COLUMN ctf_event.npc_y IS 'Y of the registration NPC.';
COMMENT ON COLUMN ctf_event.npc_z IS 'Z of the registration NPC.';
COMMENT ON COLUMN ctf_event.npc_heading IS 'Heading of the registration NPC.';
COMMENT ON COLUMN ctf_event.reward_item_template_id IS 'Reward item template (catalog tables weapon_template, armor_template, etc_item_template); 0 when not configured.';
COMMENT ON COLUMN ctf_event.reward_count IS 'Number of reward items per winner.';
COMMENT ON COLUMN ctf_event.join_duration_s IS 'Length of the registration phase in seconds.';
COMMENT ON COLUMN ctf_event.event_duration_s IS 'Length of the fight in seconds.';
COMMENT ON COLUMN ctf_event.min_players IS 'Players needed to start the event.';
COMMENT ON COLUMN ctf_event.max_players IS 'Largest number of registered players.';

CREATE TABLE ctf_event_team (
	team_number smallint PRIMARY KEY CHECK (team_number >= 0),
	name text NOT NULL,
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	name_color integer NOT NULL DEFAULT 0,
	flag_x integer NOT NULL,
	flag_y integer NOT NULL,
	flag_z integer NOT NULL
);
COMMENT ON TABLE ctf_event_team IS 'Teams of the Capture the Flag event, saved together with ctf_event.';
COMMENT ON COLUMN ctf_event_team.team_number IS 'Team index, starting at 0.';
COMMENT ON COLUMN ctf_event_team.name IS 'Team name.';
COMMENT ON COLUMN ctf_event_team.x IS 'X of the team start point.';
COMMENT ON COLUMN ctf_event_team.y IS 'Y of the team start point.';
COMMENT ON COLUMN ctf_event_team.z IS 'Z of the team start point.';
COMMENT ON COLUMN ctf_event_team.name_color IS 'Name color of team members as the client encodes it (0xBBGGRR).';
COMMENT ON COLUMN ctf_event_team.flag_x IS 'X of the team flag.';
COMMENT ON COLUMN ctf_event_team.flag_y IS 'Y of the team flag.';
COMMENT ON COLUMN ctf_event_team.flag_z IS 'Z of the team flag.';

CREATE TABLE tvt_event (
	id smallint PRIMARY KEY DEFAULT 0 CHECK (id = 0),
	name text NOT NULL DEFAULT '',
	description text NOT NULL DEFAULT '',
	joining_location_name text NOT NULL DEFAULT '',
	min_level smallint NOT NULL DEFAULT 0 CHECK (min_level >= 0),
	max_level smallint NOT NULL DEFAULT 0 CHECK (max_level >= 0),
	npc_template_id integer NOT NULL DEFAULT 0,
	npc_x integer NOT NULL DEFAULT 0,
	npc_y integer NOT NULL DEFAULT 0,
	npc_z integer NOT NULL DEFAULT 0,
	npc_heading integer NOT NULL DEFAULT 0,
	reward_item_template_id integer NOT NULL DEFAULT 0,
	reward_count integer NOT NULL DEFAULT 0 CHECK (reward_count >= 0),
	join_duration_s integer NOT NULL DEFAULT 0 CHECK (join_duration_s >= 0),
	event_duration_s integer NOT NULL DEFAULT 0 CHECK (event_duration_s >= 0),
	min_players integer NOT NULL DEFAULT 0 CHECK (min_players >= 0),
	max_players integer NOT NULL DEFAULT 0 CHECK (max_players >= 0)
);
COMMENT ON TABLE tvt_event IS 'Team versus Team event settings saved by a GM (class TvT). At most one row (id 0); the GM save replaces it.';
COMMENT ON COLUMN tvt_event.name IS 'Event name shown to players.';
COMMENT ON COLUMN tvt_event.description IS 'Event description shown to players.';
COMMENT ON COLUMN tvt_event.joining_location_name IS 'Name of the place where players register, shown in announcements.';
COMMENT ON COLUMN tvt_event.min_level IS 'Lowest player level allowed to join.';
COMMENT ON COLUMN tvt_event.max_level IS 'Highest player level allowed to join.';
COMMENT ON COLUMN tvt_event.npc_template_id IS 'Registration NPC template (catalog.npc_template.id); 0 when not configured.';
COMMENT ON COLUMN tvt_event.npc_x IS 'X of the registration NPC.';
COMMENT ON COLUMN tvt_event.npc_y IS 'Y of the registration NPC.';
COMMENT ON COLUMN tvt_event.npc_z IS 'Z of the registration NPC.';
COMMENT ON COLUMN tvt_event.npc_heading IS 'Heading of the registration NPC.';
COMMENT ON COLUMN tvt_event.reward_item_template_id IS 'Reward item template (catalog tables weapon_template, armor_template, etc_item_template); 0 when not configured.';
COMMENT ON COLUMN tvt_event.reward_count IS 'Number of reward items per winner.';
COMMENT ON COLUMN tvt_event.join_duration_s IS 'Length of the registration phase in seconds.';
COMMENT ON COLUMN tvt_event.event_duration_s IS 'Length of the fight in seconds.';
COMMENT ON COLUMN tvt_event.min_players IS 'Players needed to start the event.';
COMMENT ON COLUMN tvt_event.max_players IS 'Largest number of registered players.';

CREATE TABLE tvt_event_team (
	team_number smallint PRIMARY KEY CHECK (team_number >= 0),
	name text NOT NULL,
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	name_color integer NOT NULL DEFAULT 0
);
COMMENT ON TABLE tvt_event_team IS 'Teams of the Team versus Team event, saved together with tvt_event.';
COMMENT ON COLUMN tvt_event_team.team_number IS 'Team index, starting at 0.';
COMMENT ON COLUMN tvt_event_team.name IS 'Team name.';
COMMENT ON COLUMN tvt_event_team.x IS 'X of the team start point.';
COMMENT ON COLUMN tvt_event_team.y IS 'Y of the team start point.';
COMMENT ON COLUMN tvt_event_team.z IS 'Z of the team start point.';
COMMENT ON COLUMN tvt_event_team.name_color IS 'Name color of team members as the client encodes it (0xBBGGRR).';

CREATE TABLE dm_event (
	id smallint PRIMARY KEY DEFAULT 0 CHECK (id = 0),
	name text NOT NULL DEFAULT '',
	description text NOT NULL DEFAULT '',
	joining_location_name text NOT NULL DEFAULT '',
	min_level smallint NOT NULL DEFAULT 0 CHECK (min_level >= 0),
	max_level smallint NOT NULL DEFAULT 0 CHECK (max_level >= 0),
	npc_template_id integer NOT NULL DEFAULT 0,
	npc_x integer NOT NULL DEFAULT 0,
	npc_y integer NOT NULL DEFAULT 0,
	npc_z integer NOT NULL DEFAULT 0,
	reward_item_template_id integer NOT NULL DEFAULT 0,
	reward_count integer NOT NULL DEFAULT 0 CHECK (reward_count >= 0),
	name_color integer NOT NULL DEFAULT 0,
	player_x integer NOT NULL DEFAULT 0,
	player_y integer NOT NULL DEFAULT 0,
	player_z integer NOT NULL DEFAULT 0
);
COMMENT ON TABLE dm_event IS 'Deathmatch event settings saved by a GM (class DM). At most one row (id 0); the GM save replaces it.';
COMMENT ON COLUMN dm_event.name IS 'Event name shown to players.';
COMMENT ON COLUMN dm_event.description IS 'Event description shown to players.';
COMMENT ON COLUMN dm_event.joining_location_name IS 'Name of the place where players register, shown in announcements.';
COMMENT ON COLUMN dm_event.min_level IS 'Lowest player level allowed to join.';
COMMENT ON COLUMN dm_event.max_level IS 'Highest player level allowed to join.';
COMMENT ON COLUMN dm_event.npc_template_id IS 'Registration NPC template (catalog.npc_template.id); 0 when not configured.';
COMMENT ON COLUMN dm_event.npc_x IS 'X of the registration NPC.';
COMMENT ON COLUMN dm_event.npc_y IS 'Y of the registration NPC.';
COMMENT ON COLUMN dm_event.npc_z IS 'Z of the registration NPC.';
COMMENT ON COLUMN dm_event.reward_item_template_id IS 'Reward item template (catalog tables weapon_template, armor_template, etc_item_template); 0 when not configured.';
COMMENT ON COLUMN dm_event.reward_count IS 'Number of reward items for the winner.';
COMMENT ON COLUMN dm_event.name_color IS 'Name color of participants as the client encodes it (0xBBGGRR).';
COMMENT ON COLUMN dm_event.player_x IS 'X of the fight start point.';
COMMENT ON COLUMN dm_event.player_y IS 'Y of the fight start point.';
COMMENT ON COLUMN dm_event.player_z IS 'Z of the fight start point.';

CREATE TABLE vip_event_route (
	race_id smallint PRIMARY KEY REFERENCES race,
	end_x integer NOT NULL,
	end_y integer NOT NULL,
	end_z integer NOT NULL,
	start_x integer NOT NULL,
	start_y integer NOT NULL,
	start_z integer NOT NULL
);
COMMENT ON TABLE vip_event_route IS 'Start and goal points of the VIP escort event for each race whose players can be the VIP team (class VIP).';
COMMENT ON COLUMN vip_event_route.race_id IS 'Race of the VIP team.';
COMMENT ON COLUMN vip_event_route.end_x IS 'X of the goal the VIP must reach.';
COMMENT ON COLUMN vip_event_route.end_y IS 'Y of the goal the VIP must reach.';
COMMENT ON COLUMN vip_event_route.end_z IS 'Z of the goal the VIP must reach.';
COMMENT ON COLUMN vip_event_route.start_x IS 'X of the VIP team start point.';
COMMENT ON COLUMN vip_event_route.start_y IS 'Y of the VIP team start point.';
COMMENT ON COLUMN vip_event_route.start_z IS 'Z of the VIP team start point.';

INSERT INTO vip_event_route (race_id, end_x, end_y, end_z, start_x, start_y, start_z) VALUES
	(0, -84583, 242788, -3735, -101319, 213272, -3100),
	(1, 45714, 49703, -3065, 55782, 81597, -3610),
	(2, 11249, 16890, -4667, -22732, 12586, -2996),
	(3, -44737, -113582, -204, 27053, -88454, -3286),
	(4, 116047, -179059, -1026, 121145, -215673, -3571);

-- Scheduled tasks -----------------------------------------------------------

CREATE TABLE global_task (
	id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	task_name text NOT NULL,
	schedule_type text NOT NULL CHECK (schedule_type IN ('TYPE_NONE', 'TYPE_TIME', 'TYPE_SHEDULED', 'TYPE_FIXED_SHEDULED', 'TYPE_GLOBAL_TASK', 'TYPE_STARTUP', 'TYPE_SPECIAL')),
	last_run_at timestamptz,
	parameter1 text NOT NULL DEFAULT '',
	parameter2 text NOT NULL DEFAULT '',
	parameter3 text NOT NULL DEFAULT ''
);
COMMENT ON TABLE global_task IS 'Server tasks that TaskManager schedules at startup (restart, Olympiad save, Seven Signs update, ...).';
COMMENT ON COLUMN global_task.task_name IS 'Name of the task handler (TaskHandler.getName, for example OlympiadSave).';
COMMENT ON COLUMN global_task.schedule_type IS 'How the task is scheduled, the name of the Java enum TaskTypes.';
COMMENT ON COLUMN global_task.last_run_at IS 'Moment the task last ran; NULL when it never ran.';
COMMENT ON COLUMN global_task.parameter1 IS 'First schedule parameter; meaning depends on schedule_type (delay in ms, a date, or the day interval); empty when unused.';
COMMENT ON COLUMN global_task.parameter2 IS 'Second schedule parameter (repeat interval in ms, or HH:MM:SS); empty when unused.';
COMMENT ON COLUMN global_task.parameter3 IS 'Third schedule parameter; empty when unused.';
CREATE INDEX global_task_task_name_idx ON global_task (task_name);

-- GM audit log --------------------------------------------------------------

-- Partitioned by month on created_at. A maintenance job creates the monthly
-- partitions ahead of time (gm_audit_yYYYYmMM); rows outside every monthly
-- partition land in gm_audit_default. Writes use SET LOCAL
-- synchronous_commit = off: losing the last audit rows in a crash is accepted.
CREATE TABLE gm_audit (
	id bigint GENERATED ALWAYS AS IDENTITY,
	created_at timestamptz NOT NULL DEFAULT now(),
	gm_name text NOT NULL,
	target text NOT NULL,
	action_type text NOT NULL,
	action text NOT NULL,
	parameters text NOT NULL DEFAULT '',
	PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
COMMENT ON TABLE gm_audit IS 'Log of GM actions (GMAudit): admin commands, GM item drops and transfers, telnet enchants. Partitioned by month on created_at; writes use synchronous_commit off.';
COMMENT ON COLUMN gm_audit.created_at IS 'Moment of the action.';
COMMENT ON COLUMN gm_audit.gm_name IS 'Acting GM as "account - player name", or the telnet client IP for telnet actions.';
COMMENT ON COLUMN gm_audit.target IS 'Target as "object id - name", or the text null when the GM had no target.';
COMMENT ON COLUMN gm_audit.action_type IS 'Kind of action: admincommand, dropitem, transferitem, telnet-enchant.';
COMMENT ON COLUMN gm_audit.action IS 'The admin command, or the process that moved the item.';
COMMENT ON COLUMN gm_audit.parameters IS 'Command parameters or item details; empty when none.';

CREATE TABLE gm_audit_default PARTITION OF gm_audit DEFAULT;
COMMENT ON TABLE gm_audit_default IS 'Default partition of gm_audit: rows for which no monthly partition exists yet.';
COMMENT ON COLUMN gm_audit_default.created_at IS 'Moment of the action.';
COMMENT ON COLUMN gm_audit_default.gm_name IS 'Acting GM as "account - player name", or the telnet client IP for telnet actions.';
COMMENT ON COLUMN gm_audit_default.target IS 'Target as "object id - name", or the text null when the GM had no target.';
COMMENT ON COLUMN gm_audit_default.action_type IS 'Kind of action: admincommand, dropitem, transferitem, telnet-enchant.';
COMMENT ON COLUMN gm_audit_default.action IS 'The admin command, or the process that moved the item.';
COMMENT ON COLUMN gm_audit_default.parameters IS 'Command parameters or item details; empty when none.';

-- Player restrictions -------------------------------------------------------

CREATE TABLE player_restriction (
	id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	restriction text NOT NULL CHECK (restriction IN ('PlayerUnmount', 'PlayerCast', 'PlayerTeleport', 'PlayerScrollTeleport', 'PlayerGotoLove', 'PlayerSummonFriend', 'PlayerChat')),
	remaining_ms bigint,
	message text
);
COMMENT ON TABLE player_restriction IS 'Restrictions imposed on players, for example a chat ban (ObjectRestrictions). The game replaces all rows on every save.';
COMMENT ON COLUMN player_restriction.player_id IS 'Restricted player.';
COMMENT ON COLUMN player_restriction.restriction IS 'Restriction, the name of the Java enum AvailableRestriction.';
COMMENT ON COLUMN player_restriction.remaining_ms IS 'Time left until the restriction is lifted, in milliseconds, counted while the player is online; NULL for a permanent restriction.';
COMMENT ON COLUMN player_restriction.message IS 'Message shown to the player when a timed restriction ends; NULL when none.';
CREATE INDEX player_restriction_player_id_idx ON player_restriction (player_id);

-- Quest data that belongs to no player --------------------------------------

CREATE TABLE quest_global_variable (
	quest_name text NOT NULL,
	name text NOT NULL,
	value text NOT NULL,
	PRIMARY KEY (quest_name, name)
);
COMMENT ON TABLE quest_global_variable IS 'Quest variables that belong to no player (Quest.saveGlobalQuestVar).';
COMMENT ON COLUMN quest_global_variable.quest_name IS 'Quest script name, for example 415_PathToOrcMonk.';
COMMENT ON COLUMN quest_global_variable.name IS 'Variable name chosen by the script (sometimes an account name).';
COMMENT ON COLUMN quest_global_variable.value IS 'Variable value as text.';

-- Community board changelog and automatic announcements ---------------------

CREATE TABLE changelog_entry (
	id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	published_on date NOT NULL,
	introduction text NOT NULL,
	body text NOT NULL,
	author text NOT NULL
);
COMMENT ON TABLE changelog_entry IS 'Server changelog shown on the community board update page, newest id first. Written by admins, not by the game.';
COMMENT ON COLUMN changelog_entry.published_on IS 'Day of the change.';
COMMENT ON COLUMN changelog_entry.introduction IS 'Short summary line.';
COMMENT ON COLUMN changelog_entry.body IS 'Full text of the entry.';
COMMENT ON COLUMN changelog_entry.author IS 'Name of the author.';

CREATE TABLE auto_announcement (
	id integer PRIMARY KEY,
	initial_delay_s bigint NOT NULL CHECK (initial_delay_s >= 0),
	interval_s bigint NOT NULL CHECK (interval_s > 0),
	repeat_count integer NOT NULL CHECK (repeat_count >= 0),
	message text NOT NULL
);
COMMENT ON TABLE auto_announcement IS 'Announcements the server repeats automatically (AutoAnnouncements). Written by admins, not by the game.';
COMMENT ON COLUMN auto_announcement.initial_delay_s IS 'Delay after server start before the first announcement, in seconds.';
COMMENT ON COLUMN auto_announcement.interval_s IS 'Time between announcements, in seconds.';
COMMENT ON COLUMN auto_announcement.repeat_count IS 'How many times to announce; 0 means forever.';
COMMENT ON COLUMN auto_announcement.message IS 'Announcement text; each line is announced separately.';
