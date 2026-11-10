import { Check, Copy } from "lucide-react";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { GamePurposeView } from "./GamePurposeView";

export type Participant = { playerId: string; displayName: string; confirmed: boolean };
export type WaitingRoomViewProps = {
  participants: Participant[];
  playerCount: number;
  areaThreshold: number;
  inviteUrl: string;
  onCopyInviteUrl: () => void;
  copied: boolean;
  copyFailed?: boolean;
  onStart: () => void;
  startEnabled: boolean;
  starting: boolean;
};

export function WaitingRoomView({
  participants,
  playerCount,
  areaThreshold,
  inviteUrl,
  onCopyInviteUrl,
  copied,
  copyFailed,
  onStart,
  startEnabled,
  starting,
}: WaitingRoomViewProps) {
  return (
    <section
      aria-label="待機画面"
      className="mx-auto my-6 flex max-w-2xl flex-col gap-6 rounded-3xl bg-surface p-5 text-foreground sm:my-10 sm:p-8"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="flex items-center gap-3 text-2xl font-bold">
          <span>待機中</span>
          <span aria-hidden="true" className="flex w-10 justify-between text-primary">
            {[0, 1, 2].map((index) => (
              <span
                key={index}
                className="waiting-dot"
                style={{ animationDelay: `${index * 0.2}s` }}
              >
                •
              </span>
            ))}
          </span>
        </h1>
        <p className="text-sm font-semibold text-muted-foreground">
          参加者 <span data-testid="participant-count">{participants.length}</span> / {playerCount}
        </p>
      </div>
      <GamePurposeView areaThreshold={areaThreshold} />
      <section aria-labelledby="participants-title">
        <h2 id="participants-title" className="mb-3 text-sm font-semibold">
          参加者
        </h2>
        <ul aria-label="参加者一覧" className="space-y-2">
          {participants.map((participant) => (
            <li
              key={participant.playerId}
              className="rounded-xl bg-secondary px-4 py-3 text-sm font-medium [overflow-wrap:anywhere]"
            >
              {participant.displayName}
            </li>
          ))}
        </ul>
      </section>
      <section aria-labelledby="invite-title">
        <h2 id="invite-title" className="mb-2 text-sm font-semibold">
          仲間を招待
        </h2>
        <div className="flex items-center gap-2 rounded-xl border border-input bg-background p-1">
          <Input
            aria-label="招待URL"
            data-testid="invite-url"
            value={inviteUrl}
            readOnly
            className="border-0 bg-transparent px-3 shadow-none"
          />
          <Button
            variant="ghost"
            size="icon"
            type="button"
            aria-label="招待URLをコピー"
            onClick={onCopyInviteUrl}
          >
            {copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
          </Button>
        </div>
        <p role="status" className="mt-2 min-h-5 text-xs leading-relaxed text-muted-foreground">
          {copied
            ? "コピーしました"
            : copyFailed
              ? "コピーできませんでした。URLを選択してコピーしてください"
              : "URLを共有して仲間を招待できます。"}
        </p>
      </section>
      <Button
        type="button"
        onClick={onStart}
        disabled={!startEnabled || starting}
        aria-busy={starting}
        className="w-full"
      >
        {starting ? "開始しています…" : "ゲームを開始"}
      </Button>
    </section>
  );
}
