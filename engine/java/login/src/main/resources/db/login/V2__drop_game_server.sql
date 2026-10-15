-- Login and world run as two modules of one process (ADR-0003). The world no longer registers with the
-- login server over a socket: it is one world with one id, and the login module reaches it through a Java
-- port. The table of registered world servers has no reader or writer left, so it goes.
--
-- The account keeps last_world_id: the client sends the id of the world it chose, and the server list
-- preselects it. The column is a plain number now, without a reference to a table.

ALTER TABLE account DROP CONSTRAINT account_last_world_id_fkey;
DROP INDEX account_last_world_id_idx;
DROP TABLE game_server;

COMMENT ON SCHEMA login IS 'Player accounts. Owned by the login module; managed by Flyway.';
COMMENT ON COLUMN account.last_world_id IS 'Id of the world the account entered last, as the client sent it; NULL if none yet.';
