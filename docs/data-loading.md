# 地図・神社データの出典・ライセンスと投入手順

topoは、OpenStreetMap（OSM）から取得した神社の位置データを使い、プレイヤーが囲んだ範囲内の件数を集計する。MVPの集計対象は神社（`shrine`）のみ。この文書に、元データの取得先、加工方法、利用条件、ローカル・公開環境への投入手順をまとめる。

データの流れは、**OSMの取得 → タグによる抽出・座標変換 → 一時テーブルへの投入 → `game_object`への移送 → ゲーム内での集計**。

## 出典とライセンス

- **データの出典：** © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright)。神社データには [Open Database License（ODbL）1.0](https://opendatacommons.org/licenses/odbl/1-0/) が適用される。
- **元データの取得先：** [Geofabrikの日本データ](https://download.geofabrik.de/asia/japan.html)。手順では `japan-latest.osm.pbf` を使用する。地域別・過去のファイルも取得先から参照できる。
- **加工方法の提供：** ODbL 4.6に基づく加工方法の提供として、この文書と後述のLua・SQL・スキーマ定義を公開する方針とする。公開アプリの共通ダイアログから、この説明のGitHub公開版へ案内する（[US-18](story/18.md)）。リリース前に本文と参照先を認証なしで読めることを確認する。
- **コードとの区別：** topoのコード・文書は [MIT License](../LICENSE)。OSM由来のデータにはODbLが適用され、依存ライブラリにはそれぞれのライセンスが適用される。OSM由来のデータを再配布する場合も、帰属表示とODbLの条件に従う。

`latest`の内容は更新されるため、同じデータを再現する際は、使用した元ファイル・対象地域・データ時点を特定する。既存の投入済みデータの時点は、この文書には未記録。

## 加工方法と集計の限界

加工の実装は [`tools/osm/game_object.lua`](../tools/osm/game_object.lua) と [`tools/osm/load_into_game_object.sql`](../tools/osm/load_into_game_object.sql)、保存先のスキーマは [Flywayの初期定義](../src/main/resources/db/migration/V1__init.sql)を参照する。

| 処理 | 内容 |
| --- | --- |
| 神社の抽出 | Luaの `classify()` で `amenity=place_of_worship` かつ `religion=shinto` の地物を `shrine` に分類する |
| 点の変換 | OSMのnodeはその座標を使う |
| 敷地・線の変換 | 閉じたwayはポリゴンの重心、それ以外のwayは線の重心を使う。座標参照系はWGS84（SRID 4326） |
| 保存する値 | OSMのID、種別、名前、座標を一時テーブル `osm_game_object_stage` に保存する |
| 本テーブルへの移送 | SQLで対象種別の既存行を削除して移送する。nodeとwayで同じIDがある場合は `ON CONFLICT DO NOTHING` で後続を取り込まない |

投入スクリプトは寺院・学校・コンビニ・公園・駅にも対応しているが、MVPで選択・集計するのは神社のみ。現地のすべての神社を網羅するとは限らず、OSMの登録状況・タグ・取得時点に左右される。敷地は代表点として扱うため、敷地全体と囲んだ範囲の重なりを判定するものではない。

## 背景地図の配信と帰属表示

背景地図にはOpenStreetMap標準タイル（`https://tile.openstreetmap.org/{z}/{x}/{y}.png`）を使う。地図画像の配信は神社データの投入とは別の仕組みで、[タイル利用ポリシー](https://operations.osmfoundation.org/policies/tiles/)に従う。

- プレイ画面・結果画面で、OSMの著作権・ライセンス説明へのリンク付き帰属表示を維持し、他のUIで隠さない。
- ブラウザの通常のHTTPキャッシュを使い、配信元のキャッシュ指定に従う。
- タイルの一括取得・先読み・オフライン用ダウンロードは行わない。
- 公開環境でも、配信元へ有効なRefererが送られる設定を維持する。
- 配信は継続・可用性が保証されるサービスではない。利用状況に応じて配信元の変更を検討する。

## 投入前の準備

- `osm2pgsql`（flex出力対応）を用意する。macOSでは `brew install osm2pgsql` で導入できる。
- Geofabrikから取得したOSMファイル（例：`japan-latest.osm.pbf`）をリポジトリ直下に置く。元ファイルはGitに含めない。
- リポジトリのルートで `docker compose up -d --build` を実行し、API起動時のFlywayでスキーマを作成しておく。DBの接続準備はComposeが待つ。`docker compose logs -f api` でAPIの起動完了を確認してから、以下の投入へ進む。スキーマはFlywayが管理し、データは別途 [osm2pgsql](https://osm2pgsql.org/) で投入する。既存の保存ボリュームを維持し、APIの再起動・再ビルドでは地物データを再投入しない。

## ローカルへの投入

以下はリポジトリのルートから実行する。

```bash
# 1. 抽出・変換したデータを一時テーブルへ投入する
PGPASSWORD=topo osm2pgsql \
  --output=flex --style=tools/osm/game_object.lua \
  --database=topo --user=topo --host=localhost --port=5432 \
  --slim --drop --cache=2000 \
  japan-latest.osm.pbf

# 2. 一時テーブルからgame_objectへ移送する（再実行可能）
docker compose exec -T db psql -U topo -d topo -v ON_ERROR_STOP=1 \
  -f - < tools/osm/load_into_game_object.sql

# 3. 種別ごとの投入件数を確認する
docker compose exec -T db psql -U topo -d topo -c \
  "SELECT object_type, count(*) FROM game_object GROUP BY object_type ORDER BY count(*) DESC;"
```

## 公開環境への投入

公開環境もFlywayでスキーマを作成する。ローカルで上記の投入を行い、`game_object`のデータをダンプして公開環境へ移す。

```bash
# ローカルのデータを出力する（スキーマはFlywayが管理するためデータのみ）
docker compose exec -T db pg_dump -U topo -d topo -t game_object --data-only \
  > game_object.sql

# 公開環境へ投入する
psql "$PROD_DATABASE_URL" -f game_object.sql
```

大きなダンプにはカスタム形式（`pg_dump -Fc` と `pg_restore`）を使う。

## 将来、対象種別を追加する場合

`tools/osm/game_object.lua`の `classify()` と、`tools/osm/load_into_game_object.sql`の `DELETE ... IN (...)` に対象種別を追加する。ゲーム側で選択可能にする変更は、別途仕様を決めて対応する。
