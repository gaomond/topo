// 控えめバナー（Dumb）。
//
// 追従中の一時失敗（DEGRADED）時に地図上部へ重ねる控えめ表示。地図は隠さない。
// エラー画面（LocationErrorScreen）とは別物で、DEGRADED でエラー画面に遷移しないことを
// コンポーネント分離で担保する。

import { DEGRADED_MESSAGE } from "./geoState";

export type DegradedBannerProps = {
  visible: boolean;
};

export function DegradedBanner({ visible }: DegradedBannerProps) {
  if (!visible) {
    return null;
  }
  return (
    <div
      role="status"
      aria-live="polite"
      className="absolute inset-x-0 top-0 z-[1000] bg-neutral-800/85 px-3 py-2 text-center text-sm text-white"
    >
      {DEGRADED_MESSAGE}
    </div>
  );
}
