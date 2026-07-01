# 06-commit-ready

## 変更サマリ

US-02（クライアント雛形＝地図＋自分の現在地GPS表示）を **React 19 + Vite + TypeScript** で実装した。当初 vanilla JS（`src/main/resources/static/`）で実装していたが、レビュー方針転換により、リポジトリ直下 `frontend/` の独立 SPA（react-leaflet / Tailwind v4 / Biome / Vitest）へ全面的に作り直した。サーバー側（Kotlin/Spring/DB/Flyway）の変更はない。

### スタック確定（ドキュメント更新済み）

- フロントエンド = React 19 + Vite + TypeScript、地図 = Leaflet（**react-leaflet** で統合）、スタイル = Tailwind CSS v4、Lint/Format = Biome、テスト = Vitest + @testing-library/react（jsdom）、pre-commit = husky + lint-staged。
- 配置はリポジトリ直下 `frontend/`。**静的ホスティング前提で Spring の `resources/static` には同梱しない**（API サーバーとは別デプロイ）。品質ゲートはフロント側 npm scripts で回し、バックエンドの `./gradlew build` とは独立。
- 上記を `CLAUDE.md` / `docs/DESIGN.md` に反映済み。

### 実装（Smart/Dumb・D4/D5）

- **Smart**: `src/hooks/useGeoTracking.ts`（+ `geoState.ts`）— `watchPosition`・状態遷移（INITIALIZING / TRACKING / DEGRADED / PERMISSION_ERROR）を一元所有。`everTrackedRef`（useRef）で **D4**（初回拒否 → PERMISSION_ERROR・地図非表示・再試行可）と **D5**（追従中の一時失敗 → DEGRADED・地図維持・控えめバナー）を分離。非安全コンテキスト／geolocation 未提供は測位を開始せず `canRetry=false`。`geolocation`/`isSecureContext` を注入可能にしてテスト容易化。
- **Smart 結線**: `src/containers/GeoTrackingContainer.tsx` — 状態に応じて Dumb を出し分け（`onRetry={canRetry ? retry : undefined}`）。
- **Dumb**: `src/components/MapView.tsx`（react-leaflet で OSM 標準タイル＋自分ピン、`SelfFollower` で中心追従）/ `LocationErrorScreen.tsx`（許可拒否・非安全コンテキストのエラー画面＋再試行）/ `DegradedBanner.tsx`（控えめバナー）。Leaflet は描画専用・座標計算なし。
- URL（gameId/playerId）解釈・ルーティング・API・ポーリング・友達ドット・面積は本ストーリーに含めない（`react-router-dom` / `zod` 未導入を確認）。

## 品質ゲート（最終確認・全緑）

| 対象 | コマンド | 結果 |
| --- | --- | --- |
| フロント Lint | `frontend` `npm run lint`（Biome） | 成功・Checked 24 files・エラー 0 |
| フロント 型 | `frontend` `npm run typecheck`（tsc） | 成功・エラー 0 |
| フロント テスト | `frontend` `npm run test`（Vitest） | 成功・5 files / **27 tests passed** |
| フロント ビルド | `frontend` `npm run build`（Vite） | 成功・dist 生成 |
| バックエンド | `./gradlew build` | **BUILD SUCCESSFUL**（変更なし・回帰なし） |

レビュー（`.agent-pipeline/05-review.md`）判定 = **承認**（ブロッカーなし）。

## コミット対象ファイル

### 新規（`frontend/` 一式・27 ファイル）

- 設定: `frontend/{package.json, package-lock.json, tsconfig.json, tsconfig.app.json, tsconfig.node.json, vite.config.ts, vitest.setup.ts, biome.json, index.html, .gitignore, .husky/pre-commit}`
- ソース: `frontend/src/{main.tsx, App.tsx, index.css, vite-env.d.ts}`
- コンポーネント: `frontend/src/components/{MapView,LocationErrorScreen,DegradedBanner}.tsx`（+ 各 `.test.tsx`）
- コンテナ: `frontend/src/containers/GeoTrackingContainer.tsx`（+ `.test.tsx`）
- フック: `frontend/src/hooks/{useGeoTracking.ts, geoState.ts}`（+ `useGeoTracking.test.ts`）
- テスト補助: `frontend/src/test/fakeGeolocation.ts`

### 変更

- `CLAUDE.md` / `docs/DESIGN.md`（フロントスタックを React 等へ更新）
- `.gitignore`（`frontend/node_modules`・`frontend/dist` 等を追加）
- `.agent-pipeline/01-spec.md` 〜 `05-review.md`（US-02 React 版の記録）

### 削除

- `src/main/resources/static/{index.html, styles.css, app.js, map-view.js, error-view.js, geo-tracker.js}`（旧 vanilla 実装）。※これらは未コミットの作業ツリー上のファイルだったため `git status` の削除差分には現れない（新規追跡もされない）。

### コミット対象外

- `frontend/node_modules/`・`frontend/dist/`（`.gitignore` 済み）
- `.agent-pipeline/06-commit-ready.md` 自体（Finish 記録物）
- コミット直前に `git status` で意図しない混在がないか再確認すること

## コミットメッセージ案

```
feat: 地図と自分の現在地GPS表示のクライアント雛形をReactで追加(US-02)

本編ループの全操作の前提になる、地図と自分の現在地表示・追従を行う
最小のSPAを frontend/ に追加した。サーバー側の変更はない。

- スタックを React 19 + Vite + TypeScript に確定。地図は Leaflet を
  react-leaflet で統合、スタイルは Tailwind v4、Lint/Format は Biome、
  テストは Vitest。frontend/ を独立プロジェクトとして配置し、静的
  ホスティング前提で Spring の resources/static には同梱しない。
- Smart(useGeoTracking フック)に watchPosition・状態遷移・Geolocation
  副作用を集約。everTracked で「初回拒否(PERMISSION_ERROR・地図非表示)」
  と「追従中の一時失敗(DEGRADED・地図維持)」を分離(D4/D5)。非安全
  コンテキスト等は再試行不可(canRetry=false)。
- Dumb(MapView/LocationErrorScreen/DegradedBanner)は props 描画のみ。
  Leaflet は描画専用で座標計算しない。
- geolocation/isSecureContext を注入可能にし、fakeGeolocation で
  状態遷移(D4/D5・再試行・非安全コンテキスト)を Vitest で自動検証(27件)。
- 旧 vanilla 実装(src/main/resources/static/)は React 版へ移植のうえ削除。
- URL解釈/ルーティング/API/ポーリング/友達ドット/面積は本ストーリー
  では対象外(react-router-dom / zod 未導入)。

品質ゲート: frontend で lint/typecheck/test(27 passed)/build 緑、
バックエンド ./gradlew build も緑(変更なし)。
```

## 残課題（フォローアップ・ブロッカーでない）

- **[med] 初回未測位時の DEGRADED バナー文言**（`frontend/src/hooks/useGeoTracking.ts` 付近 / 05-review 指摘）: `everTracked=false` のまま TIMEOUT / POSITION_UNAVAILABLE が起きても「位置更新が滞っています」を表示する。仕様 D5 の方針（地図維持・控えめ表示）とは整合し承認は妨げないが、初回未測位時は「現在地を取得中です…」等の別文言に分けるとより実態に合う。将来の測位リトライ作り込み時に再考。
- **[low] 非安全コンテキストと geolocation 未提供が同一文言**（`frontend/src/hooks/geoState.ts` / 05-review 指摘）: 両者を同じメッセージで扱う。原因別に文言を分けると案内が親切。
- **フロントの品質ゲートは Gradle と分離**: 現状 `./gradlew build` はフロントを検査しない。将来 CI では `frontend` の lint/typecheck/test/build を別ジョブで回す運用を想定。
- **OSM 標準タイルの本番常用ポリシー**: MVP では許容。本番デプロイ時にタイルプロバイダ差し替えの要否を検討（spec §4 記載の既知事項）。
