# Renderへの公開と更新手順

topoを開発環境のないスマートフォンから遊べるよう、フロント・API・DBをRenderへ配置する。初回は管理画面で構築し、画面のタイムアウト後は本人が許可した公式CLI・公式APIで設定を続けた。通常はGitHubの`main`への反映から更新する。[US-24](story/24.md)が対象仕様で、Dockerfileは[US-23](story/23.md)と共通。Render上でComposeは使わない。

```text
GitHub連携・支払い準備 → DB作成 → API起動・Flyway
  → 神社だけを投入 → フロント公開・CORS設定 → 公開URLでプレイ確認
通常更新：mainへの反映 → APIとフロントがそれぞれビルド・デプロイ
```

実URL・接続先・認証値は管理画面から取得する。この文書の山括弧はプレースホルダーであり、そのまま入力しない。秘密はRenderの実行時環境変数や手元のパスワード管理で扱い、Git・Dockerのbuild args・証跡・チャットへ記録しない。

現在の公開先は[フロント](https://topo-web.onrender.com)と[API](https://topo-api-7bc3.onrender.com)。公開先のソースは`704411b`で、作業中のUS-25の画面変更や未コミットのUS-24手順・SQLはまだ反映していない。実機GPSとOn Commitの実動作など、残る受け入れは末尾の確認記録を参照する。

## 1. 準備と初期構成

GitHubアカウント連携で`gaomond/topo`を選択する。Public Git RepositoryのURLを貼るだけの接続ではなく、自動デプロイできるGitHub連携を使う。Dockerfileなど必要な変更が、Renderの参照する`main`に入っていることを先に確認する。支払い方法の入力は本人が行う。**有料DB・APIの作成を確定する前に、プラン・容量・月額を提示して本人の許可を得る。** 初期構成への合意やカード登録を購入許可として扱わない。将来の増量・有料プラン変更も確定前に同じ確認を行う。

| 対象 | 管理画面の設定 | 月額目安 |
| --- | --- | --- |
| Workspace | Hobby | $0 |
| DB | Singapore、PostgreSQL 17、0.1 CPU / 256MB（`0.1c-256mb`） | $6 |
| DB storage | **1GBを明示指定、自動拡張オフ** | $0.30 |
| API | Singapore、0.5 CPU / 512MB（`0.5c-512mb`）、1 instance | $7 |
| フロント | Static Site | $0 |
| 合計 | 上記を1か月継続 | $13.30 |

2026-11-10確認の目安。税・為替・通信やビルドの利用枠超過は別。作成直前に実画面の金額を確認する。ストレージは計算資源のメモリとは別で、1GBから増量するときは5GB（ストレージ$1.50、合計$14.50）が次の容量となり、縮小できない。APIにはPersistent Diskを追加しない。料金の根拠と採用理由は[仕様](story/24.md#12-料金とdbストレージ)を参照する。

### 管理画面が利用できない場合

今回、管理画面のタイムアウトが繰り返されたため、公式Render CLI 2.36.0をHomebrewで導入し、本人がブラウザの`Authorize CLI`で7日間のCLI認証を許可した。Gaomondのアカウント・ワークスペースが一致することを確認し、残りの設定を許可済みの公式CLI・公式APIで行った。管理画面方式からの運用上の切替えで、Blueprintや新しい常設APIキーを必須条件に加えるものではない。認証値は記録しない。利用方法は[公式CLI](https://render.com/docs/cli)と[公式API](https://render.com/docs/api)を参照する。

今回はRenderが生成したDBパスワードの利用が本人から許可されている。生成元とAPIへ渡す値の一致を、実値をログへ出さず確認する。その他のパスワードを扱う必要がある場合は、先に本人へ確認する。

## 2. DBを作成する

`New → Postgres`で上記のリージョン・バージョン・計算資源・ディスクを設定する。名称候補は`topo-db`。作成後は稼働状態と、接続情報の参照場所を確認する。

- APIからは同じリージョンの**内部ホスト**へ接続する。
- 手元からの初回投入には**外部ホストとTLS**を使う。External Accessは投入元に必要なIP範囲に絞り、作業後に不要な許可を外す。
- DBを空で作成し、次節のAPI起動時にFlywayでPostGISとテーブルを作る。ローカルのFlyway履歴はコピーしない。

DBを作り直す操作やディスクの削除は通常更新に含めない。詳細は[RenderのDB作成・接続](https://render.com/docs/postgresql-creating-connecting)を参照する。

## 3. APIを配置する

`New → Web Service`でGitHub連携のリポジトリを選ぶ。名称候補は`topo-api`。

| 項目 | 値 |
| --- | --- |
| Branch / Auto-Deploy | `main` / `On Commit` |
| Runtime | Docker |
| Root Directory | 空欄（リポジトリ直下） |
| Dockerfile Path / Docker Build Context | `./Dockerfile` / `.` |
| Region / Instance | Singapore / 0.5 CPU・512MB |
| Health Check Path | `/actuator/health` |

実行時環境変数を設定する。フロントURLが未発行の間は、仮の実在しないオリジンで起動し、フロント作成後に実値へ変更する。全許可は使わない。

| キー | 値・取得元 |
| --- | --- |
| `SERVER_ADDRESS` | `0.0.0.0` |
| `SERVER_PORT` | `10000`。Renderの`PORT`と一致させる |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<内部ホスト>:5432/<DB名>`。DB画面の内部接続情報から組み立てる |
| `SPRING_DATASOURCE_USERNAME` | DB画面のユーザー名 |
| `SPRING_DATASOURCE_PASSWORD` | DB画面のパスワード。公開資料へ転記しない |
| `APP_CORS_ALLOWED_ORIGINS` | `<フロントのHTTPSオリジン>`。末尾スラッシュ・パスを含めない |
| `SPRING_JPA_SHOW_SQL` | `false` |
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS` | `never` |
| `JAVA_TOOL_OPTIONS` | `-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -Xss512k`（ローカル測定で採用。環境ごとの観測結果は第7節） |

`postgresql://...`のURIをそのままJDBC URLに指定しない。URLへ認証値を埋め込まず別変数へ設定する。Dockerfileの`EXPOSE 8080`は説明用メタデータで、実際の待ち受けは`SERVER_PORT`で変わる。

ビルド・起動ログでFlywayとJPAの検証成功を確認する。`https://<実APIホスト>/actuator/health`が200・`status: UP`、`/api/config`が200になることを確認する。healthはDB接続も含む。DB異常時は成功扱いにせず、本文に接続情報や例外詳細を表示しない。SQL文を公開ログへ出力していないことも確認する。

## 4. 神社だけを移す

OSM加工済みのローカル`game_object`から`object_type = 'shrine'`だけをCSVにする。ゲーム・プレイヤー・他種別・Flyway履歴は移さない。`pg_dump -t game_object --data-only`は行を絞らないため、今回の公開移送では使わない。OSM一時テーブルから全種別を削除・再投入する既存SQLも、この移送には使わない。

### 書き出し

リポジトリ直下で実行する。CSVはGit管理外に置く。出力にはID・種別・名前と、座標参照系を含む座標のEWKB表現を保存し、名前の改行・引用符・NULLもCSVとして保持する。

```bash
mkdir -p .agent-pipeline/render-transfer
docker compose exec -T db psql -Xq -U topo -d topo -v ON_ERROR_STOP=1 \
  -f - < tools/osm/export_shrines.sql \
  > .agent-pipeline/render-transfer/shrine.csv

docker compose exec -T db psql -X -U topo -d topo -c \
  "SELECT count(*) FROM game_object WHERE object_type = 'shrine';"
```

### 公開DBへの投入

手元に`psql`を用意し、Renderの外部接続情報を参照して以下の変数を設定する。秘密の実値がシェル履歴やログに残る入力方法を使わない。パスワードは`psql`の対話プロンプト（または権限600の`.pgpass`）で渡す。

```bash
export PGHOST='<外部ホスト>'
export PGPORT=5432
export PGDATABASE='<DB名>'
export PGUSER='<DBユーザー>'
export PGSSLMODE=require

# psqlクライアントの作業ディレクトリにshrine.csvを置く。
# SQLのパスだけリポジトリの位置に置き換える。
cd .agent-pipeline/render-transfer
psql -X -W -v ON_ERROR_STOP=1 \
  -f /absolute/path/to/topo/tools/osm/import_shrines.sql
```

SQLは一時テーブルで種別・ID重複・Point型・SRID 4326・座標範囲を検証してから、1トランザクションで追加・更新する。異常時は全体を巻き戻す。再実行で同じIDを重複追加しない。同じIDの別種別が公開DBにある場合も失敗させる。ゲームなどは変更せず、CSVに含まれない既存行を削除しない。既存行を消して同期する操作ではないため、初回は空の公開DBへ投入する。

### 件数・空間検索・容量を確認する

同じ接続設定で実行する。神社件数を書き出し元と照合し、初回の公開DBには他種別がないこと、代表点の検索結果が元データと一致することを確認する。下の範囲は検索の例で、実際に遊ぶ地域の既知の範囲へ置き換える。

```sql
SELECT object_type, count(*) FROM game_object GROUP BY object_type;
SELECT bool_and(ST_SRID(geom) = 4326) FROM game_object;
SELECT id, name FROM game_object
WHERE object_type = 'shrine'
  AND ST_Covers(ST_MakeEnvelope(139.70,35.67,139.72,35.69,4326), geom)
ORDER BY id;
SELECT pg_size_pretty(pg_total_relation_size('game_object')) AS objects_with_indexes,
       pg_size_pretty(pg_database_size(current_database())) AS database_total;
```

DB全体の容量とRenderのディスク使用量は別々に確認する。WALなどを含むディスク残容量も管理画面で見る。移送ファイルは必要な期間だけ手元に保持し、通常のAPI再デプロイでは再投入しない。元データの出典・加工方法は[データ投入手順](data-loading.md)を参照する。

## 5. フロントを配置する

`New → Static Site`で同じGitHubリポジトリを選ぶ。名称候補は`topo-web`。発行URLは実画面から取得する。

| 項目 | 値 |
| --- | --- |
| Branch / Auto-Deploy | `main` / `On Commit` |
| Root Directory | `frontend` |
| Build Command | `npm ci && npm run build` |
| Publish Directory | `dist` |
| `NODE_VERSION` | `22`（22.12以降の22系） |
| `VITE_API_BASE_URL` | `<実APIのHTTPS URL>`。末尾スラッシュなし |
| Redirects/Rewrites | Source `/game/*`、Destination `/index.html`、Action `Rewrite` |
| Headers | Path `/THIRD_PARTY_LICENSES.txt`、Name `Content-Type`、Value `text/plain; charset=utf-8` |

`frontend/.node-version`の`22`をローカルのNode.js選択にも使い、22.12以降の22系でビルドする。Renderには`NODE_VERSION=22`も設定する。環境変数がファイルより優先されるため、今回のファイル追加で公開ビルドのメジャー指定は変わらない。

`VITE_API_BASE_URL`はビルド時に埋め込まれる。接続先変更後はフロントを再ビルドする。発行されたフロントのHTTPSオリジンをAPIのCORSに反映し、APIの更新完了を待つ。

RewriteはゲームURLを直接開くための設定。`?p=<playerId>`付きURLの直接表示・再読み込みでもプレイヤーを維持することを確認する。JS・CSS・画像・ライセンス本文は実ファイルとして配信し、HTMLへ置き換えない。Vite previewの設定だけではRenderのHeaderは設定されない。

作成画面・待機画面の「ライセンス・データ出典」、同じビルドのライセンス本文、結果を含む地図のLeaflet・OSM帰属を確認する。タイルへのRefererを止める一括ヘッダー設定は追加しない。外部のLICENSE・データ説明・加工スクリプトも認証なしで読めることを確認する。

設定方法の詳細は[Static Sites](https://render.com/docs/static-sites)、[Rewrite](https://render.com/docs/redirects-rewrites)、[Node.jsバージョン指定](https://render.com/docs/node-version)を参照する。

## 6. 公開確認・更新・復旧

公開URLで作成・3人参加・開始・2秒周期の位置共有・確定・結果を確認する。実際のスマートフォンでOSの位置情報許可とGPS取得、複数端末でのプレイを本人が確認する。ローカルの合成位置テストは実機の代わりにしない。API再デプロイ後にゲーム・神社データが残ること、Renderのメモリ使用量・OOM・再起動も確認する。確認後、READMEに実際のプレイ用URLを記載する。

`main`へのpush／マージでは、サービスのRoot Directory配下に変更がある場合に自動更新される。APIのRoot Directoryはリポジトリ直下、Static Siteは`frontend`なので、文書・SQLだけの変更はStatic Siteを更新しない。両サービスのOn Commitを確認するコミットには`frontend`配下の変更も含め、両方のDeploysで同じ対象SHA・自動デプロイ・成功を確認する。今回追加する`frontend/.node-version`は、合意済みNode.js 22系のビルド契約をGitにも残す設定で、この確認対象にもなる。Root Directoryの動作は[Monorepo Support](https://render.com/docs/monorepo-support)を参照する。同時切り替えは保証されないためAPIとフロントの互換性を保つ。手動再試行は`Manual Deploy → Deploy latest commit`。失敗時はBuild Logs／Runtime Logsと対象コミット、環境変数、healthを確認する。

アプリを戻す場合はDeploysから以前の成功版を選んでRollbackし、APIとフロントそれぞれの対象コミット、Auto-Deploy設定、再プレイを確認する。次のmain反映で戻した版が再更新されることにも注意する。**アプリのRollbackでDBスキーマは戻らない。** DB破損などはRenderのバックアップ・復旧手順で別途扱い、既存DBへ古いmigrationを適用し直さない。詳細は[デプロイ](https://render.com/docs/deploys)、[DBの復旧](https://render.com/docs/postgresql-backups)を参照する。

## 7. 今回の確認記録（2026-11-10）

ローカルとRenderの結果を区別する。API・フロントは公開されているが、US-24全体は受け入れ確認中・未承認。秘密・実プレイヤー位置は記録しない。

| 項目 | 状態 |
| --- | --- |
| 承認・認証 | DB月$6.30・API月$7はそれぞれ本人の作成許可後に作成。CLIの7日認証も本人許可済み。認証値は記録しない |
| GitHub main | 本人許可後に既存20コミットを704411bまでpush。API・Static Siteの初回デプロイは同SHAで成功。未コミットのUS-24・US-25は未反映 |
| 公開DB | Singapore・PG17.11/PostGIS3.6.2、0.1CPU/256MB、1GB、自動拡張オフ。SSL接続を確認 |
| 神社投入 | 実SQLで40,382件のみ投入。再export CSV 3,233,882bytesのSHA256が元と完全一致。投入前game/playerは0件。game_objectは索引込み6376kB、DB全体22MB |
| DB外部接続 | 投入完了後に公式CLIのclear-ip-allow-listを実行し、ipAllowList=[]で外部接続を全閉鎖。内部APIのhealthはUP継続。1GB・自動拡張オフ・256MBプランを維持。IP値は記録しない |
| 公開API | https://topo-api-7bc3.onrender.com 、512MB/0.5CPU、Singapore、1 instance、内部DB接続。Flyway1〜3成功。healthは200/UP・詳細非表示、configは200 |
| API CORS更新 | https://topo-web.onrender.com を設定し、手動デプロイdep-dbp9ca88v5cc73djbt9gがlive。許可Originは200、不許可は403 |
| 公開フロント | https://topo-web.onrender.com 。公式APIで/game/*のRewriteとライセンス本文Content-Typeを設定 |
| HTTPによる静的配信確認 | ?p付きゲームURLはindexと同じ本文で200、JS/CSSは実ファイル200、ライセンス本文40910bytesはUTF-8・text/plain; charset=utf-8・200。JSへ実API URLが埋込み済み |
| 公開APIの3人プレイ | 60回・2秒周期、04:00:43〜04:02:45 UTC、368要求成功。面積45237.39686512947m²・神社15件、独立PostGIS検索も15で一致。ネットワークを含むp95=411.72ms/max=1379.57ms。合成位置で実GPSではない |
| Renderメモリ | 負荷後を含む30秒間隔サンプル最大342,405,120bytes（約326.54MiB）、制限536,870,900bytes。真のピークではない。DBディスク観測最大96,956,420bytes/容量1,020,702,700bytes。意図的再起動以外の再起動・instance_failed・OOM等の異常イベントなし |
| 公開API再起動後の保持 | render restart後もonlineの時間変化以外、ゲーム・全確定位置・全結果が完全一致し神社15件。最終SQLでも神社40,382件・同ゲームCOMPLETED・migration成功3件・DB22MBを確認 |
| 公開ブラウザ | ?p付き結果URLで15個/0.045237km²・15マーカー・千葉神社popup、再読込後も同じ結果。Leaflet/OSM帰属と結果画面のライセンス入口非表示を確認 |
| タイル・Referer | OSMタイル画像18個がload。img.referrerPolicy空・referrer metaなし・HTTP Referrer-Policyなしで抑制設定は見られない。実送信Refererヘッダー自体は直接観測していない |
| 匿名ライセンス資料 | GitHubのLICENSE・docs/data-loading.md・tools/osm/game_object.lua・tools/osm/load_into_game_object.sqlは匿名で全200 |
| 最新5品質ゲート | 04:04 UTCに全成功、フロント100ファイルの実行前後ハッシュ一致。US-25を含む作業ツリーの検証で公開704411bとは別 |
| 本人の実機確認 | 2026-11-10、スマートフォンのOS位置情報許可・GPS取得と複数端末の作成→参加→開始→確定→結果表示の確認依頼に、本人が「実機確認できました」と回答。具体座標・個人データは記録しない |
| Node.jsの指定 | `frontend/.node-version`に`22`を追加。既存Render設定の`NODE_VERSION=22`を維持。公開ビルドのメジャー指定は変わらない |
| On Commit | `frontend`配下を含む新規コミットの本人許可前。実pushによるAPI・Static双方の自動更新は未確認 |

ローカルではLinux/ARM64・512MiB・swap追加0・CPU0.5で、3人60回・2秒周期118.08秒、372要求成功。cgroupの真のピーク318.67MiB、OOM0、確定・集計・再起動後の結果/確定位置保持を確認した。合成神社3件・公園1件での測定で、周期PUT/GET360件のp95は198.46ms、初回状態GET3件は約1205ms。全件500ms未満や長時間負荷の保証ではない。

公開先の確認とローカル測定を混同しない。本人の実機確認は回答を得ており、残る自動更新の実証と独立レビュー後にUS-24の最終判定を行う。
