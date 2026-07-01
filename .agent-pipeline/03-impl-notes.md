# 03-impl-notes — US-04: ゲーム作成（種別・面積プリセット・人数を指定）

TDD（RED→GREEN→REFACTOR）で Domain → UseCase → Adapter → UI の順に実装した。
オーケストレーター確定の設計判断（objectType=SELECTABLE のみ / react-router 導入 / game・player 別ポート）に従う。

## 確定した設計判断とその実装

### 1. objectType 検証 = SELECTABLE のみ
- `ObjectType.selectableFromJsonValueOrNull(jsonValue)` を追加し、`SELECTABLE`（＝config が公開する選択肢）を単一ソースにした。
- enum に存在しても SELECTABLE 外（temple / school / …）は null → UseCase が `INVALID_OBJECT_TYPE` で 400。config と作成 API の許可範囲が必ず一致する（DRY・乖離防止）。

### 2. Domain ポート = game / player 別ポート
- `GameRepositoryPort`（createGame / updateCreatorPlayerId）と `PlayerRepositoryPort`（createPlayer）に分割。既存 JPA リポジトリ（GameRepository / PlayerRepository）と 1 対 1。
- ポートは Domain の値（UUID / String / GameStatus）だけを受け渡し、`GameJpaEntity` などの JPA 型を露出しない。UseCase は JPA を一切 import しない（DIP）。
- 実装（`GameRepositoryAdapter` / `PlayerRepositoryAdapter`）は `@Component`、`CreateGameUseCase` は `@Service` のコンストラクタ注入。専用 `@Configuration` は増やさず、CLAUDE.md の「@Configuration / コンストラクタ注入」方針の後者で解決（Bean 実装が 1 つで曖昧さがないため）。

### 3. 創成順序と displayName フォールバックの順序依存
- UseCase 内で gameId / playerId を先に発番 → displayName を解決 → game INSERT → player INSERT → creator UPDATE。
- displayName フォールバックは playerId 先頭 8 文字を使うため、**playerId 発番を player INSERT より前**に置いた（リスク 6）。null / 空 / 空白のみ（trim 後 empty）はすべてフォールバック（D6 案A、DB には常に非 null が入る）。
- 3 操作は `@Transactional` で 1 Tx。途中失敗で全ロールバック。

### 4. エラーマッピング
- Domain に `GameValidationException`（reason: INVALID_OBJECT_TYPE / INVALID_AREA_PRESET / INVALID_PLAYER_COUNT）。
- inbound の `GameApiExceptionHandler`（`@RestControllerAdvice`）で 400（`ProblemDetail`）にマッピング。US-04 が最初のエラーマッピング導入点。

### 5. CORS
- `WebCorsConfig`（`WebMvcConfigurer#addCorsMappings`）。許可オリジンは `app.cors.allowed-origins`（application.properties）から `List<String>` で注入しハードコードしない。ローカル既定は `http://localhost:5173`（Vite dev server）。Credentials なし・`/api/**` に適用。

### 6. フロント: ルーティング（react-router-dom v7）
- `main.tsx` で `BrowserRouter`、`App.tsx` で `Routes`。`/` = 作成画面、`/game/:gameId` = 待機画面。
- パス組み立ては `routing/paths.ts` に集約（単一ソース）。作成者 URL = `/game/<gameId>?p=<playerId>`（`buildGamePath`）、招待 URL = `<origin>/game/<gameId>`（`buildInviteUrl`、playerId を含めない）。

### 7. フロント: Smart / Dumb 分割
- Smart: `CreateGameContainer`（config 取得・createGame・作成後ナビゲート）、`WaitingRoomContainer`（URL から gameId/playerId 取得・招待 URL 組み立て・クリップボードコピー）。API を呼ぶのは Smart のみ。
- Dumb: `CreateGameForm`（props で選択肢/onSubmit を受け取り、入力欄の値のみ自身で管理）、`WaitingRoomView`（props 描画のみ）。
- 依存注入: API（`TopoApi`）・clipboard・origin をコンテナの props で注入可能にし、Vitest で差し替え（fakeGeolocation の DI パターン踏襲）。API ラッパ `createTopoApi` は fetch / baseUrl を注入可能にし、ベース URL は `import.meta.env.VITE_API_BASE_URL` で外部化。

## 迷った点・レビューで見てほしい点

- **CreateGameResponse を gameId/playerId のみに限定**: 招待 URL 等はサーバーから返さずクライアント組み立て（spec 1.1）。レスポンスに `inviteUrl` が漏れていないことをテストで担保（`jsonPath("$.inviteUrl") { doesNotExist() }`）。
- **待機画面の参加者一覧は静的**（作成者のみ・`あなた（<8桁>）` 表示）。ポーリングで実データ（サーバー確定の displayName）に置換するのは US-05。US-04 では過剰実装を避けた（リスク 7）。表示名を playerId 由来の仮表示にしている点は US-05 で要差し替え。
- **mockito-kotlin をテスト依存に追加**（6.1.0）: `@MockitoBean` のスタブに Kotlin 非 null 安全な `any()/whenever()` が必要。プロダクション依存には影響しない。ポート単体（Level 2）は外部モックライブラリを使わず手書き spy で検証しているため、モック依存は inbound（Level 3 `@WebMvcTest`）に限定。
- **GameRepositoryAdapter.updateCreatorPlayerId は findById→save**: 循環回避の後埋め設計に沿う。`@Transactional` 境界（UseCase）内なので 1 Tx で成立。対象 game 不在時は `IllegalStateException`。
- **US-02 の `GeoTrackingContainer` 一式は残置**（App から未参照だが削除しない）。地図画面は後続ストーリーでゲーム画面に組み込む想定。lint/typecheck は未使用ファイルとして問題視しない。

## スコープ厳守（含まない）
- 参加 API（US-05）/ ポーリング更新（US-05・US-08）/ 開始ボタン・WAITING→ACTIVE（US-06）/ ライブ位置・友達ドット・面積（US-07〜10）は未実装。playerCount の上限バリデーションも入れない（下限 3 のみ）。
