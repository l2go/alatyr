-- Catalog: item templates, armor sets, character creation, skill trees, henna,
-- buff templates, merchant price lists, pet static data and fish.
--
-- The catalog is rebuilt from the image: the loader drops and recreates schema
-- catalog in one transaction, runs these files and loads every table from the CSV
-- files of the image with COPY. The tables are UNLOGGED and never backed up. Edits
-- made in game by admin commands last until the next catalog load.
--
-- Item references: an item template id is unique across weapon_template,
-- armor_template and etc_item_template (and their custom_ twins), so a column that
-- may point to any item has no foreign key; its comment names the tables. Columns
-- that can only point to one kind of item have a foreign key. Skills live in XML
-- files, not in the database, so skill_id columns have no foreign key.
-- References to NPC templates (npc_template_id) get their foreign keys, where the
-- data allows, from the NPC catalog file that creates catalog.npc_template.
-- Catalog tables may reference the world reference tables (race, player_class)
-- and residences (castle, fort, clan_hall): the world schema is always migrated
-- before the catalog is loaded.

COMMENT ON SCHEMA catalog IS 'Game content from the image: item, NPC and skill templates, spawns, drops, shops, teleports. Rebuilt from the image when its revision changes; UNLOGGED; never backed up.';

CREATE UNLOGGED TABLE weapon_template (
	id integer PRIMARY KEY,
	name text NOT NULL,
	body_part text NOT NULL DEFAULT 'none' CHECK (body_part IN ('shirt', 'lbracelet', 'rbracelet', 'talisman', 'chest', 'fullarmor', 'head', 'hair', 'face', 'hair2', 'dhair', 'hairall', 'underwear', 'back', 'neck', 'legs', 'feet', 'gloves', 'chest,legs', 'belt', 'rhand', 'lhand', 'lrhand', 'rear,lear', 'rfinger,lfinger', 'wolf', 'greatwolf', 'hatchling', 'strider', 'babypet', 'none')),
	weapon_type text NOT NULL DEFAULT 'none' CHECK (weapon_type IN ('blunt', 'bow', 'dagger', 'dual', 'dualfist', 'etc', 'fist', 'none', 'pole', 'sword', 'bigsword', 'pet', 'rod', 'bigblunt', 'crossbow', 'rapier', 'ancient', 'dualdagger')),
	is_crystallizable boolean NOT NULL DEFAULT false,
	crystal_type text NOT NULL DEFAULT 'none' CHECK (crystal_type IN ('none', 'd', 'c', 'b', 'a', 's', 's80', 's84')),
	crystal_count integer NOT NULL DEFAULT 0 CHECK (crystal_count >= 0),
	material text NOT NULL DEFAULT 'wood' CHECK (material IN ('paper', 'wood', 'liquid', 'cloth', 'leather', 'horn', 'bone', 'bronze', 'fine_steel', 'cotton', 'mithril', 'silver', 'gold', 'adamantaite', 'steel', 'oriharukon', 'blood_steel', 'crystal', 'damascus', 'chrysolite', 'scale_of_dragon', 'dyestuff', 'cobweb', 'seed')),
	weight integer NOT NULL DEFAULT 0 CHECK (weight >= 0),
	soulshot_count smallint NOT NULL DEFAULT 0 CHECK (soulshot_count >= 0),
	spiritshot_count smallint NOT NULL DEFAULT 0 CHECK (spiritshot_count >= 0),
	physical_damage integer NOT NULL DEFAULT 0,
	random_damage integer NOT NULL DEFAULT 0,
	magic_damage integer NOT NULL DEFAULT 0,
	critical_rate integer NOT NULL DEFAULT 0,
	accuracy_modifier integer NOT NULL DEFAULT 0,
	evasion_modifier integer NOT NULL DEFAULT 0,
	shield_defense integer NOT NULL DEFAULT 0,
	shield_defense_rate integer NOT NULL DEFAULT 0,
	attack_speed integer NOT NULL DEFAULT 0,
	mp_consumption integer NOT NULL DEFAULT 0 CHECK (mp_consumption >= 0),
	shadow_mana integer CHECK (shadow_mana > 0),
	lifetime_s integer CHECK (lifetime_s > 0),
	price bigint NOT NULL DEFAULT 0 CHECK (price >= 0),
	is_sellable boolean NOT NULL DEFAULT false,
	is_droppable boolean NOT NULL DEFAULT false,
	is_destroyable boolean NOT NULL DEFAULT true,
	is_tradable boolean NOT NULL DEFAULT false,
	is_depositable boolean NOT NULL DEFAULT false,
	item_skills text NOT NULL DEFAULT '',
	enchant4_skills text NOT NULL DEFAULT '',
	on_cast_skills text NOT NULL DEFAULT '',
	on_critical_skills text NOT NULL DEFAULT '',
	change_weapon_template_id integer REFERENCES weapon_template
);
COMMENT ON TABLE weapon_template IS 'Weapon templates (weapons, shields, fishing rods, pet weapons). The item id is shared by all three item template tables and is unique across them.';
COMMENT ON COLUMN weapon_template.name IS 'Item name for people.';
COMMENT ON COLUMN weapon_template.body_part IS 'Equipment slot as ItemTable parses it (its _slots map); pairs such as ''rear,lear'' mean either of two slots, ''none'' means not equipped.';
COMMENT ON COLUMN weapon_template.weapon_type IS 'Weapon type as ItemTable parses it (its _weaponTypes map, for example bigsword = L2WeaponType.BIGSWORD); none is a shield.';
COMMENT ON COLUMN weapon_template.is_crystallizable IS 'Whether the item can be crystallized.';
COMMENT ON COLUMN weapon_template.crystal_type IS 'Grade: none, d, c, b, a, s, s80 or s84 (ItemTable _crystalTypes).';
COMMENT ON COLUMN weapon_template.crystal_count IS 'Number of crystals of the item grade that crystallizing the item gives.';
COMMENT ON COLUMN weapon_template.material IS 'Material as ItemTable parses it (its _materials map).';
COMMENT ON COLUMN weapon_template.weight IS 'Weight of one item in weight units.';
COMMENT ON COLUMN weapon_template.soulshot_count IS 'Soulshots consumed per shot.';
COMMENT ON COLUMN weapon_template.spiritshot_count IS 'Spiritshots consumed per shot.';
COMMENT ON COLUMN weapon_template.physical_damage IS 'Physical attack of the weapon.';
COMMENT ON COLUMN weapon_template.random_damage IS 'Random damage spread in percent of the physical attack.';
COMMENT ON COLUMN weapon_template.magic_damage IS 'Magic attack of the weapon.';
COMMENT ON COLUMN weapon_template.critical_rate IS 'Critical rate of the weapon.';
COMMENT ON COLUMN weapon_template.accuracy_modifier IS 'Accuracy bonus (negative for a penalty).';
COMMENT ON COLUMN weapon_template.evasion_modifier IS 'Evasion bonus (negative for a penalty).';
COMMENT ON COLUMN weapon_template.shield_defense IS 'Shield defense; non-zero for shields only.';
COMMENT ON COLUMN weapon_template.shield_defense_rate IS 'Shield block rate in percent; non-zero for shields only.';
COMMENT ON COLUMN weapon_template.attack_speed IS 'Attack speed of the weapon.';
COMMENT ON COLUMN weapon_template.mp_consumption IS 'MP consumed per attack (bows and some magic weapons).';
COMMENT ON COLUMN weapon_template.shadow_mana IS 'Mana of a shadow item: the number of minutes it can be worn; one point is used per minute while equipped. NULL when the item is not a shadow item.';
COMMENT ON COLUMN weapon_template.lifetime_s IS 'Lifetime of a time-limited item in seconds, counted from its creation. NULL when the item never expires.';
COMMENT ON COLUMN weapon_template.price IS 'Reference price in adena (used by shops without an own price and when selling to an NPC).';
COMMENT ON COLUMN weapon_template.is_sellable IS 'Whether the item can be sold to an NPC shop.';
COMMENT ON COLUMN weapon_template.is_droppable IS 'Whether the item can be dropped on the ground.';
COMMENT ON COLUMN weapon_template.is_destroyable IS 'Whether the player can destroy the item.';
COMMENT ON COLUMN weapon_template.is_tradable IS 'Whether the item can be traded between players.';
COMMENT ON COLUMN weapon_template.is_depositable IS 'Whether the item can be put into a warehouse.';
COMMENT ON COLUMN weapon_template.item_skills IS 'Skills the item gives while equipped or used. Format id-level;id-level; empty when none.';
COMMENT ON COLUMN weapon_template.enchant4_skills IS 'Skills the item gives while equipped at enchant level +4 or higher. Format id-level;id-level; empty when none.';
COMMENT ON COLUMN weapon_template.on_cast_skills IS 'Skills cast on the target with a chance when the wielder casts a magic skill. Format id-level-chance;...; empty when none.';
COMMENT ON COLUMN weapon_template.on_critical_skills IS 'Skills cast on the target with a chance on a critical hit. Format id-level-chance;...; empty when none.';
COMMENT ON COLUMN weapon_template.change_weapon_template_id IS 'Weapon this one turns into with the change-weapon skill (catalog.weapon_template); NULL when it cannot be changed.';
CREATE INDEX weapon_template_change_weapon_template_id_idx ON weapon_template (change_weapon_template_id);

CREATE UNLOGGED TABLE armor_template (
	id integer PRIMARY KEY,
	name text NOT NULL,
	body_part text NOT NULL DEFAULT 'none' CHECK (body_part IN ('shirt', 'lbracelet', 'rbracelet', 'talisman', 'chest', 'fullarmor', 'head', 'hair', 'face', 'hair2', 'dhair', 'hairall', 'underwear', 'back', 'neck', 'legs', 'feet', 'gloves', 'chest,legs', 'belt', 'rhand', 'lhand', 'lrhand', 'rear,lear', 'rfinger,lfinger', 'wolf', 'greatwolf', 'hatchling', 'strider', 'babypet', 'none')),
	armor_type text NOT NULL DEFAULT 'none' CHECK (armor_type IN ('none', 'light', 'heavy', 'magic', 'pet', 'sigil')),
	is_crystallizable boolean NOT NULL DEFAULT false,
	crystal_type text NOT NULL DEFAULT 'none' CHECK (crystal_type IN ('none', 'd', 'c', 'b', 'a', 's', 's80', 's84')),
	crystal_count integer NOT NULL DEFAULT 0 CHECK (crystal_count >= 0),
	material text NOT NULL DEFAULT 'wood' CHECK (material IN ('paper', 'wood', 'liquid', 'cloth', 'leather', 'horn', 'bone', 'bronze', 'fine_steel', 'cotton', 'mithril', 'silver', 'gold', 'adamantaite', 'steel', 'oriharukon', 'blood_steel', 'crystal', 'damascus', 'chrysolite', 'scale_of_dragon', 'dyestuff', 'cobweb', 'seed')),
	weight integer NOT NULL DEFAULT 0 CHECK (weight >= 0),
	physical_defense integer NOT NULL DEFAULT 0,
	magic_defense integer NOT NULL DEFAULT 0,
	evasion_modifier integer NOT NULL DEFAULT 0,
	mp_bonus integer NOT NULL DEFAULT 0,
	shadow_mana integer CHECK (shadow_mana > 0),
	lifetime_s integer CHECK (lifetime_s > 0),
	price bigint NOT NULL DEFAULT 0 CHECK (price >= 0),
	is_sellable boolean NOT NULL DEFAULT false,
	is_droppable boolean NOT NULL DEFAULT false,
	is_destroyable boolean NOT NULL DEFAULT true,
	is_tradable boolean NOT NULL DEFAULT false,
	is_depositable boolean NOT NULL DEFAULT true,
	item_skills text NOT NULL DEFAULT '',
	enchant4_skills text NOT NULL DEFAULT ''
);
COMMENT ON TABLE armor_template IS 'Armor templates (armor, jewels, accessories, sigils, pet armor). The item id is unique across the three item template tables.';
COMMENT ON COLUMN armor_template.name IS 'Item name for people.';
COMMENT ON COLUMN armor_template.body_part IS 'Equipment slot as ItemTable parses it (its _slots map); pairs such as ''rear,lear'' mean either of two slots, ''none'' means not equipped.';
COMMENT ON COLUMN armor_template.armor_type IS 'Armor type as ItemTable parses it (its _armorTypes map): none, light, heavy, magic, pet or sigil.';
COMMENT ON COLUMN armor_template.is_crystallizable IS 'Whether the item can be crystallized.';
COMMENT ON COLUMN armor_template.crystal_type IS 'Grade: none, d, c, b, a, s, s80 or s84 (ItemTable _crystalTypes).';
COMMENT ON COLUMN armor_template.crystal_count IS 'Number of crystals of the item grade that crystallizing the item gives.';
COMMENT ON COLUMN armor_template.material IS 'Material as ItemTable parses it (its _materials map).';
COMMENT ON COLUMN armor_template.weight IS 'Weight of one item in weight units.';
COMMENT ON COLUMN armor_template.physical_defense IS 'Physical defense.';
COMMENT ON COLUMN armor_template.magic_defense IS 'Magic defense (jewels).';
COMMENT ON COLUMN armor_template.evasion_modifier IS 'Evasion bonus (negative for a penalty).';
COMMENT ON COLUMN armor_template.mp_bonus IS 'Bonus to maximum MP while equipped.';
COMMENT ON COLUMN armor_template.shadow_mana IS 'Mana of a shadow item: the number of minutes it can be worn; one point is used per minute while equipped. NULL when the item is not a shadow item.';
COMMENT ON COLUMN armor_template.lifetime_s IS 'Lifetime of a time-limited item in seconds, counted from its creation. NULL when the item never expires.';
COMMENT ON COLUMN armor_template.price IS 'Reference price in adena (used by shops without an own price and when selling to an NPC).';
COMMENT ON COLUMN armor_template.is_sellable IS 'Whether the item can be sold to an NPC shop.';
COMMENT ON COLUMN armor_template.is_droppable IS 'Whether the item can be dropped on the ground.';
COMMENT ON COLUMN armor_template.is_destroyable IS 'Whether the player can destroy the item.';
COMMENT ON COLUMN armor_template.is_tradable IS 'Whether the item can be traded between players.';
COMMENT ON COLUMN armor_template.is_depositable IS 'Whether the item can be put into a warehouse.';
COMMENT ON COLUMN armor_template.item_skills IS 'Skills the item gives while equipped or used. Format id-level;id-level; empty when none.';
COMMENT ON COLUMN armor_template.enchant4_skills IS 'Skills the item gives while equipped at enchant level +4 or higher. Format id-level;id-level; empty when none.';

CREATE UNLOGGED TABLE etc_item_template (
	id integer PRIMARY KEY,
	name text NOT NULL,
	item_type text NOT NULL DEFAULT 'none' CHECK (item_type IN ('none', 'castle_guard', 'material', 'pet_collar', 'potion', 'recipe', 'scroll', 'seed', 'shot', 'spellbook', 'herb', 'arrow', 'bolt', 'quest', 'lure', 'lotto', 'race_ticket', 'dye', 'harvest', 'ticket_of_lord')),
	consume_type text NOT NULL DEFAULT 'normal' CHECK (consume_type IN ('normal', 'stackable', 'asset')),
	is_crystallizable boolean NOT NULL DEFAULT false,
	crystal_type text NOT NULL DEFAULT 'none' CHECK (crystal_type IN ('none', 'd', 'c', 'b', 'a', 's', 's80', 's84')),
	crystal_count integer NOT NULL DEFAULT 0 CHECK (crystal_count >= 0),
	material text NOT NULL DEFAULT 'wood' CHECK (material IN ('paper', 'wood', 'liquid', 'cloth', 'leather', 'horn', 'bone', 'bronze', 'fine_steel', 'cotton', 'mithril', 'silver', 'gold', 'adamantaite', 'steel', 'oriharukon', 'blood_steel', 'crystal', 'damascus', 'chrysolite', 'scale_of_dragon', 'dyestuff', 'cobweb', 'seed')),
	weight integer NOT NULL DEFAULT 0 CHECK (weight >= 0),
	shadow_mana integer CHECK (shadow_mana > 0),
	lifetime_s integer CHECK (lifetime_s > 0),
	price bigint NOT NULL DEFAULT 0 CHECK (price >= 0),
	is_sellable boolean NOT NULL DEFAULT false,
	is_droppable boolean NOT NULL DEFAULT false,
	is_destroyable boolean NOT NULL DEFAULT true,
	is_tradable boolean NOT NULL DEFAULT false,
	is_depositable boolean NOT NULL DEFAULT false,
	item_skills text NOT NULL DEFAULT '',
	handler_name text
);
COMMENT ON TABLE etc_item_template IS 'Templates of all other items (materials, potions, scrolls, quest items, money). The item id is unique across the three item template tables.';
COMMENT ON COLUMN etc_item_template.name IS 'Item name for people.';
COMMENT ON COLUMN etc_item_template.item_type IS 'Item type as ItemTable.readItem parses it; unknown values (lotto, race_ticket, dye, harvest, ticket_of_lord) load as L2EtcItemType.OTHER.';
COMMENT ON COLUMN etc_item_template.consume_type IS 'Stacking: normal (not stackable), stackable, or asset (money, stackable).';
COMMENT ON COLUMN etc_item_template.is_crystallizable IS 'Whether the item can be crystallized.';
COMMENT ON COLUMN etc_item_template.crystal_type IS 'Grade: none, d, c, b, a, s, s80 or s84 (ItemTable _crystalTypes).';
COMMENT ON COLUMN etc_item_template.crystal_count IS 'Number of crystals of the item grade that crystallizing the item gives.';
COMMENT ON COLUMN etc_item_template.material IS 'Material as ItemTable parses it (its _materials map).';
COMMENT ON COLUMN etc_item_template.weight IS 'Weight of one item in weight units.';
COMMENT ON COLUMN etc_item_template.shadow_mana IS 'Mana of a shadow item: the number of minutes it can be worn; one point is used per minute while equipped. NULL when the item is not a shadow item.';
COMMENT ON COLUMN etc_item_template.lifetime_s IS 'Lifetime of a time-limited item in seconds, counted from its creation. NULL when the item never expires.';
COMMENT ON COLUMN etc_item_template.price IS 'Reference price in adena (used by shops without an own price and when selling to an NPC).';
COMMENT ON COLUMN etc_item_template.is_sellable IS 'Whether the item can be sold to an NPC shop.';
COMMENT ON COLUMN etc_item_template.is_droppable IS 'Whether the item can be dropped on the ground.';
COMMENT ON COLUMN etc_item_template.is_destroyable IS 'Whether the player can destroy the item.';
COMMENT ON COLUMN etc_item_template.is_tradable IS 'Whether the item can be traded between players.';
COMMENT ON COLUMN etc_item_template.is_depositable IS 'Whether the item can be put into a warehouse.';
COMMENT ON COLUMN etc_item_template.item_skills IS 'Skills used when the item is used. Format id-level;id-level; empty when none.';
COMMENT ON COLUMN etc_item_template.handler_name IS 'Name of the item handler class that handles using the item, for example Potions; NULL when the item has no handler.';

-- Custom item templates: operators add their own items here. A custom row with the id of a
-- standard template replaces it. Same columns as the standard tables plus display_item_id.

CREATE UNLOGGED TABLE custom_weapon_template (
	id integer PRIMARY KEY,
	display_item_id integer NOT NULL,
	name text NOT NULL,
	body_part text NOT NULL DEFAULT 'none' CHECK (body_part IN ('shirt', 'lbracelet', 'rbracelet', 'talisman', 'chest', 'fullarmor', 'head', 'hair', 'face', 'hair2', 'dhair', 'hairall', 'underwear', 'back', 'neck', 'legs', 'feet', 'gloves', 'chest,legs', 'belt', 'rhand', 'lhand', 'lrhand', 'rear,lear', 'rfinger,lfinger', 'wolf', 'greatwolf', 'hatchling', 'strider', 'babypet', 'none')),
	weapon_type text NOT NULL DEFAULT 'none' CHECK (weapon_type IN ('blunt', 'bow', 'dagger', 'dual', 'dualfist', 'etc', 'fist', 'none', 'pole', 'sword', 'bigsword', 'pet', 'rod', 'bigblunt', 'crossbow', 'rapier', 'ancient', 'dualdagger')),
	is_crystallizable boolean NOT NULL DEFAULT false,
	crystal_type text NOT NULL DEFAULT 'none' CHECK (crystal_type IN ('none', 'd', 'c', 'b', 'a', 's', 's80', 's84')),
	crystal_count integer NOT NULL DEFAULT 0 CHECK (crystal_count >= 0),
	material text NOT NULL DEFAULT 'wood' CHECK (material IN ('paper', 'wood', 'liquid', 'cloth', 'leather', 'horn', 'bone', 'bronze', 'fine_steel', 'cotton', 'mithril', 'silver', 'gold', 'adamantaite', 'steel', 'oriharukon', 'blood_steel', 'crystal', 'damascus', 'chrysolite', 'scale_of_dragon', 'dyestuff', 'cobweb', 'seed')),
	weight integer NOT NULL DEFAULT 0 CHECK (weight >= 0),
	soulshot_count smallint NOT NULL DEFAULT 0 CHECK (soulshot_count >= 0),
	spiritshot_count smallint NOT NULL DEFAULT 0 CHECK (spiritshot_count >= 0),
	physical_damage integer NOT NULL DEFAULT 0,
	random_damage integer NOT NULL DEFAULT 0,
	magic_damage integer NOT NULL DEFAULT 0,
	critical_rate integer NOT NULL DEFAULT 0,
	accuracy_modifier integer NOT NULL DEFAULT 0,
	evasion_modifier integer NOT NULL DEFAULT 0,
	shield_defense integer NOT NULL DEFAULT 0,
	shield_defense_rate integer NOT NULL DEFAULT 0,
	attack_speed integer NOT NULL DEFAULT 0,
	mp_consumption integer NOT NULL DEFAULT 0 CHECK (mp_consumption >= 0),
	shadow_mana integer CHECK (shadow_mana > 0),
	lifetime_s integer CHECK (lifetime_s > 0),
	price bigint NOT NULL DEFAULT 0 CHECK (price >= 0),
	is_sellable boolean NOT NULL DEFAULT false,
	is_droppable boolean NOT NULL DEFAULT false,
	is_destroyable boolean NOT NULL DEFAULT true,
	is_tradable boolean NOT NULL DEFAULT false,
	is_depositable boolean NOT NULL DEFAULT false,
	item_skills text NOT NULL DEFAULT '',
	enchant4_skills text NOT NULL DEFAULT '',
	on_cast_skills text NOT NULL DEFAULT '',
	on_critical_skills text NOT NULL DEFAULT '',
	change_weapon_template_id integer
);
COMMENT ON TABLE custom_weapon_template IS 'Operator-defined weapon templates; same columns as weapon_template plus display_item_id. A row replaces a standard template with the same id.';
COMMENT ON COLUMN custom_weapon_template.display_item_id IS 'Item id of an existing client item whose icon and name the client shows for this custom item.';
COMMENT ON COLUMN custom_weapon_template.name IS 'Item name for people.';
COMMENT ON COLUMN custom_weapon_template.body_part IS 'Equipment slot as ItemTable parses it (its _slots map); pairs such as ''rear,lear'' mean either of two slots, ''none'' means not equipped.';
COMMENT ON COLUMN custom_weapon_template.weapon_type IS 'Weapon type as ItemTable parses it (its _weaponTypes map, for example bigsword = L2WeaponType.BIGSWORD); none is a shield.';
COMMENT ON COLUMN custom_weapon_template.is_crystallizable IS 'Whether the item can be crystallized.';
COMMENT ON COLUMN custom_weapon_template.crystal_type IS 'Grade: none, d, c, b, a, s, s80 or s84 (ItemTable _crystalTypes).';
COMMENT ON COLUMN custom_weapon_template.crystal_count IS 'Number of crystals of the item grade that crystallizing the item gives.';
COMMENT ON COLUMN custom_weapon_template.material IS 'Material as ItemTable parses it (its _materials map).';
COMMENT ON COLUMN custom_weapon_template.weight IS 'Weight of one item in weight units.';
COMMENT ON COLUMN custom_weapon_template.soulshot_count IS 'Soulshots consumed per shot.';
COMMENT ON COLUMN custom_weapon_template.spiritshot_count IS 'Spiritshots consumed per shot.';
COMMENT ON COLUMN custom_weapon_template.physical_damage IS 'Physical attack of the weapon.';
COMMENT ON COLUMN custom_weapon_template.random_damage IS 'Random damage spread in percent of the physical attack.';
COMMENT ON COLUMN custom_weapon_template.magic_damage IS 'Magic attack of the weapon.';
COMMENT ON COLUMN custom_weapon_template.critical_rate IS 'Critical rate of the weapon.';
COMMENT ON COLUMN custom_weapon_template.accuracy_modifier IS 'Accuracy bonus (negative for a penalty).';
COMMENT ON COLUMN custom_weapon_template.evasion_modifier IS 'Evasion bonus (negative for a penalty).';
COMMENT ON COLUMN custom_weapon_template.shield_defense IS 'Shield defense; non-zero for shields only.';
COMMENT ON COLUMN custom_weapon_template.shield_defense_rate IS 'Shield block rate in percent; non-zero for shields only.';
COMMENT ON COLUMN custom_weapon_template.attack_speed IS 'Attack speed of the weapon.';
COMMENT ON COLUMN custom_weapon_template.mp_consumption IS 'MP consumed per attack (bows and some magic weapons).';
COMMENT ON COLUMN custom_weapon_template.shadow_mana IS 'Mana of a shadow item: the number of minutes it can be worn; one point is used per minute while equipped. NULL when the item is not a shadow item.';
COMMENT ON COLUMN custom_weapon_template.lifetime_s IS 'Lifetime of a time-limited item in seconds, counted from its creation. NULL when the item never expires.';
COMMENT ON COLUMN custom_weapon_template.price IS 'Reference price in adena (used by shops without an own price and when selling to an NPC).';
COMMENT ON COLUMN custom_weapon_template.is_sellable IS 'Whether the item can be sold to an NPC shop.';
COMMENT ON COLUMN custom_weapon_template.is_droppable IS 'Whether the item can be dropped on the ground.';
COMMENT ON COLUMN custom_weapon_template.is_destroyable IS 'Whether the player can destroy the item.';
COMMENT ON COLUMN custom_weapon_template.is_tradable IS 'Whether the item can be traded between players.';
COMMENT ON COLUMN custom_weapon_template.is_depositable IS 'Whether the item can be put into a warehouse.';
COMMENT ON COLUMN custom_weapon_template.item_skills IS 'Skills the item gives while equipped or used. Format id-level;id-level; empty when none.';
COMMENT ON COLUMN custom_weapon_template.enchant4_skills IS 'Skills the item gives while equipped at enchant level +4 or higher. Format id-level;id-level; empty when none.';
COMMENT ON COLUMN custom_weapon_template.on_cast_skills IS 'Skills cast on the target with a chance when the wielder casts a magic skill. Format id-level-chance;...; empty when none.';
COMMENT ON COLUMN custom_weapon_template.on_critical_skills IS 'Skills cast on the target with a chance on a critical hit. Format id-level-chance;...; empty when none.';
COMMENT ON COLUMN custom_weapon_template.change_weapon_template_id IS 'Weapon this one turns into with the change-weapon skill (catalog tables weapon_template, custom_weapon_template); NULL when it cannot be changed.';

CREATE UNLOGGED TABLE custom_armor_template (
	id integer PRIMARY KEY,
	display_item_id integer NOT NULL,
	name text NOT NULL,
	body_part text NOT NULL DEFAULT 'none' CHECK (body_part IN ('shirt', 'lbracelet', 'rbracelet', 'talisman', 'chest', 'fullarmor', 'head', 'hair', 'face', 'hair2', 'dhair', 'hairall', 'underwear', 'back', 'neck', 'legs', 'feet', 'gloves', 'chest,legs', 'belt', 'rhand', 'lhand', 'lrhand', 'rear,lear', 'rfinger,lfinger', 'wolf', 'greatwolf', 'hatchling', 'strider', 'babypet', 'none')),
	armor_type text NOT NULL DEFAULT 'none' CHECK (armor_type IN ('none', 'light', 'heavy', 'magic', 'pet', 'sigil')),
	is_crystallizable boolean NOT NULL DEFAULT false,
	crystal_type text NOT NULL DEFAULT 'none' CHECK (crystal_type IN ('none', 'd', 'c', 'b', 'a', 's', 's80', 's84')),
	crystal_count integer NOT NULL DEFAULT 0 CHECK (crystal_count >= 0),
	material text NOT NULL DEFAULT 'wood' CHECK (material IN ('paper', 'wood', 'liquid', 'cloth', 'leather', 'horn', 'bone', 'bronze', 'fine_steel', 'cotton', 'mithril', 'silver', 'gold', 'adamantaite', 'steel', 'oriharukon', 'blood_steel', 'crystal', 'damascus', 'chrysolite', 'scale_of_dragon', 'dyestuff', 'cobweb', 'seed')),
	weight integer NOT NULL DEFAULT 0 CHECK (weight >= 0),
	physical_defense integer NOT NULL DEFAULT 0,
	magic_defense integer NOT NULL DEFAULT 0,
	evasion_modifier integer NOT NULL DEFAULT 0,
	mp_bonus integer NOT NULL DEFAULT 0,
	shadow_mana integer CHECK (shadow_mana > 0),
	lifetime_s integer CHECK (lifetime_s > 0),
	price bigint NOT NULL DEFAULT 0 CHECK (price >= 0),
	is_sellable boolean NOT NULL DEFAULT false,
	is_droppable boolean NOT NULL DEFAULT false,
	is_destroyable boolean NOT NULL DEFAULT true,
	is_tradable boolean NOT NULL DEFAULT false,
	is_depositable boolean NOT NULL DEFAULT true,
	item_skills text NOT NULL DEFAULT '',
	enchant4_skills text NOT NULL DEFAULT ''
);
COMMENT ON TABLE custom_armor_template IS 'Operator-defined armor templates; same columns as armor_template plus display_item_id. A row replaces a standard template with the same id.';
COMMENT ON COLUMN custom_armor_template.display_item_id IS 'Item id of an existing client item whose icon and name the client shows for this custom item.';
COMMENT ON COLUMN custom_armor_template.name IS 'Item name for people.';
COMMENT ON COLUMN custom_armor_template.body_part IS 'Equipment slot as ItemTable parses it (its _slots map); pairs such as ''rear,lear'' mean either of two slots, ''none'' means not equipped.';
COMMENT ON COLUMN custom_armor_template.armor_type IS 'Armor type as ItemTable parses it (its _armorTypes map): none, light, heavy, magic, pet or sigil.';
COMMENT ON COLUMN custom_armor_template.is_crystallizable IS 'Whether the item can be crystallized.';
COMMENT ON COLUMN custom_armor_template.crystal_type IS 'Grade: none, d, c, b, a, s, s80 or s84 (ItemTable _crystalTypes).';
COMMENT ON COLUMN custom_armor_template.crystal_count IS 'Number of crystals of the item grade that crystallizing the item gives.';
COMMENT ON COLUMN custom_armor_template.material IS 'Material as ItemTable parses it (its _materials map).';
COMMENT ON COLUMN custom_armor_template.weight IS 'Weight of one item in weight units.';
COMMENT ON COLUMN custom_armor_template.physical_defense IS 'Physical defense.';
COMMENT ON COLUMN custom_armor_template.magic_defense IS 'Magic defense (jewels).';
COMMENT ON COLUMN custom_armor_template.evasion_modifier IS 'Evasion bonus (negative for a penalty).';
COMMENT ON COLUMN custom_armor_template.mp_bonus IS 'Bonus to maximum MP while equipped.';
COMMENT ON COLUMN custom_armor_template.shadow_mana IS 'Mana of a shadow item: the number of minutes it can be worn; one point is used per minute while equipped. NULL when the item is not a shadow item.';
COMMENT ON COLUMN custom_armor_template.lifetime_s IS 'Lifetime of a time-limited item in seconds, counted from its creation. NULL when the item never expires.';
COMMENT ON COLUMN custom_armor_template.price IS 'Reference price in adena (used by shops without an own price and when selling to an NPC).';
COMMENT ON COLUMN custom_armor_template.is_sellable IS 'Whether the item can be sold to an NPC shop.';
COMMENT ON COLUMN custom_armor_template.is_droppable IS 'Whether the item can be dropped on the ground.';
COMMENT ON COLUMN custom_armor_template.is_destroyable IS 'Whether the player can destroy the item.';
COMMENT ON COLUMN custom_armor_template.is_tradable IS 'Whether the item can be traded between players.';
COMMENT ON COLUMN custom_armor_template.is_depositable IS 'Whether the item can be put into a warehouse.';
COMMENT ON COLUMN custom_armor_template.item_skills IS 'Skills the item gives while equipped or used. Format id-level;id-level; empty when none.';
COMMENT ON COLUMN custom_armor_template.enchant4_skills IS 'Skills the item gives while equipped at enchant level +4 or higher. Format id-level;id-level; empty when none.';

CREATE UNLOGGED TABLE custom_etc_item_template (
	id integer PRIMARY KEY,
	display_item_id integer NOT NULL,
	name text NOT NULL,
	item_type text NOT NULL DEFAULT 'none' CHECK (item_type IN ('none', 'castle_guard', 'material', 'pet_collar', 'potion', 'recipe', 'scroll', 'seed', 'shot', 'spellbook', 'herb', 'arrow', 'bolt', 'quest', 'lure', 'lotto', 'race_ticket', 'dye', 'harvest', 'ticket_of_lord')),
	consume_type text NOT NULL DEFAULT 'normal' CHECK (consume_type IN ('normal', 'stackable', 'asset')),
	is_crystallizable boolean NOT NULL DEFAULT false,
	crystal_type text NOT NULL DEFAULT 'none' CHECK (crystal_type IN ('none', 'd', 'c', 'b', 'a', 's', 's80', 's84')),
	crystal_count integer NOT NULL DEFAULT 0 CHECK (crystal_count >= 0),
	material text NOT NULL DEFAULT 'wood' CHECK (material IN ('paper', 'wood', 'liquid', 'cloth', 'leather', 'horn', 'bone', 'bronze', 'fine_steel', 'cotton', 'mithril', 'silver', 'gold', 'adamantaite', 'steel', 'oriharukon', 'blood_steel', 'crystal', 'damascus', 'chrysolite', 'scale_of_dragon', 'dyestuff', 'cobweb', 'seed')),
	weight integer NOT NULL DEFAULT 0 CHECK (weight >= 0),
	shadow_mana integer CHECK (shadow_mana > 0),
	lifetime_s integer CHECK (lifetime_s > 0),
	price bigint NOT NULL DEFAULT 0 CHECK (price >= 0),
	is_sellable boolean NOT NULL DEFAULT false,
	is_droppable boolean NOT NULL DEFAULT false,
	is_destroyable boolean NOT NULL DEFAULT true,
	is_tradable boolean NOT NULL DEFAULT false,
	is_depositable boolean NOT NULL DEFAULT false,
	item_skills text NOT NULL DEFAULT '',
	handler_name text
);
COMMENT ON TABLE custom_etc_item_template IS 'Operator-defined templates of other items; same columns as etc_item_template plus display_item_id. A row replaces a standard template with the same id.';
COMMENT ON COLUMN custom_etc_item_template.display_item_id IS 'Item id of an existing client item whose icon and name the client shows for this custom item.';
COMMENT ON COLUMN custom_etc_item_template.name IS 'Item name for people.';
COMMENT ON COLUMN custom_etc_item_template.item_type IS 'Item type as ItemTable.readItem parses it; unknown values (lotto, race_ticket, dye, harvest, ticket_of_lord) load as L2EtcItemType.OTHER.';
COMMENT ON COLUMN custom_etc_item_template.consume_type IS 'Stacking: normal (not stackable), stackable, or asset (money, stackable).';
COMMENT ON COLUMN custom_etc_item_template.is_crystallizable IS 'Whether the item can be crystallized.';
COMMENT ON COLUMN custom_etc_item_template.crystal_type IS 'Grade: none, d, c, b, a, s, s80 or s84 (ItemTable _crystalTypes).';
COMMENT ON COLUMN custom_etc_item_template.crystal_count IS 'Number of crystals of the item grade that crystallizing the item gives.';
COMMENT ON COLUMN custom_etc_item_template.material IS 'Material as ItemTable parses it (its _materials map).';
COMMENT ON COLUMN custom_etc_item_template.weight IS 'Weight of one item in weight units.';
COMMENT ON COLUMN custom_etc_item_template.shadow_mana IS 'Mana of a shadow item: the number of minutes it can be worn; one point is used per minute while equipped. NULL when the item is not a shadow item.';
COMMENT ON COLUMN custom_etc_item_template.lifetime_s IS 'Lifetime of a time-limited item in seconds, counted from its creation. NULL when the item never expires.';
COMMENT ON COLUMN custom_etc_item_template.price IS 'Reference price in adena (used by shops without an own price and when selling to an NPC).';
COMMENT ON COLUMN custom_etc_item_template.is_sellable IS 'Whether the item can be sold to an NPC shop.';
COMMENT ON COLUMN custom_etc_item_template.is_droppable IS 'Whether the item can be dropped on the ground.';
COMMENT ON COLUMN custom_etc_item_template.is_destroyable IS 'Whether the player can destroy the item.';
COMMENT ON COLUMN custom_etc_item_template.is_tradable IS 'Whether the item can be traded between players.';
COMMENT ON COLUMN custom_etc_item_template.is_depositable IS 'Whether the item can be put into a warehouse.';
COMMENT ON COLUMN custom_etc_item_template.item_skills IS 'Skills used when the item is used. Format id-level;id-level; empty when none.';
COMMENT ON COLUMN custom_etc_item_template.handler_name IS 'Name of the item handler class that handles using the item, for example Potions; NULL when the item has no handler.';

-- Armor sets: wearing all parts gives the set skills.

CREATE UNLOGGED TABLE armor_set (
	chest_armor_template_id integer PRIMARY KEY REFERENCES armor_template,
	legs_armor_template_id integer REFERENCES armor_template,
	head_armor_template_id integer REFERENCES armor_template,
	gloves_armor_template_id integer REFERENCES armor_template,
	feet_armor_template_id integer REFERENCES armor_template,
	masterwork_legs_armor_template_id integer REFERENCES armor_template,
	masterwork_head_armor_template_id integer REFERENCES armor_template,
	masterwork_gloves_armor_template_id integer REFERENCES armor_template,
	masterwork_feet_armor_template_id integer REFERENCES armor_template,
	set_skills text NOT NULL DEFAULT '',
	shield_weapon_template_id integer REFERENCES weapon_template,
	masterwork_shield_weapon_template_id integer REFERENCES weapon_template,
	shield_skill_id integer,
	enchant6_skill_id integer
);
COMMENT ON TABLE armor_set IS 'Armor sets, keyed by the chest piece. When all set parts are worn the set skills apply.';
COMMENT ON COLUMN armor_set.chest_armor_template_id IS 'Chest piece of the set (catalog.armor_template); identifies the set.';
COMMENT ON COLUMN armor_set.legs_armor_template_id IS 'Legs piece (catalog.armor_template); NULL when the set has none (full armor).';
COMMENT ON COLUMN armor_set.head_armor_template_id IS 'Helmet (catalog.armor_template); NULL when the set has none.';
COMMENT ON COLUMN armor_set.gloves_armor_template_id IS 'Gloves (catalog.armor_template); NULL when the set has none.';
COMMENT ON COLUMN armor_set.feet_armor_template_id IS 'Boots (catalog.armor_template); NULL when the set has none.';
COMMENT ON COLUMN armor_set.masterwork_legs_armor_template_id IS 'Masterwork legs piece accepted instead of the normal one (catalog.armor_template); NULL when none.';
COMMENT ON COLUMN armor_set.masterwork_head_armor_template_id IS 'Masterwork helmet accepted instead of the normal one (catalog.armor_template); NULL when none.';
COMMENT ON COLUMN armor_set.masterwork_gloves_armor_template_id IS 'Masterwork gloves accepted instead of the normal ones (catalog.armor_template); NULL when none.';
COMMENT ON COLUMN armor_set.masterwork_feet_armor_template_id IS 'Masterwork boots accepted instead of the normal ones (catalog.armor_template); NULL when none.';
COMMENT ON COLUMN armor_set.set_skills IS 'Skills of the complete set. Format id-level;id-level; the entry 0-0 or an empty string means none.';
COMMENT ON COLUMN armor_set.shield_weapon_template_id IS 'Shield that adds the shield skill (catalog.weapon_template, shields are weapons); NULL when the set has no shield.';
COMMENT ON COLUMN armor_set.masterwork_shield_weapon_template_id IS 'Masterwork shield accepted instead of the normal one (catalog.weapon_template); NULL when none.';
COMMENT ON COLUMN armor_set.shield_skill_id IS 'Skill (level 1, from data/stats/skills) added when the set shield is also worn; NULL when none.';
COMMENT ON COLUMN armor_set.enchant6_skill_id IS 'Skill (level 1, from data/stats/skills) added when every set part is enchanted to +6 or higher; NULL when none.';
CREATE INDEX armor_set_legs_armor_template_id_idx ON armor_set (legs_armor_template_id);
CREATE INDEX armor_set_head_armor_template_id_idx ON armor_set (head_armor_template_id);
CREATE INDEX armor_set_gloves_armor_template_id_idx ON armor_set (gloves_armor_template_id);
CREATE INDEX armor_set_feet_armor_template_id_idx ON armor_set (feet_armor_template_id);
CREATE INDEX armor_set_masterwork_legs_armor_template_id_idx ON armor_set (masterwork_legs_armor_template_id);
CREATE INDEX armor_set_masterwork_head_armor_template_id_idx ON armor_set (masterwork_head_armor_template_id);
CREATE INDEX armor_set_masterwork_gloves_armor_template_id_idx ON armor_set (masterwork_gloves_armor_template_id);
CREATE INDEX armor_set_masterwork_feet_armor_template_id_idx ON armor_set (masterwork_feet_armor_template_id);
CREATE INDEX armor_set_shield_weapon_template_id_idx ON armor_set (shield_weapon_template_id);
CREATE INDEX armor_set_masterwork_shield_weapon_template_id_idx ON armor_set (masterwork_shield_weapon_template_id);

-- Player classes: base stats, level-up gains and starting items.
-- The class list itself is world.player_class (V1_01).

CREATE UNLOGGED TABLE player_template (
	player_class_id smallint PRIMARY KEY REFERENCES world.player_class,
	class_name text NOT NULL,
	base_strength smallint NOT NULL,
	base_constitution smallint NOT NULL,
	base_dexterity smallint NOT NULL,
	base_intelligence smallint NOT NULL,
	base_wit smallint NOT NULL,
	base_mental smallint NOT NULL,
	base_physical_attack integer NOT NULL,
	base_physical_defense integer NOT NULL,
	base_magic_attack integer NOT NULL,
	base_magic_defense integer NOT NULL,
	base_attack_speed integer NOT NULL,
	base_casting_speed integer NOT NULL,
	base_accuracy integer NOT NULL,
	base_critical_rate integer NOT NULL,
	base_evasion integer NOT NULL,
	base_run_speed integer NOT NULL,
	base_max_load integer NOT NULL CHECK (base_max_load >= 0),
	can_craft boolean NOT NULL DEFAULT false,
	male_collision_radius numeric(3,1) NOT NULL,
	male_collision_height numeric(4,1) NOT NULL,
	female_collision_radius numeric(3,1) NOT NULL,
	female_collision_height numeric(4,1) NOT NULL
);
COMMENT ON TABLE player_template IS 'Base stats of a player class (L2PlayerTemplate). The race of the class is world.player_class.race_id.';
COMMENT ON COLUMN player_template.player_class_id IS 'Player class (world.player_class).';
COMMENT ON COLUMN player_template.class_name IS 'Class name as the template shows it, for example Human Fighter; world.player_class.name is the short name.';
COMMENT ON COLUMN player_template.base_strength IS 'Base STR (strength).';
COMMENT ON COLUMN player_template.base_constitution IS 'Base CON (constitution).';
COMMENT ON COLUMN player_template.base_dexterity IS 'Base DEX (dexterity).';
COMMENT ON COLUMN player_template.base_intelligence IS 'Base INT (intelligence).';
COMMENT ON COLUMN player_template.base_wit IS 'Base WIT (wit).';
COMMENT ON COLUMN player_template.base_mental IS 'Base MEN (mental strength).';
COMMENT ON COLUMN player_template.base_physical_attack IS 'Base physical attack without equipment.';
COMMENT ON COLUMN player_template.base_physical_defense IS 'Base physical defense without equipment.';
COMMENT ON COLUMN player_template.base_magic_attack IS 'Base magic attack without equipment.';
COMMENT ON COLUMN player_template.base_magic_defense IS 'Base magic defense without equipment.';
COMMENT ON COLUMN player_template.base_attack_speed IS 'Base physical attack speed.';
COMMENT ON COLUMN player_template.base_casting_speed IS 'Base casting (magic attack) speed.';
COMMENT ON COLUMN player_template.base_accuracy IS 'Base accuracy. Not read by the current code.';
COMMENT ON COLUMN player_template.base_critical_rate IS 'Base critical rate in tenths; the code divides it by 10.';
COMMENT ON COLUMN player_template.base_evasion IS 'Base evasion. Not read by the current code.';
COMMENT ON COLUMN player_template.base_run_speed IS 'Base run speed before Config.RATE_RUN_SPEED is applied.';
COMMENT ON COLUMN player_template.base_max_load IS 'Base weight limit in weight units. Not read by the current code.';
COMMENT ON COLUMN player_template.can_craft IS 'Whether the class can craft (dwarves). Not read by the current code.';
COMMENT ON COLUMN player_template.male_collision_radius IS 'Collision radius of a male character.';
COMMENT ON COLUMN player_template.male_collision_height IS 'Collision height of a male character.';
COMMENT ON COLUMN player_template.female_collision_radius IS 'Collision radius of a female character.';
COMMENT ON COLUMN player_template.female_collision_height IS 'Collision height of a female character.';

CREATE UNLOGGED TABLE level_up_gain (
	player_class_id smallint PRIMARY KEY REFERENCES world.player_class,
	class_base_level smallint NOT NULL CHECK (class_base_level > 0),
	base_hp_max numeric(5,1) NOT NULL,
	hp_per_level numeric(4,2) NOT NULL,
	hp_per_level_increment numeric(4,2) NOT NULL,
	base_cp_max numeric(5,1) NOT NULL,
	cp_per_level numeric(4,2) NOT NULL,
	cp_per_level_increment numeric(4,2) NOT NULL,
	base_mp_max numeric(5,1) NOT NULL,
	mp_per_level numeric(4,2) NOT NULL,
	mp_per_level_increment numeric(4,2) NOT NULL
);
COMMENT ON TABLE level_up_gain IS 'Maximum HP, CP and MP of a player class and how they grow per level (Formulas.FuncMaxHpAdd and its CP/MP twins).';
COMMENT ON COLUMN level_up_gain.player_class_id IS 'Player class (world.player_class).';
COMMENT ON COLUMN level_up_gain.class_base_level IS 'Level at which the class starts (1, 20, 40 or 76); growth counts from this level.';
COMMENT ON COLUMN level_up_gain.base_hp_max IS 'Maximum HP at the class base level.';
COMMENT ON COLUMN level_up_gain.hp_per_level IS 'Maximum HP added per level above the base level.';
COMMENT ON COLUMN level_up_gain.hp_per_level_increment IS 'Amount by which hp_per_level grows with every further level.';
COMMENT ON COLUMN level_up_gain.base_cp_max IS 'Maximum CP at the class base level.';
COMMENT ON COLUMN level_up_gain.cp_per_level IS 'Maximum CP added per level above the base level.';
COMMENT ON COLUMN level_up_gain.cp_per_level_increment IS 'Amount by which cp_per_level grows with every further level.';
COMMENT ON COLUMN level_up_gain.base_mp_max IS 'Maximum MP at the class base level.';
COMMENT ON COLUMN level_up_gain.mp_per_level IS 'Maximum MP added per level above the base level.';
COMMENT ON COLUMN level_up_gain.mp_per_level_increment IS 'Amount by which mp_per_level grows with every further level.';

CREATE UNLOGGED TABLE starting_item (
	id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	player_class_id smallint REFERENCES world.player_class,
	item_template_id integer NOT NULL,
	amount integer NOT NULL DEFAULT 1 CHECK (amount > 0),
	is_equipped boolean NOT NULL DEFAULT false,
	UNIQUE NULLS NOT DISTINCT (player_class_id, item_template_id)
);
COMMENT ON TABLE starting_item IS 'Items a new character receives on creation.';
COMMENT ON COLUMN starting_item.player_class_id IS 'Starting class that receives the item (world.player_class); NULL means every class.';
COMMENT ON COLUMN starting_item.item_template_id IS 'Item template (catalog tables weapon_template, armor_template, etc_item_template).';
COMMENT ON COLUMN starting_item.amount IS 'Number of items given.';
COMMENT ON COLUMN starting_item.is_equipped IS 'Whether the item is equipped on the new character.';

-- Skill trees: which skills a player, clan or pet can learn and what it costs.

CREATE UNLOGGED TABLE skill_tree (
	player_class_id smallint NOT NULL REFERENCES world.player_class,
	skill_id integer NOT NULL,
	skill_level smallint NOT NULL CHECK (skill_level > 0),
	skill_name text NOT NULL,
	sp integer NOT NULL DEFAULT 0 CHECK (sp >= 0),
	min_level smallint NOT NULL CHECK (min_level > 0),
	PRIMARY KEY (player_class_id, skill_id, skill_level)
);
COMMENT ON TABLE skill_tree IS 'Skills a class learns from a trainer, one row per skill level. A class also learns the skills of its parent classes.';
COMMENT ON COLUMN skill_tree.player_class_id IS 'Class that learns the skill (world.player_class).';
COMMENT ON COLUMN skill_tree.skill_id IS 'Skill (skill XML files).';
COMMENT ON COLUMN skill_tree.skill_level IS 'Skill level learned.';
COMMENT ON COLUMN skill_tree.skill_name IS 'Skill name for people.';
COMMENT ON COLUMN skill_tree.sp IS 'SP the player pays.';
COMMENT ON COLUMN skill_tree.min_level IS 'Minimum player level.';

CREATE UNLOGGED TABLE skill_trainer_class (
	npc_template_id integer NOT NULL,
	player_class_id smallint NOT NULL REFERENCES world.player_class,
	PRIMARY KEY (npc_template_id, player_class_id)
);
COMMENT ON TABLE skill_trainer_class IS 'Classes a skill trainer NPC teaches.';
COMMENT ON COLUMN skill_trainer_class.npc_template_id IS 'Trainer NPC template (catalog.npc_template).';
COMMENT ON COLUMN skill_trainer_class.player_class_id IS 'Class the trainer teaches (world.player_class).';
CREATE INDEX skill_trainer_class_player_class_id_idx ON skill_trainer_class (player_class_id);

CREATE UNLOGGED TABLE skill_spellbook (
	skill_id integer PRIMARY KEY,
	item_template_id integer NOT NULL REFERENCES etc_item_template
);
COMMENT ON TABLE skill_spellbook IS 'Spellbook a player needs to learn a skill (only when Config.ALT_SP_BOOK_NEEDED is on).';
COMMENT ON COLUMN skill_spellbook.skill_id IS 'Skill (skill XML files).';
COMMENT ON COLUMN skill_spellbook.item_template_id IS 'Spellbook item consumed when learning the skill (catalog.etc_item_template).';
CREATE INDEX skill_spellbook_item_template_id_idx ON skill_spellbook (item_template_id);

CREATE UNLOGGED TABLE castle_skill (
	castle_id smallint NOT NULL REFERENCES world.castle,
	skill_id integer NOT NULL,
	skill_level smallint NOT NULL CHECK (skill_level > 0),
	PRIMARY KEY (castle_id, skill_id)
);
COMMENT ON TABLE castle_skill IS 'Skills the members of the clan owning a castle receive.';
COMMENT ON COLUMN castle_skill.castle_id IS 'Castle that grants the skill.';
COMMENT ON COLUMN castle_skill.skill_id IS 'Skill (skill XML files in data/stats/skills).';
COMMENT ON COLUMN castle_skill.skill_level IS 'Skill level given.';

CREATE UNLOGGED TABLE fort_skill (
	fort_id smallint NOT NULL REFERENCES world.fort,
	skill_id integer NOT NULL,
	skill_level smallint NOT NULL CHECK (skill_level > 0),
	PRIMARY KEY (fort_id, skill_id)
);
COMMENT ON TABLE fort_skill IS 'Skills the members of the clan owning a fort receive.';
COMMENT ON COLUMN fort_skill.fort_id IS 'Fort that grants the skill.';
COMMENT ON COLUMN fort_skill.skill_id IS 'Skill (skill XML files in data/stats/skills).';
COMMENT ON COLUMN fort_skill.skill_level IS 'Skill level given.';

CREATE UNLOGGED TABLE certification_skill_tree (
	skill_id integer NOT NULL,
	skill_level smallint NOT NULL CHECK (skill_level > 0),
	skill_name text NOT NULL,
	required_item_template_id integer NOT NULL REFERENCES etc_item_template,
	PRIMARY KEY (skill_id, skill_level)
);
COMMENT ON TABLE certification_skill_tree IS 'Sub-class certification skills and the certificate each one needs.';
COMMENT ON COLUMN certification_skill_tree.skill_id IS 'Skill (skill XML files).';
COMMENT ON COLUMN certification_skill_tree.skill_level IS 'Skill level learned.';
COMMENT ON COLUMN certification_skill_tree.skill_name IS 'Skill name for people.';
COMMENT ON COLUMN certification_skill_tree.required_item_template_id IS 'Certificate item needed to learn the skill (catalog.etc_item_template).';
CREATE INDEX certification_skill_tree_required_item_template_id_idx ON certification_skill_tree (required_item_template_id);

CREATE UNLOGGED TABLE enchant_skill_tree (
	skill_id integer NOT NULL,
	level smallint NOT NULL CHECK (level > 100),
	base_level smallint NOT NULL CHECK (base_level > 0),
	min_skill_level smallint NOT NULL CHECK (min_skill_level > 0),
	sp integer NOT NULL CHECK (sp >= 0),
	exp bigint NOT NULL CHECK (exp >= 0),
	success_rate_76 smallint NOT NULL CHECK (success_rate_76 BETWEEN 0 AND 100),
	success_rate_77 smallint NOT NULL CHECK (success_rate_77 BETWEEN 0 AND 100),
	success_rate_78 smallint NOT NULL CHECK (success_rate_78 BETWEEN 0 AND 100),
	success_rate_79 smallint NOT NULL CHECK (success_rate_79 BETWEEN 0 AND 100),
	success_rate_80 smallint NOT NULL CHECK (success_rate_80 BETWEEN 0 AND 100),
	success_rate_81 smallint NOT NULL CHECK (success_rate_81 BETWEEN 0 AND 100),
	success_rate_82 smallint NOT NULL CHECK (success_rate_82 BETWEEN 0 AND 100),
	success_rate_83 smallint NOT NULL CHECK (success_rate_83 BETWEEN 0 AND 100),
	success_rate_84 smallint NOT NULL CHECK (success_rate_84 BETWEEN 0 AND 100),
	success_rate_85 smallint NOT NULL CHECK (success_rate_85 BETWEEN 0 AND 100),
	PRIMARY KEY (skill_id, level)
);
COMMENT ON TABLE enchant_skill_tree IS 'Skill enchanting (level 76 and up): cost and success chance of each enchant step. The MySQL install computed these rows with stored functions; the catalog holds the resulting rows.';
COMMENT ON COLUMN enchant_skill_tree.skill_id IS 'Skill (skill XML files).';
COMMENT ON COLUMN enchant_skill_tree.level IS 'Enchanted skill level: route * 100 + enchant step, for example 101 is route 1, +1.';
COMMENT ON COLUMN enchant_skill_tree.base_level IS 'Highest normal level of the skill, to which it returns when the enchant is removed.';
COMMENT ON COLUMN enchant_skill_tree.min_skill_level IS 'Skill level required before this enchant step.';
COMMENT ON COLUMN enchant_skill_tree.sp IS 'SP the player pays.';
COMMENT ON COLUMN enchant_skill_tree.exp IS 'Experience the player pays.';
COMMENT ON COLUMN enchant_skill_tree.success_rate_76 IS 'Success chance in percent for a level 76 player.';
COMMENT ON COLUMN enchant_skill_tree.success_rate_77 IS 'Success chance in percent for a level 77 player.';
COMMENT ON COLUMN enchant_skill_tree.success_rate_78 IS 'Success chance in percent for a level 78 player.';
COMMENT ON COLUMN enchant_skill_tree.success_rate_79 IS 'Success chance in percent for a level 79 player.';
COMMENT ON COLUMN enchant_skill_tree.success_rate_80 IS 'Success chance in percent for a level 80 player.';
COMMENT ON COLUMN enchant_skill_tree.success_rate_81 IS 'Success chance in percent for a level 81 player.';
COMMENT ON COLUMN enchant_skill_tree.success_rate_82 IS 'Success chance in percent for a level 82 player.';
COMMENT ON COLUMN enchant_skill_tree.success_rate_83 IS 'Success chance in percent for a level 83 player.';
COMMENT ON COLUMN enchant_skill_tree.success_rate_84 IS 'Success chance in percent for a level 84 player.';
COMMENT ON COLUMN enchant_skill_tree.success_rate_85 IS 'Success chance in percent for a level 85 player.';

CREATE UNLOGGED TABLE fishing_skill_tree (
	skill_id integer NOT NULL,
	skill_level smallint NOT NULL CHECK (skill_level > 0),
	skill_name text NOT NULL,
	sp integer NOT NULL DEFAULT 0 CHECK (sp >= 0),
	min_level smallint NOT NULL CHECK (min_level > 0),
	cost_item_template_id integer NOT NULL REFERENCES etc_item_template,
	cost_item_count integer NOT NULL CHECK (cost_item_count >= 0),
	is_dwarven_craft boolean NOT NULL DEFAULT false,
	PRIMARY KEY (skill_id, skill_level)
);
COMMENT ON TABLE fishing_skill_tree IS 'Skills learned from a fisherman (fishing skills) and the extra dwarven craft skills.';
COMMENT ON COLUMN fishing_skill_tree.skill_id IS 'Skill (skill XML files).';
COMMENT ON COLUMN fishing_skill_tree.skill_level IS 'Skill level learned.';
COMMENT ON COLUMN fishing_skill_tree.skill_name IS 'Skill name for people.';
COMMENT ON COLUMN fishing_skill_tree.sp IS 'SP the player pays.';
COMMENT ON COLUMN fishing_skill_tree.min_level IS 'Minimum player level.';
COMMENT ON COLUMN fishing_skill_tree.cost_item_template_id IS 'Item the player pays (catalog.etc_item_template).';
COMMENT ON COLUMN fishing_skill_tree.cost_item_count IS 'Number of cost items the player pays.';
COMMENT ON COLUMN fishing_skill_tree.is_dwarven_craft IS 'Whether this is an expanded dwarven craft skill (dwarves only) rather than a fishing skill.';
CREATE INDEX fishing_skill_tree_cost_item_template_id_idx ON fishing_skill_tree (cost_item_template_id);

CREATE UNLOGGED TABLE clan_skill_tree (
	skill_id integer NOT NULL,
	skill_level smallint NOT NULL CHECK (skill_level > 0),
	skill_name text NOT NULL DEFAULT 'Clan Skill',
	min_clan_level smallint NOT NULL CHECK (min_clan_level >= 0),
	description text NOT NULL DEFAULT '',
	reputation_cost integer NOT NULL CHECK (reputation_cost >= 0),
	cost_item_template_id integer NOT NULL REFERENCES etc_item_template,
	cost_item_count bigint NOT NULL CHECK (cost_item_count >= 0),
	PRIMARY KEY (skill_id, skill_level)
);
COMMENT ON TABLE clan_skill_tree IS 'Clan (pledge) skills a clan can learn and what they cost.';
COMMENT ON COLUMN clan_skill_tree.skill_id IS 'Skill (skill XML files).';
COMMENT ON COLUMN clan_skill_tree.skill_level IS 'Skill level learned.';
COMMENT ON COLUMN clan_skill_tree.skill_name IS 'Skill name for people.';
COMMENT ON COLUMN clan_skill_tree.min_clan_level IS 'Minimum clan level.';
COMMENT ON COLUMN clan_skill_tree.description IS 'Effect of the skill for people. Not read by the current code.';
COMMENT ON COLUMN clan_skill_tree.reputation_cost IS 'Clan reputation points the clan pays.';
COMMENT ON COLUMN clan_skill_tree.cost_item_template_id IS 'Item the clan leader pays (catalog.etc_item_template).';
COMMENT ON COLUMN clan_skill_tree.cost_item_count IS 'Number of cost items paid.';
CREATE INDEX clan_skill_tree_cost_item_template_id_idx ON clan_skill_tree (cost_item_template_id);

CREATE UNLOGGED TABLE special_skill_tree (
	skill_id integer NOT NULL,
	skill_level smallint NOT NULL CHECK (skill_level > 0),
	skill_name text NOT NULL,
	cost_item_template_id integer NOT NULL REFERENCES etc_item_template,
	cost_item_count integer NOT NULL CHECK (cost_item_count >= 0),
	PRIMARY KEY (skill_id, skill_level)
);
COMMENT ON TABLE special_skill_tree IS 'Special skills learned for items only (no SP, no level requirement).';
COMMENT ON COLUMN special_skill_tree.skill_id IS 'Skill (skill XML files).';
COMMENT ON COLUMN special_skill_tree.skill_level IS 'Skill level learned.';
COMMENT ON COLUMN special_skill_tree.skill_name IS 'Skill name for people.';
COMMENT ON COLUMN special_skill_tree.cost_item_template_id IS 'Item the player pays (catalog.etc_item_template).';
COMMENT ON COLUMN special_skill_tree.cost_item_count IS 'Number of cost items the player pays.';
CREATE INDEX special_skill_tree_cost_item_template_id_idx ON special_skill_tree (cost_item_template_id);

CREATE UNLOGGED TABLE transform_skill_tree (
	id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	race_id smallint REFERENCES world.race,
	skill_id integer NOT NULL,
	skill_level smallint NOT NULL CHECK (skill_level > 0),
	skill_name text NOT NULL,
	required_item_template_id integer NOT NULL REFERENCES etc_item_template,
	sp integer NOT NULL DEFAULT 0 CHECK (sp >= 0),
	min_level smallint NOT NULL CHECK (min_level > 0),
	UNIQUE NULLS NOT DISTINCT (race_id, skill_id, skill_level)
);
COMMENT ON TABLE transform_skill_tree IS 'Transformation skills and what a player needs to learn them.';
COMMENT ON COLUMN transform_skill_tree.race_id IS 'Race that can learn the skill (world.race); NULL means every race.';
COMMENT ON COLUMN transform_skill_tree.skill_id IS 'Skill (skill XML files).';
COMMENT ON COLUMN transform_skill_tree.skill_level IS 'Skill level learned.';
COMMENT ON COLUMN transform_skill_tree.skill_name IS 'Skill name for people.';
COMMENT ON COLUMN transform_skill_tree.required_item_template_id IS 'Item (transformation sealbook) needed to learn the skill (catalog.etc_item_template).';
COMMENT ON COLUMN transform_skill_tree.sp IS 'SP the player pays.';
COMMENT ON COLUMN transform_skill_tree.min_level IS 'Minimum player level.';
CREATE INDEX transform_skill_tree_required_item_template_id_idx ON transform_skill_tree (required_item_template_id);

-- Henna (dye symbols) and the classes that can draw them.

CREATE UNLOGGED TABLE henna (
	id smallint PRIMARY KEY,
	name text NOT NULL,
	dye_item_template_id integer NOT NULL REFERENCES etc_item_template,
	dye_count integer NOT NULL DEFAULT 10 CHECK (dye_count > 0),
	price bigint NOT NULL CHECK (price >= 0),
	intelligence_bonus smallint NOT NULL DEFAULT 0,
	strength_bonus smallint NOT NULL DEFAULT 0,
	constitution_bonus smallint NOT NULL DEFAULT 0,
	mental_bonus smallint NOT NULL DEFAULT 0,
	dexterity_bonus smallint NOT NULL DEFAULT 0,
	wit_bonus smallint NOT NULL DEFAULT 0
);
COMMENT ON TABLE henna IS 'Henna symbols: the dye they need, their price and their stat changes.';
COMMENT ON COLUMN henna.name IS 'Symbol name for people.';
COMMENT ON COLUMN henna.dye_item_template_id IS 'Dye item consumed when drawing the symbol (catalog.etc_item_template).';
COMMENT ON COLUMN henna.dye_count IS 'Number of dyes consumed when drawing; removing the symbol returns half.';
COMMENT ON COLUMN henna.price IS 'Adena paid for drawing; removing the symbol costs a fifth.';
COMMENT ON COLUMN henna.intelligence_bonus IS 'Change of INT (negative for a penalty).';
COMMENT ON COLUMN henna.strength_bonus IS 'Change of STR (negative for a penalty).';
COMMENT ON COLUMN henna.constitution_bonus IS 'Change of CON (negative for a penalty).';
COMMENT ON COLUMN henna.mental_bonus IS 'Change of MEN (negative for a penalty).';
COMMENT ON COLUMN henna.dexterity_bonus IS 'Change of DEX (negative for a penalty).';
COMMENT ON COLUMN henna.wit_bonus IS 'Change of WIT (negative for a penalty).';
CREATE INDEX henna_dye_item_template_id_idx ON henna (dye_item_template_id);

CREATE UNLOGGED TABLE henna_class (
	player_class_id smallint NOT NULL REFERENCES world.player_class,
	henna_id smallint NOT NULL REFERENCES henna,
	PRIMARY KEY (player_class_id, henna_id)
);
COMMENT ON TABLE henna_class IS 'Henna symbols each class can draw.';
COMMENT ON COLUMN henna_class.player_class_id IS 'Class (world.player_class).';
COMMENT ON COLUMN henna_class.henna_id IS 'Symbol the class can draw.';
CREATE INDEX henna_class_henna_id_idx ON henna_class (henna_id);

-- Buff templates: lists of buffs an NPC buffer casts ('SupportMagic' is the newbie helper list).

CREATE UNLOGGED TABLE buff_template (
	id integer PRIMARY KEY CHECK (id > 0),
	name text NOT NULL UNIQUE
);
COMMENT ON TABLE buff_template IS 'Named buff lists that NPC buffers cast. The name SupportMagic is reserved for the newbie helper.';
COMMENT ON COLUMN buff_template.name IS 'Template name used in HTML links (BuffTemplateTable.getTemplateIdByName).';

CREATE UNLOGGED TABLE buff_template_skill (
	buff_template_id integer NOT NULL REFERENCES buff_template,
	position smallint NOT NULL CHECK (position > 0),
	skill_id integer NOT NULL,
	skill_level smallint NOT NULL DEFAULT 1 CHECK (skill_level > 0),
	skill_name text,
	is_force_cast boolean NOT NULL DEFAULT true,
	min_player_level smallint NOT NULL DEFAULT 1 CHECK (min_player_level >= 0),
	max_player_level smallint NOT NULL DEFAULT 85 CHECK (max_player_level >= 0),
	race_mask smallint NOT NULL DEFAULT 0 CHECK (race_mask BETWEEN 0 AND 31),
	class_kind smallint NOT NULL DEFAULT 0 CHECK (class_kind IN (0, 1, 2, 3)),
	required_faction integer NOT NULL DEFAULT 0 CHECK (required_faction >= 0),
	price_adena bigint NOT NULL DEFAULT 0 CHECK (price_adena >= 0),
	price_faction_points bigint NOT NULL DEFAULT 0 CHECK (price_faction_points >= 0),
	PRIMARY KEY (buff_template_id, position)
);
COMMENT ON TABLE buff_template_skill IS 'Buffs of a buff template in casting order, with the conditions a player must meet.';
COMMENT ON COLUMN buff_template_skill.buff_template_id IS 'Buff template the buff belongs to.';
COMMENT ON COLUMN buff_template_skill.position IS 'Casting order within the template, starting at 1.';
COMMENT ON COLUMN buff_template_skill.skill_id IS 'Buff skill (skill XML files).';
COMMENT ON COLUMN buff_template_skill.skill_level IS 'Level of the buff skill.';
COMMENT ON COLUMN buff_template_skill.skill_name IS 'Skill name for people; NULL when not given. Not read by the code.';
COMMENT ON COLUMN buff_template_skill.is_force_cast IS 'Whether the buff is cast even if the same effect is already present (shows the cast animation).';
COMMENT ON COLUMN buff_template_skill.min_player_level IS 'Minimum player level; 0 means no minimum.';
COMMENT ON COLUMN buff_template_skill.max_player_level IS 'Maximum player level; 0 means no maximum.';
COMMENT ON COLUMN buff_template_skill.race_mask IS 'Races that get the buff, a bit mask: 16 Human, 8 Elf, 4 Dark Elf, 2 Orc, 1 Dwarf; 0 or 31 means every race.';
COMMENT ON COLUMN buff_template_skill.class_kind IS 'Kind of class that gets the buff: 1 fighters, 2 mages, 0 or 3 everyone.';
COMMENT ON COLUMN buff_template_skill.required_faction IS 'Faction a player must belong to; 0 means none. The faction check is disabled in the code.';
COMMENT ON COLUMN buff_template_skill.price_adena IS 'Adena the player pays for the buff; 0 means free.';
COMMENT ON COLUMN buff_template_skill.price_faction_points IS 'Faction points the player pays; 0 means free. Not charged by the current code.';

-- Merchant shops and their price lists. The stock counters of limited items
-- (current count, restock moment) are game state in world.merchant_stock.

CREATE UNLOGGED TABLE merchant_shop (
	id integer PRIMARY KEY,
	npc_template_id integer
);
COMMENT ON TABLE merchant_shop IS 'NPC shops (buy lists), each sold by one merchant NPC or opened by a GM.';
COMMENT ON COLUMN merchant_shop.npc_template_id IS 'Merchant NPC template that opens the shop (catalog.npc_template); NULL for a GM shop.';

CREATE UNLOGGED TABLE custom_merchant_shop (
	id integer PRIMARY KEY,
	npc_template_id integer
);
COMMENT ON TABLE custom_merchant_shop IS 'Operator-defined NPC shops; same columns as merchant_shop.';
COMMENT ON COLUMN custom_merchant_shop.npc_template_id IS 'Merchant NPC template that opens the shop (catalog.npc_template); NULL for a GM shop.';

CREATE UNLOGGED TABLE merchant_buylist (
	merchant_shop_id integer NOT NULL,
	position smallint NOT NULL CHECK (position >= 0),
	item_template_id integer NOT NULL,
	price bigint CHECK (price >= 0),
	stock_count integer CHECK (stock_count >= 0),
	restock_interval_s integer CHECK (restock_interval_s > 0),
	PRIMARY KEY (merchant_shop_id, position)
);
COMMENT ON TABLE merchant_buylist IS 'Items a shop sells, in display order, with price and stock limit. No foreign key to merchant_shop: the shipped data has rows for shops that do not exist (the loader never reads them).';
COMMENT ON COLUMN merchant_buylist.merchant_shop_id IS 'Shop (catalog.merchant_shop.id).';
COMMENT ON COLUMN merchant_buylist.position IS 'Display order within the shop.';
COMMENT ON COLUMN merchant_buylist.item_template_id IS 'Item sold (catalog tables weapon_template, armor_template, etc_item_template); rows for unknown items are skipped.';
COMMENT ON COLUMN merchant_buylist.price IS 'Price in adena; NULL means the reference price of the item template.';
COMMENT ON COLUMN merchant_buylist.stock_count IS 'Stock of a limited item after each restock; NULL means unlimited.';
COMMENT ON COLUMN merchant_buylist.restock_interval_s IS 'Seconds between restocks of a limited item; NULL means never restocked.';

CREATE UNLOGGED TABLE custom_merchant_buylist (
	merchant_shop_id integer NOT NULL REFERENCES custom_merchant_shop,
	position smallint NOT NULL CHECK (position >= 0),
	item_template_id integer NOT NULL,
	price bigint CHECK (price >= 0),
	stock_count integer CHECK (stock_count >= 0),
	restock_interval_s integer CHECK (restock_interval_s > 0),
	PRIMARY KEY (merchant_shop_id, position)
);
COMMENT ON TABLE custom_merchant_buylist IS 'Items an operator-defined shop sells; same columns as merchant_buylist.';
COMMENT ON COLUMN custom_merchant_buylist.merchant_shop_id IS 'Shop (catalog.custom_merchant_shop).';
COMMENT ON COLUMN custom_merchant_buylist.position IS 'Display order within the shop.';
COMMENT ON COLUMN custom_merchant_buylist.item_template_id IS 'Item sold (catalog tables weapon_template, armor_template, etc_item_template and their custom_ twins); rows for unknown items are skipped.';
COMMENT ON COLUMN custom_merchant_buylist.price IS 'Price in adena; NULL means the reference price of the item template.';
COMMENT ON COLUMN custom_merchant_buylist.stock_count IS 'Stock of a limited item after each restock; NULL means unlimited.';
COMMENT ON COLUMN custom_merchant_buylist.restock_interval_s IS 'Seconds between restocks of a limited item; NULL means never restocked.';

-- Pets: stats per level and skills.

CREATE UNLOGGED TABLE pet_stat (
	npc_template_id integer NOT NULL,
	level smallint NOT NULL CHECK (level > 0),
	pet_name text NOT NULL,
	max_exp bigint NOT NULL CHECK (max_exp >= 0),
	max_hp integer NOT NULL,
	max_mp integer NOT NULL,
	physical_attack integer NOT NULL,
	physical_defense integer NOT NULL,
	magic_attack integer NOT NULL,
	magic_defense integer NOT NULL,
	accuracy integer NOT NULL,
	evasion integer NOT NULL,
	critical_rate integer NOT NULL,
	run_speed integer NOT NULL,
	attack_speed integer NOT NULL,
	casting_speed integer NOT NULL,
	max_feed integer NOT NULL CHECK (max_feed >= 0),
	feed_battle integer NOT NULL CHECK (feed_battle >= 0),
	feed_normal integer NOT NULL CHECK (feed_normal >= 0),
	max_load integer NOT NULL CHECK (max_load >= 0),
	hp_regeneration integer NOT NULL,
	mp_regeneration integer NOT NULL,
	owner_exp_share numeric(3,2) NOT NULL CHECK (owner_exp_share BETWEEN 0 AND 1),
	PRIMARY KEY (npc_template_id, level)
);
COMMENT ON TABLE pet_stat IS 'Stats of a pet kind at each level (L2PetData).';
COMMENT ON COLUMN pet_stat.npc_template_id IS 'Pet NPC template (catalog.npc_template).';
COMMENT ON COLUMN pet_stat.level IS 'Pet level.';
COMMENT ON COLUMN pet_stat.pet_name IS 'Pet kind for people, for example wolf. Not read by the code.';
COMMENT ON COLUMN pet_stat.max_exp IS 'Experience at which the pet reaches the next level.';
COMMENT ON COLUMN pet_stat.max_hp IS 'Maximum HP.';
COMMENT ON COLUMN pet_stat.max_mp IS 'Maximum MP.';
COMMENT ON COLUMN pet_stat.physical_attack IS 'Physical attack.';
COMMENT ON COLUMN pet_stat.physical_defense IS 'Physical defense.';
COMMENT ON COLUMN pet_stat.magic_attack IS 'Magic attack.';
COMMENT ON COLUMN pet_stat.magic_defense IS 'Magic defense.';
COMMENT ON COLUMN pet_stat.accuracy IS 'Accuracy.';
COMMENT ON COLUMN pet_stat.evasion IS 'Evasion.';
COMMENT ON COLUMN pet_stat.critical_rate IS 'Critical rate.';
COMMENT ON COLUMN pet_stat.run_speed IS 'Run speed.';
COMMENT ON COLUMN pet_stat.attack_speed IS 'Physical attack speed.';
COMMENT ON COLUMN pet_stat.casting_speed IS 'Casting speed.';
COMMENT ON COLUMN pet_stat.max_feed IS 'Maximum food meter.';
COMMENT ON COLUMN pet_stat.feed_battle IS 'Food used per feeding tick while fighting.';
COMMENT ON COLUMN pet_stat.feed_normal IS 'Food used per feeding tick while not fighting.';
COMMENT ON COLUMN pet_stat.max_load IS 'Weight limit of the pet inventory in weight units.';
COMMENT ON COLUMN pet_stat.hp_regeneration IS 'HP regeneration.';
COMMENT ON COLUMN pet_stat.mp_regeneration IS 'MP regeneration.';
COMMENT ON COLUMN pet_stat.owner_exp_share IS 'Share of the experience the pet earns that goes to the owner, 0 to 1.';

CREATE UNLOGGED TABLE pet_skill (
	npc_template_id integer NOT NULL,
	skill_id integer NOT NULL,
	skill_level smallint NOT NULL CHECK (skill_level >= 0),
	min_level smallint NOT NULL CHECK (min_level >= 0),
	PRIMARY KEY (npc_template_id, skill_id, skill_level)
);
COMMENT ON TABLE pet_skill IS 'Skills a pet or summon gets and the pet level from which it has them.';
COMMENT ON COLUMN pet_skill.npc_template_id IS 'Pet or summon NPC template (catalog.npc_template).';
COMMENT ON COLUMN pet_skill.skill_id IS 'Skill (skill XML files).';
COMMENT ON COLUMN pet_skill.skill_level IS 'Skill level; 0 means the level follows the pet level (L2PetSkillLearn).';
COMMENT ON COLUMN pet_skill.min_level IS 'Minimum pet level.';

-- Fish that can be caught.

CREATE UNLOGGED TABLE fish (
	item_template_id integer PRIMARY KEY REFERENCES etc_item_template,
	level smallint NOT NULL CHECK (level > 0),
	name text NOT NULL,
	hp integer NOT NULL CHECK (hp > 0),
	hp_regeneration integer NOT NULL DEFAULT 5,
	fish_type smallint NOT NULL CHECK (fish_type >= 0),
	fish_group smallint NOT NULL CHECK (fish_group IN (0, 1, 2)),
	guts integer NOT NULL CHECK (guts >= 0),
	guts_check_interval_ms integer NOT NULL CHECK (guts_check_interval_ms > 0),
	bite_wait_ms integer NOT NULL CHECK (bite_wait_ms > 0),
	combat_duration_ms integer NOT NULL CHECK (combat_duration_ms > 0)
);
COMMENT ON TABLE fish IS 'Fish that fishing can catch; the lure decides type, group and level.';
COMMENT ON COLUMN fish.item_template_id IS 'Fish item the player receives (catalog.etc_item_template).';
COMMENT ON COLUMN fish.level IS 'Fish level (fishing skill level range 1 to 27).';
COMMENT ON COLUMN fish.name IS 'Fish name for people.';
COMMENT ON COLUMN fish.hp IS 'Fish HP the player must reduce to zero in the fishing fight.';
COMMENT ON COLUMN fish.hp_regeneration IS 'HP the fish regains per second of the fight.';
COMMENT ON COLUMN fish.fish_type IS 'Fish kind that a lure attracts, for example 0 fat, 1 nimble, 2 ugly; L2Player chooses it from the lure item.';
COMMENT ON COLUMN fish.fish_group IS 'Difficulty group: 0 easy, 1 normal, 2 hard (FishTable).';
COMMENT ON COLUMN fish.guts IS 'Guts (fighting spirit) of the fish, passed to the look-for-fish task and the fishing fight.';
COMMENT ON COLUMN fish.guts_check_interval_ms IS 'Base interval of the guts check in milliseconds before the lure modifier.';
COMMENT ON COLUMN fish.bite_wait_ms IS 'Time until the fish bites, in milliseconds.';
COMMENT ON COLUMN fish.combat_duration_ms IS 'Length of the fishing fight in milliseconds.';
