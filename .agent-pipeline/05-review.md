# 05-review

## 判定: 承認

US-00（`game` / `player` スキーマ構築）について、受け入れ条件 §2 の全項目・テスト観点 §3 の全項目が実コードとテストで満たされていることを確認した。Clean Architecture の依存方向、ユビキタス言語、命名規約への重大な逸脱はない。オーケストレータが直接適用した 2 点（SB4.1 のテストスライス・パッケージ移動 / `TopoApplicationTests` の DataSource 接続追加）も妥当。ソースは編集していない。

## 指摘

- [med] `adapter/persistence/GameJpaEntity.kt:39` `object_type` を `objectType: String`（生 String）でマッピングしている。Domain には既に `ObjectType` enum（`jsonValue` 付き）が存在し、ユビキタス言語でも「オブジェクト種別 = objectType」は値域の決まった概念。スキーマが `TEXT` である点は DESIGN.md / 仕様どおりで問題ないが、エンティティ層で `String` のまま素通しすると型安全性とドメイン意図の表現が弱い。本ストーリーは「格納できること」の担保が目的でありこの段階では許容範囲だが、`ObjectType` への変換責務を UseCase 登場時（US-02）にどこへ置くか（エンティティに `@Convert` を持たせるか、UseCase 境界で変換するか）を引き継ぎ事項として明確化しておくのが望ましい。承認を妨げるものではない。

- [low] `support/PostgisTestContainer.kt:14` / `03-impl-notes.md §4` で既知のとおり `PostgreSQLContainer` 直起動は非推奨警告が出る。動作影響はないが、`@ServiceConnection` ベースへの移行余地を引き継ぎ済みである点を確認。今ストーリーでの対応は不要。

- [low] `GamePlayerSchemaTest.kt:118` の `test_insertPlayer_withNonExistentGameId_failsByFk` は `insertPlayerRaw` 内で `game_id` のみ指定し他カラムを default に委ねている。`game_id NOT NULL` の FK 違反を正しく突いており検証として妥当だが、テスト観点「存在しない creator_player_id を入れると失敗」(L96) と対になる FK ケースとして両方が独立テスト化されている点は良好。指摘というより確認事項。

## 良かった点

- 受け入れ条件 §2・テスト観点 §3 に対しテストが 1:1 で対応しており、自作自演ではなく実 PostGIS（Testcontainers）に対する実質的検証になっている。特に enum 制約（許可外 `'PAUSED'` の INSERT 失敗）、FK 整合（不在 `game_id` / `creator_player_id` の双方）、循環解消（1.6 の 3 ステップを順序どおり再現）、空間型（`ST_SRID` / `GeometryType` で 4326・Point/Polygon を確認）まで踏み込んでおり、機械的に通すだけの薄いテストになっていない。
- 空間カラム（`polygon_geom` / `fixed_geom`）を JPA エンティティから意図的に除外し、PostGIS を Domain/UseCase から隠す方針を貫いている。これにより Hibernate Spatial 依存を回避しつつ `ddl-auto=validate` を通す設計判断が一貫しており、CLAUDE.md「地理空間は生 SQL、PostGIS を Domain/UseCase から隠す」に正確に従っている。
- Clean Architecture 依存方向が保たれている。`GameStatus`（domain）は Spring/JPA/PostGIS を import せず純粋。エンティティ・リポジトリは adapter/persistence に閉じ、adapter 同士の直接呼び出しもない。Domain ポート抽象を UseCase 不在の段階で前倒しせず（YAGNI）US-02 へ引き継ぐ判断も妥当。
- JPA エンティティを `data class` ではなく通常 class とし、ID ベースの `equals` / `javaClass` 固定 `hashCode` を実装している点は Hibernate のエンティティ同一性の定石に沿っており、`data class` エンティティの落とし穴を回避できている。
- DB enum マッピングを `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` + `@Enumerated(STRING)` + `columnDefinition` の 1 案に収束させ、計画で最大リスクとした型不一致を実テスト（ACTIVE の save/find 往復）で裏取りしている。
- カラム命名は snake_case、Kotlin 側は camelCase で、ユビキタス言語（`area_threshold` / `area_sqm` / `object_count` / `creator_player_id` / `live_lat` 等）に一致。スキーマは DESIGN.md と整合し、`fixed_geom`（geometry 化）の逸脱も仕様 D3 として明示的に合意済み。
- `V1` を不変に保ち `V2` を新規追加、`creator_player_id` FK を player 定義後に `ALTER TABLE ... ADD CONSTRAINT` で後付けして循環を解消する手順が、Flyway チェックサム整合と創成順序の両面で正しい。

判定: 承認。
