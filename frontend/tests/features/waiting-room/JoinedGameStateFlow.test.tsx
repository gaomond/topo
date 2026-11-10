import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Link, MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { SWRConfig } from "swr";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/topoApi";
import type { GameStateResponse, JoinGameResponse } from "@/api/types";
import { WaitingRoomContainer } from "@/features/waiting-room/WaitingRoomContainer";
import { createFakeGeolocation } from "../../fakeGeolocation";
import { createFakeTopoApi, gameState, player } from "../../fakeTopoApi";

vi.mock("@/features/game-result/GameResultMap", () => ({
  GameResultMap: () => <div>結果地図</div>,
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
const joinedState = (overrides: Partial<GameStateResponse> = {}) =>
  gameState({
    players: [
      player({ playerId: "joined", displayName: "参加した人" }),
      player({ playerId: "other" }),
    ],
    ...overrides,
  });
function Location() {
  const location = useLocation();
  return (
    <output data-testid="location">
      {location.pathname}
      {location.search}
    </output>
  );
}
function setup() {
  const fake = createFakeTopoApi();
  const gps = createFakeGeolocation();
  fake.getGameState.mockResolvedValueOnce(gameState({ players: [player({ playerId: "other" })] }));
  fake.joinGame.mockResolvedValue({ playerId: "joined" });
  render(
    <SWRConfig value={{ provider: () => new Map(), focusThrottleInterval: 0 }}>
      <MemoryRouter initialEntries={["/game/game-123"]}>
        <Location />
        <Link to="/game/second?p=other">別ゲーム</Link>
        <Link to="/game/game-123?p=other">別プレイヤー</Link>
        <Routes>
          <Route
            path="/game/:gameId"
            element={
              <WaitingRoomContainer
                api={fake.api}
                refreshIntervalMs={2000}
                geoDeps={{ geolocation: gps, isSecureContext: true }}
                getWalkthroughStorage={() => ({ getItem: () => "true", setItem: () => undefined })}
              />
            }
          />
        </Routes>
      </MemoryRouter>
    </SWRConfig>,
  );
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTimeAsync });
  return {
    fake,
    gps,
    user,
    join: async () => user.click(await screen.findByRole("button", { name: "参加する" })),
  };
}

describe("参加成功後の状態確認", () => {
  beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
  afterEach(() => vi.useRealTimers());

  it("test_join_getPending_keepsJoinedUrlAndLoadingWithoutNotFound", async () => {
    const { fake, gps, join } = setup();
    const pending = deferred<GameStateResponse>();
    fake.getGameState.mockImplementation(() => pending.promise);
    await join();
    await waitFor(() => expect(fake.getGameState).toHaveBeenCalledTimes(2));
    expect(screen.getByTestId("location")).toHaveTextContent("?p=joined");
    expect(screen.getByText("読み込み中…")).toBeInTheDocument();
    expect(screen.queryByText("ゲームが見つかりません")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "参加する" })).not.toBeInTheDocument();
    expect(gps.watchPosition).not.toHaveBeenCalled();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(4000);
      window.dispatchEvent(new Event("focus"));
      window.dispatchEvent(new Event("online"));
      await vi.advanceTimersByTimeAsync(100);
    });
    expect(fake.getGameState).toHaveBeenCalledTimes(2);
    await act(async () => pending.resolve(joinedState()));
    expect(await screen.findByLabelText("参加者一覧")).toHaveTextContent("参加した人");
  });

  it("test_join_preJoinGetArrivesLast_doesNotReuseOrRestoreOldPlayers", async () => {
    const { fake, join } = setup();
    const old = deferred<GameStateResponse>();
    const fresh = deferred<GameStateResponse>();
    await screen.findByRole("button", { name: "参加する" });
    fake.getGameState
      .mockImplementationOnce(() => old.promise)
      .mockImplementation(() => fresh.promise);
    await act(async () => vi.advanceTimersByTimeAsync(2000));
    expect(fake.getGameState).toHaveBeenCalledTimes(2);
    await join();
    await waitFor(() => expect(fake.getGameState).toHaveBeenCalledTimes(3));
    expect(screen.getByText("読み込み中…")).toBeInTheDocument();
    await act(async () => fresh.resolve(joinedState()));
    expect(await screen.findByLabelText("参加者一覧")).toHaveTextContent("参加した人");
    await act(async () => old.resolve(gameState()));
    expect(screen.queryByText("ゲームが見つかりません")).not.toBeInTheDocument();
    expect(screen.getByLabelText("参加者一覧")).toHaveTextContent("参加した人");
  });

  it("test_join_getFailure_retriesOnlyGetAndPausesAutomaticRequests", async () => {
    const { fake, user, join } = setup();
    fake.getGameState.mockRejectedValueOnce(new Error("通信断"));
    await join();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "参加は完了しましたが、ゲーム状態を取得できませんでした",
    );
    expect(screen.getByTestId("location")).toHaveTextContent("?p=joined");
    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
      window.dispatchEvent(new Event("focus"));
      window.dispatchEvent(new Event("online"));
      await vi.advanceTimersByTimeAsync(100);
    });
    expect(fake.getGameState).toHaveBeenCalledTimes(2);
    expect(fake.joinGame).toHaveBeenCalledTimes(1);
    const pending = deferred<GameStateResponse>();
    fake.getGameState.mockImplementation(() => pending.promise);
    await user.click(screen.getByRole("button", { name: "再試行" }));
    expect(screen.getByText("読み込み中…")).toBeInTheDocument();
    expect(fake.getGameState).toHaveBeenCalledTimes(3);
    await act(async () => pending.resolve(joinedState()));
    expect(await screen.findByLabelText("参加者一覧")).toHaveTextContent("参加した人");
    fake.getGameState.mockResolvedValue(joinedState());
    await act(async () => vi.advanceTimersByTimeAsync(2000));
    await waitFor(() => expect(fake.getGameState).toHaveBeenCalledTimes(4));
    expect(fake.joinGame).toHaveBeenCalledTimes(1);
  });

  it.each(["missing", "404"])("test_join_get%s_showsRealNotFound", async (condition) => {
    const { fake, join } = setup();
    if (condition === "404") fake.getGameState.mockRejectedValueOnce(new ApiError(404, "/game"));
    else fake.getGameState.mockResolvedValueOnce(gameState());
    await join();
    expect(await screen.findByRole("alert")).toHaveTextContent("ゲームが見つかりません");
    expect(screen.queryByRole("button", { name: "再試行" })).not.toBeInTheDocument();
  });

  it("test_join_getActive_startsPlayOnlyAfterFreshResponse", async () => {
    const { fake, gps, join } = setup();
    fake.getGameState.mockResolvedValueOnce(joinedState({ status: "ACTIVE" }));
    await join();
    await waitFor(() => expect(gps.watchPosition).toHaveBeenCalledTimes(1));
    expect(screen.queryByText("ゲームが見つかりません")).not.toBeInTheDocument();
  });

  it("test_join_retryFailsAgain_keepsUrlAndAllowsAnotherGet", async () => {
    const { fake, user, join } = setup();
    fake.getGameState.mockRejectedValue(new Error("通信断"));
    await join();
    await user.click(await screen.findByRole("button", { name: "再試行" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("参加は完了しましたが");
    expect(fake.getGameState).toHaveBeenCalledTimes(3);
    expect(screen.getByTestId("location")).toHaveTextContent("?p=joined");
    fake.getGameState.mockResolvedValue(joinedState());
    await user.click(screen.getByRole("button", { name: "再試行" }));
    expect(await screen.findByLabelText("参加者一覧")).toHaveTextContent("参加した人");
    expect(fake.joinGame).toHaveBeenCalledTimes(1);
  });

  it("test_join_oldGetSuccessAfterPlayerSwitch_doesNotPublishOldSnapshot", async () => {
    const { fake, user, join } = setup();
    const pending = deferred<GameStateResponse>();
    fake.getGameState.mockImplementation(() => pending.promise);
    await join();
    await waitFor(() => expect(fake.getGameState).toHaveBeenCalledTimes(2));
    await user.click(screen.getByRole("link", { name: "別プレイヤー" }));
    await act(async () => pending.resolve(gameState()));
    expect(screen.getByTestId("location")).toHaveTextContent("p=other");
    expect(screen.getByLabelText("参加者一覧")).toBeInTheDocument();
    expect(screen.queryByText("ゲームが見つかりません")).not.toBeInTheDocument();
  });

  it("test_join_getCompleted_showsStoredResult", async () => {
    const { fake, gps, join } = setup();
    fake.getGameState.mockResolvedValueOnce(
      joinedState({
        status: "COMPLETED",
        result: {
          areaSqm: 100,
          areaValid: true,
          objectCount: 0,
          objects: [],
          polygon: [
            { lat: 35, lng: 139 },
            { lat: 35.01, lng: 139 },
            { lat: 35, lng: 139.01 },
            { lat: 35, lng: 139 },
          ],
        },
      }),
    );
    await join();
    expect(await screen.findByText("ゲーム完了")).toBeInTheDocument();
    expect(gps.watchPosition).not.toHaveBeenCalled();
  });

  it("test_join_oldPostAfterPlayerSwitch_doesNotNavigate", async () => {
    const { fake, user, join } = setup();
    const post = deferred<JoinGameResponse>();
    fake.joinGame.mockImplementation(() => post.promise);
    await join();
    await user.click(screen.getByRole("link", { name: "別プレイヤー" }));
    await act(async () => post.resolve({ playerId: "joined" }));
    expect(screen.getByTestId("location")).toHaveTextContent("?p=other");
    expect(fake.getGameState).toHaveBeenCalledTimes(1);
  });

  it.each([
    "別ゲーム",
    "別プレイヤー",
  ])("test_join_oldGetAfter%s_doesNotCarryJoinFlow", async (link) => {
    const { fake, user, join } = setup();
    const pending = deferred<GameStateResponse>();
    fake.getGameState.mockImplementation(() => pending.promise);
    await join();
    await waitFor(() => expect(fake.getGameState).toHaveBeenCalledTimes(2));
    fake.getGameState.mockResolvedValue(joinedState());
    await user.click(screen.getByRole("link", { name: link }));
    await act(async () => pending.reject(new Error("古い取得失敗")));
    expect(screen.getByTestId("location")).toHaveTextContent("p=other");
    expect(
      screen.queryByText("参加は完了しましたが、ゲーム状態を取得できませんでした"),
    ).not.toBeInTheDocument();
    expect(screen.queryByText("読み込み中…")).not.toBeInTheDocument();
  });
});
