// ライブ位置の送信 throttle フック（Smart 補助・US-07）。
//
// GPS の watchPosition は高頻度で発火する。この頻度をそのままサーバー送信に流さず、
// 送信周期（既定 2 秒）ごとに「最新の現在地」を 1 回だけ送る（D5 案B）。
// GPS コールバック頻度と通信頻度を分離する。送信周期は US-08 のポーリング周期（2s）と揃える。
//
// 送信のみを担い、状態遷移やエラー画面は持たない（地図維持は Container 側方針に委ねる）。
// API 通信の実体は注入された send（Container が Smart として api.updateLocation を包んで渡す）。

import { useEffect, useRef } from "react";
import type { Coordinate } from "./geoState";

// ライブ位置の送信周期（ms）。US-08 のポーリング周期（2s）と揃える。
export const LOCATION_SEND_INTERVAL_MS = 2000;

/**
 * 最新のライブ位置を [intervalMs] ごとに 1 回だけ送信する（高頻度発火を間引く）。
 *
 * - 周期ごとに「その時点の最新 location」を送るため、通信は必ず 1 周期に 1 回以下になる（trailing）。
 * - location が null（未測位）の周期は送らない。
 * - 送信失敗（ネットワーク一時失敗等）は握りつぶす。次の周期で最新値を再送する。
 * - location / send が高頻度に変わっても interval は貼り直さず、ref 越しに最新を参照する。
 *
 * @param location 最新のライブ位置（未測位は null）
 * @param send     送信関数（Container が api.updateLocation を包んで渡す）
 * @param intervalMs 送信周期（既定 [LOCATION_SEND_INTERVAL_MS]。テストで注入可）
 */
export function useThrottledLocationSend(
  location: Coordinate | null,
  send: (coordinate: Coordinate) => Promise<void>,
  intervalMs: number = LOCATION_SEND_INTERVAL_MS,
): void {
  // 最新の location / send を ref に退避する。これにより GPS 高頻度更新のたびに
  // setInterval を作り直さずに済む（周期タイマは intervalMs 変化時のみ貼り直す）。
  const locationRef = useRef(location);
  locationRef.current = location;
  const sendRef = useRef(send);
  sendRef.current = send;

  useEffect(() => {
    const timerId = setInterval(() => {
      const latest = locationRef.current;
      if (latest === null) {
        return;
      }
      // send は周期ごとに同期呼び出しする（間引きの単位を明確にする）。
      // 送信失敗（Promise reject / 同期例外）は握りつぶす。地図は維持し、次周期で最新値を再送する。
      try {
        void Promise.resolve(sendRef.current(latest)).catch(() => {});
      } catch {
        // 送信関数が同期的に throw した場合も握りつぶす（エラー画面に飛ばさない）。
      }
    }, intervalMs);
    return () => clearInterval(timerId);
  }, [intervalMs]);
}
