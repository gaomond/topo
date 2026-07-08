// 凸包ポリゴン（面積成立の形）の描画（Dumb）。
//
// サーバー提供の hull（[lat,lng] 閉環）を Leaflet の Polygon として描く。頂点はサーバー計算値で、
// クライアントでは並べ替え・重複除去・閉環補正・面積計算を一切しない（CLAUDE.md）。
// 退化（一直線・sqm=0）のときは Polygon が面を持たず破綻し得るため、Polyline（線分）に切り替える。
// hull=null（currentArea=null / live 点 3 点未満）のときは描かない。API・状態は知らない。

import { Polygon, Polyline } from "react-leaflet";
import { HULL_FILL_STYLE, HULL_LINE_STYLE } from "./mapMarkers";

export type HullPolygonProps = {
  // CurrentAreaPayload.hull をそのまま渡す。null は形が未成立（描かない）。
  hull: [number, number][] | null;
  // sqm===0（退化）を Smart から渡す。線分として描く。
  degenerate?: boolean;
};

export function HullPolygon({ hull, degenerate = false }: HullPolygonProps) {
  // 形が未成立（3 点未満）／空頂点は描かない。
  if (hull === null || hull.length === 0) {
    return null;
  }
  // 退化（一直線）はポリゴンだと面が潰れて破綻するので線分として描く。
  if (degenerate) {
    return <Polyline positions={hull} pathOptions={HULL_LINE_STYLE} />;
  }
  return <Polygon positions={hull} pathOptions={HULL_FILL_STYLE} />;
}
