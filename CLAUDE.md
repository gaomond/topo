# topo

位置情報協力型探索ゲーム。複数プレイヤーが実際に移動して位置を確定し、全員の確定位置から作る凸包の面積が条件を満たすとき、多角形内の対象オブジェクト数を競う。
タスク実行前に本ファイルと [docs/DESIGN.md](docs/DESIGN.md)（HLD / DB 設計）を参照すること。ドメインの仕様・スキーマ・API は DESIGN.md を一次情報とする。

## Tech Stack

- **Language:** Kotlin（JSR305 strict）
- **Backend:** Spring Boot（Web MVC / Data JPA / Actuator）
- **DB:** PostgreSQL + PostGIS
- **Migration:** Flyway
- **Frontend:** React 19 + Vite + TypeScript の SPA。地図描画は Leaflet（**react-leaflet** で統合）、スタイルは Tailwind CSS v4。リポジトリ直下 `frontend/` に独立プロジェクトとして配置。静的ホスティング前提で、Spring の `resources/static` には同梱しない
- **Build:** バックエンド = Gradle（Kotlin DSL）/ JDK 25。フロントエンド = Vite（`frontend/` の npm scripts）。両者は独立してビルドする
- **Infra:** Docker Compose（PostgreSQL + PostGIS）
- **Test:** バックエンド = JUnit 5 + Spring Boot Test（テストスライス）+ Testcontainers。フロントエンド = Vitest + @testing-library/react（jsdom）
- **Lint / Format:** バックエンド = ktlint。フロントエンド = Biome（lint/format）。いずれも品質ゲートで強制（detekt は JDK25/Kotlin2.3 対応版が出たら追加）。フロントの pre-commit は husky + lint-staged
- **ライブラリ方針:** 車輪の再発明をしない。react-leaflet 級に成熟・広く使われているライブラリは積極採用する（無名・小規模なものは避ける）

## Architecture

Clean Architecture。依存は内側（Domain）に向かう一方向。

```text
UI ─(HTTP)→ Adapter(inbound) → UseCase → Domain ← Adapter(outbound)
```

- **Domain:** 型定義と純粋ロジック。Spring / JPA / PostGIS / 外部ライブラリに依存しない。ポート（リポジトリ・空間計算）のインターフェースを持つ
- **UseCase:** ビジネスロジック。Domain のポートに依存し、Adapter の実装には依存しない
- **Adapter:** Domain のポートを実装する。**inbound** = REST コントローラ（Spring MVC）、**outbound** = 永続化。通常 CRUD は JPA、地理空間は生 SQL（`@Query(nativeQuery = true)` / `JdbcTemplate`）。生 SQL は outbound 内に閉じ込め、Domain / UseCase から PostGIS を隠す
- **UI:** React + Leaflet（react-leaflet）の SPA（`frontend/`）。HTTP 経由で inbound アダプタ（REST API）を呼ぶ。バックエンドとは別に静的ホスティングへデプロイする（Spring の `resources/static` には置かない）

- adapter 同士は直接呼び合わない。必ず UseCase / ポートを経由する
- 凸包・面積・内包判定はサーバー（PostGIS）で計算する。クライアントでは計算しない（DRY・改ざん防止）

### DIP

UseCase は Domain 層の抽象インターフェース（ポート）に依存する。Adapter 層の具象を直接 import しない。具象の注入は Spring の DI（`@Configuration` / コンストラクタ注入）で行う。

### Smart / Dumb

フロントエンドのコンポーネントは Smart / Dumb に分ける。

- **Smart（コンテナ）:** 状態保持・ポーリング・GPS 取得・API 通信を担う。API を呼べるのは Smart のみ
- **Dumb（プレゼンテーショナル）:** props を受け取って描画するだけ。API も共有状態も知らない。入力欄の値など UI 固有の閉じた状態のみ自身で管理してよい
- **Leaflet は描画専用**（react-leaflet 経由）。面積などの計算はしない。turf.js は使わない

Smart / Dumb は「役割」の区別であり、**フォルダは役割ではなく機能（feature）で分ける**。

#### フロントエンドのディレクトリ構成

機能ベース。関連する Smart（コンテナ）と Dumb（ビュー）、その機能専用の hooks を同じ機能フォルダに同居させる。型で分ける（`containers/` と `components/` を分離する）構成は取らない。

```text
frontend/
├── src/
│   ├── features/<機能>/   … 機能ごとに Smart+Dumb+専用hooks を同居（例: game-create / waiting-room / geo-tracking）
│   ├── api/               … API クライアント・Web 表現型（機能横断）
│   ├── routing/           … ルート定義・URL 組み立て（機能横断）
│   ├── App.tsx / main.tsx … ルート結線
│   └── ...
└── tests/                 … src をミラーしたテスト（下記 Testing 参照）
```

- 複数機能で共有する Dumb / hooks が出てきたら `src/shared/` 配下に置く（現状は無し）。
- **import は `@/` エイリアス（= `src/`）を使う。** 機能内の同居ファイルのみ相対 import（`./Xxx`）、機能をまたぐ参照（api / routing / 他 feature）や tests → src の参照は `@/...` を使う。エイリアスは `vite.config.ts` の `resolve.alias` と `tsconfig.app.json` の `paths` の2箇所で定義する。

## ユビキタス言語

コード上の命名はこの用語に従う。新規用語が必要になったら本セクションに追記すること。

| 用語 | 英語（コード識別子） | 意味 |
| --- | --- | --- |
| ゲーム | Game | 1ルーム。共有キー（UUID）で識別 |
| プレイヤー | Player | ゲーム参加者。`playerId`（UUID）で識別。認証なし |
| 人数 | playerCount | ゲームの固定参加人数 |
| ゲームオブジェクト | GameObject | ゲーム非依存の参照データ（OSM 由来）。`objectType` で種別を表す |
| オブジェクト種別 | objectType | `shrine` / `temple` / `school` / `convenience_store` / `park` / `station` |
| ライブ位置 | liveLocation | プレイヤーの最新現在地。高頻度更新・友達ドット表示用。副作用なし |
| 友達ドット | live marker | 地図上に表示する他プレイヤーのライブ位置マーカー |
| 確定 | confirm | プレイヤーが現在地を1点確定する操作 |
| 凸包 | ConvexHull（`ST_ConvexHull`） | 全員の確定位置から作る多角形 |
| 面積 | area（areaSqm） | 凸包の実面積（m²）。`ST_Area(geography)` で測地面積を取得 |
| 面積閾値 | areaThreshold | 面積成立判定の上限（m²） |
| 面積プリセット | areaPreset | 面積閾値の選択肢（small / medium / large） |
| 面積成立 | areaValid | 凸包面積が閾値以下で集計対象として成立している状態 |
| 獲得オブジェクト数 | objectCount | 面積成立時に凸包内へ内包される対象オブジェクトの数 |
| 座標 | Coordinate（lat / lng） | プレイヤー位置・オブジェクト位置を表す緯度経度のペア |
| 座標参照系 | Crs（Coordinate Reference System） | 座標の基準となる参照系。現状は WGS84（SRID 4326）を採用するが、種別はこれに限定しない |
| ポーリング | polling | 進行中の状態取得（一定間隔の `GET`） |
| プレイヤースナップショット | PlayerSnapshot | ゲーム状態内のプレイヤーの読み取り射影（ある時点・最小形）。`GameState.players` の要素。JPA 非依存の Domain 値で、`View` とは呼ばない |

## Implementation Rules

### 命名

ドメインの意図を表現する。ユビキタス言語に従う。`handleClick` ではなく `confirmLocation`、`DataList` ではなく `GameStateView`。

#### Web 表現（レスポンス）の命名

レスポンスのボディ全体は `XxxResponse`、その中にネストされる構成要素は `XxxPayload` と命名する。`Dto` は意味が広すぎ、`Response` の中身として `XxxDto` を使うと包含関係が逆に見えて分かりづらい（DTO の一部が Response であるかのように読める）ため、レスポンス構成要素には `Dto` を使わない（例: `ConfigResponse` の構成要素は `AreaPresetPayload`）。Domain モデルと Web 表現を別型として分離する方針自体は維持する。

### Error Handling

Domain でドメイン例外を定義し、inbound アダプタ（コントローラ）で HTTP ステータスにマッピングする（バリデーション失敗 = 400 / 対象不在 = 404）。

### Logging

ログは SLF4J（Spring 標準）を使う。Domain 層にはロギングフレームワークの import を持ち込まない（必要なら UseCase / Adapter 層で出す）。

### Coding Workflow

1. Domain の型とポート（インターフェース）を定義
2. UseCase を実装（Adapter 実装には依存しない）
3. Adapter を実装（inbound: コントローラ / outbound: 永続化）
4. UI で繋ぐ

## Testing

バックエンドは JUnit 5。テストスライスを活用し、フルコンテキスト起動は最小限にする。フロントエンドは `frontend/` で Vitest + @testing-library/react（jsdom）。Geolocation 等のブラウザ API は注入・モックしてユニットテスト可能にする。テスト対象は必ず実ソースから import し、テスト内で再定義しない（バックエンド・フロント共通）。

**フロントのテストは source と同居させない。** `frontend/tests/` に `src/` をミラーした構造で置き（例: `src/features/game-create/CreateGameContainer.tsx` → `tests/features/game-create/CreateGameContainer.test.tsx`）、テスト対象は `@/...` エイリアスで import する。テスト用ヘルパ（`fakeGeolocation` 等）も `tests/` 配下に置く。

- **テスト名は `test_Action_Condition_Result` で書く**。Action（対象操作）/ Condition（条件）/ Result（期待結果）をアンダースコアで区切る（例: `test_getConfig_always_returns200AndApplicationJson`）。
- **テスト対象は必ず `src/` から import すること。** テストファイル内にプロダクションのクラスや関数を再定義してはいけない。再定義するとテストが実ソースの劣化版コピーを検証するだけになり、実ソースとの乖離（仕様変更・破壊的変更）を検出できなくなる。

- **Level 1: Domain** — 純粋 Kotlin の検証。モック不要
- **Level 2: UseCase** — ポートをモックし、ビジネスロジックの正当性を検証。エッジケースを重点的に
- **Level 3: Adapter** — コントローラは `@WebMvcTest`、永続化・空間クエリは `@DataJpaTest` + Testcontainers（実 PostGIS で検証）

## Code Quality

- Kotlin の null 安全は JSR305 strict（`-Xjsr305=strict`）。プラットフォーム型を放置しない
- ktlint を通すこと（format / lint）。整形は `./gradlew ktlintFormat`
- **品質ゲート（バックエンド）:** `./gradlew build`（test + ktlint 込み）を通る状態を「完了」とする
- **品質ゲート（フロントエンド）:** `frontend/` で `npm run lint`（Biome）・`npm run typecheck`（tsc）・`npm run test`（Vitest）・`npm run build`（Vite）が通る状態を「完了」とする。`/implement` パイプラインでは SubagentStop フック（matcher=`pipeline-impl`）が Gradle ビルドに続けて上記4ゲートも自動実行し、失敗時は Impl に差し戻す（`frontend/package.json` が無いストーリーではスキップ）。ローカルのコミット時は husky + lint-staged でも担保
- **React Hooks の lint（フロントエンド）:** Biome の `useExhaustiveDependencies` と `useHookAtTopLevel` を `error` で固定する（`biome.json` の `linter.rules.correctness` に明示。recommended 任せにしない）。フックの依存配列はこれらのルールに従う。`useEffect` の依存配列ミス由来のバグ（無限ループ・無限フェッチ等）を「完了」前に機械的に弾くための強制ゲート。
- **上記2ルールの `biome-ignore` 抑制を禁止する。** ルールが鳴ったら抑制せず、依存配列を正すかコード構造を変えて解消する。抑制コメントは `npm run lint`（`lint:no-suppress`）が `src` / `tests` を走査して検出し、1件でもあればゲートを赤にする。

## コミュニケーション

応答・ドキュメント・コミットメッセージ・コメントはすべて**日本語**で書く。
