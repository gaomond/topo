// 参加フォーム（Dumb / プレゼンテーショナル）。
//
// 名前入力欄＋参加ボタンを描画するだけ。入力値のみローカル state で持つ（UI 固有の閉じた状態）。
// API も共有状態も知らない。参加処理は props の onJoin（Smart が注入）に委ねる。

import { type FormEvent, useId, useState } from "react";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";

export type JoinGameFormProps = {
  // 参加ボタン押下で呼ばれる。displayName 空文字はサーバー側フォールバック対象。
  onJoin: (displayName: string) => void;
  // 参加 API 実行中はボタンを無効化する。
  submitting: boolean;
};

export function JoinGameForm({ onJoin, submitting }: JoinGameFormProps) {
  const nameId = useId();
  const [displayName, setDisplayName] = useState("");

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!submitting) onJoin(displayName);
  };

  return (
    <section
      aria-label="参加画面"
      className="mx-auto my-8 max-w-lg rounded-3xl bg-surface p-6 text-foreground sm:my-16 sm:p-8"
    >
      <p className="mb-6 text-sm font-semibold text-muted-foreground">topo</p>
      <h1 className="mb-3 text-2xl font-bold">ゲームに参加</h1>
      <p className="mb-6 text-sm leading-relaxed text-muted-foreground">
        仲間と歩いて、街を囲もう。名前を入力して参加できます。
      </p>
      <form onSubmit={handleSubmit} className="space-y-6">
        <label htmlFor={nameId} className="grid gap-2 text-sm font-semibold">
          表示名（任意）
          <Input
            id={nameId}
            type="text"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            placeholder="名前を入力（未入力可）"
          />
        </label>
        <Button type="submit" disabled={submitting} aria-busy={submitting} className="w-full">
          {submitting ? "参加中…" : "参加する"}
        </Button>
      </form>
    </section>
  );
}
