CREATE EXTENSION IF NOT EXISTS postgis;


-- GAME OBJECT
CREATE TABLE IF NOT EXISTS game_object (
    id          BIGINT PRIMARY KEY,
    object_type TEXT NOT NULL,
    name        TEXT,
    geom        GEOMETRY(Point, 4326) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_game_object_geom
    ON game_object USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_game_object_type
    ON game_object (object_type);
