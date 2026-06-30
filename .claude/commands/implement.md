---
description: サブエージェント直列パイプライン。01-spec.md を入力に Plan→Impl→ゲート→Review→Finish を直列実行する。
argument-hint: "[01-spec.md のパス（省略時 .agent-pipeline/01-spec.md）]"
---

あなたは本パイプラインの **オーケストレーター**。各サブエージェントを順に起動し、前段の出力を次段へ渡す。**自分ではコードを書かない・レビューしない**。差し戻しループの管理が主務。

入力仕様: `$1`（省略時 `.agent-pipeline/01-spec.md`）。粒度は常に1ユーザーストーリー。

## 手順

1. **前提確認**: 入力の `01-spec.md` が存在するか確認。無ければ「`01-spec.md` を置いてください」と伝えて停止。
2. **Plan**: `pipeline-plan` を起動 → `02-plan.md` を生成。
3. **実装ループ**（最大 N=3 回）:
   1. `pipeline-impl` を起動（`01-spec.md` + `02-plan.md`、2回目以降は `05-review.md` も渡す）→ コード + `03-impl-notes.md` + `04-test-report.md`。
      - 品質ゲートは settings.json の SubagentStop フック（matcher=`pipeline-impl`）が自動で `./gradlew build` を実行する。build 失敗時は Impl に差し戻され、緑になるまで修正が続く。
   2. `pipeline-review` を起動 → `05-review.md`。
   3. `05-review.md` の判定が **承認** ならループを抜ける。**要修正** なら指摘を次の Impl に渡して継続。
4. **N 回超過**: 承認に至らなければ人間にエスカレーションして停止（`05-review.md` の指摘を要約）。
5. **Finish**: `pipeline-finish` を起動 → `06-commit-ready.md`。
6. **報告**: 生成物（02〜06）の場所と最終状態、`06-commit-ready.md` のコミットメッセージ案を要約。自動コミットはしない。

## 原則

- 各ステージは別コンテキストのサブエージェント。CLAUDE.md は全エージェントが自動参照する。
- 機械判定（test/lint）は品質ゲート Hook（`pipeline-impl` の SubagentStop）、人間的判断は Review が担当。
- 途中の出力はすべて `.agent-pipeline/` に残す。
