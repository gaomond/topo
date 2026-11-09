import { fireEvent, render, screen } from "@testing-library/react";
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
  Marker: ({
    position,
    title,
    children,
  }: {
    position: number[];
    title: string;
    children: React.ReactNode;
  }) => (
    <button
      type="button"
      aria-label={title}
      data-testid="object-marker"
      data-position={JSON.stringify(position)}
    >
      {children}
    </button>
  ),
  Popup: ({ children }: { children: React.ReactNode }) => <span>{children}</span>,
  useMap: () => ({ fitBounds }),
}));
describe("GameResultMap", () => {
  it("test_render_objects_preservesDistinctIdsAndDisplaysNamesAsText", () => {
    const polygon: [number, number][] = [
      [35, 139],
      [35.01, 139],
      [35, 139.01],
      [35, 139],
    ];
    const names = ["<img src=x onerror=alert(1)>", null, "", "   "];
    const { container } = render(
      <GameResultMap
        polygon={polygon}
        vertices={polygon.slice(0, -1)}
        objects={names.map((name, index) => ({
          id: String(index),
          name,
          objectType: "shrine",
          lat: 35,
          lng: 139,
        }))}
      />,
    );
    expect(screen.getAllByTestId("object-marker")).toHaveLength(4);
    expect(screen.getAllByTestId("vertex")).toHaveLength(3);
    expect(screen.getAllByTestId("object-marker")[0]).toHaveAttribute("data-position", "[35,139]");
    fireEvent.click(screen.getByRole("button", { name: names[0] as string }));
    expect(screen.getByText(names[0] as string)).toBeInTheDocument();
    expect(screen.getAllByText("名称未登録")).toHaveLength(3);
    expect(container.querySelector("img")).toBeNull();
  });
  it("test_render_polygon_fitsBoundsAndRendersEachVertex", () => {
    const vertices: [number, number][] = [
      [35, 139],
      [35.01, 139],
      [35, 139.01],
    ];
    render(<GameResultMap polygon={[...vertices, vertices[0]]} vertices={vertices} objects={[]} />);
    expect(screen.getAllByTestId("vertex")).toHaveLength(3);
    expect(screen.getByTestId("polygon")).toHaveAttribute("data-count", "4");
    const bounds = fitBounds.mock.calls.at(-1)?.[0];
    expect(bounds.getSouthWest().lat).toBe(35);
    expect(bounds.getNorthEast().lat).toBe(35.01);
  });
});
