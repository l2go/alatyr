-- Residences: castles, fortresses and clan halls, the state that belongs to
-- them, and the clan registrations for their sieges.
--
-- - A castle is owned by a clan through clan.castle_id (V1_03); the castle row
--   has no owner column. A castle has a treasury, a tax rate, a siege schedule,
--   paid functions (teleport, restore HP ...), door and trap upgrades, the
--   mercenary posts its owner hired, and the manor (seed sales and crop
--   purchases for the current and the next manor period).
-- - A fortress has an owner clan, a siege date and a contract state towards
--   a castle; it has paid functions and door upgrades.
-- - A clan hall has an owner clan and a rent; town halls are sold in clan hall
--   auctions, contestable halls are won in sieges (clan_hall_siege).
-- - Siege registrations: castle_siege_clan, fort_siege_clan and
--   clan_hall_siege_clan (MySQL kept castle and clan hall registrations
--   together in siege_clans, told apart by id range).
-- The static siege guards of NPC-owned castles are content (catalog.castle_siege_guard).
-- Door ids and item ids point to catalog tables and have no foreign key.
-- Moments are epoch milliseconds in Java.

CREATE TABLE castle (
	id smallint PRIMARY KEY,
	name text NOT NULL UNIQUE,
	tax_percent smallint NOT NULL DEFAULT 0 CHECK (tax_percent >= 0),
	pending_tax_percent smallint NOT NULL DEFAULT 0 CHECK (pending_tax_percent >= 0),
	tax_set_at timestamptz,
	treasury bigint NOT NULL DEFAULT 0 CHECK (treasury >= 0),
	siege_at timestamptz,
	is_registration_over boolean NOT NULL DEFAULT true,
	registration_end_at timestamptz
) WITH (fillfactor = 80);
COMMENT ON TABLE castle IS 'Castles. The owner is stored in clan.castle_id. The treasury is updated on every taxed purchase, hence fillfactor 80.';
COMMENT ON COLUMN castle.name IS 'Castle name, for example Gludio.';
COMMENT ON COLUMN castle.tax_percent IS 'Tax rate in percent that applies now.';
COMMENT ON COLUMN castle.pending_tax_percent IS 'Tax rate in percent the owner set with a delayed change; it applies at the next midnight after tax_set_at. 0 means no pending change.';
COMMENT ON COLUMN castle.tax_set_at IS 'Moment the owner set pending_tax_percent; NULL when the last change applied at once.';
COMMENT ON COLUMN castle.treasury IS 'Adena in the castle treasury.';
COMMENT ON COLUMN castle.siege_at IS 'Start of the next siege; NULL when no siege was ever scheduled (the game then computes the next date).';
COMMENT ON COLUMN castle.is_registration_over IS 'Whether registration for the next siege is closed.';
COMMENT ON COLUMN castle.registration_end_at IS 'Moment siege registration closes; NULL when it was never scheduled.';

INSERT INTO castle (id, name) VALUES
	(1, 'Gludio'),
	(2, 'Dion'),
	(3, 'Giran'),
	(4, 'Oren'),
	(5, 'Aden'),
	(6, 'Innadril'),
	(7, 'Goddard'),
	(8, 'Rune'),
	(9, 'Schuttgart');

-- Castle ownership lives in the clan row (V1_03 declares clan.castle_id UNIQUE,
-- which also serves as the index of this key).
ALTER TABLE clan ADD FOREIGN KEY (castle_id) REFERENCES castle ON DELETE SET NULL;

CREATE TABLE castle_function (
	castle_id smallint NOT NULL REFERENCES castle ON DELETE CASCADE,
	function_type smallint NOT NULL,
	level smallint NOT NULL CHECK (level >= 0),
	lease integer NOT NULL CHECK (lease >= 0),
	rate_ms bigint NOT NULL CHECK (rate_ms >= 0),
	end_at timestamptz,
	PRIMARY KEY (castle_id, function_type)
);
COMMENT ON TABLE castle_function IS 'Paid functions the castle owner activated in the castle residence.';
COMMENT ON COLUMN castle_function.castle_id IS 'Castle that has the function.';
COMMENT ON COLUMN castle_function.function_type IS 'Function, one of the Castle.FUNC_* constants: 1 teleport, 2 restore HP, 3 restore MP, 4 restore exp, 5 support, 9 security.';
COMMENT ON COLUMN castle_function.level IS 'Function level (for restore functions the percentage).';
COMMENT ON COLUMN castle_function.lease IS 'Adena taken from the clan warehouse for each period.';
COMMENT ON COLUMN castle_function.rate_ms IS 'Length of one paid period in milliseconds.';
COMMENT ON COLUMN castle_function.end_at IS 'Moment the paid period ends and the next fee is due; NULL when no period has been paid yet.';

CREATE TABLE castle_door_upgrade (
	door_id integer PRIMARY KEY,
	hp integer NOT NULL DEFAULT 0 CHECK (hp >= 0),
	physical_defense integer NOT NULL DEFAULT 0 CHECK (physical_defense >= 0),
	magic_defense integer NOT NULL DEFAULT 0 CHECK (magic_defense >= 0)
);
COMMENT ON TABLE castle_door_upgrade IS 'Door reinforcements bought by the castle owner; removed when the castle changes hands.';
COMMENT ON COLUMN castle_door_upgrade.door_id IS 'Castle door (catalog table castle_door, which also gives the castle).';
COMMENT ON COLUMN castle_door_upgrade.hp IS 'HP added to the door maximum HP.';
COMMENT ON COLUMN castle_door_upgrade.physical_defense IS 'Physical defense added to the door.';
COMMENT ON COLUMN castle_door_upgrade.magic_defense IS 'Magic defense added to the door.';

CREATE TABLE castle_trap_upgrade (
	castle_id smallint NOT NULL REFERENCES castle ON DELETE CASCADE,
	side smallint NOT NULL CHECK (side IN (1, 2)),
	level smallint NOT NULL CHECK (level >= 0),
	PRIMARY KEY (castle_id, side)
);
COMMENT ON TABLE castle_trap_upgrade IS 'Siege danger zone (trap) upgrades bought by the castle owner, per side of the castle.';
COMMENT ON COLUMN castle_trap_upgrade.castle_id IS 'Castle that has the upgrade.';
COMMENT ON COLUMN castle_trap_upgrade.side IS 'Side of the castle: 1 east or inner zones, 2 west or outer zones.';
COMMENT ON COLUMN castle_trap_upgrade.level IS 'Number of upgraded danger zone cells on this side.';

CREATE TABLE castle_hired_guard (
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL,
	item_template_id integer NOT NULL,
	heading integer NOT NULL,
	PRIMARY KEY (x, y, z)
);
COMMENT ON TABLE castle_hired_guard IS 'Mercenary posts: a mercenary ticket the castle owner placed on the castle ground. The castle is found from the position.';
COMMENT ON COLUMN castle_hired_guard.x IS 'Post position, x coordinate.';
COMMENT ON COLUMN castle_hired_guard.y IS 'Post position, y coordinate.';
COMMENT ON COLUMN castle_hired_guard.z IS 'Post position, z coordinate.';
COMMENT ON COLUMN castle_hired_guard.item_template_id IS 'Mercenary ticket item template (catalog table etc_item_template); it decides the mercenary NPC.';
COMMENT ON COLUMN castle_hired_guard.heading IS 'Direction the mercenary faces.';

CREATE TABLE castle_manor_production (
	castle_id smallint NOT NULL REFERENCES castle ON DELETE CASCADE,
	seed_item_template_id integer NOT NULL,
	period smallint NOT NULL CHECK (period IN (0, 1)),
	remaining_amount bigint NOT NULL DEFAULT 0 CHECK (remaining_amount >= 0),
	start_amount bigint NOT NULL DEFAULT 0 CHECK (start_amount >= 0),
	price bigint NOT NULL DEFAULT 0 CHECK (price >= 0),
	PRIMARY KEY (castle_id, seed_item_template_id, period)
) WITH (fillfactor = 80);
COMMENT ON TABLE castle_manor_production IS 'Manor seeds the castle sells, per manor period. Updated on every seed purchase.';
COMMENT ON COLUMN castle_manor_production.castle_id IS 'Castle whose manor sells the seed.';
COMMENT ON COLUMN castle_manor_production.seed_item_template_id IS 'Seed item template (catalog table etc_item_template).';
COMMENT ON COLUMN castle_manor_production.period IS 'Manor period, CastleManorManager.PERIOD_*: 0 current, 1 next.';
COMMENT ON COLUMN castle_manor_production.remaining_amount IS 'Seeds still for sale in this period.';
COMMENT ON COLUMN castle_manor_production.start_amount IS 'Seeds offered at the start of the period.';
COMMENT ON COLUMN castle_manor_production.price IS 'Price of one seed in adena.';

CREATE TABLE castle_manor_procure (
	castle_id smallint NOT NULL REFERENCES castle ON DELETE CASCADE,
	crop_item_template_id integer NOT NULL,
	period smallint NOT NULL CHECK (period IN (0, 1)),
	remaining_amount bigint NOT NULL DEFAULT 0 CHECK (remaining_amount >= 0),
	start_amount bigint NOT NULL DEFAULT 0 CHECK (start_amount >= 0),
	price bigint NOT NULL DEFAULT 0 CHECK (price >= 0),
	reward_type smallint NOT NULL DEFAULT 0,
	PRIMARY KEY (castle_id, crop_item_template_id, period)
) WITH (fillfactor = 80);
COMMENT ON TABLE castle_manor_procure IS 'Manor crops the castle buys, per manor period. Updated on every crop sale.';
COMMENT ON COLUMN castle_manor_procure.castle_id IS 'Castle whose manor buys the crop.';
COMMENT ON COLUMN castle_manor_procure.crop_item_template_id IS 'Crop item template (catalog table etc_item_template).';
COMMENT ON COLUMN castle_manor_procure.period IS 'Manor period, CastleManorManager.PERIOD_*: 0 current, 1 next.';
COMMENT ON COLUMN castle_manor_procure.remaining_amount IS 'Crops the manor still buys in this period.';
COMMENT ON COLUMN castle_manor_procure.start_amount IS 'Crops the manor wanted to buy at the start of the period.';
COMMENT ON COLUMN castle_manor_procure.price IS 'Price paid for one crop in adena.';
COMMENT ON COLUMN castle_manor_procure.reward_type IS 'Which of the two reward items of the crop (catalog manor data) the seller receives: 1 or 2; 0 when not chosen.';

CREATE TABLE castle_siege_clan (
	castle_id smallint NOT NULL REFERENCES castle ON DELETE CASCADE,
	clan_id integer NOT NULL REFERENCES clan ON DELETE CASCADE,
	siege_role text NOT NULL CHECK (siege_role IN ('DEFENDER', 'ATTACKER', 'DEFENDER_PENDING')),
	PRIMARY KEY (castle_id, clan_id)
);
COMMENT ON TABLE castle_siege_clan IS 'Clans registered for the next siege of a castle. The owner clan is not stored; it defends automatically.';
COMMENT ON COLUMN castle_siege_clan.castle_id IS 'Castle of the siege.';
COMMENT ON COLUMN castle_siege_clan.clan_id IS 'Registered clan.';
COMMENT ON COLUMN castle_siege_clan.siege_role IS 'Side of the clan, a L2SiegeClan.SiegeClanType name: DEFENDER, ATTACKER, or DEFENDER_PENDING (waiting for the owner''s approval).';
CREATE INDEX castle_siege_clan_clan_id_idx ON castle_siege_clan (clan_id);

CREATE TABLE fort (
	id smallint PRIMARY KEY,
	name text NOT NULL UNIQUE,
	is_large boolean NOT NULL DEFAULT false,
	owner_clan_id integer REFERENCES clan ON DELETE SET NULL,
	owned_since_at timestamptz,
	siege_at timestamptz,
	contract_state smallint NOT NULL DEFAULT 0,
	contract_castle_id smallint REFERENCES castle ON DELETE SET NULL,
	blood_oath_count integer NOT NULL DEFAULT 0 CHECK (blood_oath_count >= 0)
);
COMMENT ON TABLE fort IS 'Fortresses.';
COMMENT ON COLUMN fort.name IS 'Fortress name, for example Shanty.';
COMMENT ON COLUMN fort.is_large IS 'Whether the fortress is large (4 commanders and a control room, 5 barracks) rather than small (3 commanders, 3 barracks).';
COMMENT ON COLUMN fort.owner_clan_id IS 'Clan that owns the fortress; NULL when NPCs hold it.';
COMMENT ON COLUMN fort.owned_since_at IS 'Moment the current owner took the fortress; the blood oath reward counts from it. NULL when NPCs hold it.';
COMMENT ON COLUMN fort.siege_at IS 'Start of the next siege; NULL when none is scheduled.';
COMMENT ON COLUMN fort.contract_state IS 'Relation to the nearby castle chosen at the envoy: 0 not chosen yet, 1 independent, 2 contracted to contract_castle_id.';
COMMENT ON COLUMN fort.contract_castle_id IS 'Castle the fortress is contracted to; NULL unless contract_state is 2.';
COMMENT ON COLUMN fort.blood_oath_count IS 'Blood oath rewards the owner clan has earned while holding the fortress.';
CREATE INDEX fort_owner_clan_id_idx ON fort (owner_clan_id);
CREATE INDEX fort_contract_castle_id_idx ON fort (contract_castle_id);

INSERT INTO fort (id, name, is_large) VALUES
	(101, 'Shanty', false),
	(102, 'Southern', true),
	(103, 'Hive', false),
	(104, 'Valley', true),
	(105, 'Ivory', false),
	(106, 'Narsell', false),
	(107, 'Bayou', true),
	(108, 'White Sands', false),
	(109, 'Borderland', true),
	(110, 'Swamp', true),
	(111, 'Archaic', false),
	(112, 'Floran', true),
	(113, 'Cloud Mountain', true),
	(114, 'Tanor', false),
	(115, 'Dragonspine', false),
	(116, 'Antharas', true),
	(117, 'Western', true),
	(118, 'Hunters', true),
	(119, 'Aaru', false),
	(120, 'Demon', false),
	(121, 'Monastic', false);

CREATE TABLE fort_function (
	fort_id smallint NOT NULL REFERENCES fort ON DELETE CASCADE,
	function_type smallint NOT NULL,
	level smallint NOT NULL CHECK (level >= 0),
	lease integer NOT NULL CHECK (lease >= 0),
	rate_ms bigint NOT NULL CHECK (rate_ms >= 0),
	end_at timestamptz,
	PRIMARY KEY (fort_id, function_type)
);
COMMENT ON TABLE fort_function IS 'Paid functions the fortress owner activated.';
COMMENT ON COLUMN fort_function.fort_id IS 'Fortress that has the function.';
COMMENT ON COLUMN fort_function.function_type IS 'Function, one of the Fort.FUNC_* constants: 1 teleport, 2 restore HP, 3 restore MP, 4 restore exp, 5 support.';
COMMENT ON COLUMN fort_function.level IS 'Function level (for restore functions the percentage).';
COMMENT ON COLUMN fort_function.lease IS 'Adena taken from the clan warehouse for each period.';
COMMENT ON COLUMN fort_function.rate_ms IS 'Length of one paid period in milliseconds.';
COMMENT ON COLUMN fort_function.end_at IS 'Moment the paid period ends and the next fee is due; NULL when no period has been paid yet.';

CREATE TABLE fort_door_upgrade (
	door_id integer PRIMARY KEY,
	fort_id smallint NOT NULL REFERENCES fort ON DELETE CASCADE,
	hp integer NOT NULL DEFAULT 0 CHECK (hp >= 0),
	physical_defense integer NOT NULL DEFAULT 0 CHECK (physical_defense >= 0),
	magic_defense integer NOT NULL DEFAULT 0 CHECK (magic_defense >= 0)
);
COMMENT ON TABLE fort_door_upgrade IS 'Door reinforcements bought by the fortress owner; removed when the fortress changes hands.';
COMMENT ON COLUMN fort_door_upgrade.door_id IS 'Fortress door (catalog fortress static objects of type door).';
COMMENT ON COLUMN fort_door_upgrade.fort_id IS 'Fortress the door belongs to.';
COMMENT ON COLUMN fort_door_upgrade.hp IS 'HP added to the door maximum HP.';
COMMENT ON COLUMN fort_door_upgrade.physical_defense IS 'Physical defense added to the door.';
COMMENT ON COLUMN fort_door_upgrade.magic_defense IS 'Magic defense added to the door.';
CREATE INDEX fort_door_upgrade_fort_id_idx ON fort_door_upgrade (fort_id);

CREATE TABLE fort_siege_clan (
	fort_id smallint NOT NULL REFERENCES fort ON DELETE CASCADE,
	clan_id integer NOT NULL REFERENCES clan ON DELETE CASCADE,
	PRIMARY KEY (fort_id, clan_id)
);
COMMENT ON TABLE fort_siege_clan IS 'Clans registered to attack a fortress in its next siege.';
COMMENT ON COLUMN fort_siege_clan.fort_id IS 'Fortress of the siege.';
COMMENT ON COLUMN fort_siege_clan.clan_id IS 'Registered attacking clan.';
CREATE INDEX fort_siege_clan_clan_id_idx ON fort_siege_clan (clan_id);

CREATE TABLE clan_hall (
	id smallint PRIMARY KEY,
	name text NOT NULL,
	town_name text NOT NULL,
	description text NOT NULL DEFAULT '',
	grade smallint NOT NULL DEFAULT 0 CHECK (grade >= 0),
	lease integer NOT NULL DEFAULT 0 CHECK (lease >= 0),
	owner_clan_id integer REFERENCES clan ON DELETE SET NULL,
	paid_until_at timestamptz,
	is_paid boolean NOT NULL DEFAULT false
);
COMMENT ON TABLE clan_hall IS 'Clan halls. Town halls are rented and sold in auctions (clan_hall_auction); contestable halls are won in sieges.';
COMMENT ON COLUMN clan_hall.name IS 'Clan hall name; not unique (several towns have an Onyx Hall).';
COMMENT ON COLUMN clan_hall.town_name IS 'Town the hall belongs to, as the Java Town name spells it, for example Gludio.';
COMMENT ON COLUMN clan_hall.description IS 'Description shown by the auctioneer.';
COMMENT ON COLUMN clan_hall.grade IS 'Hall grade (1 to 3 for town halls); decides which functions and levels are available.';
COMMENT ON COLUMN clan_hall.lease IS 'Rent in adena taken from the owner clan warehouse each rent period; 0 for contestable halls.';
COMMENT ON COLUMN clan_hall.owner_clan_id IS 'Clan that owns the hall; NULL when it is free.';
COMMENT ON COLUMN clan_hall.paid_until_at IS 'Moment the paid rent runs out; infinity for a hall won in a siege (no rent); NULL when the hall is free.';
COMMENT ON COLUMN clan_hall.is_paid IS 'Whether the last rent was paid; false while the owner is overdue.';
CREATE INDEX clan_hall_owner_clan_id_idx ON clan_hall (owner_clan_id);

INSERT INTO clan_hall (id, name, lease, description, town_name, grade) VALUES
	(21, 'Fortress of Resistance', 0, 'Ol Mahum Fortress of Resistance', 'Dion', 3),
	(22, 'Moonstone Hall', 500000, 'Clan hall located in the Town of Gludio', 'Gludio', 2),
	(23, 'Onyx Hall', 500000, 'Clan hall located in the Town of Gludio', 'Gludio', 2),
	(24, 'Topaz Hall', 500000, 'Clan hall located in the Town of Gludio', 'Gludio', 2),
	(25, 'Ruby Hall', 500000, 'Clan hall located in the Town of Gludio', 'Gludio', 2),
	(26, 'Crystal Hall', 500000, 'Clan hall located in Gludin Village', 'Gludin', 2),
	(27, 'Onyx Hall', 500000, 'Clan hall located in Gludin Village', 'Gludin', 2),
	(28, 'Sapphire Hall', 500000, 'Clan hall located in Gludin Village', 'Gludin', 2),
	(29, 'Moonstone Hall', 500000, 'Clan hall located in Gludin Village', 'Gludin', 2),
	(30, 'Emerald Hall', 500000, 'Clan hall located in Gludin Village', 'Gludin', 2),
	(31, 'The Atramental Barracks', 200000, 'Clan hall located in the Town of Dion', 'Dion', 1),
	(32, 'The Scarlet Barracks', 200000, 'Clan hall located in the Town of Dion', 'Dion', 1),
	(33, 'The Viridian Barracks', 200000, 'Clan hall located in the Town of Dion', 'Dion', 1),
	(34, 'Devastated Castle', 0, 'Contestable Clan Hall', 'Aden', 3),
	(35, 'Bandit Stronghold', 0, 'Contestable Clan Hall', 'Oren', 3),
	(36, 'The Golden Chamber', 1000000, 'Clan hall located in the Town of Aden', 'Aden', 3),
	(37, 'The Silver Chamber', 1000000, 'Clan hall located in the Town of Aden', 'Aden', 3),
	(38, 'The Mithril Chamber', 1000000, 'Clan hall located in the Town of Aden', 'Aden', 3),
	(39, 'Silver Manor', 1000000, 'Clan hall located in the Town of Aden', 'Aden', 3),
	(40, 'Gold Manor', 1000000, 'Clan hall located in the Town of Aden', 'Aden', 3),
	(41, 'The Bronze Chamber', 1000000, 'Clan hall located in the Town of Aden', 'Aden', 3),
	(42, 'The Golden Chamber', 1000000, 'Clan hall located in the Town of Giran', 'Giran', 3),
	(43, 'The Silver Chamber', 1000000, 'Clan hall located in the Town of Giran', 'Giran', 3),
	(44, 'The Mithril Chamber', 1000000, 'Clan hall located in the Town of Giran', 'Giran', 3),
	(45, 'The Bronze Chamber', 1000000, 'Clan hall located in the Town of Giran', 'Giran', 3),
	(46, 'Silver Manor', 1000000, 'Clan hall located in the Town of Giran', 'Giran', 3),
	(47, 'Moonstone Hall', 1000000, 'Clan hall located in the Town of Goddard', 'Goddard', 3),
	(48, 'Onyx Hall', 1000000, 'Clan hall located in the Town of Goddard', 'Goddard', 3),
	(49, 'Emerald Hall', 1000000, 'Clan hall located in the Town of Goddard', 'Goddard', 3),
	(50, 'Sapphire Hall', 1000000, 'Clan hall located in the Town of Goddard', 'Goddard', 3),
	(51, 'Mont Chamber', 1000000, 'An upscale Clan hall located in the Rune Township', 'Rune', 3),
	(52, 'Astaire Chamber', 1000000, 'An upscale Clan hall located in the Rune Township', 'Rune', 3),
	(53, 'Aria Chamber', 1000000, 'An upscale Clan hall located in the Rune Township', 'Rune', 3),
	(54, 'Yiana Chamber', 1000000, 'An upscale Clan hall located in the Rune Township', 'Rune', 3),
	(55, 'Roien Chamber', 1000000, 'An upscale Clan hall located in the Rune Township', 'Rune', 3),
	(56, 'Luna Chamber', 1000000, 'An upscale Clan hall located in the Rune Township', 'Rune', 3),
	(57, 'Traban Chamber', 1000000, 'An upscale Clan hall located in the Rune Township', 'Rune', 3),
	(58, 'Eisen Hall', 500000, 'Clan hall located in the Town of Schuttgart', 'Schuttgart', 2),
	(59, 'Heavy Metal Hall', 500000, 'Clan hall located in the Town of Schuttgart', 'Schuttgart', 2),
	(60, 'Molten Ore Hall', 500000, 'Clan hall located in the Town of Schuttgart', 'Schuttgart', 2),
	(61, 'Titan Hall', 500000, 'Clan hall located in the Town of Schuttgart', 'Schuttgart', 2),
	(62, 'Rainbow Springs', 0, '', 'Goddard', 3),
	(63, 'Beast Farm', 0, '', 'Rune', 3),
	(64, 'Fortress of the Dead', 0, '', 'Rune', 4);

CREATE TABLE clan_hall_function (
	clan_hall_id smallint NOT NULL REFERENCES clan_hall ON DELETE CASCADE,
	function_type smallint NOT NULL,
	level smallint NOT NULL CHECK (level >= 0),
	lease integer NOT NULL CHECK (lease >= 0),
	rate_ms bigint NOT NULL CHECK (rate_ms >= 0),
	end_at timestamptz,
	PRIMARY KEY (clan_hall_id, function_type)
);
COMMENT ON TABLE clan_hall_function IS 'Paid functions the clan hall owner activated.';
COMMENT ON COLUMN clan_hall_function.clan_hall_id IS 'Clan hall that has the function.';
COMMENT ON COLUMN clan_hall_function.function_type IS 'Function, one of the ClanHall.FUNC_* constants: 1 teleport, 2 item creation, 3 restore HP, 4 restore MP, 5 restore exp, 6 support, 7 front platform decoration, 8 curtain decoration.';
COMMENT ON COLUMN clan_hall_function.level IS 'Function level (for restore functions the percentage).';
COMMENT ON COLUMN clan_hall_function.lease IS 'Adena taken from the clan warehouse for each period.';
COMMENT ON COLUMN clan_hall_function.rate_ms IS 'Length of one paid period in milliseconds.';
COMMENT ON COLUMN clan_hall_function.end_at IS 'Moment the paid period ends and the next fee is due; NULL when no period has been paid yet.';

CREATE TABLE clan_hall_siege (
	clan_hall_id smallint PRIMARY KEY REFERENCES clan_hall ON DELETE CASCADE,
	siege_at timestamptz,
	is_registration_over boolean NOT NULL DEFAULT true,
	registration_end_at timestamptz
);
COMMENT ON TABLE clan_hall_siege IS 'Siege schedule of the contestable clan halls that use the common siege code (Devastated Castle, Fortress of the Dead).';
COMMENT ON COLUMN clan_hall_siege.clan_hall_id IS 'Contestable clan hall.';
COMMENT ON COLUMN clan_hall_siege.siege_at IS 'Start of the next siege; NULL when no siege was ever scheduled (the game then computes the next date).';
COMMENT ON COLUMN clan_hall_siege.is_registration_over IS 'Whether registration for the next siege is closed.';
COMMENT ON COLUMN clan_hall_siege.registration_end_at IS 'Moment siege registration closes; NULL when it was never scheduled.';

INSERT INTO clan_hall_siege (clan_hall_id) VALUES
	(34),
	(64);

CREATE TABLE clan_hall_siege_clan (
	clan_hall_id smallint NOT NULL REFERENCES clan_hall ON DELETE CASCADE,
	clan_id integer NOT NULL REFERENCES clan ON DELETE CASCADE,
	PRIMARY KEY (clan_hall_id, clan_id)
);
COMMENT ON TABLE clan_hall_siege_clan IS 'Clans registered to attack a contestable clan hall in its next siege.';
COMMENT ON COLUMN clan_hall_siege_clan.clan_hall_id IS 'Contestable clan hall of the siege.';
COMMENT ON COLUMN clan_hall_siege_clan.clan_id IS 'Registered attacking clan.';
CREATE INDEX clan_hall_siege_clan_clan_id_idx ON clan_hall_siege_clan (clan_id);

CREATE TABLE clan_hall_auction (
	id smallint PRIMARY KEY REFERENCES clan_hall ON DELETE CASCADE,
	item_type text NOT NULL DEFAULT 'ClanHall' CHECK (item_type IN ('ClanHall')),
	item_name text NOT NULL,
	item_quantity integer NOT NULL DEFAULT 0 CHECK (item_quantity >= 0),
	seller_player_id integer REFERENCES player ON DELETE SET NULL,
	seller_name text NOT NULL DEFAULT 'NPC',
	seller_clan_name text NOT NULL DEFAULT '',
	starting_bid bigint NOT NULL CHECK (starting_bid >= 0),
	current_bid bigint NOT NULL DEFAULT 0 CHECK (current_bid >= 0),
	end_at timestamptz NOT NULL
);
COMMENT ON TABLE clan_hall_auction IS 'Running clan hall auctions. The id is the id of the clan hall on sale, so a hall has at most one auction. Sold by NPCs (free halls) or by the owner clan.';
COMMENT ON COLUMN clan_hall_auction.item_type IS 'Kind of thing on sale, an Auction.ItemTypeEnum name; only ClanHall exists.';
COMMENT ON COLUMN clan_hall_auction.item_name IS 'Name of the clan hall on sale, copied from clan_hall.name.';
COMMENT ON COLUMN clan_hall_auction.item_quantity IS 'Number of things on sale; 1 for the shipped NPC auctions, 0 for auctions started by a clan (the code does not use it).';
COMMENT ON COLUMN clan_hall_auction.seller_player_id IS 'Leader of the selling clan who started the auction; NULL when NPCs sell the hall.';
COMMENT ON COLUMN clan_hall_auction.seller_name IS 'Name of the seller at the start of the auction; NPC for NPC auctions.';
COMMENT ON COLUMN clan_hall_auction.seller_clan_name IS 'Name of the selling clan, which receives the winning bid; NPC Clan for NPC auctions.';
COMMENT ON COLUMN clan_hall_auction.starting_bid IS 'Minimum first bid in adena.';
COMMENT ON COLUMN clan_hall_auction.current_bid IS 'Written as 0 and not used; the highest bid is the highest clan_hall_auction_bid.max_bid.';
COMMENT ON COLUMN clan_hall_auction.end_at IS 'Moment the auction ends. An ended NPC auction without bids restarts for 7 days.';
CREATE INDEX clan_hall_auction_seller_player_id_idx ON clan_hall_auction (seller_player_id);

INSERT INTO clan_hall_auction (id, item_name, item_quantity, seller_name, seller_clan_name, starting_bid, end_at) VALUES
	(22, 'Moonstone Hall', 1, 'NPC', 'NPC Clan', 20000000, to_timestamp(1164841200)),
	(23, 'Onyx Hall', 1, 'NPC', 'NPC Clan', 20000000, to_timestamp(1164841200)),
	(24, 'Topaz Hall', 1, 'NPC', 'NPC Clan', 20000000, to_timestamp(1164841200)),
	(25, 'Ruby Hall', 1, 'NPC', 'NPC Clan', 20000000, to_timestamp(1164841200)),
	(26, 'Crystal Hall', 1, 'NPC', 'NPC Clan', 20000000, to_timestamp(1164841200)),
	(27, 'Onyx Hall', 1, 'NPC', 'NPC Clan', 20000000, to_timestamp(1164841200)),
	(28, 'Sapphire Hall', 1, 'NPC', 'NPC Clan', 20000000, to_timestamp(1164841200)),
	(29, 'Moonstone Hall', 1, 'NPC', 'NPC Clan', 20000000, to_timestamp(1164841200)),
	(30, 'Emerald Hall', 1, 'NPC', 'NPC Clan', 20000000, to_timestamp(1164841200)),
	(31, 'The Atramental Barracks', 1, 'NPC', 'NPC Clan', 8000000, to_timestamp(1164841200)),
	(32, 'The Scarlet Barracks', 1, 'NPC', 'NPC Clan', 8000000, to_timestamp(1164841200)),
	(33, 'The Viridian Barracks', 1, 'NPC', 'NPC Clan', 8000000, to_timestamp(1164841200)),
	(36, 'The Golden Chamber', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(37, 'The Silver Chamber', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(38, 'The Mithril Chamber', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(39, 'Silver Manor', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(40, 'Gold Manor', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(41, 'The Bronze Chamber', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(42, 'The Golden Chamber', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(43, 'The Silver Chamber', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(44, 'The Mithril Chamber', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(45, 'The Bronze Chamber', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(46, 'Silver Manor', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(47, 'Moonstone Hall', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(48, 'Onyx Hall', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(49, 'Emerald Hall', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(50, 'Sapphire Hall', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(51, 'Mont Chamber', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(52, 'Astaire Chamber', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(53, 'Aria Chamber', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(54, 'Yiana Chamber', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(55, 'Roien Chamber', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(56, 'Luna Chamber', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(57, 'Traban Chamber', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(58, 'Eisen Hall', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(59, 'Heavy Metal Hall', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(60, 'Molten Ore Hall', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200)),
	(61, 'Titan Hall', 1, 'NPC', 'NPC Clan', 50000000, to_timestamp(1164841200));

CREATE TABLE clan_hall_auction_bid (
	clan_hall_auction_id smallint NOT NULL REFERENCES clan_hall_auction ON DELETE CASCADE,
	clan_id integer NOT NULL REFERENCES clan ON DELETE CASCADE,
	bidder_name text NOT NULL,
	clan_name text NOT NULL,
	max_bid bigint NOT NULL CHECK (max_bid >= 0),
	bid_at timestamptz NOT NULL,
	PRIMARY KEY (clan_hall_auction_id, clan_id)
);
COMMENT ON TABLE clan_hall_auction_bid IS 'Bids in clan hall auctions, one per bidding clan and auction. Authoritative for clan.clan_hall_auction_id.';
COMMENT ON COLUMN clan_hall_auction_bid.clan_hall_auction_id IS 'Auction the bid is in.';
COMMENT ON COLUMN clan_hall_auction_bid.clan_id IS 'Bidding clan; the adena comes from its warehouse.';
COMMENT ON COLUMN clan_hall_auction_bid.bidder_name IS 'Name of the player who placed the bid (the clan leader after a raise).';
COMMENT ON COLUMN clan_hall_auction_bid.clan_name IS 'Name of the bidding clan at the time of the bid; the code finds the clan by this name to refund it.';
COMMENT ON COLUMN clan_hall_auction_bid.max_bid IS 'Bid in adena.';
COMMENT ON COLUMN clan_hall_auction_bid.bid_at IS 'Moment of the last bid of the clan.';
CREATE INDEX clan_hall_auction_bid_clan_id_idx ON clan_hall_auction_bid (clan_id);

-- The clan hall auction a clan bids at (V1_03).
ALTER TABLE clan ADD FOREIGN KEY (clan_hall_auction_id) REFERENCES clan_hall_auction ON DELETE SET NULL;
CREATE INDEX clan_clan_hall_auction_id_idx ON clan (clan_hall_auction_id);
