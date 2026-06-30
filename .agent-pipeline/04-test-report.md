# 04-test-report.md — US-00 テスト結果

> 品質ゲート `./gradlew build` 実行結果: **BUILD SUCCESSFUL**（20 tests / 0 failed、ktlint 通過）。
> Level 3 は実 PostGIS（Testcontainers `imresamu/postgis:17-3.5-bookworm`）に対して実行。

## 実行結果サマリ

| 項目 | 結果 |
| --- | --- |
| `./gradlew build` | SUCCESS |
| テスト総数 | 20 |
| 失敗 | 0 |
| ktlint（main / test） | 通過 |

警告: `PostgreSQLContainer` 非推奨（Testcontainers）、`sun.misc.Unsafe`（Kotlin コンパイラ）。いずれも動作影響なし。

## GamePlayerSchemaTest（Level 3・実 PostGIS）

受け入れ条件 §2 / テスト観点 §3 への対応:

| テスト | 検証する観点 |
| --- | --- |
| `test_flywayMigration_onCleanDb_createsGamePlayerAndEnum` | マイグレーション再現性。`game_status` enum が 3 値順で生成、`game`/`player` テーブル存在 |
| `test_insertGame_withInvalidStatus_failsByEnumConstraint` | enum 制約。許可外値 `'PAUSED'` の INSERT 失敗 |
| `test_insertPlayer_withNonExistentGameId_failsByFk` | FK 整合。存在しない `game_id` の player INSERT 失敗 |
| `test_updateGame_withNonExistentCreatorPlayerId_failsByFk` | FK 整合。存在しない `creator_player_id` UPDATE 失敗 |
| `test_insertGame_withNullableResultColumns_succeeds` | 結果 4 カラム + `creator_player_id` の NULL 許容 |
| `test_insertPlayer_withNullableSpatialAndLiveColumns_succeeds` | `fixed_geom`/`live_*`/`confirmed_at`/`display_name` の NULL 許容 |
| `test_createGameThenCreatorThenUpdate_inOrder_resolvesCircularReference` | 循環解消（1.6 の 3 ステップ）|
| `test_insertSpatialGeoms_with4326PointAndPolygon_storesAndReads` | 空間型。4326 の Point/Polygon を格納・SRID/型を取得 |
| `test_insertGame_withLargePlayerCount_isNotRejected` | 上限なし。`player_count=100` が CHECK で弾かれない |
| `test_saveAndFind_gameEntity_roundTripsNonSpatialColumns` | JPA CRUD。GameJpaEntity（enum 含む）ラウンドトリップ |
| `test_saveAndFind_playerEntity_roundTripsNonSpatialColumns` | JPA CRUD。PlayerJpaEntity ラウンドトリップ |

## TopoApplicationTests

| テスト | 内容 |
| --- | --- |
| `contextLoads` | data-jpa 導入後、共有 PostGIS コンテナに接続してフルコンテキスト起動を検証 |

## 備考

- 全テストは `PostgisTestContainer` の static 共有コンテナを使用し、起動コストを 1 回に抑制。
- テストは全て `src/` のプロダクションクラス（`GameStatus`/`GameJpaEntity`/`PlayerJpaEntity`/`GameRepository`/`PlayerRepository`）を import し、再定義はしていない（CLAUDE.md 準拠）。
