// ルートコンポーネント。
//
// 本ストーリーでは測位コンテナを 1 枚出すだけ。ルーティング（gameId / playerId 解釈）はしない（US-04 / US-05）。

import { GeoTrackingContainer } from "./containers/GeoTrackingContainer";

export function App() {
  return <GeoTrackingContainer />;
}
