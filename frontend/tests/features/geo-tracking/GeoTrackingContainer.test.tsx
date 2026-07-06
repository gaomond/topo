import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GeoTrackingContainer } from "@/features/geo-tracking/GeoTrackingContainer";
import {
  createFakeGeolocation,
  type FakeGeolocation,
  PERMISSION_DENIED,
  TIMEOUT,
} from "../../fakeGeolocation";
import { createFakeTopoApi, type FakeTopoApi } from "../../fakeTopoApi";

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
