-- Reference data that world state points to: races and player classes.
-- The ids are fixed by the Lineage II client protocol.

-- citext compares player, clan and account names without case, as MySQL did.
CREATE EXTENSION IF NOT EXISTS citext SCHEMA public;

COMMENT ON SCHEMA world IS 'Game state: players, items, clans, sieges and events. Managed by Flyway; this schema is the backup.';

CREATE TABLE race (
	id smallint PRIMARY KEY,
	name text NOT NULL UNIQUE
);
COMMENT ON TABLE race IS 'Playable races.';
COMMENT ON COLUMN race.name IS 'Race name as the Java enum Race spells it.';

INSERT INTO race (id, name) VALUES
	(0, 'Human'),
	(1, 'Elf'),
	(2, 'Darkelf'),
	(3, 'Orc'),
	(4, 'Dwarf'),
	(5, 'Kamael');

CREATE TABLE player_class (
	id smallint PRIMARY KEY,
	code text NOT NULL UNIQUE,
	name text NOT NULL,
	race_id smallint NOT NULL REFERENCES race,
	parent_class_id smallint REFERENCES player_class
);
COMMENT ON TABLE player_class IS 'Player classes (professions) and the class each one advances from.';
COMMENT ON COLUMN player_class.code IS 'Class code used by the server code, for example H_Warrior.';
COMMENT ON COLUMN player_class.name IS 'Class name for people, for example Warrior.';
COMMENT ON COLUMN player_class.race_id IS 'Race of the class.';
COMMENT ON COLUMN player_class.parent_class_id IS 'Class this one advances from; NULL for a starting class.';
CREATE INDEX player_class_race_id_idx ON player_class (race_id);
CREATE INDEX player_class_parent_class_id_idx ON player_class (parent_class_id);

INSERT INTO player_class (id, code, name, race_id, parent_class_id) VALUES
	(0, 'H_Fighter', 'Fighter', 0, NULL),
	(1, 'H_Warrior', 'Warrior', 0, 0),
	(2, 'H_Gladiator', 'Gladiator', 0, 1),
	(3, 'H_Warlord', 'Warlord', 0, 1),
	(4, 'H_Knight', 'Knight', 0, 0),
	(5, 'H_Paladin', 'Paladin', 0, 4),
	(6, 'H_DarkAvenger', 'Dark Avenger', 0, 4),
	(7, 'H_Rogue', 'Rogue', 0, 0),
	(8, 'H_TreasureHunter', 'Treasure Hunter', 0, 7),
	(9, 'H_Hawkeye', 'Hawkeye', 0, 7),
	(10, 'H_Mage', 'Mage', 0, NULL),
	(11, 'H_Wizard', 'Wizard', 0, 10),
	(12, 'H_Sorceror', 'Sorceror', 0, 11),
	(13, 'H_Necromancer', 'Necromancer', 0, 11),
	(14, 'H_Warlock', 'Warlock', 0, 11),
	(15, 'H_Cleric', 'Cleric', 0, 10),
	(16, 'H_Bishop', 'Bishop', 0, 15),
	(17, 'H_Prophet', 'Prophet', 0, 15),
	(18, 'E_Fighter', 'Fighter', 1, NULL),
	(19, 'E_Knight', 'Knight', 1, 18),
	(20, 'E_TempleKnight', 'Temple Knight', 1, 19),
	(21, 'E_SwordSinger', 'Sword Singer', 1, 19),
	(22, 'E_Scout', 'Scout', 1, 18),
	(23, 'E_PlainsWalker', 'Plains Walker', 1, 22),
	(24, 'E_SilverRanger', 'Silver Ranger', 1, 22),
	(25, 'E_Mage', 'Mage', 1, NULL),
	(26, 'E_Wizard', 'Wizard', 1, 25),
	(27, 'E_SpellSinger', 'Spell Singer', 1, 26),
	(28, 'E_ElementalSummoner', 'Elemental Summoner', 1, 26),
	(29, 'E_Oracle', 'Oracle', 1, 25),
	(30, 'E_Elder', 'Elder', 1, 29),
	(31, 'DE_Fighter', 'Fighter', 2, NULL),
	(32, 'DE_PaulusKnight', 'Paulus Knight', 2, 31),
	(33, 'DE_ShillienKnight', 'Shillien Knight', 2, 32),
	(34, 'DE_BladeDancer', 'Blade Dancer', 2, 32),
	(35, 'DE_Assassin', 'Assassin', 2, 31),
	(36, 'DE_AbyssWalker', 'Abyss Walker', 2, 35),
	(37, 'DE_PhantomRanger', 'Phantom Ranger', 2, 35),
	(38, 'DE_Mage', 'Mage', 2, NULL),
	(39, 'DE_DarkWizard', 'Dark Wizard', 2, 38),
	(40, 'DE_Spellhowler', 'Spellhowler', 2, 39),
	(41, 'DE_PhantomSummoner', 'Phantom Summoner', 2, 39),
	(42, 'DE_ShillienOracle', 'Shillien Oracle', 2, 38),
	(43, 'DE_ShillienElder', 'Shillien Elder', 2, 42),
	(44, 'O_Fighter', 'Fighter', 3, NULL),
	(45, 'O_Raider', 'Raider', 3, 44),
	(46, 'O_Destroyer', 'Destroyer', 3, 45),
	(47, 'O_Monk', 'Monk', 3, 44),
	(48, 'O_Tyrant', 'Tyrant', 3, 47),
	(49, 'O_Mage', 'Mage', 3, NULL),
	(50, 'O_Shaman', 'Shaman', 3, 49),
	(51, 'O_Overlord', 'Overlord', 3, 50),
	(52, 'O_Warcryer', 'Warcryer', 3, 50),
	(53, 'D_Fighter', 'Fighter', 4, NULL),
	(54, 'D_Scavenger', 'Scavenger', 4, 53),
	(55, 'D_BountyHunter', 'Bounty Hunter', 4, 54),
	(56, 'D_Artisan', 'Artisan', 4, 53),
	(57, 'D_Warsmith', 'Warsmith', 4, 56),
	(88, 'H_Duelist', 'Duelist', 0, 2),
	(89, 'H_Dreadnought', 'Dreadnought', 0, 3),
	(90, 'H_PhoenixKnight', 'Phoenix Knight', 0, 5),
	(91, 'H_HellKnight', 'Hell Knight', 0, 6),
	(92, 'H_Sagittarius', 'Sagittarius', 0, 9),
	(93, 'H_Adventurer', 'Adventurer', 0, 8),
	(94, 'H_Archmage', 'Archmage', 0, 12),
	(95, 'H_Soultaker', 'Soultaker', 0, 13),
	(96, 'H_ArcanaLord', 'Arcana Lord', 0, 14),
	(97, 'H_Cardinal', 'Cardinal', 0, 16),
	(98, 'H_Hierophant', 'Hierophant', 0, 17),
	(99, 'E_EvaTemplar', 'Eva Templar', 1, 20),
	(100, 'E_SwordMuse', 'Sword Muse', 1, 21),
	(101, 'E_WindRider', 'Wind Rider', 1, 23),
	(102, 'E_MoonlightSentinel', 'Moonlight Sentinel', 1, 24),
	(103, 'E_MysticMuse', 'Mystic Muse', 1, 27),
	(104, 'E_ElementalMaster', 'Elemental Master', 1, 28),
	(105, 'E_EvaSaint', 'Eva Saint', 1, 30),
	(106, 'DE_ShillienTemplar', 'Shillien Templar', 2, 33),
	(107, 'DE_SpectralDancer', 'Spectral Dancer', 2, 34),
	(108, 'DE_GhostHunter', 'Ghost Hunter', 2, 36),
	(109, 'DE_GhostSentinel', 'Ghost Sentinel', 2, 37),
	(110, 'DE_StormScreamer', 'Storm Screamer', 2, 40),
	(111, 'DE_SpectralMaster', 'Spectral Master', 2, 41),
	(112, 'DE_ShillienSaint', 'Shillien Saint', 2, 43),
	(113, 'O_Titan', 'Titan', 3, 46),
	(114, 'O_GrandKhauatari', 'Grand Khauatari', 3, 48),
	(115, 'O_Dominator', 'Dominator', 3, 51),
	(116, 'O_Doomcryer', 'Doomcryer', 3, 52),
	(117, 'D_FortuneSeeker', 'Fortune Seeker', 4, 55),
	(118, 'D_Maestro', 'Maestro', 4, 57),
	(123, 'K_SoldierM', 'Soldier (Male)', 5, NULL),
	(124, 'K_SoldierF', 'Soldier (Female)', 5, NULL),
	(125, 'K_Trooper', 'Trooper', 5, 123),
	(126, 'K_Warder', 'Warder', 5, 124),
	(127, 'K_Berserker', 'Berserker', 5, 125),
	(128, 'K_SoulbreakerM', 'Soulbreaker (Male)', 5, 125),
	(129, 'K_SoulbreakerF', 'Soulbreaker (Female)', 5, 126),
	(130, 'K_Arbalester', 'Arbalester', 5, 126),
	(131, 'K_Doombringer', 'Doombringer', 5, 127),
	(132, 'K_SoulhoundM', 'Soulhound (Male)', 5, 128),
	(133, 'K_SoulhoundF', 'Soulhound (Female)', 5, 129),
	(134, 'K_Trickster', 'Trickster', 5, 130),
	(135, 'K_Inspector', 'Inspector', 5, 126),
	(136, 'K_Judicator', 'Judicator', 5, 135);
