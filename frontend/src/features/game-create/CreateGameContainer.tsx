// ゲーム作成コンテナ（Smart / 結線）。
//
// config 取得・作成 API 呼び出し・作成後ナビゲーションを担う。API を呼べるのは Smart のみ。
// api は注入可能にしてテストで差し替える（fakeGeolocation の DI パターンに倣う）。

import type { ReactNode } from "react";
import { useCallback, useState } from "react";
import { useNavigate } from "react-router-dom";
import useSWR from "swr";
import { type TopoApi, topoApi } from "@/api/topoApi";
import purposeImage from "@/features/waiting-room/assets/game-purpose-little-planet-v3.png";
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
      <CreateGameLayout>
        <div role="alert">
          <p>設定の取得に失敗しました。時間をおいて再読み込みしてください。</p>
        </div>
      </CreateGameLayout>
    );
  }

  if (!config) {
    return (
      <CreateGameLayout>
        <p role="status">設定を読み込み中…</p>
      </CreateGameLayout>
    );
  }

  return (
    <CreateGameLayout>
      {submitError !== null && <p role="alert">{submitError}</p>}
      <CreateGameForm
        objectTypes={config.objectTypes}
        areaPresets={config.areaPresets}
        submitting={submitting}
        onSubmit={handleSubmit}
      />
    </CreateGameLayout>
  );
}

function CreateGameLayout({ children }: { children: ReactNode }) {
  return (
    <main className="mx-auto max-w-7xl px-5 py-6 text-foreground sm:px-10 sm:py-10">
      <header className="mb-8 border-b border-input pb-5 sm:mb-12">
        <span className="text-4xl font-bold tracking-tight">topo</span>
      </header>
      <div className="grid items-start gap-8 lg:grid-cols-2 lg:gap-16">
        <section aria-labelledby="topo-concept" className="min-w-0 lg:pt-3">
          <h1
            id="topo-concept"
            className="text-3xl font-bold leading-relaxed tracking-tight sm:text-4xl lg:text-[2.75rem]"
          >
            歩いて、囲んで、
            <br />
            街を再発見。
          </h1>
          <p className="mt-4 text-sm leading-loose text-muted-foreground sm:mt-5 sm:text-base">
            仲間と歩いて、それぞれの位置を確定。
            <br />
            みんなで囲んだ範囲に、いくつの地物があるか見つけよう！
          </p>
          <p className="mt-3 text-xs leading-relaxed text-muted-foreground sm:text-sm">
            地物は、建物や施設などのことです。
          </p>
          <figure className="mt-4 flex items-center gap-3 lg:mt-8 lg:block">
            <img
              src={purposeImage}
              width={1536}
              height={1024}
              alt="3人の位置を結んだ三角形で地物を囲む模式図"
              className="h-auto w-24 rounded-xl sm:w-32 lg:w-full"
            />
            <figcaption className="text-xs leading-relaxed text-muted-foreground lg:mt-2 lg:text-center">
              仲間と位置を決めて、ひとつの範囲をつくる
            </figcaption>
          </figure>
        </section>
        <section
          aria-labelledby="create-form-title"
          className="min-w-0 rounded-3xl bg-surface p-6 sm:p-8"
        >
          <h2 id="create-form-title" className="text-2xl font-bold">
            ゲームを作成
          </h2>
          <p className="mb-6 mt-2 text-sm text-muted-foreground">仲間と囲む広さを選びましょう。</p>
          {children}
        </section>
      </div>
    </main>
  );
}
