# 02-plan: US-02 クライアント雛形（地図＋自分の現在地GPS表示）— React 19 + Vite 版

> 対象: 確定仕様 `.agent-pipeline/01-spec.md`（US-02）。
> 本ストーリーは **フロントエンドのみ**。サーバー側（Kotlin / Spring / DB / Flyway）の変更は **含まない**。
> 技術スタックが確定（CLAUDE.md / docs/DESIGN.md 更新済み）したため、旧 vanilla 版 02-plan を破棄し、
> **React 19 + Vite + TypeScript** の独立プロジェクト `frontend/` 前提に作り直す。コードは書かない。

---

## 前提となる既存資産・環境の確認結果

- `frontend/`: **未作成**（Glob で 0 件）。本ストーリーで新規にスキャフォールドする。
- `src/main/resources/static/`: 既存の vanilla 実装が 6 ファイル存在する。
  - `index.html` / `styles.css` / `map-view.js` / `error-view.js` / `geo-tracker.js` / `app.js`
  - これらは US-02 の挙動を**先に実装済み**の資産。React へ**挙動移植**したうえで**削除**する（後述・単位 9）。
- `db/migration/`: **0 件**（本ストーリーは DB 非依存。確認のみ）。
- `src/`（Kotlin）: 本ストーリーでは触らない。React SPA は別デプロイ（DESIGN.md §4「`resources/static` には同梱しない」）。
- DESIGN.md §4 / 技術スタック補足が新スタックに更新済み。本計画はそれに整合させる。

### 既存 vanilla 実装から移植すべき確定挙動（geo-tracker.js が一次情報）

React 化で**ロジックを変えない**。以下を React フック / コンポーネントに 1:1 移植する。

- **状態モデル**: `INITIALIZING`（地図描画済み・初回測位待ち）/ `TRACKING`（測位成功・追従中）/ `DEGRADED`（追従中の一時失敗・地図維持＋控えめ表示）/ `PERMISSION_ERROR`（初回拒否＝エラー画面・地図を出さない）。
- **D4/D5 分岐軸 = `everTracked`**（一度でも測位成功したか）:
  - `!everTracked && PERMISSION_DENIED` → `PERMISSION_ERROR`（watch を clear・地図を隠す・エラー画面表示）。
  - それ以外のエラー（TRACKING 経験後の `TIMEOUT` / `POSITION_UNAVAILABLE` / `PERMISSION_DENIED`、および初回の非許可系失敗）→ `DEGRADED`（地図維持・控えめバナー・エラー画面に遷移しない）。
- **成功時**: `setSelfLocation(lat,lng)` でピン更新、地図 show、エラー画面 hide、DEGRADED バナー hide、状態 `TRACKING`。
- **watch オプション**: `{ enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }`。
- **再試行 (`retry`)**: エラー画面を畳み、状態を `INITIALIZING` に戻し、既存 watch を `clearWatch` してから `watchPosition` を貼り直す。許可済みに変わっていれば `TRACKING` 復帰。
- **安全コンテキスト**: 起動時に `isSecureContext` / `navigator.geolocation` の有無を確認。非安全/未提供なら測位を開始せず「localhost / https で開いてください」を明示（再試行ボタンは出さない）。地図は描画したまま測位だけ不可。
- **地図ビュー**: OSM 標準タイル（`https://tile.openstreetmap.org/{z}/{x}/{y}.png`、attribution 必須、maxZoom 19）。初期中心は東京駅付近 `[35.681236, 139.767125]`・初期ズーム 5。初回測位で `SELF_ZOOM = 16` に寄せ、以降は中心追従（panTo 相当）。自分ピンのみ（友達ドット live marker は対象外で導入しない）。
- **gameId / playerId を一切参照しない**。サーバー送信もしない。座標計算もしない（追従はピン移動のみ）。

---

## 設計上の重要判断（実装前に固定）

### スタック / 配置

- リポジトリ直下 `frontend/` に**独立 Vite プロジェクト**。Spring の `resources/static` には置かない。品質ゲートはフロント側 npm scripts（`lint` / `typecheck` / `test` / `build`）で回し、`./gradlew build` とは独立。
- 地図は **react-leaflet** で Leaflet を宣言的に統合（素の命令的ラップ `L.map(...)` は新コードでは使わない）。Leaflet は描画専用・座標計算しない（CLAUDE.md）。
- スタイル = **Tailwind CSS v4**（`@tailwindcss/vite` プラグイン）。Lint/Format = **Biome**。テスト = **Vitest + @testing-library/react（jsdom）**。pre-commit = **husky + lint-staged**。

### Smart / Dumb 分割（CLAUDE.md 準拠）

- **Smart**: 現在地トラッキングを所有するフック `useGeoTracking`（`watchPosition`・状態遷移・`everTracked` 分岐・retry を内包）と、それを使ってビューを出し分けるコンテナ `GeoTrackingContainer`。**唯一の状態保持・副作用担当**。API 通信は本ストーリーに無い。
- **Dumb**: `MapView`（react-leaflet で OSM タイル＋自分ピン）/ `LocationErrorScreen`（文言＋再試行ボタン or 非安全コンテキスト表示）/ `DegradedBanner`（控えめバナー）。props で受け取り描画するのみ。Geolocation も共有状態も知らない。
- **テスト可能化（注入）**: フック `useGeoTracking` は `geolocation`（`Geolocation` 相当）と `isSecureContext`（boolean）を**引数で注入可能**にし、既定で `navigator.geolocation` / `window.isSecureContext` を使う。これにより Vitest で Geolocation をモックして状態遷移ロジックを検証できる（旧 geo-tracker.js の `deps.geolocation` / `deps.isSecureContext` 注入を踏襲）。

### 本ストーリーで**入れない**依存（スコープ明記）

- `react-router-dom`: URL（gameId / playerId）解釈は US-04 / US-05。**導入しない**。
- `zod`: API スキーマ検証は US-07 以降。**導入しない**。
- シグナリングサーバ系（`express` / `ws` / `@types/express` / `@types/ws` / `reconnecting-websocket` / `tsx` / `signaling:*` scripts / `fake-indexeddb`）: 本プロジェクトに不要。**入れない**。

---

## 実装単位（依存順 / scaffold → Dumb → Smart フック → 結線 → テスト → 旧 static 削除）

> 依存方向: スキャフォールド → 描画専用の Dumb → 状態・副作用を持つ Smart（フック→コンテナ）→ エントリ結線 → テスト → 旧資産削除。
> 命名はユビキタス言語に従う（自分ピン = self、友達ドット live marker は本ストーリー非対象で導入しない）。

### 1. `frontend/` スキャフォールド一式 — 層: ui（プロジェクト基盤）

- 目的: 独立 Vite + React 19 + TS プロジェクトの土台を作る。ビルド / Lint / 型 / テストの各ゲートが空でも回る状態にする。
- 対象ファイル（役割）:
  - `frontend/package.json` — 後述「npm 構成」を厳密に反映（scripts / dependencies / devDependencies / lint-staged）。
  - `frontend/tsconfig.json`（ルート / references）, `frontend/tsconfig.app.json`（アプリ用・`src` 対象）, `frontend/tsconfig.node.json`（vite.config 等のツール用）— `tsc -b` の project references 構成。strict 有効。
  - `frontend/vite.config.ts` — `@vitejs/plugin-react` と `@tailwindcss/vite` を登録。Vitest 設定（`test.environment = "jsdom"`、`test.globals`、`setupFiles`）をここに同梱（または `vitest.config.ts` 分離可・実装時判断）。
  - `frontend/biome.json` — Biome の lint/format ルール（`biome check .` / `biome format --write .` が通る最小設定）。
  - `frontend/vitest.setup.ts` — `@testing-library/jest-dom` 相当の matcher 登録 / jsdom 補完（必要なら）。`setupFiles` から読む。
  - `frontend/index.html` — Vite のエントリ HTML。`<div id="root">` と `<script type="module" src="/src/main.tsx">`。`lang="ja"` / viewport メタ。**Leaflet を CDN で読まない**（npm の `leaflet` + `react-leaflet` を import）。
  - `frontend/src/main.tsx` — React 19 エントリ（`createRoot(...).render(<App/>)`）。Tailwind のエントリ CSS と Leaflet CSS を import。
  - `frontend/src/index.css`（Tailwind v4 エントリ。`@import "tailwindcss";`）。Leaflet の CSS（`leaflet/dist/leaflet.css`）も読み込む（main.tsx か CSS で）。
  - `frontend/src/App.tsx` — ルートコンポーネント（このストーリーでは `GeoTrackingContainer` を 1 枚出すだけ。ルーティングなし）。
  - `frontend/.gitignore`（`node_modules` / `dist` 等）, `frontend/biome` 無視設定は biome.json 側。
  - `frontend/.husky/`（husky init で生成される pre-commit フック。`lint-staged` を呼ぶ）。
- 完了条件:
  - `npm install` 後、`npm run typecheck` / `npm run lint` / `npm run test`（`--passWithNoTests`）/ `npm run build` がすべて成功する（中身が空でも通る土台）。
  - `npm run dev` で Vite が起動し、`http://localhost:5173`（localhost = 安全コンテキスト）で空の `#root` が表示できる。
  - 除外依存（シグナリング系 / react-router-dom / zod）が `package.json` に**入っていない**こと。
- 検証方法: 上記 npm scripts を実行し全て緑。`package.json` の deps/devDeps を目視で構成表と突合。

### 2. `MapView`（Dumb / react-leaflet ラッパ） — 層: ui（Dumb）

- 目的: react-leaflet で OSM 標準タイル地図＋自分ピンを宣言的に描画する。状態も Geolocation も知らない純表示部品。
- 対象ファイル: `frontend/src/components/MapView.tsx`
- 完了条件:
  - props: `selfLocation: { lat: number; lng: number } | null`（未測位は null）。座標は DESIGN.md の `{ lat, lng }`（WGS84）に揃える。
  - `MapContainer` + `TileLayer`（`url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"`、attribution 必須、`maxZoom={19}`）。初期 center 東京駅付近 `[35.681236, 139.767125]` / 初期 zoom 5。
  - `selfLocation` が非 null のとき自分ピン（`Marker`）を表示。初回設定で `SELF_ZOOM=16` に寄せ、以降は中心を現在地に追従させる（地図中心更新は react-leaflet の `useMap` を使う小コンポーネント or `MapContainer` の制御で実現。命令的 `L.map` は使わない）。
  - 計算・凸包・面積・補間は一切しない（CLAUDE.md）。API も共有状態も参照しない（Dumb 制約）。
  - 自分ピンは self を表す命名。Leaflet のデフォルトマーカーアイコンが Vite バンドルで欠落しないようアイコン解決を行う（`leaflet` の icon 設定。実装時に対処、本計画では論点として明記）。
- 検証方法: 手動（単位 8 のブラウザ統合）でタイル描画・パン/ズーム・ピン中心寄せを確認。ユニットでは jsdom 上で `MapContainer` がクラッシュせず `selfLocation` 変化でピン有無が変わることを軽く確認（描画タイルの実ロードは手動側）。

### 3. `LocationErrorScreen`（Dumb / エラー画面） — 層: ui（Dumb）

- 目的: 初回許可拒否（PERMISSION_ERROR）と非安全コンテキストの全面表示を描画する純表示部品。文言＋「再試行」ボタン。
- 対象ファイル: `frontend/src/components/LocationErrorScreen.tsx`
- 完了条件:
  - props: `message: string`、`onRetry?: () => void`（非安全コンテキスト時は未指定 = 再試行ボタンを出さない）。
  - 全面オーバーレイ（Tailwind）。`role="alertdialog"`。`onRetry` があるときのみ「再試行」ボタンを描画し、押下で `onRetry` を呼ぶ。
  - 文言は旧実装を移植: 拒否時「位置情報の利用が許可されませんでした。…再試行してください。」/ 非安全時「位置情報は安全な接続でのみ利用できます。localhost または https で開いてください。」。
  - 状態・Geolocation を持たない（Dumb 制約）。再試行ロジック自体は Smart が所有。
- 検証方法: ユニット（@testing-library/react）で、message が出ること / `onRetry` あり時にボタン表示・クリックでコールバック発火 / `onRetry` なし時にボタン非表示、を検証。

### 4. `DegradedBanner`（Dumb / 控えめバナー） — 層: ui（Dumb）

- 目的: 追従中の一時失敗（DEGRADED）時の控えめバナー（「位置更新が滞っています」）を描画する純表示部品。地図は隠さない。
- 対象ファイル: `frontend/src/components/DegradedBanner.tsx`（小さいので 3 と同ファイル統合も可・実装時判断。本計画では分離前提で記載）。
- 完了条件:
  - props: `visible: boolean`。`visible` のとき地図上部に重なる控えめバナーを表示（`role="status"` / `aria-live="polite"`）。地図は隠さない（オーバーレイのみ）。
  - エラー画面（単位 3）とは**別物**。DEGRADED でエラー画面に遷移しないことをコンポーネント分離で担保。
- 検証方法: ユニットで `visible` の true/false で表示切替を検証。地図を覆わない（全面オーバーレイでない）ことを Tailwind クラスで担保。

### 5. `useGeoTracking`（Smart / フック・状態遷移） — 層: ui（Smart）

- 目的: `watchPosition` の取得・許可/エラーの解釈・状態遷移（INITIALIZING/TRACKING/DEGRADED/PERMISSION_ERROR）と `everTracked` 分岐・retry を所有する唯一のロジック中核。旧 `geo-tracker.js` の挙動を 1:1 で React フックへ移植する。
- 対象ファイル: `frontend/src/hooks/useGeoTracking.ts`（状態定数 `GeoState` を含む。型は別ファイル `frontend/src/hooks/geoState.ts` に切り出しても可）。
- 完了条件:
  - シグネチャ（注入可能）: `useGeoTracking({ geolocation?, isSecureContext? })` を受け、`{ state, selfLocation, errorMessage, retry }` 相当を返す。既定 `geolocation = navigator.geolocation` / `isSecureContext = window.isSecureContext`。
  - 起動（マウント / `useEffect`）時に安全コンテキスト & `geolocation` 有無を確認。非安全/未提供なら `PERMISSION_ERROR` かつ「非安全コンテキスト用 message」を返し、`watchPosition` を開始しない（地図は描画したままにできるよう、コンテナ側で地図は出す）。
  - `watchPosition(onSuccess, onError, { enableHighAccuracy:true, timeout:10000, maximumAge:0 })` を開始。`watchId` を保持し、アンマウント / retry 時に `clearWatch`。
  - onSuccess: `selfLocation` を更新、`everTracked = true`、状態 `TRACKING`（DEGRADED バナーも自然に消える）。
  - onError 分岐（**D4/D5 を厳密移植**）:
    - `!everTracked && code === PERMISSION_DENIED` → `clearWatch` し `PERMISSION_ERROR`（拒否用 message）。
    - それ以外 → `DEGRADED`（地図維持・エラー画面に遷移しない）。
  - `retry()`: 状態を `INITIALIZING` に戻し、`clearWatch` 後に `watchPosition` を貼り直す。許可済みなら成功で `TRACKING` 復帰。
  - gameId / playerId を参照しない。サーバー送信しない。座標計算しない。
  - `everTracked` は再レンダリングで揺れないよう `useRef` で保持する（state ではなく ref。旧実装のローカル変数と等価）。`watchId` も `useRef`。
- 検証方法: 単位 7 の Vitest ユニット（Geolocation モック）で全分岐を検証（後述・受け入れ条件 1:1 対応表）。

### 6. `GeoTrackingContainer`（Smart / コンテナ・結線） — 層: ui（Smart）

- 目的: `useGeoTracking` を使い、状態に応じて Dumb（`MapView` / `LocationErrorScreen` / `DegradedBanner`）を出し分ける結線点。地図は測位成否と独立に**まず描画**する。
- 対象ファイル: `frontend/src/containers/GeoTrackingContainer.tsx`（`App.tsx` から描画）。
- 完了条件:
  - `useGeoTracking()` から `state` / `selfLocation` / `errorMessage` / `retry` を受け取る。
  - 描画ルール:
    - 常に `MapView`（`selfLocation` を渡す）を出す。**ただし** `PERMISSION_ERROR` のときは地図を隠す（受け入れ条件「拒否でエラー画面・フォールバックしない／地図を出さない」。旧実装の `mapView.hide()` と等価）。
    - `PERMISSION_ERROR`: `LocationErrorScreen`（拒否 message + `onRetry={retry}`）を全面表示。**非安全コンテキスト由来**の PERMISSION_ERROR では `onRetry` を渡さない（再試行不可を移植）。→ 状態だけでなく「非安全由来か」を区別できるよう、フックは message とともに retry 可否を表現する（例: `canRetry` フラグ or `onRetry` を返す/返さない）。実装時にフック戻り値で表現する。
    - `DEGRADED`: `MapView` を維持しつつ `DegradedBanner visible` を重ねる。
    - `TRACKING` / `INITIALIZING`: `MapView` のみ（バナー / エラー画面なし）。
  - API 呼び出し・ポーリング・URL 解釈を含めない（スコープ厳守）。
- 検証方法: ユニットで状態別の出し分け（拒否→地図なし＋エラー画面、DEGRADED→地図＋バナー、TRACKING→地図のみ）を Geolocation モック経由で確認。通し確認は単位 8（手動）。

### 7. Vitest ユニットテスト（状態遷移ロジック中心） — 層: ui（テスト）

- 目的: Geolocation をモックして検証可能な範囲（状態遷移 D4/D5・再試行・控えめ表示・安全コンテキスト）を自動化する。spec §2 受け入れ条件・§3 テスト観点と 1:1 対応（下表）。
- 対象ファイル:
  - `frontend/src/hooks/useGeoTracking.test.ts`（フックの状態遷移。`@testing-library/react` の `renderHook` + フェイク Geolocation）。
  - `frontend/src/components/LocationErrorScreen.test.tsx` / `DegradedBanner.test.tsx`（Dumb の表示・コールバック）。
  - `frontend/src/containers/GeoTrackingContainer.test.tsx`（状態別の出し分け）。
  - フェイク Geolocation ヘルパ `frontend/src/test/fakeGeolocation.ts`（`watchPosition` / `clearWatch` を制御し、success/error コールバックを任意に発火できるスタブ。`PERMISSION_DENIED` / `TIMEOUT` / `POSITION_UNAVAILABLE` のコードを持つ error オブジェクトを供給）。
- 完了条件: 下記「テスト 1:1 対応表」の自動化対象がすべてグリーン。`npm run test` が成功。
- 検証方法: `npm run test`（`vitest run`）。

### 8. 手動ブラウザ統合検証手順の整備 — 層: ui（運用 / 手動）

- 目的: 自動化できないブラウザ統合（地図タイル実描画・OS/ブラウザの許可ダイアログ・DevTools の位置モック・安全コンテキスト・モバイル差）の手順を残す。
- 対象ファイル: 本計画 + finish ステージの確認メモに集約（`frontend/` に運用専用ファイルは増やさない方針。必要なら `frontend/README.md` に手順節を置くのは可・実装時判断）。
- 完了条件:
  - `npm run dev` → `http://localhost:5173`（localhost = 安全コンテキスト）で地図描画・許可ダイアログ・ピン・DevTools Sensors での位置モック移動による追従を確認。
  - 平文 http（localhost 以外）で測位が動かない＝非安全コンテキスト表示が出ることを確認。
  - iOS Safari / Android Chrome で許可ダイアログとピン表示が破綻しないこと（精度・補間は対象外）。
- 検証方法: 下表「手動」行に沿った目視確認。

### 9. 旧 vanilla static 資産の削除 — 層: ui（後片付け）

- 目的: React 版へ挙動移植が完了した後、`src/main/resources/static` の旧実装を**削除**して二重実装を残さない（DESIGN.md「`resources/static` に同梱しない」と整合）。
- 対象ファイル（削除）: `src/main/resources/static/index.html` / `styles.css` / `map-view.js` / `error-view.js` / `geo-tracker.js` / `app.js`。
- 完了条件:
  - 上記 6 ファイルを削除。`src/main/resources/static/` が空になる（または Spring 側で参照されていないことを確認）。
  - 削除前に「移植チェックリスト」（状態モデル / everTracked 分岐 / watch オプション / retry / 非安全コンテキスト / OSM タイル設定 / 初期中心・ズーム / 自分ピンのみ）が React 側で満たされていることを単位 7 のテストで確認済みであること。
  - 削除がバックエンドビルド（`./gradlew build`）を壊さないこと（static は静的配信のみで Kotlin から参照されていない前提を確認）。
- 検証方法: 削除後に `frontend` 側 `npm run build` と、念のため `./gradlew build` が通ること。`Glob src/main/resources/static/**/*` が空。

---

## 受け入れ条件 / テスト観点の 1:1 対応（自動 = Vitest / 手動 = ブラウザ統合）

| spec 由来 | 期待挙動 | 区分 | 検証単位・方法 |
|---|---|---|---|
| §2 地図描画 | 開くと OSM 標準タイル地図が出る | 手動 | 単位8: `npm run dev` でタイル実ロード目視 |
| §3 地図描画 | パン/ズームできる | 手動 | 単位8: 目視操作 |
| §2 現在地ピン | 許可で現在地にピン・中心が現在地 | 手動 | 単位8: 許可ダイアログ→ピン中心寄せ目視 |
| §3 追従 | 位置モック移動でピン追従 | 手動 | 単位8: DevTools Sensors で座標変更→ピン移動 |
| （補助）ピン有無 | `selfLocation` null/非null でピン有無が変わる | 自動 | 単位7: `MapView` 軽量レンダリングテスト |
| §2 拒否 | 初回拒否でエラー画面・地図を出さない | 自動 | 単位7: フックに `PERMISSION_DENIED`(everTracked=false) 注入 → `PERMISSION_ERROR` / コンテナで地図非表示・エラー画面表示 |
| §2/§3 再試行 | 再試行ボタンで `watchPosition` 再実行。許可済みに変えて復帰 | 自動 | 単位7: `retry()` で `clearWatch`→`watchPosition` 再呼び出し、その後 success 注入で `TRACKING` 復帰・エラー画面消滅 |
| §2/§3 一時失敗 | 追従開始後の TIMEOUT/POSITION_UNAVAILABLE で地図維持・控えめ表示・エラー画面に飛ばない | 自動 | 単位7: success 注入(everTracked=true)→ `TIMEOUT`/`POSITION_UNAVAILABLE`/`PERMISSION_DENIED` 注入 → `DEGRADED`、コンテナで地図維持＋バナー |
| §2/§3 安全コンテキスト | localhost/https で動作、平文httpで動作前提不成立を明示 | 自動+手動 | 自動(単位7): `isSecureContext=false` 注入 → `PERMISSION_ERROR`(非安全 message・再試行ボタンなし)。手動(単位8): 平文 http で表示確認 |
| §2 URL解釈なし | gameId/playerId を要求せず単体表示 | 自動 | 単位7/コンパイル: フック・コンテナが URL/router を一切 import しない（router 依存なし） |
| §3 控えめ表示の文言/role | DEGRADED バナーが控えめ表示（地図を隠さない） | 自動 | 単位7: `DegradedBanner visible` で表示・全面オーバーレイでないこと |
| §3 拒否の文言/再試行UI | エラー画面に文言＋再試行ボタン | 自動 | 単位7: `LocationErrorScreen` に message・`onRetry` ありでボタン発火 |
| §3 モバイル差 | iOS Safari / Android Chrome で破綻しない | 手動 | 単位8: 実機/エミュレータ目視 |
| 移植担保 | 旧 static と等価挙動で置換できる | 自動 | 単位7 グリーン → 単位9 で旧 static 削除 |

> 自動化の境界: 「タイルの実ロード・OS の許可ダイアログ・実 GPS・モバイル実機」はブラウザ/OS 依存のため手動。
> 「状態遷移（D4/D5）・retry・安全コンテキスト分岐・Dumb の表示/コールバック」は Geolocation を注入モックして Vitest で自動化する。

---

## 採用する npm 構成（`frontend/package.json` に反映）

> ユーザー提示の標準構成から**シグナリングサーバ関連を除外**。本ストーリーで未導入の `react-router-dom` / `zod` も入れない（後続 US で追加）。

- **scripts**:
  - `dev`: `vite`
  - `build`: `tsc -b && vite build`
  - `lint`: `biome check .`
  - `format`: `biome format --write .`
  - `typecheck`: `tsc -b --noEmit`
  - `test`: `vitest run --passWithNoTests`
  - `test:watch`: `vitest`
  - `preview`: `vite preview`
  - `prepare`: `husky`
- **lint-staged**: `*.{ts,tsx}` → `biome check --write` + `vitest run --passWithNoTests`
- **dependencies**: `react ^19`, `react-dom ^19`, `leaflet`, `react-leaflet`, `tailwindcss ^4`, `@tailwindcss/vite`
- **devDependencies**: `typescript ~5.9`, `vite ^8`, `@vitejs/plugin-react ^6`, `@biomejs/biome ^2.4`, `vitest ^4`, `@vitest/coverage-v8`, `@testing-library/react`, `jsdom`, `@types/react`, `@types/react-dom`, `@types/leaflet`, `@types/node`, `husky`, `lint-staged`
- **除外（入れない）**: `express` / `ws` / `@types/express` / `@types/ws` / `reconnecting-websocket` / `tsx` / `signaling:*` scripts / `fake-indexeddb` / `react-router-dom` / `zod`

---

## 実装順（依存順サマリ）

1. 単位1: `frontend/` スキャフォールド（package.json / tsconfig*/ vite.config / biome.json / vitest setup / index.html / main.tsx / index.css / App.tsx / husky）
2. 単位2: `MapView.tsx`（Dumb / react-leaflet）
3. 単位3: `LocationErrorScreen.tsx`（Dumb）
4. 単位4: `DegradedBanner.tsx`（Dumb・単位3 統合可）
5. 単位5: `useGeoTracking.ts`（Smart フック / 状態遷移・everTracked 分岐・retry・注入）
6. 単位6: `GeoTrackingContainer.tsx`（Smart コンテナ / 出し分け）→ `App.tsx` 結線
7. 単位7: Vitest ユニット（フック中心 + Dumb + コンテナ + fakeGeolocation）
8. 単位8: 手動ブラウザ統合検証
9. 単位9: 旧 vanilla static 6 ファイルを削除

> Dumb（描画専用）→ Smart（フック → コンテナ）→ 結線 → テスト → 旧 static 削除。地図描画は測位成否と独立に先に出す。
> サーバー（Kotlin/Spring/DB/Flyway）の変更は本ストーリーに無い。

---

## リスク・前提

- **挙動移植の同値性**: React 化でロジックを変えない。`everTracked` を `useRef` で持つ（state にすると再レンダリングで揺れ得る）。旧 `geo-tracker.js` を一次情報として D4/D5 分岐・watch オプション・retry を 1:1 移植する。
- **react-leaflet のマーカーアイコン欠落**: Vite バンドルで Leaflet の既定アイコン画像パスが壊れる既知問題がある。`leaflet` の icon 設定（`L.Icon.Default` のパス解決 or import 済み png）で対処する。本計画では論点として明記、実装時に解決。
- **安全コンテキスト**: Geolocation は https / localhost のみ動作。ローカルは `npm run dev`（localhost）、本番は HTTPS 前提（デプロイ先未決・DESIGN.md）。
- **非安全コンテキスト由来の PERMISSION_ERROR の区別**: 「初回拒否」と「非安全コンテキスト」はどちらもエラー画面だが、前者は再試行可・後者は再試行不可。フック戻り値で retry 可否（`onRetry` の有無 or `canRetry`）を表現してコンテナで出し分ける（旧実装の `showPermissionError` vs `showInsecureContextNotice` を移植）。
- **品質ゲートの独立**: フロントは npm scripts（lint/typecheck/test/build）で完了判定。`./gradlew build` とは独立。単位9 の static 削除がバックエンドビルドを壊さないことだけ確認する。
- **OSM タイル常用ポリシー**: 標準タイルは本番常用に制約あり。MVP は許容、本番プロバイダ差し替えは将来論点（spec スコープ外）。
- **テスト自動化の限界**: タイル実ロード・許可ダイアログ・実 GPS・モバイル実機は手動（単位8）。E2E（Playwright 等）は本ストーリー未導入（将来論点）。
- **スコープ厳守**: URL 解釈（gameId/playerId）・ルーティング・API 通信・ポーリング・友達ドット・面積メーター・ライブ位置送信・補間・zod は本ストーリーに入れない（US-04/05/07〜10 へ）。
