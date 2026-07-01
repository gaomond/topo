import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// 各テスト後に React Testing Library のレンダリング結果を破棄し、
// テスト間で DOM が漏れないようにする。
afterEach(() => {
  cleanup();
});
