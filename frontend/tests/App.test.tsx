import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { SWRConfig } from "swr";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "@/App";
import { topoApi } from "@/api/topoApi";

// 作成画面は読込中も案内を表示し、ゲームは待機状態が確認できるまで表示しない。
describe("App routing", () => {
  beforeEach(() => {
    vi.spyOn(topoApi, "fetchConfig").mockImplementation(() => new Promise(() => {}));
    vi.spyOn(topoApi, "getGameState").mockImplementation(() => new Promise(() => {}));
  });
  afterEach(() => vi.restoreAllMocks());
  it("test_route_unknownPath_showsNotFoundAndCanReturnToCreate", async () => {
    render(
      <SWRConfig value={{ provider: () => new Map() }}>
        <MemoryRouter initialEntries={["/not-a-real-route"]}>
          <App />
        </MemoryRouter>
      </SWRConfig>,
    );
    expect(screen.getByRole("heading", { name: "ページが見つかりません" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("link", { name: "ゲームを作成する" }));
    expect(await screen.findByRole("heading", { name: /歩いて、囲んで、/ })).toBeInTheDocument();
  });
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
    await userEvent.click(screen.getByRole("button", { name: "licences" }));
    expect(screen.getByRole("dialog", { name: "Licences" })).toBeInTheDocument();
    expect(topoApi.fetchConfig).toHaveBeenCalledTimes(1);
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
    expect(screen.queryByRole("button", { name: "licences" })).not.toBeInTheDocument();
    expect(topoApi.getGameState).toHaveBeenCalledTimes(1);
  });
});
