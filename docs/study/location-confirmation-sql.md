# 位置確定時の凸包・面積を求めるSQL

## 何のために面積を求めるのか

topoは、複数のプレイヤーが実際に移動し、それぞれの位置で囲んだ範囲にある神社などのオブジェクト数を競うゲームである。範囲には、全員の点を囲む最小の凸な多角形である「凸包」を使う。

プレイヤーは現在地を「確定」し、範囲を作るための点を登録する。囲む範囲には面積の上限があるため、**新しい点を加えた形と面積を、保存前に求める**必要がある。本書では、この未保存の点を「候補点」と呼ぶ。

例えばAとBが確定済みなら、保存済みのA・Bと候補Cを合わせて計算する。以下のSQLは、この**候補を含む人数・凸包・面積**を返す。人数は全員が揃ったかの判断に、凸包は後続のオブジェクト集計に使う。

## 入力と処理の流れ

保存済みの確定点はplayerテーブルから読み、候補点は確定要求の緯度・経度を引数で受け取る。

| 入力 | 内容 |
| --- | --- |
| playerテーブル | 指定ゲームの確定済み座標。fixed_geomはgeometry(Point, 4326) |
| SQLの引数 | gameId、候補の経度、候補の緯度の順。SQL中の3つの`?`に対応 |

SQLは、点を集める処理、凸包を作る処理、返却値を取り出す処理に分ける。前段の結果を次の処理へ渡すために、`WITH 名前 AS (...)`を使う。これは括弧内の検索結果に名前を付け、後続のSELECTから表として参照する構文で、この名前付きの中間結果をCTEと呼ぶ。ここではpointsとhullという2つのCTEを定義する。

```text
① points：確定済みの点 ＋ 候補1点
       ↓
② hull：点の行数 ＋ 点群を囲む凸包
       ↓
③ 最終SELECT：人数・面積m²・凸包の文字列
```

以下の3つのSQL断片は、上から連続して1つのSQLを構成する。実装は[PostgisHullService](../../src/main/kotlin/com/github/gaomond/topo/adapter/persistence/PostgisHullService.kt)を参照。

## 1. points：対象の点を1列に集める

```sql
WITH points AS (
    SELECT pl.fixed_geom AS point_geom
    FROM player AS pl
    WHERE pl.game_id = ? AND pl.confirmed_at IS NOT NULL
    UNION ALL
    SELECT ST_SetSRID(ST_MakePoint(?, ?), 4326) AS point_geom
)
```

保存済みの点と未保存の候補点を、1つの検索結果にまとめる部分である。2つ目のSELECTにはFROMがなく、引数から空間データを1行だけ作っている。`ST_MakePoint`の引数順は経度・緯度で、`ST_SetSRID(..., 4326)`が座標の参照系を指定する。

`UNION ALL`は2つのSELECTの行を縦に連結する。同じ座標の人も別々に数えるため、重複行を残すALLを使う。UNIONだけなら重複を除く。[UNION公式資料](https://www.postgresql.org/docs/current/queries-union.html)

連結には列の数と型が対応している必要がある。ここでは両方がgeometryの1列を返す。連結結果の列名は最初のSELECTの`AS point_geom`で決まり、後続のCTEからはこの名前で参照する。

例えば2人が確定済みなら、pointsは次の3行・1列になる。

| point_geom |
| --- |
| Aの確定点 |
| Bの確定点 |
| 今回の候補点 |

## 2. hull：人数と凸包を1行にまとめる

```sql
, hull AS MATERIALIZED (
    SELECT
        COUNT(*) AS player_count,
        ST_ConvexHull(ST_Collect(po.point_geom)) AS hull_geom
    FROM points AS po
)
```


| 式 | 求める値 |
| --- | --- |
| `COUNT(*)` | pointsの行数。今回の候補を含む人数 |
| `ST_Collect(po.point_geom)` | 各行の点をひとまとめにした空間データ |
| `ST_ConvexHull(...)` | その点群を囲む最小の凸な形（凸包） |

凸包の出力列はhull_geomと名付け、個々の点のpoint_geomと区別している。内側にある点は凸包の頂点にならないが、人数には含まれる。

後段では、同じ凸包を面積計算と文字列出力の2か所で参照する。PostgreSQLは通常、CTEの処理を外側のSELECTへ組み込んで最適化する場合がある。ここでは`MATERIALIZED`でその展開を抑え、hullの計算結果を一度作ってから両方で参照させる。[WITH・MATERIALIZED公式資料](https://www.postgresql.org/docs/current/queries-with.html#QUERIES-WITH-CTE-MATERIALIZATION)

この段階の出力は1行・2列になる。

| player_count | hull_geom |
| --- | --- |
| 3 | 3点から作った凸包 |

凸包は点の配置によりPoint・LineString・Polygonになる。重複点だけならPoint、一直線ならLineStringで、面積は0となる。

## 3. 最終SELECT：面積と凸包の文字列を返す

```sql
SELECT
    h.player_count,
    ST_Area(h.hull_geom::geography) AS area_sqm,
    ST_AsText(h.hull_geom, 17) AS hull_wkt
FROM hull AS h
```

ここでの`h.hull_geom`は前段が作った凸包である。同じgeometryから、面積計算用のgeographyと、アプリへ返す文字列表現に処理が分かれる。

| 操作 | 役割 | 結果の型 |
| --- | --- | --- |
| `ST_MakePoint(lng, lat)` | 数値から点を作る | geometry |
| `ST_SetSRID(point, 4326)` | WGS84の緯度経度であると指定する | geometry |
| `ST_Collect` → `ST_ConvexHull` | 点群から凸包を作る。SRID 4326を引き継ぐ | geometry |
| `h.hull_geom::geography` | 測地計算用の型へ変換する | geography |
| `ST_Area(...)` | 面積をm²で求める | double precision |
| `ST_AsText(h.hull_geom, 17)` | 元のgeometryをWKT文字列にする | text |

SRIDは座標の参照系、geometry／geographyは型。`ST_SetSRID`では型が変わらないため、面積計算では別途geographyへキャストする。[ST_SetSRID](https://postgis.net/docs/ST_SetSRID.html)、[ST_Area](https://postgis.net/docs/ST_Area.html)

WKTは形と座標を表す文字列で、例えば三角形なら次の形になる。座標順は経度・緯度、末尾の点は先頭と同じで閉環を表す。

```text
POLYGON((139.7 35.6,139.8 35.6,139.7 35.7,139.7 35.6))
```

`17`は座標を文字列化する際の小数点以下の最大桁数（既定15）。丸めによる座標の変化を抑えるために指定している。ただし完全な精度保持の保証ではなく、公式は精度を保つ転送にはWKB（バイナリ表現）を推奨している。[ST_AsText](https://postgis.net/docs/ST_AsText.html)

最終的に返す1行は次の3列。hull_wktはKotlin側で座標列へ読み直す。

| 列 | 内容 |
| --- | --- |
| player_count | 候補を含む人数 |
| area_sqm | 凸包の測地面積（m²） |
| hull_wkt | 凸包のWKT文字列 |
