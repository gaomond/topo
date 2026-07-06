import { renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Coordinate } from "@/features/geo-tracking/geoState";
import { useThrottledLocationSend } from "@/features/geo-tracking/useThrottledLocationSend";

// fake timer で watchPosition 相当の高頻度更新（rerender）を再現し、送信が間引かれることを検証する。
describe("useThrottledLocationSend", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("test_highFrequencyUpdates_sendsAtMostOncePerInterval", () => {
    const send = vi.fn().mockResolvedValue(undefined);
    const { rerender } = renderHook(({ loc }) => useThrottledLocationSend(loc, send, 2000), {
      initialProps: { loc: { lat: 1, lng: 1 } as Coordinate },
    });

    // 2 秒未満で高頻度に更新しても、まだ送信は発生しない。
    for (let i = 0; i < 20; i++) {
      rerender({ loc: { lat: 1 + i * 0.0001, lng: 1 } });
    }
    expect(send).not.toHaveBeenCalled();

    // 1 周期進めると 1 回だけ送信される。
    vi.advanceTimersByTime(2000);
    expect(send).toHaveBeenCalledTimes(1);

    // さらに高頻度更新しても次周期まで送信は増えない。
    for (let i = 0; i < 20; i++) {
      rerender({ loc: { lat: 2 + i * 0.0001, lng: 1 } });
    }
    expect(send).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(2000);
    expect(send).toHaveBeenCalledTimes(2);
  });

  it("test_latestLocation_isSentOnTrailingTick", () => {
    const send = vi.fn().mockResolvedValue(undefined);
    const { rerender } = renderHook(({ loc }) => useThrottledLocationSend(loc, send, 2000), {
      initialProps: { loc: { lat: 1, lng: 1 } as Coordinate },
    });

    // 周期内で複数回更新 → 最後の値だけが送られる（trailing）。
    rerender({ loc: { lat: 10, lng: 10 } });
    rerender({ loc: { lat: 35.68, lng: 139.76 } });
    vi.advanceTimersByTime(2000);

    expect(send).toHaveBeenCalledTimes(1);
    expect(send).toHaveBeenCalledWith({ lat: 35.68, lng: 139.76 });
  });

  it("test_nullLocation_doesNotSend", () => {
    const send = vi.fn().mockResolvedValue(undefined);
    renderHook(() => useThrottledLocationSend(null, send, 2000));

    vi.advanceTimersByTime(6000);
    expect(send).not.toHaveBeenCalled();
  });

  it("test_sendFailure_doesNotThrowAndRetriesNextInterval", async () => {
    const send = vi.fn().mockRejectedValue(new Error("network"));
    renderHook(() => useThrottledLocationSend({ lat: 1, lng: 1 }, send, 2000));

    await vi.advanceTimersByTimeAsync(2000);
    expect(send).toHaveBeenCalledTimes(1);

    // 失敗を握りつぶし、次周期で再送する（地図維持・エラー画面に飛ばさない方針の下支え）。
    await vi.advanceTimersByTimeAsync(2000);
    expect(send).toHaveBeenCalledTimes(2);
  });
});
