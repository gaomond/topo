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
- 進行中、サーバーが全員の現在地から凸包面積を計算して返す（面積メーター）。
  - **スコア（多角形内のオブジェクト数）は確定するまで出さない**（ゲーム性のため）。
- 各プレイヤーは任意のタイミングで現在地を1点確定する。
- N人全員が確定した時点で、サーバーが凸包生成→面積→（有効なら）対象集計を行い結果を保存する。
- 全員が結果（面積・有効/無効・オブジェクト数・多角形頂点）を取得できる。

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

Player
  id            UUID (PK)
  game_id       UUID (FK -> Game)
  display_name  text?
  joined_at     timestamptz
  live_lat, live_lng, live_at              -- 最新現在地（高頻度更新・共有用）
  fixed_lat, fixed_lng, confirmed_at       -- 確定座標（確定前 null）
```

- status遷移: `WAITING`（N人未満）→ `ACTIVE`（N人参加・確定待ち）→ `COMPLETED`（N人確定・結果確定）。
- 結果は別テーブルを作らず `Game` に内包（1ゲーム1結果）。
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
| 7 | `GET  /api/objects?type=&bbox=` | （任意）地図表示用オブジェクト取得 |

コアは1〜6の6本。7は任意。ユースケースは「作る／参加する／歩く（面積を見る）／確定する／自動集計（確定の副作用）／結果を見る」の6つに対応。

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
    { "playerId": "...", "displayName": "...", "confirmed": false,
      "live": { "lat": 35.68, "lng": 139.76, "at": "..." } }
  ],
  "currentArea": { "sqm": 1234567 },
  "result": null
}
```

確定後は `result` に `{ areaSqm, areaValid, objectCount, polygon:[{lat,lng}...] }` が入る。

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

- **フロントエンド: SPA**（SSR不要）。認証なし・SEO不要・処理はほぼクライアント側（GPS取得・Leaflet描画・ポーリング）。静的ホスティングに配置（ホスト先は後決め）。
- **APIサーバー: ステートレスなSpring Boot 1プロセス**。WSなし・確定計算もリクエスト内で完結するため、ゲートウェイ/LB/複数インスタンス不要。
- **DB: PostgreSQL + PostGIS 1台**。Redis等のキャッシュなし。
- **CORS**: フロント（静的ホスト）とAPI（別ドメイン）が分離するため、Spring側でCORS許可設定が必要（認証がない分これだけ）。

### 計算の置き場所（重要な決定）

- **進行中の面積**: サーバー（PostGIS）で計算し `GET /api/games/{id}` に同梱。
  - ロジックを1箇所に集約（DRY）。turf.js等のクライアント計算は不要。
  - 進行中の暫定面積と確定面積が同じPostGISロジックになり、測地系のズレも消える。
- **確定スコア（面積＋オブジェクト数）**: サーバー（PostGIS）で同一トランザクション内に計算・保存。
  - 改ざん防止（認証なしのため）、面積判定とCOUNTの整合確保。

| | 何を | どこで |
|---|---|---|
| 進行中（2秒ポーリング） | 面積のみ（スコアは伏せる） | サーバー / PostGIS |
| 確定時 | 面積 ＋ オブジェクトCOUNT | サーバー / PostGIS |

### データフロー（2系統）

1. **進行中（高頻度）**: 各クライアントが `PUT .../location` で現在地送信 → 全員が2秒間隔で `GET /api/games/{id}` → サーバーが凸包面積を計算して返す → クライアントは友達ドットと面積メーターを表示。
2. **確定（1ゲーム1回）**: N人目の `confirm` → サーバーが同一Txで `ST_ConvexHull` → 測地面積 → 閾値判定 → 有効なら範囲内オブジェクトをCOUNT → 結果を `Game` に保存 → `COMPLETED`。以降のポーリングが `result` を返す。

### 技術スタック補足

- **Leaflet**: 地図の描画・操作（地図/自分のピン/友達ドット/凸包ポリゴン）。**計算はしない**。
- **turf.js**: 不要（面積計算をサーバーに寄せたため）。
- **ORM方針**: 通常のCRUDは **JPA**、地理空間SQL（凸包・測地面積・範囲内SELECT/COUNT）は **生SQL**（`@Query(nativeQuery=true)` または `JdbcTemplate`）で記述。
- **OSMデータ取得**: Overpass API で対象オブジェクトを取得し、PostGIS（`game_object`）へINSERT。ローカルOSMサーバー構築は不要。将来オブジェクト種別を広げる場合は pbf + osm2pgsql への移行も可能（`game_object` スキーマは共通に保つ）。

### 「ぬるぬる動く位置表示」について（参考メモ）

- フードデリバリー等の滑らかな移動表示は、通信頻度ではなく**クライアント側の補間（＋道路スナップ）**で作られている。生データは秒オーダーで疎。
- 本ゲームは徒歩（低速）のため、MVPは補間なし（2秒ごとに位置更新）で十分。必要になれば**サーバー・データモデルを変えずクライアントにマーカー補間を足すだけ**で改善可能。

---

## 5. 既知の割り切り（要レビュー時の論点）

- `GET /api/games/{id}` がやや太い（状態＋ライブ座標＋面積＋結果を1本に集約）。シンプルさ優先で許容。
- 確定座標の上書きは「結果確定（COMPLETED）前なら可・最新優先」とする。
- 退化ケース（3点が一直線/重複で面積ゼロ）の扱いは実装時に確認（PoCで検証）。
