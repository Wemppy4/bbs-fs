package mchorse.bbs_mod.ui.dashboard.textures.frames;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.textures.PixelMacro;
import mchorse.bbs_mod.ui.dashboard.textures.data.Document;
import mchorse.bbs_mod.ui.dashboard.textures.data.TextureAnimation;
import mchorse.bbs_mod.ui.dashboard.textures.data.TextureLayer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.items.UIItemGrid;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.GridLayout;
import mchorse.bbs_mod.ui.utils.cells.CellAction;
import mchorse.bbs_mod.ui.utils.cells.CellActionBar;
import mchorse.bbs_mod.ui.utils.cells.CellPainter;
import mchorse.bbs_mod.ui.utils.cells.CellState;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The frames of a texture's animation as a row of cells under the canvas, in the order they are
 * shown: each cell is its image (live, off the layers) with its number in a corner and its own
 * duration in the other when it has one. The picking, the band, the drag that reorders and the
 * keyboard come from {@link UIItemGrid}; what a choice means is handed to the
 * {@link UIFramesPanel panel}, which owns the transport and talks to the editor.
 *
 * <p>Cells are frames, not images: two frames showing the same image are two cells, and a cell
 * pointing past the strip (a broken file) shows a question mark and is left as it is.</p>
 */
public class UIFrameStrip extends UIItemGrid<TextureAnimation.Frame>
{
    public static final int MIN_CELL = 24;

    private static final int PADDING = 3;
    private static final CellAction[] ACTIONS = {CellAction.DUPLICATE, CellAction.REMOVE};
    private static final List<TextureAnimation.Frame> NONE = Collections.emptyList();

    private final UIFramesPanel panel;

    /* The frame a right click landed on, for the context menu; -1 for the empty space */
    private int contextIndex = -1;

    public UIFrameStrip(UIFramesPanel panel)
    {
        /* Frames are objects: the same frame is the same pick, even after the order changed */
        super(null, new GridLayout(0, 4, 3, 4, 4, 1F));

        this.panel = panel;

        this.horizontal();
        this.multi();
        this.sorting();
        this.scroll.cancelScrolling();
        this.context(this::buildContextMenu);
    }

    /* Geometry */

    @Override
    protected List<TextureAnimation.Frame> visible()
    {
        TextureAnimation animation = this.panel.animation();

        return animation == null ? NONE : animation.frames;
    }

    public void scrollTo(int index)
    {
        this.scrollIntoView(index);
    }

    /** The cells are as tall as the row allows: one row, filling the strip. */
    @Override
    public void resize()
    {
        super.resize();

        this.setCellSize(Math.max(MIN_CELL, this.area.h - this.layout.getHeader() - 8));
    }

    /* Hooks */

    @Override
    protected CellAction[] actions(TextureAnimation.Frame item)
    {
        return ACTIONS;
    }

    @Override
    protected void onAction(TextureAnimation.Frame item, CellAction action)
    {
        if (action == CellAction.DUPLICATE)
        {
            this.panel.duplicate(this.group(item));
        }
        else if (action == CellAction.REMOVE)
        {
            this.panel.remove(this.group(item));
        }
    }

    @Override
    protected boolean onDelete(List<TextureAnimation.Frame> items)
    {
        this.panel.remove(items);

        return true;
    }

    /** A drop between the cells moves the frames; with Ctrl held it puts copies there, as in the texture browser. */
    @Override
    protected void reorder(List<TextureAnimation.Frame> items, int insertion)
    {
        if (this.drag.isCopy())
        {
            this.panel.copyTo(items, insertion);
        }
        else
        {
            this.panel.move(items, insertion);
        }
    }

    /** Ctrl + wheel flips the frames instead of scrolling the row. */
    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        if (Window.isCtrlPressed() && context.mouseWheel != 0 && this.area.isInside(context))
        {
            this.panel.step(context.mouseWheel > 0 ? -1 : 1);

            return true;
        }

        return super.subMouseScrolled(context);
    }

    /** A plain click shows the frame; Ctrl and Shift only change the pick, the way they do everywhere. */
    @Override
    protected void applySelectionOnClick(TextureAnimation.Frame item, int index)
    {
        super.applySelectionOnClick(item, index);

        if (!Window.isCtrlPressed() && !Window.isShiftPressed())
        {
            this.panel.show(index);
        }
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        int before = this.cursor;
        boolean handled = super.subKeyPressed(context);

        /* The keyboard walked to another cell: that frame is on show now, the way a click would have it */
        if (handled && this.cursor != before && this.cursor >= 0 && this.cursor < this.visible().size())
        {
            this.panel.show(this.cursor);
        }

        return handled;
    }

    /** What an action on a cell works on: the whole pick when the cell is one of it, the cell alone otherwise. */
    private List<TextureAnimation.Frame> group(TextureAnimation.Frame item)
    {
        return this.selection.contains(item) ? new ArrayList<>(this.selection.getItems()) : Collections.singletonList(item);
    }

    /* Context menu */

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 1 && this.area.isInside(context))
        {
            this.contextIndex = this.indexAt(this.contentX(context), this.contentY(context));
        }

        return super.subMouseClicked(context);
    }

    /** One order throughout: add → copy → order → pixels → time → remove, the destructive one last. */
    private void buildContextMenu(ContextMenuManager menu)
    {
        List<TextureAnimation.Frame> visible = this.visible();
        int index = this.contextIndex;
        TextureAnimation.Frame frame = index >= 0 && index < visible.size() ? visible.get(index) : null;

        if (this.panel.animation() == null)
        {
            return;
        }

        if (frame == null)
        {
            menu.icon(MenuVerb.ADD, () -> this.panel.insert(visible.size())).label(UIKeys.TEXTURES_FRAMES_ADD);

            return;
        }

        List<TextureAnimation.Frame> group = this.group(frame);
        /* A ping-pong runs over the pick, or over the whole order when only this frame is picked */
        int run = group.size() > 1 ? group.size() : visible.size();

        menu.action(Icons.ADD, UIKeys.TEXTURES_FRAMES_INSERT_BEFORE, () -> this.panel.insert(index));
        menu.action(Icons.ADD, UIKeys.TEXTURES_FRAMES_INSERT_AFTER, () -> this.panel.insert(index + 1));
        menu.action(Icons.DUPE, UIKeys.TEXTURES_FRAMES_DUPLICATE, () -> this.panel.duplicate(group));
        /* Order entries only when they'd do something: one frame has no order, a run of two no way back */
        if (group.size() > 1)
        {
            menu.action(Icons.REVERSE, UIKeys.TEXTURES_FRAMES_REVERSE, () -> this.panel.reverse(group));
        }

        if (run >= 3)
        {
            menu.action(Icons.REFRESH, UIKeys.TEXTURES_FRAMES_PING_PONG, () -> this.panel.pingPong(group));
        }

        menu.action(Icons.ERASER, UIKeys.TEXTURES_FRAMES_CLEAR, () -> this.panel.macro(PixelMacro.CLEAR, group));
        menu.action(Icons.HORIZONTAL, UIKeys.TEXTURES_MACROS_FLIP_H, () -> this.panel.macro(PixelMacro.FLIP_HORIZONTAL, group));
        menu.action(Icons.VERTICAL, UIKeys.TEXTURES_MACROS_FLIP_V, () -> this.panel.macro(PixelMacro.FLIP_VERTICAL, group));
        menu.action(Icons.TIME, UIKeys.TEXTURES_FRAMES_TIME, () -> this.panel.askTime(group));
        menu.icon(MenuVerb.REMOVE, () -> this.panel.remove(group)).label(UIKeys.GENERAL_REMOVE).enabled(visible.size() > group.size());
    }

    /* Rendering */

    @Override
    protected void renderCell(UIContext context, TextureAnimation.Frame item, int x, int y, int w, int h, CellState state)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();
        Document document = this.panel.document();
        TextureAnimation animation = this.panel.animation();
        int position = this.visible().indexOf(item);

        /* The chosen cell is the frame on show, not the last picked */
        state.selected = position == this.panel.shown();

        batcher.clip(x, y, w, h, context);

        CellPainter.ground(context, x, y, w, h, state);

        if (document != null && animation != null)
        {
            int fw = Math.max(1, document.frameWidth());
            int fh = Math.max(1, document.frameHeight());
            int pw = w - PADDING * 2;
            int ph = h - PADDING * 2;
            float scale = Math.min(pw / (float) fw, ph / (float) fh);
            int dw = Math.max(1, Math.round(fw * scale));
            int dh = Math.max(1, Math.round(fh * scale));
            int dx = x + PADDING + (pw - dw) / 2;
            int dy = y + PADDING + (ph - dh) / 2;

            batcher.iconArea(Icons.CHECKBOARD, this.panel.checkerboardColor(), dx, dy, dw, dh);

            if (item.index < 0 || item.index >= document.imageCount())
            {
                /* A frame pointing past the strip: shown for what it is, saved as it was */
                batcher.text("?", x + (w - font.getWidth("?")) / 2, y + (h - font.getHeight()) / 2, Colors.GRAY);
            }
            else
            {
                renderImage(context, document, item.index, dx, dy, dw, dh);
            }

            CellPainter.dim(context, x, y, w, h, state);

            if (state.hover && !state.dragged && CellActionBar.fits(w))
            {
                CellActionBar.render(context, x, y, w, ACTIONS, state.hoveredAction);
            }

            /* The number in one corner; a duration of the frame's own in the other */
            batcher.textShadow(String.valueOf(position + 1), x + 3, y + 3, Colors.LIGHTEST_GRAY);

            if (item.time > 0 && item.time != animation.frametime)
            {
                String time = "×" + item.time;

                batcher.textShadow(time, x + w - 3 - font.getWidth(time), y + h - 3 - font.getHeight(), Colors.LIGHTEST_GRAY);
            }

            /* A chain on the frames that show one image between them: painting one paints them all */
            if (this.isShared(item))
            {
                batcher.icon(Icons.LINK, Colors.LIGHTER_GRAY, x + 2, y + h - 18);
            }
        }

        CellPainter.frames(context, x, y, w, h, state);

        batcher.unclip(context);
    }

    /** Whether another frame shows the same image as this one. */
    private boolean isShared(TextureAnimation.Frame item)
    {
        for (TextureAnimation.Frame frame : this.visible())
        {
            if (frame != item && frame.index == item.index)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * One image of the strip, fitted into a rectangle: the image's band of every visible layer
     * (shifted by its move offset), scaled — only the band, since a layer's texture wraps and a
     * quad past its edge would show the neighbouring images.
     */
    public static void renderImage(UIContext context, Document document, int image, float x, float y, float w, float h)
    {
        int fw = Math.max(1, document.frameWidth());
        int fh = Math.max(1, document.frameHeight());
        int ix = 0;
        int iy = image * fh;
        float sx = w / fw;
        float sy = h / fh;

        for (TextureLayer layer : document.layers)
        {
            if (!layer.visible)
            {
                continue;
            }

            int x1 = Math.max(ix, layer.offsetX);
            int y1 = Math.max(iy, layer.offsetY);
            int x2 = Math.min(ix + fw, layer.offsetX + layer.width());
            int y2 = Math.min(iy + fh, layer.offsetY + layer.height());

            if (x2 <= x1 || y2 <= y1)
            {
                continue;
            }

            layer.draw(context.batcher, Colors.setA(Colors.WHITE, layer.opacity),
                x + (x1 - ix) * sx, y + (y1 - iy) * sy, (x2 - x1) * sx, (y2 - y1) * sy,
                x1 - layer.offsetX, y1 - layer.offsetY, x2 - layer.offsetX, y2 - layer.offsetY);
        }
    }
}
