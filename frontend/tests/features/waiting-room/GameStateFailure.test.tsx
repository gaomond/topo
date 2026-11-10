import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { SWRConfig } from "swr";
import { describe, expect, it } from "vitest";
import { ApiError } from "@/api/topoApi";
import type { GameStateResponse } from "@/api/types";
import { WaitingRoomContainer } from "@/features/waiting-room/WaitingRoomContainer";
import { createFakeGeolocation } from "../../fakeGeolocation";
import { createFakeTopoApi, gameState, player } from "../../fakeTopoApi";

function setup(fake: ReturnType<typeof createFakeTopoApi>) {
  return render(
    <SWRConfig value={{ provider: () => new Map() }}>
      <MemoryRouter initialEntries={["/game/game-123?p=self"]}>
        <Routes>
          <Route
            path="/game/:gameId"
            element={
              <WaitingRoomContainer
                api={fake.api}
                refreshIntervalMs={50}
                geoDeps={{ geolocation: createFakeGeolocation(), isSecureContext: true }}
                getWalkthroughStorage={() => ({ getItem: () => "true", setItem: () => undefined })}
              />
            }
          />
        </Routes>
      </MemoryRouter>
    </SWRConfig>,
  );
}

const snapshot = (overrides: Partial<GameStateResponse> = {}) =>
  gameState({ players: [player({ playerId: "self" })], ...overrides });

describe("ゲーム状態取得の失敗", () => {
  it("test_getGameState_badRequest_showsNotFound", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockRejectedValue(new ApiError(400, "/api/games/game-123"));
    setup(fake);
    expect(await screen.findByRole("alert")).toHaveTextContent("ゲームが見つかりません");
    expect(screen.queryByText("読み込み中…")).not.toBeInTheDocument();
  });

  it.each([
    new ApiError(500, "/api/games/game-123"),
    new ApiError(503, "/api/games/game-123"),
    new TypeError("通信断"),
  ])("test_getGameState_initialFailure_%s_showsRetryAndRecoversWithoutPost", async (failure) => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockRejectedValue(failure);
    setup(fake);
    expect(await screen.findByRole("alert")).toHaveTextContent("ゲーム情報を取得できませんでした");
    let resolve!: (value: GameStateResponse) => void;
    const pending = new Promise<GameStateResponse>((done) => {
      resolve = done;
    });
    fake.getGameState.mockReturnValue(pending);
    await userEvent.click(screen.getByRole("button", { name: "再試行" }));
    expect(screen.getByRole("button", { name: "再取得中…" })).toBeDisabled();
    fake.getGameState.mockResolvedValue(snapshot());
    await act(async () => resolve(snapshot()));
    expect(await screen.findByRole("heading", { name: "待機中" })).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(fake.joinGame).not.toHaveBeenCalled();
    expect(fake.createGame).not.toHaveBeenCalled();
    expect(fake.startGame).not.toHaveBeenCalled();
  });

  it("test_getGameState_repeatedFailure_keepsRetryAvailable", async () => {
    const fake = createFakeTopoApi();
    fake.getGameState.mockRejectedValue(new ApiError(500, "/api/games/game-123"));
    setup(fake);
    await userEvent.click(await screen.findByRole("button", { name: "再試行" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "再試行" })).toBeEnabled());
    expect(fake.getGameState).toHaveBeenCalledTimes(2);
    expect(screen.getByRole("alert")).toHaveTextContent("ゲーム情報を取得できませんでした");
  });

  it.each([
    "WAITING",
    "ACTIVE",
  ])("test_getGameState_pollFailure_%s_preservesScreenAndRecovers", async (status) => {
    const fake = createFakeTopoApi();
    fake.getGameState
      .mockResolvedValueOnce(snapshot({ status }))
      .mockRejectedValue(new ApiError(503, "/api/games/game-123"));
    setup(fake);
    if (status === "WAITING") await screen.findByRole("heading", { name: "待機中" });
    else await screen.findByRole("button", { name: "遊び方" });
    const map = document.querySelector(".leaflet-container");
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "最後に取得した情報を表示しています",
    );
    if (status === "WAITING")
      expect(screen.getByTestId("participant-count")).toHaveTextContent("1");
    else expect(document.querySelector(".leaflet-container")).toBe(map);
    fake.getGameState.mockResolvedValue(snapshot({ status }));
    await userEvent.click(screen.getByRole("button", { name: "再試行" }));
    await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
    if (status === "ACTIVE") expect(document.querySelector(".leaflet-container")).toBe(map);
    expect(fake.joinGame).not.toHaveBeenCalled();
  });
});
