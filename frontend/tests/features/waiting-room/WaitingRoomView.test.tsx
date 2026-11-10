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
    participants: [{ playerId: "p-1", displayName: "たろう", confirmed: false }],
    playerCount: 3,
    areaThreshold: 500_000,
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
  it("test_copy_failed_exposesManualCopyAndRetry", async () => {
    const user = userEvent.setup();
    const { props } = renderView({ copyFailed: true });
    expect(screen.getByRole("textbox", { name: "招待URL" })).toHaveValue(props.inviteUrl);
    expect(screen.getByRole("status")).toHaveTextContent(
      "コピーできませんでした。URLを選択してコピーしてください",
    );
    expect(screen.queryByText("コピーしました")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "招待URLをコピー" }));
    await user.click(screen.getByRole("button", { name: "招待URLをコピー" }));
    expect(props.onCopyInviteUrl).toHaveBeenCalledTimes(2);
    expect(screen.queryByText(/WAITING/)).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "待機中" })).toBeInTheDocument();
  });
  it("test_waitingRoomView_differentThreshold_explainsPurposeWithSelectedImage", () => {
    renderView({ areaThreshold: 2_000_000 });
    expect(screen.getByText(/2.00 km²以内/)).toBeInTheDocument();
    expect(screen.getByText(/丸はプレイヤーの位置/)).toBeInTheDocument();
    expect(screen.getByRole("img", { name: /3人の位置/ })).toHaveAttribute(
      "src",
      expect.stringContaining("game-purpose-little-planet-v3.png"),
    );
    expect(screen.getByText("3人で囲む例")).toBeInTheDocument();
  });
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
