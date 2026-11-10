import { useEffect, useRef, useState } from "react";
import { ApiError, type TopoApi } from "@/api/topoApi";
import type { ConfirmedLocationPayload, GameResultPayload } from "@/api/types";
import type { Coordinate } from "@/features/geo-tracking/geoState";

function initialState(gameId: string, playerId: string, saved: ConfirmedLocationPayload | null) {
  return {
    gameId,
    playerId,
    generation: Symbol(),
    fixedLocation: saved === null ? null : { lat: saved.lat, lng: saved.lng },
    dialogOpen: false,
    submitting: false,
    error: null as string | null,
  };
}

export function useLocationConfirmation(
  api: TopoApi,
  gameId: string,
  playerId: string,
  coordinate: Coordinate | null,
  serverLocation: ConfirmedLocationPayload | null,
  onResult?: (result: GameResultPayload) => void,
) {
  const [state, setState] = useState(() => initialState(gameId, playerId, serverLocation));
  let current = state;
  if (state.gameId !== gameId || state.playerId !== playerId) {
    current = initialState(gameId, playerId, serverLocation);
    setState(current);
  } else if (state.fixedLocation === null && serverLocation !== null) {
    current = { ...state, fixedLocation: { lat: serverLocation.lat, lng: serverLocation.lng } };
    setState(current);
  }
  // IDが元に戻るA→B→Aでも、以前のAの非同期処理は新しいAへ作用させない。
  const activeGeneration = useRef(current.generation);
  activeGeneration.current = current.generation;
  const mounted = useRef(true);
  const pending = useRef<symbol | null>(null);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  const generation = current.generation;
  const isActive = () => mounted.current && activeGeneration.current === generation;
  function update(patch: Partial<typeof state>) {
    if (!isActive()) return;
    setState((previous) =>
      previous.generation === generation ? { ...previous, ...patch } : previous,
    );
  }

  const confirmed = current.fixedLocation !== null;
  const canConfirm = coordinate !== null && !confirmed && !current.submitting;
  function open() {
    if (canConfirm) update({ error: null, dialogOpen: true });
  }
  function cancel() {
    if (pending.current !== generation) update({ dialogOpen: false });
  }
  async function confirm() {
    if (!current.dialogOpen || !canConfirm || pending.current === generation || coordinate === null)
      return;
    const sentPoint = { lat: coordinate.lat, lng: coordinate.lng };
    pending.current = generation;
    update({ submitting: true, error: null });
    try {
      const response = await api.confirmLocation(gameId, playerId, sentPoint);
      if (!isActive()) return;
      // 応答を待つ間に変わったGPSや遅延GETから固定点を作らない。
      update({ fixedLocation: sentPoint });
      if (response.result !== null) onResult?.(response.result);
    } catch (failure) {
      if (!isActive()) return;
      let saved: ConfirmedLocationPayload | null = null;
      if (failure instanceof ApiError && failure.status === 409) {
        try {
          const game = await api.getGameState(gameId);
          if (!isActive()) return;
          saved = game.players.find((p) => p.playerId === playerId)?.confirmedLocation ?? null;
        } catch {
          // 再取得失敗時は確定済みと推測せず、再試行できる状態を保つ。
        }
      }
      if (!isActive()) return;
      if (saved !== null) update({ fixedLocation: { lat: saved.lat, lng: saved.lng } });
      else
        update({
          error:
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
        });
    } finally {
      if (isActive()) {
        pending.current = null;
        update({ submitting: false, dialogOpen: false });
      }
    }
  }
  return {
    confirmed,
    fixedLocation: current.fixedLocation,
    dialogOpen: current.dialogOpen,
    submitting: current.submitting,
    error: current.error,
    canConfirm,
    open,
    cancel,
    confirm,
  };
}
