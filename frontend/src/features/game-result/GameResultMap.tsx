import L from "leaflet";
import { useEffect } from "react";
import { CircleMarker, MapContainer, Polygon, TileLayer, useMap } from "react-leaflet";
import type { GameResultPresentation } from "./gameResultPresentation";

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
}: Pick<GameResultPresentation, "polygon" | "vertices">) {
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
      <ResultBounds polygon={polygon} />
    </MapContainer>
  );
}
