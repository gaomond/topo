# 位置情報協力型探索ゲーム「トポー」 — 設計ドキュメント (HLD / DB)

> 位置情報を使った協力型探索ゲーム。複数プレイヤーが実際に移動して位置を確定し、
> 全員の位置から作る多角形（凸包）の面積が条件を満たすとき、多角形内の対象オブジェクト数を競う。

---

## 1. 要件 (MVP)

### 機能要件

- ゲーム（ルーム）を作成でき、N人が同じゲームに参加できる（N=人数固定、既定3）。
- ゲーム作成時に「対象オブジェクト種別」「面積プリセット」「人数」を指定する。
- 各クライアントは地図と自分の現在地（GPS）を表示する。
- 各プレイヤーは現在地を高頻度でサーバーに共有する（友達ドット表示用）。
- 進行中、サーバーが確定済みプレイヤーの確定座標と未確定プレイヤーのライブ座標を1人1点採用し、凸包と面積を返す（面積メーター。[US-20](story/20.md)）。
  - **スコア（多角形内のオブジェクト数）は確定するまで出さない**（ゲーム性のため）。
- 各プレイヤーは任意のタイミングで現在地を1点確定する。
- N人全員が確定した時点で、サーバーが凸包生成→面積→（有効なら）対象取得・件数集計を行い結果を保存する。
- 全員が結果（面積・有効/無効・オブジェクト数・多角形頂点・獲得オブジェクトの明細）を取得できる。獲得時点の情報を保存し、結果地図のマーカー選択で名前を確認する（[US-15](story/15.md)）。

### スコープ外（簡略化方針）

- 認証なし → プレイヤーはサーバー発行の `playerId`(UUID) で識別、クライアント保持。
- リアルタイム位置共有の専用基盤なし → **ポーリング（2秒間隔）**で代替。WebSocketは将来差し込み可能。
- ランキング／複数多角形比較／チーム戦／写真投稿／アカウント機能なし。
- 「同じ地点から開始」は検証しない。確定3点（以上）だけ扱う。

### 非機能（あえて最小限）

- NFRは意図的に設定しない（スピード重視・小さく作る）。
- 唯一の指標: APIレイテンシ sub-500ms。空間処理はGiSTインデックス前提で容易に達成可能。

---

## 2. Core Entity

FlywayのSQLに基づく物理スキーマと外部キーは [DBのER図](ER.md) を参照する。

```
GameObject              -- ゲーム非依存の参照データ（OSM由来）
  id           bigint (osm id, PK)
  object_type  text                       -- 'shrine' | 'school' | 'convenience_store' ...
  name         text
  geom         geometry(Point, 4326)
  index: GiST(geom), btree(object_type)

Game
  id              UUID (PK, 共有キーも兼ねる)
  status          WAITING | ACTIVE | COMPLETED
  player_count    int                      -- 固定N（既定3）
  object_type     text                     -- 集計対象（MVPは 'shrine'）
  area_threshold  double                   -- m²（プリセットの実値）
  created_at      timestamptz
  -- 結果（COMPLETEDまで null）
  polygon_geom    geometry(Polygon, 4326)
  area_sqm        double
  area_valid      boolean                  -- area_sqm <= area_threshold
  object_count    int
  result_objects  jsonb                    -- 獲得時点のID・種別・名前・座標

Player
  id            UUID (PK)
  game_id       UUID (FK -> Game)
  display_name  text?
  joined_at     timestamptz
  live_lat, live_lng, live_at              -- 最新現在地（高頻度更新・共有用）
  fixed_lat, fixed_lng, confirmed_at       -- 確定座標（確定前 null）
```

- status遷移: `WAITING`（N人未満）→ `ACTIVE`（N人参加・確定待ち）→ `COMPLETED`（N人確定・結果確定）。
- 結果は `Game` が所有する（1ゲーム1結果）。既存の結果カラムとUS-15の明細を `game` に内包する。
- US-15では獲得時点のID・種別・名前・座標を、V3で追加する `result_objects` のJSONB配列へ複写する。獲得数は同じ明細の件数から決め、完了状態・既存結果と同じUPDATE／SELECTで保存・復元する。元の `game_object` の更新・削除後も結果を固定する。
- 多角形は3点なら三角形、4点以上なら多角形。**凸包（ST_ConvexHull）で統一**し、頂点ソート等の自前実装はしない。

---

## 3. API

すべて `application/json`。座標は `{ lat, lng }`（WGS84 / SRID 4326）。

| # | メソッド・パス | 用途 |
|---|---|---|
| 1 | `GET  /api/config` | 対象種別一覧・面積プリセット一覧 |
| 2 | `POST /api/games` | ゲーム作成（種別・面積プリセット・人数を指定） |
| 3 | `POST /api/games/{id}/players` | 参加（N人目でACTIVE） |
| 4 | `GET  /api/games/{id}` | 状態取得（2秒ポーリング。ライブ座標＋現在面積＋結果を同梱） |
| 5 | `PUT  /api/games/{id}/players/{pid}/location` | ライブ位置更新（高頻度・副作用なし） |
| 6 | `POST /api/games/{id}/players/{pid}/confirm` | 位置確定（N人目で自動集計） |

上記のAPIで「作る／参加する／歩く（面積を見る）／確定する／自動集計（確定の副作用）／結果を見る」を扱う。US-15は既存のconfirmと状態取得の `result` を拡張する。汎用の `GET /api/objects?type=&bbox=` は将来の周辺検索用であり、US-15では追加しない。

### 主要レスポンス例

**`GET /api/games/{id}`**（ポーリング用・やや太いがシンプルさ優先で許容）

```json
{
  "gameId": "...",
  "status": "ACTIVE",
  "objectType": "shrine",
  "areaThreshold": 2000000,
  "playerCount": 3,
  "players": [
    { "playerId": "...", "displayName": "...", "confirmed": false, "confirmedLocation": null,
      "live": { "lat": 35.68, "lng": 139.76, "at": "..." } }
  ],
  "currentArea": { "sqm": 1234567 },
  "result": null
}
```

確定後は `result` に `{ areaSqm, areaValid, objectCount, polygon:[{lat,lng}...], objects:[...] }` が入る。`objects` は保存済みの獲得明細で、最終confirmとGETで共通の形式を使う。全項目・型・0件時の表現は [US-15](story/15.md) に従う。

US-12では各プレイヤーに `confirmedLocation: {lat,lng,at} | null` を追加する。保存済みの確定位置を全員に返し、地図上では確定位置を主表示、確定後のライブ位置を薄い補助表示とする。表示は[US-12](story/12.md)、プレイ中の現在面積の採用規則と超過の警告は[US-20](story/20.md)を正とする。

### 面積プリセット（実値の例）

| key | label | sqm |
|---|---|---|
| small  | お手軽 | 500,000 (0.5km²) |
| medium | ふつう | 2,000,000 (2km²) |
| large  | がっつり | 10,000,000 (10km²) |

`area_valid = area_sqm <= area_threshold`（「xx km²以内」＝以下で有効）。

---

## 4. HLD（構成）

```
┌─────────────┐     HTTPS / JSON     ┌──────────────────┐
│  ブラウザ    │ ──────────────────→ │  API Server       │
│ (Leaflet)    │ ←────────────────── │  Kotlin /         │
│              │                      │  Spring Boot      │
│ ・地図描画    │                      │  (stateless)      │
│ ・GPS取得     │                      └────────┬─────────┘
│ ・2秒polling  │                               │ JDBC
│ ・面積/ドット  │                               ▼
│   を表示      │                      ┌──────────────────┐
└─────────────┘                      │ PostgreSQL        │
   静的ホスティング                     │  + PostGIS        │
                                      │ game / player /   │
                                      │ game_object       │
                                      └──────────────────┘
```

### レイヤー判断

- **フロントエンド: React 19 + Vite + TypeScript の SPA**（SSR不要）。認証なし・SEO不要・処理はほぼクライアント側（GPS取得・Leaflet描画・ポーリング）。リポジトリ直下 `frontend/` に独立プロジェクトとして置き、Vite で静的ビルドして静的ホスティングに配置する（ホスト先は後決め）。**Spring の `resources/static` には同梱しない**（API サーバーとは別デプロイ）。
- **APIサーバー: ステートレスなSpring Boot 1プロセス**。WSなし・確定計算もリクエスト内で完結するため、ゲートウェイ/LB/複数インスタンス不要。
- **DB: PostgreSQL + PostGIS 1台**。Redis等のキャッシュなし。
- **CORS**: フロント（静的ホスト）とAPI（別ドメイン）が分離するため、Spring側でCORS許可設定が必要（認証がない分これだけ）。

### 計算の置き場所（重要な決定）

- **進行中の面積**: サーバー（PostGIS）で計算し `GET /api/games/{id}` に同梱。
  - 採用点はUS-20に従う。確定済みは確定位置を優先し、確定後のライブ移動では面積・凸包を変えない。
  - ロジックを1箇所に集約（DRY）。turf.js等のクライアント計算は不要。
  - 進行中の暫定面積と確定面積が同じPostGISロジックになり、測地系のズレも消える。
- **確定スコア（面積＋オブジェクト数）と獲得明細**: サーバー（PostGIS）で同一トランザクション内に取得・計算・保存。
  - 改ざん防止（認証なしのため）、面積判定と対象取得の整合確保。獲得数は明細の件数から決め、別のCOUNTクエリは実行しない。

| | 何を | どこで |
|---|---|---|
| 進行中（2秒ポーリング） | 面積のみ（スコアは伏せる） | サーバー / PostGIS |
| 確定時 | 面積 ＋ 獲得明細取得・件数集計 | サーバー / PostGIS |

### データフロー（2系統）

1. **進行中（高頻度）**: 各クライアントが `PUT .../location` で現在地送信 → 全員が2秒間隔で `GET /api/games/{id}` → サーバーが凸包面積を計算して返す → クライアントは友達ドットと面積メーターを表示。
2. **確定（1ゲーム1回）**: N人目の `confirm` → サーバーが同一Txで `ST_ConvexHull` → 測地面積 → 閾値判定 → 有効なら範囲内の獲得明細を一度取得し件数を決める → 明細を含む結果を `Game` に保存 → `COMPLETED`。最終confirmと以降のGETが保存済み `result` を返す（[US-13](story/13.md)／[US-15](story/15.md)）。

### 技術スタック補足

- **Leaflet**: 地図の描画・操作（地図/自分のピン/友達ドット/凸包ポリゴン）。**計算はしない**。
- **turf.js**: 不要（面積計算をサーバーに寄せたため）。
- **フロントエンド ツールチェイン**: ビルド = Vite、言語 = TypeScript、UI = React 19、スタイル = Tailwind CSS v4、Lint/Format = Biome、テスト = Vitest + @testing-library/react（jsdom）。品質ゲートはフロント側の npm scripts（lint / typecheck / test / build）で回し、バックエンドの `./gradlew build` とは独立。pre-commit は husky + lint-staged（Biome）で担保。
- **ORM方針**: 通常のCRUDは **JPA**、地理空間SQL（凸包・測地面積・範囲内SELECT/COUNT）は **生SQL**（`@Query(nativeQuery=true)` または `JdbcTemplate`）で記述。
- **OSMデータ取得**: Overpass API で対象オブジェクトを取得し、PostGIS（`game_object`）へINSERT。ローカルOSMサーバー構築は不要。将来オブジェクト種別を広げる場合は pbf + osm2pgsql への移行も可能（`game_object` スキーマは共通に保つ）。

### 永続化の構成

通常 CRUD の取得・保存とドメインモデルへの変換が、Repository Adapter と JPA Repository に分かれていると、永続化処理を複数の場所で追う必要がある。topo では、Spring Data JPA を使う Repository 自体を outbound adapter とし、取得・保存と変換を同じ実装側にまとめる。

以下は [RS-00](refactor/00.md) で移行する構成を示す。RS-00 では、[共通方針の「経路ごとの型名」](responsibilities-and-naming.md#3-経路ごとの型名) に沿った、HTTP 入力から応答までの境界の型名の統一も行う。

```text
依存方向:
UseCase → GameRepositoryPort ← GameJpaRepository（outbound adapter）

GameJpaRepository が担当する処理:
  DB から GameRecord を取得 → Game に変換して返す
  ドメインモデルの変更を永続化表現に変換 → DB に保存
```

| 要素 | topo での方針 |
| --- | --- |
| `Game` | 業務ルールと状態を表すドメインモデル。JPA に依存しない。 |
| `GameRepositoryPort` | Domain に置く永続化の契約。操作に必要なドメインモデルを受け渡す。 |
| `GameRecord` | JPA が管理する永続化表現。`@Entity` などのマッピングを定義する。 |
| `GameJpaRepository` | outbound adapter としてポートを実装し、JPA によるアクセスとドメインモデルへの変換を担当する。 |
| `GameRepositoryAdapter` | JPA Repository の呼び出しと変換を担当する別のラッパーは設けず、`GameJpaRepository` 側へ統合する。 |

Player の通常 CRUD にも同じ方針を適用する。永続化表現の名前は `XxxRecord` とし、`GameJpaEntity`・`PlayerJpaEntity` はそれぞれ `GameRecord`・`PlayerRecord` に変更する。DB のテーブル名・カラム・制約は維持する。

- ポートのシグネチャは、ドメイン側で必要な操作から決める。Spring Data の継承メソッドと衝突する場合は、実装側のインターフェースや内部メソッド名で調整する。
- 更新操作に必要な `Game` は Repository が復元して返す。UseCase で `GameSummary` から組み立てない。状態取得などの読み取り専用の射影は、用途に応じて維持する。
- 保存側のポートは RS-00 では既存の `createGame`・`createPlayer`・`updateStatus` などの契約を維持する。ゲーム作成に必要なモデルと作成契約は [RS-04](refactor/04.md) で移行し、[RS-06](refactor/06.md) で作成ポートを `createWithoutCreator(game)` に改名して初回の作成者ID未設定保存を明示する。DomainのGameは作成者IDを保持したまま渡す。Gameの状態更新・作成者IDの紐付けもモデル入力に揃え、ライブ位置は型付き座標による部分更新を維持する。ゲームと作成者の保存順序・トランザクションはUseCaseが管理する。
- トランザクションの範囲は UseCase が管理する。ロック付き取得はポートのメソッド名で明示し、参加・開始は同じゲーム行のロック取得から判定・保存までを同一トランザクションで行う。
- PostGIS の SQL は outbound adapter 内に置く。`CurrentAreaQueryAdapter` などの空間計算は、通常 CRUD と同じ JPA の構成に統合する対象には含めない。

### 「ぬるぬる動く位置表示」について（参考メモ）

- フードデリバリー等の滑らかな移動表示は、通信頻度ではなく**クライアント側の補間（＋道路スナップ）**で作られている。生データは秒オーダーで疎。
- 本ゲームは徒歩（低速）のため、MVPは補間なし（2秒ごとに位置更新）で十分。必要になれば**サーバー・データモデルを変えずクライアントにマーカー補間を足すだけ**で改善可能。

---

## 5. 既知の割り切り（要レビュー時の論点）

- `GET /api/games/{id}` がやや太い（状態＋ライブ座標＋面積＋結果を1本に集約）。シンプルさ優先で許容。
- US-15の獲得明細は全件を既存結果に含める。応答サイズと描画密度を実データで確認し、過去の完了ゲームへの補完・移行・互換表示は行わない。マーカーの重なりは今回許容する。
- MVPでは確定座標の上書きは不可（US-11）。取消・再確定はUS-17で将来検討する。
- 確定時の面積条件はUS-13に従う。候補の凸包・面積を一度だけ計算・判定し、途中は上限、最終は上限と下限1m²を判定する。退化による進行不能はMVPで許容する。
