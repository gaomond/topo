import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MapView } from "./MapView";

// jsdom は実タイルを読まない。ここでは MapContainer がクラッシュせず描画でき、
// selfLocation の有無で自分ピン（Leaflet marker img）の有無が変わることだけを軽く確認する。
// タイル実ロード・パン/ズーム・許可ダイアログは手動検証（04-test-report.md）に委ねる。

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
});
