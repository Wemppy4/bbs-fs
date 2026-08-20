package mchorse.bbs_mod.utils.keyframes;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Keyframe <T> extends BaseValue
{
    private float tick;
    private T value;

    public float lx = 5;
    public float ly;
    public float rx = 5;
    public float ry;

    public List<Float> lx_m;
    public List<Float> ly_m;
    public List<Float> rx_m;
    public List<Float> ry_m;

    private KeyframeShape shape = BBSSettings.getDefaultKeyframeShape();
    private Color color;

    /**
     * Forced duration that would be used instead of the difference
     * between two keyframes, if not 0
     */
    private float duration;
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

    public double getY(int index)
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

    public float getLx(int axis)
    {
        return this.getHandle(this.lx_m, axis, this.lx);
    }

    public float getLy(int axis)
    {
        return this.getHandle(this.ly_m, axis, this.ly);
    }

    public float getRx(int axis)
    {
        return this.getHandle(this.rx_m, axis, this.rx);
    }

    public float getRy(int axis)
    {
        return this.getHandle(this.ry_m, axis, this.ry);
    }

    public void setLx(int axis, float value)
    {
        if (axis < 0)
        {
            this.lx = value;
            return;
        }

        this.ensureMultiHandles(axis + 1);
        this.lx_m.set(axis, value);
    }

    public void setLy(int axis, float value)
    {
        if (axis < 0)
        {
            this.ly = value;
            return;
        }

        this.ensureMultiHandles(axis + 1);
        this.ly_m.set(axis, value);
    }

    public void setRx(int axis, float value)
    {
        if (axis < 0)
        {
            this.rx = value;
            return;
        }

        this.ensureMultiHandles(axis + 1);
        this.rx_m.set(axis, value);
    }

    public void setRy(int axis, float value)
    {
        if (axis < 0)
        {
            this.ry = value;
            return;
        }

        this.ensureMultiHandles(axis + 1);
        this.ry_m.set(axis, value);
    }

    public void ensureMultiHandles(int size)
    {
        if (size <= 0)
        {
            return;
        }

        if (this.lx_m == null)
        {
            this.lx_m = new ArrayList<>();
            this.ly_m = new ArrayList<>();
            this.rx_m = new ArrayList<>();
            this.ry_m = new ArrayList<>();
        }

        this.ensureHandleSize(this.lx_m, size, this.lx);
        this.ensureHandleSize(this.ly_m, size, this.ly);
        this.ensureHandleSize(this.rx_m, size, this.rx);
        this.ensureHandleSize(this.ry_m, size, this.ry);
    }

    private float getHandle(List<Float> list, int axis, float fallback)
    {
        return axis >= 0 && list != null && axis < list.size() ? list.get(axis) : fallback;
    }

    private void ensureHandleSize(List<Float> list, int size, float fallback)
    {
        while (list.size() < size)
        {
            list.add(fallback);
        }
    }

    public Interpolation getInterpolation()
    {
        return this.interp;
    }

    public KeyframeShape getShape()
    {
        return this.shape;
    }

    public void setShape(KeyframeShape shape)
    {
        this.preNotify();
        this.shape = shape;
        this.postNotify();
    }

    public Color getColor()
    {
        return this.color;
    }

    public void setColor(Color color)
    {
        this.preNotify();
        this.color = color;
        this.postNotify();
    }

    public void copy(Keyframe<T> keyframe)
    {
        this.tick = keyframe.tick;
        this.duration = keyframe.duration;
        this.value = this.factory.copy(keyframe.value);
        this.interp.copy(keyframe.interp);
        this.shape = keyframe.shape;
        this.color = keyframe.color;

        this.lx = keyframe.lx;
        this.ly = keyframe.ly;
        this.rx = keyframe.rx;
        this.ry = keyframe.ry;

        if (keyframe.lx_m != null) this.lx_m = new ArrayList<>(keyframe.lx_m);
        if (keyframe.ly_m != null) this.ly_m = new ArrayList<>(keyframe.ly_m);
        if (keyframe.rx_m != null) this.rx_m = new ArrayList<>(keyframe.rx_m);
        if (keyframe.ry_m != null) this.ry_m = new ArrayList<>(keyframe.ry_m);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (super.equals(obj))
        {
            return true;
        }

        if (obj instanceof Keyframe<?> kf)
        {
            return this.tick == kf.tick
                && Objects.equals(this.value, kf.value)
                && this.lx == kf.lx
                && this.ly == kf.ly
                && this.rx == kf.rx
                && this.ry == kf.ry
                && Objects.equals(this.lx_m, kf.lx_m)
                && Objects.equals(this.ly_m, kf.ly_m)
                && Objects.equals(this.rx_m, kf.rx_m)
                && Objects.equals(this.ry_m, kf.ry_m)
                && this.duration == kf.duration
                && Objects.equals(this.interp, kf.interp);
        }

        return false;
    }

    @Override
    public BaseType toData()
    {
        MapType data = new MapType();

        data.putFloat("tick", this.tick);
        data.put("value", this.factory.toData(this.value));

        if (this.duration != 0F) data.putFloat("duration", this.duration);
        if (this.interp.getInterp() != Interpolations.LINEAR) data.put("interp", this.interp.toData());
        if (this.lx != 5F) data.putFloat("lx", this.lx);
        if (this.ly != 0F) data.putFloat("ly", this.ly);
        if (this.rx != 5F) data.putFloat("rx", this.rx);
        if (this.ry != 0F) data.putFloat("ry", this.ry);
        if (this.color != null) data.putInt("color", this.color.getRGBColor());
        if (this.shape != KeyframeShape.SQUARE) data.putString("shape", this.shape.toString().toUpperCase());

        if (this.lx_m != null)
        {
            ListType lx = new ListType();
            ListType ly = new ListType();
            ListType rx = new ListType();
            ListType ry = new ListType();

            for (Float f : this.lx_m) lx.addFloat(f);
            for (Float f : this.ly_m) ly.addFloat(f);
            for (Float f : this.rx_m) rx.addFloat(f);
            for (Float f : this.ry_m) ry.addFloat(f);

            data.put("lx_m", lx);
            data.put("ly_m", ly);
            data.put("rx_m", rx);
            data.put("ry_m", ry);
        }

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

        this.shape = KeyframeShape.SQUARE;
        this.color = null;

        if (map.has("tick")) this.tick = map.getFloat("tick");
        if (map.has("duration")) this.duration = map.getFloat("duration");
        if (map.has("value")) this.value = this.factory.fromData(map.get("value"));
        if (map.has("interp")) this.interp.fromData(map.get("interp"));
        if (map.has("lx")) this.lx = map.getFloat("lx");
        if (map.has("ly")) this.ly = map.getFloat("ly");
        if (map.has("rx")) this.rx = map.getFloat("rx");
        if (map.has("ry")) this.ry = map.getFloat("ry");
        if (map.has("shape")) this.shape = KeyframeShape.fromString(map.getString("shape"));
        if (map.has("color")) this.color = Color.rgb(map.getInt("color"));

        if (map.has("lx_m"))
        {
            this.lx_m = new ArrayList<>();
            this.ly_m = new ArrayList<>();
            this.rx_m = new ArrayList<>();
            this.ry_m = new ArrayList<>();

            ListType lx = map.getList("lx_m");
            ListType ly = map.getList("ly_m");
            ListType rx = map.getList("rx_m");
            ListType ry = map.getList("ry_m");

            for (int i = 0; i < lx.size(); i++) this.lx_m.add(lx.getFloat(i));
            for (int i = 0; i < ly.size(); i++) this.ly_m.add(ly.getFloat(i));
            for (int i = 0; i < rx.size(); i++) this.rx_m.add(rx.getFloat(i));
            for (int i = 0; i < ry.size(); i++) this.ry_m.add(ry.getFloat(i));
        }
    }

    public void copyOverExtra(Keyframe<?> a)
    {
        this.getInterpolation().copy(a.getInterpolation());
        this.setShape(a.getShape());
        this.setColor(a.getColor() != null ? a.getColor().copy() : null);
        this.setDuration(a.getDuration());

        this.lx = a.lx;
        this.ly = a.ly;
        this.rx = a.rx;
        this.ry = a.ry;

        if (a.lx_m != null) this.lx_m = new ArrayList<>(a.lx_m);
        if (a.ly_m != null) this.ly_m = new ArrayList<>(a.ly_m);
        if (a.rx_m != null) this.rx_m = new ArrayList<>(a.rx_m);
        if (a.ry_m != null) this.ry_m = new ArrayList<>(a.ry_m);
    }
}
