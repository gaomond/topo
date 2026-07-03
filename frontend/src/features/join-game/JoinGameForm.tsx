// 参加フォーム（Dumb / プレゼンテーショナル）。
//
// 名前入力欄＋参加ボタンを描画するだけ。入力値のみローカル state で持つ（UI 固有の閉じた状態）。
// API も共有状態も知らない。参加処理は props の onJoin（Smart が注入）に委ねる。

import { type FormEvent, useState } from "react";

export type JoinGameFormProps = {
  // 参加ボタン押下で呼ばれる。displayName 空文字はサーバー側フォールバック対象。
  onJoin: (displayName: string) => void;
  // 参加 API 実行中はボタンを無効化する。
  submitting: boolean;
};

export function JoinGameForm({ onJoin, submitting }: JoinGameFormProps) {
  const [displayName, setDisplayName] = useState("");

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    onJoin(displayName);
  };

  return (
    <section aria-label="参加画面">
      <h1>ゲームに参加</h1>
      <form onSubmit={handleSubmit}>
        <label>
          表示名（任意）
          <input
            type="text"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            placeholder="名前を入力（未入力可）"
          />
        </label>
        <button type="submit" disabled={submitting}>
          {submitting ? "参加中…" : "参加する"}
        </button>
      </form>
    </section>
  );
}
