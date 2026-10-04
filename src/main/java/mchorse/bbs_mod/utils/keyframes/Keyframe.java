package mchorse.bbs_mod.utils.keyframes;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

import java.util.Objects;

public class Keyframe <T> extends BaseValue
{
    private float tick;
    private T value;
    private boolean enabled = true;

    public float lx = 5;
    public float ly;
    public float rx = 5;
    public float ry;

    /** How this keyframe is drawn. Fresh keyframes start from whatever the settings say. */
    private final KeyframeStyle style = BBSSettings.getDefaultKeyframeStyle();

    /**
     * Forced duration that would be used instead of the difference
     * between two keyframes, if not 0
     */
    private float duration;
    /** Displacement of the transition's midpoint from 0.5, in normalized time. */
    private float motionShift;
    private final Interpolation interp = new Interpolation("interp", Interpolations.MAP);

    private final IKeyframeFactory<T> factory;

    public Keyframe(String id, IKeyframeFactory<T> factory, float tick, T value)
    {
        this(id, factory);

        this.tick = tick;
        this.value = value;
    }

    public Keyframe(String id, IKeyframeFactory<T> factory)
    {
        super(id);

        this.factory = factory;

        /* Values that jump rather than travel hold their frame by default, so
         * the author doesn't switch every new keyframe to constant by hand */
        if (factory != null && factory.isStepped())
        {
            this.interp.setInterp(Interpolations.CONST);
        }
    }

    public IKeyframeFactory<T> getFactory()
    {
        return this.factory;
    }

    /**
     * Content hash over everything this keyframe serializes: tick, value, interpolation with its
     * easing arguments, bezier handles. Built by field mixing so per-frame signature checks
     * (the motion path cache key) cost integer math, not a serialization.
     */
    public int contentHash()
    {
        int hash = Float.floatToIntBits(this.tick);

        hash = 31 * hash + Boolean.hashCode(this.enabled);
        hash = 31 * hash + Float.floatToIntBits(this.duration);
        hash = 31 * hash + Float.floatToIntBits(this.motionShift);
        hash = 31 * hash + Float.floatToIntBits(this.lx);
        hash = 31 * hash + Float.floatToIntBits(this.ly);
        hash = 31 * hash + Float.floatToIntBits(this.rx);
        hash = 31 * hash + Float.floatToIntBits(this.ry);
        hash = 31 * hash + System.identityHashCode(this.interp.getInterp());
        hash = 31 * hash + Double.hashCode(this.interp.getV1());
        hash = 31 * hash + Double.hashCode(this.interp.getV2());
        hash = 31 * hash + Double.hashCode(this.interp.getV3());
        hash = 31 * hash + Double.hashCode(this.interp.getV4());
        hash = 31 * hash + (this.value == null || this.factory == null ? 0 : this.factory.contentHash(this.value));

        return hash;
    }

    public boolean isEnabled()
    {
        return this.enabled;
    }

    public void setEnabled(boolean enabled)
    {
        if (this.enabled == enabled) return;

        this.preNotify();
        this.enabled = enabled;
        this.postNotify();
    }

    public float getTick()
    {
        return this.tick;
    }

    public void setTick(float tick)
    {
        this.setTick(tick, false);
    }

    public void setTick(float tick, boolean dirty)
    {
        if (dirty) this.preNotify();

        this.tick = tick;

        if (dirty) this.postNotify();
    }

    public float getDuration()
    {
        return this.duration;
    }

    public void setDuration(float duration)
    {
        this.preNotify();
        this.duration = Math.max(0, duration);
        this.postNotify();
    }

    public T getValue()
    {
        return this.value;
    }

    public float getMotionShift()
    {
        return this.motionShift;
    }

    public void setMotionShift(float shift)
    {
        this.setMotionShift(shift, true);
    }

    public void setMotionShift(float shift, boolean dirty)
    {
        float value = Float.isFinite(shift) ? Math.max(-0.49F, Math.min(0.49F, shift)) : 0F;
        if (value == this.motionShift) return;
        if (dirty) this.preNotify();
        this.motionShift = value;
        if (dirty) this.postNotify();
    }

    public boolean supportsMotionShift()
    {
        /* Model animation keys have no factory: CubicModelAnimator evaluates
         * their numeric Molang expressions directly. */
        return (this.factory == null || !this.factory.isStepped()) && !this.interp.has(Interpolations.CONST);
    }

    /** Smooth monotonic time warp: endpoints stay fixed and f(0.5 + shift) = 0.5. */
    public float remapMotion(float progress)
    {
        if (this.motionShift == 0F || progress <= 0F || progress >= 1F || !this.supportsMotionShift()) return progress;
        double midpoint = 0.5D + this.motionShift;
        return (float) (progress * (1D - midpoint) / (midpoint + (1D - 2D * midpoint) * progress));
    }

    public double getY()
    {
        return this.factory.getY(this.value);
    }

    public void setValue(T value)
    {
        this.setValue(value, false);
    }

    public void setValue(T value, boolean dirty)
    {
        if (dirty) this.preNotify();

        this.value = value;

        if (dirty) this.postNotify();
    }

    public Interpolation getInterpolation()
    {
        return this.interp;
    }

    /**
     * The style this keyframe is drawn with. Read it freely; to change it, hand a modified copy to
     * {@link #setStyle(KeyframeStyle)} - editing this one in place skips the change notification
     * and the edit goes unrecorded.
     */
    public KeyframeStyle getStyle()
    {
        return this.style;
    }

    public void setStyle(KeyframeStyle style)
    {
        this.preNotify();
        this.style.copy(style);
        this.postNotify();
    }

    public void copy(Keyframe<T> keyframe)
    {
        this.tick = keyframe.tick;
        this.enabled = keyframe.enabled;
        this.duration = keyframe.duration;
        this.motionShift = keyframe.motionShift;
        this.value = this.factory.copy(keyframe.value);
        this.interp.copy(keyframe.interp);
        this.style.copy(keyframe.style);

        this.lx = keyframe.lx;
        this.ly = keyframe.ly;
        this.rx = keyframe.rx;
        this.ry = keyframe.ry;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (super.equals(obj))
        {
            return true;
        }

        if (obj instanceof Keyframe<?>)
        {
            Keyframe<?> kf = (Keyframe<?>) obj;
            return this.tick == kf.tick
                && this.enabled == kf.enabled
                && Objects.equals(this.value, kf.value)
                && this.lx == kf.lx
                && this.ly == kf.ly
                && this.rx == kf.rx
                && this.ry == kf.ry
                && this.duration == kf.duration
                && this.motionShift == kf.motionShift
                && Objects.equals(this.interp, kf.interp);
        }

        return false;
    }

    @Override
    public BaseType toData()
    {
        MapType data = new MapType();

        data.putFloat("tick", this.tick);
        if (!this.enabled) data.putBool("enabled", false);
        data.put("value", this.factory.toData(this.value));

        if (this.duration != 0F) data.putFloat("duration", this.duration);
        if (this.motionShift != 0F) data.putFloat("motion_shift", this.motionShift);
        if (this.interp.getInterp() != Interpolations.LINEAR) data.put("interp", this.interp.toData());
        if (this.lx != 5F) data.putFloat("lx", this.lx);
        if (this.ly != 0F) data.putFloat("ly", this.ly);
        if (this.rx != 5F) data.putFloat("rx", this.rx);
        if (this.ry != 0F) data.putFloat("ry", this.ry);
        this.style.toData(data);

        return data;
    }

    @Override
    public void fromData(BaseType data)
    {
        if (!data.isMap())
        {
            return;
        }

        MapType map = data.asMap();

        this.enabled = !map.has("enabled") || map.getBool("enabled");
        this.setMotionShift(map.getFloat("motion_shift"), false);

        this.style.fromData(map);

        if (map.has("tick")) this.tick = map.getFloat("tick");
        if (map.has("duration")) this.duration = map.getFloat("duration");
        if (map.has("value")) this.value = this.factory.fromData(map.get("value"));
        if (map.has("interp")) this.interp.fromData(map.get("interp"));
        if (map.has("lx")) this.lx = map.getFloat("lx");
        if (map.has("ly")) this.ly = map.getFloat("ly");
        if (map.has("rx")) this.rx = map.getFloat("rx");
        if (map.has("ry")) this.ry = map.getFloat("ry");
    }

    public void copyOverExtra(Keyframe<?> a)
    {
        this.setEnabled(a.isEnabled());
        this.getInterpolation().copy(a.getInterpolation());
        this.setStyle(a.getStyle());
        this.setDuration(a.getDuration());
        this.setMotionShift(a.getMotionShift());

        this.lx = a.lx;
        this.ly = a.ly;
        this.rx = a.rx;
        this.ry = a.ry;
    }
}
