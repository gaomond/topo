# 05-review — US-02: クライアント雛形（地図＋自分の現在地GPS表示）React 19 版

> 対象: `frontend/`（React 19 + Vite + TypeScript / react-leaflet / Tailwind v4 / Biome / Vitest）。
> 旧 vanilla 実装（`src/main/resources/static/` 6 ファイル）の挙動を React へ移植し、旧資産は削除済み（確認済み: ディレクトリ空）。
> 機械判定（lint / typecheck / test 27 件 / build / 旧 static 削除）はオーケストレーター側で緑を再検証済みのため、本レビューは人間的判断（仕様充足・アーキテクチャ・命名・テストの質）に集中する。

## 判定: 承認

ブロッカー級の指摘なし。受け入れ条件 §2 は全項目を満たし、D4/D5 分岐・非安全コンテキスト再試行不可・Smart/Dumb 境界・スコープ厳守のいずれも適切。以下は将来改善（med/low）。

---

## 受け入れ条件 §2 の充足（コード位置付き）

| # | 受け入れ条件 | 充足 | 根拠 |
|---|---|---|---|
| 1 | OSM標準タイル地図が描画される | ○ | `MapView.tsx:26,70`（`OSM_TILE_URL` + `TileLayer` attribution / maxZoom 19）。実タイルロードは手動（04 §4-1） |
| 2 | 許可で現在地にピン・中心が現在地 | ○ | `MapView.tsx:71-74`（selfLocation 非 null でピン）+ `SelfFollower`（`setView` で SELF_ZOOM=16 中心寄せ）`MapView.tsx:42-60`。自動: `MapView.test.tsx` ピン有無 |
| 3 | 移動でピン追従 | ○ | `useGeoTracking.ts:76-82`（onSuccess で selfLocation 更新）+ `SelfFollower` 2 回目以降 `panTo`。自動: `useGeoTracking.test.ts:45-55`（2nd fix）。実機追従は手動（04 §4-3） |
| 4 | 拒否でエラー画面・地図を出さない（フォールバックしない） | ○ | `useGeoTracking.ts:88-94`（!everTracked && PERMISSION_DENIED → PERMISSION_ERROR + clearWatch）+ `GeoTrackingContainer.tsx:22-30`（PERMISSION_ERROR で MapView を描画しない）。自動: `GeoTrackingContainer.test.tsx:30-39` で `.leaflet-container` が null |
| 5 | 再試行ボタンで watchPosition 再実行・許可済みで復帰 | ○ | `useGeoTracking.ts:126-136`（retry → INITIALIZING + startWatch 貼り直し）。自動: `useGeoTracking.test.ts:124-153` / `GeoTrackingContainer.test.tsx:41-56`（クリック→success→地図復帰） |
| 6 | 追従中の一時失敗は控えめ表示・エラー画面に飛ばない | ○ | `useGeoTracking.ts:97-99`（everTracked 後の error は DEGRADED）+ `DegradedBanner`（地図上部オーバーレイのみ）。自動: TIMEOUT/POSITION_UNAVAILABLE/追従後 PERMISSION_DENIED の D5 群（`useGeoTracking.test.ts:72-106` / container 58-70） |
| 7 | 非安全コンテキストで動作前提不成立を明示 | ○ | `useGeoTracking.ts:113-120`（!secureContext or geolocation 未提供 → PERMISSION_ERROR + INSECURE_CONTEXT_MESSAGE、watch 開始せず）。自動: `useGeoTracking.test.ts:155-171` / container 72-78。平文 http 目視は手動（04 §4-5） |
| 8 | gameId / playerId を要求せず単体表示 | ○ | 全ソース grep で URL/router/gameId/playerId への参照なし（コメントのみ）。`App.tsx` は `GeoTrackingContainer` を 1 枚出すのみ・ルーティングなし |

---

## アーキテクチャ・設計評価

### D4 / D5 分岐（`everTracked` 軸）— 正しい
- 分岐軸が状態名ではなく `everTrackedRef`（useRef）で、再レンダリングで揺れない（`useGeoTracking.ts:66,78,88`）。旧 vanilla のローカル変数と等価。
- D4: `!everTracked && PERMISSION_DENIED` のみ PERMISSION_ERROR。`clearCurrentWatch()` で watch 解除（`:89-93`）。地図非表示はコンテナで担保。
- D5: それ以外（追従後の TIMEOUT / POSITION_UNAVAILABLE / 追従後の PERMISSION_DENIED、および初回の非許可系失敗）は DEGRADED で地図維持（`:97-99`）。エラー画面に遷移しない構造が**コンポーネント分離**（DegradedBanner ≠ LocationErrorScreen）で担保されている。
- DEGRADED → success で TRACKING 復帰時、errorMessage クリア・状態遷移でバナーが自然に消える（`:80-81`）。テストでカバー済み（`:108-122`）。

### 非安全コンテキスト由来の再試行不可 — 正しい
- フック戻り値に `canRetry` を持ち、非安全/未提供では false（`:117,131`）、初回拒否では true（`:92`）。
- コンテナで `onRetry={canRetry ? retry : undefined}` とし、`LocationErrorScreen` は `onRetry` 未指定で再試行ボタンを描画しない（`GeoTrackingContainer.tsx:27` / `LocationErrorScreen.tsx:26`）。旧 `showPermissionError` vs `showInsecureContextNotice` の出し分けを正しく移植。
- `retry()` 冒頭でも安全コンテキスト再確認の防御あり（`:127-132`）。

### Smart / Dumb 境界 — 準拠
- Smart: `useGeoTracking`（唯一の watchPosition / clearWatch / 状態保持）+ `GeoTrackingContainer`（出し分けの唯一の結線点）。
- Dumb: `MapView` / `LocationErrorScreen` / `DegradedBanner` は props のみで描画。Geolocation も共有状態も参照しない。`LocationErrorScreen` 入力欄なし・閉じた UI 状態も持たず純粋。
- Leaflet は描画専用。`MapView` は setView / panTo / Marker のみで凸包・面積・補間など座標計算をしていない（CLAUDE.md「クライアントで計算しない」準拠）。命令的 `L.map(...)` を使わず react-leaflet で宣言的統合。

### 命名（ユビキタス言語）— 準拠
- 自分ピン = self（`selfIcon` / `SelfFollower` / `selfLocation`）。友達ドット（live marker）は本ストーリー非対象として導入していない。
- 座標 = `Coordinate { lat, lng }`（WGS84）で DESIGN.md に整合（`geoState.ts:18-22`）。
- 操作名は意図表現（`retry` / `confirm` 系は本ストーリー対象外）。`handleClick` 的命名なし。

### TypeScript の厳格さ — 良好
- `any` の使用ゼロ（grep 確認）。注入 deps は `Geolocation` / `boolean` で型付け。`GeolocationPositionError.code` を `error.PERMISSION_DENIED` 定数比較しており、マジックナンバー回避（`:86`）。
- `tsconfig.app.json` strict 前提、`tsc -b` project references 構成。

### スコープ厳守 — 準拠
- `package.json` に react-router-dom / zod / シグナリング系（express/ws 等）が**入っていない**（確認済み）。`@testing-library/user-event` のみ計画外追加だがテスト基盤であり正当。
- ソースに fetch / axios / setInterval / WebSocket / ポーリング / URL 解釈なし（grep 確認）。

### テストの質 — 良好（自作自演でない）
- 全テストが `src/` から実プロダクションを import（`useGeoTracking` / 各コンポーネント / `geoState` の定数）。プロダクションロジックの再定義なし。
- D4/D5・retry・非安全コンテキスト・control の出し分けを**フェイク Geolocation 注入**で実検証。`fakeGeolocation.ts` は `watchPosition`/`clearWatch` を vi.fn 化し呼び出し回数・activeWatchCount まで検証可能。
- テスト名は `test_Action_Condition_Result` 形式に準拠。

---

## 指摘

### ブロッカー級（要修正）
- なし。

### 将来改善（med）
- [med] `useGeoTracking.ts:97-99` 初回未測位（everTracked=false）での TIMEOUT / POSITION_UNAVAILABLE も DEGRADED となり「位置更新が滞っています」を表示する。まだ一度も測位していない段階では文言がやや不正確（「更新が滞る」前提が成立していない）。旧実装との同値性を優先した据え置きであり仕様 D5（エラー画面に飛ばさず地図維持）とは整合。測位リトライ作り込みの後続 US で文言出し分けを検討。

### 将来改善（low）
- [low] `geoState.ts:29` 非安全コンテキストと `geolocation` 未提供（API 非対応ブラウザ）が同一の INSECURE_CONTEXT_MESSAGE を共有する。「localhost / https で開いてください」は API 非対応端末には正確でない。両ケースの分離は将来論点（実害は小さい）。
- [low] `MapView.tsx:15-23` Leaflet 既定アイコンを import URL で `L.icon` 明示生成しており Vite バンドルでのアイコン欠落を回避できている。OSM 標準タイルの本番常用ポリシー（プロバイダ差し替え）は spec スコープ外の将来論点として `MapView.tsx:25` にコメント済み。引き継ぎ漏れなし。
- [low] `MapView.test.tsx` はタイル実ロード・パン/ズームを検証しない（jsdom 制約のため手動 04 §4 に委譲）。自動境界として妥当だが、将来 E2E（Playwright 等）導入時にここを補完する余地あり。

---

## 良かった点
- D4/D5 を「状態名」ではなく `everTracked`（ref）軸で分岐し、再レンダリング非依存にした移植が正確。エラー画面 / 控えめバナーをコンポーネント分離することで「DEGRADED でエラー画面に飛ばない」を構造的に担保している。
- 非安全コンテキスト由来の再試行不可を `canRetry` フラグでフック戻り値に表現し、Dumb 側は `onRetry` 有無だけで描画判断する責務分離がきれい。
- Geolocation / isSecureContext を注入可能にしたことで、ブラウザ非依存に状態遷移ロジックを 27 件で網羅検証できている。テストは実ソース import で劣化コピーになっていない。
- スコープ管理が明確。除外依存（router / zod / シグナリング系）を package.json から確実に排除し、コメントでも US-04/05/07 への引き継ぎ境界を明示。

## 判定（再掲）: 承認
