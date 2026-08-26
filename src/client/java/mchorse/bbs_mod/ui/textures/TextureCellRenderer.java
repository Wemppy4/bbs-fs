package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.cells.CellAction;
import mchorse.bbs_mod.ui.utils.cells.CellActionBar;
import mchorse.bbs_mod.ui.utils.cells.CellPainter;
import mchorse.bbs_mod.ui.utils.cells.CellState;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * How a cell of the texture grid looks on the shared {@link CellPainter cell ground}: a folder
 * is its icon (growing with the cell) over its name; a texture is the picture itself on a
 * checkerboard, fitted whole, with a name strip once the cell is wide enough and the quick
 * actions along the top when hovered.
 */
public class TextureCellRenderer
{
    /** Cell width from which a texture cell carries its name. Folders always do. */
    public static final int NAME_THRESHOLD = 80;

    private static final int PADDING = 3;

    public static void render(UIContext context, TextureEntry entry, int x, int y, int w, int h, CellState state, CellAction[] actions)
    {
        context.batcher.clip(x, y, w, h, context);

        CellPainter.ground(context, x, y, w, h, state);

        if (entry.folder())
        {
            renderFolder(context, entry, x, y, w, h, state);
        }
        else
        {
            renderTexture(context, entry, x, y, w, h, state);
        }

        CellPainter.dim(context, x, y, w, h, state);

        if (state.hover && !state.dragged && CellActionBar.fits(w) && actions.length > 0)
        {
            CellActionBar.render(context, x, y, w, actions, state.hoveredAction);
        }

        renderMarks(context, entry, x, y);

        CellPainter.frames(context, x, y, w, h, state);

        context.batcher.unclip(context);
    }

    /**
     * The corner marks, in a row from the left: a bookmark for what's pinned, a gear for what
     * is built into the mod (it can be copied out, but not changed in place). Both say
     * something about the cell that holds true whether or not the cursor is on it.
     */
    private static void renderMarks(UIContext context, TextureEntry entry, int x, int y)
    {
        int mx = x + 2;

        if (TexturePins.isPinned(entry.link()))
        {
            context.batcher.icon(Icons.BOOKMARK, Colors.LIGHTER_GRAY, mx, y + 2);
            mx += 18;
        }

        if (isReadOnly(entry))
        {
            context.batcher.icon(Icons.GEAR, Colors.LIGHTER_GRAY, mx, y + 2);
        }
    }

    /** Whether an entry lives inside the mod rather than on disk — nothing there can be changed in place. */
    public static boolean isReadOnly(TextureEntry entry)
    {
        return entry.folder() ? TextureFiles.isReadOnly(entry.link()) : !TextureFiles.canModify(entry.link());
    }

    private static void renderFolder(UIContext context, TextureEntry entry, int x, int y, int w, int h, CellState state)
    {
        /* The icon grows with the cell and sits in the space above the name strip */
        int room = h - CellPainter.CAPTION_HEIGHT;
        int size = Math.max(16, Math.min(w / 2, room - 8));
        int cy = y + room / 2;

        context.batcher.scaledIcon(Icons.FOLDER, state.hover ? Colors.LIGHTEST_GRAY : Colors.WHITE, x + (w - size) / 2, cy - size / 2, size);
        CellPainter.caption(context, entry.caption(), x, y, w, h, state.hover || state.selected);
    }

    private static void renderTexture(UIContext context, TextureEntry entry, int x, int y, int w, int h, CellState state)
    {
        Batcher2D batcher = context.batcher;
        Texture texture = BBSModClient.getTextures().getTexture(entry.link());
        boolean name = w >= NAME_THRESHOLD;
        int px = x + PADDING;
        int py = y + PADDING;
        int pw = w - PADDING * 2;
        int ph = h - PADDING * 2 - (name ? CellPainter.CAPTION_HEIGHT - PADDING : 0);

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
            CellPainter.caption(context, entry.caption(), x, y, w, h, state.hover || state.selected);
        }
    }
}
