import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { JoinGameForm } from "@/features/join-game/JoinGameForm";

describe("JoinGameForm", () => {
  it("test_joinForm_rendersNameInputAndJoinButton", () => {
    render(<JoinGameForm onJoin={vi.fn()} submitting={false} />);
    expect(screen.getByLabelText(/表示名/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "参加する" })).toBeInTheDocument();
  });

  it("test_joinForm_onSubmit_callsJoinWithDisplayName", async () => {
    const user = userEvent.setup();
    const onJoin = vi.fn();
    render(<JoinGameForm onJoin={onJoin} submitting={false} />);

    await user.type(screen.getByLabelText(/表示名/), "じろう");
    await user.click(screen.getByRole("button", { name: "参加する" }));

    expect(onJoin).toHaveBeenCalledWith("じろう");
  });

  it("test_joinForm_whenSubmitting_disablesButton", () => {
    render(<JoinGameForm onJoin={vi.fn()} submitting={true} />);
    expect(screen.getByRole("button", { name: "参加中…" })).toBeDisabled();
  });
});
