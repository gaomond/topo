# 06-commit-ready — US-04: ゲーム作成（種別・面積プリセット・人数を指定）

## 変更サマリ

`POST /api/games` API（バリデーション・1トランザクション創成・displayNameフォールバック）と、作成画面→待機画面のフロントエンド一式を追加した。US-04 が最初の本編APIのため、CORS設定・ドメイン例外マッピング・ルーティング（react-router-dom v7）もこのストーリーで初導入。Clean Architecture（Domain → UseCase → Adapter）に従い実装し、品質ゲート（バックエンド53テスト + フロント44テスト）は全緑。

### バックエンド

| 分類 | 追加ファイル |
|---|---|
| Domain モデル | `domain/model/GameCreationCommand.kt`、`domain/model/GameCreationResult.kt` |
| Domain 例外 | `domain/GameValidationException.kt` |
| Domain ポート | `domain/port/GameRepositoryPort.kt`、`domain/port/PlayerRepositoryPort.kt` |
| UseCase | `usecase/CreateGameUseCase.kt` |
| Adapter (inbound) | `adapter/web/CreateGameController.kt`、`adapter/web/CreateGameRequest.kt`、`adapter/web/CreateGameResponse.kt`、`adapter/web/GameApiExceptionHandler.kt` |
| Adapter (outbound) | `adapter/persistence/GameRepositoryAdapter.kt`、`adapter/persistence/PlayerRepositoryAdapter.kt` |
| 設定 | `config/WebCorsConfig.kt` |

既存ファイル変更:
- `domain/model/AreaPreset.kt` — `byKey()` と `sqm` プロパティを追加
- `domain/model/ObjectType.kt` — `selectableFromJsonValueOrNull()` と `SELECTABLE` セットを追加
- `build.gradle.kts` — mockito-kotlin(6.1.0) をテスト依存に追加
- `src/main/resources/application.properties` — `app.cors.allowed-origins` を追加
- `src/test/resources/application.properties` — テスト用 CORS オリジン設定を追加

追加テスト:
- `domain/model/AreaPresetTest.kt`、`domain/model/ObjectTypeTest.kt`（Level 1）
- `usecase/CreateGameUseCaseTest.kt`（Level 2、手書き spy）
- `adapter/web/CreateGameControllerTest.kt`、`adapter/web/CreateGameCorsTest.kt`（Level 3 `@WebMvcTest`）
- `adapter/persistence/GameCreationPersistenceTest.kt`（Level 3 `@DataJpaTest` + Testcontainers 実 PostGIS）

### フロントエンド

| 分類 | 追加ファイル |
|---|---|
| API クライアント | `frontend/src/api/topoApi.ts`、`frontend/src/api/types.ts` |
| ルーティング | `frontend/src/routing/paths.ts` |
| Smart (コンテナ) | `frontend/src/containers/CreateGameContainer.tsx`、`frontend/src/containers/WaitingRoomContainer.tsx` |
| Dumb (プレゼン) | `frontend/src/components/CreateGameForm.tsx`、`frontend/src/components/WaitingRoomView.tsx` |
| ルート結線 | `frontend/src/App.tsx`（`Routes` 追加）、`frontend/src/main.tsx`（`BrowserRouter` 追加） |
| 型定義 | `frontend/src/vite-env.d.ts`（`VITE_API_BASE_URL` 追加） |

追加テスト:
- `frontend/src/api/topoApi.test.ts`
- `frontend/src/containers/CreateGameContainer.test.tsx`
- `frontend/src/containers/WaitingRoomContainer.test.tsx`
- `frontend/src/App.test.tsx`

パッケージ追加:
- `react-router-dom` v7（本番依存）
- `@testing-library/user-event` / テスト周辺依存（devDependencies）

---

## コミットメッセージ案

```
feat: ゲーム作成API・CORS・作成画面・待機画面を実装（US-04）

POST /api/games（バリデーション・1Tx創成・displayNameフォールバック）を追加。
US-04がサーバー側最初の本編APIのため、CORS許可設定（WebCorsConfig）・
ドメイン例外マッピング（GameApiExceptionHandler → 400/ProblemDetail）を
このストーリーで初導入。

主な設計判断:
- objectType検証は ObjectType.selectableFromJsonValueOrNull で単一ソース化。
  config APIと作成APIの許可範囲が構造的に一致し乖離不能。
- Domainポートを GameRepositoryPort / PlayerRepositoryPort に分割（DIP）。
  UseCase はポートのみに依存し、JPA具象を一切 import しない。
- 創成順序: gameId/playerId 先行発番 → game INSERT → player INSERT →
  creator_player_id UPDATE を @Transactional で1Tx。途中失敗で全ロールバック。
- displayName フォールバック: NULL/空/空白（trim後empty）→ playerId先頭8文字。
  DBカラムは常に非NULL（全箇所でNULLチェック不要）。
- フロントは react-router-dom v7 を導入（US-04がルーティング初導入点）。
  ルートパス組み立ては routing/paths.ts に集約し単一ソース化。
- Smart/Dumb 分割: API呼び出しはコンテナのみ。API/clipboard/origin を
  props で注入可能にしブラウザ非依存でテスト可能。
- CORS許可オリジンは app.cors.allowed-origins で外部化（ハードコードなし）。

品質ゲート: バックエンド 53 tests・フロント 44 tests 全緑。
```

---

## 残課題（US-05 以降へ送るもの）

### US-05 優先（ゲーム参加）

- **参加API `POST /api/games/{id}/players`** の実装。参加者が playerId を持って待機画面に入るエンドポイント。
- **待機画面のポーリング更新**（参加者一覧を `GET /api/games/{id}` 等で定期取得）。現在は作成者のみの静的表示。`WaitingRoomContainer` の participant 部分を実データで差し替える必要がある。
- **待機画面の displayName 表示**：現状は playerId の先頭8文字（クライアント由来の仮表示）。US-05 でサーバー確定の displayName をポーリングで取得して置換する。

### US-06（ゲーム開始）

- **開始ボタンと WAITING→ACTIVE 遷移**。開始押下条件・途中参加・押せる人（作成者のみ？）の仕様は US-06 で決定。

### 技術的フォローアップ（低優先）

- **CORS の `allowedMethods` に DELETE/PATCH を追加**（US-05以降で DELETE/PATCH が必要になったとき）。現状は GET/POST/PUT/OPTIONS のみ。
- **ゲーム開始後の `GeoTrackingContainer` 統合**：US-02 で実装した地図/GPS表示を、ゲーム画面（`/game/:gameId`）に組み込む（現在は App から未参照で残置中）。
- **playerCount 上限バリデーション**：下限3のみ実装。上限は仕様未決定のため保留。
- **1Tx全ロールバックの統合テスト**：`game INSERT → player INSERT 失敗 → game も残らない` を1Tx通し確認する統合テストが未整備（現状は Level 2 のトランザクション構造と Level 3 の FK 違反トリガ確認で代替）。
