-- Catalog: NPCs and where they appear.
-- NPC templates with their drops, skills and minions; world spawns and the
-- special spawn lists of instances and bosses; siege guards of castles, forts
-- and clan halls; castle and fort doors; teleport destinations; walker routes;
-- NPC auto chat.
--
-- The catalog is rebuilt from the image: the loader drops and recreates the
-- schema in one transaction and fills every table from CSV with COPY. The
-- tables are UNLOGGED and hold no seed rows. Admin commands (AdminEditNpc,
-- AdminSpawn, SiegeGuardManager.addAnyGuard) write npc_template, drop,
-- npc_skill, spawn, raid_boss_spawn, grand_boss_spawn and castle_siege_guard;
-- such edits last until the next catalog load. A permanent change goes into
-- the datapack CSV.
--
-- Foreign keys to npc_template exist only on tables that the game never writes
-- and whose shipped data satisfies them. Tables written by admin commands
-- reference NPCs without a foreign key, because an admin may legitimately use
-- a custom NPC (custom_npc_template), which npc_template does not contain.
-- The catalog is created after the world schema is migrated, so castle, fort
-- and clan hall ids have foreign keys to world.castle, world.fort and
-- world.clan_hall (world never references the catalog).

-- NPC templates ---------------------------------------------------------------

CREATE UNLOGGED TABLE npc_template (
	id integer PRIMARY KEY,
	client_template_id integer NOT NULL REFERENCES npc_template,
	name text NOT NULL DEFAULT '',
	sends_server_name boolean NOT NULL DEFAULT false,
	title text NOT NULL DEFAULT '',
	sends_server_title boolean NOT NULL DEFAULT false,
	client_class text NOT NULL,
	collision_radius numeric(5,2) NOT NULL CHECK (collision_radius >= 0),
	collision_height numeric(5,2) NOT NULL CHECK (collision_height >= 0),
	level smallint NOT NULL CHECK (level >= 0),
	sex text NOT NULL DEFAULT 'male' CHECK (sex IN ('male', 'female', 'etc')),
	instance_type text NOT NULL,
	attack_range integer NOT NULL DEFAULT 0 CHECK (attack_range >= 0),
	max_hp integer NOT NULL CHECK (max_hp >= 0),
	max_mp integer NOT NULL CHECK (max_mp >= 0),
	hp_regen numeric(8,2),
	mp_regen numeric(5,2),
	strength smallint NOT NULL,
	constitution smallint NOT NULL,
	dexterity smallint NOT NULL,
	intelligence smallint NOT NULL,
	wit smallint NOT NULL,
	mental smallint NOT NULL,
	reward_exp integer NOT NULL DEFAULT 0 CHECK (reward_exp >= 0),
	reward_sp integer NOT NULL DEFAULT 0 CHECK (reward_sp >= 0),
	p_atk integer NOT NULL DEFAULT 0 CHECK (p_atk >= 0),
	p_def integer NOT NULL DEFAULT 0 CHECK (p_def >= 0),
	m_atk integer NOT NULL DEFAULT 0 CHECK (m_atk >= 0),
	m_def integer NOT NULL DEFAULT 0 CHECK (m_def >= 0),
	p_atk_speed integer NOT NULL DEFAULT 0 CHECK (p_atk_speed >= 0),
	aggro_range integer NOT NULL DEFAULT 0 CHECK (aggro_range >= 0),
	m_atk_speed integer NOT NULL DEFAULT 0 CHECK (m_atk_speed >= 0),
	right_hand_item_template_id integer,
	left_hand_item_template_id integer,
	armor_item_template_id integer,
	walk_speed integer NOT NULL DEFAULT 0 CHECK (walk_speed >= 0),
	run_speed integer NOT NULL DEFAULT 0 CHECK (run_speed >= 0),
	faction text,
	faction_range integer NOT NULL DEFAULT 0 CHECK (faction_range >= 0),
	is_undead boolean NOT NULL DEFAULT false,
	absorb_level smallint NOT NULL DEFAULT 0 CHECK (absorb_level >= 0),
	absorb_type text NOT NULL DEFAULT 'LAST_HIT' CHECK (absorb_type IN ('LAST_HIT', 'FULL_PARTY', 'PARTY_ONE_RANDOM')),
	soulshot_count smallint NOT NULL DEFAULT 0 CHECK (soulshot_count >= 0),
	blessed_spiritshot_count smallint NOT NULL DEFAULT 0 CHECK (blessed_spiritshot_count >= 0),
	shot_chance smallint NOT NULL DEFAULT 0 CHECK (shot_chance >= 0),
	ai_type text NOT NULL DEFAULT 'FIGHTER' CHECK (ai_type IN ('FIGHTER', 'ARCHER', 'BALANCED', 'MAGE', 'HEALER', 'CORPSE')),
	drops_herbs boolean NOT NULL DEFAULT false
);
COMMENT ON TABLE npc_template IS 'NPC templates: stats, looks and behavior of every NPC and monster. Admin command edits (AdminEditNpc) last until the next catalog load.';
COMMENT ON COLUMN npc_template.client_template_id IS 'NPC template whose id the client receives, which decides the model and the client-side name (catalog.npc_template.id). Equals id for ordinary NPCs.';
COMMENT ON COLUMN npc_template.name IS 'NPC name; empty when the client shows its own name.';
COMMENT ON COLUMN npc_template.sends_server_name IS 'True when the server sends name to the client instead of the client using its own name.';
COMMENT ON COLUMN npc_template.title IS 'Title shown above the name; empty for none. The title Quest Monster marks quest monsters.';
COMMENT ON COLUMN npc_template.sends_server_title IS 'True when the server sends title to the client instead of the client using its own title.';
COMMENT ON COLUMN npc_template.client_class IS 'Client class of the NPC model, for example Monster.baium. Village masters derive the race they serve from it.';
COMMENT ON COLUMN npc_template.collision_radius IS 'Collision radius in game units.';
COMMENT ON COLUMN npc_template.collision_height IS 'Collision height in game units.';
COMMENT ON COLUMN npc_template.level IS 'NPC level.';
COMMENT ON COLUMN npc_template.sex IS 'Sex of the model: male, female or etc (neither).';
COMMENT ON COLUMN npc_template.instance_type IS 'Java instance class without the Instance suffix, for example L2Monster for L2MonsterInstance. Decides the NPC behavior.';
COMMENT ON COLUMN npc_template.attack_range IS 'Base physical attack range in game units.';
COMMENT ON COLUMN npc_template.max_hp IS 'Base maximum HP.';
COMMENT ON COLUMN npc_template.max_mp IS 'Base maximum MP.';
COMMENT ON COLUMN npc_template.hp_regen IS 'Base HP regeneration per tick; NULL means the server derives it from the level.';
COMMENT ON COLUMN npc_template.mp_regen IS 'Base MP regeneration per tick; NULL means the server derives it from the level.';
COMMENT ON COLUMN npc_template.strength IS 'Base STR; the loader clamps it to the valid stat range.';
COMMENT ON COLUMN npc_template.constitution IS 'Base CON; the loader clamps it to the valid stat range.';
COMMENT ON COLUMN npc_template.dexterity IS 'Base DEX; the loader clamps it to the valid stat range.';
COMMENT ON COLUMN npc_template.intelligence IS 'Base INT; the loader clamps it to the valid stat range.';
COMMENT ON COLUMN npc_template.wit IS 'Base WIT; the loader clamps it to the valid stat range.';
COMMENT ON COLUMN npc_template.mental IS 'Base MEN (mental); the loader clamps it to the valid stat range.';
COMMENT ON COLUMN npc_template.reward_exp IS 'Experience given for a kill, before server rates.';
COMMENT ON COLUMN npc_template.reward_sp IS 'SP given for a kill, before server rates.';
COMMENT ON COLUMN npc_template.p_atk IS 'Base physical attack.';
COMMENT ON COLUMN npc_template.p_def IS 'Base physical defense.';
COMMENT ON COLUMN npc_template.m_atk IS 'Base magic attack.';
COMMENT ON COLUMN npc_template.m_def IS 'Base magic defense.';
COMMENT ON COLUMN npc_template.p_atk_speed IS 'Base physical attack speed.';
COMMENT ON COLUMN npc_template.aggro_range IS 'Range in game units in which the NPC attacks players on sight; 0 means it is not aggressive.';
COMMENT ON COLUMN npc_template.m_atk_speed IS 'Base casting speed.';
COMMENT ON COLUMN npc_template.right_hand_item_template_id IS 'Item shown in the right hand (catalog tables weapon_template, armor_template, etc_item_template); NULL for none.';
COMMENT ON COLUMN npc_template.left_hand_item_template_id IS 'Item shown in the left hand (catalog tables weapon_template, armor_template, etc_item_template); NULL for none.';
COMMENT ON COLUMN npc_template.armor_item_template_id IS 'Armor item worn by the model (catalog tables weapon_template, armor_template, etc_item_template); NULL for none. Only displayed by admin commands.';
COMMENT ON COLUMN npc_template.walk_speed IS 'Base walk speed in game units per second.';
COMMENT ON COLUMN npc_template.run_speed IS 'Base run speed in game units per second.';
COMMENT ON COLUMN npc_template.faction IS 'Faction code, for example zaken_clan; NPCs of the same faction help each other. NULL means no faction.';
COMMENT ON COLUMN npc_template.faction_range IS 'Range in game units in which faction members come to help.';
COMMENT ON COLUMN npc_template.is_undead IS 'True when the NPC counts as undead for skills and effects.';
COMMENT ON COLUMN npc_template.absorb_level IS 'Highest soul crystal level that can absorb this NPC''s soul; 0 means soul crystals cannot absorb it.';
COMMENT ON COLUMN npc_template.absorb_type IS 'Who gets the soul crystal level-up (Java enum L2NpcTemplate.AbsorbCrystalType).';
COMMENT ON COLUMN npc_template.soulshot_count IS 'Soulshots the NPC carries; 0 for none.';
COMMENT ON COLUMN npc_template.blessed_spiritshot_count IS 'Blessed spiritshots the NPC carries; 0 for none.';
COMMENT ON COLUMN npc_template.shot_chance IS 'Chance in percent that the NPC uses a shot on an attack or cast; 0 means never.';
COMMENT ON COLUMN npc_template.ai_type IS 'Combat AI (Java enum L2NpcTemplate.AIType).';
COMMENT ON COLUMN npc_template.drops_herbs IS 'True when the NPC can drop herbs.';
CREATE INDEX npc_template_client_template_id_idx ON npc_template (client_template_id);

CREATE UNLOGGED TABLE custom_npc_template (
	id integer PRIMARY KEY,
	client_template_id integer NOT NULL REFERENCES npc_template,
	name text NOT NULL DEFAULT '',
	sends_server_name boolean NOT NULL DEFAULT false,
	title text NOT NULL DEFAULT '',
	sends_server_title boolean NOT NULL DEFAULT false,
	client_class text NOT NULL,
	collision_radius numeric(5,2) NOT NULL CHECK (collision_radius >= 0),
	collision_height numeric(5,2) NOT NULL CHECK (collision_height >= 0),
	level smallint NOT NULL CHECK (level >= 0),
	sex text NOT NULL DEFAULT 'male' CHECK (sex IN ('male', 'female', 'etc')),
	instance_type text NOT NULL,
	attack_range integer NOT NULL DEFAULT 0 CHECK (attack_range >= 0),
	max_hp integer NOT NULL CHECK (max_hp >= 0),
	max_mp integer NOT NULL CHECK (max_mp >= 0),
	hp_regen numeric(8,2),
	mp_regen numeric(5,2),
	strength smallint NOT NULL,
	constitution smallint NOT NULL,
	dexterity smallint NOT NULL,
	intelligence smallint NOT NULL,
	wit smallint NOT NULL,
	mental smallint NOT NULL,
	reward_exp integer NOT NULL DEFAULT 0 CHECK (reward_exp >= 0),
	reward_sp integer NOT NULL DEFAULT 0 CHECK (reward_sp >= 0),
	p_atk integer NOT NULL DEFAULT 0 CHECK (p_atk >= 0),
	p_def integer NOT NULL DEFAULT 0 CHECK (p_def >= 0),
	m_atk integer NOT NULL DEFAULT 0 CHECK (m_atk >= 0),
	m_def integer NOT NULL DEFAULT 0 CHECK (m_def >= 0),
	p_atk_speed integer NOT NULL DEFAULT 0 CHECK (p_atk_speed >= 0),
	aggro_range integer NOT NULL DEFAULT 0 CHECK (aggro_range >= 0),
	m_atk_speed integer NOT NULL DEFAULT 0 CHECK (m_atk_speed >= 0),
	right_hand_item_template_id integer,
	left_hand_item_template_id integer,
	armor_item_template_id integer,
	walk_speed integer NOT NULL DEFAULT 0 CHECK (walk_speed >= 0),
	run_speed integer NOT NULL DEFAULT 0 CHECK (run_speed >= 0),
	faction text,
	faction_range integer NOT NULL DEFAULT 0 CHECK (faction_range >= 0),
	is_undead boolean NOT NULL DEFAULT false,
	absorb_level smallint NOT NULL DEFAULT 0 CHECK (absorb_level >= 0),
	absorb_type text NOT NULL DEFAULT 'LAST_HIT' CHECK (absorb_type IN ('LAST_HIT', 'FULL_PARTY', 'PARTY_ONE_RANDOM')),
	soulshot_count smallint NOT NULL DEFAULT 0 CHECK (soulshot_count >= 0),
	blessed_spiritshot_count smallint NOT NULL DEFAULT 0 CHECK (blessed_spiritshot_count >= 0),
	shot_chance smallint NOT NULL DEFAULT 0 CHECK (shot_chance >= 0),
	ai_type text NOT NULL DEFAULT 'FIGHTER' CHECK (ai_type IN ('FIGHTER', 'ARCHER', 'BALANCED', 'MAGE', 'HEALER', 'CORPSE')),
	drops_herbs boolean NOT NULL DEFAULT false
);
COMMENT ON TABLE custom_npc_template IS 'Server-specific NPC templates, same columns as npc_template; loaded after it. Ids must not collide with npc_template. Admin command edits last until the next catalog load.';
COMMENT ON COLUMN custom_npc_template.client_template_id IS 'NPC template whose id the client receives, which decides the model and the client-side name (catalog.npc_template.id). Equals id for ordinary NPCs.';
COMMENT ON COLUMN custom_npc_template.name IS 'NPC name; empty when the client shows its own name.';
COMMENT ON COLUMN custom_npc_template.sends_server_name IS 'True when the server sends name to the client instead of the client using its own name.';
COMMENT ON COLUMN custom_npc_template.title IS 'Title shown above the name; empty for none. The title Quest Monster marks quest monsters.';
COMMENT ON COLUMN custom_npc_template.sends_server_title IS 'True when the server sends title to the client instead of the client using its own title.';
COMMENT ON COLUMN custom_npc_template.client_class IS 'Client class of the NPC model, for example Monster.baium. Village masters derive the race they serve from it.';
COMMENT ON COLUMN custom_npc_template.collision_radius IS 'Collision radius in game units.';
COMMENT ON COLUMN custom_npc_template.collision_height IS 'Collision height in game units.';
COMMENT ON COLUMN custom_npc_template.level IS 'NPC level.';
COMMENT ON COLUMN custom_npc_template.sex IS 'Sex of the model: male, female or etc (neither).';
COMMENT ON COLUMN custom_npc_template.instance_type IS 'Java instance class without the Instance suffix, for example L2Monster for L2MonsterInstance. Decides the NPC behavior.';
COMMENT ON COLUMN custom_npc_template.attack_range IS 'Base physical attack range in game units.';
COMMENT ON COLUMN custom_npc_template.max_hp IS 'Base maximum HP.';
COMMENT ON COLUMN custom_npc_template.max_mp IS 'Base maximum MP.';
COMMENT ON COLUMN custom_npc_template.hp_regen IS 'Base HP regeneration per tick; NULL means the server derives it from the level.';
COMMENT ON COLUMN custom_npc_template.mp_regen IS 'Base MP regeneration per tick; NULL means the server derives it from the level.';
COMMENT ON COLUMN custom_npc_template.strength IS 'Base STR; the loader clamps it to the valid stat range.';
COMMENT ON COLUMN custom_npc_template.constitution IS 'Base CON; the loader clamps it to the valid stat range.';
COMMENT ON COLUMN custom_npc_template.dexterity IS 'Base DEX; the loader clamps it to the valid stat range.';
COMMENT ON COLUMN custom_npc_template.intelligence IS 'Base INT; the loader clamps it to the valid stat range.';
COMMENT ON COLUMN custom_npc_template.wit IS 'Base WIT; the loader clamps it to the valid stat range.';
COMMENT ON COLUMN custom_npc_template.mental IS 'Base MEN (mental); the loader clamps it to the valid stat range.';
COMMENT ON COLUMN custom_npc_template.reward_exp IS 'Experience given for a kill, before server rates.';
COMMENT ON COLUMN custom_npc_template.reward_sp IS 'SP given for a kill, before server rates.';
COMMENT ON COLUMN custom_npc_template.p_atk IS 'Base physical attack.';
COMMENT ON COLUMN custom_npc_template.p_def IS 'Base physical defense.';
COMMENT ON COLUMN custom_npc_template.m_atk IS 'Base magic attack.';
COMMENT ON COLUMN custom_npc_template.m_def IS 'Base magic defense.';
COMMENT ON COLUMN custom_npc_template.p_atk_speed IS 'Base physical attack speed.';
COMMENT ON COLUMN custom_npc_template.aggro_range IS 'Range in game units in which the NPC attacks players on sight; 0 means it is not aggressive.';
COMMENT ON COLUMN custom_npc_template.m_atk_speed IS 'Base casting speed.';
COMMENT ON COLUMN custom_npc_template.right_hand_item_template_id IS 'Item shown in the right hand (catalog tables weapon_template, armor_template, etc_item_template); NULL for none.';
COMMENT ON COLUMN custom_npc_template.left_hand_item_template_id IS 'Item shown in the left hand (catalog tables weapon_template, armor_template, etc_item_template); NULL for none.';
COMMENT ON COLUMN custom_npc_template.armor_item_template_id IS 'Armor item worn by the model (catalog tables weapon_template, armor_template, etc_item_template); NULL for none. Only displayed by admin commands.';
COMMENT ON COLUMN custom_npc_template.walk_speed IS 'Base walk speed in game units per second.';
COMMENT ON COLUMN custom_npc_template.run_speed IS 'Base run speed in game units per second.';
COMMENT ON COLUMN custom_npc_template.faction IS 'Faction code, for example zaken_clan; NPCs of the same faction help each other. NULL means no faction.';
COMMENT ON COLUMN custom_npc_template.faction_range IS 'Range in game units in which faction members come to help.';
COMMENT ON COLUMN custom_npc_template.is_undead IS 'True when the NPC counts as undead for skills and effects.';
COMMENT ON COLUMN custom_npc_template.absorb_level IS 'Highest soul crystal level that can absorb this NPC''s soul; 0 means soul crystals cannot absorb it.';
COMMENT ON COLUMN custom_npc_template.absorb_type IS 'Who gets the soul crystal level-up (Java enum L2NpcTemplate.AbsorbCrystalType).';
COMMENT ON COLUMN custom_npc_template.soulshot_count IS 'Soulshots the NPC carries; 0 for none.';
COMMENT ON COLUMN custom_npc_template.blessed_spiritshot_count IS 'Blessed spiritshots the NPC carries; 0 for none.';
COMMENT ON COLUMN custom_npc_template.shot_chance IS 'Chance in percent that the NPC uses a shot on an attack or cast; 0 means never.';
COMMENT ON COLUMN custom_npc_template.ai_type IS 'Combat AI (Java enum L2NpcTemplate.AIType).';
COMMENT ON COLUMN custom_npc_template.drops_herbs IS 'True when the NPC can drop herbs.';
CREATE INDEX custom_npc_template_client_template_id_idx ON custom_npc_template (client_template_id);

-- Drops -----------------------------------------------------------------------

CREATE UNLOGGED TABLE drop (
	npc_template_id integer NOT NULL,
	item_template_id integer NOT NULL,
	category smallint NOT NULL CHECK (category >= -1),
	min_count integer NOT NULL CHECK (min_count >= 0),
	max_count integer NOT NULL,
	chance integer NOT NULL CHECK (chance >= 0),
	PRIMARY KEY (npc_template_id, item_template_id, category),
	CHECK (max_count >= min_count)
);
COMMENT ON TABLE drop IS 'Items an NPC drops or yields to spoil, grouped in categories. Written by AdminEditNpc; edits last until the next catalog load.';
COMMENT ON COLUMN drop.npc_template_id IS 'NPC that drops the item (catalog.npc_template.id or catalog.custom_npc_template.id); no foreign key because admin commands may add drops for custom NPCs.';
COMMENT ON COLUMN drop.item_template_id IS 'Dropped item (catalog tables weapon_template, armor_template, etc_item_template).';
COMMENT ON COLUMN drop.category IS 'Drop category; at most one item per category drops per kill. -1 is the spoil (sweep) category.';
COMMENT ON COLUMN drop.min_count IS 'Minimum number of items dropped.';
COMMENT ON COLUMN drop.max_count IS 'Maximum number of items dropped.';
COMMENT ON COLUMN drop.chance IS 'Drop chance in millionths (1000000 = 100 %), before server rates.';

CREATE UNLOGGED TABLE custom_drop (
	npc_template_id integer NOT NULL,
	item_template_id integer NOT NULL,
	category smallint NOT NULL CHECK (category >= -1),
	min_count integer NOT NULL CHECK (min_count >= 0),
	max_count integer NOT NULL,
	chance integer NOT NULL CHECK (chance >= 0),
	PRIMARY KEY (npc_template_id, item_template_id, category),
	CHECK (max_count >= min_count)
);
COMMENT ON TABLE custom_drop IS 'Server-specific drops, same columns as drop; loaded after it and added to the same NPC templates.';
COMMENT ON COLUMN custom_drop.npc_template_id IS 'NPC that drops the item (catalog.npc_template.id or catalog.custom_npc_template.id).';
COMMENT ON COLUMN custom_drop.item_template_id IS 'Dropped item (catalog tables weapon_template, armor_template, etc_item_template).';
COMMENT ON COLUMN custom_drop.category IS 'Drop category; at most one item per category drops per kill. -1 is the spoil (sweep) category.';
COMMENT ON COLUMN custom_drop.min_count IS 'Minimum number of items dropped.';
COMMENT ON COLUMN custom_drop.max_count IS 'Maximum number of items dropped.';
COMMENT ON COLUMN custom_drop.chance IS 'Drop chance in millionths (1000000 = 100 %), before server rates.';

-- NPC skills and minions ------------------------------------------------------

CREATE UNLOGGED TABLE npc_skill (
	npc_template_id integer NOT NULL,
	skill_id integer NOT NULL,
	skill_level smallint NOT NULL CHECK (skill_level >= 0),
	PRIMARY KEY (npc_template_id, skill_id, skill_level)
);
COMMENT ON TABLE npc_skill IS 'Skills of NPC templates. Skill 4416 is not a skill: its level is the NPC race (L2NpcTemplate.Race ordinal + 1). Written by AdminEditNpc; edits last until the next catalog load.';
COMMENT ON COLUMN npc_skill.npc_template_id IS 'NPC that has the skill (catalog.npc_template.id or catalog.custom_npc_template.id); no foreign key because the shipped data has skills for NPC ids without a template, which the loader skips.';
COMMENT ON COLUMN npc_skill.skill_id IS 'Skill, defined in the datapack skill XML files (data/stats/skills), not in a table.';
COMMENT ON COLUMN npc_skill.skill_level IS 'Skill level; for skill 4416 the NPC race.';

CREATE UNLOGGED TABLE custom_npc_skill (
	npc_template_id integer NOT NULL,
	skill_id integer NOT NULL,
	skill_level smallint NOT NULL CHECK (skill_level >= 0),
	PRIMARY KEY (npc_template_id, skill_id, skill_level)
);
COMMENT ON TABLE custom_npc_skill IS 'Server-specific NPC skills, same columns as npc_skill; loaded after it.';
COMMENT ON COLUMN custom_npc_skill.npc_template_id IS 'NPC that has the skill (catalog.npc_template.id or catalog.custom_npc_template.id).';
COMMENT ON COLUMN custom_npc_skill.skill_id IS 'Skill, defined in the datapack skill XML files (data/stats/skills), not in a table.';
COMMENT ON COLUMN custom_npc_skill.skill_level IS 'Skill level; for skill 4416 the NPC race.';

CREATE UNLOGGED TABLE minion (
	boss_npc_template_id integer NOT NULL REFERENCES npc_template,
	minion_npc_template_id integer NOT NULL REFERENCES npc_template,
	min_count integer NOT NULL CHECK (min_count >= 0),
	max_count integer NOT NULL,
	PRIMARY KEY (boss_npc_template_id, minion_npc_template_id),
	CHECK (max_count >= min_count)
);
COMMENT ON TABLE minion IS 'Minions that spawn with a boss or a leader monster.';
COMMENT ON COLUMN minion.boss_npc_template_id IS 'Leader NPC.';
COMMENT ON COLUMN minion.minion_npc_template_id IS 'Minion NPC.';
COMMENT ON COLUMN minion.min_count IS 'Minimum number of minions of this kind.';
COMMENT ON COLUMN minion.max_count IS 'Maximum number of minions of this kind.';
CREATE INDEX minion_minion_npc_template_id_idx ON minion (minion_npc_template_id);

-- Spawns ----------------------------------------------------------------------

CREATE UNLOGGED TABLE spawn (
	spawn_group text NOT NULL CHECK (spawn_group IN ('WORLD', 'CUSTOM', 'VAN_HALTER', 'LAST_IMPERIAL_TOMB')),
	id integer NOT NULL,
	npc_template_id integer NOT NULL,
	npc_count integer NOT NULL DEFAULT 1 CHECK (npc_count >= 0),
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	heading integer NOT NULL DEFAULT 0,
	respawn_delay_s integer NOT NULL DEFAULT 0 CHECK (respawn_delay_s >= 0),
	period_of_day text NOT NULL DEFAULT 'ALWAYS' CHECK (period_of_day IN ('ALWAYS', 'DAY', 'NIGHT')),
	area_code integer,
	area_name text,
	PRIMARY KEY (spawn_group, id)
);
COMMENT ON TABLE spawn IS 'NPC spawn points. spawn_group tells which code loads the row: SpawnTable (WORLD, CUSTOM), VanHalterManager, LastImperialTombSpawnlist. Written by AdminSpawn; edits last until the next catalog load.';
COMMENT ON COLUMN spawn.spawn_group IS 'Spawn list the row belongs to: WORLD (spawned at startup), CUSTOM (server-specific, spawned at startup), VAN_HALTER (High Priestess van Halter event), LAST_IMPERIAL_TOMB (Frintezza instance).';
COMMENT ON COLUMN spawn.id IS 'Spawn number, unique within spawn_group. SpawnTable assigns the next number when an admin stores a new spawn.';
COMMENT ON COLUMN spawn.npc_template_id IS 'Spawned NPC (catalog.npc_template.id or catalog.custom_npc_template.id); no foreign key because admins may spawn custom NPCs.';
COMMENT ON COLUMN spawn.npc_count IS 'Number of NPCs kept alive at this point.';
COMMENT ON COLUMN spawn.x IS 'Spawn X coordinate.';
COMMENT ON COLUMN spawn.y IS 'Spawn Y coordinate.';
COMMENT ON COLUMN spawn.z IS 'Spawn Z coordinate.';
COMMENT ON COLUMN spawn.heading IS 'Facing direction (client heading units).';
COMMENT ON COLUMN spawn.respawn_delay_s IS 'Delay before a killed NPC respawns, in seconds.';
COMMENT ON COLUMN spawn.period_of_day IS 'When the NPC is present: ALWAYS, only by DAY or only at NIGHT (game time). Used by the WORLD and CUSTOM groups.';
COMMENT ON COLUMN spawn.area_code IS 'Spawn area code kept on the spawn (L2Spawn.getLocation) but not used by game logic; NULL for none.';
COMMENT ON COLUMN spawn.area_name IS 'Area name for people, for example Kamael Island; not read by the game. NULL when unknown.';

CREATE UNLOGGED TABLE four_sepulchers_spawn (
	id integer PRIMARY KEY,
	spawn_type text NOT NULL CHECK (spawn_type IN ('MYSTERIOUS_BOX', 'PHYSICAL_MONSTER', 'MAGICAL_MONSTER', 'DUKE_FINAL_MONSTER', 'EMPEROR_GRAVE_MONSTER')),
	key_npc_template_id integer NOT NULL REFERENCES npc_template,
	npc_template_id integer NOT NULL REFERENCES npc_template,
	npc_count integer NOT NULL DEFAULT 1 CHECK (npc_count >= 0),
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	heading integer NOT NULL DEFAULT 0,
	respawn_delay_s integer NOT NULL DEFAULT 0 CHECK (respawn_delay_s >= 0)
);
COMMENT ON TABLE four_sepulchers_spawn IS 'Spawn points inside the Four Sepulchers, loaded by FourSepulchersManager.';
COMMENT ON COLUMN four_sepulchers_spawn.spawn_type IS 'What the row spawns: MYSTERIOUS_BOX, PHYSICAL_MONSTER, MAGICAL_MONSTER, DUKE_FINAL_MONSTER or EMPEROR_GRAVE_MONSTER (MySQL spawntype 0, 1, 2, 5, 6).';
COMMENT ON COLUMN four_sepulchers_spawn.key_npc_template_id IS 'NPC that groups these spawns: FourSepulchersManager looks the spawns up by this NPC (the key box or room NPC that triggers them).';
COMMENT ON COLUMN four_sepulchers_spawn.npc_template_id IS 'Spawned NPC.';
COMMENT ON COLUMN four_sepulchers_spawn.npc_count IS 'Number of NPCs spawned at this point.';
COMMENT ON COLUMN four_sepulchers_spawn.x IS 'Spawn X coordinate.';
COMMENT ON COLUMN four_sepulchers_spawn.y IS 'Spawn Y coordinate.';
COMMENT ON COLUMN four_sepulchers_spawn.z IS 'Spawn Z coordinate.';
COMMENT ON COLUMN four_sepulchers_spawn.heading IS 'Facing direction (client heading units).';
COMMENT ON COLUMN four_sepulchers_spawn.respawn_delay_s IS 'Delay before a killed NPC respawns, in seconds.';
CREATE INDEX four_sepulchers_spawn_key_npc_template_id_spawn_type_idx ON four_sepulchers_spawn (key_npc_template_id, spawn_type);
CREATE INDEX four_sepulchers_spawn_npc_template_id_idx ON four_sepulchers_spawn (npc_template_id);

CREATE UNLOGGED TABLE naia_room_spawn (
	room_number smallint NOT NULL CHECK (room_number >= 1),
	npc_template_id integer NOT NULL REFERENCES npc_template,
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	heading integer NOT NULL DEFAULT 0,
	respawn_delay_s integer CHECK (respawn_delay_s >= 0),
	PRIMARY KEY (room_number, npc_template_id, x, y, z)
);
COMMENT ON TABLE naia_room_spawn IS 'Monsters of the Tower of Naia rooms (Hellbound), loaded by TowerOfNaiaRoom.';
COMMENT ON COLUMN naia_room_spawn.room_number IS 'Room of the tower, 1 to 12.';
COMMENT ON COLUMN naia_room_spawn.npc_template_id IS 'Spawned NPC; the room controller (Ingenious Contraption) is one of them.';
COMMENT ON COLUMN naia_room_spawn.x IS 'Spawn X coordinate.';
COMMENT ON COLUMN naia_room_spawn.y IS 'Spawn Y coordinate.';
COMMENT ON COLUMN naia_room_spawn.z IS 'Spawn Z coordinate.';
COMMENT ON COLUMN naia_room_spawn.heading IS 'Facing direction (client heading units).';
COMMENT ON COLUMN naia_room_spawn.respawn_delay_s IS 'Delay before a killed NPC respawns, in seconds; NULL means it does not respawn.';
CREATE INDEX naia_room_spawn_npc_template_id_idx ON naia_room_spawn (npc_template_id);

CREATE UNLOGGED TABLE naia_room_door (
	room_number smallint NOT NULL CHECK (room_number >= 1),
	door_id integer NOT NULL,
	door_action text NOT NULL CHECK (door_action IN ('PRE_OPEN', 'PRE_CLOSE', 'POST_OPEN', 'POST_CLOSE')),
	PRIMARY KEY (room_number, door_id, door_action)
);
COMMENT ON TABLE naia_room_door IS 'Doors that a Tower of Naia room opens or closes before and after its fight, loaded by TowerOfNaiaRoom.';
COMMENT ON COLUMN naia_room_door.room_number IS 'Room of the tower, 1 to 12.';
COMMENT ON COLUMN naia_room_door.door_id IS 'Instance door, defined in the datapack door data (data/door.csv or the instance files in data/instances), not in a table.';
COMMENT ON COLUMN naia_room_door.door_action IS 'What happens to the door: PRE_OPEN or PRE_CLOSE when the room is prepared, POST_OPEN or POST_CLOSE when it is cleared (MySQL action_order 0 to 3).';

CREATE UNLOGGED TABLE random_spawn (
	id integer PRIMARY KEY,
	npc_template_id integer NOT NULL REFERENCES npc_template,
	npc_count integer NOT NULL DEFAULT 1 CHECK (npc_count >= 0),
	initial_delay_ms bigint CHECK (initial_delay_ms >= 0),
	respawn_delay_ms bigint CHECK (respawn_delay_ms >= 0),
	despawn_delay_ms bigint CHECK (despawn_delay_ms >= 0),
	broadcasts_spawn boolean NOT NULL DEFAULT false,
	uses_random_location boolean NOT NULL DEFAULT true
);
COMMENT ON TABLE random_spawn IS 'Automatic spawn groups (AutoSpawnManager): an NPC that appears at one of its locations on a timer, for example the Seven Signs merchants.';
COMMENT ON COLUMN random_spawn.npc_template_id IS 'Spawned NPC.';
COMMENT ON COLUMN random_spawn.npc_count IS 'Number of NPCs spawned each time.';
COMMENT ON COLUMN random_spawn.initial_delay_ms IS 'Delay before the first spawn after startup, in milliseconds; NULL means the server default.';
COMMENT ON COLUMN random_spawn.respawn_delay_ms IS 'Interval between spawns, in milliseconds; NULL means the server default.';
COMMENT ON COLUMN random_spawn.despawn_delay_ms IS 'Time the NPC stays before it despawns, in milliseconds; 0 means it stays, NULL means the server default.';
COMMENT ON COLUMN random_spawn.broadcasts_spawn IS 'True when players are told where the NPC appeared.';
COMMENT ON COLUMN random_spawn.uses_random_location IS 'True when each spawn picks a random location of the group; false cycles through them in order.';
CREATE INDEX random_spawn_npc_template_id_idx ON random_spawn (npc_template_id);

CREATE UNLOGGED TABLE random_spawn_location (
	random_spawn_id integer NOT NULL REFERENCES random_spawn ON DELETE CASCADE,
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	heading integer,
	PRIMARY KEY (random_spawn_id, x, y, z)
);
COMMENT ON TABLE random_spawn_location IS 'Locations of an automatic spawn group.';
COMMENT ON COLUMN random_spawn_location.random_spawn_id IS 'Spawn group.';
COMMENT ON COLUMN random_spawn_location.x IS 'Spawn X coordinate.';
COMMENT ON COLUMN random_spawn_location.y IS 'Spawn Y coordinate.';
COMMENT ON COLUMN random_spawn_location.z IS 'Spawn Z coordinate.';
COMMENT ON COLUMN random_spawn_location.heading IS 'Facing direction (client heading units); NULL means a random heading.';

-- Bosses ----------------------------------------------------------------------

CREATE UNLOGGED TABLE raid_boss_spawn (
	npc_template_id integer PRIMARY KEY,
	npc_count integer NOT NULL DEFAULT 1 CHECK (npc_count >= 0),
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	heading integer NOT NULL DEFAULT 0,
	respawn_min_delay_s integer NOT NULL DEFAULT 43200 CHECK (respawn_min_delay_s >= 0),
	respawn_max_delay_s integer NOT NULL DEFAULT 129600,
	CHECK (respawn_max_delay_s >= respawn_min_delay_s)
);
COMMENT ON TABLE raid_boss_spawn IS 'Where each raid boss spawns and how long it takes to come back. The respawn moment and current HP/MP are game state in world.raid_boss_state. Written by AdminSpawn; edits last until the next catalog load.';
COMMENT ON COLUMN raid_boss_spawn.npc_template_id IS 'Raid boss (catalog.npc_template.id or catalog.custom_npc_template.id); no foreign key because admins may place custom raid bosses.';
COMMENT ON COLUMN raid_boss_spawn.npc_count IS 'Number of boss NPCs spawned (always 1 in the shipped data).';
COMMENT ON COLUMN raid_boss_spawn.x IS 'Spawn X coordinate.';
COMMENT ON COLUMN raid_boss_spawn.y IS 'Spawn Y coordinate.';
COMMENT ON COLUMN raid_boss_spawn.z IS 'Spawn Z coordinate.';
COMMENT ON COLUMN raid_boss_spawn.heading IS 'Facing direction (client heading units).';
COMMENT ON COLUMN raid_boss_spawn.respawn_min_delay_s IS 'Shortest delay between death and respawn, in seconds.';
COMMENT ON COLUMN raid_boss_spawn.respawn_max_delay_s IS 'Longest delay between death and respawn, in seconds; the actual delay is random in between.';

CREATE UNLOGGED TABLE grand_boss_spawn (
	npc_template_id integer PRIMARY KEY,
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	heading integer NOT NULL DEFAULT 0,
	respawn_min_delay_s integer NOT NULL DEFAULT 86400 CHECK (respawn_min_delay_s >= 0),
	respawn_max_delay_s integer NOT NULL DEFAULT 129600,
	CHECK (respawn_max_delay_s >= respawn_min_delay_s)
);
COMMENT ON TABLE grand_boss_spawn IS 'Where each grand boss handled by GrandBossSpawnManager spawns and how long it takes to come back. The respawn moment and current HP/MP are game state in world.grand_boss_state. Written by AdminSpawn; edits last until the next catalog load.';
COMMENT ON COLUMN grand_boss_spawn.npc_template_id IS 'Grand boss (catalog.npc_template.id or catalog.custom_npc_template.id); no foreign key because admins may place custom bosses.';
COMMENT ON COLUMN grand_boss_spawn.x IS 'Spawn X coordinate.';
COMMENT ON COLUMN grand_boss_spawn.y IS 'Spawn Y coordinate.';
COMMENT ON COLUMN grand_boss_spawn.z IS 'Spawn Z coordinate.';
COMMENT ON COLUMN grand_boss_spawn.heading IS 'Facing direction (client heading units).';
COMMENT ON COLUMN grand_boss_spawn.respawn_min_delay_s IS 'Shortest delay between death and respawn, in seconds.';
COMMENT ON COLUMN grand_boss_spawn.respawn_max_delay_s IS 'Longest delay between death and respawn, in seconds; the actual delay is random in between.';

-- Residences: fort NPCs, siege guards, doors ------------------------------------

CREATE UNLOGGED TABLE fort_spawn (
	id integer PRIMARY KEY,
	fort_id smallint NOT NULL REFERENCES world.fort,
	spawn_type text NOT NULL CHECK (spawn_type IN ('NPC', 'COMMANDER', 'SIEGE_NPC', 'SPECIAL_ENVOY')),
	npc_template_id integer NOT NULL REFERENCES npc_template,
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	heading integer NOT NULL DEFAULT 0,
	castle_id smallint REFERENCES world.castle,
	CHECK ((castle_id IS NOT NULL) = (spawn_type = 'SPECIAL_ENVOY'))
);
COMMENT ON TABLE fort_spawn IS 'NPCs of a fortress, loaded by FortManager: permanent NPCs, siege commanders, siege NPCs and castle envoys.';
COMMENT ON COLUMN fort_spawn.fort_id IS 'Fortress.';
COMMENT ON COLUMN fort_spawn.spawn_type IS 'Role of the NPC: NPC (always present), COMMANDER (siege commander), SIEGE_NPC (present during a siege), SPECIAL_ENVOY (castle envoy after a capture); MySQL spawnType 0 to 3.';
COMMENT ON COLUMN fort_spawn.npc_template_id IS 'Spawned NPC.';
COMMENT ON COLUMN fort_spawn.x IS 'Spawn X coordinate.';
COMMENT ON COLUMN fort_spawn.y IS 'Spawn Y coordinate.';
COMMENT ON COLUMN fort_spawn.z IS 'Spawn Z coordinate.';
COMMENT ON COLUMN fort_spawn.heading IS 'Facing direction (client heading units).';
COMMENT ON COLUMN fort_spawn.castle_id IS 'Castle the envoy represents; NULL for every type except SPECIAL_ENVOY.';
CREATE INDEX fort_spawn_fort_id_spawn_type_idx ON fort_spawn (fort_id, spawn_type);
CREATE INDEX fort_spawn_npc_template_id_idx ON fort_spawn (npc_template_id);
CREATE INDEX fort_spawn_castle_id_idx ON fort_spawn (castle_id);

CREATE UNLOGGED TABLE fort_static_object (
	id integer PRIMARY KEY,
	fort_id smallint NOT NULL REFERENCES world.fort,
	object_type text NOT NULL CHECK (object_type IN ('DOOR', 'FLAG_POLE')),
	name text NOT NULL,
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	range_x_min integer NOT NULL DEFAULT 0,
	range_y_min integer NOT NULL DEFAULT 0,
	range_z_min integer NOT NULL DEFAULT 0,
	range_x_max integer NOT NULL DEFAULT 0,
	range_y_max integer NOT NULL DEFAULT 0,
	range_z_max integer NOT NULL DEFAULT 0,
	hp integer NOT NULL DEFAULT 0 CHECK (hp >= 0),
	p_def integer NOT NULL DEFAULT 0 CHECK (p_def >= 0),
	m_def integer NOT NULL DEFAULT 0 CHECK (m_def >= 0),
	is_unlockable boolean NOT NULL DEFAULT false,
	starts_open boolean NOT NULL DEFAULT false
);
COMMENT ON TABLE fort_static_object IS 'Doors and flag poles of fortresses, loaded by Fort. The id is the door or static object id; the world door upgrade table of forts refers to it.';
COMMENT ON COLUMN fort_static_object.fort_id IS 'Fortress.';
COMMENT ON COLUMN fort_static_object.object_type IS 'DOOR or FLAG_POLE (MySQL objectType 0 or 1). Flag poles use only name and position.';
COMMENT ON COLUMN fort_static_object.name IS 'Object name, for example the door name used in logs and scripts.';
COMMENT ON COLUMN fort_static_object.x IS 'X coordinate.';
COMMENT ON COLUMN fort_static_object.y IS 'Y coordinate.';
COMMENT ON COLUMN fort_static_object.z IS 'Z coordinate.';
COMMENT ON COLUMN fort_static_object.range_x_min IS 'Door bounding box: lowest X.';
COMMENT ON COLUMN fort_static_object.range_y_min IS 'Door bounding box: lowest Y.';
COMMENT ON COLUMN fort_static_object.range_z_min IS 'Door bounding box: lowest Z.';
COMMENT ON COLUMN fort_static_object.range_x_max IS 'Door bounding box: highest X.';
COMMENT ON COLUMN fort_static_object.range_y_max IS 'Door bounding box: highest Y.';
COMMENT ON COLUMN fort_static_object.range_z_max IS 'Door bounding box: highest Z.';
COMMENT ON COLUMN fort_static_object.hp IS 'Base door HP before upgrades; 0 for flag poles.';
COMMENT ON COLUMN fort_static_object.p_def IS 'Base door physical defense.';
COMMENT ON COLUMN fort_static_object.m_def IS 'Base door magic defense.';
COMMENT ON COLUMN fort_static_object.is_unlockable IS 'True when players can unlock the door with a skill or key (MySQL openType).';
COMMENT ON COLUMN fort_static_object.starts_open IS 'True when the door is open after it spawns (MySQL commanderDoor; the loader passes it to DoorTable as the start-open flag).';
CREATE INDEX fort_static_object_fort_id_object_type_idx ON fort_static_object (fort_id, object_type);

CREATE UNLOGGED TABLE castle_door (
	id integer PRIMARY KEY,
	castle_id smallint NOT NULL REFERENCES world.castle,
	name text NOT NULL,
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	range_x_min integer NOT NULL DEFAULT 0,
	range_y_min integer NOT NULL DEFAULT 0,
	range_z_min integer NOT NULL DEFAULT 0,
	range_x_max integer NOT NULL DEFAULT 0,
	range_y_max integer NOT NULL DEFAULT 0,
	range_z_max integer NOT NULL DEFAULT 0,
	hp integer NOT NULL CHECK (hp >= 0),
	p_def integer NOT NULL CHECK (p_def >= 0),
	m_def integer NOT NULL CHECK (m_def >= 0)
);
COMMENT ON TABLE castle_door IS 'Doors and walls of castles, loaded by Castle. The id is the door id; the world door upgrade table of castles refers to it.';
COMMENT ON COLUMN castle_door.castle_id IS 'Castle.';
COMMENT ON COLUMN castle_door.name IS 'Door name, for example gludio_castle_outter_001.';
COMMENT ON COLUMN castle_door.x IS 'X coordinate.';
COMMENT ON COLUMN castle_door.y IS 'Y coordinate.';
COMMENT ON COLUMN castle_door.z IS 'Z coordinate.';
COMMENT ON COLUMN castle_door.range_x_min IS 'Door bounding box: lowest X.';
COMMENT ON COLUMN castle_door.range_y_min IS 'Door bounding box: lowest Y.';
COMMENT ON COLUMN castle_door.range_z_min IS 'Door bounding box: lowest Z.';
COMMENT ON COLUMN castle_door.range_x_max IS 'Door bounding box: highest X.';
COMMENT ON COLUMN castle_door.range_y_max IS 'Door bounding box: highest Y.';
COMMENT ON COLUMN castle_door.range_z_max IS 'Door bounding box: highest Z.';
COMMENT ON COLUMN castle_door.hp IS 'Base door HP before upgrades.';
COMMENT ON COLUMN castle_door.p_def IS 'Base door physical defense.';
COMMENT ON COLUMN castle_door.m_def IS 'Base door magic defense.';
CREATE INDEX castle_door_castle_id_idx ON castle_door (castle_id);

CREATE UNLOGGED TABLE castle_siege_guard (
	id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	castle_id smallint NOT NULL REFERENCES world.castle,
	npc_template_id integer NOT NULL,
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	heading integer NOT NULL DEFAULT 0,
	respawn_delay_s integer NOT NULL DEFAULT 0 CHECK (respawn_delay_s >= 0)
);
COMMENT ON TABLE castle_siege_guard IS 'Guards that defend a castle owned by NPCs during a siege (SiegeGuardManager). Mercenaries hired by an owning clan are game state in world.castle_hired_guard. SiegeGuardManager.addAnyGuard adds rows; they last until the next catalog load.';
COMMENT ON COLUMN castle_siege_guard.castle_id IS 'Castle.';
COMMENT ON COLUMN castle_siege_guard.npc_template_id IS 'Guard NPC (catalog.npc_template.id or catalog.custom_npc_template.id); no foreign key because addAnyGuard accepts any NPC.';
COMMENT ON COLUMN castle_siege_guard.x IS 'Spawn X coordinate.';
COMMENT ON COLUMN castle_siege_guard.y IS 'Spawn Y coordinate.';
COMMENT ON COLUMN castle_siege_guard.z IS 'Spawn Z coordinate.';
COMMENT ON COLUMN castle_siege_guard.heading IS 'Facing direction (client heading units).';
COMMENT ON COLUMN castle_siege_guard.respawn_delay_s IS 'Delay before a killed guard respawns during the siege, in seconds.';
CREATE INDEX castle_siege_guard_castle_id_idx ON castle_siege_guard (castle_id);

CREATE UNLOGGED TABLE fort_siege_guard (
	id integer PRIMARY KEY,
	fort_id smallint NOT NULL REFERENCES world.fort,
	npc_template_id integer NOT NULL REFERENCES npc_template,
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	heading integer NOT NULL DEFAULT 0,
	respawn_delay_s integer NOT NULL DEFAULT 0 CHECK (respawn_delay_s >= 0)
);
COMMENT ON TABLE fort_siege_guard IS 'Guards that defend a fortress during a siege (FortSiegeGuardManager).';
COMMENT ON COLUMN fort_siege_guard.fort_id IS 'Fortress.';
COMMENT ON COLUMN fort_siege_guard.npc_template_id IS 'Guard NPC.';
COMMENT ON COLUMN fort_siege_guard.x IS 'Spawn X coordinate.';
COMMENT ON COLUMN fort_siege_guard.y IS 'Spawn Y coordinate.';
COMMENT ON COLUMN fort_siege_guard.z IS 'Spawn Z coordinate.';
COMMENT ON COLUMN fort_siege_guard.heading IS 'Facing direction (client heading units).';
COMMENT ON COLUMN fort_siege_guard.respawn_delay_s IS 'Delay before a killed guard respawns during the siege, in seconds.';
CREATE INDEX fort_siege_guard_fort_id_idx ON fort_siege_guard (fort_id);
CREATE INDEX fort_siege_guard_npc_template_id_idx ON fort_siege_guard (npc_template_id);

CREATE UNLOGGED TABLE clan_hall_siege_guard (
	id integer PRIMARY KEY,
	clan_hall_id smallint NOT NULL REFERENCES world.clan_hall,
	npc_template_id integer NOT NULL REFERENCES npc_template,
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	heading integer NOT NULL DEFAULT 0,
	respawn_delay_s integer NOT NULL DEFAULT 7200 CHECK (respawn_delay_s >= 0)
);
COMMENT ON TABLE clan_hall_siege_guard IS 'Guards of a contestable clan hall during its siege (ContestableHideoutGuardManager).';
COMMENT ON COLUMN clan_hall_siege_guard.clan_hall_id IS 'Clan hall.';
COMMENT ON COLUMN clan_hall_siege_guard.npc_template_id IS 'Guard NPC.';
COMMENT ON COLUMN clan_hall_siege_guard.x IS 'Spawn X coordinate.';
COMMENT ON COLUMN clan_hall_siege_guard.y IS 'Spawn Y coordinate.';
COMMENT ON COLUMN clan_hall_siege_guard.z IS 'Spawn Z coordinate.';
COMMENT ON COLUMN clan_hall_siege_guard.heading IS 'Facing direction (client heading units).';
COMMENT ON COLUMN clan_hall_siege_guard.respawn_delay_s IS 'Delay before a killed guard respawns during the siege, in seconds.';
CREATE INDEX clan_hall_siege_guard_clan_hall_id_idx ON clan_hall_siege_guard (clan_hall_id);
CREATE INDEX clan_hall_siege_guard_npc_template_id_idx ON clan_hall_siege_guard (npc_template_id);

-- Teleports, walker routes, auto chat -----------------------------------------

CREATE UNLOGGED TABLE teleport (
	id integer PRIMARY KEY,
	description text NOT NULL,
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	price integer NOT NULL DEFAULT 0 CHECK (price >= 0),
	is_noble_only boolean NOT NULL DEFAULT false
);
COMMENT ON TABLE teleport IS 'Teleport destinations offered by gatekeepers; NPC dialogs refer to them by id.';
COMMENT ON COLUMN teleport.description IS 'Destination for people, for example The Village of Gludin -> Talking Island; not read by the game.';
COMMENT ON COLUMN teleport.x IS 'Destination X coordinate.';
COMMENT ON COLUMN teleport.y IS 'Destination Y coordinate.';
COMMENT ON COLUMN teleport.z IS 'Destination Z coordinate.';
COMMENT ON COLUMN teleport.price IS 'Price in adena, or in noble gate passes when is_noble_only; ignored when free teleports are configured.';
COMMENT ON COLUMN teleport.is_noble_only IS 'True when only nobles may use the destination.';

CREATE UNLOGGED TABLE walker_route (
	route_number integer NOT NULL,
	npc_template_id integer NOT NULL REFERENCES npc_template,
	point_number integer NOT NULL,
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	delay_s integer NOT NULL DEFAULT 0 CHECK (delay_s >= 0),
	is_running boolean NOT NULL DEFAULT false,
	chat_text text,
	PRIMARY KEY (route_number, npc_template_id, point_number)
);
COMMENT ON TABLE walker_route IS 'Points of the routes that walking NPCs (L2NpcWalker) follow, loaded by NpcWalkerRoutesTable.';
COMMENT ON COLUMN walker_route.route_number IS 'Route number; groups the points of one route.';
COMMENT ON COLUMN walker_route.npc_template_id IS 'Walking NPC.';
COMMENT ON COLUMN walker_route.point_number IS 'Order of the point on the route.';
COMMENT ON COLUMN walker_route.x IS 'Point X coordinate.';
COMMENT ON COLUMN walker_route.y IS 'Point Y coordinate.';
COMMENT ON COLUMN walker_route.z IS 'Point Z coordinate.';
COMMENT ON COLUMN walker_route.delay_s IS 'Time the NPC waits at the point, in seconds.';
COMMENT ON COLUMN walker_route.is_running IS 'True when the NPC runs to the point, false when it walks.';
COMMENT ON COLUMN walker_route.chat_text IS 'Text the NPC says at the point; NULL for none.';
CREATE INDEX walker_route_npc_template_id_idx ON walker_route (npc_template_id);

CREATE UNLOGGED TABLE auto_chat (
	id integer PRIMARY KEY,
	name text NOT NULL DEFAULT '',
	npc_template_id integer NOT NULL REFERENCES npc_template,
	chat_delay_s bigint CHECK (chat_delay_s >= 0),
	chat_range integer CHECK (chat_range >= 0),
	is_random boolean NOT NULL DEFAULT false
);
COMMENT ON TABLE auto_chat IS 'Chat groups: lines that every NPC of a template says on a timer (AutoChatManager).';
COMMENT ON COLUMN auto_chat.name IS 'Group name for people, for example Preacher of Doom; not read by the game.';
COMMENT ON COLUMN auto_chat.npc_template_id IS 'NPC that speaks.';
COMMENT ON COLUMN auto_chat.chat_delay_s IS 'Interval between lines, in seconds; NULL means the server default (ALT_AUTOCHAT_DELAY).';
COMMENT ON COLUMN auto_chat.chat_range IS 'Range in game units in which players hear the line; NULL means the default of 1500.';
COMMENT ON COLUMN auto_chat.is_random IS 'True when lines are picked at random, false when they are said in order.';
CREATE INDEX auto_chat_npc_template_id_idx ON auto_chat (npc_template_id);

CREATE UNLOGGED TABLE auto_chat_text (
	auto_chat_id integer NOT NULL REFERENCES auto_chat ON DELETE CASCADE,
	chat_text text NOT NULL,
	PRIMARY KEY (auto_chat_id, chat_text)
);
COMMENT ON TABLE auto_chat_text IS 'Lines of a chat group. The game says them in chat_text order unless the group is random.';
COMMENT ON COLUMN auto_chat_text.auto_chat_id IS 'Chat group.';
COMMENT ON COLUMN auto_chat_text.chat_text IS 'Line; %player_random% and similar placeholders are replaced by AutoChatManager.';
