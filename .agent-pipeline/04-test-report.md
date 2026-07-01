# 04-test-report — US-04: ゲーム作成

TDD で追加したテストと品質ゲートの結果。テスト対象は必ず `src/` から import（再定義なし）。

## 品質ゲート結果（全緑）

| 対象 | コマンド | 結果 |
| --- | --- | --- |
| バックエンド | `./gradlew build`（test + ktlint） | BUILD SUCCESSFUL・53 tests |
| フロント Lint | `npm run lint`（Biome） | Checked 37 files・エラー 0 |
| フロント 型 | `npm run typecheck`（tsc -b） | エラー 0 |
| フロント テスト | `npm run test`（Vitest） | 10 files / 44 tests passed |
| フロント ビルド | `npm run build`（tsc + Vite） | 成功・dist 生成 |

## バックエンド 追加テスト（層別）

### Level 1 Domain（モック不要）
- `AreaPresetTest`: `test_byKey_withMedium_returnsPresetWith2000000Sqm` / small / large / `withUnknownKey_returnsNull` / `withEmptyKey_returnsNull`。
- `ObjectTypeTest`: `test_selectableFromJsonValueOrNull_withShrine_returnsShrine` / `withNonSelectableEnumValue_returnsNull`（temple/school/station）/ `withUnknownValue_returnsNull`。SELECTABLE 準拠を検証。

### Level 2 UseCase（ポートを手書き spy で差し替え）
- `CreateGameUseCaseTest`:
  - `test_createGame_withValidInput_persistsGameThenCreatorThenUpdatesCreatorId`（創成順序・WAITING・creatorPlayerId リンク・戻り値整合）
  - `test_createGame_always_generatesDistinctGameIdAndPlayerId`
  - `test_createGame_eachPreset_resolvesToExpectedAreaThreshold`（small/medium/large → 500k/2M/10M）
  - `test_createGame_withValidInput_storesObjectTypeJsonValue`
  - `test_createGame_withInvalidObjectType_throwsValidationException` / `withNonSelectableObjectType_...`（temple）
  - `test_createGame_withInvalidAreaPreset_throwsValidationException`
  - `test_createGame_withPlayerCountBelow3_throwsValidationException`（2 / 0 / -1）/ `withPlayerCount3_isAccepted`
  - `test_createGame_withDisplayName_persistsAsIs` / `withNullDisplayName_fallsBackToUuidPrefix8` / `withBlankDisplayName_fallsBackToUuidPrefix8`（"" / 空白 / タブ）
  - `test_createGame_whenCreatorUpdateFails_propagatesException`
  - バリデーション失敗時に永続化しない（calls empty）ことも各ケースで検証。

### Level 3 Adapter — `@WebMvcTest`（UseCase を `@MockitoBean` でモック）
- `CreateGameControllerTest`:
  - `test_postGames_withValidBody_returns201AndGamePlayerIds`（201・gameId/playerId・`inviteUrl` doesNotExist）
  - `test_postGames_withoutDisplayName_returns201`
  - `test_postGames_withInvalidObjectType_returns400` / `withInvalidAreaPreset_returns400` / `withPlayerCountBelow3_returns400`（2/0/-1）
  - `test_postGames_passesRequestFieldsToUseCaseAsCommand`（Request→Command 変換）
- `CreateGameCorsTest`:
  - `test_optionsGames_fromAllowedOrigin_returns200WithCorsHeaders`
  - `test_postGames_fromAllowedOrigin_hasCorsAllowOriginHeader`
  - `test_optionsGames_fromDisallowedOrigin_isForbidden`

### Level 3 Adapter — `@DataJpaTest` + Testcontainers（実 PostGIS）
- `GameCreationPersistenceTest`（アダプタ経由で実 DB 検証）:
  - `test_createGame_viaAdapters_insertsGamePlayerAndLinksCreatorId`（3 テーブル操作整合：game=WAITING/結果 NULL/creator リンク・player=game_id/displayName）
  - `test_updateCreatorPlayerId_forMissingGame_throws`
  - `test_createPlayer_withNonExistentGameId_failsByFk`（途中失敗のトリガ）

## フロント 追加テスト（Vitest + testing-library / 注入 DI）

- `api/topoApi.test.ts`（fetch を注入）: `test_fetchConfig_returnsObjectTypesAndAreaPresets` / `test_createGame_onSuccess_returnsGameIdAndPlayerId`（POST body 検証）/ `test_createGame_onErrorStatus_throws`。
- `containers/CreateGameContainer.test.tsx`（API を注入・MemoryRouter でルート遷移を可視化）:
  - `test_createScreen_buildsOptionsFromConfig`（config から選択肢構築）
  - `test_createScreen_onSubmit_callsCreateGameWithSelectedValues`
  - `test_createScreen_onSubmitWithoutName_sendsUndefinedDisplayName`
  - `test_createScreen_onSuccess_navigatesToGamePathWithPlayerQuery`（URL が `/game/game-123?p=player-456`）
  - `test_createScreen_whenConfigFails_showsErrorState`
- `containers/WaitingRoomContainer.test.tsx`（clipboard・origin を注入）:
  - `test_waitingRoom_showsWaitingStatus` / `test_waitingRoom_showsCreatorInParticipants`
  - `test_copyInviteUrl_containsGameIdWithoutPlayerId`（コピー先に gameId を含み playerId を含まない）
  - `test_copyInviteUrl_afterClick_showsCopiedFeedback`
- `App.test.tsx`（ルーティング結線）: `test_route_root_rendersCreateScreen` / `test_route_gamePath_rendersWaitingScreen`。

## 受け入れ条件（01-spec §2）との対応

### API
201 + gameId/playerId → controller 201 テスト。DB 状態（game WAITING/area_threshold/結果 NULL・player game_id・creator_player_id リンク） → persistence テスト。displayName 未送信 → UseCase フォールバックテスト。objectType/areaPreset/playerCount<3 で 400 → controller + usecase テスト。CORS → CORS テスト（許可/拒否/プリフライト）。

### フロント
入力→作成ボタン → CreateGameForm + container テスト。作成後待機画面遷移 → navigate テスト。待機画面（参加者・招待コピー・WAITING） → WaitingRoom テスト。招待 URL に gameId のみ → copy テスト。URL が `/game/<id>?p=<pid>` → navigate/URL テスト。

## 備考
- モックライブラリ mockito-kotlin(6.1.0) をテスト依存に追加（`@MockitoBean` の Kotlin 非 null 安全スタブ用）。プロダクション依存には影響しない。
- Level 2 はポートを手書き spy で差し替え、外部モックライブラリに依存しない。
