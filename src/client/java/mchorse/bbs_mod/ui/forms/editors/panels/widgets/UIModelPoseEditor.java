package mchorse.bbs_mod.ui.forms.editors.panels.widgets;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.pose.PoseTransform;

public class UIModelPoseEditor extends UIPoseEditor
{
    private ValuePose valuePose;

    /* No height of its own: the base editor's ask is the floor and the list expands into whatever
     * the panel has spare. Asking for a tall list here (it used to be 17 rows) would make that
     * floor taller than the panel can give back, so opening the shape keys section below could
     * only push the panel into a scrollbar instead of borrowing from the list. */

    public void setValuePose(ValuePose valuePose)
    {
        this.valuePose = valuePose;
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
    protected void setLighting(PoseTransform transform, float value)
    {
        this.valuePose.preNotify(IValueListener.FLAG_UNMERGEABLE);
        super.setLighting(transform, value);
        this.valuePose.postNotify(IValueListener.FLAG_UNMERGEABLE);
    }

    @Override
    protected void setOverlay(PoseTransform transform, int value)
    {
        this.valuePose.preNotify();
        super.setOverlay(transform, value);
        this.valuePose.postNotify();
    }
}