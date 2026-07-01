// バックエンド API を呼ぶ型付き fetch ラッパ。
//
// API を呼べるのは Smart（コンテナ）だけ（CLAUDE.md Smart/Dumb）だが、通信の実体はここに集約し、
// テスト時に fetch を注入して差し替え可能にする（fakeGeolocation の DI パターンに倣う）。
// ベース URL は別オリジン前提のため import.meta.env（VITE_API_BASE_URL）で外部化する。

import type { ConfigResponse, CreateGameRequest, CreateGameResponse } from "./types";

// 注入可能な fetch。既定はブラウザ実体。テストではモックを渡す。
export type FetchLike = typeof fetch;

export type TopoApi = {
  fetchConfig: () => Promise<ConfigResponse>;
  createGame: (request: CreateGameRequest) => Promise<CreateGameResponse>;
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
      throw new Error(`API エラー: ${response.status} ${path}`);
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
  };
}
