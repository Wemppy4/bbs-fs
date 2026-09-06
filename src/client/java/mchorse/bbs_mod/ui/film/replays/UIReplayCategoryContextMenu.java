package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
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
public class UIReplayCategoryContextMenu extends UIContextMenu
{
    private static final int WIDTH = 140;

    public final UITextbox name;
    public final UIColor color;

    private final UIElement column;
    private final UIReplayList list;

    /** Where the folder is now — a rename moves it, and the next edit has to find it there. */
    private String path;
    private int picked;

    public UIReplayCategoryContextMenu(UIReplayList list, String path, int color)
    {
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

        this.column = UI.column(UIConstants.MARGIN, UIConstants.SCROLL_PADDING, UIConstants.CONTROL_HEIGHT,
            UI.label(UIKeys.SCENE_REPLAYS_EDIT_CATEGORY_TITLE),
            this.name,
            this.color
        );
        this.column.tooltip(UIKeys.SCENE_REPLAYS_EDIT_CATEGORY_DESCRIPTION);
        this.column.relative(this).w(WIDTH);

        this.add(this.column);
        this.column.resize();
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

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public void setMouse(UIContext context)
    {
        this.xy(context.mouseX(), context.mouseY())
            .wh(this.column.area.w, this.column.area.h)
            .bounds(context.menu.overlay, 5);
    }
}
