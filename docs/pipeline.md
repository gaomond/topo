# 開発パイプライン作成計画書

個人開発（Kotlin / Spring Boot / Gradle）における、AI エージェントを活用した開発パイプラインの設計と実装計画。要求から「コミットできる状態」までを、人間の判断が必要な仕様フェーズと、自動で一気通貫するビルドフェーズの2段階で進める。

---

## 全体構成

```
[人間] ── 要求 ──▶ Phase 1: /spec ──▶ 01-spec.md ──▶ Phase 2: /build ──▶ コミット可能状態
                   （人間とラリー）      （確定仕様）     （介入なし）
```

**Phase 1 `/spec`** は人間と対話しながら仕様を固めるフェーズ。メインの対話セッションで動かす。エージェントが3つの視点（プロダクトオーナー・開発者・テスター）を演じて議論し、人間に判断を仰ぐ。

**Phase 2 `/build`** は確定した仕様を入力に、チケット化→実装→テスト→レビュー→仕上げをサブエージェントの直列パイプラインで人間の介入なしに流すフェーズ。

この分離の理由は、サブエージェントの動作モデルにある。サブエージェントは「親が起動→隔離コンテキストで作業→要約を返して終了」というモデルで、途中で人間と何往復もするのが構造的に難しい。仕様の議論には人間との往復が不可欠なのでメイン対話で行い、仕様が確定した後の作業は隔離して一気に流す。

---

## 設計方針

- **直列パイプライン**。ファンアウトしない。デバッグしやすさ優先。
- **各ステージは別コンテキスト**（Phase 2）。実装者が自分の判断を正当化してレビューをすり抜けるのを防ぐ。これがレビューが機能する核心。
- **個人開発前提**。ブランチ運用・PRレビュー承認フローは省略。最終ゴールは「コミットできる状態」で、自動コミットはしない。
- **成果物はファイル受け渡し**（`.agent-pipeline/` 配下）。各段の出力を残し、人間が途中確認できる。
- **tools は役割ごとに最小化**。レビューは読み取りのみなど、事故防止のために権限を絞る。
- **model はステージで最適化**。設計判断が必要な箇所は上位モデル、機械的作業は軽量モデル。
- **人間の介入は Phase 1 のみ**。Phase 2 は一気通貫。

---

## ツール選定方針

Anthropic 公式プラグイン（`@claude-plugins-official`）を基盤とし、公式にないものだけ外部から補完する。

| カテゴリ | ソース | 具体物 |
|---------|--------|--------|
| コードレビュー | Anthropic 公式 | `code-review` |
| Hook（ビルド強制）| Anthropic 公式 | `hookify` |
| コミットメッセージ | Anthropic 公式 | `commit-commands` |
| セキュリティガード | Anthropic 公式 | `security-guidance` |
| Kotlin/JPA スキル | Kotlin 公式 | `Kotlin/kotlin-agent-skills` |
| ヘキサゴナル規約 | 自作 CLAUDE.md | 5〜6行の規約で Opus が遵守する（専用プラグイン不要）|
| 3アミーゴス対話 | 自作コマンド | 既製プラグインは人間ラリーに不向き |
| オーケストレーター | 自作 | パイプライン制御 |

`feature-dev`（Anthropic 公式）は7フェーズ一体型のワークフローで、ステージ単位で切り出して使うには向かない。code-explorer / code-architect のプロンプト構造を参考にして自前エージェントを書く方針とする。

### インストールコマンド

```bash
# Anthropic 公式
claude plugin install code-review@claude-plugins-official --scope project
claude plugin install hookify@claude-plugins-official --scope project
claude plugin install commit-commands@claude-plugins-official --scope project
claude plugin install security-guidance@claude-plugins-official --scope project

# Kotlin 公式（バックエンドスキル）
claude plugin marketplace add Kotlin/kotlin-agent-skills
claude plugin install kotlin-agent-skills@kotlin-agent-skills --scope project
```

---

## Phase 1: `/spec`（3アミーゴス × 人間のラリー）

メインの対話セッションで動かすスラッシュコマンド（自作プロンプト）。

### 3アミーゴスの役割

要求に対し、エージェントが3つの視点を演じて議論し、観点を洗い出す。

- **プロダクトオーナー視点**: 何を・なぜ・受け入れ条件・優先度
- **開発者視点**: 実現方法・技術制約・影響範囲・既存設計との整合
- **テスター視点**: どう検証するか・エッジケース・受け入れテスト観点

### 出力フォーマット

3者の議論結果を、必ず以下の3セクションに仕分けて返す。人間が集中して見るべきは「要決定」だけで、「合意済み」は流し読みでよい。

```
## 合意済み（決定不要）
3者で見解が一致した観点。

## 要決定（人間の判断待ち）
意見が割れた / トレードオフがある観点を、選択肢付きで提示。

## 未解決リスク・前提
決まらないと進めない前提条件。
```

### ラリー（人間との往復）

人間の入力は2種類。どちらが来ても3アミーゴスは再議論し、再び3セクションで返す。

- **決定**: 「案Aで」「スコープ外」→ 確定事項に反映
- **観点の追加**: 「オフライン時の挙動が抜けてる」→ 議論に追加

### 収束と引き継ぎ

人間が「これでいい」と合図したら、確定内容を `01-spec.md` に書き出して Phase 1 終了。編集は常にエージェントが行い、人間は決定と観点出しのみ。

ストーリーが大きい場合の論理分割（`14A / 14B / 14C` のように区切る）もこのフェーズで人間と相談して決める。Phase 2 に渡す `01-spec.md` は常に1実装単位分とする。

`01-spec.md` の内容: 合意された仕様 ＋ 受け入れ条件 ＋ テスト観点 ＋ スコープ（含む/含まない）。

---

## Phase 2: `/build`（サブエージェント直列パイプライン）

`01-spec.md`（1ユーザーストーリー分）を入力に、人間の介入なしで一気通貫。各ステージは別コンテキストのサブエージェント。粒度は常に1ユーザーストーリー単位で、複数ストーリーの同時実装はしない。

### ステージ一覧とツール割り当て

```
Orchestrator
  │
  ├─▶ [2] Plan ─────────────▶ 02-plan.md
  │
  ├─▶ [3] Impl + Test ──────▶ コード + 03-impl-notes.md + 04-test-report.md
  │     │
  │     └── [Gate] hookify ── ./gradlew build 失敗なら差し戻し
  │
  ├─▶ [5] Review ────────────▶ 05-review.md（承認 or 指摘）
  │     │
  │     └── 指摘あり → Orchestrator が [3] に差し戻し（最大 N 回）
  │
  └─▶ [6] Finish ────────────▶ 06-commit-ready.md
```

---

### 0. オーケストレーター

各サブエージェントを順に起動し、前段の出力ファイルを次段へ渡す。自身はコードを書かない・レビューしない。

差し戻しループの管理が主要な責務。サブエージェント同士は直接やりとりできない（起動→結果返却→終了のモデル）ため、オーケストレーターがループを回す。

```
loop (最大 N 回, 例 N=3):
  実装エージェント起動 → 03-impl-notes.md
  レビューエージェント起動 → 05-review.md
  if 05-review.md == 承認: break
  else: 指摘を実装エージェントに渡して再実行
N 回超過 → 人間にエスカレーション
```

| 項目 | 内容 |
|------|------|
| tools | Read, Bash（サブエージェント起動のみ）|
| model | — |
| プラグイン | なし |

---

### 2. ストーリー整理（Plan）

`01-spec.md`（Phase 1 で確定済みの1実装単位）を、実装の手順に落とす。各単位に目的・対象ファイル/層・完了条件・検証方法を明記する。ヘキサゴナルの層（domain / application / adapter）を意識して依存順を決める。

| 項目 | 内容 |
|------|------|
| 入力 | `01-spec.md` |
| 出力 | `02-plan.md`（実装単位 ＋ 依存順 ＋ 各単位の完了条件）|
| tools | Read, Grep, Glob |
| model | Opus（設計判断）|
| プラグイン | なし（CLAUDE.md のヘキサゴナル規約を参照）|

---

### 3. 実装 + テスト（Impl + Test）

実装とテストを同じエージェントが担当する。テストファースト基調で、受け入れ条件から先にテストを書き（RED）、実装で通し（GREEN）、整理する（REFACTOR）。テストを先に書くことで「実装に合わせた自作自演テスト」を防ぐ。

| 項目 | 内容 |
|------|------|
| 入力 | `01-spec.md` + `02-plan.md`（+ 差し戻し時は `05-review.md`）|
| 出力 | コード変更 ＋ テストコード ＋ `03-impl-notes.md` ＋ `04-test-report.md` |
| tools | Read, Write, Edit, Glob, Bash |
| model | Opus（実装品質）|

**付与するプラグイン・スキル:**

| プラグイン / スキル | ソース | 役割 |
|-------------------|--------|------|
| `security-guidance` | Anthropic 公式 | 編集時にインジェクション・XSS等をリアルタイム検知する Hook |
| `kotlin-backend-jpa-entity-mapping` | Kotlin 公式 | JPA エンティティの Kotlin 特有の罠を防止 |
| CLAUDE.md（ヘキサゴナル規約）| 自作 | domain/application/adapter の依存方向ルール |
| CLAUDE.md（テスト戦略）| 自作 | テストスライス活用、Testcontainers 等 |

---

### Quality Gate（品質ゲート — Stage 3 の出口）

`hookify`（Anthropic 公式）で `./gradlew build`（test + ktlint + detekt 込み）を SubagentStop に強制する。実装エージェントが「完了」と報告する前に自動実行し、失敗ならそのステージに差し戻す。

「機械で判定できるもの（テスト失敗・lint 違反）= Hook」「人間的判断（設計妥当性・テストの質）= レビューエージェント」という分担。

---

### 5. レビュー（Review）

`/code-review`（Anthropic 公式）をレビューエンジンとして使う。5つの並列 Sonnet エージェントが変更を分析し、各発見に 0〜100 の確信度スコアを付与する。閾値（デフォルト80）以上のみ報告するため、ノイズが大幅に減る。

このステージの自前エージェントは、`/code-review` の出力を `05-review.md` 形式に整形する薄いラッパーとなる。

| 項目 | 内容 |
|------|------|
| 入力 | コード変更 + `01-spec.md`（仕様充足の照合用）|
| 出力 | `05-review.md`（指摘一覧＋重大度、なければ「承認」）|
| tools | Read, Grep, Glob, Bash（read-only）。**Write/Edit を持たせない** |
| model | Opus（設計妥当性の判断）|

**レビュー観点:**

| 観点 | 実現方法 |
|------|---------|
| 仕様充足 | `01-spec.md` の受け入れ条件に照合 |
| ヘキサゴナルの層の遵守 | CLAUDE.md のルールに照合 |
| テストの質 | `/code-review` 内蔵のテスト分析エージェントがカバー |
| 命名・エラー処理・CLAUDE.md 準拠 | `/code-review` 標準機能 |

指摘があればオーケストレーター経由で Stage 3 に差し戻す。

---

### 6. 仕上げ（Finish）

レビュー承認後、コミットできる状態に整える。`commit-commands`（Anthropic 公式）でコミットメッセージを生成する。自動コミットはしない。

| 項目 | 内容 |
|------|------|
| 入力 | コード変更 + `05-review.md`（承認済み）|
| 出力 | `06-commit-ready.md`（変更サマリ ＋ コミットメッセージ案 ＋ 残課題）|
| tools | Read, Grep, Glob, Bash（read-only）|
| model | Sonnet（機械的作業）|
| プラグイン | `commit-commands`（Anthropic 公式）|

---

## ファイル受け渡し規約

```
.agent-pipeline/
├── 01-spec.md          # Phase 1 産物。1ユーザーストーリーの確定仕様
├── 02-plan.md          # 実装単位への整理
├── 03-impl-notes.md    # 実装・テストの判断記録
├── 04-test-report.md   # テスト結果
├── 05-review.md        # レビュー指摘 or 承認
└── 06-commit-ready.md  # コミット可能状態のサマリ
```

## サブエージェント定義の置き場所

`.claude/agents/*.md`（プロジェクト単位、リポジトリに含める）。各ファイルは YAML フロントマター（name / description / tools / model）＋ 本文（システムプロンプト = 役割定義）。プロジェクト固有のルール（ヘキサゴナル等）は `CLAUDE.md` に書き、全エージェントから参照される。

---

## CLAUDE.md に書く規約（全エージェント共通）

```markdown
## アーキテクチャ
- ヘキサゴナルアーキテクチャ（ポート＆アダプタ）を採用
- 依存方向: adapter → application → domain。domain は外部依存ゼロ
- domain 層に Spring/JPA/外部ライブラリの import を書かない
- adapter 同士が直接呼び合わない（必ず application 層のポートを経由）

## Kotlin / Spring 規約
- Primary constructor で DI。val で宣言
- JPA エンティティに data class を使わない。DTO は data class
- テストスライス活用（@DataJpaTest, @WebMvcTest 等）
```

---

## 段階導入

一度に全部組まず、効果を確認しながら段階的に積み上げる。

1. **Phase 1 `/spec` を作る**。3アミーゴス対話と3セクション出力を実装し、`01-spec.md` が出るところまで確認する。
2. **Phase 2 のレビューエージェント単体**を作る。`/code-review` をラッパーで包み、手動で「実装→レビュー」を回して効果を確認する。
3. **Phase 2 を全直列化**する。オーケストレーター + Plan + Impl + Review + Finish を接続する。
4. **Hook（品質ゲート）を組み込む**。`hookify` で `./gradlew build` 強制と差し戻しを自動化する。
