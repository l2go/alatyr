-- Players (characters) and everything that belongs to one player: subclasses,
-- skills, shortcuts, hennas, active effects, skill reuse timers, recipe books,
-- macros, teleport bookmarks, quest variables, social lists (friends, blocks,
-- recommendations) and small per-player counters.
--
-- A player's rows in the child tables are part of the player: they reference
-- player ON DELETE CASCADE, so deleting a character is a single DELETE.
--
-- class_index is the subclass slot a row belongs to: 0 is the base class,
-- 1..n are the subclasses in player_subclass. Child rows do not have a foreign
-- key to player_subclass: base class rows (class_index 0) have no row there,
-- and the game deletes and recreates a subclass row when a subclass is changed
-- while some child rows (certification, dwarven recipes) intentionally survive.
--
-- player.clan_id gets its foreign key in V1_03__clan.sql.

CREATE TABLE player (
	id integer PRIMARY KEY,
	account_name citext NOT NULL,
	name citext NOT NULL UNIQUE,
	title varchar(16) NOT NULL DEFAULT '',
	race_id smallint NOT NULL REFERENCES race,
	active_class_id smallint NOT NULL REFERENCES player_class,
	base_class_id smallint NOT NULL REFERENCES player_class,
	is_female boolean NOT NULL DEFAULT false,
	face smallint NOT NULL DEFAULT 0 CHECK (face >= 0),
	hair_style smallint NOT NULL DEFAULT 0 CHECK (hair_style >= 0),
	hair_color smallint NOT NULL DEFAULT 0 CHECK (hair_color >= 0),
	level smallint NOT NULL DEFAULT 1 CHECK (level >= 1),
	exp bigint NOT NULL DEFAULT 0 CHECK (exp >= 0),
	exp_before_death bigint NOT NULL DEFAULT 0 CHECK (exp_before_death >= 0),
	sp integer NOT NULL DEFAULT 0 CHECK (sp >= 0),
	max_hp integer NOT NULL DEFAULT 0 CHECK (max_hp >= 0),
	current_hp integer NOT NULL DEFAULT 0 CHECK (current_hp >= 0),
	max_cp integer NOT NULL DEFAULT 0 CHECK (max_cp >= 0),
	current_cp integer NOT NULL DEFAULT 0 CHECK (current_cp >= 0),
	max_mp integer NOT NULL DEFAULT 0 CHECK (max_mp >= 0),
	current_mp integer NOT NULL DEFAULT 0 CHECK (current_mp >= 0),
	x integer NOT NULL DEFAULT 0,
	y integer NOT NULL DEFAULT 0,
	z integer NOT NULL DEFAULT 0,
	heading integer NOT NULL DEFAULT 0,
	karma integer NOT NULL DEFAULT 0 CHECK (karma >= 0),
	fame integer NOT NULL DEFAULT 0 CHECK (fame >= 0),
	pvp_kills integer NOT NULL DEFAULT 0 CHECK (pvp_kills >= 0),
	pk_kills integer NOT NULL DEFAULT 0 CHECK (pk_kills >= 0),
	access_level integer NOT NULL DEFAULT 0,
	is_online boolean NOT NULL DEFAULT false,
	last_access_at timestamptz,
	online_time_s bigint NOT NULL DEFAULT 0 CHECK (online_time_s >= 0),
	delete_at timestamptz,
	is_noble boolean NOT NULL DEFAULT false,
	is_vip boolean NOT NULL DEFAULT false,
	is_in_seven_signs_dungeon boolean NOT NULL DEFAULT false,
	is_in_jail boolean NOT NULL DEFAULT false,
	jail_remaining_ms bigint NOT NULL DEFAULT 0 CHECK (jail_remaining_ms >= 0),
	newbie_reward_mask integer NOT NULL DEFAULT 1 CHECK (newbie_reward_mask >= 0),
	transformation_id smallint CHECK (transformation_id > 0),
	clan_id integer,
	pledge_type smallint NOT NULL DEFAULT 0,
	pledge_rank smallint NOT NULL DEFAULT 0 CHECK (pledge_rank >= 0),
	wants_peace boolean NOT NULL DEFAULT false,
	academy_join_level smallint NOT NULL DEFAULT 0 CHECK (academy_join_level >= 0),
	apprentice_player_id integer REFERENCES player ON DELETE SET NULL,
	sponsor_player_id integer REFERENCES player ON DELETE SET NULL,
	clan_join_allowed_at timestamptz,
	clan_create_allowed_at timestamptz,
	varka_ketra_alliance smallint NOT NULL DEFAULT 0 CHECK (varka_ketra_alliance BETWEEN -5 AND 5),
	death_penalty_level smallint NOT NULL DEFAULT 0 CHECK (death_penalty_level >= 0),
	vitality_points integer NOT NULL DEFAULT 0 CHECK (vitality_points >= 0),
	bookmark_slots smallint NOT NULL DEFAULT 0 CHECK (bookmark_slots >= 0)
) WITH (fillfactor = 80);
COMMENT ON TABLE player IS 'Player characters. Level, exp and sp are those of the base class; subclass progress is in player_subclass. Updated on every save, hence fillfactor 80.';
COMMENT ON COLUMN player.account_name IS 'Login account that owns the character (login.account.name, other module, no foreign key).';
COMMENT ON COLUMN player.name IS 'Character name, unique without regard to case.';
COMMENT ON COLUMN player.title IS 'Title shown under the name; empty when none.';
COMMENT ON COLUMN player.race_id IS 'Race. Duplicates the race of the base class (player_class.race_id); the base class is authoritative.';
COMMENT ON COLUMN player.active_class_id IS 'Class currently in use: the base class or the class of the active subclass.';
COMMENT ON COLUMN player.base_class_id IS 'Base (main) class, the class of class_index 0.';
COMMENT ON COLUMN player.is_female IS 'Sex of the character model: true for female.';
COMMENT ON COLUMN player.face IS 'Face style chosen at creation (client appearance code).';
COMMENT ON COLUMN player.hair_style IS 'Hair style (client appearance code).';
COMMENT ON COLUMN player.hair_color IS 'Hair color (client appearance code).';
COMMENT ON COLUMN player.level IS 'Level of the base class.';
COMMENT ON COLUMN player.exp IS 'Experience points of the base class.';
COMMENT ON COLUMN player.exp_before_death IS 'Experience before the last death, used to restore exp on resurrection.';
COMMENT ON COLUMN player.sp IS 'Skill points of the base class.';
COMMENT ON COLUMN player.max_hp IS 'Maximum HP at the last save, cached for the character selection screen; the live value is computed from stats.';
COMMENT ON COLUMN player.current_hp IS 'HP at the last save; below 1 means the character is dead.';
COMMENT ON COLUMN player.max_cp IS 'Maximum CP at the last save (cache, see max_hp).';
COMMENT ON COLUMN player.current_cp IS 'CP at the last save.';
COMMENT ON COLUMN player.max_mp IS 'Maximum MP at the last save (cache, see max_hp).';
COMMENT ON COLUMN player.current_mp IS 'MP at the last save.';
COMMENT ON COLUMN player.x IS 'World X coordinate of the last saved position.';
COMMENT ON COLUMN player.y IS 'World Y coordinate of the last saved position.';
COMMENT ON COLUMN player.z IS 'World Z coordinate of the last saved position.';
COMMENT ON COLUMN player.heading IS 'Facing direction (client units, 0..65535).';
COMMENT ON COLUMN player.karma IS 'Karma points; above 0 the character is chaotic.';
COMMENT ON COLUMN player.fame IS 'Fame points.';
COMMENT ON COLUMN player.pvp_kills IS 'Number of PvP kills.';
COMMENT ON COLUMN player.pk_kills IS 'Number of player kills that gave karma.';
COMMENT ON COLUMN player.access_level IS 'GM access level: 0 normal player, above 0 GM levels, below 0 banned.';
COMMENT ON COLUMN player.is_online IS 'True while the character is in the game; reset to false at server start.';
COMMENT ON COLUMN player.last_access_at IS 'Last login or logout; NULL when the character never entered the game.';
COMMENT ON COLUMN player.online_time_s IS 'Total time spent in the game, in seconds.';
COMMENT ON COLUMN player.delete_at IS 'Moment the character will be deleted (removed at the next character list after it); NULL when no deletion is pending.';
COMMENT ON COLUMN player.is_noble IS 'True when the character has noblesse status.';
COMMENT ON COLUMN player.is_vip IS 'VIP character (custom feature, Config CHAR_VIP_*). Set by operators only; the game reads it but never writes it.';
COMMENT ON COLUMN player.is_in_seven_signs_dungeon IS 'True when the character is inside a Seven Signs dungeon (catacomb or necropolis).';
COMMENT ON COLUMN player.is_in_jail IS 'True when the character is in jail.';
COMMENT ON COLUMN player.jail_remaining_ms IS 'Remaining jail time in milliseconds; meaningful only while is_in_jail. 0 while jailed means no time limit.';
COMMENT ON COLUMN player.newbie_reward_mask IS 'Bit mask of newbie rewards already received (bits set by the starter quests, for example 2, 4, 8, 16, 32); 1 for a new character.';
COMMENT ON COLUMN player.transformation_id IS 'Transformation kept over relog (datapack transformation scripts; no catalog table); NULL when not transformed.';
COMMENT ON COLUMN player.clan_id IS 'Clan the character belongs to; NULL when in no clan.';
COMMENT ON COLUMN player.pledge_type IS 'Clan sub-unit: 0 main clan, -1 academy, 100 and 200 royal guards, 1001, 1002, 2001, 2002 orders of knights.';
COMMENT ON COLUMN player.pledge_rank IS 'Rank inside the clan (1 leader .. 9); 0 when not set, the game then uses 5.';
COMMENT ON COLUMN player.wants_peace IS 'True when the character surrendered personally in the current clan war.';
COMMENT ON COLUMN player.academy_join_level IS 'Level at which the character joined the clan academy; 0 when not an academy member.';
COMMENT ON COLUMN player.apprentice_player_id IS 'Academy apprentice this character sponsors; NULL when none.';
COMMENT ON COLUMN player.sponsor_player_id IS 'Sponsor of this academy member; NULL when none.';
COMMENT ON COLUMN player.clan_join_allowed_at IS 'End of the penalty after leaving a clan: the character cannot join a clan before this moment; NULL when no penalty.';
COMMENT ON COLUMN player.clan_create_allowed_at IS 'End of the penalty after dissolving a clan: the character cannot create a clan before this moment; NULL when no penalty.';
COMMENT ON COLUMN player.varka_ketra_alliance IS 'Alliance side and level: -5..-1 Varka Silenos, 0 neutral, 1..5 Ketra Orcs.';
COMMENT ON COLUMN player.death_penalty_level IS 'Level of the death penalty debuff; 0 when none.';
COMMENT ON COLUMN player.vitality_points IS 'Vitality points (0..20000).';
COMMENT ON COLUMN player.bookmark_slots IS 'Number of teleport bookmark slots the character has unlocked.';
CREATE INDEX player_account_name_idx ON player (account_name);
CREATE INDEX player_clan_id_idx ON player (clan_id);
CREATE INDEX player_race_id_idx ON player (race_id);
CREATE INDEX player_active_class_id_idx ON player (active_class_id);
CREATE INDEX player_base_class_id_idx ON player (base_class_id);
CREATE INDEX player_apprentice_player_id_idx ON player (apprentice_player_id);
CREATE INDEX player_sponsor_player_id_idx ON player (sponsor_player_id);

CREATE TABLE player_subclass (
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	class_index smallint NOT NULL CHECK (class_index > 0),
	class_id smallint NOT NULL REFERENCES player_class,
	level smallint NOT NULL CHECK (level >= 1),
	exp bigint NOT NULL DEFAULT 0 CHECK (exp >= 0),
	sp integer NOT NULL DEFAULT 0 CHECK (sp >= 0),
	PRIMARY KEY (player_id, class_index),
	UNIQUE (player_id, class_id)
);
COMMENT ON TABLE player_subclass IS 'Subclasses of a player. The base class is not here; it is player.base_class_id with class_index 0.';
COMMENT ON COLUMN player_subclass.player_id IS 'Owner.';
COMMENT ON COLUMN player_subclass.class_index IS 'Subclass slot, 1 .. Config ALT_MAX_SUBCLASS.';
COMMENT ON COLUMN player_subclass.class_id IS 'Class of this subclass; a player has each class at most once.';
COMMENT ON COLUMN player_subclass.level IS 'Level of the subclass.';
COMMENT ON COLUMN player_subclass.exp IS 'Experience points of the subclass.';
COMMENT ON COLUMN player_subclass.sp IS 'Skill points of the subclass.';
CREATE INDEX player_subclass_class_id_idx ON player_subclass (class_id);

CREATE TABLE player_subclass_certification (
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	class_index smallint NOT NULL CHECK (class_index >= 0),
	certification_level smallint NOT NULL DEFAULT 0 CHECK (certification_level BETWEEN 0 AND 4),
	PRIMARY KEY (player_id, class_index)
);
COMMENT ON TABLE player_subclass_certification IS 'Subclass certifications received per subclass slot. Kept when the subclass in the slot is changed; cleared as a whole when the certification skills are reset.';
COMMENT ON COLUMN player_subclass_certification.player_id IS 'Owner.';
COMMENT ON COLUMN player_subclass_certification.class_index IS 'Subclass slot (see player_subclass.class_index).';
COMMENT ON COLUMN player_subclass_certification.certification_level IS 'Certifications received: 0 none, 1 at level 65, 2 at 70, 3 at 75, 4 at 80.';

CREATE TABLE player_skill (
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	class_index smallint NOT NULL CHECK (class_index >= 0),
	skill_id integer NOT NULL,
	skill_level smallint NOT NULL,
	PRIMARY KEY (player_id, class_index, skill_id)
);
COMMENT ON TABLE player_skill IS 'Skills a player has learned, per class slot.';
COMMENT ON COLUMN player_skill.player_id IS 'Owner.';
COMMENT ON COLUMN player_skill.class_index IS 'Class slot: 0 base class, 1..n subclass (player_subclass.class_index).';
COMMENT ON COLUMN player_skill.skill_id IS 'Skill (catalog skill data, data/stats/skills; no foreign key).';
COMMENT ON COLUMN player_skill.skill_level IS 'Skill level; enchanted skills use the client levels above 100.';

CREATE TABLE player_skill_reuse (
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	skill_id integer NOT NULL,
	reuse_delay_ms integer NOT NULL CHECK (reuse_delay_ms >= 0),
	expires_at timestamptz NOT NULL,
	PRIMARY KEY (player_id, skill_id)
);
COMMENT ON TABLE player_skill_reuse IS 'Skill cooldowns that were still running at logout (only those longer than 10 seconds are stored).';
COMMENT ON COLUMN player_skill_reuse.player_id IS 'Owner.';
COMMENT ON COLUMN player_skill_reuse.skill_id IS 'Skill (catalog skill data, data/stats/skills; no foreign key).';
COMMENT ON COLUMN player_skill_reuse.reuse_delay_ms IS 'Full reuse delay of the skill in milliseconds, sent to the client.';
COMMENT ON COLUMN player_skill_reuse.expires_at IS 'Moment the skill becomes usable again.';

CREATE TABLE player_effect (
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	class_index smallint NOT NULL CHECK (class_index >= 0),
	effect_order smallint NOT NULL CHECK (effect_order >= 1),
	skill_id integer NOT NULL,
	skill_level smallint NOT NULL,
	remaining_count integer NOT NULL CHECK (remaining_count >= 0),
	remaining_s integer NOT NULL,
	PRIMARY KEY (player_id, class_index, effect_order)
);
COMMENT ON TABLE player_effect IS 'Buffs and other effects active at logout, restored at login (Config STORE_EFFECTS).';
COMMENT ON COLUMN player_effect.player_id IS 'Owner.';
COMMENT ON COLUMN player_effect.class_index IS 'Class slot the effect was saved for: 0 base class, 1..n subclass.';
COMMENT ON COLUMN player_effect.effect_order IS 'Position in the effect list, 1 first; effects are restored in this order.';
COMMENT ON COLUMN player_effect.skill_id IS 'Skill that gives the effect (catalog skill data, data/stats/skills; no foreign key).';
COMMENT ON COLUMN player_effect.skill_level IS 'Level of that skill.';
COMMENT ON COLUMN player_effect.remaining_count IS 'Remaining number of effect periods (ticks).';
COMMENT ON COLUMN player_effect.remaining_s IS 'Remaining time of the current period in seconds.';

CREATE TABLE player_henna (
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	class_index smallint NOT NULL CHECK (class_index >= 0),
	slot smallint NOT NULL CHECK (slot BETWEEN 1 AND 3),
	henna_id smallint NOT NULL,
	PRIMARY KEY (player_id, class_index, slot)
);
COMMENT ON TABLE player_henna IS 'Hennas (dyes) drawn on a player, up to three per class slot.';
COMMENT ON COLUMN player_henna.player_id IS 'Owner.';
COMMENT ON COLUMN player_henna.class_index IS 'Class slot: 0 base class, 1..n subclass.';
COMMENT ON COLUMN player_henna.slot IS 'Henna slot, 1..3.';
COMMENT ON COLUMN player_henna.henna_id IS 'Henna symbol (catalog table henna, by symbol id).';

CREATE TABLE shortcut_type (
	id smallint PRIMARY KEY,
	name text NOT NULL UNIQUE
);
COMMENT ON TABLE shortcut_type IS 'Kinds of shortcut bar entries; ids are the client protocol values (Java constants L2ShortCut.TYPE_*).';
COMMENT ON COLUMN shortcut_type.name IS 'Name as in the Java constant without the TYPE_ prefix.';

INSERT INTO shortcut_type (id, name) VALUES
	(1, 'ITEM'),
	(2, 'SKILL'),
	(3, 'ACTION'),
	(4, 'MACRO'),
	(5, 'RECIPE'),
	(6, 'TPBOOKMARK');

CREATE TABLE player_shortcut (
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	class_index smallint NOT NULL CHECK (class_index >= 0),
	page smallint NOT NULL CHECK (page >= 0),
	slot smallint NOT NULL CHECK (slot >= 0),
	shortcut_type_id smallint NOT NULL REFERENCES shortcut_type,
	target_id integer NOT NULL,
	level smallint NOT NULL,
	PRIMARY KEY (player_id, class_index, page, slot)
);
COMMENT ON TABLE player_shortcut IS 'Shortcut bar entries of a player, per class slot.';
COMMENT ON COLUMN player_shortcut.player_id IS 'Owner.';
COMMENT ON COLUMN player_shortcut.class_index IS 'Class slot: 0 base class, 1..n subclass.';
COMMENT ON COLUMN player_shortcut.page IS 'Shortcut bar page, from 0.';
COMMENT ON COLUMN player_shortcut.slot IS 'Position on the page, 0..11.';
COMMENT ON COLUMN player_shortcut.shortcut_type_id IS 'What the shortcut points to; decides the meaning of target_id.';
COMMENT ON COLUMN player_shortcut.target_id IS 'Target, depending on the type: item object id (item.id), skill id (catalog skill data), action id, macro (player_macro.macro_number), recipe id, bookmark (player_teleport_bookmark.bookmark_number). No foreign key because the target table varies.';
COMMENT ON COLUMN player_shortcut.level IS 'Skill level for skill shortcuts; not used by the other types.';
CREATE INDEX player_shortcut_shortcut_type_id_idx ON player_shortcut (shortcut_type_id);

CREATE TABLE player_macro (
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	macro_number integer NOT NULL,
	icon integer NOT NULL DEFAULT 0,
	name varchar(40) NOT NULL DEFAULT '',
	description varchar(80) NOT NULL DEFAULT '',
	acronym varchar(4) NOT NULL DEFAULT '',
	commands varchar(255) NOT NULL DEFAULT '',
	PRIMARY KEY (player_id, macro_number)
);
COMMENT ON TABLE player_macro IS 'Chat and action macros a player defined.';
COMMENT ON COLUMN player_macro.player_id IS 'Owner.';
COMMENT ON COLUMN player_macro.macro_number IS 'Macro number within the player, assigned by the game from 1000 upward and used by macro shortcuts.';
COMMENT ON COLUMN player_macro.icon IS 'Icon number shown by the client.';
COMMENT ON COLUMN player_macro.name IS 'Macro name.';
COMMENT ON COLUMN player_macro.description IS 'Description entered by the player.';
COMMENT ON COLUMN player_macro.acronym IS 'Short label shown on the icon.';
COMMENT ON COLUMN player_macro.commands IS 'Commands in the order they run, serialized by MacroList as type,d1,d2[,text]; per command.';

CREATE TABLE player_teleport_bookmark (
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	bookmark_number integer NOT NULL,
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	icon integer NOT NULL,
	tag varchar(20) NOT NULL DEFAULT '',
	name varchar(20) NOT NULL,
	PRIMARY KEY (player_id, bookmark_number)
);
COMMENT ON TABLE player_teleport_bookmark IS 'Saved teleport locations (My Teleports).';
COMMENT ON COLUMN player_teleport_bookmark.player_id IS 'Owner.';
COMMENT ON COLUMN player_teleport_bookmark.bookmark_number IS 'Bookmark number within the player, used by bookmark shortcuts.';
COMMENT ON COLUMN player_teleport_bookmark.x IS 'World X coordinate.';
COMMENT ON COLUMN player_teleport_bookmark.y IS 'World Y coordinate.';
COMMENT ON COLUMN player_teleport_bookmark.z IS 'World Z coordinate.';
COMMENT ON COLUMN player_teleport_bookmark.icon IS 'Icon number shown by the client.';
COMMENT ON COLUMN player_teleport_bookmark.tag IS 'Short tag entered by the player; empty when none.';
COMMENT ON COLUMN player_teleport_bookmark.name IS 'Bookmark name.';

CREATE TABLE player_recipe (
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	class_index smallint NOT NULL CHECK (class_index >= 0),
	recipe_id integer NOT NULL,
	is_dwarven boolean NOT NULL,
	PRIMARY KEY (player_id, class_index, recipe_id),
	CHECK (is_dwarven OR class_index = 0)
);
COMMENT ON TABLE player_recipe IS 'Recipes in a player''s recipe books. Common recipes are shared by all classes (class_index 0); dwarven recipes are kept per class slot.';
COMMENT ON COLUMN player_recipe.player_id IS 'Owner.';
COMMENT ON COLUMN player_recipe.class_index IS 'Class slot for dwarven recipes; always 0 for common recipes.';
COMMENT ON COLUMN player_recipe.recipe_id IS 'Recipe list (datapack data/recipes.xml, RecipeTable; no catalog table).';
COMMENT ON COLUMN player_recipe.is_dwarven IS 'True for the dwarven recipe book, false for the common one.';

CREATE TABLE player_quest_variable (
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	quest_name text NOT NULL,
	variable_name varchar(20) NOT NULL,
	value varchar(255) NOT NULL DEFAULT '',
	PRIMARY KEY (player_id, quest_name, variable_name)
);
COMMENT ON TABLE player_quest_variable IS 'Quest progress: one row per variable of a quest the player has started. The variable <state> holds the quest state name.';
COMMENT ON COLUMN player_quest_variable.player_id IS 'Owner.';
COMMENT ON COLUMN player_quest_variable.quest_name IS 'Quest script name, for example 255_Tutorial.';
COMMENT ON COLUMN player_quest_variable.variable_name IS 'Variable name; <state> is the quest state, cond is the step.';
COMMENT ON COLUMN player_quest_variable.value IS 'Variable value as text.';

CREATE TABLE player_quest_global_variable (
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	variable_name varchar(20) NOT NULL,
	value varchar(255) NOT NULL DEFAULT '',
	PRIMARY KEY (player_id, variable_name)
);
COMMENT ON TABLE player_quest_global_variable IS 'Per-player variables shared by all quests, for example one-time rewards already given.';
COMMENT ON COLUMN player_quest_global_variable.player_id IS 'Owner.';
COMMENT ON COLUMN player_quest_global_variable.variable_name IS 'Variable name.';
COMMENT ON COLUMN player_quest_global_variable.value IS 'Variable value as text.';

CREATE TABLE player_instance_reentry (
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	instance_template_id integer NOT NULL,
	reenter_at timestamptz NOT NULL,
	PRIMARY KEY (player_id, instance_template_id)
);
COMMENT ON TABLE player_instance_reentry IS 'Instance zones a player may not enter again yet.';
COMMENT ON COLUMN player_instance_reentry.player_id IS 'Owner.';
COMMENT ON COLUMN player_instance_reentry.instance_template_id IS 'Instance zone type (datapack data/instancenames.xml; no catalog table).';
COMMENT ON COLUMN player_instance_reentry.reenter_at IS 'Moment the player may enter this instance again; the row is removed at login after it.';

CREATE TABLE player_raid_score (
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	boss_npc_template_id integer NOT NULL,
	points integer NOT NULL DEFAULT 0 CHECK (points >= 0),
	PRIMARY KEY (player_id, boss_npc_template_id)
);
COMMENT ON TABLE player_raid_score IS 'Raid points a player earned per raid boss; cleared when the raid ranking is reset.';
COMMENT ON COLUMN player_raid_score.player_id IS 'Owner.';
COMMENT ON COLUMN player_raid_score.boss_npc_template_id IS 'Raid boss (catalog table npc_template).';
COMMENT ON COLUMN player_raid_score.points IS 'Raid points earned on this boss.';

CREATE TABLE player_birthday (
	player_id integer PRIMARY KEY REFERENCES player ON DELETE CASCADE,
	created_on date NOT NULL,
	gift_claimed_year smallint NOT NULL
);
COMMENT ON TABLE player_birthday IS 'Character creation date, used for the yearly birthday gift.';
COMMENT ON COLUMN player_birthday.player_id IS 'Owner.';
COMMENT ON COLUMN player_birthday.created_on IS 'Day the character was created.';
COMMENT ON COLUMN player_birthday.gift_claimed_year IS 'Last calendar year the birthday gift was claimed; the creation year for a new character.';

CREATE TABLE player_name_title_color (
	player_id integer PRIMARY KEY REFERENCES player ON DELETE CASCADE,
	name_color varchar(6) NOT NULL CHECK (name_color ~ '^[0-9A-Fa-f]{6}$'),
	title_color varchar(6) NOT NULL CHECK (title_color ~ '^[0-9A-Fa-f]{6}$')
);
COMMENT ON TABLE player_name_title_color IS 'Custom name and title colors; a player without a row uses the default colors.';
COMMENT ON COLUMN player_name_title_color.player_id IS 'Owner.';
COMMENT ON COLUMN player_name_title_color.name_color IS 'Name color as six hex digits in RRGGBB order.';
COMMENT ON COLUMN player_name_title_color.title_color IS 'Title color as six hex digits in RRGGBB order.';

CREATE TABLE player_recommendation_status (
	player_id integer PRIMARY KEY REFERENCES player ON DELETE CASCADE,
	recommendations_left smallint NOT NULL DEFAULT 3 CHECK (recommendations_left >= 0),
	recommendations_received smallint NOT NULL DEFAULT 0 CHECK (recommendations_received >= 0),
	updated_at timestamptz NOT NULL
);
COMMENT ON TABLE player_recommendation_status IS 'Recommendation counters of a player. They are refreshed at the daily recommendation reset (13:00).';
COMMENT ON COLUMN player_recommendation_status.player_id IS 'Owner.';
COMMENT ON COLUMN player_recommendation_status.recommendations_left IS 'Recommendations the player can still give until the next daily reset.';
COMMENT ON COLUMN player_recommendation_status.recommendations_received IS 'Recommendation points received (shown to other players); decrease at each daily reset.';
COMMENT ON COLUMN player_recommendation_status.updated_at IS 'Daily reset the counters were last brought up to date for; at login the missed resets since then are applied.';

CREATE TABLE player_recommendation (
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	recommended_player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	PRIMARY KEY (player_id, recommended_player_id),
	CHECK (player_id <> recommended_player_id)
);
COMMENT ON TABLE player_recommendation IS 'Players recommended since the last daily reset (only with Config ALT_RECOMMEND); the table is emptied at each reset.';
COMMENT ON COLUMN player_recommendation.player_id IS 'Player who gave the recommendation.';
COMMENT ON COLUMN player_recommendation.recommended_player_id IS 'Player who received it.';
CREATE INDEX player_recommendation_recommended_player_id_idx ON player_recommendation (recommended_player_id);

CREATE TABLE player_friendship (
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	friend_player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	PRIMARY KEY (player_id, friend_player_id),
	CHECK (player_id < friend_player_id)
);
COMMENT ON TABLE player_friendship IS 'Friendships. A friendship is mutual and stored once, with the lower player id in player_id.';
COMMENT ON COLUMN player_friendship.player_id IS 'Friend with the lower id.';
COMMENT ON COLUMN player_friendship.friend_player_id IS 'Friend with the higher id.';
CREATE INDEX player_friendship_friend_player_id_idx ON player_friendship (friend_player_id);

CREATE TABLE player_block (
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	blocked_player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	PRIMARY KEY (player_id, blocked_player_id)
);
COMMENT ON TABLE player_block IS 'Block list: players whose messages a player does not receive.';
COMMENT ON COLUMN player_block.player_id IS 'Owner of the block list.';
COMMENT ON COLUMN player_block.blocked_player_id IS 'Blocked player.';
CREATE INDEX player_block_blocked_player_id_idx ON player_block (blocked_player_id);
