// ゲーム作成フォーム（Dumb / プレゼンテーショナル）。
//
// props で選択肢（種別・プリセット）・送信ハンドラ・送信中フラグを受け取り描画するだけ。
// 入力欄の値（objectType / areaPreset / playerCount / displayName）は UI 固有の閉じた状態として
// 自身で管理してよい（CLAUDE.md）。API も共有状態も知らない。

import { useState } from "react";
import type { AreaPresetPayload } from "@/api/types";

export type CreateGameFormValues = {
  objectType: string;
  areaPreset: string;
  playerCount: number;
  displayName: string;
};

export type CreateGameFormProps = {
  objectTypes: string[];
  areaPresets: AreaPresetPayload[];
  submitting: boolean;
  onSubmit: (values: CreateGameFormValues) => void;
};

// 人数は初回リリース N=3 固定運用。UI は固定だが API へは可変で送る。
const DEFAULT_PLAYER_COUNT = 3;

export function CreateGameForm({
  objectTypes,
  areaPresets,
  submitting,
  onSubmit,
}: CreateGameFormProps) {
  const [objectType, setObjectType] = useState(objectTypes[0] ?? "");
  const [areaPreset, setAreaPreset] = useState(areaPresets[0]?.key ?? "");
  const [playerCount, setPlayerCount] = useState(DEFAULT_PLAYER_COUNT);
  const [displayName, setDisplayName] = useState("");

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    onSubmit({ objectType, areaPreset, playerCount, displayName });
  }

  return (
    <form onSubmit={handleSubmit} aria-label="ゲーム作成">
      <label>
        対象種別
        <select
          aria-label="対象種別"
          value={objectType}
          onChange={(e) => setObjectType(e.target.value)}
        >
          {objectTypes.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>
      </label>

      <label>
        面積プリセット
        <select
          aria-label="面積プリセット"
          value={areaPreset}
          onChange={(e) => setAreaPreset(e.target.value)}
        >
          {areaPresets.map((preset) => (
            <option key={preset.key} value={preset.key}>
              {preset.label}
            </option>
          ))}
        </select>
      </label>

      <label>
        人数
        <input
          aria-label="人数"
          type="number"
          min={3}
          value={playerCount}
          onChange={(e) => setPlayerCount(Number(e.target.value))}
        />
      </label>

      <label>
        表示名（任意）
        <input
          aria-label="表示名"
          type="text"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          placeholder="未入力なら自動で付与"
        />
      </label>

      <button type="submit" disabled={submitting}>
        {submitting ? "作成中…" : "ゲームを作成"}
      </button>
    </form>
  );
}
