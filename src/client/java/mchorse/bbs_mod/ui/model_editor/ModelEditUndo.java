package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.undo.IUndo;

/**
 * One edit of the model itself on the panel's undo stack, next to the config's value changes, so
 * Ctrl+Z walks back through both in the order they were made. The edit is kept as the model's data
 * before and after it — its groups, whole: a model is small, and a snapshot is right by
 * construction for every kind of edit — with the config's too when the edit reached into it (a
 * rename), and the rename itself, since the animations' bone keys are moved by name rather than
 * snapshotted.
 *
 * <p>Consecutive edits with the same key merge into one — the steps of a gizmo drag — until the
 * gesture ends and the stack is told to keep the next one apart.</p>
 */
public class ModelEditUndo implements IUndo<ValueGroup>
{
    private final UIModelEditorPanel panel;
    private final String label;
    private final String key;
    private final MapType modelBefore;
    private MapType modelAfter;
    private final MapType configBefore;
    private MapType configAfter;
    private final String renameFrom;
    private final String renameTo;

    private boolean mergeable = true;

    /** An edit of the model alone; {@code key} null for one that never merges. */
    public ModelEditUndo(UIModelEditorPanel panel, String label, String key, MapType modelBefore, MapType modelAfter)
    {
        this(panel, label, key, modelBefore, modelAfter, null, null, null, null);
    }

    public ModelEditUndo(UIModelEditorPanel panel, String label, String key, MapType modelBefore, MapType modelAfter, MapType configBefore, MapType configAfter, String renameFrom, String renameTo)
    {
        this.panel = panel;
        this.label = label;
        this.key = key;
        this.modelBefore = modelBefore;
        this.modelAfter = modelAfter;
        this.configBefore = configBefore;
        this.configAfter = configAfter;
        this.renameFrom = renameFrom;
        this.renameTo = renameTo;
    }

    /** What the history lists. */
    @Override
    public String toString()
    {
        return this.label;
    }

    @Override
    public IUndo<ValueGroup> noMerging()
    {
        this.mergeable = false;

        return this;
    }

    @Override
    public boolean isMergeable(IUndo<ValueGroup> undo)
    {
        return this.mergeable && this.key != null && undo instanceof ModelEditUndo other && this.key.equals(other.key);
    }

    /** The later edit's outcome replaces this one's; where it started stays. */
    @Override
    public void merge(IUndo<ValueGroup> undo)
    {
        ModelEditUndo other = (ModelEditUndo) undo;

        this.modelAfter = other.modelAfter;

        if (other.configAfter != null)
        {
            this.configAfter = other.configAfter;
        }
    }

    @Override
    public void undo(ValueGroup context)
    {
        this.panel.restoreModel(this.modelBefore, this.configBefore);

        if (this.renameFrom != null)
        {
            this.panel.renameAnimations(this.renameTo, this.renameFrom);
        }
    }

    @Override
    public void redo(ValueGroup context)
    {
        this.panel.restoreModel(this.modelAfter, this.configAfter);

        if (this.renameFrom != null)
        {
            this.panel.renameAnimations(this.renameFrom, this.renameTo);
        }
    }
}
