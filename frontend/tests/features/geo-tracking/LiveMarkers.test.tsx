import { render, screen } from "@testing-library/react";
import { MapContainer } from "react-leaflet";
import { describe, expect, it } from "vitest";
import type { LiveMarker } from "@/features/geo-tracking/geoState";
import { LiveMarkers } from "@/features/geo-tracking/LiveMarkers";

// react-leaflet の子は MapContainer コンテキストが要る。jsdom は実タイルを読まないが、
// divIcon マーカー（.live-marker）と常時ラベル（displayName テキスト）の DOM は生成される。
function renderInMap(markers: LiveMarker[]) {
  return render(
    <MapContainer center={[35, 139]} zoom={13}>
      <LiveMarkers markers={markers} />
    </MapContainer>,
  );
}

function marker(overrides: Partial<LiveMarker> = {}): LiveMarker {
  return {
    playerId: "p-1",
    displayName: "アリス",
    coordinate: { lat: 35.0, lng: 139.0 },
    online: true,
    ...overrides,
  };
}

describe("LiveMarkers", () => {
  it("test_render_複数markers_ドットがmarkers数だけ描かれる", () => {
    const { container } = renderInMap([
      marker({ playerId: "p-1", displayName: "アリス" }),
      marker({ playerId: "p-2", displayName: "ボブ", coordinate: { lat: 35.1, lng: 139.1 } }),
    ]);
    expect(container.querySelectorAll(".live-marker").length).toBe(2);
  });

  it("test_render_各marker_displayNameラベルが常時表示される", () => {
    renderInMap([
      marker({ playerId: "p-1", displayName: "アリス" }),
      marker({ playerId: "p-2", displayName: "ボブ", coordinate: { lat: 35.1, lng: 139.1 } }),
    ]);
    expect(screen.getByText("アリス")).toBeInTheDocument();
    expect(screen.getByText("ボブ")).toBeInTheDocument();
  });

  it("test_render_onlineがfalse_グレーアウト（半透明）で描かれる", () => {
    const { container } = renderInMap([marker({ online: false })]);
    // 離席は専用クラス＋半透明で在室と区別される。
    const offline = container.querySelector(".live-marker--offline");
    expect(offline).not.toBeNull();
    // Leaflet は opacity を要素の style に反映する（1.0 未満）。
    expect(Number((offline as HTMLElement).style.opacity)).toBeLessThan(1);
  });

  it("test_render_onlineがtrue_通常（不透明）で描かれる", () => {
    const { container } = renderInMap([marker({ online: true })]);
    expect(container.querySelector(".live-marker--online")).not.toBeNull();
    expect(container.querySelector(".live-marker--offline")).toBeNull();
  });

  it("test_render_空配列_ドットが描かれない", () => {
    const { container } = renderInMap([]);
    expect(container.querySelectorAll(".live-marker").length).toBe(0);
  });

  it("test_render_displayNameにHTML_エスケープされテキストとして表示される", () => {
    // 悪意ある displayName でも innerHTML 注入されない（エスケープ＝XSS 対策）。
    renderInMap([marker({ displayName: "<img src=x onerror=alert(1)>" })]);
    expect(screen.getByText("<img src=x onerror=alert(1)>")).toBeInTheDocument();
  });
});
