import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { GameWalkthrough } from "@/features/game-walkthrough/GameWalkthrough";

function setup(targets = true) {
  const onFinish = vi.fn();
  const onStartLocationTracking = vi.fn();
  const content = (showTargets: boolean) => (
    <>
      {showTargets && (
        <>
          <div data-walkthrough="map">地図</div>
          <div data-walkthrough="area">面積</div>
          <div data-walkthrough="confirm">
            <button type="button" disabled>
              この位置で確定
            </button>
          </div>
        </>
      )}
      <GameWalkthrough
        areaThreshold={500_000}
        onStartLocationTracking={onStartLocationTracking}
        onFinish={onFinish}
      />
    </>
  );
  const view = render(content(targets));
  return {
    onFinish,
    onStartLocationTracking,
    user: userEvent.setup(),
    removeTargets: () => view.rerender(content(false)),
  };
}

describe("GameWalkthrough", () => {
  it("test_walkthrough_fourSteps_finishesOnlyOnLastAction", async () => {
    const { onFinish, user } = setup();
    expect(
      await screen.findByRole("alertdialog", { name: "みんなで街を囲もう" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "戻る" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "次へ" }));
    expect(
      await screen.findByRole("alertdialog", { name: "地図で仲間と範囲を確認" }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "次へ" }));
    expect(
      await screen.findByRole("alertdialog", { name: "面積の上限を確認" }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "戻る" }));
    expect(
      await screen.findByRole("alertdialog", { name: "地図で仲間と範囲を確認" }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "次へ" }));
    await user.click(screen.getByRole("button", { name: "次へ" }));
    expect(
      await screen.findByRole("alertdialog", { name: "位置を確定して結果を見る" }),
    ).toBeInTheDocument();
    expect(onFinish).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "探検を始める" }));
    await waitFor(() => expect(onFinish).toHaveBeenCalledTimes(1));
  });

  it.each(["スキップ", "閉じる"])("test_finish_%s_notifiesOnlyOnce", async (name) => {
    const { user, onFinish } = setup();
    await user.click(await screen.findByRole("button", { name }));
    await waitFor(() => expect(onFinish).toHaveBeenCalledTimes(1));
  });

  it("test_escape_firstStep_finishesWholeTour", async () => {
    const { onFinish } = setup();
    await screen.findByRole("alertdialog");
    fireEvent.keyDown(document, { key: "Escape" });
    expect(onFinish).toHaveBeenCalledTimes(1);
  });

  it("test_target_missing_showsSameExplanationInCenter", async () => {
    const { user, onFinish } = setup(false);
    await user.click(await screen.findByRole("button", { name: "次へ" }));
    expect(
      await screen.findByRole("alertdialog", { name: "地図で仲間と範囲を確認" }),
    ).toBeInTheDocument();
    expect(onFinish).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "閉じる" }));
    await waitFor(() => expect(onFinish).toHaveBeenCalledTimes(1));
  });
  it("test_target_hiddenAfterStart_keepsCurrentStepInCenter", async () => {
    const { user, onFinish } = setup();
    await screen.findByRole("alertdialog");
    const map = document.querySelector<HTMLElement>('[data-walkthrough="map"]');
    if (map) map.style.display = "none";
    await user.click(screen.getByRole("button", { name: "次へ" }));
    expect(
      await screen.findByRole("alertdialog", { name: "地図で仲間と範囲を確認" }),
    ).toBeInTheDocument();
    expect(onFinish).not.toHaveBeenCalled();
  });
  it("test_target_removedAfterStart_fallsBackWithoutSkippingExplanation", async () => {
    const { user, onFinish, removeTargets } = setup();
    await screen.findByRole("alertdialog");
    removeTargets();
    await user.click(screen.getByRole("button", { name: "次へ" }));
    await screen.findByRole("alertdialog", { name: "地図で仲間と範囲を確認" });
    expect(screen.queryByTestId("arrow")).not.toBeInTheDocument();
    expect(onFinish).not.toHaveBeenCalled();
  });

  it.each([
    "removed",
    "hidden",
    "ancestorHidden",
    "selectorRemoved",
  ])("test_currentTarget_%s_keepsSameExplanationAndExitWithoutFinishing", async (change) => {
    const { user, onFinish, removeTargets } = setup();
    await user.click(await screen.findByRole("button", { name: "次へ" }));
    await screen.findByRole("alertdialog", { name: "地図で仲間と範囲を確認" });
    await waitFor(() => expect(screen.getByTestId("arrow")).toBeInTheDocument());

    const map = document.querySelector<HTMLElement>('[data-walkthrough="map"]');
    expect(map).not.toBeNull();
    if (change === "removed") removeTargets();
    else {
      act(() => {
        if (change === "hidden" && map) map.style.display = "none";
        if (change === "ancestorHidden" && map?.parentElement) {
          map.parentElement.style.visibility = "hidden";
        }
        if (change === "selectorRemoved") map?.removeAttribute("data-walkthrough");
      });
    }

    await waitFor(() => {
      expect(screen.getByRole("alertdialog", { name: "地図で仲間と範囲を確認" })).toHaveTextContent(
        "2 / 4",
      );
      expect(screen.queryByTestId("arrow")).not.toBeInTheDocument();
    });
    expect(onFinish).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "閉じる" }));
    await waitFor(() => expect(onFinish).toHaveBeenCalledTimes(1));
  });
});
