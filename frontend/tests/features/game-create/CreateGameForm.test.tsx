import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { CreateGameForm } from "@/features/game-create/CreateGameForm";

describe("CreateGameForm", () => {
  it("test_selectArea_customConfig_preservesOrderValuesAndEditableInputs", async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(
      <CreateGameForm
        objectTypes={["shrine", "new_type"]}
        areaPresets={[
          { key: "near", label: "近所", sqm: 125000 },
          { key: "far", label: "遠足", sqm: 3250000 },
        ]}
        submitting={false}
        onSubmit={onSubmit}
      />,
    );
    const options = screen.getAllByRole("radio");
    expect(options[0]).toHaveAccessibleName(/近所.*0.125 km²/);
    expect(options[1]).toHaveAccessibleName(/遠足.*3.25 km²/);
    expect(options[0]).toBeChecked();
    await user.click(options[1]);
    await user.clear(screen.getByLabelText("人数"));
    await user.type(screen.getByLabelText("人数"), "4");
    await user.type(screen.getByLabelText("表示名"), "探検者");
    await user.selectOptions(screen.getByLabelText("集計対象"), "new_type");
    await user.click(screen.getByRole("button", { name: "ゲームを作成" }));
    expect(options.filter((option) => option.getAttribute("aria-checked") === "true")).toHaveLength(
      1,
    );
    expect(onSubmit).toHaveBeenCalledWith({
      objectType: "new_type",
      areaPreset: "far",
      playerCount: 4,
      displayName: "探検者",
    });
  });

  it("test_selectArea_arrowKey_selectsNextSingleCard", async () => {
    const user = userEvent.setup();
    render(
      <CreateGameForm
        objectTypes={["shrine"]}
        areaPresets={[
          { key: "a", label: "近所", sqm: 500000 },
          { key: "b", label: "遠足", sqm: 2000000 },
        ]}
        submitting={false}
        onSubmit={vi.fn()}
      />,
    );
    await user.click(screen.getByRole("radio", { name: /近所/ }));
    await user.keyboard("{ArrowDown>}");
    await waitFor(() => expect(screen.getByRole("radio", { name: /遠足/ })).toBeChecked());
    expect(screen.getByRole("radio", { name: /近所/ })).not.toBeChecked();
    await user.keyboard("{/ArrowDown}");
  });
});
