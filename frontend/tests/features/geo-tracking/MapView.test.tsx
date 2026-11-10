import { render, screen } from "@testing-library/react";
import L from "leaflet";
import { describe, expect, it, vi } from "vitest";
import type { LiveMarker } from "@/features/geo-tracking/geoState";
import { MapView } from "@/features/geo-tracking/MapView";

// jsdom は実タイルを読まない。ここでは MapContainer がクラッシュせず描画でき、
// 自分ピン・友達ドット・凸包ポリゴン・全体表示ボタンの DOM 有無だけを軽く確認する。
// タイル実ロード・パン/ズーム・許可ダイアログ・カメラ追従の見た目は手動検証（04-test-report.md）に委ねる。

function friend(overrides: Partial<LiveMarker> = {}): LiveMarker {
  return {
    playerId: "p-2",
    displayName: "ボブ",
    coordinate: { lat: 35.1, lng: 139.1 },
    online: true,
    confirmed: false,
    ...overrides,
  };
}

describe("MapView", () => {
  it("test_render_selfLocationNull_rendersMapWithoutMarker", () => {
    const { container } = render(<MapView selfLocation={null} />);
    // 地図コンテナが描画される。
    expect(container.querySelector(".leaflet-container")).not.toBeNull();
    // 自分ピン（marker img）は無い。
    expect(container.querySelector("img.leaflet-marker-icon")).toBeNull();
  });

  it("test_render_selfLocationSet_rendersSelfMarker", () => {
    const { container } = render(<MapView selfLocation={{ lat: 35.0, lng: 139.0 }} />);
    expect(container.querySelector(".leaflet-container")).not.toBeNull();
    // 自分ピンが描画される。
    expect(container.querySelector("img.leaflet-marker-icon")).not.toBeNull();
  });

  it("test_render_friendsあり_友達ドットとラベルが描かれる", () => {
    const { container } = render(
      <MapView
        selfLocation={null}
        friends={[
          friend({ playerId: "p-2", displayName: "ボブ" }),
          friend({
            playerId: "p-3",
            displayName: "キャロル",
            coordinate: { lat: 35.2, lng: 139.2 },
          }),
        ]}
      />,
    );
    expect(container.querySelectorAll(".live-marker").length).toBe(2);
    expect(screen.getByText("ボブ")).toBeInTheDocument();
    expect(screen.getByText("キャロル")).toBeInTheDocument();
  });

  it("test_render_hullあり_凸包ポリゴンが描かれる", () => {
    const { container } = render(
      <MapView
        selfLocation={null}
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

  it("test_render_hullがnull_ポリゴンが描かれない", () => {
    const { container } = render(<MapView selfLocation={null} hull={null} />);
    expect(container.querySelector("path.leaflet-interactive")).toBeNull();
  });

  it("test_render_onOverviewあり_全体表示ボタンが地図上に出る", () => {
    render(<MapView selfLocation={null} onOverview={() => {}} />);
    expect(screen.getByRole("button", { name: "全体表示" })).toBeInTheDocument();
  });

  it("test_render_samePointAndUnsafeName_keepsFullStrengthCheckAndNameAboveFadedLive", () => {
    const name = "<>&\"'";
    const { container } = render(
      <MapView
        selfLocation={{ lat: 35, lng: 139 }}
        selfConfirmed
        friends={[friend({ coordinate: { lat: 35, lng: 139 }, confirmed: true, online: false })]}
        confirmedMarkers={[
          { playerId: "self", displayName: name, coordinate: { lat: 35, lng: 139 } },
        ]}
      />,
    );
    const fixed = container.querySelector(".confirmed-marker") as HTMLElement;
    const live = container.querySelector(".live-marker") as HTMLElement;
    const self = container.querySelector("img.leaflet-marker-icon") as HTMLElement;
    expect(fixed.textContent).toContain(name);
    expect(fixed.querySelector("img")).toBeNull();
    expect(Number(fixed.style.opacity || 1)).toBe(1);
    expect(Number(live.style.opacity)).toBe(0.4);
    expect(Number(self.style.opacity)).toBe(0.4);
    expect(Number(fixed.style.zIndex)).toBeGreaterThan(Number(live.style.zIndex));
    expect(Number(fixed.style.zIndex)).toBeGreaterThan(Number(self.style.zIndex));
  });

  it("test_overview_fixedPointWithoutLive_includesSavedCoordinateInBounds", () => {
    const fit = vi.spyOn(L.Map.prototype, "fitBounds");
    try {
      const { rerender } = render(
        <MapView
          selfLocation={null}
          confirmedMarkers={[
            { playerId: "other", displayName: "保存点", coordinate: { lat: 35.59, lng: 139.6 } },
          ]}
          overviewNonce={0}
        />,
      );
      const bounds = fit.mock.calls.at(-1)?.[0] as L.LatLngBounds;
      expect(bounds.contains([35.59, 139.6])).toBe(true);
      const count = fit.mock.calls.length;
      rerender(
        <MapView
          selfLocation={null}
          confirmedMarkers={[
            { playerId: "other", displayName: "保存点", coordinate: { lat: 35.59, lng: 139.6 } },
          ]}
          overviewNonce={1}
        />,
      );
      expect(fit.mock.calls.length).toBe(count + 1);
    } finally {
      fit.mockRestore();
    }
  });
});
