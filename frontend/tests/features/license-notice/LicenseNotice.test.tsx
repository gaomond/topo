import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { LicenseNotice } from "@/features/license-notice/LicenseNotice";

const links = [
  "/THIRD_PARTY_LICENSES.txt",
  "https://github.com/gaomond/topo/blob/main/LICENSE",
  "https://github.com/gaomond/topo/blob/main/docs/data-loading.md",
  "https://www.openstreetmap.org/copyright",
  "https://opendatacommons.org/licenses/odbl/1-0/",
];
describe("LicenseNotice", () => {
  it("test_open_withoutGame_showsScopedNoticesAndExactNewTabLinks", async () => {
    const fetch = vi.spyOn(globalThis, "fetch");
    render(<LicenseNotice>{() => <p>画面</p>}</LicenseNotice>);
    await userEvent.click(screen.getByRole("button", { name: "ライセンス・データ出典" }));
    const dialog = screen.getByRole("dialog", { name: "ライセンス・データ出典" });
    expect(dialog).toHaveTextContent("MIT License");
    expect(dialog).toHaveTextContent("Copyright (c) 2026 gaomond");
    expect(dialog).toHaveTextContent("© OpenStreetMap contributors");
    expect(dialog).toHaveTextContent("神社データには ODbL 1.0");
    expect(dialog).toHaveTextContent("依存ライブラリには、それぞれのライセンス");
    expect(dialog).toHaveTextContent("別タブ");
    expect(
      within(dialog).getByRole("link", { name: "依存ライブラリのライセンス" }),
    ).toHaveAttribute("href", "/THIRD_PARTY_LICENSES.txt");
    expect(
      within(dialog)
        .getAllByRole("link")
        .map((link) => link.getAttribute("href"))
        .sort(),
    ).toEqual(links.sort());
    for (const link of within(dialog).getAllByRole("link")) {
      expect(link).toHaveAttribute("target", "_blank");
      expect(link).toHaveAttribute("rel", "noopener noreferrer");
    }
    expect(fetch).not.toHaveBeenCalled();
    fetch.mockRestore();
  });
  it("test_close_enteredForm_keepsInputAndReturnsFocus", async () => {
    const submit = vi.fn();
    render(
      <LicenseNotice>
        {() => (
          <form onSubmit={submit}>
            <input aria-label="名前" />
            <button type="submit">作成</button>
          </form>
        )}
      </LicenseNotice>,
    );
    await userEvent.type(screen.getByRole("textbox"), "入力途中");
    const entry = screen.getByRole("button", { name: "ライセンス・データ出典" });
    await userEvent.click(entry);
    expect(screen.getByRole("button", { name: "閉じる" })).toHaveFocus();
    await userEvent.click(screen.getByRole("button", { name: "閉じる" }));
    expect(entry).toHaveFocus();
    expect(screen.getByRole("textbox")).toHaveValue("入力途中");
    expect(submit).not.toHaveBeenCalled();
  });
  it("test_tab_dialogEdges_keepsFocusInside", async () => {
    render(<LicenseNotice>{() => <button type="button">背景</button>}</LicenseNotice>);
    await userEvent.click(screen.getByRole("button", { name: "ライセンス・データ出典" }));
    const close = screen.getByRole("button", { name: "閉じる" });
    const last = screen.getByRole("link", { name: "依存ライブラリのライセンス" });
    fireEvent.keyDown(close, { key: "Tab", shiftKey: true });
    expect(last).toHaveFocus();
    fireEvent.keyDown(last, { key: "Tab" });
    expect(close).toHaveFocus();
    fireEvent(screen.getByRole("dialog"), new Event("cancel", { cancelable: true }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "ライセンス・データ出典" })).toHaveFocus();
  });
  it("test_open_existingBlockingDialog_disablesEntryUntilClosed", async () => {
    render(
      <LicenseNotice>
        {({ onBlockingDialogChange }) => (
          <button type="button" onClick={() => onBlockingDialogChange(true)}>
            確認開始
          </button>
        )}
      </LicenseNotice>,
    );
    await userEvent.click(screen.getByRole("button", { name: "確認開始" }));
    expect(screen.getByRole("button", { name: "ライセンス・データ出典" })).toBeDisabled();
  });
});
