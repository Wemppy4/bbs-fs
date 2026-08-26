package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.colors.Color;

/**
 * Per-material appearance settings of a model form (the "Material" tab's material level).
 * Every default is neutral: an untouched material renders exactly as if this object didn't
 * exist. Created lazily by {@link ValueMaterials} the first time the author edits a material.
 *
 * <p>The PBR sliders bake a constant LabPBR specular texture (and an albedo-derived normal
 * map for {@code relief}) that Iris serves to shader packs instead of {@code _s}/{@code _n}
 * files — see {@code IrisPbrConstLoader}. All zeros = no PBR, matching LabPBR's "no map"
 * defaults, so no separate enable flag is needed.</p>
 */
public class FormMaterial extends ValueGroup
{
    /* Color/overlay/glow — vertex-level properties, composed with the form's and the bone's. */
    public final ValueColor color = new ValueColor("color", Color.white());
    public final ValueColor overlayColor = new ValueColor("color_overlay", new Color(1F, 1F, 1F, 0F));
    public final ValueFloat lighting = new ValueFloat("lighting", 1F);

    /* Render options — per-draw properties. */
    public final ValueInt culling = new ValueInt("culling", 0);

    /* PBR sliders (LabPBR specular channels + albedo-derived normals), 0..1 each. */
    public final ValueFloat smoothness = new ValueFloat("smoothness", 0F);
    public final ValueFloat metallic = new ValueFloat("metallic", 0F);
    public final ValueFloat sss = new ValueFloat("sss", 0F);
    public final ValueFloat pixelEmission = new ValueFloat("pixel_emission", 0F);
    public final ValueFloat relief = new ValueFloat("relief", 0F);

    public static final int CULLING_MODEL = 0;
    public static final int CULLING_ON = 1;
    public static final int CULLING_OFF = 2;

    public FormMaterial(String id)
    {
        super(id);

        this.color.invisible();
        this.overlayColor.invisible();
        this.lighting.invisible();
        this.culling.invisible();
        this.smoothness.invisible();
        this.metallic.invisible();
        this.sss.invisible();
        this.pixelEmission.invisible();
        this.relief.invisible();

        this.add(this.color);
        this.add(this.overlayColor);
        this.add(this.lighting);
        this.add(this.culling);
        this.add(this.smoothness);
        this.add(this.metallic);
        this.add(this.sss);
        this.add(this.pixelEmission);
        this.add(this.relief);
    }

    /** Whether any PBR slider is set — only then the albedo is redirected to a per-material variant texture. */
    public boolean hasPbr()
    {
        return this.smoothness.get() > 0F || this.metallic.get() > 0F || this.sss.get() > 0F
            || this.pixelEmission.get() > 0F || this.relief.get() > 0F;
    }

    /**
     * A stable key of the quantized PBR slider values. It names the albedo variant texture, so
     * moving a slider produces a NEW GL texture id — Iris' PBR holder cache is keyed by that id,
     * which makes edits take effect without poking Iris' internals.
     */
    public String getPbrKey()
    {
        return "pbr" + Math.round(this.smoothness.get() * 255F)
            + "_" + Math.round(this.metallic.get() * 255F)
            + "_" + Math.round(this.sss.get() * 255F)
            + "_" + Math.round(this.pixelEmission.get() * 255F)
            + "_" + Math.round(this.relief.get() * 255F);
    }
}
