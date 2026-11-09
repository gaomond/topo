import { Link } from "react-router-dom";
import { CREATE_PATH } from "@/routing/paths";
import { formatArea } from "@/shared/formatArea";
import { GameResultMap } from "./GameResultMap";
import { gameResultPresentation } from "./gameResultPresentation";

export function GameResultView({ result }: { result: unknown }) {
  const presentation = gameResultPresentation(result);
  return (
    <section
      aria-label="ゲーム結果"
      className="flex h-dvh flex-col gap-4 overflow-auto bg-slate-50 p-4 text-slate-900"
    >
      {presentation === null ? (
        <p role="alert">結果を取得できませんでした（想定外の状態）</p>
      ) : (
        <>
          <h1 className="text-2xl font-bold">ゲーム完了</h1>
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
      <Link
        to={CREATE_PATH}
        className="rounded-lg bg-emerald-700 px-5 py-3 text-center font-semibold text-white"
      >
        新しいゲームを作成
      </Link>
    </section>
  );
}
