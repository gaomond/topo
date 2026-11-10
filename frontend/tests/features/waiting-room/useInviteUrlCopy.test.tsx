import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useInviteUrlCopy } from "@/features/waiting-room/useInviteUrlCopy";

describe("useInviteUrlCopy", () => {
  it("test_copy_unmounted_ignoresPendingSuccessAndCreatesNoTimer", async () => {
    vi.useFakeTimers();
    let resolvePending: () => void = () => {};
    const clipboard = {
      writeText: vi.fn(
        () =>
          new Promise<void>((resolve) => {
            resolvePending = resolve;
          }),
      ),
    };
    const { result, unmount } = renderHook(() => useInviteUrlCopy("url", clipboard));
    let pending: Promise<void>;
    act(() => {
      pending = result.current.copyInviteUrl();
    });
    unmount();
    await act(async () => {
      resolvePending();
      await pending;
    });
    expect(vi.getTimerCount()).toBe(0);
  });
  afterEach(() => vi.useRealTimers());
  it("test_copy_repeatedSuccess_resetsAfterLastSuccessAndCopiesFullUrl", async () => {
    vi.useFakeTimers();
    const clipboard = { writeText: vi.fn().mockResolvedValue(undefined) };
    const url = "https://topo.example/game/long-id";
    const { result } = renderHook(() => useInviteUrlCopy(url, clipboard));
    await act(() => result.current.copyInviteUrl());
    expect(clipboard.writeText).toHaveBeenCalledWith(url);
    expect(result.current.status).toBe("copied");
    act(() => vi.advanceTimersByTime(1500));
    await act(() => result.current.copyInviteUrl());
    act(() => vi.advanceTimersByTime(1500));
    expect(result.current.status).toBe("copied");
    act(() => vi.advanceTimersByTime(500));
    expect(result.current.status).toBe("idle");
    expect(clipboard.writeText).toHaveBeenCalledTimes(2);
  });

  it("test_copy_failureAndRetry_showsFailureThenRecovers", async () => {
    const clipboard = {
      writeText: vi
        .fn()
        .mockRejectedValueOnce(new Error("denied"))
        .mockResolvedValueOnce(undefined),
    };
    const { result } = renderHook(() => useInviteUrlCopy("url", clipboard));
    await act(() => result.current.copyInviteUrl());
    expect(result.current.status).toBe("failed");
    await act(() => result.current.copyInviteUrl());
    expect(result.current.status).toBe("copied");
  });

  it("test_copy_missingClipboard_reportsFailureWithoutThrowing", async () => {
    const { result } = renderHook(() => useInviteUrlCopy("url", undefined));
    await act(() => result.current.copyInviteUrl());
    expect(result.current.status).toBe("failed");
  });

  it("test_copy_urlChanged_ignoresOldResolutionAndTimer", async () => {
    vi.useFakeTimers();
    let resolveOld: () => void = () => {};
    const clipboard = {
      writeText: vi
        .fn()
        .mockImplementationOnce(
          () =>
            new Promise<void>((resolve) => {
              resolveOld = resolve;
            }),
        )
        .mockResolvedValue(undefined),
    };
    const { result, rerender } = renderHook(({ url }) => useInviteUrlCopy(url, clipboard), {
      initialProps: { url: "old" },
    });
    let pending: Promise<void>;
    act(() => {
      pending = result.current.copyInviteUrl();
    });
    rerender({ url: "new" });
    await act(async () => {
      resolveOld();
      await pending;
    });
    expect(result.current.status).toBe("idle");
    await act(() => result.current.copyInviteUrl());
    expect(clipboard.writeText).toHaveBeenLastCalledWith("new");
    act(() => vi.advanceTimersByTime(2000));
    expect(result.current.status).toBe("idle");
  });

  it("test_copy_outOfOrderResponse_keepsLatestResult", async () => {
    let rejectOld: (reason: Error) => void = () => {};
    const clipboard = {
      writeText: vi
        .fn()
        .mockImplementationOnce(
          () =>
            new Promise<void>((_, reject) => {
              rejectOld = reject;
            }),
        )
        .mockResolvedValue(undefined),
    };
    const { result, unmount } = renderHook(() => useInviteUrlCopy("url", clipboard));
    let pending: Promise<void>;
    act(() => {
      pending = result.current.copyInviteUrl();
    });
    await act(() => result.current.copyInviteUrl());
    await act(async () => {
      rejectOld(new Error("late"));
      await pending;
    });
    expect(result.current.status).toBe("copied");
    unmount();
  });
});
