# 03-impl-notes.md — US-00 実装メモ

> 入力: `01-spec.md` / `02-plan.md`。本ストーリーはスキーマ層のみ（UseCase / inbound / UI なし）。
> 品質ゲート `./gradlew build` 緑（20 tests / ktlint 通過）を確認済み。

## 1. 追加・変更ファイル

### マイグレーション
- `src/main/resources/db/migration/V2__game_player.sql`（新規）
  - `CREATE TYPE game_status AS ENUM ('WAITING','ACTIVE','COMPLETED')`。
  - `game` テーブル（UUID 主キー、`status game_status NOT NULL`、`player_count INT NOT NULL`（上限 CHECK なし）、`object_type TEXT`、`area_threshold DOUBLE PRECISION`、`creator_player_id UUID`（NULL 許容）、`created_at TIMESTAMPTZ DEFAULT now()`、結果 4 カラム `polygon_geom GEOMETRY(Polygon,4326)` / `area_sqm` / `area_valid` / `object_count` は全て NULL 許容）。
  - `player` テーブル（UUID 主キー、`game_id UUID NOT NULL REFERENCES game(id)`（ON DELETE 句なし）、`display_name`、`joined_at TIMESTAMPTZ DEFAULT now()`、ライブ位置 `live_lat`/`live_lng`/`live_at`、`fixed_geom GEOMETRY(Point,4326)`、`confirmed_at` は全て NULL 許容）。
  - **循環解消**: `game.creator_player_id → player.id` の FK は player 作成後に `ALTER TABLE ... ADD CONSTRAINT fk_game_creator_player` で後付け。NULL 許容を維持。
  - `idx_player_game_id` を付与（参加者一覧・ポーリング検索用）。
  - V1（postgis 拡張＋`game_object`）は適用済みのため不変。

### Domain
- `domain/model/GameStatus.kt`（新規）: DB enum `game_status` / JSON 値と名称一致の純粋 enum。遷移ロジックは持たない（US-05/06/13 スコープ）。Spring/JPA/PostGIS 非依存。

### Adapter(outbound: persistence)
- `adapter/persistence/GameJpaEntity.kt` / `PlayerJpaEntity.kt`（新規）
  - **非空間カラムのみマッピング**。`polygon_geom` / `fixed_geom` は意図的に除外（地理空間は US-13 以降の生 SQL で扱い PostGIS を Domain/UseCase から隠す）。`ddl-auto=validate` はエンティティ未定義カラムを検証しないため除外しても validate は通る。
  - JPA エンティティのため `data class` を使わず通常 class。ID ベースの `equals`/`hashCode`（`hashCode` は `javaClass` 固定）。
  - enum マッピング: `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` + `@Enumerated(STRING)` + `columnDefinition="game_status"` で PostgreSQL named enum と対応（varchar 送信による型不一致を回避）。← 計画で最大リスクとした箇所、この 1 案に収束。
  - `PlayerJpaEntity.game_id` は `@ManyToOne` を張らず単純 UUID 保持（遅延ロードの罠回避、参照解決は UseCase/生 SQL に委譲）。
- `adapter/persistence/GameRepository.kt` / `PlayerRepository.kt`（新規）: `JpaRepository<_, UUID>` のみ。Domain ポート抽象は UseCase 登場の US-02 で導入予定（現段階は YAGNI）。

### テスト
- `src/test/kotlin/.../support/PostgisTestContainer.kt`（新規）: `imresamu/postgis:17-3.5-bookworm`（compose.yaml と同一）を static に 1 つ起動して全テスト共有。
- `src/test/kotlin/.../adapter/persistence/GamePlayerSchemaTest.kt`（新規）: `@DataJpaTest` + `@AutoConfigureTestDatabase(replace=NONE)` + `@DynamicPropertySource` で実 PostGIS に対し Level 3 検証。受け入れ条件 §2・テスト観点 §3 に 1:1 対応。
- `src/test/kotlin/.../TopoApplicationTests.kt`（変更）: data-jpa 導入によりフルコンテキスト起動が DataSource を要求するため、`@DynamicPropertySource` で共有 PostGIS コンテナへ接続。
- `src/test/resources/application.properties`（新規）: `ddl-auto=validate` / Flyway 有効 / locations 指定。
- `build.gradle.kts`（変更）: Testcontainers（`spring-boot-testcontainers` / `testcontainers-junit-jupiter` / `testcontainers-postgresql`）を testImplementation 追加。

## 2. オーケストレータによる修正（impl エージェント中断のため直接適用）

impl サブエージェントが API 切断・ストールで 2 回中断したため、診断済みの 2 点をオーケストレータが直接修正してゲートを緑にした:

1. **Spring Boot 4.1 のテストスライス・パッケージ移動**（`GamePlayerSchemaTest.kt`）:
   - `org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest` → `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`
   - `org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase` → `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`
   - 旧パッケージは SB4.1 で廃止。jar 内のクラス位置で確認。
2. **`TopoApplicationTests.contextLoads()` の DataSource 欠如**: data-jpa 追加でフルコンテキスト起動が DB を要求。共有 PostGIS コンテナに接続して解消。

## 3. 設計判断（計画踏襲）

- DB enum を維持（TEXT+CHECK への退避は不可）。
- 空間カラムは JPA 非マッピング、生 SQL（US-13 以降）に隠蔽。
- `creator_player_id` FK は後付けで循環解消。
- Domain ポートは US-02 へ引き継ぎ（UseCase 不在のため前倒ししない）。

## 4. 残課題 / 引き継ぎ

- `PostgreSQLContainer` の非推奨警告（Testcontainers）。動作影響なし。将来 `@ServiceConnection` への移行余地。
- Domain リポジトリポート抽象の導入は US-02。
