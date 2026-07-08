// 「全体表示」ボタン（Dumb）。
//
// 地図右下に控えめに重ねる。押下で全頂点（自分・友達・凸包）へ強制リフィットする要求を親へ伝えるだけ。
// 自身はロジックを持たず、onOverview を呼ぶ表示専用部品（AccuracyReadout と同様のオーバーレイ様式）。

export type OverviewButtonProps = {
  onOverview: () => void;
};

export function OverviewButton({ onOverview }: OverviewButtonProps) {
  return (
    <button
      type="button"
      onClick={onOverview}
      // z は Leaflet コントロール（400 台）より上に置き、地図の右下に固定する。
      className="absolute bottom-2 right-2 z-[1000] rounded bg-neutral-800/85 px-3 py-1.5 text-xs text-white shadow"
    >
      全体表示
    </button>
  );
}
