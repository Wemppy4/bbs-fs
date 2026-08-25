package mchorse.bbs_mod.ui.forms;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.cells.CellAction;
import mchorse.bbs_mod.ui.utils.cells.CellActionBar;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * How a single form cell looks. The cell is a picture of the form first; everything else —
 * hover, selection, the name, the quick action bar — is laid over it and appears only when it
 * earns the space.
 *
 * <p>Which overlays fit depends on {@link #NAME_THRESHOLD the cell size}: a 40px cell is a
 * bare thumbnail, an 80px cell can carry its name, and from {@link CellActionBar#THRESHOLD}
 * up a hovered cell shows its actions along the top edge.</p>
 */
public class FormCellRenderer
{
    /** Cell width from which a name strip is drawn along the bottom edge. */
    public static final int NAME_THRESHOLD = 80;

    /** Height of the name strip along the bottom of a cell. */
    public static final int NAME_HEIGHT = 14;

    /** Everything about one cell that decides its overlays. Reused by the caller across cells. */
    public static class State
    {
        public boolean hover;

        /** The one form the list has chosen — what the editor edits and the morph wears. */
        public boolean selected;

        /** Part of a multi-selection. */
        public boolean picked;

        /** Being dragged right now: the cell stays in place but goes translucent. */
        public boolean dragged;

        /** Index of the hovered quick action, or -1. Only read while {@link #hover}. */
        public int hoveredAction = -1;

        public State reset()
        {
            this.hover = this.selected = this.picked = this.dragged = false;
            this.hoveredAction = -1;

            return this;
        }
    }

    public static boolean hasName(int cellWidth)
    {
        return cellWidth >= NAME_THRESHOLD;
    }

    public static void render(UIContext context, Form form, int x, int y, int w, int h, State state, CellAction[] actions)
    {
        Batcher2D batcher = context.batcher;
        int primary = BBSSettings.primaryColor.get();

        context.batcher.clip(x, y, w, h, context);

        if (state.selected)
        {
            batcher.box(x, y, x + w, y + h, Colors.A25 | primary);
        }
        else if (state.hover)
        {
            batcher.box(x, y, x + w, y + h, CellActionBar.ink(Colors.A12));
        }

        FormUtilsClient.renderPreview(form, context, x, y, x + w, y + h);

        if (state.dragged)
        {
            batcher.box(x, y, x + w, y + h, BBSSettings.color(BBSSettings.baseSurface(), Colors.A75));
        }

        boolean bar = state.hover && !state.dragged && CellActionBar.fits(w) && actions.length > 0;

        if (hasName(w))
        {
            renderName(context, form.getDisplayName(), x, y, w, h, state.hover || state.selected);
        }

        if (bar)
        {
            CellActionBar.render(context, x, y, w, actions, state.hoveredAction);
        }
        else
        {
            renderHotkey(context, form, x, y, w);
        }

        /* Frames go last so nothing paints over them */
        if (state.selected)
        {
            batcher.outline(x, y, x + w, y + h, Colors.A100 | primary, 1);
        }
        else if (state.picked)
        {
            batcher.outline(x, y, x + w, y + h, Colors.A75 | primary, 1);
        }

        context.batcher.unclip(context);
    }

    /** A caption along the bottom of a cell, on a gradient so it reads over any picture. */
    public static void renderName(UIContext context, String label, int x, int y, int w, int h, boolean bright)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();

        label = font.limitToWidth(label, w - 6);

        batcher.gradientVBox(x, y + h - NAME_HEIGHT - 8, x + w, y + h, 0, Colors.A75);
        batcher.textShadow(label, x + (w - font.getWidth(label)) / 2, y + h - NAME_HEIGHT + (NAME_HEIGHT - font.getHeight()) / 2 + 1, bright ? Colors.WHITE : Colors.LIGHTEST_GRAY);
    }

    private static void renderHotkey(UIContext context, Form form, int x, int y, int w)
    {
        int keybind = form.hotkey.get();

        if (keybind <= 0)
        {
            return;
        }

        FontRenderer font = context.batcher.getFont();
        String key = font.limitToWidth(KeyCodes.getName(keybind), w - 8);

        context.batcher.textCard(key, x + w - font.getWidth(key) - 4, y + 4, Colors.WHITE, Colors.A50, 2);
    }
}
