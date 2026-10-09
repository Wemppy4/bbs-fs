package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.framework.elements.input.UIDeltaPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.function.Consumer;

/** Delta editing with one undo snapshot for the whole value gesture. */
public abstract class UIKeyframePropTransform extends UIDeltaPropTransform
{
    private boolean applying;
    protected abstract UIKeyframes getKeyframes();

    @Override public boolean isUserEditing() { return this.applying || super.isUserEditing(); }

    @Override
    protected void applyToTarget(Consumer<Transform> edit)
    {
        this.applying = true;
        try
        {
            this.applyToSelection(edit);
            /* The fields show a copy of the key's value, never a stored key: move it with the edit. */
            if (this.getTransform() != null) edit.accept(this.getTransform());
        }
        finally { this.applying = false; }
    }

    @Override public void beginGesture() { this.getKeyframes().beginValueGesture(); }
    @Override public void endGesture() { this.getKeyframes().endValueGesture(); }
    @Override public boolean cancelGesture()
    {
        this.getKeyframes().cancelValueGesture();
        this.syncTargetTransform();
        return true;
    }
}
