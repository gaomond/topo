# 01-spec.md — US-00: `game` / `player` スキーマ構築

> 本編ループ（US-02以降）の全APIが依存する基盤テーブルのスキーマを定義する。
> `game_object`（GiST含む）は投入済みのため本仕様の対象外。
> 本仕様は1実装単位（US-00）分。Phase 2（実装）へ引き継ぐ。

---

## 1. 合意された仕様

### 1.1 対象テーブル

`game` と `player` の2テーブル。ともにUUID主キー。マイグレーションは **Flyway**（導入済み）で管理する。

### 1.2 `game`

| カラム | 型 | 制約 / 既定 | 備考 |
|---|---|---|---|
| `id` | UUID | PK | 共有キー（URLのgameId）を兼ねる |
| `status` | enum `game_status` | NOT NULL | `WAITING` / `ACTIVE` / `COMPLETED` |
| `player_count` | int | NOT NULL | 部屋の定員（可変保持）。初回リリースは N=3 運用。上限CHECKは張らない |
| `object_type` | text | NOT NULL | 集計対象（MVPは `'shrine'`） |
| `area_threshold` | double precision | NOT NULL | m²（プリセットの実値） |
| `creator_player_id` | UUID | FK → `player.id`、**NULL許容** | 作成者（US-06の開始ボタン押下者判定の根拠）。循環回避のため後段UPDATEで埋める |
| `created_at` | timestamptz | NOT NULL, default now() | |
| `polygon_geom` | geometry(Polygon, 4326) | NULL | 結果。COMPLETEDまでNULL |
| `area_sqm` | double precision | NULL | 結果。同上 |
| `area_valid` | boolean | NULL | `area_sqm <= area_threshold`。同上 |
| `object_count` | int | NULL | 結果。同上 |

### 1.3 `player`

| カラム | 型 | 制約 / 既定 | 備考 |
|---|---|---|---|
| `id` | UUID | PK | クライアントはURLに同梱して保持（後述スコープ外メモ） |
| `game_id` | UUID | FK → `game.id` | ON DELETE 句は付けない（物理削除を前提にしない） |
| `display_name` | text | NULL | 任意 |
| `joined_at` | timestamptz | NOT NULL, default now() | |
| `live_lat` | double precision | NULL | 最新現在地（高頻度更新・共有用） |
| `live_lng` | double precision | NULL | 同上 |
| `live_at` | timestamptz | NULL | ライブ位置の更新時刻 |
| `fixed_geom` | geometry(Point, 4326) | NULL | 確定座標。確定前NULL。US-13で `ST_ConvexHull` にそのまま渡す |
| `confirmed_at` | timestamptz | NULL | 確定時刻。確定前NULL |

> 注: 確定座標は当初ドキュメントの `fixed_lat`/`fixed_lng`(double) ではなく **`geometry(Point,4326)`** で保持する（D3決定）。集計SQLが素直になるため。ライブ座標は表示用途のみのため `lat`/`lng`(double) のまま。

### 1.4 enum型

```sql
create type game_status as enum ('WAITING', 'ACTIVE', 'COMPLETED');
```

- 状態値の追加はほぼ起きない前提のため enum 型を採用（D2決定）。

### 1.5 status 遷移（参考・本仕様は格納のみ担保）

`WAITING`（定員未満〜定員到達） → `ACTIVE`（作成者が開始ボタンを押下） → `COMPLETED`（N人確定・結果確定）。
※ 参加到達では自動でACTIVEにしない（US-06変更点）。遷移ロジック自体はUS-05/06/13のスコープ。

### 1.6 創成順序（循環参照の回避）

`game.creator_player_id` ↔ `player.game_id` の相互参照を、以下の順で解消する。

1. `game` を作成（`creator_player_id` は NULL）
2. 作成者の `player` を作成（`game_id` を埋める）
3. `game.creator_player_id` を 2 のplayer.idでUPDATE

### 1.7 永続化方針

- **物理削除しない**（ゲーム・プレイヤーとも）。MVPに削除機能・TTL・削除バッチは設けない。
- 進行中にゲームを放棄しても status を保持したまま残し、URL（gameId）で **いつでも復帰可能**とする。
- プレイヤーは使い捨て（認証なし・UUID識別）だが物理削除せず、無期限保持。データ量がMVP規模で問題にならないため。

---

## 2. 受け入れ条件

- [ ] Flywayマイグレーションで `game` / `player` / `game_status`(enum) が生成される。
- [ ] `game.id` / `player.id` が UUID 主キーである。
- [ ] `player.game_id` → `game.id` の FK が存在し、ON DELETE 句が付いていない。
- [ ] `game.creator_player_id` → `player.id` の FK が存在し、**NULL許容**である。
- [ ] `game.status` は enum `game_status` 型で、3値以外は格納できない。
- [ ] 結果カラム（`polygon_geom` / `area_sqm` / `area_valid` / `object_count`）が NULL 許容である。
- [ ] `player.fixed_geom` が `geometry(Point,4326)`、`game.polygon_geom` が `geometry(Polygon,4326)` である。
- [ ] `created_at` / `joined_at` が `timestamptz`・default now() を持つ。
- [ ] `player_count` に上限CHECKが付いていない（可変保持）。
- [ ] 1.6の3ステップ（game作成→creator player作成→creator_id UPDATE）が成功する。
- [ ] JPAエンティティから通常CRUDがマッピングでき、空間カラムは生SQL側で扱える。

---

## 3. テスト観点

- **マイグレーション再現性**: クリーンDBに対しFlyway適用が成功し、enum型・FK・default・NULL許容が定義どおり生成される。
- **enum制約**: `status` に許可外値を INSERT すると失敗する。
- **FK整合**: 存在しない `game_id` での player INSERT が失敗する。`creator_player_id` に存在しないUUIDを入れると失敗する。
- **NULL許容**: 結果4カラム・`fixed_geom`・`live_*`・`confirmed_at`・`creator_player_id` が NULL のまま INSERT できる。
- **循環解消**: 1.6の順序でcreatorを埋められる。逆順（先に creator_id 必須）にしていないことを確認。
- **空間型**: `fixed_geom`/`polygon_geom` に 4326 のPoint/Polygonを格納・取得できる。
- **エッジ**: `player_count` に大きな値（例: 100）を入れてもCHECKで弾かれない（上限なし確認）。

---

## 4. スコープ

### 含む

- `game` / `player` テーブルおよび `game_status` enum のスキーマ定義（Flywayマイグレーション）。
- 上記の制約（PK / FK / NOT NULL / default / NULL許容 / enum）。
- 循環参照を解消できるカラム設計（creator_player_id NULL許容）。

### 含まない（他ストーリー / 将来）

- `game_object` テーブル（**投入済み・対象外**）。
- status遷移ロジック、開始ボタンの押下条件・途中参加・押せる人の判定（US-05 / US-06）。**未回答の3問は当該ストーリーで決定**。
- 凸包・測地面積・範囲内COUNT等の空間SQL（US-13）。
- **playerId のクライアント保持方式**: URL同梱（案C `/game/<gameId>?p=<playerId>`）に決定済みだが、これはUS-02 / US-05の実装事項。スキーマはクライアント保持方式に依存しない（サーバーは発番してJSONで返すだけ）。
- 退化ケース（一直線・重複で面積ゼロ）の結果保存挙動（US-16）。`polygon_geom` のNULL許容で吸収可能だが、扱いの確定は当該ストーリー。
- TTL / 物理削除 / 削除バッチ（永続化方針によりMVP対象外）。
- 将来のユーザー登録・匿名playerのアカウント紐付け・古いplayerの掃除。

### 座標系に関するスコープ外メモ

- 現状の確定点・凸包・測地面積はすべて **SRID 4326（緯度経度）** 前提。
- 将来の「生産多角形 / 正多角形」要素は **平面投影系**（対象エリアに応じた平面直角座標系 / UTM 等）を想定するため、測地系は1つに固定しない。
- 当該図形カラムを追加する際にSRIDの持ち方（保存時SRIDを別に持つ / 変換して扱う）を別途決める。本US-00では4326のPoint/Polygonのみ定義し、図形カラムのSRIDを4326でハード固定する設計判断は将来列に波及させない。
