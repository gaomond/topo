# 04-test-report — US-02: クライアント雛形（地図＋自分の現在地GPS表示）React 19 版

> 対象: `frontend/`（React 19 + Vite + TS）。品質ゲートは **frontend の npm scripts**（lint / typecheck / test / build）。
> バックエンド（Kotlin / Spring / DB / Flyway）の変更は無い（`./gradlew build` は別フックが担保）。

---

## 1. 品質ゲート実行結果（4 ゲートすべて緑）

`cd frontend` で実行。

| ゲート | コマンド | 結果 |
| --- | --- | --- |
| Lint | `npm run lint`（`biome check .`） | **成功**。Checked 23 files. エラー 0 / 警告 0。 |
| 型チェック | `npm run typecheck`（`tsc -b --noEmit`） | **成功**。エラー 0。 |
| テスト | `npm run test`（`vitest run --passWithNoTests`） | **成功**。Test Files 5 passed (5) / Tests **27 passed (27)**。 |
| ビルド | `npm run build`（`tsc -b && vite build`） | **成功**。44 modules / dist 生成（index.html 0.38kB・css 21.85kB・js 353.28kB）。 |

> `npm install` も成功（registry 到達 OK・約 190 パッケージ・脆弱性 0）。

---

## 2. 自動テスト一覧（Vitest / 27 件）

Geolocation は `src/test/fakeGeolocation.ts`（注入用フェイク）でモックし、ブラウザ非依存で状態遷移を検証する。

### `src/hooks/useGeoTracking.test.ts`（フックの状態遷移・15 件）
- `test_mount_secureContext_startsWatchAndInitializing` — 安全コンテキストで watchPosition 開始・INITIALIZING。
- `test_onSuccess_firstFix_tracksAndSetsSelfLocation` — 初回成功で TRACKING・selfLocation 設定。
- `test_onSuccess_secondFix_followsToNewLocation` — 2 回目成功で追従（座標更新）。
- `test_onError_firstPermissionDenied_goesToPermissionErrorAndClearsWatch` — **D4**: 初回拒否で PERMISSION_ERROR・clearWatch・地図を出さない（selfLocation null）・canRetry=true。
- `test_onError_timeoutAfterTracking_goesToDegradedAndKeepsMap` — **D5**: 追従後 TIMEOUT で DEGRADED・地図維持（最後の位置保持）。
- `test_onError_positionUnavailableAfterTracking_goesToDegraded` — **D5**: 追従後 POSITION_UNAVAILABLE で DEGRADED。
- `test_onError_permissionDeniedAfterTracking_goesToDegradedNotPermissionError` — **D5**: 追従後の許可取り消しは DEGRADED（エラー画面に飛ばさない）。
- `test_onSuccess_afterDegraded_recoversToTracking` — DEGRADED から成功で TRACKING 復帰。
- `test_retry_afterPermissionError_clearsAndReattachesWatch` — retry で watchPosition 貼り直し・INITIALIZING。
- `test_retry_thenSuccess_recoversToTracking` — retry 後の成功で TRACKING 復帰。
- `test_mount_insecureContext_goesToPermissionErrorWithoutRetryAndDoesNotWatch` — 非安全コンテキストで PERMISSION_ERROR（非安全文言）・canRetry=false・watchPosition 未呼び出し。
- `test_mount_noGeolocation_goesToPermissionErrorInsecureMessage` — geolocation 未提供で PERMISSION_ERROR・canRetry=false。
- `test_unmount_clearsWatch` — アンマウントで clearWatch。
- `test_degradedMessage_isQuietNoticeWording` — DEGRADED 文言の同値確認。

### `src/containers/GeoTrackingContainer.test.tsx`（出し分け・5 件）
- `test_initial_secureContext_showsMapWithoutErrorOrBanner` — 初期は地図のみ（エラー画面・バナーなし）。
- `test_firstPermissionDenied_showsErrorScreenAndHidesMap` — 拒否でエラー画面表示・**地図を出さない**・再試行ボタンあり。
- `test_retry_afterDenied_thenSuccess_recoversToMap` — 再試行ボタン押下 → 成功で地図に復帰・エラー画面消滅。
- `test_timeoutAfterTracking_keepsMapAndShowsDegradedBanner` — 追従後 TIMEOUT で地図維持＋控えめバナー・エラー画面に飛ばない。
- `test_insecureContext_showsErrorScreenWithoutRetry` — 非安全で再試行ボタンなし・watchPosition 未呼び出し。

### `src/components/LocationErrorScreen.test.tsx`（Dumb・4 件）
- alertdialog で文言表示 / onRetry あり時にボタン表示・クリックでコールバック発火 / onRetry なし時にボタン非表示。

### `src/components/DegradedBanner.test.tsx`（Dumb・2 件）
- visible=true で控えめ表示（role=status・top-0・全面オーバーレイでない＝地図を覆わない）/ visible=false で何も描画しない。

### `src/components/MapView.test.tsx`（Dumb・2 件）
- selfLocation=null で地図描画・自分ピンなし / selfLocation 設定で自分ピン（marker img）描画。

---

## 3. 受け入れ条件（spec §2）との対応

| 受け入れ条件 | 区分 | 検証 |
| --- | --- | --- |
| OSM 標準タイル地図が描画される | 手動 | §4 手順1（タイル実ロード） |
| 許可で現在地にピン・中心が現在地 | 手動 + 自動 | §4 手順2（実 GPS）/ MapView ピン有無テスト・SelfFollower の setView |
| 移動でピン追従 | 手動 | §4 手順3（DevTools Sensors） |
| 拒否でエラー画面・地図を出さない | 自動 | `test_firstPermissionDenied_showsErrorScreenAndHidesMap` ほか |
| 再試行ボタンで watchPosition 再実行・許可済みで復帰 | 自動 | `test_retry_*` / `test_retry_afterDenied_thenSuccess_recoversToMap` |
| 追従中の一時失敗は控えめ表示・エラー画面に飛ばない | 自動 | `test_timeoutAfterTracking_keepsMapAndShowsDegradedBanner` ほか D5 群 |
| 非安全コンテキストで動作前提不成立を明示 | 自動 + 手動 | `test_insecureContext_*` / §4 手順5（平文 http） |
| gameId / playerId を要求せず単体表示 | 自動 | router 非依存・URL 解釈コードなし（全ソース grep / コンパイル） |

---

## 4. 手動ブラウザ統合検証手順（自動化できない範囲）

タイル実ロード・OS の許可ダイアログ・実 GPS・モバイル実機はブラウザ/OS 依存のため手動。

1. **地図描画 / 操作**: `cd frontend && npm run dev` → `http://localhost:5173`（localhost = 安全コンテキスト）を開く。OSM タイルがロードされ、ドラッグでパン・ホイールでズームできること。右下に OpenStreetMap の attribution が出ること。
2. **許可 → ピン**: 許可ダイアログで「許可」。現在地に自分ピンが立ち、地図中心が現在地（zoom 16）へ寄ること。
3. **追従**: DevTools → Sensors（Chrome）で Location を別座標に変更。ピンが追従して動き、中心が panTo されること。
4. **拒否 → エラー画面 → 再試行**: ブラウザの位置情報を「ブロック」に変えて再読込。エラー画面（文言＋再試行ボタン）が出て地図が消えること。許可に戻して「再試行」を押すと地図表示に復帰すること。
5. **非安全コンテキスト**: localhost 以外の平文 http（例: LAN IP `http://192.168.x.x:5173`）で開く。`watchPosition` が動かず「localhost または https で開いてください」の表示（再試行ボタンなし）になり、地図は描画されたままであること。
6. **一時失敗**: 一度測位成功後、Sensors を「Location unavailable」に切り替え。地図が維持され「位置更新が滞っています」の控えめバナーが上部に出ること（エラー画面に飛ばないこと）。
7. **モバイル差**: iOS Safari / Android Chrome で許可ダイアログとピン表示が破綻しないこと（精度・補間は対象外）。

---

## 5. 旧 vanilla static 削除の確認

- `src/main/resources/static/` の 6 ファイル（index.html / styles.css / app.js / map-view.js / error-view.js / geo-tracker.js）を削除済み。ディレクトリは空。
- Kotlin から当該ファイルへの参照は無い（grep 確認済み）。バックエンドビルドへの影響なし。
