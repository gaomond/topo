import { CircleCheck } from "lucide-react";
import { Link } from "react-router-dom";
import { CREATE_PATH } from "@/routing/paths";
import { formatArea } from "@/shared/formatArea";
import { buttonVariants } from "@/shared/ui/button";
import { GameResultMap } from "./GameResultMap";
import { gameResultPresentation } from "./gameResultPresentation";

export function GameResultView({ result }: { result: unknown }) {
  const presentation = gameResultPresentation(result);
  return (
    <section
      aria-label="ゲーム結果"
      className="flex h-full flex-col gap-4 overflow-auto bg-background p-4 text-foreground"
    >
      {presentation === null ? (
        <>
          <p role="alert">結果を取得できませんでした（想定外の状態）</p>
          <TrackingStoppedNotice />
        </>
      ) : (
        <>
          <h1 className="text-2xl font-bold">ゲーム完了</h1>
          <TrackingStoppedNotice />
          <dl className="flex flex-wrap gap-x-8 gap-y-2">
            <div>
              <dt>面積</dt>
              <dd className="text-xl font-semibold">{formatArea(presentation.areaSqm)}</dd>
            </div>
            <div>
              <dt>獲得オブジェクト数</dt>
              <dd className="text-3xl font-bold">
                {presentation.objectCount.toLocaleString("ja-JP")}個
              </dd>
            </div>
          </dl>
          <div className="min-h-64 flex-1 overflow-hidden rounded-xl border border-slate-200">
            <GameResultMap
              polygon={presentation.polygon}
              vertices={presentation.vertices}
              objects={presentation.objects}
            />
          </div>
        </>
      )}
      <Link to={CREATE_PATH} className={buttonVariants({ className: "w-full" })}>
        新しいゲームを作成
      </Link>
    </section>
  );
}

function TrackingStoppedNotice() {
  return (
    <p
      role="status"
      className="flex shrink-0 items-start gap-2 rounded-xl bg-secondary px-4 py-3 text-sm leading-relaxed text-foreground"
    >
      <CircleCheck aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-emerald-700" />
      <span className="text-balance">位置情報のトラッキングを終了しました</span>
    </p>
  );
}
