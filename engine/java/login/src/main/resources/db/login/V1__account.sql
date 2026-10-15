-- Login schema baseline: player accounts and the registered world servers.
-- Only the login module reads or writes this schema. The world module never
-- sees password hashes.

CREATE EXTENSION IF NOT EXISTS citext SCHEMA public;

COMMENT ON SCHEMA login IS 'Accounts and world server registrations. Owned by the login module; managed by Flyway.';

CREATE TABLE game_server (
	id smallint PRIMARY KEY CHECK (id > 0),
	registration_key text NOT NULL,
	host text NOT NULL
);
COMMENT ON TABLE game_server IS 'World servers allowed to register with this login server.';
COMMENT ON COLUMN game_server.registration_key IS 'Registration key the world server presents, as a hexadecimal string.';
COMMENT ON COLUMN game_server.host IS 'Host name or address the world server registered from.';

CREATE TABLE account (
	name citext PRIMARY KEY,
	password_hash text NOT NULL,
	access_level smallint NOT NULL DEFAULT 0,
	last_active_at timestamptz,
	last_ip inet,
	last_world_id smallint REFERENCES game_server ON DELETE SET NULL,
	birthday_on date NOT NULL DEFAULT DATE '1900-01-01'
);
COMMENT ON TABLE account IS 'A player account. Characters reference it by name (world.player.account_name).';
COMMENT ON COLUMN account.name IS 'Login name, unique without regard to case.';
COMMENT ON COLUMN account.password_hash IS 'Base64 of the unsalted SHA-1 digest of the password (legacy format, kept so existing accounts still log in).';
COMMENT ON COLUMN account.access_level IS 'Access level: 0 is a normal player, a negative value is a ban, a positive value grants GM rights.';
COMMENT ON COLUMN account.last_active_at IS 'Last successful login; NULL if the account never logged in.';
COMMENT ON COLUMN account.last_ip IS 'Address of the last successful login; NULL if the account never logged in.';
COMMENT ON COLUMN account.last_world_id IS 'World server the account entered last; NULL if none yet.';
CREATE INDEX account_last_world_id_idx ON account (last_world_id);
COMMENT ON COLUMN account.birthday_on IS 'Birth date the player gave; the client uses it for age checks.';
