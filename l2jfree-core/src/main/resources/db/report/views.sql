-- Read-only views for people. The
-- server drops and creates the report schema from this file on every start and
-- after every catalog load: the views read the catalog, which is replaced
-- wholesale. Views never expose password hashes or other secrets.

COMMENT ON SCHEMA report IS 'Read-only views for operators over world and catalog, one view per question. Recreated by the server on every start and catalog load; nobody writes here.';

CREATE VIEW report.item_name AS
	SELECT id AS item_template_id, name AS item_name, 'weapon' AS item_kind FROM catalog.weapon_template
	UNION ALL SELECT id, name, 'armor' FROM catalog.armor_template
	UNION ALL SELECT id, name, 'etc' FROM catalog.etc_item_template;
COMMENT ON VIEW report.item_name IS 'Name and kind of every item template of the catalog.';
COMMENT ON COLUMN report.item_name.item_template_id IS 'Item template id (world.item.item_template_id).';
COMMENT ON COLUMN report.item_name.item_name IS 'Item name.';
COMMENT ON COLUMN report.item_name.item_kind IS 'weapon, armor or etc: the catalog table of the template.';

CREATE VIEW report.player_overview AS
	SELECT p.id AS player_id, p.name AS player_name, p.account_name, r.name AS race, c.name AS class,
		p.level, p.exp, p.sp, cl.name AS clan_name, p.is_online, p.last_access_at AS last_login_at,
		p.pvp_kills, p.pk_kills, p.karma, p.is_noble, p.access_level
	FROM world.player p
	JOIN world.race r ON r.id = p.race_id
	JOIN world.player_class c ON c.id = p.active_class_id
	LEFT JOIN world.clan cl ON cl.id = p.clan_id;
COMMENT ON VIEW report.player_overview IS 'One row per character: who it is, how far it got and where it belongs.';
COMMENT ON COLUMN report.player_overview.player_id IS 'Character id (world.player.id).';
COMMENT ON COLUMN report.player_overview.player_name IS 'Character name.';
COMMENT ON COLUMN report.player_overview.account_name IS 'Login account that owns the character.';
COMMENT ON COLUMN report.player_overview.race IS 'Race name.';
COMMENT ON COLUMN report.player_overview.class IS 'Name of the active class.';
COMMENT ON COLUMN report.player_overview.level IS 'Level of the base class.';
COMMENT ON COLUMN report.player_overview.exp IS 'Experience of the base class.';
COMMENT ON COLUMN report.player_overview.sp IS 'Skill points of the base class.';
COMMENT ON COLUMN report.player_overview.clan_name IS 'Clan of the character; NULL when it has none.';
COMMENT ON COLUMN report.player_overview.is_online IS 'True while the character is in the game.';
COMMENT ON COLUMN report.player_overview.last_login_at IS 'Moment the character last entered or left the game; NULL if it never did.';
COMMENT ON COLUMN report.player_overview.pvp_kills IS 'Players killed in PvP.';
COMMENT ON COLUMN report.player_overview.pk_kills IS 'Players killed as a player killer.';
COMMENT ON COLUMN report.player_overview.karma IS 'Current karma.';
COMMENT ON COLUMN report.player_overview.is_noble IS 'True for a noblesse.';
COMMENT ON COLUMN report.player_overview.access_level IS 'Access level: 0 for players, above 0 for GMs, below 0 for banned characters.';

CREATE VIEW report.player_inventory AS
	SELECT p.id AS player_id, p.name AS player_name, i.id AS item_id, i.item_template_id, n.item_name,
		i.location, i.count, i.enchant_level
	FROM world.item i
	JOIN world.player p ON p.id = i.owner_player_id
	LEFT JOIN report.item_name n ON n.item_template_id = i.item_template_id;
COMMENT ON VIEW report.player_inventory IS 'Items each character owns: inventory, equipped items, warehouse and freight.';
COMMENT ON COLUMN report.player_inventory.player_id IS 'Character id (world.player.id).';
COMMENT ON COLUMN report.player_inventory.player_name IS 'Character name.';
COMMENT ON COLUMN report.player_inventory.item_id IS 'Item instance id (world.item.id).';
COMMENT ON COLUMN report.player_inventory.item_template_id IS 'Item template id.';
COMMENT ON COLUMN report.player_inventory.item_name IS 'Item name from the catalog; NULL when the catalog has no such template.';
COMMENT ON COLUMN report.player_inventory.location IS 'INVENTORY, PAPERDOLL (equipped), WAREHOUSE or FREIGHT.';
COMMENT ON COLUMN report.player_inventory.count IS 'Number of pieces in the stack.';
COMMENT ON COLUMN report.player_inventory.enchant_level IS 'Enchant level.';

CREATE VIEW report.clan_overview AS
	SELECT cl.id AS clan_id, cl.name AS clan_name, cl.level, leader.name AS leader_name,
		(SELECT count(*) FROM world.player m WHERE m.clan_id = cl.id) AS member_count,
		cl.reputation_score, cl.alliance_name, castle.name AS castle_name
	FROM world.clan cl
	JOIN world.player leader ON leader.id = cl.leader_player_id
	LEFT JOIN world.castle castle ON castle.id = cl.castle_id;
COMMENT ON VIEW report.clan_overview IS 'One row per clan: leader, size, reputation, alliance and castle.';
COMMENT ON COLUMN report.clan_overview.clan_id IS 'Clan id (world.clan.id).';
COMMENT ON COLUMN report.clan_overview.clan_name IS 'Clan name.';
COMMENT ON COLUMN report.clan_overview.level IS 'Clan level.';
COMMENT ON COLUMN report.clan_overview.leader_name IS 'Name of the clan leader.';
COMMENT ON COLUMN report.clan_overview.member_count IS 'Number of characters in the clan, the leader included.';
COMMENT ON COLUMN report.clan_overview.reputation_score IS 'Clan reputation points.';
COMMENT ON COLUMN report.clan_overview.alliance_name IS 'Alliance of the clan; NULL when it is in none.';
COMMENT ON COLUMN report.clan_overview.castle_name IS 'Castle the clan owns; NULL when it owns none.';
