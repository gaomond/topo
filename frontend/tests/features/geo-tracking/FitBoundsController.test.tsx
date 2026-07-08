import { act, render } from "@testing-library/react";
import L from "leaflet";
import { useEffect } from "react";
import { MapContainer, useMap } from "react-leaflet";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { FitBoundsController } from "@/features/geo-tracking/FitBoundsController";

// 内部の useMap インスタンスを掴んで手動イベント（dragstart 等）を発火するためのプローブ。
function CaptureMap({ onReady }: { onReady: (map: L.Map) => void }) {
  const map = useMap();
  useEffect(() => {
    onReady(map);
  }, [map, onReady]);
  return null;
}

type HarnessProps = {
  points: [number, number][];
  overviewNonce: number;
  onReady: (map: L.Map) => void;
};

function Harness({ points, overviewNonce, onReady }: HarnessProps) {
  return (
    <MapContainer center={[35, 139]} zoom={5}>
      <FitBoundsController points={points} overviewNonce={overviewNonce} />
      <CaptureMap onReady={onReady} />
    </MapContainer>
  );
}

describe("FitBoundsController", () => {
  let fitSpy: ReturnType<typeof vi.spyOn>;
  let map: L.Map;

  beforeEach(() => {
    // 実挙動（fitBounds）は残しつつ呼び出し引数を観測する（vi.spyOn は既定で原実装を保持）。
    fitSpy = vi.spyOn(L.Map.prototype, "fitBounds");
  });

  afterEach(() => {
    fitSpy.mockRestore();
  });

  function renderHarness(points: [number, number][], overviewNonce = 0) {
    const onReady = (m: L.Map) => {
      map = m;
    };
    return render(<Harness points={points} overviewNonce={overviewNonce} onReady={onReady} />);
  }

  it("test_全頂点_fitBoundsのboundsに全点が含まれる", () => {
    const points: [number, number][] = [
      [35.0, 139.0],
      [35.5, 139.5],
      [36.0, 140.0],
    ];
    renderHarness(points);
    expect(fitSpy).toHaveBeenCalled();
    const bounds = fitSpy.mock.calls.at(-1)?.[0] as L.LatLngBounds;
    for (const p of points) {
      expect(bounds.contains(L.latLng(p))).toBe(true);
    }
  });

  it("test_bounds微小変化_再フィットしない", () => {
    const base: [number, number][] = [
      [35.0, 139.0],
      [35.5, 139.5],
      [36.0, 140.0],
    ];
    const { rerender } = renderHarness(base);
    const initialCalls = fitSpy.mock.calls.length;
    // 約 1m の微小移動。閾値（25m）未満なので再フィットしない。
    const nudged: [number, number][] = [
      [35.00001, 139.0],
      [35.5, 139.5],
      [36.0, 140.0],
    ];
    rerender(<Harness points={nudged} overviewNonce={0} onReady={() => {}} />);
    expect(fitSpy.mock.calls.length).toBe(initialCalls);
  });

  it("test_bounds有意変化_再フィットする", () => {
    const base: [number, number][] = [
      [35.0, 139.0],
      [35.5, 139.5],
      [36.0, 140.0],
    ];
    const { rerender } = renderHarness(base);
    const initialCalls = fitSpy.mock.calls.length;
    // 約 1km 以上の移動。閾値超えで再フィットする。
    const moved: [number, number][] = [
      [35.2, 139.0],
      [35.5, 139.5],
      [36.0, 140.0],
    ];
    rerender(<Harness points={moved} overviewNonce={0} onReady={() => {}} />);
    expect(fitSpy.mock.calls.length).toBe(initialCalls + 1);
  });

  it("test_手動操作中_自動フィットが停止する", () => {
    const base: [number, number][] = [
      [35.0, 139.0],
      [35.5, 139.5],
      [36.0, 140.0],
    ];
    const { rerender } = renderHarness(base);
    const initialCalls = fitSpy.mock.calls.length;
    // ユーザーがドラッグ開始 → 自動フィットを一時停止。
    act(() => {
      map.fire("dragstart");
    });
    // 有意変化が来ても操作中は追従しない。
    const moved: [number, number][] = [
      [35.3, 139.0],
      [35.5, 139.5],
      [36.0, 140.0],
    ];
    rerender(<Harness points={moved} overviewNonce={0} onReady={() => {}} />);
    expect(fitSpy.mock.calls.length).toBe(initialCalls);
  });

  it("test_全体表示ボタン押下_pause中でも全体へ強制リフィットする", () => {
    const base: [number, number][] = [
      [35.0, 139.0],
      [35.5, 139.5],
      [36.0, 140.0],
    ];
    const { rerender } = renderHarness(base, 0);
    const initialCalls = fitSpy.mock.calls.length;
    // 手動操作で pause。
    act(() => {
      map.fire("dragstart");
    });
    // overviewNonce を進める＝「全体表示」押下。pause を無視して強制リフィット。
    rerender(<Harness points={base} overviewNonce={1} onReady={() => {}} />);
    expect(fitSpy.mock.calls.length).toBe(initialCalls + 1);
  });
});
