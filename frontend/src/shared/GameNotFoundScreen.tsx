// 「ゲームが見つかりません」画面（Dumb）。
//
// 01-spec 1.4: 存在しない gameId / 不正な playerId のとき表示する。作成画面（/）へのリンクを添える。
// 待機/参加など複数機能から共有するため src/shared に置く（CLAUDE.md）。

import { Link } from "react-router-dom";
import { CREATE_PATH } from "@/routing/paths";

export function GameNotFoundScreen() {
  return (
    <section aria-label="ゲームが見つかりません" role="alert">
      <h1>ゲームが見つかりません</h1>
      <p>指定されたゲームは存在しないか、URL が正しくありません。</p>
      <Link to={CREATE_PATH}>ゲームを作成する</Link>
    </section>
  );
}
