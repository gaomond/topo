import { Button } from "@/shared/ui/button";
// 測位コンテナ（Smart / 結線）。
//
// useGeoTracking を使い、状態に応じて Dumb（MapView / LocationErrorScreen / DegradedBanner）を
// 出し分ける結線点。地図は測位成否と独立に「まず描画」する（PERMISSION_ERROR のときだけ隠す）。
// さらに Smart として、自分のライブ位置を throttle（≤1回/2秒）でサーバーへ送信する（US-07）。
// API を呼ぶのは本コンテナ（Smart）のみ。Dumb は API を知らない。

import { type Ref, useCallback, useEffect, useMemo, useState } from "react";
import { type TopoApi, topoApi } from "@/api/topoApi";
import type { CurrentAreaPayload, GameResultPayload, PlayerPayload } from "@/api/types";
import { AreaMeter } from "@/features/area-meter/AreaMeter";
import { ConfirmLocationControl } from "@/features/location-confirm/ConfirmLocationControl";
import { useLocationConfirmation } from "@/features/location-confirm/useLocationConfirmation";
import { AccuracyReadout } from "./AccuracyReadout";
import { DegradedBanner } from "./DegradedBanner";
import { type ConfirmedMarker, type Coordinate, GeoState, type LiveMarker } from "./geoState";
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
  // US-08 でポーリング済みの他プレイヤー（live/online）と暫定凸包。US-09 で友達ドット・凸包ポリゴンとして描画する。
  players?: PlayerPayload[];
  currentArea?: CurrentAreaPayload | null;
  // 面積成立判定の上限（m²・作成時確定）。US-10 の面積メーターの満杯ラインに使う。
  // 親 Smart（WaitingRoomContainer）から state.areaThreshold を渡す。未指定時はメーターを出さない。
  areaThreshold?: number;
  onResult?: (result: GameResultPayload) => void;
  locationEnabled?: boolean;
  interactionBlocked?: boolean;
  onBlockingDialogChange?: (open: boolean) => void;
  onOpenWalkthrough?: () => void;
  walkthroughButtonRef?: Ref<HTMLButtonElement>;
};

export function GeoTrackingContainer({
  gameId,
  playerId,
  api,
  deps,
  players = [],
  currentArea = null,
  areaThreshold,
  onResult,
  locationEnabled = true,
  interactionBlocked = false,
  onBlockingDialogChange,
  onOpenWalkthrough,
  walkthroughButtonRef,
}: GeoTrackingContainerProps) {
  // 既定は共有シングルトン（identity が安定し、send の再生成連鎖を招かない）。
  const resolvedApi = api ?? topoApi;
  const { state, selfLocation, accuracyMeters, errorMessage, canRetry, retry } = useGeoTracking(
    deps,
    locationEnabled,
  );

  const confirmation = useLocationConfirmation(
    resolvedApi,
    gameId,
    playerId,
    selfLocation,
    players.find((p) => p.playerId === playerId)?.confirmedLocation ?? null,
    onResult,
  );

  useEffect(() => {
    onBlockingDialogChange?.(confirmation.dialogOpen);
    return () => onBlockingDialogChange?.(false);
  }, [onBlockingDialogChange, confirmation.dialogOpen]);

  // ライブ位置の送信関数（Smart）。ペイロードは { lat, lng } のみ（Coordinate と一致）。
  const sendLocation = useCallback(
    (coordinate: Coordinate) => resolvedApi.updateLocation(gameId, playerId, coordinate),
    [resolvedApi, gameId, playerId],
  );
  // 高頻度発火を ≤1回/2秒 に間引いて送信する。失敗しても地図は維持する（hook 側で握りつぶす）。
  useThrottledLocationSend(selfLocation, sendLocation);

  // 友達ドットのビューモデルを導出（US-09）。自分（playerId）を除外し（自分は watchPosition で別描画・F1）、
  // live=null（未送信）は非描画。identity を安定させ MapView / FitBoundsController の無駄な再評価を避ける。
  const friends = useMemo<LiveMarker[]>(
    () =>
      players.flatMap((p) => {
        if (p.playerId === playerId || p.live === null) {
          return [];
        }
        return [
          {
            playerId: p.playerId,
            displayName: p.displayName,
            coordinate: { lat: p.live.lat, lng: p.live.lng },
            online: p.online,
            confirmed: p.confirmed,
          },
        ];
      }),
    [players, playerId],
  );

  const confirmedMarkers = useMemo<ConfirmedMarker[]>(
    () =>
      players.flatMap((p) => {
        const fixed = p.playerId === playerId ? confirmation.fixedLocation : p.confirmedLocation;
        return fixed === null
          ? []
          : [
              {
                playerId: p.playerId,
                displayName: p.displayName,
                coordinate: { lat: fixed.lat, lng: fixed.lng },
              },
            ];
      }),
    [players, playerId, confirmation.fixedLocation],
  );

  // 凸包（面積成立の形）。頂点はサーバー提供。sqm=0 は退化（線分）として描く。objectCount・正多角形スコアは扱わない。
  const hull = currentArea?.hull ?? null;
  const degenerate = currentArea?.sqm === 0;

  // 「全体表示」押下ごとに +1 して FitBoundsController の強制リフィットをトリガする。
  const [overviewNonce, setOverviewNonce] = useState(0);
  const handleOverview = useCallback(() => setOverviewNonce((n) => n + 1), []);

  const helpButton = onOpenWalkthrough && (
    <Button
      ref={walkthroughButtonRef}
      type="button"
      onClick={onOpenWalkthrough}
      variant="outline"
      className="absolute right-3 top-3 z-[3000] px-4 shadow-sm"
    >
      遊び方
    </Button>
  );

  // 初回拒否 / 非安全コンテキスト: 地図を出さず、エラー画面を全面表示する（フォールバックしない）。
  if (state === GeoState.PERMISSION_ERROR) {
    return (
      <>
        <LocationErrorScreen
          message={errorMessage ?? ""}
          // 非安全コンテキスト由来は再試行不可（canRetry=false）なので onRetry を渡さない。
          onRetry={canRetry ? retry : undefined}
        />
        {helpButton}
      </>
    );
  }

  // それ以外（INITIALIZING / TRACKING / DEGRADED）は地図を維持する。
  // DEGRADED のときのみ控えめバナーを地図上部に重ねる（地図は隠さない）。
  // US-09: 友達ドット（friends）・凸包ポリゴン（hull/degenerate）・全体表示（overview）を可視描画する。
  // US-10: 面積メーター（現在面積の数値＋上限ゲージ）を重ねる。面積のみ開示し、objectCount・
  // 正多角形スコアは引き続き渡さない/描かない（情報マスキング）。凸包・面積の計算はサーバー側。
  return (
    <div className="absolute inset-0">
      {helpButton}
      <div data-walkthrough="map" className="absolute inset-0">
        <MapView
          selfLocation={selfLocation}
          confirmedMarkers={confirmedMarkers}
          selfConfirmed={confirmation.confirmed}
          friends={friends}
          hull={hull}
          degenerate={degenerate}
          overviewNonce={overviewNonce}
          onOverview={handleOverview}
        />
      </div>
      <DegradedBanner visible={state === GeoState.DEGRADED} />
      <div className="pointer-events-none absolute inset-x-0 bottom-6 z-[1000] flex flex-col gap-3 px-3">
        <div className="flex flex-wrap items-end justify-between gap-2">
          <AccuracyReadout accuracyMeters={accuracyMeters} />
          {areaThreshold !== undefined && (
            <div data-walkthrough="area" className="ml-auto">
              <AreaMeter sqm={currentArea?.sqm ?? null} threshold={areaThreshold} />
            </div>
          )}
        </div>
        <div data-walkthrough="confirm" className="w-full max-w-sm self-center">
          <ConfirmLocationControl
            confirmed={confirmation.confirmed}
            dialogOpen={confirmation.dialogOpen}
            submitting={confirmation.submitting}
            canConfirm={confirmation.canConfirm && !interactionBlocked}
            error={confirmation.error}
            onOpen={() => {
              if (!interactionBlocked) confirmation.open();
            }}
            onCancel={confirmation.cancel}
            onConfirm={() => {
              void confirmation.confirm();
            }}
          />
        </div>
      </div>
    </div>
  );
}
