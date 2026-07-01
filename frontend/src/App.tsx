// ルートコンポーネント。US-04 でルーティングを導入する。
//
// `/`（作成画面）→ 作成後 `/game/<gameId>?p=<playerId>`（待機画面）へナビゲートする。
// Router プロバイダ（BrowserRouter）は main.tsx で供給し、ここではルート定義のみを持つ。

import { Route, Routes } from "react-router-dom";
import { CreateGameContainer } from "@/features/game-create/CreateGameContainer";
import { WaitingRoomContainer } from "@/features/waiting-room/WaitingRoomContainer";
import { CREATE_PATH, GAME_ROUTE_PATTERN } from "@/routing/paths";

export function App() {
  return (
    <Routes>
      <Route path={CREATE_PATH} element={<CreateGameContainer />} />
      <Route path={GAME_ROUTE_PATTERN} element={<WaitingRoomContainer />} />
    </Routes>
  );
}
