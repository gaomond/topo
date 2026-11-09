import L from "leaflet";
import { useEffect } from "react";
import {
  CircleMarker,
  MapContainer,
  Marker,
  Polygon,
  Popup,
  TileLayer,
  useMap,
} from "react-leaflet";
import type { GameResultPresentation } from "./gameResultPresentation";

const objectIcon = L.divIcon({
  className: "",
  html: '<span style="display:block;width:20px;height:20px;background:#d97706;border:2px solid white;border-radius:4px;box-shadow:0 1px 4px #0006;transform:rotate(45deg)"></span>',
  iconSize: [20, 20],
  iconAnchor: [10, 10],
});

function ResultBounds({ polygon }: Pick<GameResultPresentation, "polygon">) {
  const map = useMap();
  useEffect(() => {
    map.fitBounds(L.latLngBounds(polygon), { padding: [24, 24], maxZoom: 18, animate: false });
  }, [map, polygon]);
  return null;
}

export function GameResultMap({
  polygon,
  vertices,
  objects,
}: Pick<GameResultPresentation, "polygon" | "vertices" | "objects">) {
  return (
    <MapContainer
      center={polygon[0]}
      zoom={13}
      className="h-full w-full"
      aria-label="最終多角形の地図"
    >
      <TileLayer
        url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution={
          '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        }
        maxZoom={19}
      />
      <Polygon positions={polygon} pathOptions={{ color: "#059669", fillOpacity: 0.2 }} />
      {vertices.map((point) => (
        <CircleMarker
          key={`${point[0]}:${point[1]}`}
          center={point}
          radius={6}
          pathOptions={{ color: "#065f46", fillColor: "#fff", fillOpacity: 1 }}
        />
      ))}
      {objects.map((object) => (
        <Marker
          key={object.id}
          position={[object.lat, object.lng]}
          icon={objectIcon}
          title={object.name?.trim() ? object.name : "名称未登録"}
        >
          <Popup>{object.name?.trim() ? object.name : "名称未登録"}</Popup>
        </Marker>
      ))}
      <ResultBounds polygon={polygon} />
    </MapContainer>
  );
}
