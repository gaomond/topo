import { useId, useState } from "react";
import type { AreaPresetPayload } from "@/api/types";
import { formatArea } from "@/shared/formatArea";
import { Button } from "@/shared/ui/button";
import { Input, inputClassName } from "@/shared/ui/input";
import { RadioGroup, RadioGroupItem } from "@/shared/ui/radio-group";
import { cn } from "@/shared/utils";

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
const objectTypeLabels: Record<string, string> = {
  shrine: "神社",
  temple: "寺院",
  school: "学校",
  convenience_store: "コンビニ",
  park: "公園",
  station: "駅",
};

export function CreateGameForm({
  objectTypes,
  areaPresets,
  submitting,
  onSubmit,
}: CreateGameFormProps) {
  const id = useId();
  const [objectType, setObjectType] = useState(objectTypes[0] ?? "");
  const [areaPreset, setAreaPreset] = useState(areaPresets[0]?.key ?? "");
  const [playerCount, setPlayerCount] = useState(3);
  const [displayName, setDisplayName] = useState("");
  return (
    <form
      className="space-y-6"
      aria-label="ゲーム作成"
      onSubmit={(event) => {
        event.preventDefault();
        if (!submitting) onSubmit({ objectType, areaPreset, playerCount, displayName });
      }}
    >
      <label className="grid gap-2 text-sm font-semibold">
        集計対象
        <select
          aria-label="集計対象"
          className={inputClassName}
          value={objectType}
          onChange={(event) => setObjectType(event.target.value)}
        >
          {objectTypes.map((type) => (
            <option key={type} value={type}>
              {objectTypeLabels[type] ?? type}
            </option>
          ))}
        </select>
      </label>
      <fieldset className="min-w-0 space-y-2">
        <legend className="mb-2 text-sm font-semibold">面積上限</legend>
        <RadioGroup aria-label="面積プリセット" value={areaPreset} onValueChange={setAreaPreset}>
          {areaPresets.map((preset, index) => (
            <label
              key={preset.key}
              htmlFor={`${id}-${index}`}
              className={cn(
                "flex min-h-15 cursor-pointer items-center gap-3 rounded-xl border border-input bg-surface px-4 py-4 text-sm transition-colors hover:bg-accent has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-primary",
                areaPreset === preset.key && "border-primary bg-accent",
              )}
            >
              <RadioGroupItem id={`${id}-${index}`} value={preset.key} />
              <span className="min-w-0 flex-1 font-semibold [overflow-wrap:anywhere]">
                {preset.label}
              </span>
              <span className="shrink-0 font-semibold text-primary">{formatArea(preset.sqm)}</span>
            </label>
          ))}
        </RadioGroup>
      </fieldset>
      <div className="grid gap-5 sm:grid-cols-[1fr_2fr]">
        <label htmlFor={`${id}-count`} className="grid gap-2 text-sm font-semibold">
          人数
          <Input
            id={`${id}-count`}
            aria-label="人数"
            type="number"
            min={3}
            value={playerCount}
            onChange={(event) => setPlayerCount(Number(event.target.value))}
          />
        </label>
        <label htmlFor={`${id}-name`} className="grid gap-2 text-sm font-semibold">
          表示名（任意）
          <Input
            id={`${id}-name`}
            aria-label="表示名"
            type="text"
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
            placeholder="名前を入力"
          />
        </label>
      </div>
      <Button type="submit" disabled={submitting} aria-busy={submitting} className="w-full">
        {submitting ? "作成中…" : "ゲームを作成"}
      </Button>
    </form>
  );
}
