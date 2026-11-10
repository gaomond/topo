import { Button } from "@/shared/ui/button";
// 「全体表示」ボタン（Dumb）。
//
// 地図右上の遊び方の下に控えめに重ねる。押下で全頂点（自分・友達・凸包）へ強制リフィットする要求を親へ伝えるだけ。
// 自身はロジックを持たず、onOverview を呼ぶ表示専用部品（AccuracyReadout と同様のオーバーレイ様式）。

export type OverviewButtonProps = {
  onOverview: () => void;
};

export function OverviewButton({ onOverview }: OverviewButtonProps) {
  return (
    <Button
      type="button"
      onClick={onOverview}
      // 画面下部の面積・確定操作と、Leafletの帰属表示を塞がない位置に置く。
      variant="outline"
      className="absolute top-17 right-3 z-[1000] px-3 text-xs shadow"
    >
      全体表示
    </Button>
  );
}
