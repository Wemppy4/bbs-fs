package mchorse.bbs_mod.ui.framework.elements.input.list;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.items.UIItemGrid;
import mchorse.bbs_mod.ui.utils.GridLayout;
import mchorse.bbs_mod.ui.utils.ScrollZoomAnchor;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.utils.cells.CellPainter;
import mchorse.bbs_mod.ui.utils.cells.CellState;
import java.util.List;

/** A second view of a list: the same filtered data, selection and click callback. */
public class UIListGrid<T> extends UIItemGrid<T>
{
    private final UIList<T> source;

    public UIListGrid(UIList<T> source)
    {
        super(values -> { if (!values.isEmpty() && source.callback != null) source.callback.accept(values); }, null,
            source.selection, source.drag, new GridLayout(0, 4, 4, 4, 4, 1F));
        this.source = source;
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

    public void revealSelection()
    {
        this.relayout();
        int index = this.visible().indexOf(this.source.getCurrentFirst());
        if (index >= 0) this.scrollIntoView(index);
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
        if (CellPainter.hasCaption(w))
        {
            CellPainter.caption(context, this.caption(item), x, y, w, h, state.hover || state.selected);
        }
        CellPainter.bar(context, x, y, w, h, state);
    }
}
