import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import {
  createFakeGeolocation,
  type FakeGeolocation,
  PERMISSION_DENIED,
  TIMEOUT,
} from "../test/fakeGeolocation";
import { GeoTrackingContainer } from "./GeoTrackingContainer";

describe("GeoTrackingContainer", () => {
  let geolocation: FakeGeolocation;

  beforeEach(() => {
    geolocation = createFakeGeolocation();
  });

  function renderContainer(isSecureContext = true) {
    return render(<GeoTrackingContainer deps={{ geolocation, isSecureContext }} />);
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
});
