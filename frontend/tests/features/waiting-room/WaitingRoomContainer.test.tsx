import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { SWRConfig } from "swr";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/topoApi";
import type { GameStateResponse } from "@/api/types";
import { WaitingRoomContainer } from "@/features/waiting-room/WaitingRoomContainer";
import { createFakeGeolocation } from "../../fakeGeolocation";
import { createFakeTopoApi, type FakeTopoApi, gameState } from "../../fakeTopoApi";

// 現在 URL（パス + クエリ）を可視化するプローブ。遷移後の URL を検証する。
function LocationProbe() {
  const location = useLocation();
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>;
}

// ACTIVE 遷移先の地図（GeoTrackingContainer）は測位を注入して描画させる。
// 安全コンテキスト + fake Geolocation なら INITIALIZING で地図（.leaflet-container）を出す。
function geoDeps() {
  return { geolocation: createFakeGeolocation(), isSecureContext: true };
}

// SWR キャッシュはテストごとに provider（新規 Map）で隔離する。
function renderAt(url: string, fake: FakeTopoApi, clipboard?: Pick<Clipboard, "writeText">) {
  const clip = clipboard ?? { writeText: vi.fn().mockResolvedValue(undefined) };
  return render(
    <SWRConfig value={{ provider: () => new Map() }}>
      <MemoryRouter initialEntries={[url]}>
        <LocationProbe />
        <Routes>
          <Route
            path="/game/:gameId"
            element={
              <WaitingRoomContainer
                api={fake.api}
                clipboard={clip}
                origin="https://topo.example"
                refreshIntervalMs={5000}
                geoDeps={geoDeps()}
              />
            }
          />
        </Routes>
      </MemoryRouter>
    </SWRConfig>,
  );
}

describe("WaitingRoomContainer", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("test_waitingRoom_withoutPlayerId_whenWaiting_showsJoinForm", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(gameState({ status: "WAITING" }));

    renderAt("/game/game-123", fake);

    expect(await screen.findByRole("button", { name: "参加する" })).toBeInTheDocument();
    // 参加前は POST しない。
    expect(fake.joinGame).not.toHaveBeenCalled();
  });

  it("test_waitingRoom_afterJoin_navigatesToGamePathWithPlayerQuery", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(gameState({ status: "WAITING" }));
    fake.joinGame.mockResolvedValue({ playerId: "player-789" });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTimeAsync });

    renderAt("/game/game-123", fake);

    await user.type(await screen.findByLabelText(/表示名/), "じろう");
    await user.click(screen.getByRole("button", { name: "参加する" }));

    expect(fake.joinGame).toHaveBeenCalledWith("game-123", { displayName: "じろう" });
    await waitFor(() =>
      expect(screen.getByTestId("location")).toHaveTextContent("/game/game-123?p=player-789"),
    );
  });

  it("test_waitingRoom_withPlayerId_doesNotCallJoin_andShowsWaiting", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(
      gameState({
        status: "WAITING",
        players: [{ playerId: "player-456", displayName: "あなた", confirmed: false }],
      }),
    );

    renderAt("/game/game-123?p=player-456", fake);

    expect(await screen.findByTestId("game-status")).toHaveTextContent("WAITING");
    expect(fake.joinGame).not.toHaveBeenCalled();
  });

  it("test_waitingRoom_polling_reflectsNewParticipant", async () => {
    const fake = createFakeTopoApi();
    const one: GameStateResponse = gameState({
      status: "WAITING",
      players: [{ playerId: "player-456", displayName: "あなた", confirmed: false }],
    });
    const two: GameStateResponse = gameState({
      status: "WAITING",
      players: [
        { playerId: "player-456", displayName: "あなた", confirmed: false },
        { playerId: "player-999", displayName: "なかま", confirmed: false },
      ],
    });
    fake.getGameState.mockResolvedValueOnce(one).mockResolvedValue(two);

    renderAt("/game/game-123?p=player-456", fake);

    await waitFor(() => expect(screen.getByTestId("participant-count")).toHaveTextContent("1"));

    await vi.advanceTimersByTimeAsync(5000);

    await waitFor(() => expect(screen.getByTestId("participant-count")).toHaveTextContent("2"));
    expect(screen.getByLabelText("参加者一覧")).toHaveTextContent("なかま");
  });

  it("test_waitingRoom_withUnknownGameId_showsNotFoundWithCreateLink", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockRejectedValue(new ApiError(404, "/api/games/game-x"));

    renderAt("/game/game-x?p=player-1", fake);

    expect(await screen.findByRole("alert")).toHaveTextContent("ゲームが見つかりません");
    expect(screen.getByRole("link", { name: /作成/ })).toBeInTheDocument();
  });

  it("test_waitingRoom_withActiveGame_withoutPlayerId_showsCannotJoin", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(gameState({ status: "ACTIVE" }));

    renderAt("/game/game-123", fake);

    expect(await screen.findByText("このゲームには参加できません")).toBeInTheDocument();
    expect(fake.joinGame).not.toHaveBeenCalled();
  });

  it("test_waitingRoom_withCompletedGame_withoutPlayerId_showsCannotJoin", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(gameState({ status: "COMPLETED" }));

    renderAt("/game/game-123", fake);

    expect(await screen.findByText("このゲームには参加できません")).toBeInTheDocument();
  });

  it("test_waitingRoom_withPlayerIdNotInParticipants_showsNotFound", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(
      gameState({
        status: "WAITING",
        players: [{ playerId: "someone-else", displayName: "別人", confirmed: false }],
      }),
    );

    renderAt("/game/game-123?p=player-456", fake);

    expect(await screen.findByRole("alert")).toHaveTextContent("ゲームが見つかりません");
  });

  it("test_copyInviteUrl_containsGameIdWithoutPlayerId", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(
      gameState({
        status: "WAITING",
        players: [{ playerId: "player-456", displayName: "あなた", confirmed: false }],
      }),
    );
    const clipboard = { writeText: vi.fn().mockResolvedValue(undefined) };
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTimeAsync });

    renderAt("/game/game-123?p=player-456", fake, clipboard);

    await user.click(await screen.findByRole("button", { name: "招待URLをコピー" }));

    expect(clipboard.writeText).toHaveBeenCalledWith("https://topo.example/game/game-123");
    const copied = clipboard.writeText.mock.calls[0][0] as string;
    expect(copied).not.toContain("player-456");
  });

  // 定員ちょうどの参加者一覧（player-456 を含む 3 人）。
  function fullPlayers() {
    return [
      { playerId: "player-456", displayName: "あなた", confirmed: false },
      { playerId: "player-2", displayName: "ふたり", confirmed: false },
      { playerId: "player-3", displayName: "さんにん", confirmed: false },
    ];
  }

  it("test_waitingRoom_creatorAtCapacity_startButtonEnabled", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(
      gameState({
        status: "WAITING",
        playerCount: 3,
        creatorPlayerId: "player-456",
        players: fullPlayers(),
      }),
    );

    renderAt("/game/game-123?p=player-456", fake);

    expect(await screen.findByRole("button", { name: "ゲームを開始" })).toBeEnabled();
  });

  it("test_waitingRoom_creatorUnderCapacity_startButtonDisabled", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(
      gameState({
        status: "WAITING",
        playerCount: 3,
        creatorPlayerId: "player-456",
        players: [{ playerId: "player-456", displayName: "あなた", confirmed: false }],
      }),
    );

    renderAt("/game/game-123?p=player-456", fake);

    expect(await screen.findByRole("button", { name: "ゲームを開始" })).toBeDisabled();
  });

  it("test_waitingRoom_nonCreatorAtCapacity_startButtonDisabled", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(
      gameState({
        status: "WAITING",
        playerCount: 3,
        creatorPlayerId: "player-2",
        players: fullPlayers(),
      }),
    );

    renderAt("/game/game-123?p=player-456", fake);

    expect(await screen.findByRole("button", { name: "ゲームを開始" })).toBeDisabled();
  });

  it("test_waitingRoom_onStartClick_callsStartGameWithPlayerId", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(
      gameState({
        status: "WAITING",
        playerCount: 3,
        creatorPlayerId: "player-456",
        players: fullPlayers(),
      }),
    );
    fake.startGame.mockResolvedValue({ gameId: "game-123", status: "ACTIVE" });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTimeAsync });

    renderAt("/game/game-123?p=player-456", fake);

    await user.click(await screen.findByRole("button", { name: "ゲームを開始" }));

    expect(fake.startGame).toHaveBeenCalledWith("game-123", { playerId: "player-456" });
  });

  it("test_waitingRoom_afterStartSucceeds_showsMapScreen", async () => {
    const fake = createFakeTopoApi();
    const waiting = gameState({
      status: "WAITING",
      playerCount: 3,
      creatorPlayerId: "player-456",
      players: fullPlayers(),
    });
    const active = gameState({
      status: "ACTIVE",
      playerCount: 3,
      creatorPlayerId: "player-456",
      players: fullPlayers(),
    });
    fake.getGameState.mockResolvedValueOnce(waiting).mockResolvedValue(active);
    fake.startGame.mockResolvedValue({ gameId: "game-123", status: "ACTIVE" });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTimeAsync });

    const { container } = renderAt("/game/game-123?p=player-456", fake);

    await user.click(await screen.findByRole("button", { name: "ゲームを開始" }));

    // 開始成功 → mutate で ACTIVE を取り込み、地図画面（US-02 雛形）へ切り替わる。
    await waitFor(() => expect(container.querySelector(".leaflet-container")).not.toBeNull());
    expect(screen.queryByRole("button", { name: "ゲームを開始" })).not.toBeInTheDocument();
  });

  it("test_waitingRoom_whenStatusBecomesActiveViaPolling_showsMapScreen", async () => {
    const fake = createFakeTopoApi();
    const waiting = gameState({
      status: "WAITING",
      playerCount: 3,
      creatorPlayerId: "player-2",
      players: fullPlayers(),
    });
    const active = gameState({
      status: "ACTIVE",
      playerCount: 3,
      creatorPlayerId: "player-2",
      players: fullPlayers(),
    });
    fake.getGameState.mockResolvedValueOnce(waiting).mockResolvedValue(active);

    // 非作成者（player-456）。開始ボタンは押さず、ポーリングで ACTIVE を検知する。
    const { container } = renderAt("/game/game-123?p=player-456", fake);

    await screen.findByRole("button", { name: "ゲームを開始" });

    await vi.advanceTimersByTimeAsync(5000);

    await waitFor(() => expect(container.querySelector(".leaflet-container")).not.toBeNull());
  });
});
