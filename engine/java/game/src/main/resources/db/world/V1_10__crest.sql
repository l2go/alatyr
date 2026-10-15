-- State that 2.0 kept in files (plan 4.6), part 4: clan and alliance crest
-- images, formerly data/crests/Crest_<id>.bmp, Crest_Large_<id>.bmp and
-- AllyCrest_<id>.bmp.

CREATE TABLE crest (
	id integer PRIMARY KEY,
	kind text NOT NULL CHECK (kind IN ('clan', 'clan_large', 'alliance')),
	image bytea NOT NULL
);
COMMENT ON TABLE crest IS 'Crest images that clans and alliances upload (CrestCache). The id comes from the IdFactory object id space; clan.crest_id, clan.large_crest_id and clan.alliance_crest_id refer to it. A new image gets a new id, so clients that cached the old one ask again.';
COMMENT ON COLUMN crest.id IS 'Crest id the client asks for.';
COMMENT ON COLUMN crest.kind IS 'clan: 16x12 clan crest; clan_large: large clan crest; alliance: 8x12 alliance crest.';
COMMENT ON COLUMN crest.image IS 'Image as the client uploaded it (BMP or DDS bytes); the server does not decode it.';

-- The crest ids carry no foreign key: the client asks for a crest by id, and
-- a 2.0 server could lose crest files while clans still named them.
COMMENT ON COLUMN clan.crest_id IS 'Clan crest the client shows, crest.id of kind clan (id from the object id space); NULL when the clan has no crest.';
COMMENT ON COLUMN clan.large_crest_id IS 'Large clan crest the client shows, crest.id of kind clan_large; NULL when the clan has no large crest.';
COMMENT ON COLUMN clan.alliance_crest_id IS 'Alliance crest the client shows, crest.id of kind alliance, copied to every member clan; the leader clan row is authoritative. NULL when there is no alliance crest.';
