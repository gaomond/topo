-- osm2pgsql flex output スタイル
-- 役割: 日本全体OSMから「ゲームお題候補」の地物を判定し、ステージングテーブルへ投入する。
--
-- 設計メモ:
--   - Flyway管理の game_object を osm2pgsql に直接所有させると壊すため、ここでは
--     専用ステージングテーブル osm_game_object_stage に入れる。後段の INSERT...SELECT で
--     game_object へ移す（手順書の二段構え / 代替案）。
--   - node は点、way は重心(centroid)を点として扱う（Pythonスクリプト準拠）。
--   - id は OSM id をそのまま使う。node/way 間の id 衝突は後段 ON CONFLICT DO NOTHING で先勝ち。
--   - 出力 geom は 4326（game_object と同じSRID）。

local stage = osm2pgsql.define_table({
  name = 'osm_game_object_stage',
  -- 複合主キーにせず id 単独。osm2pgsql には ids 不要（自前で id カラムを持つ）。
  columns = {
    { column = 'id',          type = 'bigint', not_null = true },
    { column = 'object_type', type = 'text',   not_null = true },
    { column = 'name',        type = 'text' },
    { column = 'geom',        type = 'point',  projection = 4326, not_null = true },
  },
})

-- タグ条件 → object_type の判定。該当しなければ nil。
local function classify(tags)
  if tags.amenity == 'place_of_worship' then
    if tags.religion == 'shinto'   then return 'shrine' end
    if tags.religion == 'buddhist' then return 'temple' end
    return nil
  end
  if tags.amenity == 'school'    then return 'school' end
  if tags.shop == 'convenience'  then return 'convenience_store' end
  if tags.leisure == 'park'      then return 'park' end
  if tags.railway == 'station'   then return 'station' end
  return nil
end

function osm2pgsql.process_node(object)
  local object_type = classify(object.tags)
  if not object_type then return end
  stage:insert({
    id = object.id,
    object_type = object_type,
    name = object.tags.name,
    geom = object:as_point(),
  })
end

function osm2pgsql.process_way(object)
  local object_type = classify(object.tags)
  if not object_type then return end
  -- 閉じた way（敷地ポリゴン）は面の重心、そうでなければ線の重心を点として使う。
  local geom
  if object.is_closed then
    geom = object:as_polygon():centroid()
  else
    geom = object:as_linestring():centroid()
  end
  stage:insert({
    id = object.id,
    object_type = object_type,
    name = object.tags.name,
    geom = geom,
  })
end
