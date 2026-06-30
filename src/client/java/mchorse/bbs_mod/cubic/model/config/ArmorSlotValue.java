package mchorse.bbs_mod.cubic.model.config;

import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.utils.pose.Transform;

/**
 * A single attachment slot in a model's config (an item-hand entry, an armor piece or a first-person
 * hand placement), mirroring {@link ArmorSlot} as an editable value tree: the bone it rides on plus a
 * {@link Transform}. An empty {@link #group} means the slot is inactive — the config skips writing it.
 *
 * <p>Rotations live in radians at runtime (like the rest of the value tree), but the config file stores
 * them in degrees — the legacy {@code ArmorSlot} format — so {@link #fromData}/{@link #toData} convert
 * around the transform child. Item entries authored as a bare bone string (no transform) are accepted.</p>
 */
public class ArmorSlotValue extends ValueGroup
{
    public final ValueString group = new ValueString("group", "");
    public final ValueTransform transform = new ValueTransform("transform", new Transform());

    public ArmorSlotValue(String id)
    {
        super(id);

        this.add(this.group);
        this.add(this.transform);
    }

    public boolean isActive()
    {
        return !this.group.get().trim().isEmpty();
    }

    public ArmorSlot toSlot()
    {
        ArmorSlot slot = new ArmorSlot();

        slot.group = this.group.get();
        slot.transform.copy(this.transform.get());

        return slot;
    }

    @Override
    protected boolean canPersist(BaseValue value)
    {
        /* An identity transform stays out of the file — the slot then reads back as just a bone. */
        if (value == this.transform)
        {
            return !this.transform.get().isDefault();
        }

        return super.canPersist(value);
    }

    @Override
    public void fromData(BaseType data)
    {
        if (data.isString())
        {
            this.group.set(data.asString());
            this.transform.get().identity();

            return;
        }

        super.fromData(data);

        this.transform.get().toRad();
    }

    @Override
    public BaseType toData()
    {
        Transform transform = this.transform.get();

        transform.toDeg();

        BaseType data = super.toData();

        transform.toRad();

        return data;
    }
}
