-- osm2pgsql で投入した osm_game_object_stage から game_object へ移送する。
-- node/way 間の id 衝突は先勝ち（ON CONFLICT DO NOTHING）。Pythonスクリプトの dedup と同じ方針。
-- 冪等にするため、対象 object_type を一旦削除してから入れ直す。

BEGIN;

DELETE FROM game_object
WHERE object_type IN ('shrine','temple','school','convenience_store','park','station');

INSERT INTO game_object (id, object_type, name, geom)
SELECT id, object_type, name, geom
FROM osm_game_object_stage
ON CONFLICT (id) DO NOTHING;

COMMIT;

-- 確認用
SELECT object_type, count(*)
FROM game_object
GROUP BY object_type
ORDER BY count(*) DESC;
