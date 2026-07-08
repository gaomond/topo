import { render } from "@testing-library/react";
import type { ReactNode } from "react";
import { MapContainer } from "react-leaflet";
import { describe, expect, it } from "vitest";
import { HullPolygon } from "@/features/geo-tracking/HullPolygon";

// Leaflet の Path（Polygon / Polyline）は SVG の path.leaflet-interactive として描かれる。
function renderInMap(node: ReactNode) {
  return render(
    <MapContainer center={[35, 139]} zoom={13}>
      {node}
    </MapContainer>,
  );
}

describe("HullPolygon", () => {
  it("test_render_hullあり_ポリゴンが描かれる", () => {
    const { container } = renderInMap(
      <HullPolygon
        hull={[
          [35.0, 139.0],
          [35.1, 139.1],
          [35.0, 139.2],
          [35.0, 139.0],
        ]}
      />,
    );
    expect(container.querySelector("path.leaflet-interactive")).not.toBeNull();
  });

  it("test_render_hullがnull_何も描かれない", () => {
    const { container } = renderInMap(<HullPolygon hull={null} />);
    expect(container.querySelector("path.leaflet-interactive")).toBeNull();
  });

  it("test_render_空hull_何も描かれない", () => {
    const { container } = renderInMap(<HullPolygon hull={[]} />);
    expect(container.querySelector("path.leaflet-interactive")).toBeNull();
  });

  it("test_render_一直線3点degenerate_例外なく線分として描かれる", () => {
    // 退化（sqm=0）でもクラッシュせず SVG path（線分）が出る。
    const { container } = renderInMap(
      <HullPolygon
        hull={[
          [35.0, 139.0],
          [35.0, 139.1],
          [35.0, 139.2],
        ]}
        degenerate
      />,
    );
    expect(container.querySelector("path.leaflet-interactive")).not.toBeNull();
  });
});
