// 測位コンテナ（Smart / 結線）。
//
// useGeoTracking を使い、状態に応じて Dumb（MapView / LocationErrorScreen / DegradedBanner）を
// 出し分ける結線点。地図は測位成否と独立に「まず描画」する（PERMISSION_ERROR のときだけ隠す）。
// さらに Smart として、自分のライブ位置を throttle（≤1回/2秒）でサーバーへ送信する（US-07）。
// API を呼ぶのは本コンテナ（Smart）のみ。Dumb は API を知らない。

import { useCallback } from "react";
import { type TopoApi, topoApi } from "@/api/topoApi";
import { AccuracyReadout } from "./AccuracyReadout";
import { DegradedBanner } from "./DegradedBanner";
import { type Coordinate, GeoState } from "./geoState";
import { LocationErrorScreen } from "./LocationErrorScreen";
import { MapView } from "./MapView";
import { type UseGeoTrackingDeps, useGeoTracking } from "./useGeoTracking";
import { useThrottledLocationSend } from "./useThrottledLocationSend";

export type GeoTrackingContainerProps = {
  // ライブ位置の送信先を特定するための識別子（Smart のみが保持し送信に使う）。
  gameId: string;
  playerId: string;
  // テスト用に API を注入できる（既定は共有シングルトン topoApi）。
  api?: TopoApi;
  // テスト用に Geolocation / isSecureContext を注入できる（既定はブラウザ実体）。
  deps?: UseGeoTrackingDeps;
};

export function GeoTrackingContainer({ gameId, playerId, api, deps }: GeoTrackingContainerProps) {
  // 既定は共有シングルトン（identity が安定し、send の再生成連鎖を招かない）。
  const resolvedApi = api ?? topoApi;
  const { state, selfLocation, accuracyMeters, errorMessage, canRetry, retry } =
    useGeoTracking(deps);

  // ライブ位置の送信関数（Smart）。ペイロードは { lat, lng } のみ（Coordinate と一致）。
  const sendLocation = useCallback(
    (coordinate: Coordinate) => resolvedApi.updateLocation(gameId, playerId, coordinate),
    [resolvedApi, gameId, playerId],
  );
  // 高頻度発火を ≤1回/2秒 に間引いて送信する。失敗しても地図は維持する（hook 側で握りつぶす）。
  useThrottledLocationSend(selfLocation, sendLocation);

  // 初回拒否 / 非安全コンテキスト: 地図を出さず、エラー画面を全面表示する（フォールバックしない）。
  if (state === GeoState.PERMISSION_ERROR) {
    return (
      <LocationErrorScreen
        message={errorMessage ?? ""}
        // 非安全コンテキスト由来は再試行不可（canRetry=false）なので onRetry を渡さない。
        onRetry={canRetry ? retry : undefined}
      />
    );
  }

  // それ以外（INITIALIZING / TRACKING / DEGRADED）は地図を維持する。
  // DEGRADED のときのみ控えめバナーを地図上部に重ねる（地図は隠さない）。
  return (
    <div className="absolute inset-0">
      <MapView selfLocation={selfLocation} />
      <AccuracyReadout accuracyMeters={accuracyMeters} />
      <DegradedBanner visible={state === GeoState.DEGRADED} />
    </div>
  );
}
