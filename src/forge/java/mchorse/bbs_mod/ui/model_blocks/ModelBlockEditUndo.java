package mchorse.bbs_mod.ui.model_blocks;

import mchorse.bbs_mod.forge.ModelTileEntity;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.undo.IUndo;

/** A block property edit; consecutive updates of one gesture share a history entry. */
final class ModelBlockEditUndo implements IUndo<UIModelBlockPanel>
{
    private final ModelTileEntity block;
    private final MapType before;
    private MapType after;
    private final String key;
    private boolean mergeable = true;

    ModelBlockEditUndo(ModelTileEntity block, MapType before, MapType after, String key)
    {
        this.block = block;
        this.before = (MapType) before.copy();
        this.after = (MapType) after.copy();
        this.key = key;
    }

    @Override
    public IUndo<UIModelBlockPanel> noMerging()
    {
        this.mergeable = false;
        return this;
    }

    @Override
    public boolean isMergeable(IUndo<UIModelBlockPanel> undo)
    {
        return this.mergeable && this.key != null && undo instanceof ModelBlockEditUndo other
            && this.block == other.block && this.key.equals(other.key);
    }

    @Override
    public void merge(IUndo<UIModelBlockPanel> undo)
    {
        this.after = ((ModelBlockEditUndo) undo).after;
    }

    @Override
    public void undo(UIModelBlockPanel panel)
    {
        panel.restoreBlock(this.block, this.before);
    }

    @Override
    public void redo(UIModelBlockPanel panel)
    {
        panel.restoreBlock(this.block, this.after);
    }
}
