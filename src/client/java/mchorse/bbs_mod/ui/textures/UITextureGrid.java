package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.items.UIItemGrid;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.GridLayout;
import mchorse.bbs_mod.ui.utils.ScrollZoomAnchor;
import mchorse.bbs_mod.ui.utils.cells.CellAction;
import mchorse.bbs_mod.ui.utils.cells.CellState;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The grid of a texture browser: the entries of the current folder (or the search hits) as
 * square cells, zoomed with Ctrl + wheel. The picking, the band, the drag and the keyboard
 * come from {@link UIItemGrid}; what a choice means — entering a folder, the picker's current
 * texture, a drop into a folder — is handed to the {@link UITextureBrowser browser}.
 */
public class UITextureGrid extends UIItemGrid<TextureEntry>
{
    public static final int ZOOM_STEP = 8;
    public static final int MIN_CELL = 40;
    public static final int MAX_CELL = 200;

    private final UITextureBrowser browser;

    /* A folder pressed but not yet released: a release without a drag enters it */
    private Link pendingFolder;

    /* The entry a Shift-press landed on: a band that goes nowhere extends the pick to it */
    private TextureEntry marqueeEntry;

    public UITextureGrid(UITextureBrowser browser)
    {
        /* Links are values: the same link is the same pick, whatever the caption says */
        super(null, (a, b) -> a.link().equals(b.link()), new GridLayout(0, 6, 3, 6, 6, 1F));

        this.browser = browser;

        this.multi();
        this.scroll.cancelScrolling();
        this.setCellSize(BBSSettings.textureCellSize.get());
    }

    public void scrollTo(int index)
    {
        this.scrollIntoView(index);
    }

    /* Geometry */

    @Override
    protected List<TextureEntry> visible()
    {
        return this.browser.getEntries();
    }

    /* Hooks */

    @Override
    protected String caption(TextureEntry item)
    {
        return item.caption();
    }

    /** Zoomed out past the name strip (or with a name longer than it), the cell says its name by the cursor. */
    @Override
    protected boolean showsCaption(UIContext context, TextureEntry item, int cellWidth)
    {
        return TextureCellRenderer.showsWholeName(context, item, cellWidth);
    }

    @Override
    protected CellAction[] actions(TextureEntry item)
    {
        return this.browser.getActions(item);
    }

    @Override
    protected void onAction(TextureEntry item, CellAction action)
    {
        this.browser.runAction(item, action);
    }

    @Override
    protected void hoveredAction(CellAction action, int x, int y)
    {
        /* The browser draws the label after all of its parts, so the info column can't cover it */
        this.browser.setHoveredAction(action, x, y);
    }

    @Override
    protected boolean onOpen(TextureEntry item)
    {
        if (item.folder())
        {
            this.browser.navigate(item.link());
        }
        else
        {
            this.browser.picker.selectCurrent(item.link());
        }

        return true;
    }

    @Override
    protected boolean onDelete(List<TextureEntry> items)
    {
        this.browser.deleteEntries(items);

        return true;
    }

    /**
     * A hovered folder is where the drop goes; anywhere else on the grid it's the folder on
     * show — where Ctrl makes a copy beside the originals. Search hits come from many folders,
     * so between them there is nowhere to drop.
     */
    @Override
    protected Object dropTargetAt(int x, int y)
    {
        int index = this.indexAt(x, y);
        TextureEntry entry = index == -1 ? null : this.visible().get(index);

        if (entry != null && entry.folder() && !this.drag.isDragging(entry))
        {
            return entry.link();
        }

        return this.browser.isSearching() ? null : this.browser.getPath();
    }

    @Override
    protected void reportDropTarget(int x, int y)
    {
        Object target = this.dropTargetAt(x, y);

        /* Nothing is ever put between cells here — the listing is sorted, not ordered by hand */
        if (target != null)
        {
            this.drag.setTarget(target);
        }
    }

    @Override
    protected void onDrop(Object target, List<TextureEntry> items)
    {
        this.browser.drop((Link) target, items);
    }

    /* Input */

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 1 && this.area.isInside(context))
        {
            int index = this.indexAt(this.contentX(context), this.contentY(context));

            this.browser.setContextEntry(index == -1 ? null : this.visible().get(index));
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        if (Window.isCtrlPressed() && context.mouseWheel != 0 && this.area.isInside(context))
        {
            this.zoom(context, context.mouseWheel > 0 ? ZOOM_STEP : -ZOOM_STEP);

            return true;
        }

        return super.subMouseScrolled(context);
    }

    private void zoom(UIContext context, int delta)
    {
        int old = this.getCellSize();
        int size = MathUtils.clamp(old + delta, MIN_CELL, MAX_CELL);

        if (size == old)
        {
            return;
        }

        ScrollZoomAnchor.keep(this.scroll, context.mouseY - this.area.y, (y) ->
        {
            int row = this.layout.getRowAt(y);

            return row < 0 ? null : Integer.valueOf(row);
        }, (row) -> new ScrollZoomAnchor.Placement(this.layout.getRowY(row), this.layout.getCellHeight()), () ->
        {
            BBSSettings.textureCellSize.set(size);
            this.setCellSize(size);
        });
    }

    @Override
    protected boolean pressItem(int index, UIContext context)
    {
        if (Window.isShiftPressed())
        {
            /* Shift + drag stretches a band even from a cell; a Shift-click that goes nowhere still extends the pick */
            this.marqueeEntry = this.visible().get(index);

            return this.pressEmpty(context);
        }

        return super.pressItem(index, context);
    }

    /** A plain press also tells the picker: a file becomes the current one, a folder is entered on release. */
    @Override
    protected void applySelectionOnClick(TextureEntry item, int index)
    {
        super.applySelectionOnClick(item, index);

        if (Window.isCtrlPressed() || Window.isShiftPressed())
        {
            return;
        }

        if (item.folder())
        {
            /* Entered on release, so a press can also begin dragging the folder */
            this.pendingFolder = item.link();
        }
        else
        {
            this.browser.picker.onFileClicked(item.link());
        }
    }

    /** Any cell can be carried: the whole pick when it's one of the picked, itself alone otherwise. */
    @Override
    protected List<TextureEntry> dragPayload(TextureEntry item)
    {
        return this.selection.contains(item) ? new ArrayList<>(this.selection.getItems()) : Collections.singletonList(item);
    }

    @Override
    protected void release()
    {
        Link folder = this.pendingFolder;
        TextureEntry shifted = this.marqueeEntry;
        boolean dragged = this.drag.isActive();
        boolean clicked = this.marquee.isPressed() && !this.marquee.isActive();

        this.pendingFolder = null;
        this.marqueeEntry = null;

        super.release();

        if (shifted != null && clicked)
        {
            this.selection.range(shifted, this.scope(), this.visible());
            this.fireCallback();
        }

        if (folder != null && !dragged)
        {
            this.browser.navigate(folder);
        }
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        int before = this.cursor;
        boolean handled = super.subKeyPressed(context);

        /* The keyboard walked to another cell: that cell is what the browser shows now, the way a click would */
        if (handled && this.cursor != before && this.cursor >= 0 && this.cursor < this.visible().size())
        {
            this.browser.show(this.visible().get(this.cursor));
        }

        return handled;
    }

    /* Rendering */

    @Override
    protected void renderCell(UIContext context, TextureEntry item, int x, int y, int w, int h, CellState state)
    {
        /* The chosen cell is the picker's current texture (or the folder on show), not the last picked */
        state.selected = item.link().equals(this.browser.getCurrent()) || (item.folder() && this.browser.isCurrentFolder(item.link()));
        state.dropTarget = item.folder() && this.drag.isTarget(item.link());

        TextureCellRenderer.render(context, item, x, y, w, h, state, this.actions(item));
    }

    @Override
    protected void renderContent(UIContext context)
    {
        super.renderContent(context);

        if (this.visible().isEmpty())
        {
            FontRenderer font = context.batcher.getFont();
            String label = (this.browser.isSearching() ? UIKeys.TEXTURES_BROWSER_NO_RESULTS : UIKeys.TEXTURE_NO_DATA).get();

            context.batcher.text(label, this.area.mx(font.getWidth(label)), this.area.my() - 4, Colors.GRAY);
        }
    }

    /** The ghost is the browser's: drawn over the folder tree too, which is painted after this grid. */
    @Override
    protected void renderDragGhost(UIContext context)
    {}
}
