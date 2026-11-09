import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ApiError } from "@/api/topoApi";
import { useLocationConfirmation } from "@/features/location-confirm/useLocationConfirmation";
import { createFakeTopoApi, gameState, player } from "../../fakeTopoApi";

describe("useLocationConfirmation", () => {
  it("test_confirm_afterDialogAndGpsUpdate_sendsLatestPointAndKeepsIt", async () => {
    const fake = createFakeTopoApi();
    let resolve!: (value: { playerId: string; confirmed: boolean; confirmedAt: string }) => void;
    fake.confirmLocation.mockReturnValue(
      new Promise((r) => {
        resolve = r;
      }),
    );
    const { result, rerender } = renderHook(
      ({ coordinate, confirmed }) =>
        useLocationConfirmation(fake.api, "game", "self", coordinate, confirmed),
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
      resolve({ playerId: "self", confirmed: true, confirmedAt: "2026-11-08T01:00:00Z" });
      await pending;
    });
    expect(result.current.confirmed).toBe(true);
    expect(result.current.fixedLocation).toEqual({ lat: 36, lng: 140 });
    rerender({ coordinate: { lat: 38, lng: 142 }, confirmed: false });
    expect(result.current.confirmed).toBe(true);
    expect(result.current.fixedLocation).toEqual({ lat: 36, lng: 140 });
  });
  it("test_confirm_withServerConfirmation_disablesWithoutInventingPoint", () => {
    const fake = createFakeTopoApi();
    const { result, rerender } = renderHook(
      ({ confirmed }) =>
        useLocationConfirmation(fake.api, "game", "self", { lat: 35, lng: 139 }, confirmed),
      { initialProps: { confirmed: true } },
    );
    expect(result.current.confirmed).toBe(true);
    expect(result.current.canConfirm).toBe(false);
    expect(result.current.fixedLocation).toBeNull();
    rerender({ confirmed: false });
    expect(result.current.confirmed).toBe(true);
  });
  it("test_confirm_withoutGps_doesNotSend", async () => {
    const fake = createFakeTopoApi();
    const { result } = renderHook(() =>
      useLocationConfirmation(fake.api, "game", "self", null, false),
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
      useLocationConfirmation(fake.api, "game", "self", { lat: 35, lng: 139 }, false),
    );
    act(() => result.current.open());
    await act(() => result.current.confirm());
    expect(result.current.confirmed).toBe(false);
    expect(result.current.error).toBeTruthy();
    expect(result.current.canConfirm).toBe(true);
  });
  it("test_confirm_withAlreadyConfirmedConflict_restoresOnlyState", async () => {
    const fake = createFakeTopoApi();
    fake.confirmLocation.mockRejectedValue(new ApiError(409, "/confirm"));
    fake.getGameState.mockResolvedValue(
      gameState({ status: "ACTIVE", players: [player({ playerId: "self", confirmed: true })] }),
    );
    const { result } = renderHook(() =>
      useLocationConfirmation(fake.api, "game", "self", { lat: 35, lng: 139 }, false),
    );
    act(() => result.current.open());
    await act(() => result.current.confirm());
    expect(result.current.confirmed).toBe(true);
    expect(result.current.fixedLocation).toBeNull();
  });
  it("test_confirm_afterNetworkFailure_allowsRetry", async () => {
    const fake = createFakeTopoApi();
    fake.confirmLocation.mockRejectedValueOnce(new TypeError("offline")).mockResolvedValue({
      playerId: "self",
      confirmed: true,
      confirmedAt: "2026-11-08T01:00:00Z",
    });
    const { result } = renderHook(() =>
      useLocationConfirmation(fake.api, "game", "self", { lat: 35, lng: 139 }, false),
    );
    act(() => result.current.open());
    await act(() => result.current.confirm());
    expect(result.current.confirmed).toBe(false);
    expect(result.current.error).toBeTruthy();
    act(() => result.current.open());
    await act(() => result.current.confirm());
    expect(result.current.confirmed).toBe(true);
  });
});
