package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.misc.ValueVector3f;
import mchorse.bbs_mod.utils.colors.Color;
import org.joml.Vector3f;

/**
 * The {@code bbs:structure} form: renders a structure NBT file (saved by a vanilla structure
 * block into {@code world/generated/<ns>/structures}) as a model, with a selectable biome that
 * drives grass/foliage/water tinting.
 */
public class StructureForm extends Form
{
    public static final Link FORM_ID = Link.bbs("structure");

    /** Structure id, {@code namespace:name}, resolved against {@code world/generated}. */
    public final ValueString structure = new ValueString("structure", "");

    /** Biome id used for tint colors (grass/foliage/water), e.g. {@code minecraft:plains}. */
    public final ValueString biome = new ValueString("biome", "minecraft:plains");

    /** Tint applied to the whole structure (blended with the film's color keyframes). */
    public final ValueColor color = new ValueColor("color", Color.white());

    /**
     * Where the form's pivot sits inside the structure, in blocks, relative to the default: the
     * middle of the footprint at its lowest layer (X/Z centered, Y at the bottom). Raising a
     * component pushes the pivot that way through the structure, so the structure itself renders
     * the other way and the form's transform rotates it around the new point.
     */
    public final ValueVector3f origin = new ValueVector3f("origin", new Vector3f());

    public StructureForm()
    {
        this.add(this.structure);
        this.add(this.biome);
        this.add(this.color);
        this.add(this.origin);
    }

    @Override
    protected String getDefaultDisplayName()
    {
        return "Structure";
    }
}
