import { describe, expect, it } from "vitest";
import { deriveMeterState, formatArea, WARNING_RATIO } from "@/features/area-meter/areaMeterState";

// 面積メーターの純粋ロジック（React 非依存・スカラー比較のみ）を検証する（US-10・単位3）。
// 凸包/面積/内包の計算はしない。サーバー計算値 sqm と threshold の比較のみ。
describe("areaMeter", () => {
  const THRESHOLD = 2_000_000;

  it("test_deriveMeterState_belowThreshold_returnsNormal", () => {
    // ratio 0.5 < WARNING_RATIO → 通常。
    const s = deriveMeterState(1_000_000, THRESHOLD);
    expect(s.kind).toBe("normal");
    expect(s.fillRatio).toBeCloseTo(0.5);
    expect(s.overflowSqm).toBeNull();
  });

  it("test_deriveMeterState_nearThreshold_returnsWarning", () => {
    // 上限に接近（>= threshold*WARNING_RATIO かつ <= threshold）→ 警告。
    const s = deriveMeterState(THRESHOLD * WARNING_RATIO, THRESHOLD);
    expect(s.kind).toBe("warning");
    expect(s.overflowSqm).toBeNull();
  });

  it("test_deriveMeterState_aboveThreshold_returnsOver", () => {
    const s = deriveMeterState(THRESHOLD + 1, THRESHOLD);
    expect(s.kind).toBe("over");
  });

  it("test_deriveMeterState_equalThreshold_returnsWarningNotOver", () => {
    // 境界 sqm === threshold は over にしない（上限以下＝有効側・DESIGN.md area_valid = sqm <= threshold）。
    const s = deriveMeterState(THRESHOLD, THRESHOLD);
    expect(s.kind).toBe("warning");
    expect(s.fillRatio).toBe(1);
    expect(s.overflowSqm).toBeNull();
  });

  it("test_deriveMeterState_null_returnsUnmeasured", () => {
    // live 3点未満で形が非成立。0% にしない（未計測）。
    const s = deriveMeterState(null, THRESHOLD);
    expect(s.kind).toBe("unmeasured");
  });

  it("test_deriveMeterState_zeroSqm_returnsNormalNotUnmeasured", () => {
    // 退化（一直線・sqm=0）は「面積ゼロ」の正当値。ゲージ0側の通常表示（未計測と区別）。
    const s = deriveMeterState(0, THRESHOLD);
    expect(s.kind).toBe("normal");
    expect(s.fillRatio).toBe(0);
  });

  it("test_deriveMeterState_over_reportsOverflowSqm", () => {
    const s = deriveMeterState(2_500_000, THRESHOLD);
    expect(s.kind).toBe("over");
    expect(s.overflowSqm).toBe(500_000);
    // over でもバーは満杯止まり（超過は別表現）。
    expect(s.fillRatio).toBe(1);
  });

  it("test_formatArea_smallValue_returnsSquareMetersWithGrouping", () => {
    const out = formatArea(1234);
    expect(out).toContain("㎡");
    expect(out).toContain("1,234");
  });

  it("test_formatArea_largeValue_returnsSquareKilometers", () => {
    // ㎢級（1,000,000㎡〜）は ㎢ 表記で可読にする。
    const out = formatArea(2_000_000);
    expect(out).toContain("㎢");
    expect(out).toContain("2");
  });
});
