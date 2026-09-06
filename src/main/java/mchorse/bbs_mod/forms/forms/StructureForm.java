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

    /** What a new structure form shows: the portal that ships in BBS's own structures folder. */
    public static final String DEFAULT_STRUCTURE = "assets:portal";

    /**
     * Structure id: {@code assets:path} for one of BBS's own, {@code namespace:name} for one the
     * world's {@code generated} folder holds.
     *
     * <p>Starts on the portal BBS ships, so a fresh structure form is something rather than an
     * empty spot waiting for the picker.</p>
     */
    public final ValueString structure = new ValueString("structure", DEFAULT_STRUCTURE);

    /** Biome id used for tint colors (grass/foliage/water), e.g. {@code minecraft:plains}. */
    public final ValueString biome = new ValueString("biome", "minecraft:plains");

    /** Tint applied to the whole structure (blended with the film's color keyframes). */
    public final ValueColor color = new ValueColor("color", Color.white());

    /**
     * Where the form's pivot sits inside the structure, in blocks, relative to the default: the
     * middle of the footprint at its lowest layer (X/Z centered, Y at the bottom). Raising a
     * component pushes the pivot that way through the structure, so the structure itself renders
     * the other way and the form's transform rotates it around the new point.
     *
     * <p>Shown as "Anchor" and not animatable: it says where the structure is held, and moving the
     * structure over time is what the form's own transform is for.</p>
     */
    public final ValueVector3f origin = new ValueVector3f("origin", new Vector3f());

    public StructureForm()
    {
        this.add(this.structure);
        this.add(this.biome);
        this.add(this.color);
        this.add(this.origin);
    }

    /**
     * The structure's id, whole — the way a billboard, an audio clip or a video form name
     * themselves. Every structure form used to read "Structure", which told a list of them nothing
     * about which was which.
     */
    @Override
    protected String getDefaultDisplayName()
    {
        String id = this.structure.get();

        return id.isEmpty() ? "Structure" : id;
    }
}
