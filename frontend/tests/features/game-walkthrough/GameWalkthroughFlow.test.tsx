import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { SWRConfig } from "swr";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { WaitingRoomContainer } from "@/features/waiting-room/WaitingRoomContainer";
import { createFakeGeolocation } from "../../fakeGeolocation";
import { createFakeTopoApi, gameState, player } from "../../fakeTopoApi";

function setup(initiallyFinished = false) {
  const api = createFakeTopoApi();
  const gps = createFakeGeolocation();
  const storage = { getItem: vi.fn(() => (initiallyFinished ? "true" : null)), setItem: vi.fn() };
  const state = gameState({ status: "ACTIVE", players: [player({ playerId: "self" })] });
  api.getGameState.mockResolvedValue(state);
  const view = render(
    <SWRConfig value={{ provider: () => new Map() }}>
      <MemoryRouter initialEntries={["/game/first?p=self"]}>
        <Routes>
          <Route
            path="/game/:gameId"
            element={
              <WaitingRoomContainer
                api={api.api}
                geoDeps={{ geolocation: gps, isSecureContext: true }}
                getWalkthroughStorage={() => storage}
                refreshIntervalMs={100}
              />
            }
          />
        </Routes>
      </MemoryRouter>
    </SWRConfig>,
  );
  return {
    api,
    gps,
    storage,
    state,
    view,
    user: userEvent.setup({ advanceTimers: vi.advanceTimersByTimeAsync }),
  };
}

describe("GameWalkthroughFlow", () => {
  beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
  afterEach(() => vi.useRealTimers());
  it("test_firstActive_introNext_startsGpsAndSendingWithoutRecordingOrRestarting", async () => {
    const { api, gps, storage, user } = setup();
    expect(await screen.findByRole("alertdialog")).toHaveTextContent("みんなで街を囲もう");
    expect(gps.watchPosition).not.toHaveBeenCalled();
    expect(api.updateLocation).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "次へ" }));
    await waitFor(() => expect(gps.watchPosition).toHaveBeenCalledTimes(1));
    act(() => gps.emitSuccess({ latitude: 35, longitude: 139 }));
    await waitFor(() => expect(document.querySelector(".leaflet-marker-icon")).not.toBeNull());
    await act(() => vi.advanceTimersByTimeAsync(2000));
    await waitFor(() => expect(api.updateLocation).toHaveBeenCalledTimes(1));
    await user.click(await screen.findByRole("button", { name: "戻る" }));
    expect(storage.setItem).not.toHaveBeenCalled();
    expect(gps.watchPosition).toHaveBeenCalledTimes(1);
    await user.click(screen.getByRole("button", { name: "次へ" }));
    expect(gps.watchPosition).toHaveBeenCalledTimes(1);
    await user.click(await screen.findByRole("button", { name: "閉じる" }));
    await waitFor(() => expect(gps.watchPosition).toHaveBeenCalledTimes(1));
    expect(storage.setItem).toHaveBeenCalledTimes(1);
  });
  it("test_manualOpen_alreadyTracking_keepsWatchAndSendingAndPolling", async () => {
    const { api, gps, storage, user } = setup(true);
    await waitFor(() => expect(gps.activeWatchCount()).toBe(1));
    act(() => gps.emitSuccess({ latitude: 35, longitude: 139 }));
    await act(() => vi.advanceTimersByTimeAsync(2000));
    await waitFor(() => expect(api.updateLocation).toHaveBeenCalledTimes(1));
    await user.click(screen.getByRole("button", { name: "遊び方" }));
    expect(await screen.findByRole("alertdialog")).toHaveTextContent("みんなで街を囲もう");
    const polls = api.getGameState.mock.calls.length;
    await act(() => vi.advanceTimersByTimeAsync(2100));
    act(() => gps.emitSuccess({ latitude: 35.01, longitude: 139 }));
    await waitFor(() => expect(api.updateLocation.mock.calls.length).toBeGreaterThan(1));
    expect(api.getGameState.mock.calls.length).toBeGreaterThan(polls);
    expect(gps.watchPosition).toHaveBeenCalledTimes(1);
    expect(gps.clearWatch).not.toHaveBeenCalled();
    expect(storage.setItem).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "スキップ" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "遊び方" })).toHaveFocus());
  });
  it("test_firstGuide_permissionDeniedDuringMap_keepsExplanationAndExit", async () => {
    const { gps, storage, user } = setup();
    await user.click(await screen.findByRole("button", { name: "次へ" }));
    await waitFor(() => expect(gps.activeWatchCount()).toBe(1));
    act(() => gps.emitError(1));
    await waitFor(() => {
      expect(screen.getByRole("alertdialog", { name: "地図で仲間と範囲を確認" })).toHaveTextContent(
        "2 / 4",
      );
      expect(screen.queryByTestId("arrow")).not.toBeInTheDocument();
    });
    expect(storage.setItem).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "閉じる" }));
    expect(
      screen.getByRole("alertdialog", { name: "位置情報を取得できません" }),
    ).toBeInTheDocument();
    expect(gps.watchPosition).toHaveBeenCalledTimes(1);
  });
  it("test_firstGuide_completedAfterIntro_clearsWatchWithoutRecording", async () => {
    const { api, gps, storage, state, user } = setup();
    await user.click(await screen.findByRole("button", { name: "次へ" }));
    await waitFor(() => expect(gps.activeWatchCount()).toBe(1));
    api.getGameState.mockResolvedValue({ ...state, status: "COMPLETED" });
    await act(() => vi.advanceTimersByTimeAsync(200));
    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
    expect(gps.activeWatchCount()).toBe(0);
    expect(storage.setItem).not.toHaveBeenCalled();
    expect(gps.watchPosition).toHaveBeenCalledTimes(1);
  });
  it("test_currentTarget_removedDuringFirstGuide_doesNotRecordOrRestartGps", async () => {
    const { api, gps, storage, user } = setup();
    await user.click(await screen.findByRole("button", { name: "次へ" }));
    await screen.findByRole("alertdialog", { name: "地図で仲間と範囲を確認" });
    await waitFor(() => expect(screen.getByTestId("arrow")).toBeInTheDocument());
    act(() => document.querySelector('[data-walkthrough="map"]')?.remove());
    await waitFor(() => {
      expect(screen.getByRole("alertdialog")).toHaveTextContent("2 / 4");
      expect(screen.queryByTestId("arrow")).not.toBeInTheDocument();
    });
    await act(() => vi.advanceTimersByTimeAsync(300));
    expect(storage.setItem).not.toHaveBeenCalled();
    expect(gps.watchPosition).toHaveBeenCalledTimes(1);
    expect(api.updateLocation).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "閉じる" })).toBeInTheDocument();
  });
  it("test_manualGuide_permissionDeniedDuringMap_keepsExplanationUntilExplicitExit", async () => {
    const { gps, storage, user } = setup(true);
    await waitFor(() => expect(gps.activeWatchCount()).toBe(1));
    await user.click(screen.getByRole("button", { name: "遊び方" }));
    await user.click(await screen.findByRole("button", { name: "次へ" }));
    await screen.findByRole("alertdialog", { name: "地図で仲間と範囲を確認" });
    await waitFor(() => expect(screen.getByTestId("arrow")).toBeInTheDocument());
    act(() => gps.emitError(1));
    await waitFor(() => {
      expect(screen.getByRole("alertdialog", { name: "地図で仲間と範囲を確認" })).toHaveTextContent(
        "2 / 4",
      );
      expect(screen.queryByTestId("arrow")).not.toBeInTheDocument();
    });
    expect(storage.setItem).not.toHaveBeenCalled();
    expect(gps.watchPosition).toHaveBeenCalledTimes(1);
    await user.click(screen.getByRole("button", { name: "閉じる" }));
    await waitFor(() => expect(storage.setItem).toHaveBeenCalledTimes(1));
    expect(gps.watchPosition).toHaveBeenCalledTimes(1);
  });
  it("test_pollCompleted_unfinishedGuide_unmountsWithoutRecordingOrStartingGps", async () => {
    const { api, gps, storage, state } = setup();
    await screen.findByRole("alertdialog");
    api.getGameState.mockResolvedValue({ ...state, status: "COMPLETED" });
    await act(() => vi.advanceTimersByTimeAsync(200));
    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
    expect(storage.setItem).not.toHaveBeenCalled();
    expect(gps.watchPosition).not.toHaveBeenCalled();
    expect(api.updateLocation).not.toHaveBeenCalled();
  });
  it("test_unmount_unfinishedGuide_doesNotRecordOrStartGps", async () => {
    const { gps, storage, view } = setup();
    await screen.findByRole("alertdialog");
    view.unmount();
    expect(storage.setItem).not.toHaveBeenCalled();
    expect(gps.watchPosition).not.toHaveBeenCalled();
  });
});
