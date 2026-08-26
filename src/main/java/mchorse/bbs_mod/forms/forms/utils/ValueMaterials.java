package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.settings.values.base.BaseValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueGroup;

import java.util.Map;

/**
 * Dynamic map of a model form's touched materials (material name &rarr; {@link FormMaterial}).
 * Unlike a plain {@link ValueGroup}, whose {@code fromData} only fills children that already
 * exist, this creates a child per key found in the data — the material set comes from the
 * model, not from the class.
 */
public class ValueMaterials extends ValueGroup
{
    public ValueMaterials(String id)
    {
        super(id);
    }

    /** The material's settings, or null when the author never touched it (render treats null as neutral). */
    public FormMaterial getMaterial(String name)
    {
        return this.get(name) instanceof FormMaterial material ? material : null;
    }

    /** The material's settings, created neutral on first edit. */
    public FormMaterial getOrCreate(String name)
    {
        FormMaterial material = this.getMaterial(name);

        if (material == null)
        {
            material = new FormMaterial(name);

            this.preNotify();
            this.add(material);
            this.postNotify();
        }

        return material;
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
            FormMaterial material = new FormMaterial(entry.getKey());

            this.add(material);
            material.fromData(entry.getValue());
        }
    }

    @Override
    public void copy(BaseValueGroup group)
    {
        /* The default copy only fills matching children; a copied form must reproduce
         * the source's material set exactly, so rebuild from the source's data. */
        if (group instanceof ValueMaterials materials)
        {
            this.fromData(materials.toData());
        }
        else
        {
            super.copy(group);
        }
    }
}
