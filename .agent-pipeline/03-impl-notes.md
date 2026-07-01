# 03-impl-notes — US-02: クライアント雛形（地図＋自分の現在地GPS表示）React 19 版

> 対象仕様: `.agent-pipeline/01-spec.md`（US-02） / 実装計画: `.agent-pipeline/02-plan.md`（React 19 + Vite 版）。
> 本ストーリーは **フロントエンドのみ**。サーバー（Kotlin / Spring / DB / Flyway）の変更は無い。
> 旧 vanilla 実装（`src/main/resources/static/` 6 ファイル）の挙動を React へ 1:1 移植し、旧資産は削除した。
> 05-review.md は旧 vanilla 実装に対する **承認** 記録。React への差し戻し指摘は無く、med/low の改善提案（初回未測位時のバナー文言・retry の防御確認・アイコン self-host 引き継ぎ）を本実装で考慮した。

---

## 成果物（`frontend/` 一式）

### スキャフォールド / 設定
- `frontend/package.json` — scripts（dev / build / lint / format / typecheck / test / test:watch / preview / prepare）、lint-staged、deps / devDeps。**除外依存**（react-router-dom / zod / シグナリング系）は入れていない。`@testing-library/user-event` のみ計画に対して追加（クリック等の DOM 操作テストの標準手段。スコープ拡張ではなくテスト基盤）。
- `frontend/tsconfig.json`（references）/ `tsconfig.app.json`（src 用・strict）/ `tsconfig.node.json`（vite.config 用）。`tsc -b` の project references 構成。
- `frontend/vite.config.ts` — `@vitejs/plugin-react` + `@tailwindcss/vite`。Vitest 設定（jsdom / globals / setupFiles）を同梱。
- `frontend/biome.json` — Biome 2.5 lint/format。`linter.rules.preset: "recommended"`（旧 `recommended: true` は非推奨化したため移行）。
- `frontend/vitest.setup.ts` — `@testing-library/jest-dom/vitest` matcher 登録 + 各テスト後 `cleanup()`。
- `frontend/index.html` / `src/main.tsx` / `src/index.css`（Tailwind v4 `@import "tailwindcss";` + `leaflet/dist/leaflet.css`）/ `src/App.tsx` / `src/vite-env.d.ts`（`vite/client` 参照で png / css モジュール型を解決）。
- `frontend/.husky/pre-commit` — `cd frontend && npx lint-staged`。

### ソース（Smart / Dumb 分割）
- **Dumb**: `src/components/MapView.tsx`（react-leaflet / OSM タイル + 自分ピン + 中心追従）/ `LocationErrorScreen.tsx`（全面エラー画面・文言＋再試行ボタン）/ `DegradedBanner.tsx`（控えめバナー）。
- **Smart**: `src/hooks/useGeoTracking.ts`（watchPosition・状態遷移・everTracked 分岐・retry・注入）+ `src/hooks/geoState.ts`（状態定数 / 型 / 文言）/ `src/containers/GeoTrackingContainer.tsx`（状態別の出し分け）。
- **テスト**: 各コンポーネント / フック / コンテナの `*.test.ts(x)` + `src/test/fakeGeolocation.ts`（注入用フェイク）。

---

## 設計判断

### スタック / 配置
- リポジトリ直下 `frontend/` の**独立 Vite プロジェクト**。Spring の `resources/static` には置かない（DESIGN.md / CLAUDE.md）。品質ゲートはフロント側 npm scripts（lint / typecheck / test / build）で回し、`./gradlew build` とは独立。
- 解決バージョン: Vite 8.1.1 / React 19.2 / react-leaflet 5.0 / Biome 2.5.1 / Vitest 4.1 / Tailwind 4.3。Node 24。

### Smart / Dumb（CLAUDE.md 準拠）
- 状態保持・副作用（`watchPosition` / `clearWatch` / 状態遷移）は **Smart の `useGeoTracking` に一元化**。`GeoTrackingContainer` がフックを使ってビューを出し分ける唯一の結線点。
- Dumb 3 部品は props を受け取って描画するだけで Geolocation も共有状態も知らない。Leaflet は描画専用（凸包・面積・補間など座標計算をしない。`MapView` は setView / panTo / marker のみ）。

### D4 / D5 分岐（旧 geo-tracker.js を 1:1 移植）
- 分岐軸は状態名ではなく **`everTracked`**（一度でも測位成功したか）。再レンダリングで揺れないよう **`useRef`** で保持（`watchId` も ref）。
  - `!everTracked && PERMISSION_DENIED` → **PERMISSION_ERROR**（D4）。`clearWatch` し、地図を出さずエラー画面を全面表示。
  - それ以外のエラー（TRACKING 経験後の TIMEOUT / POSITION_UNAVAILABLE / PERMISSION_DENIED、初回の非許可系失敗）→ **DEGRADED**（D5）。地図維持＋控えめバナーのみ。エラー画面に遷移しない。
- 成功時: `selfLocation` 更新・状態 TRACKING・errorMessage クリア（DEGRADED バナーは状態が TRACKING に戻ることで自然に消える）。
- watch オプション: `{ enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }`（旧実装と同値）。

### 非安全コンテキスト / Geolocation 未提供
- マウント時に `isSecureContext` / `geolocation` 有無を確認。非安全 or 未提供なら測位を**開始せず** PERMISSION_ERROR（非安全用文言）にする。
- 「初回拒否（再試行可）」と「非安全コンテキスト（再試行不可）」を区別するため、フックは戻り値に **`canRetry`** を持つ。コンテナは `canRetry ? retry : undefined` を `LocationErrorScreen.onRetry` に渡し、非安全時は再試行ボタンを出さない（旧 `showPermissionError` vs `showInsecureContextNotice` を移植）。
- 旧レビュー [low] 指摘に対応し、`retry()` 冒頭でも安全コンテキスト / geolocation を再確認する防御を入れた（将来の結線変更時の事故防止）。

### 注入可能化（テスト容易性）
- `useGeoTracking({ geolocation?, isSecureContext? })` で両者を注入可能にし、既定は `navigator.geolocation` / `window.isSecureContext`。`GeoTrackingContainer` も `deps?` で透過注入。これにより Vitest で `fakeGeolocation` を差し込み、状態遷移（D4/D5）・retry・安全コンテキスト分岐をブラウザ非依存で検証できる。

### react-leaflet 採用と既知の罠への対処
- 命令的 `L.map(...)` は使わず **react-leaflet（MapContainer / TileLayer / Marker / useMap）** で宣言的に統合。
- **マーカーアイコン欠落**: Vite バンドルで Leaflet 既定アイコンの画像パスが壊れる既知問題に対し、`leaflet/dist/images/marker-icon*.png` / `marker-shadow.png` を **import した URL で `L.icon(...)` を明示生成**して自分ピンに渡した（CDN 既定パス依存を排除＝旧レビュー [low] のアイコン引き継ぎ懸念を解消）。
- **中心追従**: `useMap` を使う内部部品 `SelfFollower` が、初回 `selfLocation` で `setView(latLng, SELF_ZOOM=16)`、以降は `panTo` で追従（`centeredOnce` は ref）。旧 map-view.js の挙動と同値。

### スコープ厳守
- gameId / playerId を参照しない。サーバー送信・API 呼び出し・ポーリング・友達ドット・面積メーター・ルーティング・補間・zod のいずれも持ち込んでいない。

---

## 旧 vanilla 資産の削除（移植チェックリスト）

React 側で以下が満たされていることをテストで確認後、`src/main/resources/static/` の 6 ファイル（index.html / styles.css / app.js / map-view.js / error-view.js / geo-tracker.js）を削除した。

- [x] 状態モデル（INITIALIZING / TRACKING / DEGRADED / PERMISSION_ERROR）
- [x] everTracked による D4/D5 分岐
- [x] watch オプション同値
- [x] retry（clearWatch → watchPosition 貼り直し → 許可済みなら TRACKING 復帰）
- [x] 非安全コンテキスト表示（再試行ボタンなし）
- [x] OSM タイル設定（url / attribution / maxZoom 19）
- [x] 初期中心（東京駅）/ ズーム 5 / SELF_ZOOM 16
- [x] 自分ピンのみ（友達ドットは非導入）

削除後の確認: `src/main/resources/static/` は空。Kotlin から当該ファイルへの参照は無く（grep 確認済み）、バックエンドビルドを壊さない。

---

## トレードオフ / 環境メモ
- 旧レビュー [med]（初回未測位前の TIMEOUT/POSITION_UNAVAILABLE も DEGRADED で「位置更新が滞っています」になる文言ズレ）は、旧実装との**同値性を優先**して挙動を変えず、文言の出し分けは将来 US（測位リトライ作り込み）の論点として据え置いた。エラー画面に飛ばさず地図維持＋控えめ表示にする方針自体は仕様 D5 と整合。
- `npm install` は成功（registry 到達 OK・約 190 パッケージ・脆弱性 0）。`prepare`（husky）が `frontend/` 単独実行時に「.git can't be found」を警告するが無害（git ルートはリポジトリ直下、フックは `frontend/.husky/pre-commit`）。
- 将来引き継ぎ: OSM 標準タイルは本番常用ポリシーに制約あり（プロバイダ差し替えは将来論点）。
