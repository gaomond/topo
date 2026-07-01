import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { DEGRADED_MESSAGE } from "../hooks/geoState";
import { DegradedBanner } from "./DegradedBanner";

describe("DegradedBanner", () => {
  it("test_render_visibleTrue_showsQuietNotice", () => {
    render(<DegradedBanner visible={true} />);
    const banner = screen.getByRole("status");
    expect(banner).toHaveTextContent(DEGRADED_MESSAGE);
    // 控えめ表示: 地図を覆う全面オーバーレイ（inset-0）ではなく、上部に重ねるだけ。
    expect(banner.className).toContain("top-0");
    expect(banner.className).not.toContain("inset-0");
  });

  it("test_render_visibleFalse_rendersNothing", () => {
    render(<DegradedBanner visible={false} />);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
});
