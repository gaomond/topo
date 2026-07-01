// 測位フック（Smart）。
//
// 本ストーリーで唯一の状態保持・副作用担当。Geolocation `watchPosition` の取得・
// 許可/エラー状態の解釈・状態遷移（INITIALIZING/TRACKING/DEGRADED/PERMISSION_ERROR）と
// everTracked による D4/D5 分岐・retry を所有する。旧 geo-tracker.js を 1:1 で React 化したもの。
//
// gameId / playerId は一切参照しない。サーバー送信もしない。座標計算もしない（追従はピン移動のみ）。

import { useCallback, useEffect, useRef, useState } from "react";
import {
  type Coordinate,
  GeoState,
  INSECURE_CONTEXT_MESSAGE,
  PERMISSION_DENIED_MESSAGE,
} from "./geoState";

// 旧実装の WATCH_OPTIONS を踏襲。
const WATCH_OPTIONS: PositionOptions = {
  enableHighAccuracy: true,
  timeout: 10000,
  maximumAge: 0,
};

export type UseGeoTrackingDeps = {
  // テスト用に注入可。既定は navigator.geolocation。
  geolocation?: Geolocation;
  // テスト用に注入可。既定は window.isSecureContext。
  isSecureContext?: boolean;
};

export type UseGeoTrackingResult = {
  state: GeoState;
  selfLocation: Coordinate | null;
  // 直近の測位精度（メートル / coords.accuracy）。未測位は null。
  accuracyMeters: number | null;
  // PERMISSION_ERROR のときのみ意味を持つエラー文言。
  errorMessage: string | null;
  // 再試行可能か（非安全コンテキスト由来の PERMISSION_ERROR では false）。
  canRetry: boolean;
  retry: () => void;
};

function resolveGeolocation(injected: Geolocation | undefined): Geolocation | undefined {
  if (injected !== undefined) {
    return injected;
  }
  return typeof navigator !== "undefined" ? navigator.geolocation : undefined;
}

function resolveSecureContext(injected: boolean | undefined): boolean {
  if (injected !== undefined) {
    return injected;
  }
  return typeof window !== "undefined" ? window.isSecureContext : false;
}

export function useGeoTracking(deps: UseGeoTrackingDeps = {}): UseGeoTrackingResult {
  const geolocation = resolveGeolocation(deps.geolocation);
  const secureContext = resolveSecureContext(deps.isSecureContext);

  const [state, setState] = useState<GeoState>(GeoState.INITIALIZING);
  const [selfLocation, setSelfLocation] = useState<Coordinate | null>(null);
  const [accuracyMeters, setAccuracyMeters] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [canRetry, setCanRetry] = useState(false);

  // 一度でも測位に成功したか（初回拒否と追従中失敗を分ける判定軸）。
  // 再レンダリングで揺れないよう ref で保持する（旧実装のローカル変数と等価）。
  const everTrackedRef = useRef(false);
  const watchIdRef = useRef<number | null>(null);

  const clearCurrentWatch = useCallback(() => {
    if (geolocation !== undefined && watchIdRef.current !== null) {
      geolocation.clearWatch(watchIdRef.current);
      watchIdRef.current = null;
    }
  }, [geolocation]);

  const onSuccess = useCallback((position: GeolocationPosition) => {
    const { latitude, longitude, accuracy } = position.coords;
    everTrackedRef.current = true;
    setSelfLocation({ lat: latitude, lng: longitude });
    setAccuracyMeters(accuracy);
    setErrorMessage(null);
    setState(GeoState.TRACKING);
  }, []);

  const onError = useCallback(
    (error: GeolocationPositionError) => {
      const isPermissionDenied = error.code === error.PERMISSION_DENIED;

      if (!everTrackedRef.current && isPermissionDenied) {
        // 初回拒否（D4）: エラー画面・地図を出さない。watch を解除する。
        clearCurrentWatch();
        setErrorMessage(PERMISSION_DENIED_MESSAGE);
        setCanRetry(true);
        setState(GeoState.PERMISSION_ERROR);
        return;
      }

      // 追従に入った後の一時失敗、または初回の非許可系失敗（D5）:
      // 地図を維持し控えめ表示にとどめる（エラー画面に遷移しない）。
      setState(GeoState.DEGRADED);
    },
    [clearCurrentWatch],
  );

  const startWatch = useCallback(() => {
    if (geolocation === undefined) {
      return;
    }
    clearCurrentWatch();
    watchIdRef.current = geolocation.watchPosition(onSuccess, onError, WATCH_OPTIONS);
  }, [geolocation, clearCurrentWatch, onSuccess, onError]);

  // 起動。安全コンテキスト/Geolocation の有無を確認してから測位を開始する。
  const start = useCallback(() => {
    if (!secureContext || geolocation === undefined) {
      // 地図は描画したまま、測位だけ不可である旨を明示する（再試行不可）。
      setErrorMessage(INSECURE_CONTEXT_MESSAGE);
      setCanRetry(false);
      setState(GeoState.PERMISSION_ERROR);
      return;
    }
    startWatch();
  }, [secureContext, geolocation, startWatch]);

  // 再試行: 既存 watch を貼り直す。許可済みに変わっていれば TRACKING に復帰する。
  // 非安全/未提供では測位を開始しない（防御的に再確認する）。
  const retry = useCallback(() => {
    if (!secureContext || geolocation === undefined) {
      setErrorMessage(INSECURE_CONTEXT_MESSAGE);
      setCanRetry(false);
      setState(GeoState.PERMISSION_ERROR);
      return;
    }
    setErrorMessage(null);
    setState(GeoState.INITIALIZING);
    startWatch();
  }, [secureContext, geolocation, startWatch]);

  // マウント時に起動し、アンマウントで watch を解除する。
  // start は依存値が変わらない限り安定（注入 deps が変わったときのみ再起動）。
  useEffect(() => {
    start();
    return () => {
      clearCurrentWatch();
    };
  }, [start, clearCurrentWatch]);

  return { state, selfLocation, accuracyMeters, errorMessage, canRetry, retry };
}
