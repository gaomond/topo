import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { OverviewButton } from "@/features/geo-tracking/OverviewButton";

describe("OverviewButton", () => {
  it("test_render_always_全体表示ボタンが出る", () => {
    render(<OverviewButton onOverview={() => {}} />);
    expect(screen.getByRole("button", { name: "全体表示" })).toBeInTheDocument();
  });

  it("test_click_押下_onOverviewが呼ばれる", async () => {
    const user = userEvent.setup();
    const onOverview = vi.fn();
    render(<OverviewButton onOverview={onOverview} />);
    await user.click(screen.getByRole("button", { name: "全体表示" }));
    expect(onOverview).toHaveBeenCalledTimes(1);
  });
});
