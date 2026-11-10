// 待機画面コンテナ（Smart / 結線）。
//
// URL の gameId（パス）/ playerId（?p=）と GET 状態で画面を分岐する（01-spec 1.2〜1.6）:
// - playerId 無し + WAITING            → 参加画面（JoinGameForm）。参加成功で ?p=<playerId> へ遷移
// - playerId 無し + ACTIVE/COMPLETED   → 「参加できません」（1.6）
// - playerId 有り（復帰）              → 参加 API は叩かず状態表示。参加者一覧に自分が無ければ 404 扱い（1.3/1.4）
// - 404                                 → 「ゲームが見つかりません」（1.4）
// ポーリング（1.5）は useGameState（SWR）で実現し、参加者の増減を自動反映する。
// API を呼ぶのは Smart のみ。api / clipboard / origin はテスト用に注入可能。

import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ApiError, type TopoApi, topoApi } from "@/api/topoApi";
import type { GameResultPayload } from "@/api/types";
import { GameResultView } from "@/features/game-result/GameResultView";
import { GameWalkthroughContainer } from "@/features/game-walkthrough/GameWalkthroughContainer";
import type { GetWalkthroughStorage } from "@/features/game-walkthrough/useGameWalkthrough";
import type { UseGeoTrackingDeps } from "@/features/geo-tracking/useGeoTracking";
import { JoinGameForm } from "@/features/join-game/JoinGameForm";
import { buildGamePath, buildInviteUrl, PLAYER_QUERY_KEY } from "@/routing/paths";
import { CannotJoinScreen } from "@/shared/CannotJoinScreen";
import { GameNotFoundScreen } from "@/shared/GameNotFoundScreen";
import { type UseGameStateResult, useGameState } from "@/shared/useGameState";
import type { Participant } from "./WaitingRoomView";
import { WaitingRoomView } from "./WaitingRoomView";

export type WaitingRoomContainerProps = {
  licenseOpen?: boolean;
  onBlockingDialogChange?: (open: boolean) => void;
  onLicenseEntryVisibilityChange?: (visible: boolean) => void;
  // テスト用に API・クリップボード・origin・ポーリング間隔を注入できる（既定はブラウザ実体）。
  api?: TopoApi;
  clipboard?: Pick<Clipboard, "writeText">;
  origin?: string;
  refreshIntervalMs?: number;
  // 地図画面（ACTIVE 遷移先）の Geolocation 注入。テスト用（既定はブラウザ実体）。
  geoDeps?: UseGeoTrackingDeps;
  getWalkthroughStorage?: GetWalkthroughStorage;
};

// ポーリング頻度（統一 2 秒・US-08 / E5）。WAITING / ACTIVE で周期を分けず、送信周期
// （LOCATION_SEND_INTERVAL_MS=2000）と概念一致させる。ACTIVE 遷移後も本 hook が回り続ける。
const POLL_INTERVAL_MS = 2000;

type JoinedGameLoading = {
  playerId: string;
  status: "loading" | "failed" | "not-found";
};

export function WaitingRoomContainer(props: WaitingRoomContainerProps = {}) {
  const { gameId } = useParams();
  return <WaitingRoomGame key={gameId} {...props} gameId={gameId} />;
}

function WaitingRoomGame({
  api = topoApi,
  refreshIntervalMs = POLL_INTERVAL_MS,
  gameId,
  ...props
}: WaitingRoomContainerProps & { gameId: string | undefined }) {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const playerId = searchParams.get(PLAYER_QUERY_KEY) ?? "";
  const [joinedLoading, setJoinedLoading] = useState<JoinedGameLoading | null>(null);
  const request = useRef<AbortController | null>(null);
  const joinedPlayer = useRef<string | null>(null);
  const active = useRef(true);
  const gameState = useGameState({
    api,
    gameId,
    refreshIntervalMs,
    pauseAutomaticRevalidation: joinedLoading !== null,
  });

  useLayoutEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      request.current?.abort();
    };
  }, []);
  useLayoutEffect(() => {
    if (joinedPlayer.current !== null && joinedPlayer.current !== playerId) {
      request.current?.abort();
      joinedPlayer.current = null;
      setJoinedLoading(null);
    }
  }, [playerId]);

  const loadJoinedGame = useCallback(
    async (joinedPlayerId: string) => {
      if (request.current !== null && !request.current.signal.aborted) return;
      const operation = new AbortController();
      request.current = operation;
      joinedPlayer.current = joinedPlayerId;
      setJoinedLoading({ playerId: joinedPlayerId, status: "loading" });
      try {
        await gameState.refreshState(operation.signal);
        if (active.current && !operation.signal.aborted) {
          joinedPlayer.current = null;
          setJoinedLoading(null);
        }
      } catch (failure) {
        if (active.current && !operation.signal.aborted) {
          setJoinedLoading({
            playerId: joinedPlayerId,
            status: failure instanceof ApiError && failure.status === 404 ? "not-found" : "failed",
          });
        }
      } finally {
        if (request.current === operation) request.current = null;
      }
    },
    [gameState.refreshState],
  );

  const showJoinedGame = useCallback(
    (joinedPlayerId: string) => {
      if (!gameId || !active.current) return;
      navigate(buildGamePath(gameId, joinedPlayerId));
      void loadJoinedGame(joinedPlayerId);
    },
    [gameId, navigate, loadJoinedGame],
  );

  const joining = joinedLoading?.playerId === playerId ? joinedLoading : null;
  useLayoutEffect(() => {
    if (joining !== null) props.onLicenseEntryVisibilityChange?.(false);
  }, [joining, props.onLicenseEntryVisibilityChange]);
  if (joining?.status === "loading") return <p role="status">読み込み中…</p>;
  if (joining?.status === "not-found") return <GameNotFoundScreen />;
  if (joining?.status === "failed") {
    return (
      <section role="alert">
        <p>参加は完了しましたが、ゲーム状態を取得できませんでした</p>
        <button type="button" onClick={() => void loadJoinedGame(playerId)}>
          再試行
        </button>
      </section>
    );
  }
  return (
    <WaitingRoomSession
      key={`${gameId}:${playerId}`}
      {...props}
      api={api}
      gameId={gameId}
      playerId={playerId}
      gameState={gameState}
      onJoined={showJoinedGame}
    />
  );
}

function WaitingRoomSession({
  api,
  clipboard = navigator.clipboard,
  origin = window.location.origin,
  geoDeps,
  getWalkthroughStorage,
  licenseOpen,
  onBlockingDialogChange,
  onLicenseEntryVisibilityChange,
  gameId,
  playerId,
  gameState,
  onJoined,
}: WaitingRoomContainerProps & {
  gameId: string | undefined;
  playerId: string;
  gameState: UseGameStateResult;
  onJoined: (playerId: string) => void;
}) {
  const mounted = useRef(true);
  useLayoutEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  // 既定は共有シングルトン topoApi（identity が安定し SWR key の無限再実行を招かない）。
  const resolvedApi = api ?? topoApi;
  const [copied, setCopied] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [starting, setStarting] = useState(false);
  // 参加/開始アクションの失敗メッセージ（409 等）。従来は握り潰していたためユーザーに何も出なかった。
  // ポーリングで是正しきれない 409（満員のまま WAITING 継続など）を明示するために保持する。
  const [actionError, setActionError] = useState<string | null>(null);

  const { state, error, mutate } = gameState;

  const [completion, setCompletion] = useState<{ result: GameResultPayload | null } | null>(null);
  const showResult = useCallback((result: GameResultPayload) => {
    setCompletion((previous) => previous ?? { result });
  }, []);
  useEffect(() => {
    if (state?.status === "COMPLETED") {
      setCompletion((previous) => previous ?? { result: state.result });
    }
  }, [state]);

  const waitingRoomVisible =
    completion === null &&
    state?.status === "WAITING" &&
    state.players.some((player) => player.playerId === playerId) &&
    !(error instanceof ApiError && error.status === 404);
  useLayoutEffect(() => {
    onLicenseEntryVisibilityChange?.(waitingRoomVisible);
    return () => onLicenseEntryVisibilityChange?.(false);
  }, [onLicenseEntryVisibilityChange, waitingRoomVisible]);

  const inviteUrl = gameId ? buildInviteUrl(origin, gameId) : "";

  const handleCopyInviteUrl = useCallback(async () => {
    await clipboard.writeText(inviteUrl);
    setCopied(true);
  }, [clipboard, inviteUrl]);

  const handleJoin = useCallback(
    async (displayName: string) => {
      if (!gameId) return;
      setSubmitting(true);
      setActionError(null);
      try {
        const joined = await resolvedApi.joinGame(gameId, {
          // 空文字はサーバー側フォールバック対象。未入力は送らない。
          displayName: displayName.trim() === "" ? undefined : displayName,
        });
        if (!mounted.current) return;
        onJoined(joined.playerId);
      } catch (e) {
        if (!mounted.current) return;
        // 参加失敗を明示する。409（満員/開始済み）は WAITING 継続時ポーリングでは是正されないため必須。
        setActionError(
          e instanceof ApiError && e.status === 409
            ? "このゲームには参加できません。満員か、すでに開始されています。"
            : "参加に失敗しました。通信状態を確認してもう一度お試しください。",
        );
        setSubmitting(false);
      }
    },
    [resolvedApi, gameId, onJoined],
  );

  const handleStart = useCallback(async () => {
    if (!gameId) return;
    setStarting(true);
    setActionError(null);
    try {
      await resolvedApi.startGame(gameId, { playerId });
      // 開始成功で ACTIVE を即取り込み、地図画面へ切り替える（作成者の即時遷移）。
      await mutate();
    } catch (e) {
      // 開始失敗を明示する。403（非作成者）/ 409（人数・状態不整合）を区別してメッセージ化する。
      setActionError(
        e instanceof ApiError && e.status === 403
          ? "ゲームを開始できるのは作成者だけです。"
          : e instanceof ApiError && e.status === 409
            ? "ゲームを開始できません。参加人数や状態が変化した可能性があります。"
            : "ゲームの開始に失敗しました。もう一度お試しください。",
      );
      setStarting(false);
    }
  }, [resolvedApi, gameId, playerId, mutate]);

  // 404（存在しない gameId）→ 見つかりません（1.4）。
  if (completion === null && error instanceof ApiError && error.status === 404) {
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
    return (
      <>
        {actionError !== null && <p role="alert">{actionError}</p>}
        <JoinGameForm onJoin={handleJoin} submitting={submitting} />
      </>
    );
  }

  // playerId 有り（復帰・1.3）。参加者一覧に自分がいなければ不正 playerId として 404 扱い（1.4）。
  const isKnownPlayer = state.players.some((p) => p.playerId === playerId);
  if (!isKnownPlayer) {
    return <GameNotFoundScreen />;
  }

  if (completion !== null || state.status === "COMPLETED") {
    return <GameResultView result={completion === null ? state.result : completion.result} />;
  }

  // ACTIVE 検知で地図画面（US-02 雛形）へ切り替える（1.3）。作成者は開始成功→mutate で、
  // 非作成者はポーリングで ACTIVE を検知し、両者とも status 駆動の同一分岐に集約する。
  if (state.status === "ACTIVE") {
    // 地図画面へ切り替え。ライブ位置送信のため gameId / playerId / api を Smart へ渡す（US-07）。
    // さらに US-08 でポーリング済みの players（live/online）と currentArea を Smart→Smart で渡す
    // （保持・公開のみ。友達ドット/面積メーターの描画は US-09/10）。
    return (
      <GameWalkthroughContainer
        key={`${gameId}:${playerId}`}
        gameId={gameId ?? ""}
        playerId={playerId}
        api={resolvedApi}
        deps={geoDeps}
        players={state.players}
        currentArea={state.currentArea}
        areaThreshold={state.areaThreshold}
        onResult={showResult}
        getWalkthroughStorage={getWalkthroughStorage}
        licenseOpen={licenseOpen}
        onBlockingDialogChange={onBlockingDialogChange}
      />
    );
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
    <>
      {actionError !== null && <p role="alert">{actionError}</p>}
      <WaitingRoomView
        status={state.status}
        participants={participants}
        playerCount={state.playerCount}
        areaThreshold={state.areaThreshold}
        inviteUrl={inviteUrl}
        onCopyInviteUrl={handleCopyInviteUrl}
        copied={copied}
        onStart={handleStart}
        startEnabled={startEnabled}
        starting={starting}
      />
    </>
  );
}
