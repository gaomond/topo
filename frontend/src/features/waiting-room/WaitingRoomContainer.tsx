// 待機画面コンテナ（Smart / 結線）。
//
// URL から gameId（パス）/ playerId（?p=）を取得し、招待 URL（gameId のみ）を組み立てる。
// 招待 URL の組み立てはクライアント責務（サーバーは gameId/playerId のみ返す）。
// 参加者一覧のポーリング更新は US-05 のスコープ。本ストーリーでは作成者のみを静的表示する。

import { useCallback, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { buildInviteUrl, PLAYER_QUERY_KEY } from "@/routing/paths";
import type { Participant } from "./WaitingRoomView";
import { WaitingRoomView } from "./WaitingRoomView";

export type WaitingRoomContainerProps = {
  // テスト用にクリップボード・origin を注入できる（既定はブラウザ実体）。
  clipboard?: Pick<Clipboard, "writeText">;
  origin?: string;
};

export function WaitingRoomContainer({
  clipboard = navigator.clipboard,
  origin = window.location.origin,
}: WaitingRoomContainerProps = {}) {
  const { gameId } = useParams();
  const [searchParams] = useSearchParams();
  const playerId = searchParams.get(PLAYER_QUERY_KEY) ?? "";
  const [copied, setCopied] = useState(false);

  const inviteUrl = gameId ? buildInviteUrl(origin, gameId) : "";

  // 参加者はこの時点では作成者（自分）のみ。表示名はサーバーが確定済みだが、
  // US-04 ではポーリング取得しないため playerId から仮表示する（US-05 で実データに置換）。
  const participants: Participant[] =
    playerId === "" ? [] : [{ playerId, displayName: `あなた（${playerId.slice(0, 8)}）` }];

  const handleCopyInviteUrl = useCallback(async () => {
    await clipboard.writeText(inviteUrl);
    setCopied(true);
  }, [clipboard, inviteUrl]);

  return (
    <WaitingRoomView
      status="WAITING"
      participants={participants}
      inviteUrl={inviteUrl}
      onCopyInviteUrl={handleCopyInviteUrl}
      copied={copied}
    />
  );
}
