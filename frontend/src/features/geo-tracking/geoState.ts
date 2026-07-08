// 測位の状態モデルと共有型。
//
// 旧 vanilla 実装（geo-tracker.js の GeoState）を 1:1 で移植する。
//   INITIALIZING     地図描画済み・初回測位待ち
//   TRACKING         測位成功・追従中
//   DEGRADED         追従中の一時失敗・地図維持＋控えめ表示
//   PERMISSION_ERROR 初回拒否 / 非安全コンテキスト＝エラー画面（地図を出さない）

export const GeoState = {
  INITIALIZING: "INITIALIZING",
  TRACKING: "TRACKING",
  DEGRADED: "DEGRADED",
  PERMISSION_ERROR: "PERMISSION_ERROR",
} as const;

export type GeoState = (typeof GeoState)[keyof typeof GeoState];

// プレイヤー位置。DESIGN.md の Coordinate（WGS84 / lat・lng）に揃える。
export type Coordinate = {
  lat: number;
  lng: number;
};

// 友達ドット（他プレイヤーのライブ位置）の描画用ビューモデル（US-09）。
// API 型（PlayerPayload）とは分離した「描画に必要な最小形」。Dumb はこの型だけを受け取り、
// PlayerPayload / api を import しない。自分除外・live=null 除外は Smart（導出側）の責務。
export type LiveMarker = {
  playerId: string;
  displayName: string;
  coordinate: Coordinate;
  // 在室（online=true）= 通常表示 / 離席（online=false）= グレーアウト（半透明）。
  online: boolean;
};

// 許可拒否時のエラー文言（旧 error-view.js から移植）。
export const PERMISSION_DENIED_MESSAGE =
  "位置情報の利用が許可されませんでした。ブラウザの設定で許可してから再試行してください。";

// 非安全コンテキスト時のエラー文言（再試行しても解決しない）。
export const INSECURE_CONTEXT_MESSAGE =
  "位置情報は安全な接続でのみ利用できます。localhost または https で開いてください。";

// 控えめバナーの文言（DEGRADED）。
export const DEGRADED_MESSAGE = "位置更新が滞っています";

// 現在地精度（accuracy）の表示ラベル。
// GeolocationPosition.coords.accuracy（メートル・95% 信頼半径）を丸めて表示する。
// モバイル/GPS 環境では精度が大きく変わるため、実測値をそのまま見せる。
export function formatAccuracy(meters: number): string {
  return `現在地精度 ±${Math.round(meters)} m`;
}
