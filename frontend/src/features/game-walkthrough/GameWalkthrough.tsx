import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import {
  ACTIONS,
  EVENTS,
  type EventHandler,
  Joyride,
  STATUS,
  type Step,
  type TooltipRenderProps,
} from "react-joyride";
import { formatArea } from "@/shared/formatArea";
import { Button } from "@/shared/ui/button";

function WalkthroughTooltip({
  index,
  isLastStep,
  step,
  tooltipProps,
  primaryProps,
  backProps,
  skipProps,
  closeProps,
}: TooltipRenderProps) {
  return (
    <div
      {...tooltipProps}
      role="alertdialog"
      aria-labelledby="walkthrough-title"
      aria-describedby="walkthrough-content"
      data-walkthrough-step={index}
      className="w-88 max-w-[calc(100vw-2rem)] max-h-[calc(100dvh-3rem)] overflow-auto rounded-3xl bg-surface p-5 text-left text-foreground shadow-xl"
    >
      <div className="mb-3 flex items-start justify-between gap-3">
        <h2 id="walkthrough-title" className="text-lg font-bold leading-snug">
          {step.title}
        </h2>
        <Button type="button" {...closeProps} variant="ghost" className="shrink-0 px-3">
          閉じる
        </Button>
      </div>
      <div id="walkthrough-content" className="text-sm leading-relaxed">
        {step.content}
      </div>
      <p className="mt-3 text-xs text-slate-500">{index + 1} / 4</p>
      <div className="mt-4 flex flex-wrap items-center justify-end gap-2">
        <Button type="button" {...skipProps} variant="ghost" className="mr-auto px-3">
          スキップ
        </Button>
        {index > 0 && (
          <Button type="button" {...backProps} variant="outline" className="px-3">
            戻る
          </Button>
        )}
        <Button type="button" {...primaryProps} className="px-3">
          {isLastStep ? "探検を始める" : "次へ"}
        </Button>
      </div>
    </div>
  );
}

export function GameWalkthrough({
  areaThreshold,
  onStartLocationTracking,
  onFinish,
}: {
  areaThreshold: number;
  onStartLocationTracking: () => void;
  onFinish: () => void;
}) {
  const [mounted, setMounted] = useState(false);
  const [stepIndex, setStepIndex] = useState(0);
  const [fallbackIndices, setFallbackIndices] = useState<ReadonlySet<number>>(new Set());
  const finished = useRef(false);
  useLayoutEffect(() => setMounted(true), []);

  const finish = useCallback(() => {
    if (finished.current) return;
    finished.current = true;
    onFinish();
  }, [onFinish]);

  const showStepInCenter = useCallback((index: number) => {
    setFallbackIndices((indices) => (indices.has(index) ? indices : new Set([...indices, index])));
  }, []);

  useEffect(() => {
    const finishOnEscape = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      event.preventDefault();
      event.stopPropagation();
      finish();
    };
    const preserveTooltipFocus = (event: PointerEvent) => {
      if (event.target instanceof Element && !event.target.closest("[data-walkthrough-step]")) {
        event.preventDefault();
      }
    };
    document.addEventListener("keydown", finishOnEscape, true);
    document.addEventListener("pointerdown", preserveTooltipFocus, true);
    return () => {
      document.removeEventListener("keydown", finishOnEscape, true);
      document.removeEventListener("pointerdown", preserveTooltipFocus, true);
    };
  }, [finish]);

  const steps = useMemo<Step[]>(() => {
    if (!mounted) return [];
    const definitions = [
      {
        title: "みんなで街を囲もう",
        content: (
          <ul className="list-disc space-y-2 pl-5">
            <li>
              {formatArea(areaThreshold)}
              以内で、できるだけ多くの地物（建物や施設など）を囲みましょう。
            </li>
            <li>現在地はサーバーに送信・保存され、同じゲームの参加者と共有されます。</li>
            <li>「次へ」で位置情報の取得を始めます。許可を求められたら選択してください。</li>
            <li>周囲の安全を確かめて遊びましょう。</li>
          </ul>
        ),
        selector: null,
      },
      {
        title: "地図で仲間と範囲を確認",
        content: (
          <ul className="list-disc space-y-2 pl-5">
            <li>仲間の位置と、みんなで囲む範囲を地図で確認しましょう。</li>
            <li>
              自分の位置マーカーは測位でき次第表示されます。許可前や測位待ちでも説明は進められます。
            </li>
            <li>みんなで囲む範囲は、3人以上の位置情報が揃うと表示されます。</li>
          </ul>
        ),
        selector: '[data-walkthrough="map"]',
      },
      {
        title: "面積の上限を確認",
        content: (
          <ul className="list-disc space-y-2 pl-5">
            <li>
              面積メーターで囲む範囲の面積と上限 {formatArea(areaThreshold)}{" "}
              を確認し、移動する方向を相談しましょう。
            </li>
            <li>3人以上の位置情報が揃うまでは、囲む範囲の面積は「未計測」と表示されます。</li>
          </ul>
        ),
        selector: '[data-walkthrough="area"]',
      },
      {
        title: "位置を確定して結果を見る",
        content: (
          <ul className="list-disc space-y-2 pl-5">
            <li>ここだと思った場所で位置を確定します。確定後は変更できません。</li>
            <li>確定時の面積条件を満たさない場合は、位置を調整して試し直しましょう。</li>
            <li>全員が確定すると、囲んだ範囲にある対象の地物の数が分かります。</li>
          </ul>
        ),
        selector: '[data-walkthrough="confirm"]',
      },
    ];
    return definitions.map(({ title, content, selector }, index) => {
      const target =
        selector && !fallbackIndices.has(index)
          ? document.querySelector<HTMLElement>(selector)
          : null;
      return {
        title,
        content,
        target: target && selector ? selector : document.body,
        placement: target ? "auto" : "center",
        isFixed: true,
        floatingOptions: { shiftOptions: { crossAxis: true, padding: 16 } },
      };
    });
  }, [areaThreshold, fallbackIndices, mounted]);

  useEffect(() => {
    const stepTarget = steps[stepIndex]?.target;
    if (typeof stepTarget !== "string") return;
    const selector = stepTarget;
    const target = document.querySelector<HTMLElement>(selector);
    const observer = new MutationObserver(checkTarget);
    let ancestors: HTMLElement[] = [];

    function checkTarget() {
      const currentAncestors: HTMLElement[] = [];
      for (let element = target; element; element = element.parentElement) {
        currentAncestors.push(element);
      }
      const isVisible = currentAncestors.every((element) => {
        const { display, visibility } = getComputedStyle(element);
        return !element.hidden && display !== "none" && visibility !== "hidden";
      });
      if (!target?.isConnected || document.querySelector(selector) !== target || !isVisible) {
        observer.disconnect();
        showStepInCenter(stepIndex);
        return;
      }
      if (currentAncestors.some((element, index) => element !== ancestors[index])) {
        observer.disconnect();
        ancestors = currentAncestors;
        // 表示中の対象とその祖先だけを監視し、Leafletや説明内のDOM更新は監視しない。
        for (const element of ancestors) {
          observer.observe(element, {
            childList: true,
            attributes: true,
            attributeFilter: ["class", "style", "hidden", "data-walkthrough"],
          });
        }
      }
    }

    checkTarget();
    window.addEventListener("resize", checkTarget);
    return () => {
      observer.disconnect();
      window.removeEventListener("resize", checkTarget);
    };
  }, [showStepInCenter, stepIndex, steps]);

  const onEvent = useCallback<EventHandler>(
    (data) => {
      if (
        data.type === EVENTS.TOUR_END &&
        (data.status === STATUS.FINISHED || data.status === STATUS.SKIPPED)
      ) {
        finish();
      } else if (data.type === EVENTS.TARGET_NOT_FOUND) {
        showStepInCenter(data.index);
      } else if (
        data.type === EVENTS.STEP_AFTER &&
        (data.action === ACTIONS.NEXT || data.action === ACTIONS.PREV)
      ) {
        if (data.action === ACTIONS.NEXT && data.index === 0) {
          onStartLocationTracking();
        }
        if (data.action === ACTIONS.NEXT && data.index === 3) {
          finish();
        } else {
          setStepIndex(data.index + (data.action === ACTIONS.PREV ? -1 : 1));
        }
      }
    },
    [finish, onStartLocationTracking, showStepInCenter],
  );

  if (!mounted) return null;
  // 欠落した対象を中央表示へ差し替えるときは、同じステップのライフサイクルを再開始する。
  return (
    <Joyride
      key={fallbackIndices.size}
      run
      continuous
      steps={steps}
      stepIndex={stepIndex}
      onEvent={onEvent}
      tooltipComponent={WalkthroughTooltip}
      locale={{
        back: "戻る",
        close: "閉じる",
        last: "探検を始める",
        next: "次へ",
        skip: "スキップ",
        open: "遊び方の説明を開く",
      }}
      options={{
        zIndex: 10000,
        width: 352,
        skipBeacon: true,
        skipScroll: true,
        targetWaitTimeout: 0,
        blockTargetInteraction: true,
        overlayClickAction: false,
        dismissKeyAction: false,
        closeButtonAction: "skip",
        buttons: ["back", "close", "primary", "skip"],
        spotlightPadding: 6,
      }}
    />
  );
}
