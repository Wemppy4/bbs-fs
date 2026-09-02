package mchorse.bbs_mod.cubic.chains;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keyframe value holding the per-chain {@link ChainControl} scalars, keyed by the bone that
 * names the chain. Mirrors {@link mchorse.bbs_mod.utils.pose.Pose} (its
 * {@code Map<bone, PoseTransform>}), so the ordinary keyframe-track path handles a solver
 * track with the same union-of-keys interpolation as the pose track.
 */
public abstract class ChainControls <C extends ChainControl<C>, S extends ChainControls<C, S>> implements IMapSerializable
{
    private static Set<String> keys = new HashSet<>();

    public final Map<String, C> controls = new HashMap<>();

    /** An empty container of this kind. */
    protected abstract S createControls();

    protected abstract C createControl();

    /** The key this container's map is stored under, and the name of the track it drives. */
    protected abstract String getDataKey();

    public C get(String bone)
    {
        C control = this.controls.get(bone);

        if (control == null)
        {
            control = this.createControl();

            this.controls.put(bone, control);
        }

        return control;
    }

    public S copy()
    {
        S controls = this.createControls();

        controls.copy(this);

        return controls;
    }

    public void copy(ChainControls<C, S> other)
    {
        this.controls.clear();

        for (Map.Entry<String, C> entry : other.controls.entrySet())
        {
            if (!entry.getValue().isDefault())
            {
                this.controls.put(entry.getKey(), entry.getValue().copy());
            }
        }
    }

    public boolean isEmpty()
    {
        return this.controls.isEmpty();
    }

    @Override
    public void toData(MapType data)
    {
        if (this.controls.isEmpty())
        {
            return;
        }

        MapType map = new MapType();

        for (Map.Entry<String, C> entry : this.controls.entrySet())
        {
            if (!entry.getValue().isDefault())
            {
                map.put(entry.getKey(), entry.getValue().toData());
            }
        }

        data.put(this.getDataKey(), map);
    }

    @Override
    public void fromData(MapType data)
    {
        this.controls.clear();

        MapType map = data.getMap(this.getDataKey());

        for (String key : map.keys())
        {
            C control = this.createControl();

            control.fromData(map.getMap(key));

            if (!control.isDefault())
            {
                this.controls.put(key, control);
            }
        }
    }

    /** Value equality over the union of chains, a chain absent on one side counting as default — so two keyframes the user means as identical are marked identical even when one dropped its default entries. */
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (obj == null || obj.getClass() != this.getClass())
        {
            return false;
        }

        ChainControls<C, S> other = (ChainControls<C, S>) obj;

        keys.clear();
        keys.addAll(this.controls.keySet());
        keys.addAll(other.controls.keySet());

        for (String key : keys)
        {
            C a = this.controls.get(key);
            C b = other.controls.get(key);

            if (a != null && b != null && !a.equals(b)) return false;
            if (a == null && !b.isDefault()) return false;
            if (b == null && !a.isDefault()) return false;
        }

        return true;
    }
}
