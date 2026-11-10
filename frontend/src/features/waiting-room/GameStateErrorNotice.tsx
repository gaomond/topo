import { Button } from "@/shared/ui/button";

export function GameStateErrorNotice({
  hasSnapshot,
  retrying,
  onRetry,
}: {
  hasSnapshot: boolean;
  retrying: boolean;
  onRetry: () => void;
}) {
  return (
    <section role="alert" className="shrink-0 border-b border-input bg-surface p-4 text-foreground">
      <div className="mx-auto flex max-w-2xl flex-wrap items-center justify-between gap-3">
        <p className="min-w-0 flex-1 text-sm leading-relaxed">
          {hasSnapshot
            ? "ゲーム情報を更新できませんでした。最後に取得した情報を表示しています。"
            : "ゲーム情報を取得できませんでした。通信状態を確認して再試行してください。"}
        </p>
        <Button type="button" disabled={retrying} aria-busy={retrying} onClick={onRetry}>
          {retrying ? "再取得中…" : "再試行"}
        </Button>
      </div>
    </section>
  );
}
