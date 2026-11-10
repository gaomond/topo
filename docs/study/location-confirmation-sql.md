# 位置確定時・プレイ中の凸包・面積を求めるSQL

## 位置確定時の凸包・面積を求めるSQL

### 何のために面積を求めるのか

topoは、複数のプレイヤーが実際に移動し、それぞれの位置で囲んだ範囲にある神社などのオブジェクト数を競うゲームである。範囲には、全員の点を囲む最小の凸な多角形である「凸包」を使う。

プレイヤーは現在地を「確定」し、範囲を作るための点を登録する。囲む範囲には面積の上限があるため、**新しい点を加えた形と面積を、保存前に求める**必要がある。本書では、この未保存の点を「候補点」と呼ぶ。

例えばAとBが確定済みなら、保存済みのA・Bと候補Cを合わせて計算する。以下のSQLは、この**候補を含む人数・凸包・面積**を返す。人数は全員が揃ったかの判断に、凸包は後続のオブジェクト集計に使う。

### 入力と処理の流れ

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

### 1. points：対象の点を1列に集める

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

### 2. hull：人数と凸包を1行にまとめる

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

### 3. 最終SELECT：面積と凸包の文字列を返す

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

---

## プレイ中の位置・凸包・面積を取得するSQL

プレイ中は、地図に参加者の現在地と確定点を表示し、その配置から作った範囲と面積を確認できるようにする。そのためにクライアントが受け取るRESTレスポンスと、レスポンスを作るSQLを順に説明する。

### RESTインターフェイス：何を返すのか

クライアントは2秒ごとに次のAPIを呼ぶ。

```http
GET /api/games/{gameId}
```

成功時は200でゲームの状態を返す。以下は、位置と面積に関係する部分だけを抜粋した例。IDはA・B・Cに簡略化し、座標・時刻・面積は説明用の値としている。

```json
{
  "status": "ACTIVE",
  "players": [
    {
      "playerId": "A",
      "confirmedLocation": {
        "lat": 35.68, "lng": 139.76, "at": "2026-11-10T09:00:00Z"
      },
      "live": {
        "lat": 35.681, "lng": 139.761, "at": "2026-11-10T09:01:00Z"
      }
    },
    {
      "playerId": "B",
      "confirmedLocation": null,
      "live": {
        "lat": 35.69, "lng": 139.76, "at": "2026-11-10T09:01:00Z"
      }
    },
    {
      "playerId": "C",
      "confirmedLocation": null,
      "live": {
        "lat": 35.68, "lng": 139.77, "at": "2026-11-10T09:01:00Z"
      }
    }
  ],
  "currentArea": {
    "sqm": 500000.0,
    "hull": [
      [35.68, 139.76],
      [35.69, 139.76],
      [35.68, 139.77],
      [35.68, 139.76]
    ]
  }
}
```

| 項目 | 返す内容 | 画面での使い道 |
| --- | --- | --- |
| `players[]` | 指定ゲームの全参加者。自分も含む | プレイヤーごとのマーカーを表示する |
| `players[].confirmedLocation` | 保存済みの確定座標と確定時刻。未確定ならnull | 名前付きの確定点を表示する |
| `players[].live` | 最後に受信したライブ座標と記録時刻。未送信などで揃わなければnull | 移動する現在地のマーカーを表示する |
| `currentArea.sqm` | 現在の配置から計算した面積（m²） | 面積カウンターに表示する。表示時にkm²へ変換する |
| `currentArea.hull` | 凸包の頂点列。`[緯度, 経度]`の順で、多角形では末尾を先頭と同じ点にして閉じる | 囲んだ範囲を描画する |

Aは確定後に移動しているため、confirmedLocationとliveが異なる。**RESTでは両方を返す**ので、確定点を残したまま現在地も表示できる。面積計算にはAの確定点とB・Cのライブ点を使う。

currentArea全体は、ゲームがACTIVE以外の場合、または位置を採用できる人が3人未満の場合にnullになる。RESTの型定義は[GameStateResponse](../../src/main/kotlin/com/github/gaomond/topo/adapter/web/GameStateResponse.kt)を参照。

### RESTの各項目をどのSQLで作るのか

位置の一覧と、凸包・面積にはそれぞれ取得処理がある。[GetGameStateUseCase](../../src/main/kotlin/com/github/gaomond/topo/usecase/GetGameStateUseCase.kt)が両方の結果をまとめ、RESTレスポンスへ渡す。

```text
GET /api/games/{gameId}
  ├─ PlayerJpaRepository：全参加者のライブ位置・確定点を取得
  │    → players[]
  └─ CurrentAreaQueryAdapter：1人1点を採用して凸包・面積を計算
       → currentArea（ACTIVEのときに計算）
```

以下では、まずplayers[]の取得SQLを説明し、その後にcurrentAreaのSQLを3段階に分けて説明する。

### 1. players：全参加者のライブ位置と確定点を取得する

```sql
select id, display_name as "displayName", live_lat as "liveLat", live_lng as "liveLng",
       live_at as "liveAt", ST_Y(fixed_geom) as "fixedLat", ST_X(fixed_geom) as "fixedLng",
       confirmed_at as "confirmedAt"
from player where game_id = :gameId order by joined_at asc
```

指定したゲームのplayer行を、参加順に1人1行で取得する。引数はgameIdのみで、自分を除外する条件はない。確定点とライブ位置のどちらかを選ぶ処理もなく、それぞれを別の列として取得する。実装は[PlayerJpaRepository.findReadingsByGameId](../../src/main/kotlin/com/github/gaomond/topo/adapter/persistence/jpa/PlayerJpaRepository.kt)を参照。

| SQLの列 | Kotlin側での組み立て | RESTの項目 |
| --- | --- | --- |
| `id` | プレイヤーの識別子 | `players[].playerId` |
| `liveLat`・`liveLng`・`liveAt` | 3つが揃えばLiveLocation、揃わなければnull | `players[].live` |
| `fixedLat`・`fixedLng`・`confirmedAt` | 3つが揃えばConfirmedLocation、すべてなければnull。部分欠損は内部不整合として例外 | `players[].confirmedLocation` |

fixed_geomはPostGISの点なので、`ST_Y`で緯度、`ST_X`で経度を取り出す。取得した列からKotlin側で位置オブジェクトを組み立て、RESTのlat・lng・atへ変換する。

### currentAreaのSQLの流れ

面積用のSQLもgameIdだけを引数に取る。プレイヤーごとの返却用位置とは別に、**面積計算に使う点を1人につき1点選ぶ**。

```text
① selected：確定済みなら確定点、未確定ならライブ位置を採用
       ↓
② hull：採用人数・不整合の有無・凸包を集計
       ↓
③ 最終SELECT：面積を求め、凸包の頂点を1行ずつ取り出す
       ↓
Kotlin：面積と頂点列をまとめてcurrentAreaにする
```

以下の3つのSQL断片は、上から連続して1つのSQLを構成する。`WITH 名前 AS (...)`は、検索結果に名前を付けて後続のSELECTから参照する構文で、その名前付きの中間結果をCTEと呼ぶ。ここではselectedとhullの2つを使う。実装は[CurrentAreaQueryAdapter](../../src/main/kotlin/com/github/gaomond/topo/adapter/persistence/CurrentAreaQueryAdapter.kt)を参照。

### 2. selected：1人につき使う点を選ぶ

```sql
WITH selected AS (
    SELECT
        (fixed_geom IS NULL) <> (confirmed_at IS NULL) AS inconsistent,
        CASE
            WHEN fixed_geom IS NOT NULL AND confirmed_at IS NOT NULL THEN fixed_geom
            WHEN fixed_geom IS NULL AND confirmed_at IS NULL
                AND live_lat IS NOT NULL AND live_lng IS NOT NULL
                THEN ST_SetSRID(ST_MakePoint(live_lng, live_lat), 4326)
        END AS geom
    FROM player WHERE game_id = ?
)
```

`CASE`は、上から条件を調べて、最初に一致したWHENの値を返す。ここでは確定点を優先し、未確定の場合にだけライブ位置を使う。どの条件にも一致せず、ELSEもない場合はNULLになる。

| プレイヤーの状態 | geom | inconsistent |
| --- | --- | --- |
| 確定座標・確定時刻が両方ある | fixed_geom。ライブ位置がなくても採用する | false |
| 両方なく、ライブ緯度・経度がある | ライブ座標から作った点 | false |
| 両方なく、ライブ緯度・経度が揃っていない | NULL | false |
| 確定座標・確定時刻の片方しかない | NULL。ライブ位置では代用しない | true |

`<>`は「等しくない」を表す。両側の`IS NULL`の結果を比べることで、**確定座標と確定時刻の片方だけが欠けている場合**を検出する。この不整合を、単なるライブ未送信として扱わないため、geomとは別の列に残す。

ライブ位置から点を作る場合も、経度をX、緯度をYとして`ST_MakePoint`へ渡す。`ST_SetSRID(..., 4326)`は座標の参照系を指定し、座標の数字自体は変えない。

RESTの例では、Aが確定済み、B・Cが未確定なので、selectedは次の3行・2列になる。在室・離席による絞り込みは行わず、古いライブ位置でも座標が揃っていれば採用する。

| inconsistent | geom |
| --- | --- |
| false | Aの確定点 |
| false | Bのライブ点 |
| false | Cのライブ点 |

### 3. hull：採用人数・不整合・凸包を1行にまとめる

```sql
, hull AS (
    SELECT COUNT(geom) AS selected_count,
        COALESCE(BOOL_OR(inconsistent), false) AS inconsistent,
        ST_ConvexHull(ST_Collect(geom)) AS h
    FROM selected
)
```

| 式 | 求める値 |
| --- | --- |
| `COUNT(geom)` | geomがNULLでない行の数。位置を採用できた人数 |
| `BOOL_OR(inconsistent)` | 1人でも不整合ならtrue |
| `COALESCE(..., false)` | 集計対象が0行でBOOL_ORがNULLになった場合はfalseにする |
| `ST_Collect(geom)` | NULLを除いた点をひとまとめにした空間データ |
| `ST_ConvexHull(...)` | その点群を囲む凸包 |

位置を採用できない人の行はgeomがNULLになるため、ここでは`COUNT(geom)`を使って数から除く。同じ座標を持つ人は別々に数え、3人が同じ場所にいれば採用人数は3になる。凸包の頂点数とは区別する。[集約関数の公式資料](https://www.postgresql.org/docs/current/functions-aggregate.html)

GROUP BYのない全体集計なので、selectedが0行でも集計結果は1行残る。RESTの例では3人を採用し、次の1行・3列になる。

| selected_count | inconsistent | h |
| --- | --- | --- |
| 3 | false | Aの確定点とB・Cのライブ点から作った凸包 |

採用人数・凸包・面積は、1本のSQLで同じ点群から求める。PostgreSQLのREAD COMMITTEDではSELECTごとに読み取り時点が決まるため、人数と凸包を別々のSELECTで求めると、その間の位置更新によって入力がずれる余地がある。[READ COMMITTEDの公式資料](https://www.postgresql.org/docs/current/transaction-iso.html#XACT-READ-COMMITTED)

このSQL内では人数と図形の入力が揃うが、別SELECTで取得するplayers[]まで同じ読み取り時点に固定するものではない。

### 4. 最終SELECT：面積と頂点ごとの座標を返す

```sql
SELECT selected_count, inconsistent, ST_Area(h::geography) AS sqm,
    ST_Y(dp.geom) AS lat, ST_X(dp.geom) AS lng
FROM hull LEFT JOIN LATERAL ST_DumpPoints(h) AS dp ON true
ORDER BY dp.path
```

凸包をgeographyへ変換してm²で面積を計算する。また、**凸包を頂点1つにつき1行へ展開**し、Kotlinから座標列として返す。

| 式・指定 | 役割 |
| --- | --- |
| `ST_Area(h::geography)` | 凸包の測地面積をm²で求める。画面表示ではkm²へ変換する |
| `ST_DumpPoints(h)` | 凸包の各頂点を、点のgeomと位置を表すpathの組として返す |
| `LATERAL` | 左側のhullの値hを使って、頂点を展開する関数を呼ぶ関係を明示する |
| `AS dp` | 展開結果にdpという名前を付け、dp.geom・dp.pathで参照する |
| `LEFT JOIN ... ON true` | 関数が返した頂点をすべて結合し、0行でも左側の集計行を残す |
| `ST_Y(dp.geom)`・`ST_X(dp.geom)` | 点から緯度・経度を取り出す |
| `ORDER BY dp.path` | 頂点を元の形の中での順番に並べる |

pathは、頂点が形のどこにあるかを示す整数の配列。Polygonでは例えば`{1,1}`が外周の1番目の頂点、`{1,2}`が2番目の頂点を表す。この順で並べると、多角形では先頭と末尾が同じ閉環の座標列になる。[ST_DumpPointsの公式資料](https://postgis.net/docs/ST_DumpPoints.html)

LEFT JOINを使うのは、**採用できる点がなくても、不整合を検出するための集計行を失わないようにするため**。通常の内部結合では、頂点の展開結果が0行だと、左側の人数・不整合の情報まで結果から消える。LEFT JOINなら、その場合はlat・lngをNULLにした行が残る。[LATERAL・LEFT JOINの公式資料](https://www.postgresql.org/docs/current/queries-table-expressions.html#QUERIES-LATERAL)

3点が三角形を作る場合、出力は次の4行・5列になる。頂点順はSQLが返すpath順で、以下のA→B→Cは一例。人数・不整合・面積は各行で同じ値になる。

| selected_count | inconsistent | sqm | lat | lng |
| --- | --- | --- | --- | --- |
| 3 | false | 面積S | Aの緯度 | Aの経度 |
| 3 | false | 面積S | Bの緯度 | Bの経度 |
| 3 | false | 面積S | Cの緯度 | Cの経度 |
| 3 | false | 面積S | Aの緯度 | Aの経度 |

### 5. Kotlin：SQLの結果をcurrentAreaへ組み立てる

Kotlin側は先頭行から人数・不整合・面積を読み、各行のlat・lngから頂点列を作る。判定は次の順で行う。

| 順番 | 条件 | 扱い |
| --- | --- | --- |
| 1 | inconsistentがtrue | 内部不整合として例外にする。人数が少なくても先に検出する |
| 2 | 採用人数が3人分未満 | CurrentArea全体をnullで返す。画面上は未計測 |
| 3 | 3人分以上 | 面積と頂点列を返す。頂点が取得できない場合は内部不整合として例外にする |

3人分以上でも、全員が同じ場所なら凸包はPoint、一直線ならLineStringになる。その場合は**面積0と点・線の座標列**を返し、位置が不足している未計測とは区別する。

正常な結果では、先頭行のsqmをcurrentArea.sqmへ、各行のlat・lngを並べた座標列をcurrentArea.hullへ渡す。上の出力例なら、`[[Aの緯度, Aの経度], [Bの緯度, Bの経度], [Cの緯度, Cの経度], [Aの緯度, Aの経度]]`になる。selected_countとinconsistentはサーバー内の判定に使い、RESTの項目には含めない。

ここで作ったcurrentAreaと、最初のSQLで取得したplayers[]をUseCaseがまとめることで、章の冒頭に示したRESTレスポンスになる。

実PostGISを使う[CurrentAreaQueryPersistenceTest](../../src/test/kotlin/com/github/gaomond/topo/adapter/persistence/CurrentAreaQueryPersistenceTest.kt)で、確定後のライブ移動で面積が変わらないこと、未確定者の移動による変化、同座標3人の面積0、採用点が不足していても部分欠損を検出することを確認している。
