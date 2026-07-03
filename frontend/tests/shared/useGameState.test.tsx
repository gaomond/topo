import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { SWRConfig } from "swr";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/topoApi";
import { useGameState } from "@/shared/useGameState";
import { createFakeTopoApi, gameState } from "../fakeTopoApi";

// SWR はモジュールレベルにキャッシュを持つため、テストごとに provider（新規 Map）で隔離する。
function wrapper({ children }: { children: ReactNode }) {
  return <SWRConfig value={{ provider: () => new Map() }}>{children}</SWRConfig>;
}

describe("useGameState", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("test_useGameState_withGameId_fetchesInitialState", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(gameState({ status: "WAITING" }));

    const { result } = renderHook(
      () => useGameState({ api: fake.api, gameId: "game-123", refreshIntervalMs: 5000 }),
      { wrapper },
    );

    await waitFor(() => expect(result.current.state?.status).toBe("WAITING"));
    expect(fake.getGameState).toHaveBeenCalledWith("game-123");
  });

  it("test_useGameState_pollsGameStateAtInterval", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(gameState());

    renderHook(() => useGameState({ api: fake.api, gameId: "game-123", refreshIntervalMs: 5000 }), {
      wrapper,
    });

    await waitFor(() => expect(fake.getGameState).toHaveBeenCalledTimes(1));

    // 間隔を進めると再フェッチされる（SWR refreshInterval）。
    await vi.advanceTimersByTimeAsync(5000);
    await waitFor(() => expect(fake.getGameState).toHaveBeenCalledTimes(2));
    await vi.advanceTimersByTimeAsync(5000);
    await waitFor(() => expect(fake.getGameState).toHaveBeenCalledTimes(3));
  });

  it("test_useGameState_withoutGameId_doesNotFetch", () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockResolvedValue(gameState());

    renderHook(() => useGameState({ api: fake.api, gameId: undefined }), { wrapper });

    expect(fake.getGameState).not.toHaveBeenCalled();
  });

  it("test_useGameState_onNotFound_exposesError", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockRejectedValue(new ApiError(404, "/api/games/game-x"));

    const { result } = renderHook(
      () => useGameState({ api: fake.api, gameId: "game-x", refreshIntervalMs: 5000 }),
      { wrapper },
    );

    await waitFor(() => expect(result.current.error).toBeInstanceOf(ApiError));
    expect((result.current.error as ApiError).status).toBe(404);
  });
});
