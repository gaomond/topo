// テスト用フェイク Geolocation。
//
// `watchPosition` / `clearWatch` を制御し、success / error コールバックを任意に発火できる。
// 実ブラウザ・実 GPS に依存せず、useGeoTracking の状態遷移（D4/D5）を Vitest で検証するために使う。

import { type Mock, vi } from "vitest";

// GeolocationPositionError のコード（実ブラウザの定数と同値）。
export const PERMISSION_DENIED = 1;
export const POSITION_UNAVAILABLE = 2;
export const TIMEOUT = 3;

type WatchHandlers = {
  onSuccess: PositionCallback;
  onError: PositionErrorCallback | undefined;
};

// watchPosition / clearWatch は Vitest モックとして公開し、呼び出し回数・引数を検証できるようにする。
// Geolocation インターフェースを満たしつつ、テストからは .mock にアクセスできる型にする。
export type FakeGeolocation = Geolocation & {
  watchPosition: Mock<Geolocation["watchPosition"]>;
  clearWatch: Mock<Geolocation["clearWatch"]>;
  // 現在アクティブな watch の数（clearWatch で減る）。
  activeWatchCount: () => number;
  // 直近に登録された watch ハンドラに success を流す。accuracy 省略時は 1（メートル）。
  emitSuccess: (coords: { latitude: number; longitude: number; accuracy?: number }) => void;
  // 直近に登録された watch ハンドラに error を流す。
  emitError: (code: number) => void;
};

// 位置オブジェクトを最小構成で生成する（テストで使う coords のみ埋める）。
function makePosition(coords: {
  latitude: number;
  longitude: number;
  accuracy?: number;
}): GeolocationPosition {
  return {
    coords: {
      latitude: coords.latitude,
      longitude: coords.longitude,
      accuracy: coords.accuracy ?? 1,
      altitude: null,
      altitudeAccuracy: null,
      heading: null,
      speed: null,
      toJSON() {
        return this;
      },
    },
    timestamp: Date.now(),
    toJSON() {
      return this;
    },
  };
}

function makeError(code: number): GeolocationPositionError {
  return {
    code,
    message: `fake error ${code}`,
    PERMISSION_DENIED,
    POSITION_UNAVAILABLE,
    TIMEOUT,
  };
}

/**
 * フェイク Geolocation を生成する。
 * watchPosition / clearWatch は vi.fn() なので呼び出し回数・引数を検証できる。
 */
export function createFakeGeolocation(): FakeGeolocation {
  const watches = new Map<number, WatchHandlers>();
  let nextId = 1;

  const watchPosition = vi.fn(
    (onSuccess: PositionCallback, onError?: PositionErrorCallback | null): number => {
      const id = nextId++;
      watches.set(id, { onSuccess, onError: onError ?? undefined });
      return id;
    },
  );

  const clearWatch = vi.fn((id: number): void => {
    watches.delete(id);
  });

  const getCurrentPosition = vi.fn();

  function latestWatch(): WatchHandlers {
    const ids = [...watches.keys()];
    const lastId = ids.at(-1);
    if (lastId === undefined) {
      throw new Error("アクティブな watch がありません");
    }
    const handlers = watches.get(lastId);
    if (handlers === undefined) {
      throw new Error("watch ハンドラが見つかりません");
    }
    return handlers;
  }

  return {
    watchPosition,
    clearWatch,
    getCurrentPosition,
    activeWatchCount: () => watches.size,
    emitSuccess: (coords) => {
      latestWatch().onSuccess(makePosition(coords));
    },
    emitError: (code) => {
      latestWatch().onError?.(makeError(code));
    },
  };
}
