// 地図マーカー / 凸包ポリゴンの見た目定義（Leaflet アイコン・スタイル定数）。
//
// 純粋な定義のみ（型・定数・アイコンファクトリ）。座標計算・凸包計算は一切しない（CLAUDE.md）。
// react-leaflet の Dumb 部品（MapView / LiveMarkers / HullPolygon）から参照する。api には依存しない。

import L from "leaflet";
import markerIcon from "leaflet/dist/images/marker-icon.png";
import markerIcon2x from "leaflet/dist/images/marker-icon-2x.png";
import markerShadow from "leaflet/dist/images/marker-shadow.png";

// 自分ピン（self）。Leaflet 既定の青ピン。Vite バンドルでは画像パスが壊れるため import URL で明示解決する。
// 友達ドット（下記 liveMarkerIcon）とは「青ピン vs オレンジ円ドット」で色・形が視覚的に区別される（F1）。
export const selfIcon = L.icon({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
});

// 離席（online=false）マーカーの不透明度。在室は 1.0（Marker の opacity prop で適用）。
export const OFFLINE_OPACITY = 0.45;

// 凸包ポリゴン（面積成立の形）の塗り＋輪郭スタイル。控えめな塗りで、上に重なる友達ドットが読める透過度にする。
export const HULL_FILL_STYLE = {
  color: "#2563eb",
  weight: 2,
  fillColor: "#3b82f6",
  fillOpacity: 0.15,
} as const;

// 退化（一直線・sqm=0）時に線分として描くスタイル。塗りを持たず輪郭のみ。
export const HULL_LINE_STYLE = {
  color: "#2563eb",
  weight: 3,
  opacity: 0.8,
} as const;

// divIcon の html は Leaflet が innerHTML で描画するため、ユーザー入力（displayName）は必ずエスケープする（XSS 対策）。
function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

// 友達ドット（他プレイヤーのライブ位置）のアイコン。オレンジの円ドット＋常時表示の displayName ラベル（F3）。
// online の別（グレーアウト）は className と Marker の opacity prop で表現する（本ファクトリは className を付与）。
export function liveMarkerIcon(online: boolean, displayName: string): L.DivIcon {
  const stateClass = online ? "live-marker--online" : "live-marker--offline";
  const name = escapeHtml(displayName);
  const dot =
    '<span style="display:block;width:12px;height:12px;border-radius:9999px;' +
    'background:#f97316;border:2px solid #ffffff;box-shadow:0 0 0 1px rgba(0,0,0,0.3)"></span>';
  const label =
    '<span class="live-marker__label" style="position:absolute;top:15px;left:50%;' +
    "transform:translateX(-50%);white-space:nowrap;font-size:11px;line-height:1.2;color:#1f2937;" +
    `background:rgba(255,255,255,0.85);padding:0 3px;border-radius:3px">${name}</span>`;
  return L.divIcon({
    className: `live-marker ${stateClass}`,
    html: `${dot}${label}`,
    iconSize: [12, 12],
    iconAnchor: [6, 6],
  });
}
