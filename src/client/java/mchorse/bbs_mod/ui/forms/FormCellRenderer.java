package mchorse.bbs_mod.ui.forms;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * How a single form cell looks. The cell is a picture of the form first; everything else —
 * hover, selection, the name, the type badge, the quick action bar — is laid over it and
 * appears only when it earns the space.
 *
 * <p>Which overlays fit depends on {@link #NAME_THRESHOLD the cell size}: a 40px cell is a
 * thumbnail with a type badge in its corner, an 80px cell can carry its name, and from
 * {@link #BAR_THRESHOLD} up a hovered cell shows its actions along the top edge.</p>
 */
public class FormCellRenderer
{
    /** Cell width from which a name strip is drawn along the bottom edge. */
    public static final int NAME_THRESHOLD = 80;

    /** Cell width from which the hovered cell shows its quick action bar. */
    public static final int BAR_THRESHOLD = 60;

    public static final int BAR_HEIGHT = 16;
    public static final int BAR_BUTTON = 16;

    /** Height of the name strip along the bottom of a cell. */
    public static final int NAME_HEIGHT = 14;

    private static final int BADGE = 12;

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

    /**
     * A translucent overlay that darkens on the light theme and lightens on the dark one —
     * the way hover and press read on any ground without being tied to a surface tone.
     */
    public static int ink(int alpha)
    {
        return alpha | (BBSSettings.isLightTheme() ? 0 : 0xffffff);
    }

    public static boolean hasName(int cellWidth)
    {
        return cellWidth >= NAME_THRESHOLD;
    }

    public static boolean hasBar(int cellWidth)
    {
        return cellWidth >= BAR_THRESHOLD;
    }

    /** Left edge of the first action button — the bar is right-aligned along the cell's top. */
    public static int getBarX(int cellX, int cellWidth, int actions)
    {
        return cellX + cellWidth - actions * BAR_BUTTON - 1;
    }

    /**
     * Which quick action is under a point of a cell, or -1. Only meaningful for a hovered cell
     * of a size that {@link #hasBar shows a bar}.
     */
    public static int getAction(int cellX, int cellY, int cellWidth, int actions, int x, int y)
    {
        if (y < cellY || y >= cellY + BAR_HEIGHT)
        {
            return -1;
        }

        int index = (x - getBarX(cellX, cellWidth, actions)) / BAR_BUTTON;

        return x >= getBarX(cellX, cellWidth, actions) && index >= 0 && index < actions ? index : -1;
    }

    public static void render(UIContext context, Form form, int x, int y, int w, int h, State state, FormCellAction[] actions)
    {
        Batcher2D batcher = context.batcher;
        int primary = BBSSettings.primaryColor.get();

        context.batcher.clip(x, y, w, h, context);

        /* Ground: a quiet raised tile under the cursor, the accent under the chosen form */
        if (state.selected)
        {
            batcher.box(x, y, x + w, y + h, Colors.A25 | primary);
        }
        else if (state.hover)
        {
            batcher.box(x, y, x + w, y + h, ink(Colors.A12));
        }

        FormUtilsClient.renderPreview(form, context, x, y, x + w, y + h);

        if (state.dragged)
        {
            batcher.box(x, y, x + w, y + h, BBSSettings.color(BBSSettings.baseSurface(), Colors.A75));
        }

        boolean name = hasName(w);
        boolean bar = state.hover && !state.dragged && hasBar(w) && actions.length > 0;

        if (name)
        {
            renderName(context, form, x, y, w, h, state);
        }
        else
        {
            renderBadge(context, form, x, y, w, h);
        }

        if (bar)
        {
            renderBar(context, x, y, w, state, actions);
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

        if (state.picked)
        {
            renderPickMark(context, x, y);
        }

        context.batcher.unclip(context);
    }

    /** The display name along the bottom, on a gradient so it reads over any picture. */
    private static void renderName(UIContext context, Form form, int x, int y, int w, int h, State state)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();
        String label = font.limitToWidth(form.getDisplayName(), w - 6);
        int color = state.hover || state.selected ? Colors.WHITE : Colors.LIGHTEST_GRAY;
        int ground = BBSSettings.isLightTheme() ? 0xffffff : 0;

        batcher.gradientVBox(x, y + h - NAME_HEIGHT - 8, x + w, y + h - NAME_HEIGHT, Colors.setA(ground, 0F), Colors.A75 | ground);
        batcher.box(x, y + h - NAME_HEIGHT, x + w, y + h, Colors.A75 | ground);
        batcher.textShadow(label, x + (w - font.getWidth(label)) / 2, y + h - NAME_HEIGHT + (NAME_HEIGHT - font.getHeight()) / 2 + 1, color);
    }

    /** With no room for a name, the kind of form goes in the corner instead. */
    private static void renderBadge(UIContext context, Form form, int x, int y, int w, int h)
    {
        int bx = x + w - BADGE - 2;
        int by = y + h - BADGE - 2;
        int ground = BBSSettings.isLightTheme() ? 0xffffff : 0;

        context.batcher.box(bx, by, bx + BADGE, by + BADGE, Colors.A50 | ground);
        context.batcher.scaledIcon(form.getIcon(), Colors.LIGHTEST_GRAY, bx + 1, by + 1, BADGE - 2);
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

    /** The quick actions, right-aligned along the top edge, in the same voice as the panel action bars. */
    private static void renderBar(UIContext context, int x, int y, int w, State state, FormCellAction[] actions)
    {
        Batcher2D batcher = context.batcher;
        int ground = BBSSettings.isLightTheme() ? 0xffffff : 0;
        int bx = getBarX(x, w, actions.length);

        batcher.box(x, y, x + w, y + BAR_HEIGHT, Colors.A75 | ground);
        batcher.gradientVBox(x, y + BAR_HEIGHT, x + w, y + BAR_HEIGHT + 6, Colors.A75 | ground, Colors.setA(ground, 0F));

        for (int i = 0; i < actions.length; i++)
        {
            FormCellAction action = actions[i];
            int ax = bx + i * BAR_BUTTON;
            boolean hovered = state.hoveredAction == i;
            int color = Colors.LIGHTER_GRAY;

            if (hovered)
            {
                int fill = action.danger ? Colors.A50 | Colors.RED : ink(Colors.A25);

                batcher.box(ax, y, ax + BAR_BUTTON, y + BAR_HEIGHT, fill);
                color = Colors.WHITE;
            }

            batcher.icon(action.icon, color, ax + BAR_BUTTON / 2, y + BAR_HEIGHT / 2, 0.5F, 0.5F);
        }
    }

    /** Small check in the corner, so a multi-selection reads at a glance at any zoom. */
    private static void renderPickMark(UIContext context, int x, int y)
    {
        int primary = BBSSettings.primaryColor.get();

        context.batcher.box(x, y, x + BADGE, y + BADGE, Colors.A100 | primary);
        context.batcher.scaledIcon(Icons.CHECKMARK, Colors.WHITE, x + 1, y + 1, BADGE - 2);
    }

    /**
     * The tooltip of the hovered quick action — drawn by the list after everything else so it
     * isn't clipped by the cell or covered by neighbours.
     */
    public static void renderActionLabel(UIContext context, FormCellAction action, int x, int y)
    {
        String label = action.label.get();
        FontRenderer font = context.batcher.getFont();
        int w = font.getWidth(label) + 6;

        x = Math.max(2, Math.min(x - w / 2, context.menu.width - w - 2));

        context.batcher.textCard(label, x + 3, y + 3, Colors.WHITE, Colors.A75, 3);
    }
}
