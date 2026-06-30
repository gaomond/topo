# Data loading (OSM)

Game objects (shrines, temples, schools, convenience stores, parks, stations)
are imported from OpenStreetMap. The table schema is owned by Flyway; bulk data
is loaded separately with [osm2pgsql](https://osm2pgsql.org/) and never goes
through migrations.

Type mapping lives in [`tools/osm/game_object.lua`](../tools/osm/game_object.lua)
(`classify()`). Nodes become points, ways become their centroid; OSM ids are
reused and node/way id collisions are dropped via `ON CONFLICT DO NOTHING`.

## Prerequisites

- `osm2pgsql` (flex output) — `brew install osm2pgsql`
- An OSM extract from [Geofabrik](https://download.geofabrik.de/) in the project
  root (e.g. `japan-latest.osm.pbf`). Not committed to the repo.

## Local

```bash
# 1. Import the extract into a staging table
PGPASSWORD=topo osm2pgsql \
  --output=flex --style=tools/osm/game_object.lua \
  --database=topo --user=topo --host=localhost --port=5432 \
  --slim --drop --cache=2000 \
  japan-latest.osm.pbf

# 2. Move staged rows into game_object (idempotent)
docker compose exec -T db psql -U topo -d topo -v ON_ERROR_STOP=1 \
  -f - < tools/osm/load_into_game_object.sql

# 3. Verify
docker compose exec -T db psql -U topo -d topo -c \
  "SELECT object_type, count(*) FROM game_object GROUP BY object_type ORDER BY count(*) DESC;"
```

## Production

Schema is created by Flyway on the production database. Only the data is shipped:
run the local import above, then dump and restore `game_object`.

```bash
# Dump local data (schema is managed by Flyway, so data only)
docker compose exec -T db pg_dump -U topo -d topo -t game_object --data-only \
  > game_object.sql

# Restore into the production database
psql "$PROD_DATABASE_URL" -f game_object.sql
```

For large dumps use the custom format (`pg_dump -Fc` + `pg_restore`).

## Adding object types

Add a branch in `classify()` in `tools/osm/game_object.lua`, and add the type to
the `DELETE ... IN (...)` list in `tools/osm/load_into_game_object.sql`.
