// ルートコンポーネント。US-04 でルーティングを導入する。
//
// `/`（作成画面）→ 作成後 `/game/<gameId>?p=<playerId>`（待機画面）へナビゲートする。
// Router プロバイダ（BrowserRouter）は main.tsx で供給し、ここではルート定義のみを持つ。

import { useCallback, useState } from "react";
import { Route, Routes, useLocation } from "react-router-dom";
import { CreateGameContainer } from "@/features/game-create/CreateGameContainer";
import { LicenseNotice } from "@/features/license-notice/LicenseNotice";
import {
  WaitingRoomContainer,
  type WaitingRoomContainerProps,
} from "@/features/waiting-room/WaitingRoomContainer";
import { CREATE_PATH, GAME_ROUTE_PATTERN } from "@/routing/paths";
import { PageNotFoundScreen } from "@/shared/PageNotFoundScreen";

type AppProps = Pick<
  WaitingRoomContainerProps,
  "api" | "geoDeps" | "getWalkthroughStorage" | "refreshIntervalMs"
>;

export function App(props: AppProps = {}) {
  const { pathname, search } = useLocation();
  const pageKey = `${pathname}${search}`;
  const [waitingRoomEntry, setWaitingRoomEntry] = useState({ pageKey: "", visible: false });
  const updateWaitingRoomEntry = useCallback(
    (visible: boolean) => {
      setWaitingRoomEntry((previous) =>
        previous.pageKey === pageKey && previous.visible === visible
          ? previous
          : { pageKey, visible },
      );
    },
    [pageKey],
  );
  const entryVisible =
    pathname === CREATE_PATH || (waitingRoomEntry.pageKey === pageKey && waitingRoomEntry.visible);
  return (
    <LicenseNotice entryVisible={entryVisible}>
      {(bindings) => (
        <Routes>
          <Route path={CREATE_PATH} element={<CreateGameContainer api={props.api} />} />
          <Route
            path={GAME_ROUTE_PATTERN}
            element={
              <WaitingRoomContainer
                {...props}
                {...bindings}
                onLicenseEntryVisibilityChange={updateWaitingRoomEntry}
              />
            }
          />
          <Route path="*" element={<PageNotFoundScreen />} />
        </Routes>
      )}
    </LicenseNotice>
  );
}
