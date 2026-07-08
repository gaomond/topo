// 地図ビュー（Dumb / react-leaflet ラッパ）。
//
// 描画専用部品。OSM 標準タイル地図に、自分ピン・友達ドット（LiveMarkers）・凸包ポリゴン（HullPolygon）を
// 宣言的に重ね、全頂点が収まるよう FitBoundsController で追従する。Geolocation も API も状態も知らない。
// 座標計算（凸包・面積・補間）は一切しない（CLAUDE.md）。命令的な L.map(...) は使わず react-leaflet で統合する。

import { useMemo } from "react";
import { MapContainer, Marker, TileLayer } from "react-leaflet";
import { FitBoundsController } from "./FitBoundsController";
import type { Coordinate, LiveMarker } from "./geoState";
import { HullPolygon } from "./HullPolygon";
import { LiveMarkers } from "./LiveMarkers";
import { selfIcon } from "./mapMarkers";
import { OverviewButton } from "./OverviewButton";

// OSM 標準タイル。本番常用ポリシー対応・プロバイダ差し替えは将来論点（US-02 スコープ外）。
const OSM_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
const OSM_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

// 初回測位が来るまでの暫定中心・ズーム（描画を測位成否と独立に出すための仮値）。
// 対象点が 1 つでも現れれば FitBoundsController が全体へ寄せる。
const INITIAL_CENTER: [number, number] = [35.681236, 139.767125]; // 東京駅付近
const INITIAL_ZOOM = 5;

export type MapViewProps = {
  // 未測位は null。座標は DESIGN.md の { lat, lng }（WGS84）。自分は watchPosition のリアルタイム値（F1）。
  selfLocation: Coordinate | null;
  // 友達ドット（他プレイヤーの live 位置）。自分除外・live=null 除外済みで Smart から渡る。
  friends?: LiveMarker[];
  // 凸包の頂点（サーバー提供・[lat,lng] 閉環）。null は形が未成立（描かない）。
  hull?: [number, number][] | null;
  // 退化（sqm=0）。線分として描く。
  degenerate?: boolean;
  // 「全体表示」押下ごとに +1。FitBoundsController の強制リフィットのトリガ。
  overviewNonce?: number;
  // 「全体表示」ボタン押下を Smart へ伝える。
  onOverview?: () => void;
};

export function MapView({
  selfLocation,
  friends = [],
  hull = null,
  degenerate = false,
  overviewNonce = 0,
  onOverview,
}: MapViewProps) {
  // 全対象点（自分＋友達＋凸包の全頂点）。identity を安定させ、FitBoundsController の無駄な再評価を抑える。
  const points = useMemo<[number, number][]>(() => {
    const result: [number, number][] = [];
    if (selfLocation !== null) {
      result.push([selfLocation.lat, selfLocation.lng]);
    }
    for (const friend of friends) {
      result.push([friend.coordinate.lat, friend.coordinate.lng]);
    }
    if (hull !== null) {
      for (const vertex of hull) {
        result.push(vertex);
      }
    }
    return result;
  }, [selfLocation, friends, hull]);

  return (
    <>
      <MapContainer
        center={INITIAL_CENTER}
        zoom={INITIAL_ZOOM}
        className="absolute inset-0 h-full w-full"
        aria-label="地図"
      >
        <TileLayer url={OSM_TILE_URL} attribution={OSM_ATTRIBUTION} maxZoom={19} />
        {/* 描画順は overlayPane（ポリゴン）→ markerPane（ドット/ピン）。塗りがドットを隠さない。 */}
        <HullPolygon hull={hull} degenerate={degenerate} />
        <LiveMarkers markers={friends} />
        {selfLocation !== null && (
          // 自分ピン（self）。友達ドットとは色/形で区別される。
          <Marker position={[selfLocation.lat, selfLocation.lng]} icon={selfIcon} title="現在地" />
        )}
        <FitBoundsController points={points} overviewNonce={overviewNonce} />
      </MapContainer>
      {onOverview !== undefined && <OverviewButton onOverview={onOverview} />}
    </>
  );
}
