package mchorse.bbs_mod.ui.forms.editors.panels.widgets;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.renderers.BoneHierarchy;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

/** Pose editor bound to a form's own {@link ValuePose}, including undo notifications. */
public class UIPoseFormEditor extends UIPoseEditor
{
    private ValuePose valuePose;

    public UIPoseFormEditor()
    {
        this.groups.list.h(UIStringList.DEFAULT_HEIGHT * 17);
    }

    public void setValuePose(ValuePose valuePose)
    {
        this.valuePose = valuePose;
    }

    public void migratePose(BoneHierarchy hierarchy)
    {
        Pose original = this.valuePose.getOriginalValue();
        Pose runtime = this.valuePose.getRuntimeValue();

        if (hierarchy.needsMigration(original))
        {
            this.valuePose.preNotify(IValueListener.FLAG_UNMERGEABLE);
            hierarchy.migratePose(original);
            this.valuePose.postNotify(IValueListener.FLAG_UNMERGEABLE);
        }

        if (hierarchy.needsMigration(runtime))
        {
            hierarchy.migratePose(runtime);
        }
    }

    @Override
    protected UIPropTransform createTransformEditor()
    {
        return super.createTransformEditor().callbacks(() -> this.valuePose);
    }

    @Override
    protected void pastePose(MapType data)
    {
        this.valuePose.preNotify(IValueListener.FLAG_UNMERGEABLE);
        super.pastePose(data);
        this.valuePose.postNotify(IValueListener.FLAG_UNMERGEABLE);
    }

    @Override
    protected void flipPose()
    {
        this.valuePose.preNotify(IValueListener.FLAG_UNMERGEABLE);
        super.flipPose();
        this.valuePose.postNotify(IValueListener.FLAG_UNMERGEABLE);
    }

    @Override
    protected void setFix(PoseTransform transform, float value)
    {
        this.valuePose.preNotify(IValueListener.FLAG_UNMERGEABLE);
        super.setFix(transform, value);
        this.valuePose.postNotify(IValueListener.FLAG_UNMERGEABLE);
    }

    @Override
    protected void setColor(PoseTransform transform, int value)
    {
        this.valuePose.preNotify();
        super.setColor(transform, value);
        this.valuePose.postNotify();
    }

    @Override
    protected void setLighting(PoseTransform transform, boolean value)
    {
        this.valuePose.preNotify(IValueListener.FLAG_UNMERGEABLE);
        super.setLighting(transform, value);
        this.valuePose.postNotify(IValueListener.FLAG_UNMERGEABLE);
    }
}
