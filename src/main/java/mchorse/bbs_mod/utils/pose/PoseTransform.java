package mchorse.bbs_mod.utils.pose;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.colors.OverlayBlend;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Lerps;

public class PoseTransform extends Transform
{
    private static PoseTransform DEFAULT = new PoseTransform();

    public float fix;
    public final Color color = new Color().set(Colors.WHITE);
    /* Color overlay for the bone (RGB = color, A = strength); neutral at zero strength. */
    public final Color overlay = new Color(1F, 1F, 1F, 0F);
    public float lighting;

    @Override
    public void identity()
    {
        super.identity();

        this.fix = 0F;
        this.color.set(Colors.WHITE);
        this.overlay.set(1F, 1F, 1F, 0F);
        this.lighting = 0F;
    }

    @Override
    public void lerp(Transform transform, float a)
    {
        if (transform instanceof PoseTransform pose)
        {
            this.fix = Lerps.lerp(this.fix, pose.fix, a);

            this.color.r = Lerps.lerp(this.color.r, pose.color.r, a);
            this.color.g = Lerps.lerp(this.color.g, pose.color.g, a);
            this.color.b = Lerps.lerp(this.color.b, pose.color.b, a);
            this.color.a = Lerps.lerp(this.color.a, pose.color.a, a);

            this.overlay.r = Lerps.lerp(this.overlay.r, pose.overlay.r, a);
            this.overlay.g = Lerps.lerp(this.overlay.g, pose.overlay.g, a);
            this.overlay.b = Lerps.lerp(this.overlay.b, pose.overlay.b, a);
            this.overlay.a = Lerps.lerp(this.overlay.a, pose.overlay.a, a);

            this.lighting = Lerps.lerp(this.lighting, pose.lighting, a);
        }

        super.lerp(transform, a);
    }

    @Override
    public void lerp(Transform preA, Transform a, Transform b, Transform postB, IInterp interp, float x)
    {
        super.lerp(preA, a, b, postB, interp, x);

        if (preA instanceof PoseTransform preA1)
        {
            PoseTransform a1 = (PoseTransform) a;
            PoseTransform b1 = (PoseTransform) b;
            PoseTransform postB1 = (PoseTransform) postB;

            this.fix = (float) interp.interpolate(IInterp.context.set(preA1.fix, a1.fix, b1.fix, postB1.fix, x));

            this.color.set(
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preA1.color.r, a1.color.r, b1.color.r, postB1.color.r, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preA1.color.g, a1.color.g, b1.color.g, postB1.color.g, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preA1.color.b, a1.color.b, b1.color.b, postB1.color.b, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preA1.color.a, a1.color.a, b1.color.a, postB1.color.a, x)), 0F, 1F)
            );

            this.overlay.set(
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preA1.overlay.r, a1.overlay.r, b1.overlay.r, postB1.overlay.r, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preA1.overlay.g, a1.overlay.g, b1.overlay.g, postB1.overlay.g, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preA1.overlay.b, a1.overlay.b, b1.overlay.b, postB1.overlay.b, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preA1.overlay.a, a1.overlay.a, b1.overlay.a, postB1.overlay.a, x)), 0F, 1F)
            );

            this.lighting = (float) interp.interpolate(IInterp.context.set(preA1.lighting, a1.lighting, b1.lighting, postB1.lighting, x));
        }
    }

    @Override
    public void autoLerp(Transform preA, Transform a, Transform b, Transform postB, float pt, float at, float bt, float qt, boolean clamped, float x)
    {
        super.autoLerp(preA, a, b, postB, pt, at, bt, qt, clamped, x);

        if (preA instanceof PoseTransform preA1)
        {
            PoseTransform a1 = (PoseTransform) a;
            PoseTransform b1 = (PoseTransform) b;
            PoseTransform postB1 = (PoseTransform) postB;

            this.fix = (float) AutoBezier.get(preA1.fix, a1.fix, b1.fix, postB1.fix, pt, at, bt, qt, clamped, x);

            this.color.set(
                (float) MathUtils.clamp(AutoBezier.get(preA1.color.r, a1.color.r, b1.color.r, postB1.color.r, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preA1.color.g, a1.color.g, b1.color.g, postB1.color.g, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preA1.color.b, a1.color.b, b1.color.b, postB1.color.b, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preA1.color.a, a1.color.a, b1.color.a, postB1.color.a, pt, at, bt, qt, clamped, x), 0F, 1F)
            );

            this.overlay.set(
                (float) MathUtils.clamp(AutoBezier.get(preA1.overlay.r, a1.overlay.r, b1.overlay.r, postB1.overlay.r, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preA1.overlay.g, a1.overlay.g, b1.overlay.g, postB1.overlay.g, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preA1.overlay.b, a1.overlay.b, b1.overlay.b, postB1.overlay.b, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preA1.overlay.a, a1.overlay.a, b1.overlay.a, postB1.overlay.a, pt, at, bt, qt, clamped, x), 0F, 1F)
            );

            this.lighting = (float) AutoBezier.get(preA1.lighting, a1.lighting, b1.lighting, postB1.lighting, pt, at, bt, qt, clamped, x);
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        boolean result = super.equals(obj);

        if (obj instanceof PoseTransform poseTransform)
        {
            result = result && this.fix == poseTransform.fix;
            result = result && this.color.equals(poseTransform.color);
            result = result && this.overlay.equals(poseTransform.overlay);
            result = result && this.lighting == poseTransform.lighting;
        }

        return result;
    }

    @Override
    public int contentHash()
    {
        int hash = super.contentHash();

        hash = 31 * hash + Float.floatToIntBits(this.fix);
        hash = 31 * hash + this.color.getARGBColor();
        hash = 31 * hash + this.overlay.getARGBColor();
        hash = 31 * hash + Float.floatToIntBits(this.lighting);

        return hash;
    }

    @Override
    public Transform copy()
    {
        PoseTransform transform = new PoseTransform();

        transform.copy(this);

        return transform;
    }

    @Override
    public void copy(Transform transform)
    {
        if (transform instanceof PoseTransform poseTransform)
        {
            this.fix = poseTransform.fix;
            this.color.copy(poseTransform.color);
            this.overlay.copy(poseTransform.overlay);
            this.lighting = poseTransform.lighting;
        }

        super.copy(transform);
    }

    @Override
    public void add(Transform transform)
    {
        super.add(transform);

        if (transform instanceof PoseTransform pose)
        {
            this.fix += pose.fix;
            this.color.mul(pose.color);
            OverlayBlend.stack(this.overlay, pose.overlay);
            this.lighting += pose.lighting;
        }
    }

    @Override
    public void toData(MapType data)
    {
        super.toData(data);

        data.putFloat("fix", this.fix);
        data.putInt("color", this.color.getARGBColor());
        data.putInt("overlay", this.overlay.getARGBColor());
        data.putFloat("lighting", this.lighting);
    }

    @Override
    public void fromData(MapType data)
    {
        super.fromData(data);

        this.fix = data.getFloat("fix");
        this.color.set(data.getInt("color", Colors.WHITE));
        this.overlay.set(data.getInt("overlay", 0x00ffffff));
        this.lighting = data.getFloat("lighting");
    }

    @Override
    public boolean isDefault()
    {
        return this.equals(DEFAULT);
    }
}