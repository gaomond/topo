import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// 各テスト後に React Testing Library のレンダリング結果を破棄し、
// テスト間で DOM が漏れないようにする。
afterEach(() => {
  cleanup();
});

// jsdomはネイティブdialogの開閉メソッドを提供しないため、open状態だけを再現する。
HTMLDialogElement.prototype.showModal = function () {
  this.open = true;
};
HTMLDialogElement.prototype.close = function () {
  this.open = false;
};
