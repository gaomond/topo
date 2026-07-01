import { describe, expect, it, vi } from "vitest";
import { createTopoApi } from "@/api/topoApi";
import type { ConfigResponse, CreateGameResponse } from "@/api/types";

// fetch を注入して API ラッパの入出力・エラー変換を検証する（実 HTTP に依存しない）。
function jsonResponse(body: unknown, ok = true, status = 200): Response {
  return {
    ok,
    status,
    json: async () => body,
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
});
