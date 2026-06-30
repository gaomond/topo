# 学習メモ: 空間インデックス & 測地系

PostGIS を使う上での最小限の勘所。PoC で空間クエリを書くための土台。

---

## 1. 空間インデックス (R-tree / GiST)

- **なぜB-treeではダメか**: B-treeは1次元の順序前提。緯度経度は2次元で大小の順序が定義できないので使えない。
- **R-tree**: 各オブジェクトを囲む最小の長方形 = **MBR (Minimum Bounding Rectangle, 最小外接矩形)** で代表させ、近いMBRをまとめて大きいMBRで束ね、階層化したツリー。
- **探索**: クエリ範囲QのMBRと各MBRを上から判定し、**交差する枝だけ降りるDFS**。交差しない枝は中を見ずに丸ごと枝刈り。
  - B-treeと違い、**降りる枝は1本とは限らない**（MBRは重なりうる）。
  - **親がQと交差 → 子も交差、とは限らない**（親MBRにはdead spaceがある）。だから子ごとに再判定が要る。これがR-treeにO(log n)保証がない理由。
- **当たり判定 (AABB)**: MBRは軸並行なので、`[xmin,ymin,xmax,ymax]` の各軸で区間が重なるかをANDするだけ（4比較）。幾何計算ゼロ・激安。これが速さの正体。
  - = LeetCodeの区間重なり判定の2次元版。
- **2段階処理（重要）**:
  1. **フィルタ**: R-tree(MBR)で候補を高速に絞る（安い・近似）。
  2. **リファイン**: 候補だけ本物の幾何で厳密判定（高い・正確）。多角形でも、まず外接矩形で絞り、次にRay casting等で点が多角形内か精査。
- **GiST**: PostgreSQLの汎用ツリーインデックスの器。`CREATE INDEX ... USING GIST (geom)` で空間列にR-treeを貼る呪文。**貼って初めて枝刈りが効く**（貼らないと全件スキャン）。
- **PostGISでは自動**: `ST_Contains` / `ST_DWithin` 等を書くと、上記2段階（MBRフィルタ→厳密判定）を内部で自動実行。`EXPLAIN` で `Index Cond` にMBRフィルタが見える。

### クエリ3タイプ（参考）
| クエリ | フィルタ(安い) | リファイン(高い) | 探索 |
|---|---|---|---|
| 多角形内カウント | 凸包のMBRで絞る | 点-多角形包含 | 交差枝DFS |
| 半径内 (ST_DWithin) | ±r正方形で絞る | 円内か距離計算 | 交差枝DFS |
| 最近傍 (KNN, `<->`) | MBRまでの最短距離 | 実距離 | 近い枝優先のヒープ探索 |

範囲系は「矩形で粗く絞ってDFS」、最近傍は「近い順にヒープで必要数だけ掘る」。

---

## 2. 測地系 (geometry vs geography)

- **geometry型**: 汎用の座標型。**平面として計算する**（座標系=SRIDは持っているが計算は平面近似）。
- **geography型**: 同じ座標を**地球(楕円体)の曲面として計算する**型。距離・面積が実 m / m² で返る。
- **SRIDは最初から付いている**（例 `geometry(Point, 4326)` の 4326 = WGS84）。`::geography` キャストは「座標系を特定する」操作ではなく、**計算を平面→曲面に切り替える指示**。
- **使い分け（実務ルール）**:
  | やりたいこと | 型 | 理由 |
  |---|---|---|
  | 面積・距離を実m²/mで | `::geography` | 歪まない・単位が正しい |
  | 包含判定 (中か外か) | `geometry` のまま | 狭域では歪みが判定を覆さない & 速い |

```sql
ST_Area(geom)             -- 平方度。無意味。✗（= ユークリッド距離問題の面積版）
ST_Area(geom::geography)  -- 実 m²。これが欲しい。✓
```

- **補足**: 特定地域を高速・高精度に扱うなら、その地域用の投影座標系（日本の平面直角座標系 JGD2011 など）に変換して平面計算する道もある。**MVPでは不要**（4326のgeographyキャストで十分）。

---

## 確定クエリのイメージ（2トピックの合体）

```sql
WITH hull AS (
  SELECT ST_ConvexHull(ST_Collect(fixed_point)) AS g
  FROM player WHERE game_id = :gid
)
SELECT
  ST_Area(hull.g::geography) AS area_sqm,   -- 測地で実m²
  count(o.*)                 AS object_count -- R-treeで絞ってカウント
FROM hull
LEFT JOIN game_object o
  ON o.object_type = :type
 AND ST_Contains(hull.g, o.geom)            -- geometryのまま包含(GiST発動)
GROUP BY hull.g;
```

- `ST_Contains` → GiST(R-tree)が候補を絞る（2段階の前段）。
- `::geography` → 面積を実m²で取得。

---

## 用語ミニ辞書
- **MBR**: Minimum Bounding Rectangle（最小外接矩形）。
- **AABB**: Axis-Aligned Bounding Box。軸並行な矩形同士の高速な重なり判定。
- **GiST**: Generalized Search Tree。空間インデックスを貼る器（`USING GIST`）。
- **ST_**: 空間関数の接頭辞（Spatial Type）。
- **SRID**: 座標系のID（4326 = WGS84 = GPS標準の緯度経度）。
- **`:x`**: バインドパラメータ（値の差し込み穴）。 **`::型`**: 型キャスト。
- **priority queue ≈ heap**: 機能(優先度付き取り出し)と、その実装(ヒープ)の関係。
