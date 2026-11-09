import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { GameResultView } from "@/features/game-result/GameResultView";

vi.mock("@/features/game-result/GameResultMap", () => ({
  GameResultMap: ({ polygon, vertices }: { polygon: unknown[]; vertices: unknown[] }) => (
    <div
      data-testid="result-map"
      data-polygon-count={polygon.length}
      data-vertex-count={vertices.length}
    />
  ),
}));
const result = {
  areaSqm: 500,
  areaValid: true,
  objectCount: 0,
  objects: [],
  polygon: [
    { lat: 35, lng: 139 },
    { lat: 35.01, lng: 139 },
    { lat: 35, lng: 139.01 },
    { lat: 35, lng: 139 },
  ],
};

describe("GameResultView", () => {
  it("test_render_zeroObjects_showsCompletionAndUniqueVertices", () => {
    render(
      <MemoryRouter>
        <GameResultView result={result} />
      </MemoryRouter>,
    );
    expect(screen.getByRole("heading", { name: "ゲーム完了" })).toBeInTheDocument();
    expect(screen.getByText("0個")).toBeInTheDocument();
    expect(screen.getByText("500 ㎡")).toBeInTheDocument();
    expect(screen.getByTestId("result-map")).toHaveAttribute("data-vertex-count", "3");
    expect(screen.getByTestId("result-map")).toHaveAttribute("data-polygon-count", "4");
    expect(screen.getByRole("link", { name: "新しいゲームを作成" })).toHaveAttribute("href", "/");
  });
  it.each([
    null,
    { ...result, areaValid: false },
    { ...result, polygon: [] },
  ])("test_render_invalidResult_showsErrorWithoutMap %j", (value) => {
    render(
      <MemoryRouter>
        <GameResultView result={value} />
      </MemoryRouter>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      "結果を取得できませんでした（想定外の状態）",
    );
    expect(screen.queryByTestId("result-map")).not.toBeInTheDocument();
    expect(screen.queryByText("0個")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "新しいゲームを作成" })).toHaveAttribute("href", "/");
  });
});
