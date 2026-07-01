import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { App } from "@/App";

// ルーティングの結線を検証する。CreateGameContainer は既定 API（実 fetch）を使うため、
// ルート `/` では config 取得中の status（読み込み中）の描画で作成画面ルートに乗っていることを確認する。
// 待機画面ルートはパス解釈のみで API に依存しないため文言まで検証する。
describe("App routing", () => {
  it("test_route_root_rendersCreateScreen", async () => {
    render(
      <MemoryRouter initialEntries={["/"]}>
        <App />
      </MemoryRouter>,
    );
    // 作成画面コンテナは初期に「設定を読み込み中…」を出す（config fetch 中）。
    await waitFor(() => {
      expect(screen.getByRole("status")).toBeInTheDocument();
    });
  });

  it("test_route_gamePath_rendersWaitingScreen", () => {
    render(
      <MemoryRouter initialEntries={["/game/game-abc?p=player-xyz"]}>
        <App />
      </MemoryRouter>,
    );
    expect(screen.getByLabelText("待機画面")).toBeInTheDocument();
    expect(screen.getByTestId("game-status")).toHaveTextContent("WAITING");
  });
});
