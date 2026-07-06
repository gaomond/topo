// テスト用の TopoApi フェイク。API を注入して Smart コンポーネント/フックを実ブラウザ・実 HTTP 非依存で検証する。
// fakeGeolocation の DI パターンに倣う。必要なメソッドだけ vi.fn で差し替える。

import { vi } from "vitest";
import type { TopoApi } from "@/api/topoApi";
import type {
  ConfigResponse,
  CreateGameResponse,
  GameStateResponse,
  JoinGameResponse,
  PlayerPayload,
  StartGameResponse,
} from "@/api/types";

export type FakeTopoApi = {
  api: TopoApi;
  fetchConfig: ReturnType<typeof vi.fn<() => Promise<ConfigResponse>>>;
  createGame: ReturnType<typeof vi.fn<() => Promise<CreateGameResponse>>>;
  joinGame: ReturnType<typeof vi.fn<() => Promise<JoinGameResponse>>>;
  getGameState: ReturnType<typeof vi.fn<() => Promise<GameStateResponse>>>;
  startGame: ReturnType<typeof vi.fn<() => Promise<StartGameResponse>>>;
  updateLocation: ReturnType<typeof vi.fn<() => Promise<void>>>;
};

export function createFakeTopoApi(): FakeTopoApi {
  const fetchConfig = vi.fn<() => Promise<ConfigResponse>>();
  const createGame = vi.fn<() => Promise<CreateGameResponse>>();
  const joinGame = vi.fn<() => Promise<JoinGameResponse>>();
  const getGameState = vi.fn<() => Promise<GameStateResponse>>();
  const startGame = vi.fn<() => Promise<StartGameResponse>>();
  // 既定は resolve（送信は副作用なし）。テストで rejectValue も設定できる。
  const updateLocation = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
  const api: TopoApi = {
    fetchConfig,
    createGame,
    joinGame,
    getGameState,
    startGame,
    updateLocation,
  };
  return { api, fetchConfig, createGame, joinGame, getGameState, startGame, updateLocation };
}

export function gameState(overrides: Partial<GameStateResponse> = {}): GameStateResponse {
  return {
    gameId: "game-123",
    status: "WAITING",
    playerCount: 3,
    players: [],
    // US-08 で追加。既定は「未計測（currentArea=null）」「未確定（result=null）」。
    currentArea: null,
    result: null,
    ...overrides,
  };
}

// PlayerPayload のテスト用ファクトリ。live/online の既定（未送信・非在室）を埋める。
export function player(overrides: Partial<PlayerPayload> = {}): PlayerPayload {
  return {
    playerId: "player-1",
    displayName: "プレイヤー",
    confirmed: false,
    live: null,
    online: false,
    ...overrides,
  };
}
