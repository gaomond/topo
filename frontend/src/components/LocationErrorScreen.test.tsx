import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { LocationErrorScreen } from "./LocationErrorScreen";

describe("LocationErrorScreen", () => {
  it("test_render_always_showsMessageAsAlertdialog", () => {
    render(<LocationErrorScreen message="テスト文言" />);
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
    expect(screen.getByText("テスト文言")).toBeInTheDocument();
  });

  it("test_render_withOnRetry_showsRetryButton", () => {
    render(<LocationErrorScreen message="拒否" onRetry={() => {}} />);
    expect(screen.getByRole("button", { name: "再試行" })).toBeInTheDocument();
  });

  it("test_clickRetry_withOnRetry_invokesCallback", async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    render(<LocationErrorScreen message="拒否" onRetry={onRetry} />);
    await user.click(screen.getByRole("button", { name: "再試行" }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it("test_render_withoutOnRetry_hidesRetryButton", () => {
    render(<LocationErrorScreen message="非安全コンテキスト" />);
    expect(screen.queryByRole("button", { name: "再試行" })).not.toBeInTheDocument();
  });
});
