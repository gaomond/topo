// 現在地精度の読み取り表示（Dumb）。
//
// 測位精度（coords.accuracy / メートル）を地図左下に控えめに重ねて表示する。
// 描画専用部品で、Geolocation も状態も知らない（props で受け取るだけ）。
// 地図は隠さない（DegradedBanner と同様のオーバーレイ様式）。

import { formatAccuracy } from "../hooks/geoState";

export type AccuracyReadoutProps = {
  // 直近の測位精度（メートル）。未測位は null で、その場合は何も表示しない。
  accuracyMeters: number | null;
};

export function AccuracyReadout({ accuracyMeters }: AccuracyReadoutProps) {
  if (accuracyMeters === null) {
    return null;
  }
  // 精度は移動のたびに更新される常時表示のため、ライブリージョン（role=status / aria-live）に
  // すると読み上げが頻発して煩わしい。ここでは静的な補足情報として表示する。
  return (
    <div className="absolute bottom-2 left-2 z-[1000] rounded bg-neutral-800/85 px-2 py-1 text-xs text-white">
      {formatAccuracy(accuracyMeters)}
    </div>
  );
}
