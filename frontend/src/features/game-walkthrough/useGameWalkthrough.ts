import { useCallback, useState } from "react";

export const WALKTHROUGH_STORAGE_KEY = "topo.walkthrough.finished";
export type WalkthroughStorage = Pick<Storage, "getItem" | "setItem">;
export type GetWalkthroughStorage = () => WalkthroughStorage;

const getBrowserStorage: GetWalkthroughStorage = () => window.localStorage;

function readFinished(getStorage: GetWalkthroughStorage): boolean {
  try {
    return getStorage().getItem(WALKTHROUGH_STORAGE_KEY) === "true";
  } catch {
    return false;
  }
}

export function useGameWalkthrough(getStorage: GetWalkthroughStorage = getBrowserStorage) {
  const [locationEnabled, setLocationEnabled] = useState(() => readFinished(getStorage));
  const [isOpen, setIsOpen] = useState(!locationEnabled);
  const startLocationTracking = useCallback(() => setLocationEnabled(true), []);

  const finish = useCallback(() => {
    try {
      getStorage().setItem(WALKTHROUGH_STORAGE_KEY, "true");
    } catch {
      // 保存できない環境でも、この画面内では終了状態を保持して探索へ進む。
    }
    setIsOpen(false);
    setLocationEnabled(true);
  }, [getStorage]);
  const open = useCallback(() => setIsOpen(true), []);

  return { isOpen, locationEnabled, startLocationTracking, open, finish };
}
