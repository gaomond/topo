// 地図ビュー（Dumb / react-leaflet ラッパ）。
//
// 描画専用部品。OSM 標準タイル地図＋自分ピンを宣言的に描画する。Geolocation も状態も知らない。
// 座標計算（凸包・面積・補間）は一切しない（CLAUDE.md）。命令的な L.map(...) は使わず react-leaflet で統合する。

import L from "leaflet";
import markerIcon from "leaflet/dist/images/marker-icon.png";
import markerIcon2x from "leaflet/dist/images/marker-icon-2x.png";
import markerShadow from "leaflet/dist/images/marker-shadow.png";
import { useEffect, useRef } from "react";
import { MapContainer, Marker, TileLayer, useMap } from "react-leaflet";
import type { Coordinate } from "./geoState";

// Vite バンドルでは Leaflet 既定アイコンの画像パスが壊れるため、import した URL で明示解決する。
const selfIcon = L.icon({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
});

// OSM 標準タイル。本番常用ポリシー対応・プロバイダ差し替えは将来論点（US-02 スコープ外）。
const OSM_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
const OSM_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

// 初回測位が来るまでの暫定中心・ズーム（描画を測位成否と独立に出すための仮値）。
const INITIAL_CENTER: [number, number] = [35.681236, 139.767125]; // 東京駅付近
const INITIAL_ZOOM = 5;
const SELF_ZOOM = 16;

export type MapViewProps = {
  // 未測位は null。座標は DESIGN.md の { lat, lng }（WGS84）。
  selfLocation: Coordinate | null;
};

// 自分ピンへの中心追従を担う内部部品。
// 初回設定で SELF_ZOOM に寄せ、以後は中心のみ追従する（旧実装の setView→panTo を移植）。
function SelfFollower({ selfLocation }: { selfLocation: Coordinate | null }) {
  const map = useMap();
  const centeredOnceRef = useRef(false);

  useEffect(() => {
    if (selfLocation === null) {
      return;
    }
    const latLng: [number, number] = [selfLocation.lat, selfLocation.lng];
    if (!centeredOnceRef.current) {
      map.setView(latLng, SELF_ZOOM);
      centeredOnceRef.current = true;
    } else {
      map.panTo(latLng);
    }
  }, [map, selfLocation]);

  return null;
}

export function MapView({ selfLocation }: MapViewProps) {
  return (
    <MapContainer
      center={INITIAL_CENTER}
      zoom={INITIAL_ZOOM}
      className="absolute inset-0 h-full w-full"
      aria-label="地図"
    >
      <TileLayer url={OSM_TILE_URL} attribution={OSM_ATTRIBUTION} maxZoom={19} />
      {selfLocation !== null && (
        // 自分ピン（self）。友達ドット（live marker）は本ストーリー対象外なので導入しない。
        <Marker position={[selfLocation.lat, selfLocation.lng]} icon={selfIcon} title="現在地" />
      )}
      <SelfFollower selfLocation={selfLocation} />
    </MapContainer>
  );
}
