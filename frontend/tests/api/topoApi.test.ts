import { describe, expect, it, vi } from "vitest";
import { ApiError, createTopoApi } from "@/api/topoApi";
import type {
  ConfigResponse,
  CreateGameResponse,
  GameStateResponse,
  JoinGameResponse,
} from "@/api/types";

// fetch を注入して API ラッパの入出力・エラー変換を検証する（実 HTTP に依存しない）。
function jsonResponse(body: unknown, ok = true, status = 200): Response {
  return {
    ok,
    status,
    json: async () => body,
  } as Response;
}

// 204 No Content（ボディなし）。json を呼ぶと失敗するようにして「読まない」ことを保証する。
function noContentResponse(ok = true, status = 204): Response {
  return {
    ok,
    status,
    json: async (): Promise<unknown> => {
      throw new Error("204 のボディを読んではいけない");
    },
  } as Response;
}

describe("createTopoApi", () => {
  it("test_fetchConfig_returnsObjectTypesAndAreaPresets", async () => {
    const config: ConfigResponse = {
      objectTypes: ["shrine"],
      areaPresets: [{ key: "medium", label: "ふつう", sqm: 2_000_000 }],
    };
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(config));
    const api = createTopoApi({ baseUrl: "http://api.test", fetchImpl });

    const result = await api.fetchConfig();

    expect(result).toEqual(config);
    expect(fetchImpl).toHaveBeenCalledWith("http://api.test/api/config", undefined);
  });

  it("test_createGame_onSuccess_returnsGameIdAndPlayerId", async () => {
    const created: CreateGameResponse = { gameId: "g-1", playerId: "p-1" };
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(created));
    const api = createTopoApi({ baseUrl: "http://api.test", fetchImpl });

    const result = await api.createGame({
      objectType: "shrine",
      areaPreset: "medium",
      playerCount: 3,
      displayName: "たろう",
    });

    expect(result).toEqual(created);
    const [url, init] = fetchImpl.mock.calls[0];
    expect(url).toBe("http://api.test/api/games");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(init?.body as string)).toEqual({
      objectType: "shrine",
      areaPreset: "medium",
      playerCount: 3,
      displayName: "たろう",
    });
  });

  it("test_createGame_onErrorStatus_throws", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({}, false, 400));
    const api = createTopoApi({ baseUrl: "http://api.test", fetchImpl });

    await expect(
      api.createGame({ objectType: "shrine", areaPreset: "medium", playerCount: 3 }),
    ).rejects.toThrow(/400/);
  });

  it("test_joinGame_onSuccess_returnsPlayerId", async () => {
    const joined: JoinGameResponse = { playerId: "p-99" };
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(joined));
    const api = createTopoApi({ baseUrl: "http://api.test", fetchImpl });

    const result = await api.joinGame("g-1", { displayName: "じろう" });

    expect(result).toEqual(joined);
    const [url, init] = fetchImpl.mock.calls[0];
    expect(url).toBe("http://api.test/api/games/g-1/players");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(init?.body as string)).toEqual({ displayName: "じろう" });
  });

  it("test_joinGame_on404_throwsApiErrorWithStatus404", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({}, false, 404));
    const api = createTopoApi({ baseUrl: "http://api.test", fetchImpl });

    const error = await api.joinGame("g-x", {}).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(404);
  });

  it("test_joinGame_on409_throwsApiErrorWithStatus409", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({}, false, 409));
    const api = createTopoApi({ baseUrl: "http://api.test", fetchImpl });

    const error = await api.joinGame("g-1", {}).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(409);
  });

  it("test_getGameState_onSuccess_returnsState", async () => {
    const state: GameStateResponse = {
      gameId: "g-1",
      status: "WAITING",
      playerCount: 3,
      players: [
        { playerId: "p-1", displayName: "たろう", confirmed: false, live: null, online: false },
      ],
      currentArea: null,
      result: null,
    };
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(state));
    const api = createTopoApi({ baseUrl: "http://api.test", fetchImpl });

    const result = await api.getGameState("g-1");

    expect(result).toEqual(state);
    expect(fetchImpl).toHaveBeenCalledWith("http://api.test/api/games/g-1", undefined);
  });

  it("test_getGameState_on404_throwsApiErrorWithStatus404", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({}, false, 404));
    const api = createTopoApi({ baseUrl: "http://api.test", fetchImpl });

    const error = await api.getGameState("g-x").catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(404);
  });

  it("test_startGame_onSuccess_returnsGameIdAndStatus", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({ gameId: "g-1", status: "ACTIVE" }));
    const api = createTopoApi({ baseUrl: "http://api.test", fetchImpl });

    const result = await api.startGame("g-1", { playerId: "p-1" });

    expect(result).toEqual({ gameId: "g-1", status: "ACTIVE" });
    const [url, init] = fetchImpl.mock.calls[0];
    expect(url).toBe("http://api.test/api/games/g-1/start");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(init?.body as string)).toEqual({ playerId: "p-1" });
  });

  it("test_startGame_on403_throwsApiErrorWithStatus403", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({}, false, 403));
    const api = createTopoApi({ baseUrl: "http://api.test", fetchImpl });

    const error = await api.startGame("g-1", { playerId: "p-x" }).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(403);
  });

  it("test_startGame_on409_throwsApiErrorWithStatus409", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({}, false, 409));
    const api = createTopoApi({ baseUrl: "http://api.test", fetchImpl });

    const error = await api.startGame("g-1", { playerId: "p-1" }).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(409);
  });

  it("test_updateLocation_on204_resolvesWithoutReadingBody", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(noContentResponse());
    const api = createTopoApi({ baseUrl: "http://api.test", fetchImpl });

    await expect(
      api.updateLocation("g-1", "p-1", { lat: 35.68, lng: 139.76 }),
    ).resolves.toBeUndefined();
  });

  it("test_updateLocation_usesPutWithLatLngBodyAtLocationPath", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(noContentResponse());
    const api = createTopoApi({ baseUrl: "http://api.test", fetchImpl });

    await api.updateLocation("g-1", "p-1", { lat: 35.68, lng: 139.76 });

    const [url, init] = fetchImpl.mock.calls[0];
    expect(url).toBe("http://api.test/api/games/g-1/players/p-1/location");
    expect(init?.method).toBe("PUT");
    expect(JSON.parse(init?.body as string)).toEqual({ lat: 35.68, lng: 139.76 });
  });

  it("test_updateLocation_on404_throwsApiErrorWithStatus404", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(noContentResponse(false, 404));
    const api = createTopoApi({ baseUrl: "http://api.test", fetchImpl });

    const error = await api.updateLocation("g-x", "p-1", { lat: 1, lng: 2 }).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(404);
  });

  it("test_updateLocation_on400_throwsApiErrorWithStatus400", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(noContentResponse(false, 400));
    const api = createTopoApi({ baseUrl: "http://api.test", fetchImpl });

    const error = await api.updateLocation("g-1", "p-1", { lat: 999, lng: 2 }).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(400);
  });
});
