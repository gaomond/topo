import { fireEvent, render, screen } from "@testing-library/react";
import { createRef } from "react";
import { describe, expect, it, vi } from "vitest";
import { Button } from "@/shared/ui/button";

describe("Button", () => {
  it("test_click_disabled_ignoresActionAndPreservesRef", () => {
    const ref = createRef<HTMLButtonElement>();
    const action = vi.fn();
    render(
      <Button ref={ref} type="button" disabled onClick={action}>
        確定する
      </Button>,
    );
    fireEvent.click(screen.getByRole("button", { name: "確定する" }));
    expect(action).not.toHaveBeenCalled();
    expect(ref.current).toBe(screen.getByRole("button"));
  });

  it("test_render_asChild_preservesAnchorNavigation", () => {
    render(
      <Button asChild>
        <a href="/">新しいゲームを作成</a>
      </Button>,
    );
    expect(screen.getByRole("link", { name: "新しいゲームを作成" })).toHaveAttribute("href", "/");
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
