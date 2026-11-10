// 友達ドット（他プレイヤーのライブ位置）の描画（Dumb）。
//
// LiveMarker[] を受け取り、各ライブ位置にオレンジのドット＋常時表示の displayName ラベルを描く。
// 離席（online=false）は半透明でグレーアウト。API・状態は知らない（受け取った配列をそのまま描く）。
// 自分除外・live=null 除外は呼び出し側（Smart）の責務で、ここでは絞り込まない。

import { Marker } from "react-leaflet";
import type { LiveMarker } from "./geoState";
import { CONFIRMED_LIVE_OPACITY, liveMarkerIcon, OFFLINE_OPACITY } from "./mapMarkers";

export type LiveMarkersProps = {
  // 描くべき友達ドットだけを含む配列（自分除外・live=null 除外済み）。
  markers: LiveMarker[];
};

export function LiveMarkers({ markers }: LiveMarkersProps) {
  return (
    <>
      {markers.map((marker) => (
        <Marker
          key={marker.playerId}
          position={[marker.coordinate.lat, marker.coordinate.lng]}
          icon={liveMarkerIcon(marker.online, marker.displayName)}
          // 確定後は補助表示とし、離席と不透明度を乗算しない。
          opacity={marker.confirmed ? CONFIRMED_LIVE_OPACITY : marker.online ? 1 : OFFLINE_OPACITY}
        />
      ))}
    </>
  );
}
