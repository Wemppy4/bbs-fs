package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormMaterial;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;

import java.util.Map;

/**
 * Resolves a material's effective appearance values: the animation track's runtime override
 * first, the static material settings second, neutral otherwise. The empty material name (a
 * single-texture model) has no material level at all — the form level covers it.
 */
public class FormMaterialLevels
{
    /** The material's multiply color, or null when neutral. */
    public static Color materialColor(ModelForm form, String material)
    {
        if (material == null || material.isEmpty())
        {
            return null;
        }

        Color override = form.materialColorOverrides.get(material);

        if (override != null)
        {
            return override;
        }

        FormMaterial formMaterial = form.materials.getMaterial(material);

        return formMaterial == null ? null : formMaterial.color.get();
    }

    /**
     * The material's glow amount 0..1 (0 = normally lit). Stored as "lighting" with the form's
     * semantics (1 = normal) for consistency, so this returns {@code 1 - lighting}.
     */
    public static float materialGlow(ModelForm form, String material)
    {
        if (material == null || material.isEmpty())
        {
            return 0F;
        }

        Float override = form.materialLightingOverrides.get(material);

        if (override != null)
        {
            return 1F - MathUtils.clamp(override, 0F, 1F);
        }

        FormMaterial formMaterial = form.materials.getMaterial(material);

        if (formMaterial == null)
        {
            return 0F;
        }

        return 1F - MathUtils.clamp(formMaterial.lighting.get(), 0F, 1F);
    }

    /** The material's face culling mode ({@link FormMaterial#CULLING_MODEL} when untouched). */
    public static int materialCulling(ModelForm form, String material)
    {
        if (material == null || material.isEmpty())
        {
            return FormMaterial.CULLING_MODEL;
        }

        Integer override = form.materialCullingOverrides.get(material);

        if (override != null)
        {
            return override;
        }

        FormMaterial formMaterial = form.materials.getMaterial(material);

        return formMaterial == null ? FormMaterial.CULLING_MODEL : formMaterial.culling.get();
    }

    /**
     * The material's effective PBR slider (the animation track's override over the static
     * value). The static material may be null — tracks can drive an untouched material.
     */
    public static float pbrSlider(ModelForm form, String material, String property, float staticValue)
    {
        Map<String, Float> sliders = form.materialPbrOverrides.get(material == null ? "" : material);
        Float override = sliders == null ? null : sliders.get(property);

        return override != null ? override : staticValue;
    }
}
