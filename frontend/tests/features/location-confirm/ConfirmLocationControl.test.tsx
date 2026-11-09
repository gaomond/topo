import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ConfirmLocationControl } from "@/features/location-confirm/ConfirmLocationControl";

describe("ConfirmLocationControl", () => {
  it("test_dialog_withCancel_doesNotConfirm", async () => {
    const confirm = vi.fn();
    const cancel = vi.fn();
    render(
      <ConfirmLocationControl
        confirmed={false}
        dialogOpen
        submitting={false}
        canConfirm
        error={null}
        onOpen={vi.fn()}
        onCancel={cancel}
        onConfirm={confirm}
      />,
    );
    expect(screen.getByRole("dialog")).toHaveTextContent(
      "この位置で確定しますか？ 確定すると変更できません。",
    );
    await userEvent.click(screen.getByRole("button", { name: "キャンセル" }));
    expect(cancel).toHaveBeenCalledOnce();
    expect(confirm).not.toHaveBeenCalled();
  });
  it("test_dialog_whileSending_disablesBothActions", () => {
    render(
      <ConfirmLocationControl
        confirmed={false}
        dialogOpen
        submitting
        canConfirm={false}
        error={null}
        onOpen={vi.fn()}
        onCancel={vi.fn()}
        onConfirm={vi.fn()}
      />,
    );
    for (const button of screen.getAllByRole("button")) expect(button).toBeDisabled();
  });
});
