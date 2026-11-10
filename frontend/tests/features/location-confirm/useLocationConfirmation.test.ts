import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/topoApi";
import { useLocationConfirmation } from "@/features/location-confirm/useLocationConfirmation";
import { createFakeTopoApi, gameState, player } from "../../fakeTopoApi";

const saved = { lat: 34, lng: 138, at: "2026-11-09T12:00:00Z" };
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
describe("useLocationConfirmation", () => {
  it("test_confirm_afterDialogAndGpsUpdate_sendsLatestPointAndKeepsIt", async () => {
    const fake = createFakeTopoApi();
    let resolve!: (value: {
      playerId: string;
      confirmed: boolean;
      confirmedAt: string;
      result: null;
    }) => void;
    fake.confirmLocation.mockReturnValue(
      new Promise((r) => {
        resolve = r;
      }),
    );
    const { result, rerender } = renderHook(
      ({ coordinate, confirmed }) =>
        useLocationConfirmation(fake.api, "game", "self", coordinate, confirmed ? saved : null),
      { initialProps: { coordinate: { lat: 35, lng: 139 }, confirmed: false } },
    );
    act(() => result.current.open());
    rerender({ coordinate: { lat: 36, lng: 140 }, confirmed: false });
    let pending!: Promise<void>;
    act(() => {
      pending = result.current.confirm();
    });
    act(() => {
      void result.current.confirm();
    });
    expect(fake.confirmLocation).toHaveBeenCalledExactlyOnceWith("game", "self", {
      lat: 36,
      lng: 140,
    });
    expect(result.current.submitting).toBe(true);
    rerender({ coordinate: { lat: 37, lng: 141 }, confirmed: false });
    await act(async () => {
      resolve({
        playerId: "self",
        confirmed: true,
        confirmedAt: "2026-11-08T01:00:00Z",
        result: null,
      });
      await pending;
    });
    expect(result.current.confirmed).toBe(true);
    expect(result.current.fixedLocation).toEqual({ lat: 36, lng: 140 });
    rerender({ coordinate: { lat: 38, lng: 142 }, confirmed: false });
    expect(result.current.confirmed).toBe(true);
    expect(result.current.fixedLocation).toEqual({ lat: 36, lng: 140 });
  });
  it("test_confirm_withServerConfirmation_restoresSavedPointWithoutUsingGps", () => {
    const fake = createFakeTopoApi();
    const { result, rerender } = renderHook(
      ({ confirmed }) =>
        useLocationConfirmation(
          fake.api,
          "game",
          "self",
          { lat: 35, lng: 139 },
          confirmed ? saved : null,
        ),
      { initialProps: { confirmed: true } },
    );
    expect(result.current.confirmed).toBe(true);
    expect(result.current.canConfirm).toBe(false);
    expect(result.current.fixedLocation).toEqual({ lat: saved.lat, lng: saved.lng });
    rerender({ confirmed: false });
    expect(result.current.confirmed).toBe(true);
  });
  it("test_confirm_withoutGps_doesNotSend", async () => {
    const fake = createFakeTopoApi();
    const { result } = renderHook(() =>
      useLocationConfirmation(fake.api, "game", "self", null, null),
    );
    await act(() => result.current.confirm());
    expect(fake.confirmLocation).not.toHaveBeenCalled();
  });
  it("test_confirm_withNonActiveConflict_doesNotMarkConfirmed", async () => {
    const fake = createFakeTopoApi();
    fake.confirmLocation.mockRejectedValue(new ApiError(409, "/confirm"));
    fake.getGameState.mockResolvedValue(
      gameState({ status: "COMPLETED", players: [player({ playerId: "self", confirmed: false })] }),
    );
    const { result } = renderHook(() =>
      useLocationConfirmation(fake.api, "game", "self", { lat: 35, lng: 139 }, null),
    );
    act(() => result.current.open());
    await act(() => result.current.confirm());
    expect(result.current.confirmed).toBe(false);
    expect(result.current.error).toBeTruthy();
    expect(result.current.canConfirm).toBe(true);
  });
  it("test_confirm_withAlreadyConfirmedConflict_restoresSavedPoint", async () => {
    const fake = createFakeTopoApi();
    fake.confirmLocation.mockRejectedValue(new ApiError(409, "/confirm"));
    fake.getGameState.mockResolvedValue(
      gameState({
        status: "ACTIVE",
        players: [player({ playerId: "self", confirmed: true, confirmedLocation: saved })],
      }),
    );
    const { result } = renderHook(() =>
      useLocationConfirmation(fake.api, "game", "self", { lat: 35, lng: 139 }, null),
    );
    act(() => result.current.open());
    await act(() => result.current.confirm());
    expect(result.current.confirmed).toBe(true);
    expect(result.current.fixedLocation).toEqual({ lat: saved.lat, lng: saved.lng });
  });
  it("test_confirm_afterNetworkFailure_allowsRetry", async () => {
    const fake = createFakeTopoApi();
    fake.confirmLocation.mockRejectedValueOnce(new TypeError("offline")).mockResolvedValue({
      playerId: "self",
      confirmed: true,
      confirmedAt: "2026-11-08T01:00:00Z",
      result: null,
    });
    const { result } = renderHook(() =>
      useLocationConfirmation(fake.api, "game", "self", { lat: 35, lng: 139 }, null),
    );
    act(() => result.current.open());
    await act(() => result.current.confirm());
    expect(result.current.confirmed).toBe(false);
    expect(result.current.error).toBeTruthy();
    act(() => result.current.open());
    await act(() => result.current.confirm());
    expect(result.current.confirmed).toBe(true);
  });

  it.each([
    ["AREA_EXCEEDS_THRESHOLD", "面積が上限を超えるため確定できません。近づいてください"],
    ["AREA_BELOW_MIN", "面積が小さすぎるため確定できません。もっと散らばってください"],
  ])("test_confirm_areaRejected_%s_showsReasonAndRetriesNewGps", async (reason, message) => {
    const fake = createFakeTopoApi();
    fake.confirmLocation
      .mockRejectedValueOnce(new ApiError(422, "/confirm", reason))
      .mockResolvedValue({
        playerId: "self",
        confirmed: true,
        confirmedAt: "2026-11-09T01:00:00Z",
        result: null,
      });
    const { result, rerender } = renderHook(
      ({ coordinate }) => useLocationConfirmation(fake.api, "game", "self", coordinate, null),
      { initialProps: { coordinate: { lat: 35, lng: 139 } } },
    );
    act(() => result.current.open());
    await act(() => result.current.confirm());
    expect(result.current.error).toBe(message);
    expect(result.current.confirmed).toBe(false);
    expect(result.current.fixedLocation).toBeNull();
    expect(result.current.canConfirm).toBe(true);
    rerender({ coordinate: { lat: 35.1, lng: 139.1 } });
    act(() => result.current.open());
    await act(() => result.current.confirm());
    expect(fake.confirmLocation).toHaveBeenLastCalledWith("game", "self", {
      lat: 35.1,
      lng: 139.1,
    });
    expect(result.current.confirmed).toBe(true);
  });
  it("test_confirm_unknown422_keepsRetryWithGeneralError", async () => {
    const fake = createFakeTopoApi();
    fake.confirmLocation.mockRejectedValue(new ApiError(422, "/confirm", "UNKNOWN"));
    const { result } = renderHook(() =>
      useLocationConfirmation(fake.api, "game", "self", { lat: 35, lng: 139 }, null),
    );
    act(() => result.current.open());
    await act(() => result.current.confirm());
    expect(result.current.error).toBe("位置の確定に失敗しました。もう一度お試しください。");
    expect(result.current.canConfirm).toBe(true);
  });

  it.each([
    "game",
    "player",
    "aba",
  ])("test_confirm_oldSessionSuccess_%s_doesNotChangeNewSessionOrResult", async (change) => {
    const fake = createFakeTopoApi();
    const response = deferred<Awaited<ReturnType<typeof fake.api.confirmLocation>>>();
    fake.confirmLocation.mockReturnValue(response.promise);
    const onResult = vi.fn();
    const { result, rerender } = renderHook(
      ({ game, playerId }) =>
        useLocationConfirmation(fake.api, game, playerId, { lat: 35, lng: 139 }, null, onResult),
      { initialProps: { game: "a", playerId: "self" } },
    );
    act(() => result.current.open());
    let pending!: Promise<void>;
    act(() => {
      pending = result.current.confirm();
    });
    rerender({
      game: change === "player" ? "a" : "b",
      playerId: change === "player" ? "other" : "self",
    });
    if (change === "aba") rerender({ game: "a", playerId: "self" });
    expect(result.current.submitting).toBe(false);
    expect(result.current.dialogOpen).toBe(false);
    act(() => result.current.open());
    await act(async () => {
      response.resolve({
        playerId: "self",
        confirmed: true,
        confirmedAt: saved.at,
        result: { areaSqm: 1, areaValid: true, objectCount: 0, polygon: [], objects: [] },
      });
      await pending;
    });
    expect(result.current.confirmed).toBe(false);
    expect(result.current.fixedLocation).toBeNull();
    expect(result.current.dialogOpen).toBe(true);
    expect(result.current.error).toBeNull();
    expect(onResult).not.toHaveBeenCalled();
  });

  it.each([
    "failure",
    "conflict",
  ])("test_confirm_oldSession_%s_doesNotApplyErrorOrRefetch", async (kind) => {
    const fake = createFakeTopoApi();
    const response = deferred<Awaited<ReturnType<typeof fake.api.confirmLocation>>>();
    fake.confirmLocation.mockReturnValue(response.promise);
    const { result, rerender } = renderHook(
      ({ game }) => useLocationConfirmation(fake.api, game, "self", { lat: 35, lng: 139 }, null),
      { initialProps: { game: "a" } },
    );
    act(() => result.current.open());
    let pending!: Promise<void>;
    act(() => {
      pending = result.current.confirm();
    });
    rerender({ game: "b" });
    await act(async () => {
      response.reject(
        kind === "failure" ? new TypeError("offline") : new ApiError(409, "/confirm"),
      );
      await pending;
    });
    expect(result.current.error).toBeNull();
    expect(fake.getGameState).not.toHaveBeenCalled();
  });

  it("test_confirm_conflictRefetchAfterAToBToA_doesNotApplyOldPointOrClearNewPending", async () => {
    const fake = createFakeTopoApi();
    fake.confirmLocation.mockRejectedValueOnce(new ApiError(409, "/confirm"));
    const refetch = deferred<ReturnType<typeof gameState>>();
    fake.getGameState.mockReturnValue(refetch.promise);
    const { result, rerender } = renderHook(
      ({ game }) => useLocationConfirmation(fake.api, game, "self", { lat: 35, lng: 139 }, null),
      { initialProps: { game: "a" } },
    );
    act(() => result.current.open());
    let oldPending!: Promise<void>;
    await act(async () => {
      oldPending = result.current.confirm();
      await Promise.resolve();
    });
    expect(fake.getGameState).toHaveBeenCalledOnce();
    rerender({ game: "b" });
    rerender({ game: "a" });
    const newResponse = deferred<Awaited<ReturnType<typeof fake.api.confirmLocation>>>();
    fake.confirmLocation.mockReturnValue(newResponse.promise);
    act(() => result.current.open());
    let newPending!: Promise<void>;
    act(() => {
      newPending = result.current.confirm();
    });
    await act(async () => {
      refetch.resolve(
        gameState({
          players: [player({ playerId: "self", confirmed: true, confirmedLocation: saved })],
        }),
      );
      await oldPending;
    });
    expect(result.current.confirmed).toBe(false);
    expect(result.current.submitting).toBe(true);
    expect(result.current.dialogOpen).toBe(true);
    await act(async () => {
      newResponse.resolve({
        playerId: "self",
        confirmed: true,
        confirmedAt: saved.at,
        result: null,
      });
      await newPending;
    });
    expect(result.current.fixedLocation).toEqual({ lat: 35, lng: 139 });
  });

  it("test_poll_savedLocationArrivesAfterMount_keepsItThroughOldUnconfirmedGetWithoutGps", () => {
    const fake = createFakeTopoApi();
    const { result, rerender } = renderHook(
      ({ savedPoint }) => useLocationConfirmation(fake.api, "game", "self", null, savedPoint),
      { initialProps: { savedPoint: null as typeof saved | null } },
    );
    expect(result.current.fixedLocation).toBeNull();
    rerender({ savedPoint: saved });
    expect(result.current.fixedLocation).toEqual({ lat: saved.lat, lng: saved.lng });
    rerender({ savedPoint: null });
    expect(result.current.confirmed).toBe(true);
    expect(result.current.fixedLocation).toEqual({ lat: saved.lat, lng: saved.lng });
  });

  it("test_confirm_responseAfterUnmount_doesNotPublishResult", async () => {
    const fake = createFakeTopoApi();
    const response = deferred<Awaited<ReturnType<typeof fake.api.confirmLocation>>>();
    fake.confirmLocation.mockReturnValue(response.promise);
    const onResult = vi.fn();
    const { result, unmount } = renderHook(() =>
      useLocationConfirmation(fake.api, "game", "self", { lat: 35, lng: 139 }, null, onResult),
    );
    act(() => result.current.open());
    let pending!: Promise<void>;
    act(() => {
      pending = result.current.confirm();
    });
    unmount();
    await act(async () => {
      response.resolve({
        playerId: "self",
        confirmed: true,
        confirmedAt: saved.at,
        result: { areaSqm: 1, areaValid: true, objectCount: 0, objects: [], polygon: [] },
      });
      await pending;
    });
    expect(onResult).not.toHaveBeenCalled();
  });
});
