// ゲーム状態のポーリング基盤（SWR）。
//
// 01-spec 1.5「共通ポーリング基盤」を SWR（stale-while-revalidate）で実現する。自前ポーリングは作らない。
// US-08（ACTIVE 後の 2 秒ポーリング）と共有する前提で、頻度は refreshInterval 引数で切り替える。
// この hook は複数機能（待機 / ACTIVE）で共有するため src/shared に置く（CLAUDE.md）。

import { useCallback } from "react";
import useSWR, { type KeyedMutator } from "swr";
import type { TopoApi } from "@/api/topoApi";
import type { GameStateResponse } from "@/api/types";

export type UseGameStateOptions = {
  // 状態取得に使う API（fetcher）。テストで注入して差し替える。
  api: TopoApi;
  // gameId。未確定（undefined）のときはフェッチしない。
  gameId: string | undefined;
  // ポーリング間隔（ms）。待機は低頻度（既定 5000）、ACTIVE は 2000 などに切り替える。
  refreshIntervalMs?: number;
  pauseAutomaticRevalidation?: boolean;
};

export type UseGameStateResult = {
  state: GameStateResponse | undefined;
  error: unknown;
  isLoading: boolean;
  // 手動再検証（SWR mutate）。開始成功後に即 ACTIVE を取り込むために使う（US-06 creator 即時遷移）。
  mutate: KeyedMutator<GameStateResponse>;
  refreshState: (signal?: AbortSignal) => Promise<GameStateResponse | undefined>;
};

const DEFAULT_REFRESH_INTERVAL_MS = 5000;

/**
 * gameId をキーに GET /api/games/{id} を一定間隔でポーリングする。
 *
 * 404 などのエラーは error に載せて呼び出し側の分岐（404 画面）に使う。
 * gameId が undefined の間は SWR key を null にしてフェッチしない。
 */
export function useGameState({
  api,
  gameId,
  refreshIntervalMs = DEFAULT_REFRESH_INTERVAL_MS,
  pauseAutomaticRevalidation = false,
}: UseGameStateOptions): UseGameStateResult {
  // SWR key は [プレフィックス, gameId]。gameId 未確定時は null でフェッチを止める。
  const key = gameId ? (["game-state", gameId] as const) : null;

  const { data, error, isLoading, mutate } = useSWR<GameStateResponse>(
    key,
    ([, id]: readonly [string, string]) => api.getGameState(id),
    {
      refreshInterval: pauseAutomaticRevalidation ? 0 : refreshIntervalMs,
      revalidateOnMount: pauseAutomaticRevalidation ? false : undefined,
      revalidateIfStale: !pauseAutomaticRevalidation,
      revalidateOnFocus: !pauseAutomaticRevalidation,
      revalidateOnReconnect: !pauseAutomaticRevalidation,
      // SWR の既定 dedupingInterval（2000ms）は、refreshInterval が同値以下（US-08 は 2000ms）のとき
      // 定期リバリデーションを重複とみなして握り潰す。ポーリング周期より必ず短くして各周期のフェッチを通す。
      dedupingInterval: Math.floor(refreshIntervalMs / 2),
      // 404 は復旧しない永続エラーのため自動リトライしない（error に載せて分岐させる）。
      shouldRetryOnError: false,
    },
  );

  const refreshState = useCallback(
    (signal?: AbortSignal) =>
      mutate(
        async () => {
          if (!gameId) return undefined;
          const state = await api.getGameState(gameId);
          // 画面を離れた後の取得結果を共有キャッシュへ反映しない。
          signal?.throwIfAborted();
          return state;
        },
        { revalidate: false },
      ),
    [api, gameId, mutate],
  );

  return { state: data, error, isLoading, mutate, refreshState };
}
