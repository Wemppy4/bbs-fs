package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.forms.FormCellRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.cells.CellAction;
import mchorse.bbs_mod.ui.utils.cells.CellActionBar;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * How a cell of the texture grid looks: a folder is its icon over its name; a texture is
 * the picture itself on a checkerboard, fitted whole into the cell, with a name strip once
 * the cell is wide enough and the quick actions along the top when hovered.
 */
public class TextureCellRenderer
{
    /** Cell width from which a texture cell carries its name. Folders always do. */
    public static final int NAME_THRESHOLD = 80;

    private static final int PADDING = 3;

    public static class State
    {
        public boolean hover;
        public boolean selected;
        public boolean picked;
        public boolean dragged;

        /** The folder a drag would drop into. */
        public boolean dropTarget;

        public int hoveredAction = -1;

        public State reset()
        {
            this.hover = this.selected = this.picked = this.dragged = this.dropTarget = false;
            this.hoveredAction = -1;

            return this;
        }
    }

    public static void render(UIContext context, TextureEntry entry, int x, int y, int w, int h, State state, CellAction[] actions)
    {
        Batcher2D batcher = context.batcher;
        int primary = BBSSettings.primaryColor.get();

        batcher.clip(x, y, w, h, context);

        if (state.selected || state.dropTarget)
        {
            batcher.box(x, y, x + w, y + h, Colors.A25 | primary);
        }
        else if (state.hover)
        {
            batcher.box(x, y, x + w, y + h, CellActionBar.ink(Colors.A12));
        }

        if (entry.folder())
        {
            renderFolder(context, entry, x, y, w, h, state);
        }
        else
        {
            renderTexture(context, entry, x, y, w, h, state);
        }

        if (state.dragged)
        {
            batcher.box(x, y, x + w, y + h, BBSSettings.color(BBSSettings.baseSurface(), Colors.A75));
        }

        if (state.hover && !state.dragged && CellActionBar.fits(w) && actions.length > 0)
        {
            CellActionBar.render(context, x, y, w, actions, state.hoveredAction);
        }

        if (state.selected || state.dropTarget)
        {
            batcher.outline(x, y, x + w, y + h, Colors.A100 | primary, 1);
        }
        else if (state.picked)
        {
            batcher.outline(x, y, x + w, y + h, Colors.A75 | primary, 1);
        }

        batcher.unclip(context);
    }

    private static void renderFolder(UIContext context, TextureEntry entry, int x, int y, int w, int h, State state)
    {
        /* The icon grows with the cell and sits in the space above the name strip */
        int room = h - FormCellRenderer.NAME_HEIGHT;
        int size = Math.max(16, Math.min(w / 2, room - 8));
        int cy = y + room / 2;

        context.batcher.scaledIcon(Icons.FOLDER, state.hover ? Colors.LIGHTEST_GRAY : Colors.WHITE, x + (w - size) / 2, cy - size / 2, size);
        FormCellRenderer.renderName(context, entry.caption(), x, y, w, h, state.hover || state.selected);
    }

    private static void renderTexture(UIContext context, TextureEntry entry, int x, int y, int w, int h, State state)
    {
        Batcher2D batcher = context.batcher;
        Texture texture = BBSModClient.getTextures().getTexture(entry.link());
        boolean name = w >= NAME_THRESHOLD;
        int px = x + PADDING;
        int py = y + PADDING;
        int pw = w - PADDING * 2;
        int ph = h - PADDING * 2 - (name ? FormCellRenderer.NAME_HEIGHT - PADDING : 0);

        if (texture == null || texture == BBSModClient.getTextures().getError())
        {
            batcher.icon(Icons.IMAGE, Colors.GRAY, x + w / 2, py + ph / 2, 0.5F, 0.5F);
        }
        else
        {
            int tw = Math.max(1, texture.width);
            int th = Math.max(1, texture.height);
            float scale = Math.min(pw / (float) tw, ph / (float) th);
            int fw = Math.max(1, Math.round(tw * scale));
            int fh = Math.max(1, Math.round(th * scale));
            int fx = px + (pw - fw) / 2;
            int fy = py + (ph - fh) / 2;

            batcher.iconArea(Icons.CHECKBOARD, fx, fy, fw, fh);
            batcher.fullTexturedBox(texture, fx, fy, fw, fh);
        }

        if (name)
        {
            FormCellRenderer.renderName(context, entry.caption(), x, y, w, h, state.hover || state.selected);
        }
    }
}
