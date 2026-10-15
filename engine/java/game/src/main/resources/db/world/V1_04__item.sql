-- Items and what hangs off them: item instances owned by players, clans and
-- pets, their augmentation and elemental attributes, pets (keyed by their
-- control item), items lying on the ground, cursed weapons in play, community
-- board mail, the stores of players who went offline while trading, and the
-- limited-stock counters of NPC merchants.
--
-- item.id, pet.item_id and ground_item.id are game object ids handed out by
-- the in-memory IdFactory, so they have no default.
-- Item templates live in the catalog (catalog.weapon_template,
-- catalog.armor_template, catalog.etc_item_template); world has no foreign key
-- into the catalog.

CREATE TABLE item (
	id integer PRIMARY KEY,
	item_template_id integer NOT NULL,
	owner_player_id integer REFERENCES player ON DELETE CASCADE,
	owner_clan_id integer REFERENCES clan ON DELETE CASCADE,
	-- The foreign key to pet is added below, after the pet table exists.
	owner_pet_id integer,
	location text NOT NULL CHECK (location IN ('INVENTORY', 'PAPERDOLL', 'WAREHOUSE', 'CLANWH', 'PET', 'PET_EQUIP', 'FREIGHT')),
	location_slot integer NOT NULL DEFAULT 0,
	count bigint NOT NULL CHECK (count > 0),
	enchant_level integer NOT NULL DEFAULT 0 CHECK (enchant_level >= 0),
	custom_type1 integer NOT NULL DEFAULT 0,
	custom_type2 integer NOT NULL DEFAULT 0,
	mana_left integer NOT NULL DEFAULT -1 CHECK (mana_left >= -1),
	expire_at timestamptz,
	CONSTRAINT item_owner_check CHECK (CASE
		WHEN location = 'CLANWH' THEN owner_clan_id IS NOT NULL AND owner_player_id IS NULL AND owner_pet_id IS NULL
		WHEN location IN ('PET', 'PET_EQUIP') THEN owner_pet_id IS NOT NULL AND owner_player_id IS NULL AND owner_clan_id IS NULL
		ELSE owner_player_id IS NOT NULL AND owner_clan_id IS NULL AND owner_pet_id IS NULL
	END)
) WITH (fillfactor = 80);
COMMENT ON TABLE item IS 'Item instances stored in the game: a stack or a single piece in a player inventory, warehouse or freight, a clan warehouse, or a pet inventory. Items on the ground are in ground_item; items that no one owns are not stored.';
COMMENT ON COLUMN item.item_template_id IS 'Item template (catalog tables weapon_template, armor_template, etc_item_template).';
COMMENT ON COLUMN item.owner_player_id IS 'Player who owns the item, for locations INVENTORY, PAPERDOLL, WAREHOUSE and FREIGHT; NULL for the other locations.';
COMMENT ON COLUMN item.owner_clan_id IS 'Clan whose warehouse holds the item, for location CLANWH; NULL for the other locations.';
COMMENT ON COLUMN item.owner_pet_id IS 'Pet (pet.item_id, the pet control item) that carries the item, for locations PET and PET_EQUIP; NULL for the other locations.';
COMMENT ON COLUMN item.location IS 'Where the item is, as the Java enum L2ItemInstance.ItemLocation spells it: INVENTORY, PAPERDOLL (equipped), WAREHOUSE, CLANWH (clan warehouse), PET, PET_EQUIP (equipped by the pet), FREIGHT. VOID, NPC and LEASE are never stored.';
COMMENT ON COLUMN item.location_slot IS 'Position inside the location: paperdoll slot for PAPERDOLL and PET_EQUIP, order in the inventory window for INVENTORY, destination town (freight location id) for FREIGHT; 0 when not used.';
COMMENT ON COLUMN item.count IS 'Number of pieces in the stack; 1 for items that do not stack. A stack that reaches 0 is deleted.';
COMMENT ON COLUMN item.enchant_level IS 'Enchant level. For a pet control item it is the pet level; lottery and race tickets use it for the chosen numbers or the race number.';
COMMENT ON COLUMN item.custom_type1 IS 'Extra value for special items: lottery number of a lottery ticket, lane of a monster race ticket; 0 otherwise.';
COMMENT ON COLUMN item.custom_type2 IS 'Extra value for special items: chosen numbers 17-20 of a lottery ticket (bit mask), bet of a race ticket in hundreds of adena, 1 on a pet control item whose pet has been named; 0 otherwise.';
COMMENT ON COLUMN item.mana_left IS 'Remaining mana of a shadow item; one point is spent per minute while it is equipped and the item disappears at 0. -1 for an item that is not a shadow item.';
COMMENT ON COLUMN item.expire_at IS 'Moment when a time-limited item disappears; NULL for an item without a time limit.';
CREATE INDEX item_owner_player_id_location_idx ON item (owner_player_id, location);
CREATE INDEX item_owner_clan_id_idx ON item (owner_clan_id);
CREATE INDEX item_owner_pet_id_idx ON item (owner_pet_id);
CREATE INDEX item_item_template_id_idx ON item (item_template_id);

CREATE TABLE item_attribute (
	item_id integer PRIMARY KEY REFERENCES item ON DELETE CASCADE,
	augmentation_attributes integer,
	augmentation_skill_id integer,
	augmentation_skill_level integer CHECK (augmentation_skill_level > 0),
	element_type smallint CHECK (element_type BETWEEN 0 AND 5),
	element_value integer CHECK (element_value >= 0),
	CONSTRAINT item_attribute_augmentation_skill_check CHECK ((augmentation_skill_id IS NULL) = (augmentation_skill_level IS NULL)
		AND (augmentation_skill_id IS NULL OR augmentation_attributes IS NOT NULL)),
	CONSTRAINT item_attribute_element_check CHECK ((element_type IS NULL) = (element_value IS NULL)),
	CONSTRAINT item_attribute_value_check CHECK (augmentation_attributes IS NOT NULL OR element_type IS NOT NULL)
);
COMMENT ON TABLE item_attribute IS 'Augmentation and elemental attribute of an item. A row exists only while the item has at least one of the two.';
COMMENT ON COLUMN item_attribute.item_id IS 'Item that carries the attributes.';
COMMENT ON COLUMN item_attribute.augmentation_attributes IS 'Augmentation stat bonuses as the client encodes them (two 16-bit stat ids in one integer); NULL when the item is not augmented.';
COMMENT ON COLUMN item_attribute.augmentation_skill_id IS 'Skill granted by the augmentation (catalog table skill); NULL when the item is not augmented or the augmentation gives no skill.';
COMMENT ON COLUMN item_attribute.augmentation_skill_level IS 'Level of the augmentation skill; NULL when there is no augmentation skill.';
COMMENT ON COLUMN item_attribute.element_type IS 'Element of the elemental attribute, as the constants in Java class Elementals: 0 fire, 1 water, 2 wind, 3 earth, 4 holy, 5 dark; NULL when the item has no elemental attribute.';
COMMENT ON COLUMN item_attribute.element_value IS 'Strength of the elemental attribute; NULL when the item has no elemental attribute.';

CREATE TABLE pet (
	item_id integer PRIMARY KEY REFERENCES item ON DELETE CASCADE,
	name citext CHECK (char_length(name) <= 16),
	level smallint NOT NULL CHECK (level >= 0),
	current_hp double precision NOT NULL CHECK (current_hp >= 0),
	current_mp double precision NOT NULL CHECK (current_mp >= 0),
	exp bigint NOT NULL CHECK (exp >= 0),
	sp integer NOT NULL CHECK (sp >= 0),
	current_feed integer NOT NULL CHECK (current_feed >= 0),
	weapon_template_id integer,
	armor_template_id integer,
	jewel_template_id integer
);
COMMENT ON TABLE pet IS 'Pets. A pet is identified by its control item (the collar or flute in the owner inventory); deleting the control item deletes the pet and everything the pet carries.';
COMMENT ON COLUMN pet.item_id IS 'Control item of the pet.';
COMMENT ON COLUMN pet.name IS 'Name the owner gave the pet, at most 16 characters; NULL while the pet has no name.';
COMMENT ON COLUMN pet.level IS 'Pet level; the control item enchant level shows the same value.';
COMMENT ON COLUMN pet.current_hp IS 'Current HP; below 0.5 the pet is dead.';
COMMENT ON COLUMN pet.current_mp IS 'Current MP.';
COMMENT ON COLUMN pet.exp IS 'Experience points.';
COMMENT ON COLUMN pet.sp IS 'Skill points.';
COMMENT ON COLUMN pet.current_feed IS 'Food meter of the pet; 0 means hungry.';
COMMENT ON COLUMN pet.weapon_template_id IS 'Weapon the pet wore when it was last recalled (catalog table weapon_template); it is equipped again from the owner inventory on the next summon. NULL for none.';
COMMENT ON COLUMN pet.armor_template_id IS 'Armor the pet wore when it was last recalled (catalog table armor_template); equipped again on the next summon. NULL for none.';
COMMENT ON COLUMN pet.jewel_template_id IS 'Jewel the pet wore when it was last recalled (catalog table armor_template); equipped again on the next summon. NULL for none.';
CREATE INDEX pet_name_idx ON pet (name);

ALTER TABLE item ADD FOREIGN KEY (owner_pet_id) REFERENCES pet ON DELETE CASCADE;

CREATE TABLE ground_item (
	id integer PRIMARY KEY,
	item_template_id integer NOT NULL,
	count bigint NOT NULL CHECK (count > 0),
	enchant_level integer NOT NULL DEFAULT 0 CHECK (enchant_level >= 0),
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	dropped_at timestamptz,
	is_protected boolean NOT NULL DEFAULT false,
	is_equipable boolean NOT NULL DEFAULT false
);
COMMENT ON TABLE ground_item IS 'Items lying on the ground, saved at shutdown and put back into the world at startup when SaveDroppedItem is enabled. The table is rewritten as a whole on every save.';
COMMENT ON COLUMN ground_item.item_template_id IS 'Item template (catalog tables weapon_template, armor_template, etc_item_template).';
COMMENT ON COLUMN ground_item.count IS 'Number of pieces in the stack.';
COMMENT ON COLUMN ground_item.enchant_level IS 'Enchant level.';
COMMENT ON COLUMN ground_item.x IS 'World X coordinate.';
COMMENT ON COLUMN ground_item.y IS 'World Y coordinate.';
COMMENT ON COLUMN ground_item.z IS 'World Z coordinate.';
COMMENT ON COLUMN ground_item.dropped_at IS 'Moment the item was dropped; the auto-destroy timer counts from it. NULL when the item is protected or was never given a drop time, so it is never auto-destroyed.';
COMMENT ON COLUMN ground_item.is_protected IS 'True when the item is protected from auto-destroy (a player drop while DestroyPlayerDroppedItem was off).';
COMMENT ON COLUMN ground_item.is_equipable IS 'True when the item can be equipped; copied from the template so the startup recycle of protected items can skip equipment without reading the catalog.';

CREATE TABLE cursed_weapon (
	item_template_id integer PRIMARY KEY,
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	previous_karma integer NOT NULL CHECK (previous_karma >= 0),
	previous_pk_kills integer NOT NULL CHECK (previous_pk_kills >= 0),
	kill_count integer NOT NULL DEFAULT 0 CHECK (kill_count >= 0),
	end_at timestamptz
);
COMMENT ON TABLE cursed_weapon IS 'Cursed weapons (Zariche, Akamanah) that a player currently holds. A weapon that no one holds has no row.';
COMMENT ON COLUMN cursed_weapon.item_template_id IS 'Item template of the cursed weapon (catalog table weapon_template).';
COMMENT ON COLUMN cursed_weapon.player_id IS 'Player who holds the weapon.';
COMMENT ON COLUMN cursed_weapon.previous_karma IS 'Karma the player had before picking the weapon up; given back when the weapon leaves them.';
COMMENT ON COLUMN cursed_weapon.previous_pk_kills IS 'PK count the player had before picking the weapon up; given back when the weapon leaves them.';
COMMENT ON COLUMN cursed_weapon.kill_count IS 'Players killed with the weapon; it decides the weapon skill level.';
COMMENT ON COLUMN cursed_weapon.end_at IS 'Moment the weapon disappears; each kill moves it earlier. NULL when no end was set (a weapon given by a GM), and such a weapon ends at the next startup.';
CREATE INDEX cursed_weapon_player_id_idx ON cursed_weapon (player_id);

CREATE TABLE player_mail (
	id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	player_id integer NOT NULL REFERENCES player ON DELETE CASCADE,
	sender_player_id integer REFERENCES player ON DELETE SET NULL,
	folder text NOT NULL CHECK (folder IN ('inbox', 'sentbox', 'archive', 'temparchive')),
	recipient_names varchar(200) NOT NULL,
	subject varchar(12) NOT NULL,
	message varchar(3000) NOT NULL,
	sent_at timestamptz NOT NULL,
	delete_at timestamptz NOT NULL,
	is_unread boolean NOT NULL DEFAULT true
);
COMMENT ON TABLE player_mail IS 'Community board mail. Sending a letter stores one copy in the inbox of every recipient and one copy in the sentbox of the sender.';
COMMENT ON COLUMN player_mail.player_id IS 'Player whose mailbox holds this copy of the letter.';
COMMENT ON COLUMN player_mail.sender_player_id IS 'Player who wrote the letter; NULL when the sender has been deleted.';
COMMENT ON COLUMN player_mail.folder IS 'Mailbox folder: inbox, sentbox, archive or temparchive (the game writes only inbox and sentbox).';
COMMENT ON COLUMN player_mail.recipient_names IS 'Recipient player names exactly as the sender typed them, separated by semicolons; kept as text for display, not a reference.';
COMMENT ON COLUMN player_mail.subject IS 'Subject line, at most 12 characters (client limit).';
COMMENT ON COLUMN player_mail.message IS 'Letter text; line breaks are stored as the HTML tag <br1>.';
COMMENT ON COLUMN player_mail.sent_at IS 'Moment the letter was sent.';
COMMENT ON COLUMN player_mail.delete_at IS 'Moment after which the daily mail clean-up task deletes the letter from inbox and sentbox.';
COMMENT ON COLUMN player_mail.is_unread IS 'True until the mailbox owner opens the letter.';
CREATE INDEX player_mail_player_id_folder_idx ON player_mail (player_id, folder);
CREATE INDEX player_mail_sender_player_id_idx ON player_mail (sender_player_id);

CREATE TABLE offline_store (
	player_id integer PRIMARY KEY REFERENCES player ON DELETE CASCADE,
	store_type text NOT NULL CHECK (store_type IN ('STORE_PRIVATE_SELL', 'STORE_PRIVATE_BUY', 'STORE_PRIVATE_MANUFACTURE', 'STORE_PRIVATE_PACKAGE_SELL')),
	title varchar(255)
);
COMMENT ON TABLE offline_store IS 'Private stores of players who went offline while trading. Written at shutdown and read at startup, when the players come back as offline traders.';
COMMENT ON COLUMN offline_store.player_id IS 'Player who runs the store.';
COMMENT ON COLUMN offline_store.store_type IS 'Kind of store, as the constants L2Player.STORE_PRIVATE_* are named: STORE_PRIVATE_SELL (1), STORE_PRIVATE_BUY (3), STORE_PRIVATE_MANUFACTURE (5), STORE_PRIVATE_PACKAGE_SELL (8).';
COMMENT ON COLUMN offline_store.title IS 'Store message shown above the player; NULL when the player set none.';

CREATE TABLE offline_store_item (
	id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	player_id integer NOT NULL REFERENCES offline_store ON DELETE CASCADE,
	item_id integer REFERENCES item ON DELETE CASCADE,
	item_template_id integer,
	recipe_id integer,
	count bigint CHECK (count >= 0),
	price bigint NOT NULL CHECK (price >= 0),
	CONSTRAINT offline_store_item_goods_check CHECK (num_nonnulls(item_id, item_template_id, recipe_id) = 1
		AND (count IS NULL) = (recipe_id IS NOT NULL))
);
COMMENT ON TABLE offline_store_item IS 'Lines of an offline store: items offered for sale, items the player wants to buy, or recipes offered for crafting, depending on the store type.';
COMMENT ON COLUMN offline_store_item.player_id IS 'Store the line belongs to.';
COMMENT ON COLUMN offline_store_item.item_id IS 'Item from the player inventory offered for sale (sell and package sell stores); NULL for the other store types.';
COMMENT ON COLUMN offline_store_item.item_template_id IS 'Item the player wants to buy (buy store), an item template (catalog tables weapon_template, armor_template, etc_item_template); NULL for the other store types.';
COMMENT ON COLUMN offline_store_item.recipe_id IS 'Recipe offered for crafting (manufacture store; catalog table recipe); NULL for the other store types.';
COMMENT ON COLUMN offline_store_item.count IS 'Number of pieces to sell or to buy; NULL for a recipe line.';
COMMENT ON COLUMN offline_store_item.price IS 'Price per piece in adena; for a recipe line the crafting fee.';
CREATE INDEX offline_store_item_player_id_idx ON offline_store_item (player_id);
CREATE INDEX offline_store_item_item_id_idx ON offline_store_item (item_id);

CREATE TABLE merchant_stock (
	shop_id integer NOT NULL,
	item_template_id integer NOT NULL,
	current_count integer NOT NULL CHECK (current_count >= 0),
	PRIMARY KEY (shop_id, item_template_id)
);
COMMENT ON TABLE merchant_stock IS 'Remaining stock of limited merchant goods (catalog.merchant_buylist rows with a limited count). A row exists only while the stock is below the initial count; without a row the item has its full catalog count.';
COMMENT ON COLUMN merchant_stock.shop_id IS 'Shop (catalog table merchant_buylist, column shop_id).';
COMMENT ON COLUMN merchant_stock.item_template_id IS 'Item sold (catalog tables weapon_template, armor_template, etc_item_template). The same item listed twice in one shop shares one counter.';
COMMENT ON COLUMN merchant_stock.current_count IS 'Pieces left until the next restock.';

CREATE TABLE merchant_restock (
	restock_interval_s bigint PRIMARY KEY CHECK (restock_interval_s > 0),
	next_restock_at timestamptz NOT NULL
);
COMMENT ON TABLE merchant_restock IS 'Next restock moment of limited merchant goods, one row per restock interval used in catalog.merchant_buylist. Every item with that interval is refilled to its catalog count at that moment.';
COMMENT ON COLUMN merchant_restock.restock_interval_s IS 'Restock interval in seconds, equal to the interval of the catalog.merchant_buylist rows it drives.';
COMMENT ON COLUMN merchant_restock.next_restock_at IS 'Moment of the next restock; a moment in the past means restock at startup.';
