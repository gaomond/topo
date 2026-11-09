import { useCallback, useEffect, useRef } from "react";
import {
  GeoTrackingContainer,
  type GeoTrackingContainerProps,
} from "@/features/geo-tracking/GeoTrackingContainer";
import { GameWalkthrough } from "./GameWalkthrough";
import { type GetWalkthroughStorage, useGameWalkthrough } from "./useGameWalkthrough";

type Props = Omit<
  GeoTrackingContainerProps,
  "locationEnabled" | "onOpenWalkthrough" | "walkthroughButtonRef"
> & { areaThreshold: number; getWalkthroughStorage?: GetWalkthroughStorage };

export function GameWalkthroughContainer({ getWalkthroughStorage, ...props }: Props) {
  const walkthrough = useGameWalkthrough(getWalkthroughStorage);
  const helpButton = useRef<HTMLButtonElement>(null);
  const restoreHelpFocus = useRef(false);
  const finishWalkthrough = useCallback(() => {
    restoreHelpFocus.current = true;
    walkthrough.finish();
  }, [walkthrough.finish]);
  useEffect(() => {
    if (walkthrough.isOpen || !restoreHelpFocus.current) return;
    const frame = requestAnimationFrame(() => {
      helpButton.current?.focus();
      restoreHelpFocus.current = false;
    });
    return () => cancelAnimationFrame(frame);
  }, [walkthrough.isOpen]);
  return (
    <>
      <div inert={walkthrough.isOpen} className="absolute inset-0" data-walkthrough-surface="">
        <GeoTrackingContainer
          {...props}
          locationEnabled={walkthrough.locationEnabled}
          onOpenWalkthrough={walkthrough.open}
          walkthroughButtonRef={helpButton}
        />
      </div>
      {walkthrough.isOpen && (
        <GameWalkthrough
          areaThreshold={props.areaThreshold}
          onStartLocationTracking={walkthrough.startLocationTracking}
          onFinish={finishWalkthrough}
        />
      )}
    </>
  );
}
