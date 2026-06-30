# 06-commit-ready

## 変更サマリ

US-00（`game` / `player` スキーマ構築）。本編ループ（US-02以降）の全 API が依存する基盤テーブルを Flyway マイグレーションと JPA エンティティで整備した。

- **Domain**: `GameStatus` enum（`WAITING` / `ACTIVE` / `COMPLETED`）を新規追加。Spring/JPA/PostGIS 非依存の純粋 Kotlin。
- **スキーマ（Flyway）**: `V2__game_player.sql` を新規追加。`game_status` enum・`game`・`player` テーブルを定義。`creator_player_id` ↔ `player.game_id` の循環参照は FK 後付け（`ALTER TABLE ... ADD CONSTRAINT`）で解消。空間カラム（`polygon_geom` / `fixed_geom`）は `geometry(...,4326)`。V1（`postgis` 拡張 + `game_object`）は不変のまま。
- **Adapter(outbound: persistence)**: `GameJpaEntity` / `PlayerJpaEntity`（非空間カラムのみマッピング、空間カラムは意図的に除外し US-13 以降の生 SQL に隠蔽）、`GameRepository` / `PlayerRepository`（`JpaRepository` の薄いリポジトリ。Domain ポート抽象は US-02 へ引き継ぎ）。
- **テスト基盤**: Testcontainers（`imresamu/postgis:17-3.5-bookworm`）を `build.gradle.kts` に追加。共有コンテナ `PostgisTestContainer` を新設し、`GamePlayerSchemaTest`（Level 3・実 PostGIS、11 テスト）で受け入れ条件 §2・テスト観点 §3 を 1:1 検証。`TopoApplicationTests.contextLoads` も data-jpa 導入に伴い同コンテナへ接続するよう変更。

品質ゲート `./gradlew build` は **SUCCESS**（20 tests / 0 failed、ktlint 通過）。レビューは `.agent-pipeline/05-review.md` にて **承認**済み。

## コミット対象ファイル

### 新規

- `src/main/kotlin/com/github/gaomond/topo/domain/model/GameStatus.kt`
- `src/main/kotlin/com/github/gaomond/topo/adapter/persistence/GameJpaEntity.kt`
- `src/main/kotlin/com/github/gaomond/topo/adapter/persistence/PlayerJpaEntity.kt`
- `src/main/kotlin/com/github/gaomond/topo/adapter/persistence/GameRepository.kt`
- `src/main/kotlin/com/github/gaomond/topo/adapter/persistence/PlayerRepository.kt`
- `src/main/resources/db/migration/V2__game_player.sql`
- `src/test/kotlin/com/github/gaomond/topo/domain/model/GameStatusTest.kt`
- `src/test/kotlin/com/github/gaomond/topo/adapter/persistence/GamePlayerSchemaTest.kt`
- `src/test/kotlin/com/github/gaomond/topo/support/PostgisTestContainer.kt`
- `src/test/resources/application.properties`
- `.agent-pipeline/01-spec.md` 〜 `.agent-pipeline/06-commit-ready.md`（本パイプライン記録一式）
- `docs/story/00.md`（US-00 確定仕様。`01-spec.md` と同内容のストーリー記録）

### 変更

- `build.gradle.kts`: Testcontainers（`spring-boot-testcontainers` / `testcontainers-junit-jupiter` / `testcontainers-postgresql`）を `testImplementation` に追加。
- `src/test/kotlin/com/github/gaomond/topo/TopoApplicationTests.kt`: `@DynamicPropertySource` で共有 PostGIS コンテナに接続するよう変更（data-jpa 導入でフルコンテキスト起動が DataSource を要求するため）。

### 本コミットに含めない（別件として作業ツリーに残っている変更）

作業ツリーには US-00 と無関係な変更が混在している。誤って同一コミットに含めないこと。

- `src/main/kotlin/com/github/gaomond/topo/adapter/web/ConfigController.kt` / `ConfigResponse.kt`、`domain/model/ObjectType.kt` / `AreaPreset.kt` とそのテスト一式（`GET /api/config` ストーリーの成果物。US-00 とは別タスク）
- `.claude/agents/*.md`、`.claude/commands/build.md → implement.md`（リネーム）、`.agent-pipeline/README.md`、`CLAUDE.md` の差分（パイプライン基盤の改修。別件）
- `docs/learning-notes-java-spring.md` / `docs/learning-notes-postgis.md` の削除、`docs/study/` への移動、`docs/story/01.md`〜`04.md`・`STORY.md`、`docs/request.md`（ドキュメント整理・別ストーリーの記録。US-00 では `docs/story/00.md` のみ対象）
- `diff.txt`（作業用一時ファイル。コミット対象外。削除を推奨）

## コミットメッセージ案

```
feat: game/playerスキーマとJPAエンティティを追加(US-00)

本編ループ(US-02以降)の全APIが依存する基盤テーブルを整備した。

- Flywayマイグレーション(V2)でgame_status enum・game・playerテーブルを追加。
  game.creator_player_id <-> player.game_idの循環参照はFK後付けで解消し、
  NULL許容を維持した。
- domain.GameStatusを追加。DB enum値と一致する純粋Kotlin enumで、
  Spring/JPA/PostGISに非依存。
- adapter.persistenceにGameJpaEntity/PlayerJpaEntityを追加。空間カラム
  (polygon_geom/fixed_geom)は意図的に除外し、PostGISをDomain/UseCaseから
  隠す方針(CLAUDE.md)に従いUS-13以降の生SQLに委ねる。
- game_status enumはNAMED_ENUM+STRING+columnDefinitionの組み合わせで
  PostgreSQL named enumとマッピングし、型不一致を回避した。
- GameRepository/PlayerRepositoryはJpaRepositoryの薄い実装に留め、
  Domainポート抽象はUseCase登場(US-02)まで前倒ししない(YAGNI)。
- Testcontainers(実PostGIS)によるGamePlayerSchemaTestを追加し、受け入れ
  条件・テスト観点を1:1で検証(enum制約・FK整合・NULL許容・循環解消・
  空間型・上限なしの計11テスト)。

品質ゲート: ./gradlew build SUCCESS(20 tests / 0 failed、ktlint通過)。
```

## 残課題（フォローアップ）

- **[med] `GameJpaEntity.objectType` の型強化（05-review.md 指摘）**: 現状 `objectType: String`（生 String）でマッピングしている。Domain には `ObjectType` enum（`jsonValue` 付き）が既に存在し、ユビキタス言語上も値域の決まった概念。スキーマが `TEXT` である点自体は仕様どおりで問題ないが、`ObjectType` への変換責務をどこに置くか（エンティティに `@Convert` を持たせるか、UseCase 境界で変換するか）を **US-02 で決定**する。承認を妨げる指摘ではない。
- **[low] `PostgreSQLContainer` 非推奨警告**: Testcontainers の直起動 API が非推奨。動作影響はないが、将来 `@ServiceConnection` ベースへの移行余地がある（`PostgisTestContainer.kt:14`）。今ストーリーでの対応は不要。
- **Domain リポジトリポート抽象の導入**: UseCase が存在しない段階のため本ストーリーでは作成していない。US-02 で `GameRepository` / `PlayerRepository` を実装詳細に降格させ、Domain 層にポートインターフェースを定義する。
- **作業ツリーの整理**: 上記「本コミットに含めない」ファイル群は別タスクの成果物のため、US-00 とは別のコミット（または作業）として扱うこと。`diff.txt` は一時ファイルのため削除を推奨。
