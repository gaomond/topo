# 02-plan: US-04 ゲーム作成（種別・面積プリセット・人数を指定）

前提: 本計画は `.agent-pipeline/01-spec.md`（US-04）を実装手順に分解したもの。ソースコードは書かない。
既存資産の調査結果（後段「既存資産の整合」参照）を踏まえ、Clean Architecture の依存順（Domain → UseCase → Adapter → UI）で実装単位を並べる。

## 既存資産の整合（調査サマリ）

- **DB スキーマは US-00 で完成済み**（`V2__game_player.sql`）。`game` / `player` / `game_status` enum / 循環 FK / `idx_player_game_id` まで揃っている。**本ストーリーで新規マイグレーションは不要**。`display_name` は DB 上 NULL 許容だが、US-04 の D6 案A（サーバー側フォールバックで常に値を入れる）方針なので、DB 制約変更はせずアプリ層で担保する。
- **JPA エンティティ・リポジトリは実装済み**。`GameJpaEntity`（status/playerCount/objectType/areaThreshold/creatorPlayerId/createdAt/結果カラム）、`PlayerJpaEntity`（gameId/displayName/joinedAt/live系/confirmedAt）、`GameRepository` / `PlayerRepository`（いずれも `JpaRepository`）。**空間カラムは非マッピング**。US-04 の創成順序（game→player→creator UPDATE）はこれらの CRUD だけで完結し、生 SQL は不要。
- **Domain 定数は US-01/03 で実装済み**: `ObjectType`（`jsonValue` と `SELECTABLE=[SHRINE]`）、`AreaPreset`（`key/label/sqm`、`ALL` に small/medium/large）、`GameStatus`（WAITING/ACTIVE/COMPLETED）。**objectType 検証・areaPreset→sqm 解決はこれらを再利用**する。
- **config API（US-03）は実装済み**: `ConfigController`（`GET /api/config`）+ `ConfigResponse` / `AreaPresetPayload`。フロントの選択肢はここから構築する。Web 表現命名は `XxxResponse` / `XxxPayload` が既に確立。
- **UseCase 層・Domain ポート（リポジトリ抽象）は未導入**。実コードには存在しない。**US-04 が最初の UseCase 導入点**になる。CLAUDE.md の DIP 方針に従い、Domain にポート（インターフェース）を置き、既存 JPA リポジトリを実装（アダプタ）としてポートに適合させる。
- **CORS 設定は未実装**（`WebMvcConfigurer` / `@CrossOrigin` なし）。US-04 で導入する。
- **フロントは単体ページ**（`App.tsx` が `GeoTrackingContainer` を 1 枚出すだけ）。**ルーティング・API 呼び出し基盤（fetch ラッパ / `import.meta.env` 参照）は未導入**。react-router 等のルータライブラリも未依存。US-04 で導入する。Smart/Dumb 分割、`fakeGeolocation` 相当の依存注入テストパターンは既存にあるので踏襲する。

---

## 実装単位（依存順）

### 1. GameCreationCommand（作成入力の Domain 値） — 層: domain

- 目的: 作成 API のドメイン入力を表す不変値。Web 表現（`XxxRequest`）とは別に、UseCase が受け取るドメイン入力型を定義する（DDD 方針: Web と Domain を別型に分離）。
- 対象ファイル: `src/main/kotlin/com/github/gaomond/topo/domain/model/`（例: `GameCreationCommand.kt`。命名はユビキタス言語に沿わせる）
- 内容: `objectType`（生文字列）/ `areaPresetKey`（生文字列）/ `playerCount`（Int）/ `displayName`（nullable）を保持する値。Spring/JPA に非依存。
- 完了条件: Domain 層の純粋 Kotlin 型として定義され、外部フレームワーク import を持たない。
- 検証方法: 型定義のみ。次単位のバリデーション/UseCase テストで間接検証。

### 2. ドメイン例外（バリデーション失敗） — 層: domain

- 目的: `objectType` 不正 / `areaPreset` 不正 / `playerCount < 3` を表すドメイン例外を定義する。CLAUDE.md「Domain でドメイン例外を定義し、inbound で HTTP ステータスにマッピング」に従う。
- 対象ファイル: `src/main/kotlin/com/github/gaomond/topo/domain/`（例: `GameValidationException.kt`。既存の共通ドメイン例外があれば流用、無ければ新規）
- 内容: バリデーション失敗（→ 400 にマップ）を表現する例外型。理由が識別できるメッセージ/種別を持つ。
- 完了条件: Domain 層に例外型が定義される。inbound での 400 マッピングに使える粒度。
- 検証方法: 単体テストは不要（UseCase / Controller テストで発火を確認）。

### 3. 面積プリセット解決・種別検証のドメインロジック — 層: domain

- 目的: `areaPresetKey → sqm`（`areaThreshold`）解決と `objectType` 文字列の妥当性判定を、既存 `AreaPreset.ALL` / `ObjectType` を用いて行う。DRY のため config と同じ定数を単一ソースにする。
- 対象ファイル: 既存 `AreaPreset.kt` / `ObjectType.kt` にルックアップ関数を追加（例: `AreaPreset.byKey(key): AreaPreset?`、`ObjectType.fromJsonValueOrNull(value)`）。純粋 Kotlin で追記。
- 完了条件: key 未一致で null（→ UseCase が例外化）、一致で対応する `AreaPreset` / `ObjectType` を返す。objectType 検証の基準範囲は「リスク 1」で確定する。
- 検証方法: Level 1 Domain テスト。`test_byKey_withUnknownKey_returnsNull` / `test_byKey_withMedium_returns2000000` / objectType 解決の正常・異常。既存 `AreaPresetTest` / `ObjectTypeTest` に追記。

### 4. リポジトリポート（Domain 抽象） — 層: domain

- 目的: UseCase が依存する永続化ポートを Domain に定義する（DIP）。UseCase から JPA 具象を隠す。
- 対象ファイル: `src/main/kotlin/com/github/gaomond/topo/domain/port/`（例: `GameRepositoryPort.kt` / `PlayerRepositoryPort.kt`、または創成をまとめた単一ポート）
- 内容: 「game を作成」「player を作成」「game.creatorPlayerId を更新」を表現するポート操作。Domain のモデル型（または最小限の値）を引数/戻り値にし、JPA エンティティを露出しない。
- 完了条件: Domain 層にインターフェースが定義され、Spring/JPA 非依存。
- 検証方法: 型定義のみ。UseCase テストでモックして検証。

### 5. CreateGameUseCase（創成順序・バリデーション・フォールバック） — 層: usecase

- 目的: 01-spec 1.1 のサーバー処理（1トランザクション）を実装する。バリデーション → game INSERT（WAITING/結果 NULL/creator NULL）→ creator player INSERT（game_id・displayName 確定）→ game.creatorPlayerId UPDATE。
- 対象ファイル: `src/main/kotlin/com/github/gaomond/topo/usecase/`（例: `CreateGameUseCase.kt`）
- 内容:
  - 入力バリデーション: `objectType` を単位3で解決（失敗→単位2の例外）、`areaPresetKey` を sqm 解決（失敗→例外）、`playerCount >= 3`（違反→例外）。
  - `gameId` / `playerId`（UUID）はサーバー側で発番（DESIGN: サーバー発行）。playerId は player INSERT より前に確定（フォールバックに使うため。リスク 6）。
  - displayName フォールバック: NULL/空/空白のみ/未送信なら `playerId` の先頭 8 文字を採用。値は player INSERT 時に確定（D6 案A）。
  - トランザクション境界: 3 操作を 1 Tx にまとめる（`@Transactional`）。途中失敗で全ロールバック。
  - ポート（単位4）経由で永続化。JPA 具象は import しない。
  - 戻り値: `gameId` / `playerId`（ドメイン結果値）。
- 完了条件: 上記処理が UseCase 内で完結し、Adapter 実装に非依存。
- 検証方法: Level 2 UseCase テスト（ポートをモック）。
  - `test_createGame_withValidInput_persistsGameThenCreatorThenUpdatesCreatorId`（呼び出し順序を検証）
  - `test_createGame_withInvalidObjectType_throwsValidationException`
  - `test_createGame_withInvalidAreaPreset_throwsValidationException`
  - `test_createGame_withPlayerCountBelow3_throwsValidationException`（2 / 0 / -1）
  - `test_createGame_withBlankDisplayName_fallsBackToUuidPrefix8`
  - `test_createGame_withDisplayName_persistsAsIs`
  - `test_createGame_eachPreset_resolvesToExpectedSqm`（small/medium/large）

### 6. リポジトリポートのアダプタ実装（outbound） — 層: adapter

- 目的: 単位4のポートを既存 JPA リポジトリ（`GameRepository` / `PlayerRepository`）で実装する。ドメイン表現 ↔ `GameJpaEntity` / `PlayerJpaEntity` の変換をここに閉じ込める。
- 対象ファイル: `src/main/kotlin/com/github/gaomond/topo/adapter/persistence/`（例: `GameRepositoryAdapter.kt` 等。既存 `*Repository` インターフェースはそのまま利用）
- 内容: ポート操作を `save`（game）/ `save`（player）/ game 取得＋`creatorPlayerId` 設定＋`save`（UPDATE）にマッピング。`status=WAITING`、結果カラム NULL、`objectType` は解決済み enum の `jsonValue`、`areaThreshold` は解決済み sqm を Double で格納。
- 完了条件: ポートを満たす具象が Spring Bean として登録可能な形。生 SQL は使わない（本ストーリーは非空間 CRUD のみ）。
- 検証方法: Level 3 `@DataJpaTest` + Testcontainers（実 PostGIS）。既存 `GamePlayerSchemaTest` / `PostgisTestContainer` のパターンを踏襲。
  - `test_createGame_viaAdapter_insertsGamePlayerAndLinksCreatorId`（3 テーブル操作の整合を DB で確認）
  - `test_createGame_rollsBack_whenCreatorUpdateFails`（途中失敗で game だけ残らないこと）

### 7. DI 構成（ポート ↔ 具象の注入） — 層: adapter

- 目的: UseCase が依存するポートに単位6の具象を Spring DI で注入する（CLAUDE.md「具象注入は `@Configuration` / コンストラクタ注入」）。
- 対象ファイル: `src/main/kotlin/com/github/gaomond/topo/`（例: `config/BeanConfig.kt`、または各 UseCase/Adapter を `@Component` 化しコンストラクタ注入）
- 完了条件: アプリ起動時に `CreateGameUseCase` がポート実装込みで解決される。
- 検証方法: 単位9のコントローラテスト・アプリ起動テスト（既存 `TopoApplicationTests`）で間接確認。

### 8. Web 表現（Request / Response）— 層: adapter(inbound)

- 目的: 01-spec 1.1 のリクエスト/レスポンス JSON を Web 表現型として定義する。命名は CLAUDE.md 規約（body 全体 `XxxResponse`、構成要素 `XxxPayload`）に従い、リクエストは対称に `XxxRequest`。
- 対象ファイル: `src/main/kotlin/com/github/gaomond/topo/adapter/web/`（例: `CreateGameRequest.kt` / `CreateGameResponse.kt`）
- 内容:
  - `CreateGameRequest`: `objectType`（String）/ `areaPreset`（String）/ `playerCount`（Int）/ `displayName`（String?、任意）。
  - `CreateGameResponse`: `gameId`（String/UUID）/ `playerId`（String/UUID）。
- 完了条件: JSON キーが仕様と一致（リクエストは `areaPreset`、レスポンスは `gameId`/`playerId` のみ）。
- 検証方法: 単位9のコントローラテストでシリアライズ/デシリアライズを検証。

### 9. CreateGameController（inbound）＋ 例外→HTTP マッピング — 層: adapter(inbound)

- 目的: `POST /api/games` を提供し、`CreateGameRequest` → ドメイン入力（単位1）変換 → UseCase 呼び出し → 201 + `CreateGameResponse`。ドメイン例外（単位2）を 400 にマッピングする。
- 対象ファイル:
  - `src/main/kotlin/com/github/gaomond/topo/adapter/web/CreateGameController.kt`
  - 例外ハンドラ: `@ExceptionHandler` または `@RestControllerAdvice`（`ConfigController` は UseCase を経由しない設計のため、本 API が最初のエラーマッピング導入点）
- 完了条件: バリデーション失敗で 400、正常で 201。レスポンスは `gameId`/`playerId` のみ。招待 URL 組み立てはしない（クライアント責務）。
- 検証方法: Level 3 `@WebMvcTest`（UseCase をモック）。既存 `ConfigControllerTest` のパターンを踏襲。
  - `test_postGames_withValidBody_returns201AndGamePlayerIds`
  - `test_postGames_withInvalidObjectType_returns400`
  - `test_postGames_withInvalidAreaPreset_returns400`
  - `test_postGames_withPlayerCount2_returns400`（0 / -1 / 未送信も）
  - `test_postGames_withoutDisplayName_returns201`（フォールバックは UseCase 責務なのでここは 201 のみ確認）

### 10. CORS 許可設定 — 層: adapter(inbound)

- 目的: フロント（静的ホスト・別オリジン）から API を呼べるようにする。認証なしのため Credentials なし・オリジン許可のみ（01-spec 1.3 / DESIGN 4）。
- 対象ファイル: `src/main/kotlin/com/github/gaomond/topo/config/`（例: `WebConfig.kt` implements `WebMvcConfigurer#addCorsMappings`）。許可オリジンは `application.properties` の設定値（例: `app.cors.allowed-origins`）から注入し、ハードコードしない。
- 完了条件: 許可オリジンからのプリフライト（OPTIONS）が通り、本リクエストが成功。未許可オリジンは拒否。`GET /api/config` にも同一設定が効く。
- 検証方法: Level 3 テスト。
  - `test_optionsGames_fromAllowedOrigin_returns200WithCorsHeaders`
  - `test_postGames_fromDisallowedOrigin_isRejected`

### 11. フロント: API クライアント（config 取得・game 作成） — 層: ui（Smart 側の下請け）

- 目的: `GET /api/config` と `POST /api/games` を呼ぶ型付き fetch ラッパを用意する。API を呼べるのは Smart のみ（CLAUDE.md Smart/Dumb）だが、通信の実体はここに集約しテスト時に注入可能にする（既存 `fakeGeolocation` の依存注入パターンに倣う）。
- 対象ファイル: `frontend/src/api/`（例: `topoApi.ts` / 型定義 `types.ts`）。API ベース URL は `import.meta.env`（例: `VITE_API_BASE_URL`）で外部化（別オリジン前提）。`vite-env.d.ts` に env 型を追加。
- 内容: `fetchConfig(): Promise<ConfigResponse>` / `createGame(req): Promise<CreateGameResponse>`。レスポンス型は API 仕様と一致（`gameId`/`playerId`）。
- 完了条件: 型付きで呼べ、注入で差し替え可能。
- 検証方法: Vitest。fetch をモック/注入。
  - `test_createGame_onSuccess_returnsGameIdAndPlayerId`
  - `test_fetchConfig_returnsObjectTypesAndAreaPresets`
  - `test_createGame_onErrorStatus_throws`

### 12. フロント: ルーティング導入 — 層: ui

- 目的: 01-spec のルーティング（`/` = 作成画面、`/game/<gameId>?p=<playerId>` = 待機画面）を導入。US-02 は単体ページだったため本ストーリーで初導入。
- 対象ファイル: `frontend/src/App.tsx`（ルート定義）、`frontend/src/main.tsx`（必要なら Router プロバイダ）
- 内容: ルータライブラリ選定は要相談（リスク 4）。`/game/:gameId` パスと `?p=` クエリを解釈できること。作成後 `pushState`/ナビゲーションで遷移。
- 完了条件: `/` で作成画面、`/game/<id>?p=<pid>` で待機画面が描画される。
- 検証方法: Vitest + @testing-library/react。
  - `test_route_root_rendersCreateScreen`
  - `test_route_gamePath_rendersWaitingScreen`
  - `test_afterCreate_urlBecomesGamePathWithPlayerQuery`（URL 遷移。受け入れ条件）

### 13. フロント: 作成画面（Smart コンテナ + Dumb フォーム） — 層: ui

- 目的: 種別/プリセット/人数/名前の入力 UI と作成ボタン。config から選択肢を動的構築し、`POST /api/games` を呼ぶ。
- 対象ファイル:
  - Smart: `frontend/src/containers/CreateGameContainer.tsx`（config 取得・作成 API 呼び出し・遷移トリガ）
  - Dumb: `frontend/src/components/CreateGameForm.tsx`（props で選択肢・値・onSubmit を受け取り描画。入力欄のローカル状態のみ自身で管理）
- 内容:
  - config（単位11）から `objectTypes` / `areaPresets` を取得して選択肢を構築。
  - 人数は初回 N=3 固定運用可（UI 固定でも API は可変で送る）。
  - displayName 任意（未入力可）。
  - 作成成功で単位12の遷移を発火。config が空/エラー時のフォールバック表示。
- 完了条件: 入力→作成→遷移が一気通貫。Smart のみが API を呼ぶ。
- 検証方法: Vitest。API とルータを注入/モック。
  - `test_createScreen_buildsOptionsFromConfig`
  - `test_createScreen_onSubmit_callsCreateGameWithSelectedValues`
  - `test_createScreen_whenConfigFails_showsErrorState`
  - `test_createScreen_onSuccess_navigatesToGamePath`

### 14. フロント: 待機画面（Smart コンテナ + Dumb ビュー） — 層: ui

- 目的: 作成後の待機画面（US-04 の最低限）。参加者一覧（作成者のみ）・招待 URL コピー・WAITING 状態表示。ポーリング/開始ボタンは含めない（US-05/06）。
- 対象ファイル:
  - Smart: `frontend/src/containers/WaitingRoomContainer.tsx`（URL から `gameId`/`playerId` を取得。招待 URL 組み立てはクライアント責務）
  - Dumb: `frontend/src/components/WaitingRoomView.tsx`（参加者一覧・状態・コピーボタンを描画）
- 内容:
  - 参加者一覧: この時点では作成者のみ（ポーリング更新は US-05 に委ねる。表示は静的でよい。リスク 7）。
  - 招待 URL コピー: `gameId` のみの URL（`playerId` を含めない）をクリップボードにコピー。クリップボード API は注入可能にしてテスト可能に。
  - 状態表示: WAITING。
- 完了条件: 3 要素が表示され、コピー URL に `gameId` を含み `playerId` を含まない。
- 検証方法: Vitest。
  - `test_waitingRoom_showsCreatorInParticipants`
  - `test_waitingRoom_showsWaitingStatus`
  - `test_copyInviteUrl_containsGameIdWithoutPlayerId`

---

## テスト戦略まとめ（層別）

- **Level 1 Domain**（モック不要）: 単位3（preset 解決・objectType 検証）。既存 `AreaPresetTest` / `ObjectTypeTest` に追記。
- **Level 2 UseCase**（ポートをモック）: 単位5（創成順序・バリデーション・フォールバック・トランザクション観点）。
- **Level 3 Adapter**:
  - `@WebMvcTest`（UseCase モック）: 単位9（201/400 マッピング）、単位10（CORS）。
  - `@DataJpaTest` + Testcontainers（実 PostGIS）: 単位6（3 テーブル操作の DB 整合・ロールバック）。既存 `PostgisTestContainer` / `GamePlayerSchemaTest` を踏襲。
- **フロント Vitest + testing-library**: 単位11〜14。fetch / ルータ / クリップボードは注入・モック（既存 `fakeGeolocation` の DI パターン踏襲）。
- **品質ゲート**: バックエンド `./gradlew build`（test + ktlint）。フロント `npm run lint / typecheck / test / build`。

---

## リスク・前提

1. **objectType 検証の範囲（要確認）**: 01-spec 1.1 は「既存 Domain Entity データクラスに存在しない種別は 400」と書き、`ObjectType` enum 全体（shrine/temple/school/convenience_store/park/station）を許容範囲と読める。一方 config（US-03）が公開する選択肢は `ObjectType.SELECTABLE = [SHRINE]` のみ。**「enum に存在すれば可」なのか「SELECTABLE に限る」のか**で 400 の境界が変わる。DESIGN の「MVP は shrine のみ」と整合させ SELECTABLE 準拠（config で選べるものだけ許可）を推奨だが、独断で決めず確認する。

2. **UseCase 層・Domain ポートの新規導入（重要なアーキ判断）**: 既存コードは UseCase / ポートを持たず、`ConfigController` は Domain 定数を直接返している。US-04 が最初の UseCase 導入点になるため、**ポートの粒度（game/player 別々か、創成をまとめた単一ポートか）**と **DI の置き場所**を確定する必要がある。CLAUDE.md の DIP 方針に沿うが、抽象の切り方は要相談（MEMORY: 重要なアーキ決定は独断で決めない）。

3. **トランザクション境界と JPA の後埋め設計**: 創成は「game INSERT → player INSERT → game UPDATE」の 1 Tx。`GameJpaEntity` は循環回避のため creatorPlayerId 後埋め設計になっており、既存の取得＋save でも `@Transactional`（UseCase 境界）内なら 1 Tx で成立する。ロールバックテスト（途中失敗で game だけ残らない）を必ず入れる。

4. **フロントのルータライブラリ選定（依存追加）**: 現状ルータ未導入。react-router が成熟の第一候補（CLAUDE.md「成熟ライブラリは積極採用」に合致）だが、依存追加はアーキ判断のため独断で決めず確認する（MEMORY: prefer-mature-libraries と ask-before-arch-decisions）。導入しない場合は最小自前ルーティング（`history` API + パス解析）も選択肢。

5. **API ベース URL / CORS 許可オリジンの設定値**: フロントは別オリジン前提（`resources/static` 非同梱）。バックの許可オリジンとフロントの `VITE_API_BASE_URL` は環境依存値。ハードコードせず設定/env で外出しする。デプロイ先未確定のため、ローカル開発の既定値（例: `http://localhost:5173`）で仮置きし、後決めに耐える形にする。

6. **displayName フォールバックの実行順序依存**: フォールバックは `playerId` 先頭 8 文字を使うため、**playerId 発番が player INSERT より前**でなければならない（サーバー発番 UUID を先に確定 → displayName 決定 → INSERT）。UseCase の手順で発番順序を明示する。空文字・空白のみも未送信同様にフォールバックする（01-spec テスト観点）。

7. **待機画面の参加者一覧はこの時点で静的**: ポーリング更新は US-05/US-08 のスコープ。US-04 では「作成者のみ」を表示できれば受け入れ条件を満たす。過剰実装（ポーリング前倒し）をしない。
