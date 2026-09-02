package mchorse.bbs_mod.film.replays.tracks.behaviours;

import mchorse.bbs_mod.film.replays.tracks.TrackBehaviour;
import mchorse.bbs_mod.film.replays.tracks.TrackBlend;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormMaterial;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.HashMap;
import java.util.Map;

/**
 * One appearance property of one material — colour, colour overlay, glow, culling, or a PBR slider.
 *
 * <p>Each writes into the model form's transient override map for that property, which the renderer
 * layers over the material's static value. Blending starts from that static value, so a partially
 * applied state eases out of what the material actually shows.</p>
 */
public class MaterialPropTrack implements TrackBehaviour
{
    @Override
    public IKeyframeFactory factory(TrackId track)
    {
        String property = track.property();

        if (TrackId.MATERIAL_PROP_COLOR.equals(property) || TrackId.MATERIAL_PROP_OVERLAY.equals(property))
        {
            return KeyframeFactories.COLOR;
        }

        if (TrackId.MATERIAL_PROP_CULLING.equals(property))
        {
            return KeyframeFactories.INTEGER;
        }

        return KeyframeFactories.FLOAT;
    }

    @Override
    public void apply(TrackContext context, TrackId track, KeyframeChannel channel, float tick, float blend)
    {
        if (!(FormUtils.getForm(context.root(), track.formPath()) instanceof ModelForm modelForm))
        {
            return;
        }

        String material = track.material();
        String property = track.property();
        KeyframeSegment segment = channel.find(tick);
        FormMaterial staticMaterial = modelForm.materials.getMaterial(material);

        switch (property)
        {
            case TrackId.MATERIAL_PROP_COLOR ->
            {
                if (segment != null)
                {
                    Color current = staticMaterial == null ? Color.white() : staticMaterial.color.get();

                    modelForm.materialColorOverrides.put(material, (Color) TrackBlend.value(channel, current, segment, blend));
                }
                else if (blend >= 1F)
                {
                    modelForm.materialColorOverrides.remove(material);
                }
            }
            case TrackId.MATERIAL_PROP_OVERLAY ->
            {
                if (segment != null)
                {
                    Color current = staticMaterial == null ? new Color(1F, 1F, 1F, 0F) : staticMaterial.overlayColor.get();

                    modelForm.materialOverlayOverrides.put(material, (Color) TrackBlend.value(channel, current, segment, blend));
                }
                else if (blend >= 1F)
                {
                    modelForm.materialOverlayOverrides.remove(material);
                }
            }
            case TrackId.MATERIAL_PROP_LIGHTING ->
            {
                if (segment != null)
                {
                    Float current = staticMaterial == null ? 1F : staticMaterial.lighting.get();

                    modelForm.materialLightingOverrides.put(material, ((Number) TrackBlend.value(channel, current, segment, blend)).floatValue());
                }
                else if (blend >= 1F)
                {
                    modelForm.materialLightingOverrides.remove(material);
                }
            }
            case TrackId.MATERIAL_PROP_CULLING ->
            {
                if (segment != null)
                {
                    Integer current = staticMaterial == null ? 0 : staticMaterial.culling.get();

                    modelForm.materialCullingOverrides.put(material, ((Number) TrackBlend.value(channel, current, segment, blend)).intValue());
                }
                else if (blend >= 1F)
                {
                    modelForm.materialCullingOverrides.remove(material);
                }
            }
            default ->
            {
                /* The PBR sliders (smoothness/metallic/sss/pixel_emission/relief), all floats. */
                if (segment != null)
                {
                    Float current = staticMaterial == null ? 0F : pbrSlider(staticMaterial, property);
                    float value = ((Number) TrackBlend.value(channel, current, segment, blend)).floatValue();

                    modelForm.materialPbrOverrides.computeIfAbsent(material, (k) -> new HashMap<>()).put(property, value);
                }
                else if (blend >= 1F)
                {
                    removePbr(modelForm, material, property);
                }
            }
        }
    }

    /*
     * No reset, for the same reason as the texture track: the override maps are shared, and a state
     * releases after every render of the form. Off the end of its own keyframes the track already
     * removes what it put there.
     */

    private static void removePbr(ModelForm modelForm, String material, String property)
    {
        Map<String, Float> sliders = modelForm.materialPbrOverrides.get(material);

        if (sliders != null)
        {
            sliders.remove(property);

            if (sliders.isEmpty())
            {
                modelForm.materialPbrOverrides.remove(material);
            }
        }
    }

    private static float pbrSlider(FormMaterial material, String property)
    {
        return switch (property)
        {
            case TrackId.MATERIAL_PROP_SMOOTHNESS -> material.smoothness.get();
            case TrackId.MATERIAL_PROP_METALLIC -> material.metallic.get();
            case TrackId.MATERIAL_PROP_SSS -> material.sss.get();
            case TrackId.MATERIAL_PROP_PIXEL_EMISSION -> material.pixelEmission.get();
            case TrackId.MATERIAL_PROP_RELIEF -> material.relief.get();
            default -> 0F;
        };
    }
}
