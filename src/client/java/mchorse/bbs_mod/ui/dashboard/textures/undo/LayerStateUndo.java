package mchorse.bbs_mod.ui.dashboard.textures.undo;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.dashboard.textures.data.Document;
import mchorse.bbs_mod.utils.undo.IUndo;

/**
 * Undo entry for layer-management operations (add / remove / duplicate / reorder / rename /
 * visibility / opacity / move) that change the document's structure rather than a single layer's
 * pixels.
 *
 * <p>It stores a {@link Document#snapshot() snapshot} of the document (layers and animation) before
 * and after the change and restores it via {@link Document#restore(MapType)}, which rebuilds the
 * layer stack from scratch (recreating GPU textures) &mdash; so no detached layers are held and there
 * is nothing to leak when the entry is dropped from history.</p>
 *
 * <p>When given a non-null {@code mergeTag} consecutive entries with the same tag merge, collapsing a
 * continuous edit (e.g. dragging the opacity slider) into a single undo step.</p>
 */
public class LayerStateUndo implements IUndo<Document>
{
    private final MapType before;
    private MapType after;
    private final String mergeTag;
    private boolean merging = true;

    public LayerStateUndo(MapType before, MapType after)
    {
        this(before, after, null);
    }

    public LayerStateUndo(MapType before, MapType after, String mergeTag)
    {
        this.before = before;
        this.after = after;
        this.mergeTag = mergeTag;
    }

    @Override
    public IUndo<Document> noMerging()
    {
        this.merging = false;

        return this;
    }

    @Override
    public boolean isMergeable(IUndo<Document> undo)
    {
        return this.merging
            && this.mergeTag != null
            && undo instanceof LayerStateUndo other
            && this.mergeTag.equals(other.mergeTag);
    }

    @Override
    public void merge(IUndo<Document> undo)
    {
        if (undo instanceof LayerStateUndo other)
        {
            this.after = other.after;
        }
    }

    @Override
    public void undo(Document context)
    {
        context.restore(this.before);
    }

    @Override
    public void redo(Document context)
    {
        context.restore(this.after);
    }
}
