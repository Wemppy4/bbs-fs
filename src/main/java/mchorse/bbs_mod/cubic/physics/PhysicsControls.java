package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keyframe value holding the per-chain {@link PhysicsControl} scalars, keyed by the
 * chain's root bone. Mirrors {@link mchorse.bbs_mod.cubic.ik.IKControls}, so the
 * ordinary keyframe-track path handles the physics track with the same
 * union-of-keys interpolation as the IK and pose tracks.
 */
public class PhysicsControls implements IMapSerializable
{
    private static Set<String> keys = new HashSet<>();

    public final Map<String, PhysicsControl> controls = new HashMap<>();

    public PhysicsControl get(String root)
    {
        PhysicsControl control = this.controls.get(root);

        if (control == null)
        {
            control = new PhysicsControl();

            this.controls.put(root, control);
        }

        return control;
    }

    public PhysicsControls copy()
    {
        PhysicsControls controls = new PhysicsControls();

        controls.copy(this);

        return controls;
    }

    public void copy(PhysicsControls other)
    {
        this.controls.clear();

        for (Map.Entry<String, PhysicsControl> entry : other.controls.entrySet())
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

        MapType physics = new MapType();

        for (Map.Entry<String, PhysicsControl> entry : this.controls.entrySet())
        {
            if (!entry.getValue().isDefault())
            {
                physics.put(entry.getKey(), entry.getValue().toData());
            }
        }

        data.put("physics", physics);
    }

    @Override
    public void fromData(MapType data)
    {
        this.controls.clear();

        MapType physics = data.getMap("physics");

        for (String key : physics.keys())
        {
            PhysicsControl control = new PhysicsControl();

            control.fromData(physics.getMap(key));

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

    /** Value equality over the union of chains, a chain absent on one side counting as default — so two keyframes the user means as identical are marked identical even when one dropped its default entries. */
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (obj instanceof PhysicsControls other)
        {
            keys.clear();
            keys.addAll(this.controls.keySet());
            keys.addAll(other.controls.keySet());

            for (String key : keys)
            {
                PhysicsControl a = this.controls.get(key);
                PhysicsControl b = other.controls.get(key);

                if (a != null && b != null && !a.equals(b)) return false;
                if (a == null && !b.isDefault()) return false;
                if (b == null && !a.isDefault()) return false;
            }

            return true;
        }

        return false;
    }
}
