// 測位コンテナ（Smart / 結線）。
//
// useGeoTracking を使い、状態に応じて Dumb（MapView / LocationErrorScreen / DegradedBanner）を
// 出し分ける結線点。地図は測位成否と独立に「まず描画」する（PERMISSION_ERROR のときだけ隠す）。
// API 呼び出し・ポーリング・URL（gameId / playerId）解釈は本ストーリーに含めない。

import { AccuracyReadout } from "../components/AccuracyReadout";
import { DegradedBanner } from "../components/DegradedBanner";
import { LocationErrorScreen } from "../components/LocationErrorScreen";
import { MapView } from "../components/MapView";
import { GeoState } from "../hooks/geoState";
import { type UseGeoTrackingDeps, useGeoTracking } from "../hooks/useGeoTracking";

export type GeoTrackingContainerProps = {
  // テスト用に Geolocation / isSecureContext を注入できる（既定はブラウザ実体）。
  deps?: UseGeoTrackingDeps;
};

export function GeoTrackingContainer({ deps }: GeoTrackingContainerProps = {}) {
  const { state, selfLocation, accuracyMeters, errorMessage, canRetry, retry } =
    useGeoTracking(deps);

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
