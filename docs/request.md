# 追加要件: 正多角形制約

> 本ドキュメントは HLD 本体（設計ドキュメント）の**追記**であり、既存 MVP スコープを変更しない。
> MVP 実装完了後に本要件を追加レイヤーとして組み込む。手戻りは最小限の想定。

---

## 1. 概要

### 動機

- 元のゲームデザインでは「友達と集まっているのに散らばらないといけない」のが楽しくない。
- 正多角形を作るという**協力目標**を加えることで、散らばる理由（＝ゲーム性）が生まれる。
- 「完璧な円を描く」系ブラウザゲームと同じ構造: 確定の瞬間まで正確なスコアを伏せ、えいやで押させる。

### 要件サマリ

- N 人プレイ時、確定座標から成る多角形が**正 N 角形にどれだけ近いか**を判定する。
- 正多角形スコア（regularity, 0〜100%）を算出し、閾値（既定 80%）以上で合格。
- **面積条件は残る**。最終判定は「面積 valid AND 正多角形 valid」の両方を満たすこと。
- 正多角形は**球面上ではなく投影面上**で判定する。投影系は Web Mercator (EPSG:3857) を使用する（地理院タイルと同一）。

---

## 2. 正多角形スコアの定義

### 計算手順

1. 確定座標 N 点を EPSG:3857 に変換する（`ST_Transform(geom, 3857)`）。
2. N 点の重心を求める。
3. 各頂点から重心までの距離 d₁, d₂, ..., dₙ を計算する。
4. 隣接頂点間の中心角 θ₁, θ₂, ..., θₙ を計算する（重心から見た角度差、頂点は角度順にソート）。
5. 距離の均一性スコアと角度の均一性スコアを合成して最終スコアとする。

### スコア算出式

```
距離の均一性:
  d_mean = mean(d₁..dₙ)
  distance_score = 1 - (max(|dᵢ - d_mean|) / d_mean)    ※ 0〜1

角度の均一性:
  θ_ideal = 360° / N
  angle_score = 1 - (max(|θᵢ - θ_ideal|) / θ_ideal)      ※ 0〜1

最終スコア:
  regularity_pct = distance_score × angle_score × 100      ※ 0〜100%
```

- 距離・角度の両方が完全に均一なら 100%。
- どちらか一方でも大きくズレれば急激に下がる（乗算合成）。
- max ベースにすることで「1点だけ大きくズレている」ケースを正しくペナルティする。

> **定数の選定**: 上記の式および閾値（80%）は仮置き。実地プレイテストで調整する前提。
> コード上は定数として切り出し、後から変更可能にする。

---

## 3. UX: 情報のマスキング

### 設計方針

確定ボタンを押すまで正確なスコアは見せない。進行中は**ぼかしたヒント**のみ返す。

| フェーズ | 返す情報 | 返さない情報 |
|---|---|---|
| 進行中（ACTIVE） | 正多角形ヒント（粗い段階表示） | 正確なスコア値 |
| 確定後（COMPLETED） | 正確なスコア値（%表示） | — |

### 進行中ヒント: `regularityHint`

サーバーは正確なスコアを内部計算した上で、以下のバケットに量子化して返す。

| hint 値 | 内部スコア範囲 | UI表示イメージ |
|---|---|---|
| `far` | 0〜39% | ぼんやり（形が崩れている） |
| `moderate` | 40〜64% | まあまあ |
| `close` | 65〜84% | 近い |
| `very_close` | 85〜100% | かなり近い |

- バケット境界値はアプリケーション定数（コード内）で管理。DB には持たない。
- UI 側の表現はぼかしたゲージ / 色グラデーション等。数値は一切出さない。
- 微調整を許すと作業化するため、意図的に粗くする。

### 確定時の演出

- 確定ボタン押下 → サーバーから正確な `regularityPct` が返る。
- クライアントは結果画面でスコアをバンと表示（「87.3%！」等）。
- 100% に近いほど称賛演出（ドーパミン設計）。

---

## 4. API 変更

### 既存 API への影響

新規エンドポイントの追加はなし。既存レスポンスへのフィールド追加のみ。

### `GET /api/games/{id}`（ポーリング）— 追加フィールド

```jsonc
{
  // ... 既存フィールド ...
  "currentArea": { "sqm": 1234567 },
  "regularityHint": "close",        // ← 追加（ACTIVE 時のみ、量子化済み）
  "result": null
}
```

### `result`（COMPLETED 時）— 追加フィールド

```jsonc
{
  "result": {
    "areaSqm": 1500000,
    "areaValid": true,
    "regularityPct": 87.3,           // ← 追加（0〜100、小数1桁）
    "regularityValid": true,         // ← 追加（pct >= threshold）
    "objectCount": 12,
    "polygon": [{ "lat": 35.68, "lng": 139.76 }, ...]
  }
}
```

### `GET /api/config` — 追加フィールド

```jsonc
{
  "objectTypes": [...],
  "areaPresets": [...],
  "regularityThreshold": 80         // ← 追加（%）
}
```

---

## 5. スキーマ変更

`Game` テーブルへのカラム追加のみ。新テーブルなし。

```sql
ALTER TABLE game ADD COLUMN regularity_threshold double precision NOT NULL DEFAULT 80;
ALTER TABLE game ADD COLUMN regularity_pct       double precision;          -- COMPLETED 後に確定
ALTER TABLE game ADD COLUMN regularity_valid     boolean;                   -- COMPLETED 後に確定
```

| カラム | 用途 | 設定タイミング |
|---|---|---|
| `regularity_threshold` | 合格ライン（%）。既定 80。 | ゲーム作成時（config から） |
| `regularity_pct` | 確定時の実測スコア | COMPLETED 遷移時 |
| `regularity_valid` | `regularity_pct >= regularity_threshold` | COMPLETED 遷移時 |

---

## 6. 計算の置き場所

元 HLD の方針を踏襲: **すべてサーバー（PostGIS）で計算**。

| フェーズ | 計算内容 | 実行タイミング |
|---|---|---|
| 進行中 | ライブ座標から暫定スコア → 量子化してヒント返却 | `GET /api/games/{id}` のたび |
| 確定時 | 確定座標から正確なスコア → DB 保存 | N 人目の `confirm` 時、同一 Tx 内 |

進行中の計算は面積計算クエリに相乗りさせる（追加クエリなし）。

### PostGIS 実装メモ

```sql
-- 投影変換して距離・角度を計算する例（概念）
WITH pts AS (
  SELECT ST_Transform(ST_SetSRID(ST_MakePoint(fixed_lng, fixed_lat), 4326), 3857) AS geom
  FROM player WHERE game_id = :gameId AND confirmed_at IS NOT NULL
),
centroid AS (
  SELECT ST_Centroid(ST_Collect(geom)) AS c FROM pts
),
metrics AS (
  SELECT
    ST_Distance(p.geom, c.c) AS dist,
    degrees(ST_Azimuth(c.c, p.geom)) AS azimuth
  FROM pts p, centroid c
)
-- ここから distance_score, angle_score を算出
```

実際の SQL は実装時に詰めるが、PostGIS の標準関数で完結する。

---

## 7. 影響範囲まとめ

| レイヤー | 変更内容 | 規模 |
|---|---|---|
| DB スキーマ | `Game` に 3 カラム追加 | 極小 |
| API レスポンス | 既存 3 エンドポイントにフィールド追加 | 小 |
| サーバーロジック | 正多角形スコア計算関数（数十行）＋ヒント量子化（数行） | 小 |
| フロント | ぼかしゲージ表示＋結果画面にスコア表示 | 小 |
| API 新規追加 | なし | — |
| アーキテクチャ変更 | なし | — |

**追加工数見積もり: 1〜2 日**（元 MVP 完了後の追加作業として）。

---

## 8. 未決事項

- [ ] スコア算出式の最終確定（乗算 vs 重み付き平均 vs 別方式）→ プレイテストで検証。
- [ ] ヒントのバケット境界値の調整 → プレイテストで検証。
- [ ] 閾値 80% の妥当性 → GPS 精度を考慮した実地テストで検証。
- [ ] N=2 のケース: 2 点では多角形にならないため正多角形判定が不可。N≧3 を前提とするか、N=2 時は制約を無効にするか。
- [ ] 退化ケース（全員ほぼ同一地点）のスコア定義。
