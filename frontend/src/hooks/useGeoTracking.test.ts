import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import {
  createFakeGeolocation,
  type FakeGeolocation,
  PERMISSION_DENIED,
  POSITION_UNAVAILABLE,
  TIMEOUT,
} from "../test/fakeGeolocation";
import {
  DEGRADED_MESSAGE,
  GeoState,
  INSECURE_CONTEXT_MESSAGE,
  PERMISSION_DENIED_MESSAGE,
} from "./geoState";
import { useGeoTracking } from "./useGeoTracking";

describe("useGeoTracking", () => {
  let geolocation: FakeGeolocation;

  beforeEach(() => {
    geolocation = createFakeGeolocation();
  });

  function render() {
    return renderHook(() => useGeoTracking({ geolocation, isSecureContext: true }));
  }

  it("test_mount_secureContext_startsWatchAndInitializing", () => {
    const { result } = render();
    expect(geolocation.watchPosition).toHaveBeenCalledTimes(1);
    expect(result.current.state).toBe(GeoState.INITIALIZING);
    expect(result.current.selfLocation).toBeNull();
  });

  it("test_onSuccess_firstFix_tracksAndSetsSelfLocation", () => {
    const { result } = render();
    act(() => {
      geolocation.emitSuccess({ latitude: 35.0, longitude: 139.0 });
    });
    expect(result.current.state).toBe(GeoState.TRACKING);
    expect(result.current.selfLocation).toEqual({ lat: 35.0, lng: 139.0 });
  });

  it("test_onSuccess_withAccuracy_exposesAccuracyMeters", () => {
    const { result } = render();
    expect(result.current.accuracyMeters).toBeNull();
    act(() => {
      geolocation.emitSuccess({ latitude: 35.0, longitude: 139.0, accuracy: 23.4 });
    });
    expect(result.current.accuracyMeters).toBe(23.4);
  });

  it("test_onSuccess_secondFix_followsToNewLocation", () => {
    const { result } = render();
    act(() => {
      geolocation.emitSuccess({ latitude: 35.0, longitude: 139.0 });
    });
    act(() => {
      geolocation.emitSuccess({ latitude: 36.0, longitude: 140.0 });
    });
    expect(result.current.state).toBe(GeoState.TRACKING);
    expect(result.current.selfLocation).toEqual({ lat: 36.0, lng: 140.0 });
  });

  it("test_onError_firstPermissionDenied_goesToPermissionErrorAndClearsWatch", () => {
    const { result } = render();
    act(() => {
      geolocation.emitError(PERMISSION_DENIED);
    });
    expect(result.current.state).toBe(GeoState.PERMISSION_ERROR);
    expect(result.current.errorMessage).toBe(PERMISSION_DENIED_MESSAGE);
    expect(result.current.canRetry).toBe(true);
    // 拒否時は地図を出さないため selfLocation は null のまま。
    expect(result.current.selfLocation).toBeNull();
    // watch は解除される。
    expect(geolocation.clearWatch).toHaveBeenCalled();
    expect(geolocation.activeWatchCount()).toBe(0);
  });

  it("test_onError_timeoutAfterTracking_goesToDegradedAndKeepsMap", () => {
    const { result } = render();
    act(() => {
      geolocation.emitSuccess({ latitude: 35.0, longitude: 139.0 });
    });
    act(() => {
      geolocation.emitError(TIMEOUT);
    });
    expect(result.current.state).toBe(GeoState.DEGRADED);
    // 地図は維持され、最後の位置も保持される（エラー画面に飛ばない）。
    expect(result.current.selfLocation).toEqual({ lat: 35.0, lng: 139.0 });
  });

  it("test_onError_positionUnavailableAfterTracking_goesToDegraded", () => {
    const { result } = render();
    act(() => {
      geolocation.emitSuccess({ latitude: 35.0, longitude: 139.0 });
    });
    act(() => {
      geolocation.emitError(POSITION_UNAVAILABLE);
    });
    expect(result.current.state).toBe(GeoState.DEGRADED);
  });

  it("test_onError_permissionDeniedAfterTracking_goesToDegradedNotPermissionError", () => {
    const { result } = render();
    act(() => {
      geolocation.emitSuccess({ latitude: 35.0, longitude: 139.0 });
    });
    // 追従中の許可取り消しは DEGRADED にとどめ、エラー画面に飛ばさない（D5）。
    act(() => {
      geolocation.emitError(PERMISSION_DENIED);
    });
    expect(result.current.state).toBe(GeoState.DEGRADED);
  });

  it("test_onSuccess_afterDegraded_recoversToTracking", () => {
    const { result } = render();
    act(() => {
      geolocation.emitSuccess({ latitude: 35.0, longitude: 139.0 });
    });
    act(() => {
      geolocation.emitError(TIMEOUT);
    });
    expect(result.current.state).toBe(GeoState.DEGRADED);
    act(() => {
      geolocation.emitSuccess({ latitude: 37.0, longitude: 141.0 });
    });
    expect(result.current.state).toBe(GeoState.TRACKING);
    expect(result.current.selfLocation).toEqual({ lat: 37.0, lng: 141.0 });
  });

  it("test_retry_afterPermissionError_clearsAndReattachesWatch", () => {
    const { result } = render();
    act(() => {
      geolocation.emitError(PERMISSION_DENIED);
    });
    expect(result.current.state).toBe(GeoState.PERMISSION_ERROR);

    const callsBeforeRetry = geolocation.watchPosition.mock.calls.length;
    act(() => {
      result.current.retry();
    });
    // watchPosition が貼り直され、INITIALIZING に戻る。
    expect(geolocation.watchPosition.mock.calls.length).toBe(callsBeforeRetry + 1);
    expect(result.current.state).toBe(GeoState.INITIALIZING);
  });

  it("test_retry_thenSuccess_recoversToTracking", () => {
    const { result } = render();
    act(() => {
      geolocation.emitError(PERMISSION_DENIED);
    });
    act(() => {
      result.current.retry();
    });
    act(() => {
      geolocation.emitSuccess({ latitude: 35.5, longitude: 139.5 });
    });
    expect(result.current.state).toBe(GeoState.TRACKING);
    expect(result.current.selfLocation).toEqual({ lat: 35.5, lng: 139.5 });
  });

  it("test_mount_insecureContext_goesToPermissionErrorWithoutRetryAndDoesNotWatch", () => {
    const { result } = renderHook(() => useGeoTracking({ geolocation, isSecureContext: false }));
    expect(result.current.state).toBe(GeoState.PERMISSION_ERROR);
    expect(result.current.errorMessage).toBe(INSECURE_CONTEXT_MESSAGE);
    // 非安全コンテキストは再試行しても解決しないので canRetry=false。
    expect(result.current.canRetry).toBe(false);
    // 測位は開始しない。
    expect(geolocation.watchPosition).not.toHaveBeenCalled();
  });

  it("test_mount_noGeolocation_goesToPermissionErrorInsecureMessage", () => {
    const { result } = renderHook(() =>
      useGeoTracking({ geolocation: undefined, isSecureContext: true }),
    );
    expect(result.current.state).toBe(GeoState.PERMISSION_ERROR);
    expect(result.current.canRetry).toBe(false);
  });

  it("test_unmount_clearsWatch", () => {
    const { unmount } = render();
    expect(geolocation.activeWatchCount()).toBe(1);
    unmount();
    expect(geolocation.activeWatchCount()).toBe(0);
  });

  it("test_degradedMessage_isQuietNoticeWording", () => {
    // DEGRADED の文言は控えめ表示の文言であること（地図を隠さない前提の文言）。
    expect(DEGRADED_MESSAGE).toBe("位置更新が滞っています");
  });
});
