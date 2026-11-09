import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { GameResultMap } from "@/features/game-result/GameResultMap";

const { fitBounds } = vi.hoisted(() => ({ fitBounds: vi.fn() }));
vi.mock("react-leaflet", () => ({
  MapContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  TileLayer: () => null,
  Polygon: ({ positions }: { positions: unknown[] }) => (
    <div data-testid="polygon" data-count={positions.length} />
  ),
  CircleMarker: ({ center }: { center: number[] }) => (
    <div data-testid="vertex" data-center={JSON.stringify(center)} />
  ),
  useMap: () => ({ fitBounds }),
}));
describe("GameResultMap", () => {
  it("test_render_polygon_fitsBoundsAndRendersEachVertex", () => {
    const vertices: [number, number][] = [
      [35, 139],
      [35.01, 139],
      [35, 139.01],
    ];
    render(<GameResultMap polygon={[...vertices, vertices[0]]} vertices={vertices} />);
    expect(screen.getAllByTestId("vertex")).toHaveLength(3);
    expect(screen.getByTestId("polygon")).toHaveAttribute("data-count", "4");
    const bounds = fitBounds.mock.calls.at(-1)?.[0];
    expect(bounds.getSouthWest().lat).toBe(35);
    expect(bounds.getNorthEast().lat).toBe(35.01);
  });
});
