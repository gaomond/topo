# topo

## Overview

A location-based cooperative exploration game. Players move through the real
world and lock in their positions; when the area of everyone's convex hull meets
a target, they compete on the number of objects (shrines, parks, …) inside it.

## Tech stack

- Kotlin / Spring Boot
- PostgreSQL + PostGIS
- Flyway
- Docker
- Leaflet (frontend map)

## Architecture / design decisions

- **Hexagonal architecture** — domain isolated from web/DB adapters.
- **Spatial logic in PostGIS** (convex hull, area, containment); CRUD via JPA,
  spatial queries via raw SQL.

## Getting started

```bash
# Requires JDK 25
docker compose up -d      # start PostgreSQL + PostGIS
./gradlew bootRun         # Flyway applies the schema on startup
```

## Data loading (OSM)

Game objects are imported from OpenStreetMap. See [docs/data-loading.md](docs/data-loading.md)
for local and production import steps.
