// テスト用の TopoApi フェイク。API を注入して Smart コンポーネント/フックを実ブラウザ・実 HTTP 非依存で検証する。
// fakeGeolocation の DI パターンに倣う。必要なメソッドだけ vi.fn で差し替える。

import { vi } from "vitest";
import type { TopoApi } from "@/api/topoApi";
import type {
  ConfigResponse,
  CreateGameResponse,
  GameStateResponse,
  JoinGameResponse,
  StartGameResponse,
} from "@/api/types";

export type FakeTopoApi = {
  api: TopoApi;
  fetchConfig: ReturnType<typeof vi.fn<() => Promise<ConfigResponse>>>;
  createGame: ReturnType<typeof vi.fn<() => Promise<CreateGameResponse>>>;
  joinGame: ReturnType<typeof vi.fn<() => Promise<JoinGameResponse>>>;
  getGameState: ReturnType<typeof vi.fn<() => Promise<GameStateResponse>>>;
  startGame: ReturnType<typeof vi.fn<() => Promise<StartGameResponse>>>;
};

export function createFakeTopoApi(): FakeTopoApi {
  const fetchConfig = vi.fn<() => Promise<ConfigResponse>>();
  const createGame = vi.fn<() => Promise<CreateGameResponse>>();
  const joinGame = vi.fn<() => Promise<JoinGameResponse>>();
  const getGameState = vi.fn<() => Promise<GameStateResponse>>();
  const startGame = vi.fn<() => Promise<StartGameResponse>>();
  const api: TopoApi = { fetchConfig, createGame, joinGame, getGameState, startGame };
  return { api, fetchConfig, createGame, joinGame, getGameState, startGame };
}

export function gameState(overrides: Partial<GameStateResponse> = {}): GameStateResponse {
  return {
    gameId: "game-123",
    status: "WAITING",
    playerCount: 3,
    players: [],
    ...overrides,
  };
}
