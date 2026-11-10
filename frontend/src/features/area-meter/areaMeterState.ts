// 面積メーターの純粋ロジック（domain 相当・React 非依存）。
//
// 「面積の人間可読整形」と「メーター状態（未計測/通常/警告/上限超過）＋ゲージ充填率＋超過分」の
// 導出を純関数に切り出す（テスト容易・Dumb を薄く保つ）。クライアント計算はサーバー計算値
// （sqm と threshold）の**スカラー比較のみ**で、凸包・面積・内包の計算はしない（US-10 / CLAUDE.md）。

// 上限接近（警告）の判定比率。この比率以上かつ上限以下で「上限に接近」を出す。
// spec 1.5/1.6 の「実装時決定」に基づく定数（0.85）。警告＝「近い・超えるな」であって「下げろ」ではない。
export const WARNING_RATIO = 0.85;

// メーターの状態種別。
// - unmeasured: currentArea=null（採用位置が3人分未満で形が非成立）。0% とは区別する。
// - normal:     上限に余裕あり（退化 sqm=0 を含む）。
// - warning:    上限に接近（<= 上限）。
// - over:       上限超過（overflowSqm に超過分）。
export type MeterKind = "unmeasured" | "normal" | "warning" | "over";

export type MeterState = {
  kind: MeterKind;
  // 0〜1。0〜threshold を満杯とする充填率。over でも満杯止まり（1）。unmeasured は 0。
  fillRatio: number;
  // over のときのみ超過分（m²）。それ以外は null。
  overflowSqm: number | null;
};

/**
 * 現在面積（サーバー計算値）と上限から、メーター状態を導出する。
 *
 * 判定は sqm と threshold のスカラー比較のみ。境界 sqm === threshold は over にしない（有効側＝warning）。
 */
export function deriveMeterState(sqm: number | null, threshold: number): MeterState {
  if (sqm === null) {
    return { kind: "unmeasured", fillRatio: 0, overflowSqm: null };
  }
  // threshold は作成時確定の正の上限だが、0 除算を避けるため防御する。
  const fillRatio = threshold > 0 ? Math.min(sqm / threshold, 1) : 0;

  if (sqm > threshold) {
    return { kind: "over", fillRatio: 1, overflowSqm: sqm - threshold };
  }
  if (threshold > 0 && sqm >= threshold * WARNING_RATIO) {
    return { kind: "warning", fillRatio, overflowSqm: null };
  }
  return { kind: "normal", fillRatio, overflowSqm: null };
}

export { formatArea } from "@/shared/formatArea";
