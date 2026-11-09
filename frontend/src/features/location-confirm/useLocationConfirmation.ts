import { useEffect, useRef, useState } from "react";
import { ApiError, type TopoApi } from "@/api/topoApi";
import type { GameResultPayload } from "@/api/types";
import type { Coordinate } from "@/features/geo-tracking/geoState";

export function useLocationConfirmation(
  api: TopoApi,
  gameId: string,
  playerId: string,
  coordinate: Coordinate | null,
  serverConfirmed: boolean,
  onResult?: (result: GameResultPayload) => void,
) {
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  const [localConfirmed, setLocalConfirmed] = useState(serverConfirmed);
  const [fixedLocation, setFixedLocation] = useState<Coordinate | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pending = useRef(false);
  const confirmed = serverConfirmed || localConfirmed;
  const canConfirm = coordinate !== null && !confirmed && !submitting;

  // 遅れて到着した未確定のGETが、確認済みの状態を巻き戻さないようにする。
  useEffect(() => {
    if (serverConfirmed) setLocalConfirmed(true);
  }, [serverConfirmed]);

  function open() {
    if (!canConfirm) return;
    setError(null);
    setDialogOpen(true);
  }

  function cancel() {
    if (!pending.current) setDialogOpen(false);
  }

  async function confirm() {
    if (!dialogOpen || !canConfirm || pending.current || coordinate === null) return;
    const sentPoint = { lat: coordinate.lat, lng: coordinate.lng };
    pending.current = true;
    setSubmitting(true);
    setError(null);
    try {
      const response = await api.confirmLocation(gameId, playerId, sentPoint);
      if (!mounted.current) return;
      setFixedLocation(sentPoint);
      setLocalConfirmed(true);
      if (response.result !== null) onResult?.(response.result);
    } catch (failure) {
      let foundConfirmed = false;
      if (failure instanceof ApiError && failure.status === 409) {
        try {
          const current = await api.getGameState(gameId);
          foundConfirmed = current.players.some((p) => p.playerId === playerId && p.confirmed);
        } catch {
          // 取得にも失敗した場合は未確認のままとし、再試行できる状態を保つ。
        }
      }
      if (foundConfirmed) setLocalConfirmed(true);
      else
        setError(
          failure instanceof ApiError && failure.status === 409
            ? "現在のゲーム状態では確定できません。"
            : failure instanceof ApiError &&
                failure.status === 422 &&
                failure.reason === "AREA_EXCEEDS_THRESHOLD"
              ? "面積が上限を超えるため確定できません。近づいてください"
              : failure instanceof ApiError &&
                  failure.status === 422 &&
                  failure.reason === "AREA_BELOW_MIN"
                ? "面積が小さすぎるため確定できません。もっと散らばってください"
                : "位置の確定に失敗しました。もう一度お試しください。",
        );
    } finally {
      pending.current = false;
      setSubmitting(false);
      setDialogOpen(false);
    }
  }

  return {
    confirmed,
    fixedLocation,
    dialogOpen,
    submitting,
    error,
    canConfirm,
    open,
    cancel,
    confirm,
  };
}
