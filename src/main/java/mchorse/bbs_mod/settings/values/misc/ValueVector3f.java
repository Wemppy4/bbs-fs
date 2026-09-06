package mchorse.bbs_mod.settings.values.misc;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import org.joml.Vector3f;

/**
 * A plain three component vector: a setting, not an animatable property.
 *
 * <p>It used to carry a keyframe factory, which made every value of this type a track. The
 * keyframes behind it needed a bezier handle per axis and a graph that draws three curves — a
 * parallel half of the keyframe editor that, in the end, exactly one property in BBS ever
 * reached. Vectors that <em>are</em> animated (a pose transform, an anchor) go through their own
 * factory, so nothing was lost with it.</p>
 */
public class ValueVector3f extends BaseValueBasic<Vector3f>
{
    public ValueVector3f(String id, Vector3f value)
    {
        super(id, value);
    }

    @Override
    protected Vector3f copyValue(Vector3f value)
    {
        return value == null ? null : new Vector3f(value);
    }

    @Override
    public BaseType toData()
    {
        return DataStorageUtils.vector3fToData(this.value);
    }

    @Override
    public void fromData(BaseType data)
    {
        if (data.isList())
        {
            this.value = DataStorageUtils.vector3fFromData(data.asList());
        }
    }
}
