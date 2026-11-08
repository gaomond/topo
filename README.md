# topo

<img src="./image.png" width=500>

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
cd frontend
npm run dev
```

## API仕様の閲覧

バックエンドの起動後、[Swagger UI](http://localhost:8080/swagger-ui.html) で
実装済みAPIの入力・応答・エラーを確認できる。
機械可読の [OpenAPI JSON](http://localhost:8080/v3/api-docs) も取得できる。
`server.port` を変更した場合は、URLのポートも合わせる。

仕様はControllerのマッピングとRequest／Responseから実行時に生成する。
自動推論できない説明・制約・エラー応答は、それらのWeb側クラスの
OpenAPIアノテーションへ直接記載する。別の手書きJSON／YAMLは管理しない。
`operationId` はControllerのメソッド名と一致するため、実際の呼び出しから
担当UseCaseを確認できる。業務ルールと設計判断は `docs/` の仕様書を参照する。

画面のタグは「ゲームの準備」（設定・作成・参加・開始）と
「プレイ中」（状態取得・位置更新）でまとめる。
「プレイ結果」は、結果取得の実装時に追加する。

## Data loading (OSM)

Game objects are imported from OpenStreetMap. See [docs/data-loading.md](docs/data-loading.md)
for local and production import steps.
