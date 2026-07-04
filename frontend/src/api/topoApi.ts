// バックエンド API を呼ぶ型付き fetch ラッパ。
//
// API を呼べるのは Smart（コンテナ）だけ（CLAUDE.md Smart/Dumb）だが、通信の実体はここに集約し、
// テスト時に fetch を注入して差し替え可能にする（fakeGeolocation の DI パターンに倣う）。
// ベース URL は別オリジン前提のため import.meta.env（VITE_API_BASE_URL）で外部化する。

import type {
  ConfigResponse,
  CreateGameRequest,
  CreateGameResponse,
  GameStateResponse,
  JoinGameRequest,
  JoinGameResponse,
  StartGameRequest,
  StartGameResponse,
} from "./types";

// 注入可能な fetch。既定はブラウザ実体。テストではモックを渡す。
export type FetchLike = typeof fetch;

/**
 * API エラー。HTTP ステータスを構造的に保持する。
 *
 * 参加/取得のフロント分岐（404 → 「見つかりません」/ 409 → 「参加できません」）で
 * メッセージ文字列パースに頼らず status で判別するために使う。
 */
export class ApiError extends Error {
  readonly status: number;
  readonly path: string;

  constructor(status: number, path: string) {
    super(`API エラー: ${status} ${path}`);
    this.name = "ApiError";
    this.status = status;
    this.path = path;
  }
}

export type TopoApi = {
  fetchConfig: () => Promise<ConfigResponse>;
  createGame: (request: CreateGameRequest) => Promise<CreateGameResponse>;
  joinGame: (gameId: string, request: JoinGameRequest) => Promise<JoinGameResponse>;
  getGameState: (gameId: string) => Promise<GameStateResponse>;
  startGame: (gameId: string, request: StartGameRequest) => Promise<StartGameResponse>;
};

export type CreateTopoApiOptions = {
  // 別オリジンの API ベース URL（末尾スラッシュなし）。既定は env 値。
  baseUrl?: string;
  // 注入可能な fetch。既定は globalThis.fetch。
  fetchImpl?: FetchLike;
};

// env 値。未設定時は同一オリジン相対（空文字）にフォールバックする。
function defaultBaseUrl(): string {
  return import.meta.env.VITE_API_BASE_URL ?? "";
}

/**
 * TopoApi を生成する。baseUrl / fetch を注入でき、テストで差し替え可能。
 */
export function createTopoApi(options: CreateTopoApiOptions = {}): TopoApi {
  const baseUrl = options.baseUrl ?? defaultBaseUrl();
  const fetchImpl = options.fetchImpl ?? globalThis.fetch.bind(globalThis);

  async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await fetchImpl(`${baseUrl}${path}`, init);
    if (!response.ok) {
      throw new ApiError(response.status, path);
    }
    return (await response.json()) as T;
  }

  return {
    fetchConfig: () => requestJson<ConfigResponse>("/api/config"),
    createGame: (request) =>
      requestJson<CreateGameResponse>("/api/games", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(request),
      }),
    joinGame: (gameId, request) =>
      requestJson<JoinGameResponse>(`/api/games/${encodeURIComponent(gameId)}/players`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(request),
      }),
    getGameState: (gameId) =>
      requestJson<GameStateResponse>(`/api/games/${encodeURIComponent(gameId)}`),
    startGame: (gameId, request) =>
      requestJson<StartGameResponse>(`/api/games/${encodeURIComponent(gameId)}/start`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(request),
      }),
  };
}

/**
 * 既定の API 実体（別オリジン・ブラウザ fetch）。アプリ全体で 1 個を共有する。
 *
 * Smart コンテナは既定でこれを使う（毎レンダリング生成せず identity が安定するため
 * useMemo での防御が要らない）。テストは createTopoApi で baseUrl/fetch を注入するか、
 * コンテナに api を注入して差し替える。
 */
export const topoApi: TopoApi = createTopoApi();
