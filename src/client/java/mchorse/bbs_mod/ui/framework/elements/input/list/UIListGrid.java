package mchorse.bbs_mod.ui.framework.elements.input.list;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.items.UIItemGrid;
import mchorse.bbs_mod.ui.utils.GridLayout;
import mchorse.bbs_mod.ui.utils.ScrollZoomAnchor;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.utils.cells.CellPainter;
import mchorse.bbs_mod.ui.utils.cells.CellState;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import java.util.List;

/** A second view of a list: the same filtered data, selection and click callback. */
public class UIListGrid<T> extends UIItemGrid<T>
{
    private final UIList<T> source;

    /* The pick has to be brought into view as soon as the cells know where they are */
    private boolean reveal;

    public UIListGrid(UIList<T> source)
    {
        super(values -> { if (!values.isEmpty() && source.callback != null) source.callback.accept(values); }, null,
            source.selection, source.drag, new GridLayout(0, 4, 4, 4, 4, 1F));
        this.source = source;
        this.source.onReveal = this::revealSelection;
        this.setCellSize(72);
        this.context(() -> source.createContextMenu(this.getContext()));
    }

    @Override
    protected List<T> visible()
    {
        return this.source.visible();
    }

    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        if (Window.isCtrlPressed() && context.mouseWheel != 0 && this.area.isInside(context))
        {
            int size = this.layout.zoom(this.getCellSize(), context.mouseWheel > 0, 8, 40, 160);

            if (size != this.getCellSize())
            {
                ScrollZoomAnchor.keep(this.scroll, context.mouseY - this.area.y, y ->
                {
                    int index = this.indexAt(this.contentX(context), y);

                    return index < 0 ? null : Integer.valueOf(index);
                }, index -> new ScrollZoomAnchor.Placement(this.layout.getY(index), this.layout.getCellHeight()),
                    () -> this.setCellSize(size));
            }

            return true;
        }

        return super.subMouseScrolled(context);
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 1 && this.area.isInside(context))
        {
            int index = this.indexAt(this.contentX(context), this.contentY(context));
            if (index >= 0) this.source.setCurrent(this.visible().get(index));
        }
        return super.subMouseClicked(context);
    }

    @Override
    protected String caption(T item)
    {
        return this.source.elementToString(this.getContext(), this.source.getList().indexOf(item), item);
    }

    @Override
    protected boolean showsCaption(UIContext context, T item, int width)
    {
        return CellPainter.hasCaption(width) && CellPainter.captionFits(context, this.caption(item), width);
    }

    /**
     * Bring the pick into view. Waits for the next layout or frame, because a picker sets its
     * value before it's opened, when the grid has no size to work out the cells from.
     */
    public void revealSelection()
    {
        this.reveal = true;
    }

    /** Index of the pick among the cells if it's waiting to be revealed, otherwise -1. */
    private int takeReveal()
    {
        if (!this.reveal)
        {
            return -1;
        }

        this.reveal = false;

        return this.visible().indexOf(this.source.getCurrentFirst());
    }

    /** Laid out with the pick still waiting means a picker has just opened: straight to it, like the list. */
    @Override
    public void resize()
    {
        super.resize();

        int index = this.takeReveal();

        if (index >= 0)
        {
            this.scroll.setScroll(this.layout.getY(index) - this.layout.getGap());
        }
    }

    /** Already on screen, the pick moved by the keyboard or a search: only as far as it takes. */
    @Override
    public void render(UIContext context)
    {
        if (this.reveal)
        {
            this.relayout();

            int index = this.takeReveal();

            if (index >= 0)
            {
                this.scrollIntoView(index);
            }
        }

        super.render(context);
    }

    @Override
    protected void renderCell(UIContext context, T item, int x, int y, int w, int h, CellState state)
    {
        CellPainter.marks(context, x, y, w, h, state);
        if (this.source.preview != null)
        {
            int size = Math.max(1, Math.min(w - 8, h - (CellPainter.hasCaption(w) ? CellPainter.CAPTION_HEIGHT : 0) - 8));
            context.batcher.clip(x, y, w, h, context);
            try
            {
                this.source.preview.render(context, item, x + (w - size) / 2, y + 4, size);
            }
            finally
            {
                context.batcher.unclip(context);
            }
        }
        if (this.source.isDone(item))
        {
            context.batcher.icon(Icons.CHECKMARK, Colors.A100 | Colors.POSITIVE, x + w - 2, y + 2, 1F, 0F);
        }
        if (CellPainter.hasCaption(w))
        {
            CellPainter.caption(context, this.caption(item), x, y, w, h, state.hover || state.selected);
        }
        CellPainter.bar(context, x, y, w, h, state);
    }
}
