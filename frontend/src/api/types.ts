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
