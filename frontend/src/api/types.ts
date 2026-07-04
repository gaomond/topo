// バックエンド API の Web 表現に対応する型。
//
// 命名・構造はサーバー側（ConfigResponse / CreateGameResponse / CreateGameRequest）に一致させる。

/** GET /api/config のレスポンス。選択肢の単一ソース。 */
export type ConfigResponse = {
  objectTypes: string[];
  areaPresets: AreaPresetPayload[];
};

/** 面積プリセット（config の構成要素）。 */
export type AreaPresetPayload = {
  key: string;
  label: string;
  sqm: number;
};

/** POST /api/games のリクエストボディ。 */
export type CreateGameRequest = {
  objectType: string;
  areaPreset: string;
  playerCount: number;
  displayName?: string;
};

/** POST /api/games のレスポンス。サーバーは gameId / playerId のみ返す。 */
export type CreateGameResponse = {
  gameId: string;
  playerId: string;
};

/** POST /api/games/{id}/players のリクエストボディ。displayName は任意。 */
export type JoinGameRequest = {
  displayName?: string;
};

/** POST /api/games/{id}/players のレスポンス。サーバーは playerId のみ返す。 */
export type JoinGameResponse = {
  playerId: string;
};

/** GET /api/games/{id} のレスポンス（US-05 最小形 + US-06 の creatorPlayerId）。 */
export type GameStateResponse = {
  gameId: string;
  status: string;
  playerCount: number;
  // 作成者の playerId（開始ボタンの creator 判定用）。作成直後の一瞬は null。
  creatorPlayerId?: string | null;
  players: PlayerPayload[];
};

/** POST /api/games/{id}/start のリクエストボディ。playerId は creator 判定に使う必須項目。 */
export type StartGameRequest = {
  playerId: string;
};

/** POST /api/games/{id}/start のレスポンス。開始後の gameId と status（"ACTIVE"）を返す。 */
export type StartGameResponse = {
  gameId: string;
  status: string;
};

/** 参加者（GameStateResponse の構成要素）。 */
export type PlayerPayload = {
  playerId: string;
  displayName: string;
  confirmed: boolean;
};
