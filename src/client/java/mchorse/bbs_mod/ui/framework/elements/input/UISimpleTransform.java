package mchorse.bbs_mod.ui.framework.elements.input;

import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.pose.Transform;

/**
 * A plain numeric {@link Transform} editor — the trackpad rows of {@link UITransform} without the
 * gizmo of {@link UIPropTransform}. Used where there's no 3D handle to drag (e.g. the model editor's
 * attachment slots): edits write straight into the bound transform and notify {@link #onChange}.
 *
 * <p>The transform is edited in place, so each edit is bracketed in the backing {@link ValueTransform}'s
 * notify (via {@link BaseValue#edit}) — that's what lets undo/redo catch it. Binding a raw {@link Transform}
 * without a value still works (it just won't be undoable).
 */
public class UISimpleTransform extends UITransform
{
    private ValueTransform value;
    private Transform transform;
    private Runnable onChange;

    public UISimpleTransform(Runnable onChange)
    {
        super();

        this.onChange = onChange;
    }

    /** Bind the value whose transform this editor mutates; edits go through its notify so they're undoable. */
    public void setValue(ValueTransform value)
    {
        this.value = value;

        this.setTransform(value == null ? null : value.get());
    }

    public void setTransform(Transform transform)
    {
        this.transform = transform;

        if (transform == null)
        {
            this.fillT(0, 0, 0);
            this.fillS(1, 1, 1);
            this.fillR(0, 0, 0);
            this.fillR2(0, 0, 0);

            return;
        }

        this.fillT(transform.translate.x, transform.translate.y, transform.translate.z);
        this.fillS(transform.scale.x, transform.scale.y, transform.scale.z);
        this.fillR(MathUtils.toDeg(transform.rotate.x), MathUtils.toDeg(transform.rotate.y), MathUtils.toDeg(transform.rotate.z));
        this.fillR2(MathUtils.toDeg(transform.rotate2.x), MathUtils.toDeg(transform.rotate2.y), MathUtils.toDeg(transform.rotate2.z));
    }

    private void changed()
    {
        if (this.onChange != null)
        {
            this.onChange.run();
        }
    }

    /**
     * Mutate the bound transform in place, bracketing it in the value's notify when one is bound so the
     * undo handler caches the value before the change and diffs it after.
     */
    private void edit(Runnable mutation)
    {
        if (this.value != null)
        {
            BaseValue.edit(this.value, (v) -> mutation.run());
        }
        else
        {
            mutation.run();
        }

        this.changed();
    }

    @Override
    public void setT(Axis axis, double x, double y, double z)
    {
        if (this.transform == null)
        {
            return;
        }

        this.edit(() -> this.transform.translate.set((float) x, (float) y, (float) z));
    }

    @Override
    public void setS(Axis axis, double x, double y, double z)
    {
        if (this.transform == null)
        {
            return;
        }

        this.edit(() -> this.transform.scale.set((float) x, (float) y, (float) z));
    }

    @Override
    public void setR(Axis axis, double x, double y, double z)
    {
        if (this.transform == null)
        {
            return;
        }

        this.edit(() -> this.transform.rotate.set(MathUtils.toRad((float) x), MathUtils.toRad((float) y), MathUtils.toRad((float) z)));
    }

    @Override
    public void setR2(Axis axis, double x, double y, double z)
    {
        if (this.transform == null)
        {
            return;
        }

        this.edit(() -> this.transform.rotate2.set(MathUtils.toRad((float) x), MathUtils.toRad((float) y), MathUtils.toRad((float) z)));
    }
}
