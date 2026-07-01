import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { WaitingRoomContainer } from "@/features/waiting-room/WaitingRoomContainer";

// クリップボード・origin を注入して招待 URL コピーと表示を検証する（実ブラウザ非依存）。
function renderAt(url: string, clipboard: Pick<Clipboard, "writeText">) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route
          path="/game/:gameId"
          element={<WaitingRoomContainer clipboard={clipboard} origin="https://topo.example" />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe("WaitingRoomContainer", () => {
  let clipboard: { writeText: ReturnType<typeof vi.fn<(data: string) => Promise<void>>> };

  beforeEach(() => {
    clipboard = {
      writeText: vi.fn<(data: string) => Promise<void>>().mockResolvedValue(undefined),
    };
  });

  it("test_waitingRoom_showsWaitingStatus", () => {
    renderAt("/game/game-123?p=player-456", clipboard);
    expect(screen.getByTestId("game-status")).toHaveTextContent("WAITING");
  });

  it("test_waitingRoom_showsCreatorInParticipants", () => {
    renderAt("/game/game-123?p=player-456", clipboard);
    const list = screen.getByLabelText("参加者一覧");
    expect(list).toHaveTextContent("player-4");
  });

  it("test_copyInviteUrl_containsGameIdWithoutPlayerId", async () => {
    const user = userEvent.setup();
    renderAt("/game/game-123?p=player-456", clipboard);

    await user.click(screen.getByRole("button", { name: "招待URLをコピー" }));

    expect(clipboard.writeText).toHaveBeenCalledWith("https://topo.example/game/game-123");
    const copied = clipboard.writeText.mock.calls[0][0] as string;
    expect(copied).toContain("game-123");
    expect(copied).not.toContain("player-456");
  });

  it("test_copyInviteUrl_afterClick_showsCopiedFeedback", async () => {
    const user = userEvent.setup();
    renderAt("/game/game-123?p=player-456", clipboard);

    await user.click(screen.getByRole("button", { name: "招待URLをコピー" }));

    expect(screen.getByRole("button", { name: "コピーしました" })).toBeInTheDocument();
  });
});
