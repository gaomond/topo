// 単位換算の境界（㎡ / 万㎡ / ㎢）。
const SQM_PER_MAN = 10_000; // 万㎡（= 1 ha）
const SQM_PER_KM2 = 1_000_000; // ㎢

// 表示単位ラベル。
const UNIT_SQM = "㎡";
const UNIT_MAN = "万㎡";
const UNIT_KM2 = "㎢";

/**
 * 面積（m²）を人間可読に整形する（㎡ / 万㎡ / ㎢ を自動選択＋桁区切り）。
 *
 * 小さい値は ㎡、㎢級（1,000,000㎡〜）は ㎢。変化が見える精度で丸める。
 */
export function formatArea(sqm: number): string {
  const value = Math.max(0, sqm);
  if (value >= SQM_PER_KM2) {
    const km2 = (value / SQM_PER_KM2).toLocaleString("ja-JP", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
    return `${km2} ${UNIT_KM2}`;
  }
  if (value >= SQM_PER_MAN) {
    const man = (value / SQM_PER_MAN).toLocaleString("ja-JP", {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1,
    });
    return `${man} ${UNIT_MAN}`;
  }
  return `${Math.round(value).toLocaleString("ja-JP")} ${UNIT_SQM}`;
}
