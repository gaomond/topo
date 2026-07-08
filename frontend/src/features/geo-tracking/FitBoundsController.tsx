// 全体フィット＋カメラ暴れ制御（react-leaflet 制御部品 / Dumb）。
//
// 自分・友達・凸包の全頂点が収まるよう map.fitBounds する。旧 SelfFollower（自分中心固定）を置換する。
// 毎ポーリング（2 秒）で無条件に再フィットするとカメラが暴れるため、次の抑制を行う（spec 1.4）:
//   1. 有意変化のみ再フィット（直近適用 bounds と閾値以下の微小変化なら動かさない）
//   2. 手動パン/ズーム中は自動フィットを一時停止（dragstart/zoomstart で pause、moveend/zoomend で解除）
//   3. 「全体表示」要求（overviewNonce 変化）で pause・閾値を無視して即時に全頂点へ強制リフィット
// 自前の fitBounds が発火する move/zoom イベントを手動操作と誤認しないよう programmatic フラグでガードする。
// 座標計算はビュー都合の bounds 算出のみ（ゲーム計算＝凸包/面積はしない・CLAUDE.md）。

import L from "leaflet";
import { useEffect, useRef } from "react";
import { useMap } from "react-leaflet";

// フィットの余白（px）と、単一点に寄せるときの最大ズーム。
const FIT_PADDING: [number, number] = [48, 48];
const FIT_MAX_ZOOM = 16;

// 有意変化とみなす境界（メートル）。bounds の両隅の移動がこの値以下なら再フィットしない。
// 過敏だとカメラが暴れ、鈍いと追従しない。プレイテストで調整するアプリ定数の暫定値。
const SIGNIFICANT_SHIFT_METERS = 25;

// 手動操作の落ち着き待ち（ms）。moveend/zoomend からこの時間で自動追従を再開する。
const SETTLE_MS = 1200;

export type FitBoundsControllerProps = {
  // 全対象点（自分＋友達＋凸包の全頂点）。[lat,lng]。空なら何もしない。
  points: [number, number][];
  // 「全体表示」ボタン押下ごとに +1 される。変化を検知したら pause・閾値を無視して強制リフィットする。
  overviewNonce: number;
};

// 両隅の移動距離で有意変化を判定（平行移動・拡大縮小の双方を捉える）。
function isSignificantChange(prev: L.LatLngBounds, next: L.LatLngBounds): boolean {
  return (
    prev.getNorthEast().distanceTo(next.getNorthEast()) > SIGNIFICANT_SHIFT_METERS ||
    prev.getSouthWest().distanceTo(next.getSouthWest()) > SIGNIFICANT_SHIFT_METERS
  );
}

export function FitBoundsController({ points, overviewNonce }: FitBoundsControllerProps) {
  const map = useMap();
  const appliedBoundsRef = useRef<L.LatLngBounds | null>(null);
  const pausedRef = useRef(false);
  const programmaticRef = useRef(false);
  const settleTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // 前回処理した overviewNonce。変化＝「全体表示」押下として強制リフィットのトリガに使う。
  const lastOverviewRef = useRef(overviewNonce);

  // 手動パン/ズームの検知と、操作落ち着き後の追従再開。自前 fitBounds 由来のイベントは programmatic で除外。
  useEffect(() => {
    const onUserStart = () => {
      if (programmaticRef.current) {
        return;
      }
      pausedRef.current = true;
      if (settleTimerRef.current !== null) {
        clearTimeout(settleTimerRef.current);
        settleTimerRef.current = null;
      }
    };
    const onUserEnd = () => {
      if (programmaticRef.current || !pausedRef.current) {
        return;
      }
      if (settleTimerRef.current !== null) {
        clearTimeout(settleTimerRef.current);
      }
      settleTimerRef.current = setTimeout(() => {
        pausedRef.current = false;
        settleTimerRef.current = null;
      }, SETTLE_MS);
    };
    map.on("dragstart", onUserStart);
    map.on("zoomstart", onUserStart);
    map.on("moveend", onUserEnd);
    map.on("zoomend", onUserEnd);
    return () => {
      map.off("dragstart", onUserStart);
      map.off("zoomstart", onUserStart);
      map.off("moveend", onUserEnd);
      map.off("zoomend", onUserEnd);
      if (settleTimerRef.current !== null) {
        clearTimeout(settleTimerRef.current);
        settleTimerRef.current = null;
      }
    };
  }, [map]);

  // 全頂点への追従。points / overviewNonce が変わるたびに評価する。
  useEffect(() => {
    if (points.length === 0) {
      return;
    }
    const bounds = L.latLngBounds(points);
    const forced = overviewNonce !== lastOverviewRef.current;
    if (forced) {
      // 「全体表示」: pause 解除＋閾値無視で必ず全体へ戻す。
      lastOverviewRef.current = overviewNonce;
      pausedRef.current = false;
    } else {
      // 手動操作中は追従しない。
      if (pausedRef.current) {
        return;
      }
      // 微小変化ではカメラを動かさない。
      const prev = appliedBoundsRef.current;
      if (prev !== null && !isSignificantChange(prev, bounds)) {
        return;
      }
    }
    // 自前 fitBounds が発火する move/zoom を手動操作と誤認しないようガード。jsdom/実機とも同期完了する。
    programmaticRef.current = true;
    map.fitBounds(bounds, { padding: FIT_PADDING, maxZoom: FIT_MAX_ZOOM });
    programmaticRef.current = false;
    appliedBoundsRef.current = bounds;
  }, [map, points, overviewNonce]);

  return null;
}
