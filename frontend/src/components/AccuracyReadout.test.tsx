import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AccuracyReadout } from "./AccuracyReadout";

describe("AccuracyReadout", () => {
  it("test_render_withAccuracy_showsRoundedMeters", () => {
    render(<AccuracyReadout accuracyMeters={23.4} />);
    // メートルは丸めて表示する。
    const readout = screen.getByText("現在地精度 ±23 m");
    // 控えめ表示: 地図を覆わず左下に重ねるだけ（inset-0 ではない）。
    expect(readout.className).not.toContain("inset-0");
  });

  it("test_render_null_rendersNothing", () => {
    render(<AccuracyReadout accuracyMeters={null} />);
    expect(screen.queryByText(/現在地精度/)).not.toBeInTheDocument();
  });
});
