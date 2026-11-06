// 面積メーター（Dumb / view・US-10）。
//
// props（sqm / threshold）だけで「現在面積の数値」「0〜上限を満杯とする進捗ゲージ」「上限接近の警告」
// 「上限超過（確定不能）＋超過分」「未計測」を描き分ける。API も共有状態も知らない（Smart から props で受け取る）。
// 判定・整形は純関数 areaMeter に委譲する。凸包・面積・内包の計算はしない（サーバー計算値の比較のみ）。
//
// 描画してはいけないもの（回帰防止）: objectCount・獲得数・正多角形スコア・regularityHint・
// 「達成」「確定できます」等の到達促し文言。面積メーターは「上限を超えないよう見張る」ゲージであって
// 「目標に届かせる」ゲージではない。
//
// レイアウト: 地図オーバーレイ（AccuracyReadout / DegradedBanner に倣った absolute z-[1000]）。
// 将来の正多角形ヒントゲージを兄弟要素として横に足せるよう、フレックス行の 1 要素として置く。

import { deriveMeterState, formatArea } from "./areaMeterState";

export type AreaMeterProps = {
  // 現在の暫定凸包面積（m²・サーバー計算）。live 3点未満で形が非成立のときは null（未計測）。
  sqm: number | null;
  // 面積成立判定の上限（m²・作成時確定）。ゲージの満杯ライン。
  threshold: number;
};

export function AreaMeter({ sqm, threshold }: AreaMeterProps) {
  const state = deriveMeterState(sqm, threshold);
  const percent = Math.round(state.fillRatio * 100);

  // ゲージのバー色。warning=琥珀（近い・超えるな）、over=赤（振り切れ）、normal=緑。
  const barColor =
    state.kind === "over"
      ? "bg-red-500"
      : state.kind === "warning"
        ? "bg-amber-400"
        : "bg-emerald-500";

  return (
    // 将来の兄弟ゲージ（正多角形ヒント）を横に並べられるフレックス行。汎用ラッパの先取りはしない。
    <div className="absolute right-2 bottom-2 z-[1000] flex gap-2">
      <div className="min-w-44 rounded bg-neutral-800/85 px-3 py-2 text-white">
        <div className="text-xs text-neutral-300">面積（上限まで）</div>

        {state.kind === "unmeasured" ? (
          // 未計測: 0% ではなく空/グレーのプレースホルダ＋案内文（progressbar は出さない）。
          <>
            <p className="mt-1 text-sm">まだ計測できません（3人以上の位置が必要）</p>
            <div className="mt-1 h-2 w-full rounded bg-neutral-600/70" aria-hidden="true" />
          </>
        ) : (
          <>
            <p className="mt-0.5 text-lg font-semibold tabular-nums">{formatArea(sqm as number)}</p>
            <div
              role="progressbar"
              aria-label="上限に対する現在面積"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={percent}
              className="mt-1 h-2 w-full overflow-hidden rounded bg-neutral-600/70"
            >
              <div className={`h-full ${barColor}`} style={{ width: `${percent}%` }} />
            </div>
            <p className="mt-1 text-[10px] text-neutral-300 tabular-nums">
              上限 {formatArea(threshold)}
            </p>

            {state.kind === "warning" && (
              <p className="mt-1 text-xs text-amber-300">上限に接近しています</p>
            )}
            {state.kind === "over" && state.overflowSqm !== null && (
              <>
                <p className="mt-1 text-xs font-semibold text-red-300">
                  上限超過（このままでは確定できません）
                </p>
                <p className="text-xs text-red-200 tabular-nums">
                  超過分 {formatArea(state.overflowSqm)}
                </p>
              </>
            )}
          </>
        )}
      </div>
    </div>
  );
}
