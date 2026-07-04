import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import {
  WaitingRoomView,
  type WaitingRoomViewProps,
} from "@/features/waiting-room/WaitingRoomView";

// Dumb（描画のみ）の検証。開始ボタンの表示・活性/非活性・onStart 発火を確認する（US-06）。
function renderView(overrides: Partial<WaitingRoomViewProps> = {}) {
  const props: WaitingRoomViewProps = {
    status: "WAITING",
    participants: [{ playerId: "p-1", displayName: "たろう", confirmed: false }],
    playerCount: 3,
    inviteUrl: "https://topo.example/game/g-1",
    onCopyInviteUrl: vi.fn(),
    copied: false,
    onStart: vi.fn(),
    startEnabled: true,
    starting: false,
    ...overrides,
  };
  return { props, ...render(<WaitingRoomView {...props} />) };
}

describe("WaitingRoomView", () => {
  it("test_waitingRoomView_rendersStartButton", () => {
    renderView();
    expect(screen.getByRole("button", { name: "ゲームを開始" })).toBeInTheDocument();
  });

  it("test_waitingRoomView_whenStartDisabled_buttonIsDisabled", () => {
    renderView({ startEnabled: false });
    expect(screen.getByRole("button", { name: "ゲームを開始" })).toBeDisabled();
  });

  it("test_waitingRoomView_whileStarting_buttonIsDisabled", () => {
    renderView({ starting: true });
    expect(screen.getByRole("button", { name: "開始しています…" })).toBeDisabled();
  });

  it("test_waitingRoomView_onStartClick_callsOnStart", async () => {
    const user = userEvent.setup();
    const { props } = renderView({ startEnabled: true });
    await user.click(screen.getByRole("button", { name: "ゲームを開始" }));
    expect(props.onStart).toHaveBeenCalledTimes(1);
  });
});
