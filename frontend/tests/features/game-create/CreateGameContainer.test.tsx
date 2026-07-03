import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { SWRConfig } from "swr";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { type TopoApi, topoApi } from "@/api/topoApi";
import type { ConfigResponse, CreateGameResponse } from "@/api/types";
import { CreateGameContainer } from "@/features/game-create/CreateGameContainer";

// config / createGame を注入して作成フローを検証する（実 HTTP・実ルータ遷移に依存しない）。
const config: ConfigResponse = {
  objectTypes: ["shrine"],
  areaPresets: [
    { key: "small", label: "お手軽", sqm: 500_000 },
    { key: "medium", label: "ふつう", sqm: 2_000_000 },
    { key: "large", label: "がっつり", sqm: 10_000_000 },
  ],
};

// 現在 URL を可視化して URL 遷移を検証するためのプローブ。
function LocationProbe() {
  const location = useLocation();
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>;
}

// SWR キャッシュはテストごとに provider（新規 Map）で隔離する。
function renderWithRouter(api: TopoApi) {
  return render(
    <SWRConfig value={{ provider: () => new Map() }}>
      <MemoryRouter initialEntries={["/"]}>
        <Routes>
          <Route path="/" element={<CreateGameContainer api={api} />} />
          <Route path="/game/:gameId" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </SWRConfig>,
  );
}

describe("CreateGameContainer", () => {
  let fetchConfig: ReturnType<typeof vi.fn>;
  let createGame: ReturnType<typeof vi.fn>;
  let api: TopoApi;

  beforeEach(() => {
    fetchConfig = vi.fn().mockResolvedValue(config);
    createGame = vi
      .fn()
      .mockResolvedValue({ gameId: "game-123", playerId: "player-456" } as CreateGameResponse);
    api = { fetchConfig, createGame } as unknown as TopoApi;
  });

  it("test_createScreen_buildsOptionsFromConfig", async () => {
    renderWithRouter(api);
    // config 取得後に選択肢が動的構築される。
    await waitFor(() => {
      expect(screen.getByLabelText("対象種別")).toBeInTheDocument();
    });
    expect(screen.getByRole("option", { name: "shrine" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "お手軽" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "ふつう" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "がっつり" })).toBeInTheDocument();
  });

  it("test_createScreen_onSubmit_callsCreateGameWithSelectedValues", async () => {
    const user = userEvent.setup();
    renderWithRouter(api);
    await waitFor(() => screen.getByLabelText("面積プリセット"));

    await user.selectOptions(screen.getByLabelText("面積プリセット"), "large");
    await user.type(screen.getByLabelText("表示名"), "たろう");
    await user.click(screen.getByRole("button", { name: "ゲームを作成" }));

    await waitFor(() => {
      expect(createGame).toHaveBeenCalledWith({
        objectType: "shrine",
        areaPreset: "large",
        playerCount: 3,
        displayName: "たろう",
      });
    });
  });

  it("test_createScreen_onSubmitWithoutName_sendsUndefinedDisplayName", async () => {
    const user = userEvent.setup();
    renderWithRouter(api);
    await waitFor(() => screen.getByRole("button", { name: "ゲームを作成" }));

    await user.click(screen.getByRole("button", { name: "ゲームを作成" }));

    await waitFor(() => {
      expect(createGame).toHaveBeenCalledWith({
        objectType: "shrine",
        areaPreset: "small",
        playerCount: 3,
        displayName: undefined,
      });
    });
  });

  it("test_createScreen_onSuccess_navigatesToGamePathWithPlayerQuery", async () => {
    const user = userEvent.setup();
    renderWithRouter(api);
    await waitFor(() => screen.getByRole("button", { name: "ゲームを作成" }));

    await user.click(screen.getByRole("button", { name: "ゲームを作成" }));

    // 作成後 URL が /game/<gameId>?p=<playerId> になる。
    await waitFor(() => {
      expect(screen.getByTestId("location")).toHaveTextContent("/game/game-123?p=player-456");
    });
  });

  it("test_createScreen_withDefaultApi_usesSharedSingletonTopoApi", async () => {
    // api 未注入時は共有シングルトン topoApi を使う（毎レンダリング生成しない）。
    const spy = vi.spyOn(topoApi, "fetchConfig").mockResolvedValue(config);

    render(
      <SWRConfig value={{ provider: () => new Map() }}>
        <MemoryRouter initialEntries={["/"]}>
          <Routes>
            <Route path="/" element={<CreateGameContainer />} />
          </Routes>
        </MemoryRouter>
      </SWRConfig>,
    );

    await screen.findByLabelText("対象種別");
    expect(spy).toHaveBeenCalledTimes(1);
    spy.mockRestore();
  });

  it("test_createScreen_whenConfigFails_showsErrorState", async () => {
    fetchConfig.mockRejectedValue(new Error("boom"));
    renderWithRouter(api);
    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
    expect(screen.queryByLabelText("対象種別")).not.toBeInTheDocument();
  });
});
