package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3d;

import java.util.List;
import java.util.function.Consumer;

public abstract class UIKeyframePropTransform extends UIPropTransform
{
    protected abstract void applyToSelection(Consumer<Transform> consumer);

    protected abstract void applyDuringRecording(int tick, Consumer<Transform> consumer);

    protected Transform getRecordedTransform(int tick)
    {
        return null;
    }

    /**
     * Resolves the film panel from the menu root rather than walking up the parent chain, so this
     * transform works even when it lives outside the film panel (e.g. the animation state editor,
     * where there is no film panel and recording simply doesn't apply). Mirrors the lookup in
     * {@link UIAnchorKeyframeFactory}.
     */
    protected UIFilmPanel getPanel()
    {
        UIContext context = this.getContext();

        if (context == null)
        {
            return null;
        }

        List<UIFilmPanel> panels = context.menu.main.getChildren(UIFilmPanel.class);

        return panels.isEmpty() ? null : panels.get(0);
    }

    protected boolean isTransformRecording()
    {
        UIFilmPanel panel = this.getPanel();

        return panel != null && panel.getController().isTransformRecording();
    }

    protected int getRecordingTick()
    {
        UIFilmPanel panel = this.getPanel();

        return panel == null ? 0 : panel.getCursor();
    }

    protected Transform getTargetTransform()
    {
        if (this.isTransformRecording())
        {
            return this.getRecordedTransform(this.getRecordingTick());
        }

        return this.getTransform();
    }

    protected void applyToTarget(Consumer<Transform> consumer)
    {
        if (this.isTransformRecording())
        {
            this.applyDuringRecording(this.getRecordingTick(), consumer);
        }
        else
        {
            this.applyToSelection(consumer);
        }
    }

    protected void syncTargetTransform()
    {
        Transform transform = this.getTargetTransform();

        if (transform != null)
        {
            this.setTransform(transform);
        }
    }

    @Override
    public void pasteTranslation(Vector3d translation)
    {
        this.applyToTarget((transform) -> transform.translate.set(translation));
        this.syncTargetTransform();
    }

    @Override
    public void pasteScale(Vector3d scale)
    {
        this.applyToTarget((transform) -> transform.scale.set(scale));
        this.syncTargetTransform();
    }

    @Override
    public void pasteRotation(Vector3d rotation)
    {
        this.applyToTarget((transform) -> transform.rotate.set(Vectors.toRad(rotation)));
        this.syncTargetTransform();
    }

    @Override
    public void pasteRotation2(Vector3d rotation)
    {
        this.applyToTarget((transform) -> transform.rotate2.set(Vectors.toRad(rotation)));
        this.syncTargetTransform();
    }

    @Override
    public void setT(Axis axis, double x, double y, double z)
    {
        Transform transform = this.getTargetTransform();

        if (transform == null)
        {
            return;
        }

        float dx = (float) (x - transform.translate.x);
        float dy = (float) (y - transform.translate.y);
        float dz = (float) (z - transform.translate.z);

        this.applyToTarget((poseT) ->
        {
            poseT.translate.x += dx;
            poseT.translate.y += dy;
            poseT.translate.z += dz;
        });

        this.syncTargetTransform();
    }

    @Override
    public void setS(Axis axis, double x, double y, double z)
    {
        Transform transform = this.getTargetTransform();

        if (transform == null)
        {
            return;
        }

        float dx = (float) (x - transform.scale.x);
        float dy = (float) (y - transform.scale.y);
        float dz = (float) (z - transform.scale.z);

        this.applyToTarget((poseT) ->
        {
            poseT.scale.x += dx;
            poseT.scale.y += dy;
            poseT.scale.z += dz;
        });

        this.syncTargetTransform();
    }

    @Override
    public void setR(Axis axis, double x, double y, double z)
    {
        Transform transform = this.getTargetTransform();

        if (transform == null)
        {
            return;
        }

        float dx = MathUtils.toRad((float) x) - transform.rotate.x;
        float dy = MathUtils.toRad((float) y) - transform.rotate.y;
        float dz = MathUtils.toRad((float) z) - transform.rotate.z;

        this.applyToTarget((poseT) ->
        {
            poseT.rotate.x += dx;
            poseT.rotate.y += dy;
            poseT.rotate.z += dz;
        });

        this.syncTargetTransform();
    }

    @Override
    public void setR2(Axis axis, double x, double y, double z)
    {
        Transform transform = this.getTargetTransform();

        if (transform == null)
        {
            return;
        }

        float dx = MathUtils.toRad((float) x) - transform.rotate2.x;
        float dy = MathUtils.toRad((float) y) - transform.rotate2.y;
        float dz = MathUtils.toRad((float) z) - transform.rotate2.z;

        this.applyToTarget((poseT) ->
        {
            poseT.rotate2.x += dx;
            poseT.rotate2.y += dy;
            poseT.rotate2.z += dz;
        });

        this.syncTargetTransform();
    }
}
