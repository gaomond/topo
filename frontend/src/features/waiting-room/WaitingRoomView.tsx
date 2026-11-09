// 待機画面ビュー（Dumb / プレゼンテーショナル）。
//
// props で参加者一覧・状態・定員・招待 URL・コピーハンドラを受け取り描画するだけ。
// API も共有状態も知らない。参加者一覧は GET のポーリング結果（実データ）を Smart から受け取る。

import { GamePurposeView } from "./GamePurposeView";

export type Participant = {
  playerId: string;
  displayName: string;
  confirmed: boolean;
};

export type WaitingRoomViewProps = {
  status: string;
  participants: Participant[];
  playerCount: number;
  areaThreshold: number;
  inviteUrl: string;
  onCopyInviteUrl: () => void;
  copied: boolean;
  // 開始ボタン（US-06）。全員に表示し、活性/非活性は Smart が算出して渡す（Dumb は描画のみ）。
  onStart: () => void;
  // 押下可能か（作成者かつ定員到達で true）。非作成者・定員未達は false。
  startEnabled: boolean;
  // 開始 API 送信中か（二重送信防止）。
  starting: boolean;
};

export function WaitingRoomView({
  status,
  participants,
  playerCount,
  areaThreshold,
  inviteUrl,
  onCopyInviteUrl,
  copied,
  onStart,
  startEnabled,
  starting,
}: WaitingRoomViewProps) {
  return (
    <section
      aria-label="待機画面"
      className="mx-auto flex min-h-dvh max-w-2xl flex-col gap-4 bg-slate-50 p-4 text-slate-800"
    >
      <h1>待機中</h1>
      <GamePurposeView areaThreshold={areaThreshold} />

      <p>
        状態: <span data-testid="game-status">{status}</span>
      </p>

      <h2>
        参加者（
        <span data-testid="participant-count">{participants.length}</span> / {playerCount}）
      </h2>
      <ul aria-label="参加者一覧">
        {participants.map((participant) => (
          <li key={participant.playerId}>{participant.displayName}</li>
        ))}
      </ul>

      <h2>仲間を招待</h2>
      <p data-testid="invite-url">{inviteUrl}</p>
      <button type="button" onClick={onCopyInviteUrl}>
        {copied ? "コピーしました" : "招待URLをコピー"}
      </button>

      <button type="button" onClick={onStart} disabled={!startEnabled || starting}>
        {starting ? "開始しています…" : "ゲームを開始"}
      </button>
    </section>
  );
}
