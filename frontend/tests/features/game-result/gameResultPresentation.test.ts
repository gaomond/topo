import { describe, expect, it } from "vitest";
import { gameResultPresentation } from "@/features/game-result/gameResultPresentation";

const polygon = [
  { lat: 35, lng: 139 },
  { lat: 35.01, lng: 139 },
  { lat: 35, lng: 139.01 },
  { lat: 35, lng: 139 },
];
const result = { areaSqm: 500, areaValid: true, objectCount: 0, polygon };

describe("gameResultPresentation", () => {
  it("test_present_closedPolygon_preservesOrderAndOmitsClosingMarker", () => {
    const view = gameResultPresentation(result);
    expect(view?.polygon).toEqual(polygon.map((p) => [p.lat, p.lng]));
    expect(view?.vertices).toHaveLength(3);
    expect(view?.objectCount).toBe(0);
  });
  it.each([
    null,
    { ...result, areaValid: false },
    { ...result, polygon: null },
    { ...result, polygon: [] },
    { ...result, areaSqm: NaN },
    { ...result, objectCount: -1 },
    { ...result, polygon: polygon.slice(0, 3) },
  ])("test_present_invalidResult_returnsNull %j", (value) => {
    expect(gameResultPresentation(value)).toBeNull();
  });
});
