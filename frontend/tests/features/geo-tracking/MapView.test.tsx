import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
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
});
