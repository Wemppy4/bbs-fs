package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.context.UIPopup;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.utils.categories.CategoryPath;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * What a folder of the replay list has to say for itself — its name and its colour — as a popup at
 * the cursor rather than a dialog in the middle of the screen: it is opened from the row it edits,
 * and looking at that row while typing is the whole point.
 *
 * <p>There is nothing to confirm here. The colour lands as it is picked; the name lands when the
 * field is left or the popup goes away, so a rename sweeps the replays once instead of once per
 * letter typed.</p>
 */
public class UIReplayCategoryContextMenu extends UIPopup
{
    public final UITextbox name;
    public final UIColor color;

    private final UIReplayList list;

    /** Where the folder is now — a rename moves it, and the next edit has to find it there. */
    private String path;
    private int picked;

    public UIReplayCategoryContextMenu(UIReplayList list, String path, int color)
    {
        super(UIKeys.SCENE_REPLAYS_EDIT_CATEGORY_TITLE);

        this.list = list;
        this.path = path;
        this.picked = color;

        this.name = new UITextbox(1000, (text) -> this.apply());
        this.name.delayedInput();
        this.name.setText(CategoryPath.name(path));
        this.name.placeholder(UIKeys.SCENE_REPLAYS_ADD_CATEGORY_PLACEHOLDER);

        this.color = new UIColor((value) ->
        {
            this.picked = value & Colors.RGB;

            this.apply();
        });
        this.color.setColor(color);

        this.column.add(this.name, this.color);
        this.column.tooltip(UIKeys.SCENE_REPLAYS_EDIT_CATEGORY_DESCRIPTION);
    }

    private void apply()
    {
        this.path = this.list.applyCategoryEdit(this.path, this.name.getText(), this.picked);
    }

    /**
     * Closing the popup is as much a way of finishing a name as leaving the field is — a click
     * outside takes both the focus and the popup, and only one of them would have been noticed.
     */
    @Override
    public void removeFromParent()
    {
        if (!CategoryPath.name(this.path).equals(this.name.getText()))
        {
            this.apply();
        }

        super.removeFromParent();
    }
}
