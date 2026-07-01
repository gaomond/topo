# 05-review — US-04: ゲーム作成

## 判定: 承認

品質ゲート（build/lint/typecheck/test）は緑。人間的観点（仕様充足・Clean Architecture・ユビキタス言語・テストの質）でレビューした結果、承認とする。オーケストレーター確定の3判断（objectType=SELECTABLE のみ / react-router-dom 導入 / game・player 別ポート）にいずれも忠実。ブロッキング指摘なし。以下は任意の改善提案（low）のみ。

## 指摘

- [low] `src/main/kotlin/.../usecase/CreateGameUseCase.kt:65-68` `areaThreshold = areaPreset.sqm.toDouble()`。ポート/エンティティ/DBカラム（`area_threshold double`）が Double、プリセット定義は Long。現状は 500k/2M/10M いずれも Double で正確に表現でき丸め誤差は出ないため実害なし。将来 sqm が大きくなる/端数を持つ場合に備え、DESIGN の `area_threshold double` 前提に整合していることの認識だけ共有（対応不要）。
- [low] `frontend/src/containers/CreateGameContainer.tsx:57-63` 送信成功時は `navigate` で離脱するため `submitting` を戻していないが、失敗時のみ `setSubmitting(false)`。意図的で妥当。ただし submit ハンドラ内で `values.displayName.trim() === ""` 判定とサーバー側フォールバックが二重（クライアントで空→undefined 化、サーバーで null/空/空白→UUID）。責務はサーバーが単一ソースなのでクライアント判定は「空文字を送らない」最小化に留まっており問題なし（DRY 逸脱ではない）。
- [low] `src/main/kotlin/.../config/WebCorsConfig.kt:24` `allowedMethods("GET","POST","PUT","OPTIONS")` に DELETE/PATCH 非含。US-04 のスコープ内 API（GET/POST）は充足。後続で確定/参加等の DELETE を足す際に追記が要る点のみメモ（現時点で対応不要）。
- [low] `src/test/kotlin/.../adapter/persistence/GameCreationPersistenceTest.kt:106-118` トランザクションのロールバックは「途中失敗のトリガ（FK 違反・game 不在で例外送出）」までを検証し、`@Transactional` による全ロールバック（game だけ残り player 無し状態にならない）自体は UseCase の責務として Level 2/構造で担保する設計。DataJpaTest は各テストが既定ロールバックされるため、この層で全ロールバックを直接アサートしづらいのは妥当。受け入れ条件は満たすが、「game INSERT 後に player INSERT が失敗→game も残らない」を1トランザクションで通す統合テストがあると仕様の安全性がより明示的になる（任意・将来）。

## 良かった点

- 依存方向が正しい。Domain（GameCreationCommand/Result・ObjectType・AreaPreset・GameValidationException・ポート）は Spring/JPA/PostGIS 非依存。UseCase は `GameRepositoryPort`/`PlayerRepositoryPort` のみに依存し JPA 具象（GameRepository/GameJpaEntity）を import していない。adapter 同士の直接呼び出しもなく、outbound は Domain 値（UUID/String/GameStatus）だけを授受して JPA 型を露出していない（DIP 遵守）。
- 検証範囲 SELECTABLE の単一ソース化が的確。`ObjectType.selectableFromJsonValueOrNull` を config（ConfigController）と作成 API（UseCase）の両方が使い、公開選択肢と許可範囲が構造的に一致（乖離不能）。判断1に忠実。
- 創成順序と displayName フォールバックの順序依存を正しく処理。playerId をフォールバックより前に発番し、game→player→creator UPDATE を `@Transactional` で1トランザクション。null/空/空白（trim 後 empty）を一律フォールバックし DB は常に非 null（D6 案A）。
- 命名規約遵守。`CreateGameRequest`/`CreateGameResponse`（ボディ全体）、`AreaPresetPayload`（構成要素）で Payload/Response の使い分けが CLAUDE.md 通り。Dto を Response 構成要素に使っていない。Web 表現と Domain 型（Command/Result）を別型に分離し境界で変換。
- Error Handling が規約通り。Domain で `GameValidationException(reason)` を定義し、inbound の `@RestControllerAdvice` で 400（ProblemDetail・reason プロパティ）へマッピング。Domain 層にロギング/HTTP 依存を持ち込んでいない。
- Smart/Dumb 分割が明確。API 呼び出しは Container（Smart）のみ、`CreateGameForm`/`WaitingRoomView`（Dumb）は props 描画と入力欄の閉じた状態のみ。API/clipboard/origin/baseUrl/fetch を props・オプションで注入可能にし、fakeGeolocation の DI パターンを踏襲してブラウザ非依存でテスト。
- テストの質が高く自作自演でない。全レベルで src からの import を確認（Domain/UseCase/Adapter/CORS/Persistence、フロント各所）、テスト内でのプロダクション再定義なし。テスト名は `test_Action_Condition_Result` 準拠。招待URLに playerId が含まれない（`not.toContain("player-456")`）、レスポンスに inviteUrl が漏れない（`doesNotExist`）等、仕様の否定条件まで検証。CORS は許可/拒否/プリフライトの3系統、playerCount は 2/0/-1 の境界、displayName は 空/空白/タブのフォールバックを網羅。
- Level 2 はポートを手書き spy で差し替えて外部モックライブラリに非依存、mockito-kotlin は Level 3（@WebMvcTest）に限定しプロダクション依存を汚さない、という層ごとの依存分離が適切。
- スコープ厳守。参加API・ポーリング更新・開始ボタン・上限バリデーションは未実装で「含まない」に忠実。待機画面の参加者一覧を静的（作成者のみ・仮表示）に留め US-05 での差し替えを明記し過剰実装を回避。
