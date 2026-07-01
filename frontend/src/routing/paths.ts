// ルーティングのパス組み立て（単一ソース）。
//
// 01-spec のルーティング: `/` = 作成画面、`/game/<gameId>?p=<playerId>` = 待機画面。
// 招待 URL（gameId のみ・playerId を含めない）と作成者 URL（gameId + playerId）を区別する。

export const CREATE_PATH = "/";

/** react-router のルート定義に使う待機画面のパスパターン。 */
export const GAME_ROUTE_PATTERN = "/game/:gameId";

/** プレイヤー識別のクエリキー（?p=<playerId>）。 */
export const PLAYER_QUERY_KEY = "p";

/**
 * 作成者自身の待機画面パス（gameId + playerId）。作成後のナビゲーション先。
 */
export function buildGamePath(gameId: string, playerId: string): string {
  return `/game/${gameId}?${PLAYER_QUERY_KEY}=${encodeURIComponent(playerId)}`;
}

/**
 * 招待 URL（gameId のみ・playerId を含めない）。origin を与えて絶対 URL を作る。
 * 招待された側は playerId を持たず、参加時に新規発番される（US-05）。
 */
export function buildInviteUrl(origin: string, gameId: string): string {
  return `${origin}/game/${gameId}`;
}
