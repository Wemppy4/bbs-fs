package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.utils.colors.Color;

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
     * Opt-in fast replay: copies the baked vertices into the render buffer as raw bytes instead of
     * going through the per-vertex consumer path. Faster on large structures, but only takes effect
     * without a shaderpack (Iris owns the terrain pipeline), and it routes translucent geometry
     * through the terrain layers — so semi-transparent blocks can show the depth-sorting artifacts
     * the default path avoids. Off by default.
     */
    public final ValueBoolean fastRender = new ValueBoolean("fastRender", false);

    public StructureForm()
    {
        this.add(this.structure);
        this.add(this.biome);
        this.add(this.color);
        this.add(this.fastRender);
    }

    @Override
    protected String getDefaultDisplayName()
    {
        return "Structure";
    }
}
