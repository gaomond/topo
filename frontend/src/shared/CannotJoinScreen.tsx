// 「このゲームには参加できません」画面（Dumb）。
//
// 01-spec 1.6: ACTIVE / COMPLETED のゲームに招待 URL で来た場合に表示する（理由は出し分けない）。
// 作成画面（/）へのリンクを添える。複数機能から共有するため src/shared に置く。

import { Link } from "react-router-dom";
import { CREATE_PATH } from "@/routing/paths";

export function CannotJoinScreen() {
  return (
    <section aria-label="参加できません" role="alert">
      <h1>このゲームには参加できません</h1>
      <p>すでに開始済み、または締め切られています。</p>
      <Link to={CREATE_PATH}>新しいゲームを作成する</Link>
    </section>
  );
}
