import { Link } from "react-router-dom";
import { CREATE_PATH } from "@/routing/paths";
import { buttonVariants } from "@/shared/ui/button";

export function PageNotFoundScreen() {
  return (
    <main className="mx-auto my-8 max-w-lg space-y-5 rounded-3xl bg-surface p-6 text-foreground">
      <h1 className="text-2xl font-bold">ページが見つかりません</h1>
      <p>URLをご確認いただくか、ゲーム作成画面から始めてください。</p>
      <Link to={CREATE_PATH} className={buttonVariants()}>
        ゲームを作成する
      </Link>
    </main>
  );
}
