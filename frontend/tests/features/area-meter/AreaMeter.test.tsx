import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AreaMeter } from "@/features/area-meter/AreaMeter";

// AreaMeter のソースを生文字列で読み込む（Vite の ?raw）。Dumb 分離の静的検証に使う。
const areaMeterSources = import.meta.glob("@/features/area-meter/AreaMeter.tsx", {
  query: "?raw",
  import: "default",
  eager: true,
}) as Record<string, string>;

// 面積メーター Dumb（US-10・単位4）。props（sqm/threshold）だけで状態を描き分ける。
describe("AreaMeter", () => {
  const THRESHOLD = 2_000_000;

  it("test_render_belowThreshold_showsAreaValueAndGauge", () => {
    render(<AreaMeter sqm={1234} threshold={THRESHOLD} />);
    expect(screen.getByText("0.001234 km²")).toBeInTheDocument();
    // 0〜上限を満杯とする進捗ゲージ。
    const gauge = screen.getByRole("progressbar");
    expect(gauge).toHaveAttribute("aria-valuemax", "100");
    expect(gauge).toHaveAttribute("aria-valuenow", "0"); // 1234/2e6 ≒ 0%
    // 上限（満杯ライン）を数値で明示する（2,000,000m² = 2.00 km²）。
    expect(screen.getByText("上限 2.00 km²")).toBeInTheDocument();
  });

  it("test_render_nearThreshold_showsWarning", () => {
    // 上限接近（1,800,000 / 2,000,000 = 90%）→ 警告。
    render(<AreaMeter sqm={1_800_000} threshold={THRESHOLD} />);
    expect(screen.getByText(/上限に接近/)).toBeInTheDocument();
    // 「下げろ」「達成」等の到達促し/減少促しは出さない（警告＝近い・超えるな）。
    expect(screen.queryByText(/達成|下げ/)).not.toBeInTheDocument();
  });

  it("test_render_overThreshold_showsWarningAndOverflow", () => {
    render(<AreaMeter sqm={2_500_000} threshold={THRESHOLD} />);
    // 現在の配置への警告は、今回の確定可否を断定しない。
    expect(screen.getByText("上限超過")).toBeInTheDocument();
    expect(screen.queryByText(/このままでは確定できません/)).not.toBeInTheDocument();
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "100");
    expect(screen.getByText("上限超過")).toHaveClass("text-red-300");
    // 超過分（どれだけ超えているか）。2,500,000 - 2,000,000 = 500,000m² = 0.50 km²。
    expect(screen.getByText("超過分 0.50 km²")).toBeInTheDocument();
    // 「失格／無効」語は使わない。
    expect(screen.queryByText(/失格|無効/)).not.toBeInTheDocument();
  });

  it.each([
    [1_699_999, false, "bg-emerald-500"],
    [1_700_000, true, "bg-amber-400"],
    [2_000_000, true, "bg-amber-400"],
  ])("test_render_boundary%s_showsExpectedWarningAndColor", (sqm, warning, color) => {
    const { container } = render(<AreaMeter sqm={sqm} threshold={THRESHOLD} />);
    expect(screen.queryByText("上限に接近しています") !== null).toBe(warning);
    expect(screen.queryByText("上限超過")).not.toBeInTheDocument();
    expect(container.querySelector(`.${color}`)).not.toBeNull();
  });

  it("test_render_nullArea_showsUnmeasuredNotZeroPercent", () => {
    render(<AreaMeter sqm={null} threshold={THRESHOLD} />);
    expect(screen.getByText(/まだ計測できません/)).toBeInTheDocument();
    // 0% のゲージ（progressbar）は出さない（未計測は 0% とは区別）。
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
  });

  it("test_render_zeroSqm_showsGaugeZeroNotUnmeasured", () => {
    // 退化（sqm=0）はゲージ 0 側で表示（未計測とは区別）。
    render(<AreaMeter sqm={0} threshold={THRESHOLD} />);
    const gauge = screen.getByRole("progressbar");
    expect(gauge).toHaveAttribute("aria-valuenow", "0");
    expect(screen.queryByText(/まだ計測できません/)).not.toBeInTheDocument();
  });

  it("test_render_always_hidesObjectCountAndRegularityAndAchievement", () => {
    // 面積のみ開示。objectCount・獲得数・正多角形スコア・「達成/確定できます」は描画しない（回帰防止）。
    render(<AreaMeter sqm={1_000_000} threshold={THRESHOLD} />);
    expect(
      screen.queryByText(/オブジェクト数|objectCount|獲得|正多角形|スコア/i),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/達成/)).not.toBeInTheDocument();
    expect(screen.queryByText(/確定できます/)).not.toBeInTheDocument();
  });

  it("test_source_always_doesNotImportApi", () => {
    // Dumb 分離: AreaMeter は API（@/api/*）を import しない（props のみで描画）。
    const source = Object.values(areaMeterSources)[0];
    expect(source).toBeDefined();
    expect(source).not.toContain("@/api");
    expect(source).not.toContain("topoApi");
  });
});
