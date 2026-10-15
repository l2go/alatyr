-- Clans: the clan itself, its alliance membership, notice, rank privileges,
-- clan skills, sub-units (academy, royal guards, orders of knights) and clan wars.
-- An alliance has no table of its own: its id is the id of the leader clan, and
-- every member clan stores alliance_id, alliance_name and alliance_crest_id. The
-- leader clan row (alliance_id = id) is authoritative for the alliance name and crest.
-- Castle ownership (clan.castle_id) and the clan hall auction bid
-- (clan.clan_hall_auction_id) point to tables of V1_05__residence, which adds
-- their foreign keys.

CREATE TABLE clan (
	id integer PRIMARY KEY,
	name citext NOT NULL UNIQUE,
	level smallint NOT NULL DEFAULT 0 CHECK (level >= 0),
	leader_player_id integer NOT NULL REFERENCES player,
	reputation_score integer NOT NULL DEFAULT 0,
	crest_id integer,
	large_crest_id integer,
	castle_id smallint UNIQUE,
	clan_hall_auction_id integer,
	alliance_id integer REFERENCES clan ON DELETE SET NULL,
	alliance_name citext,
	alliance_crest_id integer,
	alliance_penalty_expire_at timestamptz,
	alliance_penalty_type text CHECK (alliance_penalty_type IN ('CLAN_LEAVED', 'CLAN_DISMISSED', 'DISMISS_CLAN', 'DISSOLVE_ALLY')),
	member_penalty_expire_at timestamptz,
	dissolve_at timestamptz
);
COMMENT ON TABLE clan IS 'Player clans. The row of an alliance leader clan (alliance_id = id) also holds the authoritative alliance name and crest.';
COMMENT ON COLUMN clan.name IS 'Clan name, unique without regard to case.';
COMMENT ON COLUMN clan.level IS 'Clan level, 0 for a new clan.';
COMMENT ON COLUMN clan.leader_player_id IS 'Player who leads the clan. A clan leader cannot be deleted.';
COMMENT ON COLUMN clan.reputation_score IS 'Clan reputation points; can be negative, which deactivates the clan skills.';
COMMENT ON COLUMN clan.crest_id IS 'Clan crest image id (file data/crests/Crest_<id>.bmp, id from the object id space); NULL when the clan has no crest.';
COMMENT ON COLUMN clan.large_crest_id IS 'Large clan crest image id (file data/crests/Crest_Large_<id>.bmp); NULL when the clan has no large crest.';
COMMENT ON COLUMN clan.castle_id IS 'Castle the clan owns (castle.id; the foreign key is added by the residence migration); NULL when it owns none. This is the only place castle ownership is stored.';
COMMENT ON COLUMN clan.clan_hall_auction_id IS 'Clan hall auction the clan currently bids at (the foreign key is added by the residence migration); NULL when it bids at none. Duplicates the auction bid rows, which are authoritative.';
COMMENT ON COLUMN clan.alliance_id IS 'Alliance of the clan, identified by the id of its leader clan; NULL when the clan is in no alliance.';
COMMENT ON COLUMN clan.alliance_name IS 'Alliance name, copied to every member clan; the leader clan row is authoritative and unique among alliances. NULL when the clan is in no alliance.';
COMMENT ON COLUMN clan.alliance_crest_id IS 'Alliance crest image id (file data/crests/AllyCrest_<id>.bmp), copied to every member clan; the leader clan row is authoritative. NULL when there is no alliance crest.';
COMMENT ON COLUMN clan.alliance_penalty_expire_at IS 'Moment the alliance penalty of the clan ends; NULL when there is no penalty.';
COMMENT ON COLUMN clan.alliance_penalty_type IS 'Kind of alliance penalty, named after the L2Clan.PENALTY_TYPE_* constants: CLAN_LEAVED, CLAN_DISMISSED (this clan cannot join an alliance), DISMISS_CLAN (the leader clan cannot accept a clan), DISSOLVE_ALLY (cannot create an alliance). NULL when there is no penalty.';
COMMENT ON COLUMN clan.member_penalty_expire_at IS 'Moment set when the clan dismisses a member; the clan cannot accept new members for the configured number of days. NULL when there is no penalty.';
COMMENT ON COLUMN clan.dissolve_at IS 'Moment the clan is dissolved after its leader requested it; NULL when no dissolution is pending.';
CREATE INDEX clan_leader_player_id_idx ON clan (leader_player_id);
CREATE INDEX clan_alliance_id_idx ON clan (alliance_id);
-- Alliance names are unique among alliances; member clans carry copies of the leader's name.
CREATE UNIQUE INDEX clan_alliance_name_idx ON clan (alliance_name) WHERE alliance_id = id;

ALTER TABLE player ADD FOREIGN KEY (clan_id) REFERENCES clan ON DELETE SET NULL;

CREATE TABLE clan_notice (
	clan_id integer PRIMARY KEY REFERENCES clan ON DELETE CASCADE,
	notice text NOT NULL,
	is_enabled boolean NOT NULL DEFAULT false
);
COMMENT ON TABLE clan_notice IS 'Clan notice shown to members when they enter the game.';
COMMENT ON COLUMN clan_notice.clan_id IS 'Clan the notice belongs to.';
COMMENT ON COLUMN clan_notice.notice IS 'Notice text; line breaks are stored as <br>.';
COMMENT ON COLUMN clan_notice.is_enabled IS 'Whether the notice is shown to members on entering the game.';

CREATE TABLE clan_rank_privilege (
	clan_id integer NOT NULL REFERENCES clan ON DELETE CASCADE,
	pledge_rank smallint NOT NULL,
	privileges integer NOT NULL DEFAULT 0,
	PRIMARY KEY (clan_id, pledge_rank)
);
COMMENT ON TABLE clan_rank_privilege IS 'Privileges that a clan grants to the members of one pledge rank.';
COMMENT ON COLUMN clan_rank_privilege.clan_id IS 'Clan that grants the privileges.';
COMMENT ON COLUMN clan_rank_privilege.pledge_rank IS 'Pledge rank (power grade) of the members, 1 to 9; rows with -1 from older versions are ignored.';
COMMENT ON COLUMN clan_rank_privilege.privileges IS 'Bit mask of L2Clan.CP_* privileges (for example 2 = invite members, 16777214 = all).';

CREATE TABLE clan_skill (
	clan_id integer NOT NULL REFERENCES clan ON DELETE CASCADE,
	skill_id integer NOT NULL,
	skill_level smallint NOT NULL CHECK (skill_level > 0),
	PRIMARY KEY (clan_id, skill_id)
);
COMMENT ON TABLE clan_skill IS 'Clan skills learned by a clan; they apply to its members.';
COMMENT ON COLUMN clan_skill.clan_id IS 'Clan that learned the skill.';
COMMENT ON COLUMN clan_skill.skill_id IS 'Skill id (skills are defined in the datapack XML files under data/stats/skills).';
COMMENT ON COLUMN clan_skill.skill_level IS 'Learned level of the skill.';

CREATE TABLE clan_subpledge (
	clan_id integer NOT NULL REFERENCES clan ON DELETE CASCADE,
	subpledge_type smallint NOT NULL CHECK (subpledge_type IN (-1, 100, 200, 1001, 1002, 2001, 2002)),
	name text NOT NULL,
	leader_player_id integer REFERENCES player ON DELETE SET NULL,
	PRIMARY KEY (clan_id, subpledge_type)
);
COMMENT ON TABLE clan_subpledge IS 'Sub-units of a clan: the academy, royal guards and orders of knights.';
COMMENT ON COLUMN clan_subpledge.clan_id IS 'Clan the sub-unit belongs to.';
COMMENT ON COLUMN clan_subpledge.subpledge_type IS 'Sub-unit type, the L2Clan.SUBUNIT_* value sent to the client: -1 academy, 100 and 200 royal guards, 1001, 1002, 2001, 2002 orders of knights.';
COMMENT ON COLUMN clan_subpledge.name IS 'Sub-unit name chosen by the clan leader.';
COMMENT ON COLUMN clan_subpledge.leader_player_id IS 'Player who leads the sub-unit; NULL for the academy or when the unit has no leader.';
CREATE INDEX clan_subpledge_leader_player_id_idx ON clan_subpledge (leader_player_id);

CREATE TABLE clan_war (
	declaring_clan_id integer NOT NULL REFERENCES clan ON DELETE CASCADE,
	target_clan_id integer NOT NULL REFERENCES clan ON DELETE CASCADE,
	PRIMARY KEY (declaring_clan_id, target_clan_id)
);
COMMENT ON TABLE clan_war IS 'War declared by one clan on another. The war is directed: a mutual war is two rows, one for each direction.';
COMMENT ON COLUMN clan_war.declaring_clan_id IS 'Clan that declared the war.';
COMMENT ON COLUMN clan_war.target_clan_id IS 'Clan the war is declared on.';
CREATE INDEX clan_war_target_clan_id_idx ON clan_war (target_clan_id);
