# 01-spec.md — US-04: ゲーム作成（種別・面積プリセット・人数を指定）

> 作成者として、種別・面積プリセット・人数を指定してゲームを作成したい。
> 仲間と遊ぶ部屋を用意するため。
> 本仕様は1実装単位（US-04）分。Phase 2（実装）へ引き継ぐ。

---

## 1. 合意された仕様

### 1.1 API: `POST /api/games`

#### リクエスト

```json
{
  "objectType": "shrine",
  "areaPreset": "medium",
  "playerCount": 3,
  "displayName": "たろう"
}
```

| フィールド | 型 | 必須 | 備考 |
|---|---|---|---|
| `objectType` | string | ○ | 既存Domain Entityデータクラスと照合。不一致は400 |
| `areaPreset` | string | ○ | US-03（config）が返すプリセットkey。サーバーがsqm実値に解決して `area_threshold` に格納 |
| `playerCount` | int | ○ | `>= 3` をサーバーバリデーション。違反は400 |
| `displayName` | string | 任意 | 作成者の表示名。NULL/未送信の場合はサーバーがUUIDの先頭8文字でフォールバック |

#### サーバー処理（1トランザクション）

US-00で決めた創成順序を同一Tx内で実行する。

1. `game` をINSERT（`status = WAITING`、`creator_player_id = NULL`、結果カラムNULL）
2. 作成者の `player` をINSERT（`game_id` を埋める、`display_name` をフォールバック込みで確定）
3. `game.creator_player_id` を 2 の `player.id` でUPDATE

#### レスポンス（201 Created）

```json
{
  "gameId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "playerId": "yyyyyyyy-yyyy-yyyy-yyyy-yyyyyyyyyyyy"
}
```

- サーバーは `gameId` と `playerId` を返すのみ。
- 招待URL（gameIdだけのURL）の組み立てはクライアント責務。
- 作成者自身のURL（gameId + playerId）もクライアントが組み立てる。

#### エラーレスポンス（400 Bad Request）

- `objectType` が不正: Domain Entityデータクラスに存在しない種別
- `areaPreset` が不正: configのプリセットkeyに存在しないkey
- `playerCount < 3`: 凸包成立に必要な最低点数を満たさない

### 1.2 displayNameフォールバック

- サーバー側でDB保存時に確定する（D6: 案A）。
- `display_name` がNULL/空/未送信の場合、`player.id`（UUID）の**先頭8文字**を `display_name` に格納。
- DBの `display_name` カラムには常に値が入る（NULLにしない）。これにより全箇所でNULLチェックが不要になる。

### 1.3 CORS

US-04がサーバー側最初の本編APIのため、**CORS許可設定をこのストーリーで入れる**。

- フロント（静的ホスト）とAPI（別ドメイン）の分離に対応。
- 認証なしのため、オリジン許可のみ（Credentialsなし）。

### 1.4 フロントエンド: 作成画面

#### 作成画面

- 対象種別の選択（US-03 `GET /api/config` の返却値から選択肢を構築）
- 面積プリセットの選択（同上）
- 人数の入力 or 選択（初回リリースは3固定運用。UIは固定でも可だがAPIは可変で受ける）
- 作成者のdisplayName入力（任意・未入力ならフォールバック）
- 作成ボタン → `POST /api/games` を呼ぶ

#### 作成後の遷移（D7: 案A）

作成ボタン押下→201返却後、**待機画面**に遷移する。

待機画面の最低限の内容（US-04で含める範囲）:

- 参加者一覧（この時点では作成者のみ）
- 招待URLのコピーボタン（クライアントが `gameId` からビルド）
- ゲームの状態表示（WAITING）

待機画面の本体（ポーリングによる参加者更新、開始ボタン等）はUS-05/US-06で拡張する。

#### ルーティング

US-02は「URL解釈なし・単体ページ」と決めた。US-04で初めてルーティングが入る。

- 作成画面: ルートページ（例: `/`）
- 待機/ゲーム画面: `/game/<gameId>?p=<playerId>`（案C: URL同梱）
- 作成後の遷移で上記URLに `pushState` / ナビゲーションする

---

## 2. 受け入れ条件

### API

- [ ] `POST /api/games` に有効なリクエストを送ると201が返り、`gameId` と `playerId`（UUID）が返却される。
- [ ] DBに `game` レコードが作成されている（`status = WAITING`、`area_threshold` にプリセットの実値、結果カラムNULL）。
- [ ] DBに作成者の `player` レコードが作成されている（`game_id` が埋まっている）。
- [ ] `game.creator_player_id` が作成者の `player.id` で埋まっている。
- [ ] `displayName` 未送信時、`player.display_name` にUUID先頭8文字が格納されている（NULLではない）。
- [ ] `objectType` が不正な場合400が返る。
- [ ] `areaPreset` が不正な場合400が返る。
- [ ] `playerCount < 3` の場合400が返る。
- [ ] CORSが有効で、フロントのオリジンからAPIを呼べる。

### フロント

- [ ] 作成画面で種別・プリセット・人数・名前を入力/選択して作成ボタンを押せる。
- [ ] 作成後、待機画面に遷移する。
- [ ] 待機画面に参加者一覧（作成者のみ）、招待URLコピーボタン、WAITING状態が表示される。
- [ ] 招待URLコピーボタンでクリップボードにgameIdのみのURLがコピーされる。
- [ ] ブラウザのURLが `/game/<gameId>?p=<playerId>` に変わる。

---

## 3. テスト観点

### API

- **正常系**: 有効な入力で201、DB状態（3テーブル操作の整合：game + player + creator_id UPDATE）。
- **バリデーション**: objectType不正/areaPreset不正/playerCount=2/playerCount=0/playerCount=-1/playerCount未送信で400。
- **displayNameフォールバック**: NULL/空文字/未送信でUUID先頭8文字が入る。送信時はそのまま保存。
- **プリセット解決**: 各preset key（small/medium/large）がそれぞれ正しいsqm値に解決される。
- **トランザクション**: 途中で失敗した場合にロールバックされる（game だけ残って player がない状態にならない）。
- **CORS**: フロントオリジンからのプリフライト（OPTIONS）が200を返し、本リクエストが通る。異なるオリジンからは拒否される。

### フロント

- **作成フロー**: 入力→作成→待機画面遷移が一気通貫で動く。
- **config連携**: `GET /api/config` の返却値で選択肢が動的に構築される。configが空/エラーの場合の挙動。
- **招待URLコピー**: コピーされたURLに `gameId` が含まれ、`playerId` が含まれない。
- **URL遷移**: 遷移後のURLが `/game/<gameId>?p=<playerId>` 形式になっている。

---

## 4. スコープ

### 含む

- `POST /api/games` API（バリデーション・創成順序・displayNameフォールバック含む）。
- CORS許可設定。
- 作成画面（種別/プリセット/人数/名前の入力UI＋作成ボタン）。
- 作成後の待機画面への遷移（待機画面の最低限：参加者一覧・招待URLコピー・状態表示）。
- ルーティングの導入（`/` → `/game/<gameId>?p=<playerId>`）。

### 含まない（他ストーリー / 将来）

- 待機画面のポーリングによる参加者リアルタイム更新（US-05 / US-08）。
- 開始ボタンと WAITING→ACTIVE 遷移（US-06）。開始ボタンの押下条件・途中参加・押せる人の3問はUS-06で決定。
- 参加API `POST /api/games/{id}/players`（US-05）。
- ライブ位置送信・友達ドット・面積メーター（US-07〜10）。
- `playerCount` の上限バリデーション（現時点では上限なし）。
- 人数選択UIの拡張（初回はN=3固定運用でよい）。
