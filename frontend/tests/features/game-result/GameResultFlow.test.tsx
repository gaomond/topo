import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Link, MemoryRouter, Route, Routes } from "react-router-dom";
import { SWRConfig } from "swr";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ConfirmLocationResponse, GameStateResponse } from "@/api/types";
import { WaitingRoomContainer } from "@/features/waiting-room/WaitingRoomContainer";
import { createFakeGeolocation } from "../../fakeGeolocation";
import { createFakeTopoApi, gameState, player } from "../../fakeTopoApi";

vi.mock("@/features/game-result/GameResultMap", () => ({
  GameResultMap: () => <div data-testid="result-map" />,
}));
const result = {
  areaSqm: 500,
  areaValid: true,
  objectCount: 0,
  polygon: [
    { lat: 35, lng: 139 },
    { lat: 35.01, lng: 139 },
    { lat: 35, lng: 139.01 },
    { lat: 35, lng: 139 },
  ],
};
const members = [player({ playerId: "self" }), player({ playerId: "other" })];
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function setup(
  url = "/game/first?p=self",
  initialState = gameState({ gameId: "first", status: "ACTIVE", players: members }),
) {
  const fake = createFakeTopoApi();
  const gps = createFakeGeolocation();
  fake.getGameState.mockResolvedValue(initialState);
  const view = render(
    <SWRConfig value={{ provider: () => new Map() }}>
      <MemoryRouter initialEntries={[url]}>
        <Link to="/game/second?p=self">別ゲーム</Link>
        <Link to="/game/first?p=other">別プレイヤー</Link>
        <Routes>
          <Route
            path="/game/:gameId"
            element={
              <WaitingRoomContainer
                api={fake.api}
                geoDeps={{ geolocation: gps, isSecureContext: true }}
                refreshIntervalMs={2000}
              />
            }
          />
          <Route path="/" element={<h1>作成画面</h1>} />
        </Routes>
      </MemoryRouter>
    </SWRConfig>,
  );
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTimeAsync });
  return { fake, gps, view, user };
}
async function submit(
  gps: ReturnType<typeof createFakeGeolocation>,
  user: ReturnType<typeof userEvent.setup>,
) {
  await waitFor(() => expect(gps.activeWatchCount()).toBe(1));
  act(() => gps.emitSuccess({ latitude: 35, longitude: 139 }));
  await user.click(screen.getByRole("button", { name: "この位置で確定" }));
  await user.click(screen.getByRole("button", { name: "確定する" }));
}
describe("GameResultFlow", () => {
  beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
  afterEach(() => vi.useRealTimers());
  it("test_confirm_finalResult_showsImmediatelyAndStopsGpsAndSendingDespiteLateActive", async () => {
    const { fake, gps, user } = setup();
    const late = deferred<GameStateResponse>();
    fake.confirmLocation.mockResolvedValue({
      playerId: "self",
      confirmed: true,
      confirmedAt: "2026-11-09T00:00:00Z",
      result,
    });
    await waitFor(() => expect(gps.activeWatchCount()).toBe(1));
    fake.getGameState.mockReturnValue(late.promise);
    await act(() => vi.advanceTimersByTimeAsync(2000));
    await submit(gps, user);
    expect(await screen.findByRole("heading", { name: "ゲーム完了" })).toBeInTheDocument();
    expect(gps.activeWatchCount()).toBe(0);
    expect(gps.clearWatch).toHaveBeenCalled();
    const sends = fake.updateLocation.mock.calls.length;
    await act(async () => {
      late.resolve(gameState({ status: "ACTIVE", players: members }));
    });
    await act(() => vi.advanceTimersByTimeAsync(5000));
    expect(screen.getByRole("heading", { name: "ゲーム完了" })).toBeInTheDocument();
    expect(fake.updateLocation).toHaveBeenCalledTimes(sends);
    await user.click(screen.getByRole("link", { name: "新しいゲームを作成" }));
    expect(screen.getByRole("heading", { name: "作成画面" })).toBeInTheDocument();
  });
  it.each([
    "別ゲーム",
    "別プレイヤー",
  ])("test_confirm_oldSession_doesNotShowResultInNewSession %s", async (link) => {
    const { fake, gps, user } = setup();
    const response = deferred<ConfirmLocationResponse>();
    fake.confirmLocation.mockReturnValue(response.promise);
    await submit(gps, user);
    await user.click(screen.getByRole("link", { name: link }));
    await act(async () =>
      response.resolve({
        playerId: "self",
        confirmed: true,
        confirmedAt: "2026-11-09T00:00:00Z",
        result,
      }),
    );
    expect(screen.queryByRole("heading", { name: "ゲーム完了" })).not.toBeInTheDocument();
  });
  it("test_poll_completedThenLateActive_keepsResultAndStopsGps", async () => {
    const { fake, gps } = setup();
    await waitFor(() => expect(gps.activeWatchCount()).toBe(1));
    fake.getGameState
      .mockResolvedValueOnce(gameState({ status: "COMPLETED", result, players: members }))
      .mockResolvedValue(gameState({ status: "ACTIVE", players: members }));
    await act(() => vi.advanceTimersByTimeAsync(2000));
    expect(await screen.findByRole("heading", { name: "ゲーム完了" })).toBeInTheDocument();
    await act(() => vi.advanceTimersByTimeAsync(3000));
    expect(screen.getByRole("heading", { name: "ゲーム完了" })).toBeInTheDocument();
    expect(gps.activeWatchCount()).toBe(0);
  });
  it.each([
    result,
    null,
  ])("test_revisit_completedGame_doesNotStartGpsOrReplayActions %j", async (savedResult) => {
    const { fake, gps } = setup(
      "/game/first?p=self",
      gameState({ status: "COMPLETED", result: savedResult, players: members }),
    );
    if (savedResult === null)
      expect(await screen.findByRole("alert")).toHaveTextContent("結果を取得できませんでした");
    else expect(await screen.findByRole("heading", { name: "ゲーム完了" })).toBeInTheDocument();
    expect(gps.watchPosition).not.toHaveBeenCalled();
    expect(fake.joinGame).not.toHaveBeenCalled();
    expect(fake.confirmLocation).not.toHaveBeenCalled();
  });
});
