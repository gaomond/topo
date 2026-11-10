import { useCallback, useEffect, useRef, useState } from "react";
import {
  GeoTrackingContainer,
  type GeoTrackingContainerProps,
} from "@/features/geo-tracking/GeoTrackingContainer";
import { GameWalkthrough } from "./GameWalkthrough";
import { type GetWalkthroughStorage, useGameWalkthrough } from "./useGameWalkthrough";

type Props = Omit<
  GeoTrackingContainerProps,
  "locationEnabled" | "onOpenWalkthrough" | "walkthroughButtonRef"
> & { areaThreshold: number; getWalkthroughStorage?: GetWalkthroughStorage; licenseOpen?: boolean };

export function GameWalkthroughContainer({
  getWalkthroughStorage,
  licenseOpen = false,
  onBlockingDialogChange,
  ...props
}: Props) {
  const walkthrough = useGameWalkthrough(getWalkthroughStorage);
  const [confirmationOpen, setConfirmationOpen] = useState(false);
  const walkthroughVisible = walkthrough.isOpen && !licenseOpen;
  useEffect(() => {
    onBlockingDialogChange?.(walkthroughVisible || confirmationOpen);
    return () => onBlockingDialogChange?.(false);
  }, [onBlockingDialogChange, walkthroughVisible, confirmationOpen]);
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
      <div inert={walkthroughVisible} className="absolute inset-0" data-walkthrough-surface="">
        <GeoTrackingContainer
          {...props}
          locationEnabled={walkthrough.locationEnabled}
          onOpenWalkthrough={licenseOpen || confirmationOpen ? undefined : walkthrough.open}
          interactionBlocked={licenseOpen || walkthroughVisible}
          onBlockingDialogChange={setConfirmationOpen}
          walkthroughButtonRef={helpButton}
        />
      </div>
      {walkthroughVisible && (
        <GameWalkthrough
          areaThreshold={props.areaThreshold}
          onStartLocationTracking={walkthrough.startLocationTracking}
          onFinish={finishWalkthrough}
        />
      )}
    </>
  );
}
