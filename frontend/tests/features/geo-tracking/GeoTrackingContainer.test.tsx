import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { CurrentAreaPayload, PlayerPayload } from "@/api/types";
import { GeoTrackingContainer } from "@/features/geo-tracking/GeoTrackingContainer";
import {
  createFakeGeolocation,
  type FakeGeolocation,
  PERMISSION_DENIED,
  TIMEOUT,
} from "../../fakeGeolocation";
import { createFakeTopoApi, type FakeTopoApi, player } from "../../fakeTopoApi";

describe("GeoTrackingContainer", () => {
  let geolocation: FakeGeolocation;
  let fake: FakeTopoApi;

  beforeEach(() => {
    geolocation = createFakeGeolocation();
    fake = createFakeTopoApi();
  });

  function renderContainer(isSecureContext = true) {
    return render(
      <GeoTrackingContainer
        gameId="game-123"
        playerId="player-1"
        api={fake.api}
        deps={{ geolocation, isSecureContext }}
      />,
    );
  }

  it("test_initial_secureContext_showsMapWithoutErrorOrBanner", () => {
    const { container } = renderContainer();
    expect(container.querySelector(".leaflet-container")).not.toBeNull();
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("test_firstPermissionDenied_showsErrorScreenAndHidesMap", () => {
    const { container } = renderContainer();
    act(() => {
      geolocation.emitError(PERMISSION_DENIED);
    });
    // 拒否でエラー画面・地図を出さない（フォールバックしない）。
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
    expect(container.querySelector(".leaflet-container")).toBeNull();
    expect(screen.getByRole("button", { name: "再試行" })).toBeInTheDocument();
  });

  it("test_retry_afterDenied_thenSuccess_recoversToMap", async () => {
    const user = userEvent.setup();
    const { container } = renderContainer();
    act(() => {
      geolocation.emitError(PERMISSION_DENIED);
    });
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "再試行" }));
    // 許可済みに変わった想定で success を流すと地図に復帰する。
    act(() => {
      geolocation.emitSuccess({ latitude: 35.0, longitude: 139.0 });
    });
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(container.querySelector(".leaflet-container")).not.toBeNull();
  });

  it("test_timeoutAfterTracking_keepsMapAndShowsDegradedBanner", () => {
    const { container } = renderContainer();
    act(() => {
      geolocation.emitSuccess({ latitude: 35.0, longitude: 139.0 });
    });
    act(() => {
      geolocation.emitError(TIMEOUT);
    });
    // 地図は維持され、控えめバナーが出る。エラー画面には飛ばない。
    expect(container.querySelector(".leaflet-container")).not.toBeNull();
    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("test_insecureContext_showsErrorScreenWithoutRetry", () => {
    renderContainer(false);
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
    // 非安全コンテキストは再試行しても解決しないので再試行ボタンを出さない。
    expect(screen.queryByRole("button", { name: "再試行" })).not.toBeInTheDocument();
    expect(geolocation.watchPosition).not.toHaveBeenCalled();
  });

  // US-09: 友達ドット・凸包ポリゴンの可視描画。API を呼ぶのは本 Smart のみで、Dumb には props で渡す。
  function renderActive(players: PlayerPayload[], currentArea: CurrentAreaPayload | null = null) {
    return render(
      <GeoTrackingContainer
        gameId="game-123"
        playerId="player-1"
        api={fake.api}
        deps={{ geolocation, isSecureContext: true }}
        players={players}
        currentArea={currentArea}
      />,
    );
  }

  it("test_ACTIVE_他プレイヤーのlive_友達ドットがN-1個描かれる", () => {
    // 3 人中、自分（player-1）以外の 2 人が live を持つ → 友達ドット 2。
    const { container } = renderActive([
      player({ playerId: "player-1" }),
      player({
        playerId: "player-2",
        displayName: "ボブ",
        live: { lat: 35.1, lng: 139.1, at: "2026-07-06T12:00:00Z" },
        online: true,
      }),
      player({
        playerId: "player-3",
        displayName: "キャロル",
        live: { lat: 35.2, lng: 139.2, at: "2026-07-06T12:00:00Z" },
        online: true,
      }),
    ]);
    expect(container.querySelectorAll(".live-marker").length).toBe(2);
    expect(screen.getByText("ボブ")).toBeInTheDocument();
    expect(screen.getByText("キャロル")).toBeInTheDocument();
  });

  it("test_自分要素_友達ドットに含めない", () => {
    // GET 内に自分（player-1）の live が含まれても友達ドットに含めない（自分は watchPosition で別描画・F1）。
    const { container } = renderActive([
      player({
        playerId: "player-1",
        displayName: "わたし",
        live: { lat: 35.0, lng: 139.0, at: "2026-07-06T12:00:00Z" },
        online: true,
      }),
      player({
        playerId: "player-2",
        displayName: "ボブ",
        live: { lat: 35.1, lng: 139.1, at: "2026-07-06T12:00:00Z" },
        online: true,
      }),
    ]);
    // 友達ドットは 1（ボブのみ）。自分は含めない。
    expect(container.querySelectorAll(".live-marker").length).toBe(1);
    expect(screen.queryByText("わたし")).not.toBeInTheDocument();
    expect(screen.getByText("ボブ")).toBeInTheDocument();
  });

  it("test_liveがnullのplayer_ドットが描かれない", () => {
    const { container } = renderActive([
      player({ playerId: "player-1" }),
      player({ playerId: "player-2", displayName: "ボブ", live: null, online: false }),
    ]);
    expect(container.querySelectorAll(".live-marker").length).toBe(0);
    expect(screen.queryByText("ボブ")).not.toBeInTheDocument();
  });

  it("test_onlineがfalse_グレーアウトで描かれる", () => {
    const { container } = renderActive([
      player({ playerId: "player-1" }),
      player({
        playerId: "player-2",
        displayName: "ボブ",
        live: { lat: 35.1, lng: 139.1, at: "2026-07-06T12:00:00Z" },
        online: false,
      }),
    ]);
    expect(container.querySelector(".live-marker--offline")).not.toBeNull();
  });

  it("test_currentAreaあり_凸包ポリゴンが描かれる", () => {
    const { container } = renderActive([player({ playerId: "player-1" })], {
      sqm: 500000,
      hull: [
        [35.0, 139.0],
        [35.1, 139.1],
        [35.0, 139.2],
        [35.0, 139.0],
      ],
    });
    expect(container.querySelector("path.leaflet-interactive")).not.toBeNull();
  });

  it("test_currentAreaがnull_ポリゴンが描かれない", () => {
    const { container } = renderActive([player({ playerId: "player-1" })], null);
    expect(container.querySelector("path.leaflet-interactive")).toBeNull();
  });

  it("test_sqm0_線分として破綻せず描かれる", () => {
    // 退化（一直線・sqm=0）。クラッシュせず SVG path（線分）が出る。
    const { container } = renderActive([player({ playerId: "player-1" })], {
      sqm: 0,
      hull: [
        [35.0, 139.0],
        [35.0, 139.1],
        [35.0, 139.2],
      ],
    });
    expect(container.querySelector("path.leaflet-interactive")).not.toBeNull();
  });

  it("test_全体表示ボタンが地図上に出る", () => {
    renderActive([player({ playerId: "player-1" })], null);
    expect(screen.getByRole("button", { name: "全体表示" })).toBeInTheDocument();
  });

  it("test_情報マスキング_objectCountや正多角形スコアを描画しない", () => {
    // 形＋（US-10 の）面積数値までが開示範囲。objectCount・正多角形スコアは伏せる（回帰防止）。
    renderActive([player({ playerId: "player-1" })], {
      sqm: 500000,
      hull: [
        [35.0, 139.0],
        [35.1, 139.1],
        [35.0, 139.2],
        [35.0, 139.0],
      ],
    });
    expect(
      screen.queryByText(/オブジェクト数|objectCount|正多角形|スコア/i),
    ).not.toBeInTheDocument();
  });

  describe("ライブ位置送信（US-07）", () => {
    beforeEach(() => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it("test_activeMap_selfLocationUpdates_sendsAtMostOncePer2s", async () => {
      renderContainer();
      // GPS を高頻度に発火させても、送信は 2 秒に 1 回以下に間引かれる。
      for (let i = 0; i < 10; i++) {
        act(() => {
          geolocation.emitSuccess({ latitude: 35.0 + i * 0.0001, longitude: 139.0 });
        });
      }
      expect(fake.updateLocation).not.toHaveBeenCalled();

      await vi.advanceTimersByTimeAsync(2000);
      expect(fake.updateLocation).toHaveBeenCalledTimes(1);

      await vi.advanceTimersByTimeAsync(2000);
      expect(fake.updateLocation).toHaveBeenCalledTimes(2);
    });

    it("test_sendPayload_isGameIdPlayerIdAndLatLng", async () => {
      renderContainer();
      act(() => {
        geolocation.emitSuccess({ latitude: 35.68, longitude: 139.76 });
      });

      await vi.advanceTimersByTimeAsync(2000);

      expect(fake.updateLocation).toHaveBeenCalledWith("game-123", "player-1", {
        lat: 35.68,
        lng: 139.76,
      });
    });

    it("test_sendFailure_keepsMapAndDoesNotShowErrorScreen", async () => {
      fake.updateLocation.mockRejectedValue(new Error("network"));
      const { container } = renderContainer();
      act(() => {
        geolocation.emitSuccess({ latitude: 35.68, longitude: 139.76 });
      });

      await vi.advanceTimersByTimeAsync(2000);

      // 送信が失敗しても地図は維持され、エラー画面（alertdialog）に遷移しない。
      expect(fake.updateLocation).toHaveBeenCalled();
      expect(container.querySelector(".leaflet-container")).not.toBeNull();
      expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    });
  });
});
