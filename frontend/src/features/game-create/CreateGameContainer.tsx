// ゲーム作成コンテナ（Smart / 結線）。
//
// config 取得・作成 API 呼び出し・作成後ナビゲーションを担う。API を呼べるのは Smart のみ。
// api は注入可能にしてテストで差し替える（fakeGeolocation の DI パターンに倣う）。

import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { createTopoApi, type TopoApi } from "@/api/topoApi";
import type { ConfigResponse } from "@/api/types";
import { buildGamePath } from "@/routing/paths";
import { CreateGameForm, type CreateGameFormValues } from "./CreateGameForm";

export type CreateGameContainerProps = {
  // テスト用に API を注入できる（既定はブラウザ実体）。
  api?: TopoApi;
};

type LoadState = "loading" | "ready" | "error";

export function CreateGameContainer({ api }: CreateGameContainerProps = {}) {
  const navigate = useNavigate();
  // 既定の API 実体は毎レンダリング生成せず安定させる。ここを default 引数で生成すると
  // レンダリングごとに別インスタンスになり、下の useEffect([resolvedApi]) が毎回再実行され
  // fetchConfig が無限ループする。api を注入した場合はそれをそのまま使う。
  const resolvedApi = useMemo(() => api ?? createTopoApi(), [api]);
  const [config, setConfig] = useState<ConfigResponse | null>(null);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    resolvedApi
      .fetchConfig()
      .then((loaded) => {
        if (!active) return;
        setConfig(loaded);
        setLoadState("ready");
      })
      .catch(() => {
        if (!active) return;
        setLoadState("error");
      });
    return () => {
      active = false;
    };
  }, [resolvedApi]);

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

  if (loadState === "loading") {
    return <p role="status">設定を読み込み中…</p>;
  }

  if (loadState === "error" || config === null) {
    return (
      <div role="alert">
        <p>設定の取得に失敗しました。時間をおいて再読み込みしてください。</p>
      </div>
    );
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
