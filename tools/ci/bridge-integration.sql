-- Isolated characters for the live bridge acceptance test. The server has
-- already migrated this disposable database and stopped. Seed before the next
-- startup loads character names and object IDs. No production data is imported.
INSERT INTO login.account (name, password_hash) VALUES
    ('bridge-trade', '2jmj7l5rSw0yVb/vlWAYkK/YBwk='),
    ('bridge-rise', '2jmj7l5rSw0yVb/vlWAYkK/YBwk='),
    ('bridge-witness', '2jmj7l5rSw0yVb/vlWAYkK/YBwk='),
    ('bridge-learn', '2jmj7l5rSw0yVb/vlWAYkK/YBwk='),
    ('bridge-move', '2jmj7l5rSw0yVb/vlWAYkK/YBwk='),
    ('bridge-watch', '2jmj7l5rSw0yVb/vlWAYkK/YBwk=');

INSERT INTO world.player
    (id, account_name, name, race_id, active_class_id, base_class_id,
     level, exp, sp, max_hp, current_hp, max_mp, current_mp, x, y, z)
VALUES
    (1500000001, 'bridge-trade', 'BridgeTrader', 0, 0, 0,
     2, 68, 100, 200, 200, 100, 100, -71057, 258183, -3104),
    (1500000002, 'bridge-rise', 'BridgeRiser', 0, 0, 0,
     2, 68, 100, 200, 0, 100, 100, -71057, 258183, -3104),
    (1500000003, 'bridge-witness', 'BridgeWitness', 0, 0, 0,
     2, 68, 100, 200, 200, 100, 100, -72117, 258063, -3104),
    (1500000004, 'bridge-learn', 'BridgeLearner', 0, 0, 0,
     2, 68, 100, 200, 200, 100, 100, -71057, 257943, -3104),
    (1500000005, 'bridge-move', 'BridgeMover', 0, 0, 0,
     2, 68, 100, 200, 200, 100, 100, -71057, 258183, -3104),
    (1500000006, 'bridge-watch', 'BridgeWatcher', 0, 0, 0,
     2, 68, 100, 200, 200, 100, 100, -71057, 258183, -3104);

INSERT INTO world.item
    (id, item_template_id, owner_player_id, location, count)
VALUES (1500000010, 57, 1500000001, 'INVENTORY', 600);

-- Character creation normally writes these rows alongside the player. Keep
-- seeded characters on the same load path instead of invoking missing-row repair.
INSERT INTO world.player_birthday (player_id, created_on, gift_claimed_year)
SELECT id, CURRENT_DATE, EXTRACT(YEAR FROM CURRENT_DATE)::smallint
FROM world.player WHERE id BETWEEN 1500000001 AND 1500000006;

INSERT INTO world.player_recommendation_status
    (player_id, recommendations_left, recommendations_received, updated_at)
SELECT id, 3, 0, CURRENT_TIMESTAMP
FROM world.player WHERE id BETWEEN 1500000001 AND 1500000006;
