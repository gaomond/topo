import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { App } from "./App.tsx";
import "./index.css";

const rootEl = document.getElementById("root");
if (rootEl === null) {
  throw new Error("ルート要素 #root が見つかりません");
}

createRoot(rootEl).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
);
