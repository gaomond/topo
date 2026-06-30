# 02-plan: US-00 `game` / `player` スキーマ構築

> 対象: 確定仕様 `.agent-pipeline/01-spec.md`（`game` / `player` テーブル＋ `game_status` enum の Flyway スキーマ定義と JPA マッピング）。
> 本ストーリーは「スキーマ層」のみ。UseCase / inbound コントローラ / UI は含まない（US-02 以降）。
> コードは書かない。実装単位・対象ファイル・完了条件・検証方法を Clean Architecture 依存順で定義する。

## 前提となる既存資産の確認結果

- `src/main/resources/db/migration/V1__init.sql`: `CREATE EXTENSION postgis` と `game_object`（GiST 含む）が定義済み。**本ストーリーは V1 を編集せず新規 `V2` を追加する**（適用済みマイグレーションの改変禁止／Flyway チェックサム整合のため）。
- `application.properties`: `spring.jpa.hibernate.ddl-auto=validate`、`spring.flyway.locations=classpath:db/migration`。**スキーマは Flyway が真実。JPA は validate のみ**なので、JPA エンティティのマッピングはマイグレーションと完全一致が必須。
- `build.gradle.kts`: Spring Data JPA / Flyway / postgresql ドライバは導入済み。**Testcontainers と PostGIS 用の Hibernate Spatial 型対応は未導入**。Level 3（`@DataJpaTest` + 実 PostGIS）の検証に追加が必要。
- `compose.yaml`: `imresamu/postgis:17-3.5-bookworm`。Testcontainers のイメージはこれに揃える。
- Domain には `ObjectType` / `AreaPreset` が既存。`game.status` に対応する `GameStatus` enum は未定義。
- 既存の縦串（`ConfigController` 〜 `domain/model`）でパッケージ構成の先例あり: domain は `domain/model`、inbound は `adapter/web`。本ストーリーで outbound 永続化 `adapter/persistence` の先例を確立する。

## 設計上の重要判断（実装前に固定）

- **空間カラムは JPA エンティティにマッピングしない**。`player.fixed_geom`（Point,4326）・`game.polygon_geom`（Polygon,4326）は US-13 以降に outbound の生 SQL で扱う方針（CLAUDE.md「地理空間は生 SQL、PostGIS を Domain/UseCase から隠す」）。JPA エンティティでは空間カラムを除外し、`ddl-auto=validate` を空間型でこけさせない。これにより Hibernate Spatial 導入を本ストーリーでは不要にできる。
- それ以外の全カラム（UUID / enum / int / double / timestamptz / boolean / text）は JPA マッピング対象。`game_status` は **DB enum 型**で、JPA 側のマッピング方式を実装単位 3/4 で確定する。
- `creator_player_id` は NULL 許容 FK。創成順序（game→player→UPDATE）の循環をスキーマで吸収する。
- **Domain ポート（リポジトリ抽象）は本ストーリーでは作らない**。UseCase が存在しない段階で抽象を前倒ししない（YAGNI）。US-02 で Domain ポートを定義し本 JPA リポジトリを実装に降格させる方針を後続計画へ引き継ぐ。

---

## 実装単位（依存順）

### 1. `GameStatus` enum — 層: domain
- 目的: `game.status` のドメイン値（`WAITING` / `ACTIVE` / `COMPLETED`）を純粋 Kotlin で表現し、DB enum 型と対応づける単一の真実を作る。既存 `ObjectType` の作法に倣う。
- 対象ファイル: `src/main/kotlin/com/github/gaomond/topo/domain/model/GameStatus.kt`（新規）
- 完了条件:
  - `enum class GameStatus { WAITING, ACTIVE, COMPLETED }` をユビキタス言語どおりに定義。
  - Spring / JPA / PostGIS に依存しない（Domain 純粋性）。
  - DB enum 値（大文字 3 値）と enum 名が一致するため変換用フィールドは不要。
  - 遷移ロジックは持たない（US-05/06/13 スコープ）旨を KDoc に明示。
- 検証方法: Level 1（純粋 Kotlin・モック不要）。`test_values_always_returnsWaitingActiveCompletedInOrder` で 3 値・順序を検証。

### 2. `V2__game_player.sql` マイグレーション — 層: adapter(outbound / schema)
- 目的: `game_status` enum・`game`・`player` を Flyway で生成し、受け入れ条件のスキーマ制約をすべて満たす。
- 対象ファイル: `src/main/resources/db/migration/V2__game_player.sql`（新規。V1 は触らない）
- 完了条件（受け入れ条件 §2 に対応）:
  - `CREATE TYPE game_status AS ENUM ('WAITING','ACTIVE','COMPLETED');`
  - `game` テーブル:
    - `id UUID PRIMARY KEY`、`status game_status NOT NULL`、`player_count INT NOT NULL`（**上限 CHECK を張らない**）、`object_type TEXT NOT NULL`、`area_threshold DOUBLE PRECISION NOT NULL`、`creator_player_id UUID NULL`、`created_at TIMESTAMPTZ NOT NULL DEFAULT now()`。
    - 結果カラム（NULL 許容）: `polygon_geom GEOMETRY(Polygon,4326)`、`area_sqm DOUBLE PRECISION`、`area_valid BOOLEAN`、`object_count INT`。
  - `player` テーブル:
    - `id UUID PRIMARY KEY`、`game_id UUID NOT NULL REFERENCES game(id)`（**ON DELETE 句を付けない**）、`display_name TEXT NULL`、`joined_at TIMESTAMPTZ NOT NULL DEFAULT now()`、`live_lat/live_lng DOUBLE PRECISION NULL`、`live_at TIMESTAMPTZ NULL`、`fixed_geom GEOMETRY(Point,4326) NULL`、`confirmed_at TIMESTAMPTZ NULL`。
  - `game.creator_player_id` の FK は **player 定義後に `ALTER TABLE game ADD CONSTRAINT ... FOREIGN KEY (creator_player_id) REFERENCES player(id)`** で後付けし、game↔player の循環を解消する。NULL 許容を維持。
  - カラム命名は snake_case、ユビキタス言語（`area_threshold` / `area_sqm` / `object_count` 等）に一致。
- 検証方法: 実装単位 6 のクリーン DB 再現テストで一括検証。任意で `docker compose up` 後にローカル適用して目視確認。

### 3. `GameJpaEntity` + `PlayerJpaEntity`（JPA・非空間） — 層: adapter(outbound / persistence)
- 目的: `game` / `player` の非空間カラムを JPA エンティティにマッピングし通常 CRUD を可能にする。空間カラムは除外。
- 対象ファイル:
  - `src/main/kotlin/com/github/gaomond/topo/adapter/persistence/GameJpaEntity.kt`（新規）
  - `src/main/kotlin/com/github/gaomond/topo/adapter/persistence/PlayerJpaEntity.kt`（新規）
- 完了条件:
  - `GameJpaEntity`: `@Entity @Table(name = "game")`、`@Id id: UUID`、`status: GameStatus`（マッピング方式は単位 4）、`playerCount: Int`、`objectType: String`、`areaThreshold: Double`、`creatorPlayerId: UUID?`、`createdAt: Instant`、結果カラム `areaSqm: Double?` / `areaValid: Boolean?` / `objectCount: Int?`。**`polygon_geom` はフィールドを持たせない**。
  - `PlayerJpaEntity`: `@Entity @Table(name = "player")`、`@Id id: UUID`、`gameId: UUID`、`displayName: String?`、`joinedAt: Instant`、`liveLat: Double?` / `liveLng: Double?` / `liveAt: Instant?`、`confirmedAt: Instant?`。**`fixed_geom` はフィールドを持たせない**。FK は単純な `UUID` 保持（`@ManyToOne` は本ストーリー不要）。
  - `timestamptz` 対応は `Instant`（UTC 一貫）を第一候補。プロジェクト内で型を統一する。
  - kotlin nullable 型で NOT NULL / NULL を表現。`build.gradle.kts` の `allOpen(@Entity)` と JPA プラグインで open/no-arg は満たされる。
  - `ddl-auto=validate` 配下で、エンティティに無い空間カラムは検証対象外（除外方針で validate は通る）。
- 検証方法: 実装単位 6 の `@DataJpaTest` で save/findById により非空間カラムのラウンドトリップを検証。

### 4. `game_status` の JPA enum マッピング確定（単位 3 に内包・要 PoC） — 層: adapter(outbound)
- 目的: PostgreSQL `game_status` enum と Kotlin `GameStatus` の JPA マッピング手段を 1 つに固定する。Hibernate × PostgreSQL enum は単純な `EnumType.STRING` で型不一致（`column "status" is of type game_status but expression is of type character varying`）になり得るため事前検証する。
- 対象ファイル: `GameJpaEntity.kt`（フィールドアノテーション）。設定はエンティティ側に閉じ、`application.properties` には enum 用の型設定を足さない方針。
- 完了条件（候補を優先順で評価し、最初に通ったものを採用）:
  1. `@Enumerated(EnumType.STRING)` のみ。
  2. 不可なら `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` を採用（Hibernate 6 系の PostgreSQL named enum 対応）。
  3. それでも不可なら `columnDefinition = "game_status"` を併用。
  - いずれも **DB enum を維持**（仕様 D2 決定）。`TEXT + CHECK` への退避は受け入れ条件「enum 型で 3 値以外格納不可」に反するため採らない。
- 検証方法: 実装単位 6 の `@DataJpaTest`。`GameStatus.ACTIVE` を save→find で往復一致。許可外文字列の直 INSERT 失敗は別テストで確認。

### 5. `GameRepository` / `PlayerRepository` — 層: adapter(outbound)
- 目的: 受け入れ条件「JPA エンティティから通常 CRUD がマッピングできる」を満たす最小リポジトリ。
- 対象ファイル:
  - `src/main/kotlin/com/github/gaomond/topo/adapter/persistence/GameRepository.kt`（新規）
  - `src/main/kotlin/com/github/gaomond/topo/adapter/persistence/PlayerRepository.kt`（新規）
- 完了条件:
  - それぞれ `JpaRepository<GameJpaEntity, UUID>` / `JpaRepository<PlayerJpaEntity, UUID>` を継承するのみ。
  - 空間クエリ・派生クエリは本ストーリーでは追加しない（US-13 以降）。
  - Domain ポートは未導入（理由は「設計上の重要判断」参照）。
- 検証方法: 実装単位 6 のテストから save/findById/findAll を呼んで確認。

### 6. テスト基盤整備 + スキーマ/永続化テスト — 層: adapter(test)
- 目的: 受け入れ条件・テスト観点（マイグレーション再現性／enum 制約／FK 整合／NULL 許容／循環解消／空間型／上限なし／JPA CRUD）を実 PostGIS で検証する。
- 対象ファイル:
  - `build.gradle.kts`（編集）: `testImplementation` に Testcontainers（`org.testcontainers:junit-jupiter`、`org.testcontainers:postgresql`、必要なら `org.springframework.boot:spring-boot-testcontainers`）を追加。バージョンは Spring Boot 4 の BOM 管理に委ねる。
  - `src/test/resources/application.properties`（新規・任意）: テスト時に Flyway 有効・`ddl-auto=validate`・Testcontainers 動的データソースを使う設定。
  - `src/test/kotlin/com/github/gaomond/topo/adapter/persistence/GamePlayerSchemaTest.kt`（新規）: `@DataJpaTest(replace = NONE)` + `@Testcontainers` + `imresamu/postgis:17-3.5-bookworm`。
  - 必要に応じて共通基底 `src/test/kotlin/com/github/gaomond/topo/support/PostgisTestContainer.kt`（新規）。
- 完了条件（テスト名は `test_Action_Condition_Result`、対象は必ず `src/` から import）:
  - `test_flywayMigration_onCleanDb_createsGamePlayerAndEnum`: クリーン DB に V1+V2 が適用され起動成功（コンテナ起動＝Flyway 成功）。
  - `test_insertGame_withInvalidStatus_failsByEnumConstraint`: 生 SQL で許可外 `status` INSERT が例外。
  - `test_insertPlayer_withNonExistentGameId_failsByFk`: 不在 `game_id` で FK 違反。
  - `test_updateGame_withNonExistentCreatorPlayerId_failsByFk`: `creator_player_id` 不在 UUID で FK 違反。
  - `test_insertGame_withNullableResultColumns_succeeds`: 結果 4 カラム・`creator_player_id` を NULL のまま INSERT 可。
  - `test_insertPlayer_withNullableSpatialAndLiveColumns_succeeds`: `fixed_geom`・`live_*`・`confirmed_at` を NULL のまま INSERT 可。
  - `test_createGameThenCreatorThenUpdate_inOrder_resolvesCircularReference`: 1.6 の 3 ステップ（game 作成→creator player 作成→`creator_player_id` UPDATE）成功。
  - `test_insertSpatialGeoms_with4326PointAndPolygon_storesAndReads`: `fixed_geom`（Point,4326）・`polygon_geom`（Polygon,4326）を生 SQL（`ST_GeomFromText`/`ST_SetSRID`）で格納・取得（空間カラムは JPA 非対象のため生 SQL で検証）。
  - `test_insertGame_withLargePlayerCount_isNotRejected`: `player_count = 100` でも CHECK で弾かれない。
  - `test_saveAndFind_gameEntity_roundTripsNonSpatialColumns` / `..._playerEntity_...`: JPA 経由 CRUD ラウンドトリップ（enum・timestamptz・NULL 許容含む）。
- 検証方法: `./gradlew build`（test + ktlint）。品質ゲート通過を「完了」とする。Testcontainers は Docker 必須（リスク欄）。

---

## 実装順（依存順サマリ）
1. `GameStatus`（Domain）
2. `V2__game_player.sql`（Flyway スキーマ）
3. `GameJpaEntity` / `PlayerJpaEntity`（JPA・非空間）＋ enum マッピング方式の PoC 確定
4. `GameRepository` / `PlayerRepository`
5. テスト基盤（Testcontainers 追加）+ スキーマ/永続化検証テスト

> Domain → Adapter(outbound: schema, persistence) の順。本ストーリーに UseCase / inbound / UI は無い。

## リスク・前提
- **Hibernate × PostgreSQL enum マッピング**: 最大の不確実性。`EnumType.STRING` 単独で型不一致になる可能性が高い。単位 3/4 で PoC し、`@JdbcTypeCode(NAMED_ENUM)` / `columnDefinition` まで含め 1 案に収束。DB enum を捨てて TEXT+CHECK にする回避は受け入れ条件に反するため不可。
- **空間カラムを JPA 非マッピングにする方針**: `ddl-auto=validate` はエンティティに無いカラムを検証しないため、除外しても validate は通る前提。エンティティに `geom` を持たせると Hibernate Spatial 依存が必要になりスコープが膨らむため、除外方針を維持。
- **Testcontainers 未導入**: `build.gradle.kts` への依存追加が必要。Docker 実行環境（ローカル/CI）が前提。Docker 不在環境では Level 3 テストがスキップ/失敗する点を finish 段で確認。
- **適用済みマイグレーションの不変性**: `V1__init.sql` は編集しない。すべて `V2` に追加。`build/resources/main/db/migration/V1__init.sql` はビルド生成物なので無視。
- **Domain ポート未導入は意図的**: UseCase が無い段階でリポジトリ抽象を作らず、JPA リポジトリ直置き。US-02 で Domain ポート定義＋本リポジトリを実装に降格させる設計変更を後続計画へ引き継ぐ。
- **`creator_player_id` FK の後付け順序**: 同一マイグレーション内で player 定義後に `ALTER TABLE ... ADD CONSTRAINT`。テーブル/制約の記述順を誤ると循環で失敗するため注意。
- **タイムスタンプ型統一**: `timestamptz` ↔ Kotlin は `Instant`（UTC 一貫）を第一候補。`OffsetDateTime` でも可だがプロジェクト内で統一する。
