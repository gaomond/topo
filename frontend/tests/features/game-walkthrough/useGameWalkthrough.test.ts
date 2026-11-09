import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import {
  useGameWalkthrough,
  WALKTHROUGH_STORAGE_KEY,
} from "@/features/game-walkthrough/useGameWalkthrough";

function createStorage(saved: string | null = null) {
  let value = saved;
  return {
    getItem: vi.fn(() => value),
    setItem: vi.fn((_key: string, next: string) => {
      value = next;
    }),
  };
}

describe("useGameWalkthrough", () => {
  it("test_initialize_unread_keepsLocationDisabledUntilFinish", () => {
    const storage = createStorage();
    const { result, rerender } = renderHook(() => useGameWalkthrough(() => storage));
    expect(result.current.isOpen).toBe(true);
    expect(result.current.locationEnabled).toBe(false);
    rerender();
    expect(result.current.locationEnabled).toBe(false);
    act(() => result.current.finish());
    expect(result.current.isOpen).toBe(false);
    expect(result.current.locationEnabled).toBe(true);
    expect(storage.setItem).toHaveBeenCalledWith(WALKTHROUGH_STORAGE_KEY, "true");
  });

  it("test_reopen_finished_keepsLocationEnabledAndRestoresOpenState", () => {
    const storage = createStorage();
    const { result } = renderHook(() => useGameWalkthrough(() => storage));
    act(() => result.current.finish());
    act(() => result.current.open());
    expect(result.current.isOpen).toBe(true);
    expect(result.current.locationEnabled).toBe(true);
    act(() => result.current.finish());
    expect(result.current.locationEnabled).toBe(true);
  });

  it("test_startLocationTracking_introNext_startsWithoutClosingOrRecording", () => {
    const storage = createStorage();
    const { result } = renderHook(() => useGameWalkthrough(() => storage));
    act(() => result.current.startLocationTracking());
    expect(result.current.locationEnabled).toBe(true);
    expect(result.current.isOpen).toBe(true);
    expect(storage.setItem).not.toHaveBeenCalled();
  });

  it("test_initialize_finishedInAnotherGame_doesNotAutomaticallyOpen", () => {
    const storage = createStorage("true");
    const { result } = renderHook(() => useGameWalkthrough(() => storage));
    expect(result.current.isOpen).toBe(false);
    expect(result.current.locationEnabled).toBe(true);
  });

  it("test_unmount_unfinished_doesNotRecordCompletionAndReopensFromStart", () => {
    const storage = createStorage();
    const first = renderHook(() => useGameWalkthrough(() => storage));
    first.unmount();
    expect(storage.setItem).not.toHaveBeenCalled();
    const second = renderHook(() => useGameWalkthrough(() => storage));
    expect(second.result.current.isOpen).toBe(true);
    expect(second.result.current.locationEnabled).toBe(false);
  });

  it("test_finish_storageReadAndWriteFail_stillAllowsPlayingWithoutReopening", () => {
    const getStorage = () => {
      throw new Error("保存領域を利用できません");
    };
    const { result, rerender } = renderHook(() => useGameWalkthrough(getStorage));
    expect(result.current.isOpen).toBe(true);
    act(() => result.current.finish());
    rerender();
    expect(result.current.isOpen).toBe(false);
    expect(result.current.locationEnabled).toBe(true);
  });

  it("test_finish_storageWriteFails_keepsCompletionInMemory", () => {
    const storage = createStorage();
    storage.setItem.mockImplementation(() => {
      throw new Error("保存不可");
    });
    const { result, rerender } = renderHook(() => useGameWalkthrough(() => storage));
    act(() => result.current.finish());
    rerender();
    expect(result.current.isOpen).toBe(false);
    expect(result.current.locationEnabled).toBe(true);
  });
});
