# .agent-pipeline

`/implement`（サブエージェント直列パイプライン）の成果物受け渡し場所。各ステージが前段の出力ファイルを読み、自分の出力を書く。人間が途中経過を確認できるよう、すべてファイルとして残す。

## ファイル規約

| ファイル | 産物 | 書く人 |
| --- | --- | --- |
| `01-spec.md` | 1ユーザーストーリーの確定仕様（受け入れ条件・テスト観点・スコープ） | **人間が用意**（仕様策定の自動化は未実装のため手で置く） |
| `02-plan.md` | 実装単位への整理（依存順・各単位の完了条件・検証方法） | pipeline-plan |
| `03-impl-notes.md` | 実装・テストの判断記録 | pipeline-impl |
| `04-test-report.md` | テスト結果 | pipeline-impl |
| `05-review.md` | レビュー指摘＋重大度、なければ「承認」 | pipeline-review |
| `06-commit-ready.md` | 変更サマリ＋コミットメッセージ案＋残課題 | pipeline-finish |

## 使い方

1. `01-spec.md` に1ストーリー分の確定仕様を置く。
2. メイン対話で `/implement` を実行する。
3. オーケストレーターが Plan → Impl →（品質ゲート）→ Review → Finish を順に流す。Review で指摘があれば Impl に差し戻す（最大3回）。
4. `06-commit-ready.md` が出たら人間が確認してコミットする（自動コミットはしない）。

> 品質ゲートは `.claude/settings.json` の SubagentStop フック（matcher=`pipeline-impl`）。Impl 停止時に `./gradlew build` を自動実行し、失敗なら Impl に差し戻す。
