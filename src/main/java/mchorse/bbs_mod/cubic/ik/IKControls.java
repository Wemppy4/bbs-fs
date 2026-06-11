package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;

import java.util.HashMap;
import java.util.Map;

/**
 * Keyframe value holding the per-chain {@link IKControl} scalars, keyed by the
 * chain's tip bone. Mirrors {@link mchorse.bbs_mod.utils.pose.Pose} (its
 * {@code Map<bone, PoseTransform>}), so the ordinary keyframe-track path handles
 * the IK track with the same union-of-keys interpolation as the pose track.
 */
public class IKControls implements IMapSerializable
{
    public final Map<String, IKControl> controls = new HashMap<>();

    public IKControl get(String tip)
    {
        IKControl control = this.controls.get(tip);

        if (control == null)
        {
            control = new IKControl();

            this.controls.put(tip, control);
        }

        return control;
    }

    public IKControls copy()
    {
        IKControls controls = new IKControls();

        controls.copy(this);

        return controls;
    }

    public void copy(IKControls other)
    {
        this.controls.clear();

        for (Map.Entry<String, IKControl> entry : other.controls.entrySet())
        {
            if (!entry.getValue().isDefault())
            {
                this.controls.put(entry.getKey(), entry.getValue().copy());
            }
        }
    }

    @Override
    public void toData(MapType data)
    {
        if (this.controls.isEmpty())
        {
            return;
        }

        MapType ik = new MapType();

        for (Map.Entry<String, IKControl> entry : this.controls.entrySet())
        {
            if (!entry.getValue().isDefault())
            {
                ik.put(entry.getKey(), entry.getValue().toData());
            }
        }

        data.put("ik", ik);
    }

    @Override
    public void fromData(MapType data)
    {
        this.controls.clear();

        MapType ik = data.getMap("ik");

        for (String key : ik.keys())
        {
            IKControl control = new IKControl();

            control.fromData(ik.getMap(key));

            if (!control.isDefault())
            {
                this.controls.put(key, control);
            }
        }
    }

    public boolean isEmpty()
    {
        return this.controls.isEmpty();
    }
}
