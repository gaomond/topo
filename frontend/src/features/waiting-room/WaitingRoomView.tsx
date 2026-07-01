// 待機画面ビュー（Dumb / プレゼンテーショナル）。
//
// props で参加者一覧・状態・招待 URL・コピーハンドラを受け取り描画するだけ。
// API も共有状態も知らない。ポーリング更新・開始ボタンは本ストーリー対象外（US-05/06）。

export type Participant = {
  playerId: string;
  displayName: string;
};

export type WaitingRoomViewProps = {
  status: string;
  participants: Participant[];
  inviteUrl: string;
  onCopyInviteUrl: () => void;
  copied: boolean;
};

export function WaitingRoomView({
  status,
  participants,
  inviteUrl,
  onCopyInviteUrl,
  copied,
}: WaitingRoomViewProps) {
  return (
    <section aria-label="待機画面">
      <h1>待機中</h1>

      <p>
        状態: <span data-testid="game-status">{status}</span>
      </p>

      <h2>参加者</h2>
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
    </section>
  );
}
