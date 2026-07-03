// ゲーム作成コンテナ（Smart / 結線）。
//
// config 取得・作成 API 呼び出し・作成後ナビゲーションを担う。API を呼べるのは Smart のみ。
// api は注入可能にしてテストで差し替える（fakeGeolocation の DI パターンに倣う）。

import { useCallback, useState } from "react";
import { useNavigate } from "react-router-dom";
import useSWR from "swr";
import { type TopoApi, topoApi } from "@/api/topoApi";
import { buildGamePath } from "@/routing/paths";
import { CreateGameForm, type CreateGameFormValues } from "./CreateGameForm";

export type CreateGameContainerProps = {
  // テスト用に API を注入できる（既定はブラウザ実体）。
  api?: TopoApi;
};

export function CreateGameContainer({ api }: CreateGameContainerProps = {}) {
  const navigate = useNavigate();
  // 既定は共有シングルトン topoApi（identity が安定）。api を注入した場合はそれを使う。
  const resolvedApi = api ?? topoApi;

  // config は静的な一発取得。自前 useEffect を組まず SWR に寄せる（01-spec 1.5 と同じ流儀）。
  // キーは定数なのでフェッチはマウント時 1 回のみ（fetchConfig 無限ループの心配がない）。
  // 静的なのでフォーカス復帰の再取得は不要、エラーはリトライせず即 error 画面へ。
  const { data: config, error: configError } = useSWR(
    "app-config",
    () => resolvedApi.fetchConfig(),
    {
      revalidateOnFocus: false,
      shouldRetryOnError: false,
    },
  );
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const handleSubmit = useCallback(
    async (values: CreateGameFormValues) => {
      setSubmitting(true);
      setSubmitError(null);
      try {
        const created = await resolvedApi.createGame({
          objectType: values.objectType,
          areaPreset: values.areaPreset,
          playerCount: values.playerCount,
          // 空文字はサーバー側フォールバック対象。未入力は送らない。
          displayName: values.displayName.trim() === "" ? undefined : values.displayName,
        });
        // 作成後は自分の URL（gameId + playerId）で待機画面へ遷移する。
        navigate(buildGamePath(created.gameId, created.playerId));
      } catch {
        setSubmitError("ゲームの作成に失敗しました。もう一度お試しください。");
        setSubmitting(false);
      }
    },
    [resolvedApi, navigate],
  );

  if (configError) {
    return (
      <div role="alert">
        <p>設定の取得に失敗しました。時間をおいて再読み込みしてください。</p>
      </div>
    );
  }

  if (!config) {
    return <p role="status">設定を読み込み中…</p>;
  }

  return (
    <div>
      <h1>ゲームを作成</h1>
      {submitError !== null && <p role="alert">{submitError}</p>}
      <CreateGameForm
        objectTypes={config.objectTypes}
        areaPresets={config.areaPresets}
        submitting={submitting}
        onSubmit={handleSubmit}
      />
    </div>
  );
}
