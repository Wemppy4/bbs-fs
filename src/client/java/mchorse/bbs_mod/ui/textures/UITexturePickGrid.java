package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.items.UIItemGrid;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.GridLayout;
import mchorse.bbs_mod.ui.utils.cells.CellState;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A folder's textures as cells, for picking one of them — what the browser's grid is, minus
 * everything that browsing needs: no folders to enter, no dragging, no quick actions, one
 * pick at a time. The save dialog shows the textures already in the chosen folder with it,
 * so overwriting one is a matter of pointing at its picture.
 *
 * <p>The cells are painted by the {@link TextureCellRenderer same renderer} as the browser's,
 * so a texture looks the same wherever it is offered — the checkerboard, the fitted picture,
 * the name strip, and the fade over what lives inside the mod.</p>
 */
public class UITexturePickGrid extends UIItemGrid<TextureEntry>
{
    public static final int CELL = 56;

    private final List<TextureEntry> entries = new ArrayList<>();

    /** Which cell reads as chosen; the dialog answers with whatever its name box says. */
    private Supplier<Link> current;

    private final Consumer<TextureEntry> opened;

    /**
     * @param callback the cell that was picked, or null when the pick was dropped
     * @param opened   the cell that was opened (double click, Enter)
     */
    public UITexturePickGrid(Consumer<TextureEntry> callback, Consumer<TextureEntry> opened)
    {
        /* Links are values: the same link is the same pick, whatever the caption says */
        super((list) -> callback.accept(list.isEmpty() ? null : list.get(0)), (a, b) -> a.link().equals(b.link()), new GridLayout(0, 6, 3, 6, 6, 1F));

        this.opened = opened;

        this.setCellSize(CELL);
    }

    public UITexturePickGrid current(Supplier<Link> current)
    {
        this.current = current;

        return this;
    }

    public void setEntries(List<TextureEntry> entries)
    {
        this.entries.clear();
        this.entries.addAll(entries);

        this.selection.clear();
        this.cursor = -1;
        this.scroll.scrollTo(0);
        this.relayout();
    }

    @Override
    protected List<TextureEntry> visible()
    {
        return this.entries;
    }

    @Override
    protected String caption(TextureEntry item)
    {
        return item.caption();
    }

    @Override
    protected boolean onOpen(TextureEntry item)
    {
        this.opened.accept(item);

        return true;
    }

    @Override
    protected void renderCell(UIContext context, TextureEntry item, int x, int y, int w, int h, CellState state)
    {
        Link link = this.current == null ? null : this.current.get();

        state.selected = link != null && link.equals(item.link());

        TextureCellRenderer.render(context, item, x, y, w, h, state, this.actions(item));
    }

    @Override
    protected void renderContent(UIContext context)
    {
        super.renderContent(context);

        if (this.entries.isEmpty())
        {
            FontRenderer font = context.batcher.getFont();
            String label = UIKeys.TEXTURE_NO_DATA.get();

            context.batcher.text(label, this.area.mx(font.getWidth(label)), this.area.my() - 4, Colors.GRAY);
        }
    }
}
