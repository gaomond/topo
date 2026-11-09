const SQM_PER_KM2 = 1_000_000;

/**
 * 面積（m²）をkm²で表示する。1m²単位の差がゼロに丸められない精度を保つ。
 */
export function formatArea(sqm: number): string {
  const value = Math.max(0, sqm);
  const km2 = (value / SQM_PER_KM2).toLocaleString("ja-JP", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 6,
  });
  return `${km2} km²`;
}
