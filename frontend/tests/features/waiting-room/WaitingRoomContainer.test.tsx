import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { SWRConfig } from "swr";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/topoApi";
import type { GameStateResponse } from "@/api/types";
import { WaitingRoomContainer } from "@/features/waiting-room/WaitingRoomContainer";
import { createFakeGeolocation } from "../../fakeGeolocation";
import { createFakeTopoApi, type FakeTopoApi, gameState, player } from "../../fakeTopoApi";

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
                refreshIntervalMs={2000}
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
        players: [player({ playerId: "player-456", displayName: "あなた" })],
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
      players: [player({ playerId: "player-456", displayName: "あなた" })],
    });
    const two: GameStateResponse = gameState({
      status: "WAITING",
      players: [
        player({ playerId: "player-456", displayName: "あなた" }),
        player({ playerId: "player-999", displayName: "なかま" }),
      ],
    });
    fake.getGameState.mockResolvedValueOnce(one).mockResolvedValue(two);

    renderAt("/game/game-123?p=player-456", fake);

    await waitFor(() => expect(screen.getByTestId("participant-count")).toHaveTextContent("1"));

    await vi.advanceTimersByTimeAsync(2000);

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
        players: [player({ playerId: "someone-else", displayName: "別人" })],
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
        players: [player({ playerId: "player-456", displayName: "あなた" })],
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
      player({ playerId: "player-456", displayName: "あなた" }),
      player({ playerId: "player-2", displayName: "ふたり" }),
      player({ playerId: "player-3", displayName: "さんにん" }),
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
        players: [player({ playerId: "player-456", displayName: "あなた" })],
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

    await vi.advanceTimersByTimeAsync(2000);

    await waitFor(() => expect(container.querySelector(".leaflet-container")).not.toBeNull());
  });

  it("test_waitingRoom_whenJoinReturns409_showsCannotJoinAlert_andReenablesButton", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(gameState({ status: "WAITING" }));
    fake.joinGame.mockRejectedValue(new ApiError(409, "/api/games/game-123/players"));
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTimeAsync });

    renderAt("/game/game-123", fake);

    await user.click(await screen.findByRole("button", { name: "参加する" }));

    // 409 は握り潰さず、参加不可を alert で明示する。
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "このゲームには参加できません。満員か、すでに開始されています。",
    );
    // 再試行できるようボタンは戻す。
    expect(screen.getByRole("button", { name: "参加する" })).toBeEnabled();
  });

  it("test_waitingRoom_whenStartReturns409_showsCannotStartAlert_andStaysOnWaiting", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(
      gameState({
        status: "WAITING",
        playerCount: 3,
        creatorPlayerId: "player-456",
        players: fullPlayers(),
      }),
    );
    fake.startGame.mockRejectedValue(new ApiError(409, "/api/games/game-123/start"));
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTimeAsync });

    renderAt("/game/game-123?p=player-456", fake);

    await user.click(await screen.findByRole("button", { name: "ゲームを開始" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "ゲームを開始できません。参加人数や状態が変化した可能性があります。",
    );
    // 待機画面に留まり、開始ボタンは再度押せる。
    expect(screen.getByRole("button", { name: "ゲームを開始" })).toBeEnabled();
  });

  // US-08: 統一 2 秒ポーリング（WAITING / ACTIVE 共通）と live/online/currentArea の保持。
  it("test_WAITING_2秒ごとにgetGameStateが呼ばれる", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(
      gameState({
        status: "WAITING",
        players: [player({ playerId: "player-456", displayName: "あなた" })],
      }),
    );

    renderAt("/game/game-123?p=player-456", fake);

    await waitFor(() => expect(fake.getGameState).toHaveBeenCalledTimes(1));
    await vi.advanceTimersByTimeAsync(2000);
    await waitFor(() => expect(fake.getGameState).toHaveBeenCalledTimes(2));
    await vi.advanceTimersByTimeAsync(2000);
    await waitFor(() => expect(fake.getGameState).toHaveBeenCalledTimes(3));
  });

  it("test_ACTIVE_2秒ごとにgetGameStateが呼ばれ続ける", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(
      gameState({
        status: "ACTIVE",
        players: [player({ playerId: "player-456", displayName: "あなた" })],
      }),
    );

    const { container } = renderAt("/game/game-123?p=player-456", fake);

    // ACTIVE では地図画面に切り替わるが、ポーリング hook（WaitingRoomContainer）は回り続ける。
    await waitFor(() => expect(container.querySelector(".leaflet-container")).not.toBeNull());
    const callsAfterMount = fake.getGameState.mock.calls.length;

    await vi.advanceTimersByTimeAsync(2000);
    await waitFor(() =>
      expect(fake.getGameState.mock.calls.length).toBeGreaterThan(callsAfterMount),
    );
  });

  it("test_取得したlive_online_currentAreaがパースされGeoTrackingへ伝播する", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(
      gameState({
        status: "ACTIVE",
        players: [
          player({
            playerId: "player-456",
            displayName: "あなた",
            live: { lat: 35.68, lng: 139.76, at: "2026-07-06T12:00:00Z" },
            online: true,
          }),
          player({ playerId: "player-2", displayName: "ふたり" }),
        ],
        currentArea: {
          sqm: 1234567,
          hull: [
            [35.68, 139.76],
            [35.69, 139.77],
            [35.68, 139.78],
          ],
        },
      }),
    );

    const { container } = renderAt("/game/game-123?p=player-456", fake);

    // 地図ラッパの不可視 data 属性に、live 保有者数と currentArea.sqm が伝播している（保持・公開）。
    await waitFor(() => {
      const wrapper = container.querySelector("[data-current-area-sqm]");
      expect(wrapper).not.toBeNull();
      expect(wrapper?.getAttribute("data-live-player-count")).toBe("1");
      expect(wrapper?.getAttribute("data-current-area-sqm")).toBe("1234567");
    });
  });
});
