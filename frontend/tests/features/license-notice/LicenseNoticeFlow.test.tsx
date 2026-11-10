import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import { SWRConfig } from "swr";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "@/App";
import { ApiError } from "@/api/topoApi";
import type { GameStateResponse } from "@/api/types";
import { createFakeGeolocation } from "../../fakeGeolocation";
import { createFakeTopoApi, gameState, player } from "../../fakeTopoApi";

const result = {
  areaSqm: 100,
  areaValid: true,
  objectCount: 0,
  objects: [],
  polygon: [
    { lat: 35, lng: 139 },
    { lat: 35.001, lng: 139 },
    { lat: 35, lng: 139.001 },
    { lat: 35, lng: 139 },
  ],
};
function CurrentUrl() {
  const location = useLocation();
  return (
    <output aria-label="URL">
      {location.pathname}
      {location.search}
    </output>
  );
}
function setup({
  status = "ACTIVE",
  finished = true,
  url = "/game/first?p=self",
  failure,
  loading = false,
}: {
  status?: GameStateResponse["status"];
  finished?: boolean;
  url?: string;
  failure?: ApiError;
  loading?: boolean;
} = {}) {
  const api = createFakeTopoApi();
  api.fetchConfig.mockResolvedValue({
    objectTypes: ["shrine"],
    areaPresets: [{ key: "small", label: "お手軽", sqm: 500000 }],
  });
  if (url === "/" && loading) api.fetchConfig.mockReturnValue(new Promise(() => {}));
  if (url === "/" && failure) api.fetchConfig.mockRejectedValue(failure);
  const gps = createFakeGeolocation();
  const storage = { getItem: vi.fn(() => (finished ? "true" : null)), setItem: vi.fn() };
  const state = gameState({
    status,
    players: [player({ playerId: "self" })],
    result: status === "COMPLETED" ? result : null,
  });
  if (failure) api.getGameState.mockRejectedValue(failure);
  else if (loading) api.getGameState.mockReturnValue(new Promise(() => {}));
  else api.getGameState.mockResolvedValue(state);
  render(
    <SWRConfig value={{ provider: () => new Map(), shouldRetryOnError: false }}>
      <MemoryRouter initialEntries={[url]}>
        <CurrentUrl />
        <App
          api={api.api}
          geoDeps={{ geolocation: gps, isSecureContext: true }}
          getWalkthroughStorage={() => storage}
          refreshIntervalMs={100}
        />
      </MemoryRouter>
    </SWRConfig>,
  );
  return {
    api,
    gps,
    storage,
    state,
    user: userEvent.setup({ advanceTimers: vi.advanceTimersByTimeAsync }),
  };
}
const entry = () => screen.getByRole("button", { name: "licences" });
const notice = () => screen.getByRole("dialog", { name: "Licences" });

describe("LicenseNoticeFlow", () => {
  beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
  afterEach(() => vi.useRealTimers());
  it.each([
    { name: "作成", url: "/" },
    { name: "設定読込", url: "/", loading: true },
    { name: "設定エラー", url: "/", failure: new ApiError(500, "config") },
    { name: "待機", status: "WAITING" },
  ] as const)("test_open_$name_rendersCommonNoticeWithoutActions", async (options) => {
    const { api, gps, user } = setup(options);
    await act(() => vi.advanceTimersByTimeAsync(50));
    await user.click(entry());
    expect(notice()).toBeInTheDocument();
    expect(api.createGame).not.toHaveBeenCalled();
    expect(api.joinGame).not.toHaveBeenCalled();
    expect(api.confirmLocation).not.toHaveBeenCalled();
    expect(gps.watchPosition).not.toHaveBeenCalled();
  });
  it.each([
    { name: "参加", status: "WAITING", url: "/game/first" },
    { name: "プレイ", status: "ACTIVE" },
    { name: "結果", status: "COMPLETED" },
    { name: "読込", loading: true },
    { name: "404", failure: new ApiError(404, "not found") },
    { name: "通信エラー", failure: new ApiError(500, "error") },
    { name: "参加不可", url: "/game/first" },
    { name: "不明プレイヤー", url: "/game/first?p=unknown" },
  ] as const)("test_entry_$name_doesNotRenderFooter", async (options) => {
    setup(options);
    await act(() => vi.advanceTimersByTimeAsync(50));
    expect(screen.queryByRole("button", { name: "licences" })).not.toBeInTheDocument();
    expect(screen.queryByRole("contentinfo")).not.toBeInTheDocument();
  });
  it("test_close_enteredCreateForm_preservesValueAndUrl", async () => {
    const url = "/";
    const { api, user } = setup({ status: "WAITING", url });
    const name = await screen.findByRole("textbox");
    await user.type(name, "入力を保持");
    await user.click(entry());
    await user.click(screen.getByRole("button", { name: "閉じる" }));
    expect(name).toHaveValue("入力を保持");
    expect(screen.getByLabelText("URL")).toHaveTextContent(url);
    expect(api.createGame).not.toHaveBeenCalled();
    expect(api.joinGame).not.toHaveBeenCalled();
  });
  it("test_license_waitingBecomesActive_defersUnreadGuideAndGpsUntilClose", async () => {
    const { api, gps, storage, state, user } = setup({ status: "WAITING", finished: false });
    await screen.findByRole("heading", { name: "待機中" });
    await user.click(entry());
    api.getGameState.mockResolvedValue({ ...state, status: "ACTIVE" });
    await act(() => vi.advanceTimersByTimeAsync(200));
    expect(notice()).toBeInTheDocument();
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(gps.watchPosition).not.toHaveBeenCalled();
    expect(storage.setItem).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "閉じる" }));
    expect(
      await screen.findByRole("alertdialog", { name: "みんなで街を囲もう" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("contentinfo")).not.toBeInTheDocument();
    expect(gps.watchPosition).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "スキップ" }));
    expect(screen.queryByRole("contentinfo")).not.toBeInTheDocument();
  });
  it("test_license_waitingToActiveAndCompleted_preservesProgressWithoutFooter", async () => {
    const { api, gps, storage, state, user } = setup({ status: "WAITING" });
    await user.click(await screen.findByRole("button", { name: "licences" }));
    api.getGameState.mockResolvedValue({ ...state, status: "ACTIVE" });
    await act(() => vi.advanceTimersByTimeAsync(200));
    await waitFor(() => expect(gps.activeWatchCount()).toBe(1));
    act(() => gps.emitSuccess({ latitude: 35, longitude: 139 }));
    expect(notice()).toBeInTheDocument();
    expect(screen.queryByRole("contentinfo")).not.toBeInTheDocument();
    const polls = api.getGameState.mock.calls.length;
    await act(() => vi.advanceTimersByTimeAsync(2100));
    act(() => gps.emitSuccess({ latitude: 35.001, longitude: 139 }));
    await act(() => vi.advanceTimersByTimeAsync(2100));
    expect(api.updateLocation.mock.calls.length).toBeGreaterThan(1);
    expect(api.getGameState.mock.calls.length).toBeGreaterThan(polls);
    expect(gps.watchPosition).toHaveBeenCalledTimes(1);
    expect(gps.clearWatch).not.toHaveBeenCalled();
    expect(storage.setItem).not.toHaveBeenCalled();
    expect(screen.getByLabelText("URL")).toHaveTextContent("/game/first?p=self");
    api.getGameState.mockResolvedValue({ ...state, status: "COMPLETED", result });
    await act(() => vi.advanceTimersByTimeAsync(200));
    expect(notice()).toBeInTheDocument();
    expect(gps.activeWatchCount()).toBe(0);
    const sends = api.updateLocation.mock.calls.length;
    await act(() => vi.advanceTimersByTimeAsync(3000));
    expect(api.updateLocation).toHaveBeenCalledTimes(sends);
    await user.click(screen.getByRole("button", { name: "閉じる" }));
    expect(screen.getByRole("heading", { name: "ゲーム完了" })).toBeInTheDocument();
    expect(gps.watchPosition).toHaveBeenCalledTimes(1);
  });
  it("test_entry_confirmOrManualGuide_remainsAbsent", async () => {
    const { api, gps, user } = setup();
    await waitFor(() => expect(gps.activeWatchCount()).toBe(1));
    act(() => gps.emitSuccess({ latitude: 35, longitude: 139 }));
    await user.click(screen.getByRole("button", { name: "この位置で確定" }));
    expect(screen.queryByRole("contentinfo")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "キャンセル" }));
    expect(screen.queryByRole("contentinfo")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "遊び方" }));
    await screen.findByRole("alertdialog", { name: "みんなで街を囲もう" });
    expect(screen.queryByRole("contentinfo")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "スキップ" }));
    expect(screen.queryByRole("contentinfo")).not.toBeInTheDocument();
    expect(api.confirmLocation).not.toHaveBeenCalled();
  });
  it("test_entry_gpsPermissionError_remainsAbsent", async () => {
    const { gps } = setup();
    await waitFor(() => expect(gps.activeWatchCount()).toBe(1));
    act(() => gps.emitError(1));
    await screen.findByRole("alertdialog", { name: "位置情報を取得できません" });
    expect(screen.queryByRole("contentinfo")).not.toBeInTheDocument();
    expect(gps.watchPosition).toHaveBeenCalledTimes(1);
  });
});
