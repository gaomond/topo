\set ON_ERROR_STOP on
BEGIN;

-- クライアント側の作業ディレクトリにあるshrine.csvを読み込む。
-- 型・SRID・重複IDは本テーブルに触る前に検証する。
CREATE TEMP TABLE shrine_import_stage (
    id bigint PRIMARY KEY,
    object_type text NOT NULL CHECK (object_type = 'shrine'),
    name text,
    geom geometry(Point, 4326) NOT NULL CHECK (
        NOT ST_IsEmpty(geom)
        AND ST_X(geom) BETWEEN -180 AND 180
        AND ST_Y(geom) BETWEEN -90 AND 90
    )
) ON COMMIT DROP;

\copy shrine_import_stage (id, object_type, name, geom) FROM 'shrine.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')

-- 既存の別種別を同じIDで上書きしない。検証と反映の間の競合も防ぐ。
LOCK TABLE game_object IN SHARE ROW EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM shrine_import_stage AS source
        JOIN game_object AS target USING (id)
        WHERE target.object_type <> 'shrine'
    ) THEN
        RAISE EXCEPTION 'Shrine import conflicts with an existing non-shrine ID';
    END IF;
END;
$$;

INSERT INTO game_object (id, object_type, name, geom)
SELECT id, object_type, name, geom FROM shrine_import_stage
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    geom = EXCLUDED.geom;

COMMIT;
