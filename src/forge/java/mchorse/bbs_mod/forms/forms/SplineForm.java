package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import mchorse.bbs_mod.cubic.spline.*;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Transform;

/** Editor-only path geometry, optionally repeating its ordinary body parts. */
public class SplineForm extends Form implements SplineSource
{
    public final BaseKeyframeFactoryValue<SplinePositions> curve = new BaseKeyframeFactoryValue<>("curve", KeyframeFactories.SPLINE_POINTS, new SplinePositions());
    public final ValueSplinePoints points = new ValueSplinePoints("points");
    public final ValueBoolean closed = new ValueBoolean("closed", false);
    public final ValueBoolean repeatEnabled = new ValueBoolean("repeatEnabled", false);
    public final ValueInt repeatMode = new ValueInt("repeatMode", 0, 0, 1);
    public final ValueInt repeatCount = new ValueInt("repeatCount", 5, 1, SplineArray.MAX_COPIES);
    public final ValueFloat repeatDistance = new ValueFloat("repeatDistance", 1F, 0.001F, Float.MAX_VALUE);
    public final ValueInt repeatRotation = new ValueInt("repeatRotation", 1, 0, 2);
    public final ValueFloat repeatStart = new ValueFloat("repeatStart", 0F, 0F, 100F);
    public final ValueFloat repeatEnd = new ValueFloat("repeatEnd", 100F, 0F, 100F);
    public final ValueFloat repeatOffset = new ValueFloat("repeatOffset", 0F);

    public SplineForm()
    {
        this.add(this.curve);
        this.add(this.points);
        this.add(this.closed.animatable(false));
        this.add(this.repeatEnabled);
        this.add(this.repeatMode);
        this.add(this.repeatCount);
        this.add(this.repeatDistance);
        this.add(this.repeatRotation);
        this.add(this.repeatStart);
        this.add(this.repeatEnd);
        this.add(this.repeatOffset);
        for (int i = 0; i < 2; i++)
        {
            SplinePoint point = new SplinePoint("");
            point.position.getOriginalValue().translate.set(0, 0, i * 2);
            this.points.add(point);
        }
    }

    @Override public Icon getIcon() { return Icons.GRAPH; }

    @Override public ValueSplinePoints points() { return this.points; }
    @Override public boolean closed() { return this.closed.get(); }
    @Override public Transform position(String id)
    {
        return this.curve.get().getOrDefault(id, this.curve.getOriginalValue().point(id));
    }
    @Override public void bindPoint(SplinePoint point)
    {
        if (point.position.isBound()) return;
        this.curve.getOriginalValue().put(point.getId(), point.position.getOriginalValue());
        point.position.bind(this.curve, () -> this.curve.getOriginalValue().point(point.getId()),
            () -> this.position(point.getId()), value -> this.curve.getOriginalValue().put(point.getId(), value));
    }
    @Override public void fromData(BaseType data)
    {
        super.fromData(data);
        if (data.isMap() && data.asMap().has("curve")) this.curve.fromData(data.asMap().getMap("curve"));
    }
    @Override protected BaseType serializeChild(BaseValue value)
    {
        return value == this.points ? this.points.toData(point -> new MapType()) : super.serializeChild(value);
    }
}
