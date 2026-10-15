-- State that 2.0 kept in files (plan 4.6), part 2: the login announcements,
-- formerly data/announcements.txt, and the GM teleport bookmarks, formerly
-- data/html/admin/tele/bookmark.txt.

CREATE TABLE announcement (
	position integer PRIMARY KEY CHECK (position >= 0),
	message text NOT NULL
);
COMMENT ON TABLE announcement IS 'Announcements shown to every player who enters the world (Announcements), edited with //announce_menu. The game replaces all rows on every change.';
COMMENT ON COLUMN announcement.position IS 'Place in the list, from 0; the GM menu deletes by this number.';
COMMENT ON COLUMN announcement.message IS 'Text of the announcement.';

-- The text that 2.0 shipped in data/announcements.txt.
INSERT INTO announcement (position, message) VALUES (0, 'Write your announcements here!');

CREATE TABLE admin_teleport_bookmark (
	name text PRIMARY KEY,
	saved_order bigint GENERATED ALWAYS AS IDENTITY,
	x integer NOT NULL,
	y integer NOT NULL,
	z integer NOT NULL
);
COMMENT ON TABLE admin_teleport_bookmark IS 'Teleport bookmarks shared by all GMs (//bookmark, //delbookmark in AdminTeleport).';
COMMENT ON COLUMN admin_teleport_bookmark.name IS 'Name of the bookmark, one word: the GM menu passes it as a bypass argument.';
COMMENT ON COLUMN admin_teleport_bookmark.saved_order IS 'Increases with every save; the menu lists bookmarks in this order.';
COMMENT ON COLUMN admin_teleport_bookmark.x IS 'World X coordinate.';
COMMENT ON COLUMN admin_teleport_bookmark.y IS 'World Y coordinate.';
COMMENT ON COLUMN admin_teleport_bookmark.z IS 'World Z coordinate.';
