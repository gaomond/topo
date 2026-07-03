import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { App } from "@/App";

// ルーティングの結線を検証する。両コンテナとも既定 API（実 fetch）を使うため、ここでは
// API に依存せず初期描画（読み込み中 status）でルートに乗っていることのみを確認する。
// 画面遷移・状態表示の詳細は各コンテナのテスト（API 注入）で検証する。
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

  it("test_route_gamePath_rendersWaitingRoomRoute", async () => {
    render(
      <MemoryRouter initialEntries={["/game/game-abc?p=player-xyz"]}>
        <App />
      </MemoryRouter>,
    );
    // 待機画面コンテナは状態取得中に「読み込み中…」status を出す（GET fetch 中）。
    await waitFor(() => {
      expect(screen.getByRole("status")).toHaveTextContent("読み込み中");
    });
  });
});
