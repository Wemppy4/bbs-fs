package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueGroup;

import java.util.Map;

/**
 * Dynamic map of a model form's touched bones (bone name &rarr; {@link FormBone}). Mirrors
 * {@link ValueMaterials}: the bone set comes from the model, not from the class, so
 * {@code fromData} creates a child per key found in the data, and a bone appears here only
 * once the author edits it. A bone the current model doesn't have is kept, never dropped —
 * the model is itself animatable, so the bone may exist on another frame.
 */
public class ValueBones extends ValueGroup
{
    public ValueBones(String id)
    {
        super(id);
    }

    /** The bone's properties, or null when the author never touched it (neutral). */
    public FormBone getBone(String name)
    {
        return this.get(name) instanceof FormBone bone ? bone : null;
    }

    /** The bone's properties, created neutral on first edit. */
    public FormBone getOrCreate(String name)
    {
        FormBone bone = this.getBone(name);

        if (bone == null)
        {
            bone = new FormBone(name);

            this.preNotify();
            this.add(bone);
            this.postNotify();
        }

        return bone;
    }

    @Override
    public void fromData(BaseType data)
    {
        this.removeAll();

        if (!data.isMap())
        {
            return;
        }

        for (Map.Entry<String, BaseType> entry : data.asMap())
        {
            FormBone bone = new FormBone(entry.getKey());

            this.add(bone);
            bone.fromData(entry.getValue());
        }
    }

    /** An all-neutral bone isn't persisted — it behaves exactly like an absent one. */
    @Override
    protected boolean canPersist(BaseValue value)
    {
        return !(value instanceof FormBone bone && bone.isDefault());
    }

    @Override
    public void copy(BaseValueGroup group)
    {
        /* The default copy only fills matching children; a copied form must reproduce
         * the source's bone set exactly, so rebuild from the source's data. */
        if (group instanceof ValueBones bones)
        {
            this.fromData(bones.toData());
        }
        else
        {
            super.copy(group);
        }
    }
}
