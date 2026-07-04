// 待機画面コンテナ（Smart / 結線）。
//
// URL の gameId（パス）/ playerId（?p=）と GET 状態で画面を分岐する（01-spec 1.2〜1.6）:
// - playerId 無し + WAITING            → 参加画面（JoinGameForm）。参加成功で ?p=<playerId> へ遷移
// - playerId 無し + ACTIVE/COMPLETED   → 「参加できません」（1.6）
// - playerId 有り（復帰）              → 参加 API は叩かず状態表示。参加者一覧に自分が無ければ 404 扱い（1.3/1.4）
// - 404                                 → 「ゲームが見つかりません」（1.4）
// ポーリング（1.5）は useGameState（SWR）で実現し、参加者の増減を自動反映する。
// API を呼ぶのは Smart のみ。api / clipboard / origin はテスト用に注入可能。

import { useCallback, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ApiError, type TopoApi, topoApi } from "@/api/topoApi";
import { GeoTrackingContainer } from "@/features/geo-tracking/GeoTrackingContainer";
import type { UseGeoTrackingDeps } from "@/features/geo-tracking/useGeoTracking";
import { JoinGameForm } from "@/features/join-game/JoinGameForm";
import { buildGamePath, buildInviteUrl, PLAYER_QUERY_KEY } from "@/routing/paths";
import { CannotJoinScreen } from "@/shared/CannotJoinScreen";
import { GameNotFoundScreen } from "@/shared/GameNotFoundScreen";
import { useGameState } from "@/shared/useGameState";
import type { Participant } from "./WaitingRoomView";
import { WaitingRoomView } from "./WaitingRoomView";

export type WaitingRoomContainerProps = {
  // テスト用に API・クリップボード・origin・ポーリング間隔を注入できる（既定はブラウザ実体）。
  api?: TopoApi;
  clipboard?: Pick<Clipboard, "writeText">;
  origin?: string;
  refreshIntervalMs?: number;
  // 地図画面（ACTIVE 遷移先）の Geolocation 注入。テスト用（既定はブラウザ実体）。
  geoDeps?: UseGeoTrackingDeps;
};

// 待機中のポーリング頻度（低頻度）。US-08 の ACTIVE 2s は refreshIntervalMs で切り替える。
const WAITING_REFRESH_INTERVAL_MS = 5000;

export function WaitingRoomContainer({
  api,
  clipboard = navigator.clipboard,
  origin = window.location.origin,
  refreshIntervalMs = WAITING_REFRESH_INTERVAL_MS,
  geoDeps,
}: WaitingRoomContainerProps = {}) {
  const navigate = useNavigate();
  const { gameId } = useParams();
  const [searchParams] = useSearchParams();
  const playerId = searchParams.get(PLAYER_QUERY_KEY) ?? "";

  // 既定は共有シングルトン topoApi（identity が安定し SWR key の無限再実行を招かない）。
  const resolvedApi = api ?? topoApi;
  const [copied, setCopied] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [starting, setStarting] = useState(false);

  const { state, error, mutate } = useGameState({
    api: resolvedApi,
    gameId,
    refreshIntervalMs,
  });

  const inviteUrl = gameId ? buildInviteUrl(origin, gameId) : "";

  const handleCopyInviteUrl = useCallback(async () => {
    await clipboard.writeText(inviteUrl);
    setCopied(true);
  }, [clipboard, inviteUrl]);

  const handleJoin = useCallback(
    async (displayName: string) => {
      if (!gameId) return;
      setSubmitting(true);
      try {
        const joined = await resolvedApi.joinGame(gameId, {
          // 空文字はサーバー側フォールバック対象。未入力は送らない。
          displayName: displayName.trim() === "" ? undefined : displayName,
        });
        // 参加成功で自分の URL（gameId + playerId）へ遷移する（1.2）。
        navigate(buildGamePath(gameId, joined.playerId));
      } catch {
        // 参加失敗（409 等）は再取得（ポーリング）で画面が分岐に追従する。ボタンは戻す。
        setSubmitting(false);
      }
    },
    [resolvedApi, gameId, navigate],
  );

  const handleStart = useCallback(async () => {
    if (!gameId) return;
    setStarting(true);
    try {
      await resolvedApi.startGame(gameId, { playerId });
      // 開始成功で ACTIVE を即取り込み、地図画面へ切り替える（作成者の即時遷移）。
      await mutate();
    } catch {
      // 失敗（403/409）はポーリングで状態が追従し画面分岐が是正される。ボタンは戻す。
      setStarting(false);
    }
  }, [resolvedApi, gameId, playerId, mutate]);

  // 404（存在しない gameId）→ 見つかりません（1.4）。
  if (error instanceof ApiError && error.status === 404) {
    return <GameNotFoundScreen />;
  }

  // 初回ロード中。
  if (!state) {
    return <p role="status">読み込み中…</p>;
  }

  // playerId 無し（招待 URL で来た側）。
  if (playerId === "") {
    if (state.status !== "WAITING") {
      // ACTIVE / COMPLETED は締め切り（1.6）。
      return <CannotJoinScreen />;
    }
    return <JoinGameForm onJoin={handleJoin} submitting={submitting} />;
  }

  // playerId 有り（復帰・1.3）。参加者一覧に自分がいなければ不正 playerId として 404 扱い（1.4）。
  const isKnownPlayer = state.players.some((p) => p.playerId === playerId);
  if (!isKnownPlayer) {
    return <GameNotFoundScreen />;
  }

  // ACTIVE 検知で地図画面（US-02 雛形）へ切り替える（1.3）。作成者は開始成功→mutate で、
  // 非作成者はポーリングで ACTIVE を検知し、両者とも status 駆動の同一分岐に集約する。
  if (state.status === "ACTIVE") {
    return <GeoTrackingContainer deps={geoDeps} />;
  }

  const participants: Participant[] = state.players.map((p) => ({
    playerId: p.playerId,
    displayName: p.displayName,
    confirmed: p.confirmed,
  }));

  // creator 判定と開始ボタン活性: 作成者かつ定員到達で押下可（1.2）。
  const isCreator = playerId === state.creatorPlayerId;
  const startEnabled = isCreator && participants.length === state.playerCount;

  return (
    <WaitingRoomView
      status={state.status}
      participants={participants}
      playerCount={state.playerCount}
      inviteUrl={inviteUrl}
      onCopyInviteUrl={handleCopyInviteUrl}
      copied={copied}
      onStart={handleStart}
      startEnabled={startEnabled}
      starting={starting}
    />
  );
}
