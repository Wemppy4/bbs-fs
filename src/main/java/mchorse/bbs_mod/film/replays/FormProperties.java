package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormMaterial;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FormProperties extends ValueGroup
{
    private static final String POSE_PROPERTY = "pose";

    public final Map<String, KeyframeChannel> properties = new HashMap<>();

    public FormProperties(String id)
    {
        super(id);
    }

    public void shift(float tick)
    {
        for (KeyframeChannel<?> value : this.properties.values())
        {
            for (Keyframe<?> keyframe : value.getKeyframes())
            {
                keyframe.setTick(keyframe.getTick() + tick);
            }
        }
    }

    public KeyframeChannel getOrCreate(Form form, String key)
    {
        BaseValue value = this.get(key);
        BaseValue property = FormUtils.getProperty(form, key);

        if (value instanceof KeyframeChannel channel)
        {
            return channel;
        }

        if (property != null)
        {
            return this.create(property);
        }

        if (PerLimbService.isPoseBoneChannel(key))
        {
            return this.registerChannel(key, KeyframeFactories.POSE_TRANSFORM);
        }

        if (PerLimbService.isMaterialTextureChannel(key))
        {
            return this.registerChannel(key, KeyframeFactories.LINK);
        }

        PerLimbService.MaterialPropPath materialProp = PerLimbService.parseMaterialPropPath(key);

        if (materialProp != null)
        {
            return this.registerChannel(key, materialPropFactory(materialProp.property()));
        }

        return null;
    }

    /** The keyframe factory of a per-material channel by its property name. */
    public static IKeyframeFactory materialPropFactory(String property)
    {
        if (PerLimbService.MATERIAL_PROP_COLOR.equals(property) || PerLimbService.MATERIAL_PROP_OVERLAY.equals(property))
        {
            return KeyframeFactories.COLOR;
        }

        if (PerLimbService.MATERIAL_PROP_CULLING.equals(property))
        {
            return KeyframeFactories.INTEGER;
        }

        return KeyframeFactories.FLOAT;
    }

    public KeyframeChannel create(BaseValue property)
    {
        if (property.isVisible() && property instanceof BaseKeyframeFactoryValue<?> keyframeFactoryValue)
        {
            String key = FormUtils.getPropertyPath(property);
            IKeyframeFactory factory = keyframeFactoryValue.getFactory();

            if (factory == KeyframeFactories.TRANSFORM && PerLimbService.isPoseBoneChannel(key))
            {
                factory = KeyframeFactories.POSE_TRANSFORM;
            }

            KeyframeChannel channel = new KeyframeChannel(key, factory);

            this.properties.put(key, channel);
            this.add(channel);

            return channel;
        }

        return null;
    }

    public KeyframeChannel registerChannel(String key, IKeyframeFactory factory)
    {
        KeyframeChannel channel = this.properties.get(key);

        if (channel == null)
        {
            channel = new KeyframeChannel(key, factory);

            this.properties.put(key, channel);
            this.add(channel);
        }

        return channel;
    }

    public void applyProperties(Form form, float tick)
    {
        this.applyProperties(form, tick, 1F);
    }

    public void applyProperties(Form form, float tick, float blend)
    {
        if (form == null)
        {
            return;
        }

        float clampedBlend = MathUtils.clamp(blend, 0F, 1F);
        List<KeyframeChannel> poseBoneChannels = new ArrayList<>();

        for (KeyframeChannel value : this.properties.values())
        {
            if (!PerLimbService.isPoseBoneChannel(value.getId()))
            {
                this.applyProperty(tick, form, value, clampedBlend);
            }
            else
            {
                poseBoneChannels.add(value);
            }
        }

        Set<String> processedForms = new HashSet<>();

        for (KeyframeChannel value : poseBoneChannels)
        {
            PerLimbService.PoseBonePath poseBonePath = PerLimbService.parsePoseBonePath(value.getId());

            if (poseBonePath != null)
            {
                String formPath = poseBonePath.formPath();

                if (!processedForms.contains(formPath))
                {
                    processedForms.add(formPath);

                    String poseKey = this.toPosePropertyKey(formPath);

                    if (!this.properties.containsKey(poseKey))
                    {
                        Form targetForm = FormUtils.getForm(form, formPath);

                        if (targetForm instanceof ModelForm modelForm)
                        {
                            modelForm.pose.setRuntimeValue(null);
                        }
                    }
                }
            }
        }

        for (KeyframeChannel value : poseBoneChannels)
        {
            this.applyProperty(tick, form, value, clampedBlend);
        }
    }

    private void applyProperty(float tick, Form form, KeyframeChannel value, float blend)
    {
        PerLimbService.PoseBonePath poseBonePath = PerLimbService.parsePoseBonePath(value.getId());

        if (poseBonePath != null)
        {
            String formPath = poseBonePath.formPath();
            String bone = poseBonePath.bone();
            Form targetForm = FormUtils.getForm(form, formPath);

            if (targetForm instanceof ModelForm modelForm)
            {
                KeyframeSegment segment = value.find(tick);

                if (segment != null)
                {
                    /* Copy on write */
                    if (modelForm.pose.getRuntimeValue() == null)
                    {
                        modelForm.pose.setRuntimeValue(modelForm.pose.getOriginalValue().copy());
                    }

                    PoseTransform transform = modelForm.pose.get().get(bone);
                    boolean isNew = transform == null;

                    if (isNew)
                    {
                        transform = new PoseTransform();
                        modelForm.pose.get().transforms.put(bone, transform);
                    }

                    Transform interpolated = (Transform) this.interpolateValue(value, new PoseTransform(), segment, blend);

                    transform.add(interpolated);
                }
            }

            return;
        }

        PerLimbService.MaterialTexturePath materialPath = PerLimbService.parseMaterialTexturePath(value.getId());

        if (materialPath != null)
        {
            Form targetForm = FormUtils.getForm(form, materialPath.formPath());

            if (targetForm instanceof ModelForm modelForm)
            {
                KeyframeSegment segment = value.find(tick);

                if (segment != null)
                {
                    modelForm.materialTextureOverrides.put(materialPath.material(), (Link) segment.createInterpolated());
                }
                else if (blend >= 1F)
                {
                    modelForm.materialTextureOverrides.remove(materialPath.material());
                }
            }

            return;
        }

        PerLimbService.MaterialPropPath materialProp = PerLimbService.parseMaterialPropPath(value.getId());

        if (materialProp != null)
        {
            Form targetForm = FormUtils.getForm(form, materialProp.formPath());

            if (targetForm instanceof ModelForm modelForm)
            {
                this.applyMaterialProp(tick, modelForm, materialProp, value, blend);
            }

            return;
        }

        BaseValueBasic property = FormUtils.getProperty(form, value.getId());

        if (property == null)
        {
            return;
        }

        KeyframeSegment segment = value.find(tick);

        if (segment != null)
        {
            Object interpolated = this.interpolateValue(value, property.get(), segment, blend);
            property.setRuntimeValue(interpolated);
        }
        else if (blend >= 1F)
        {
            property.setRuntimeValue(null);
        }
    }

    /**
     * A per-material appearance channel writes into the model form's transient override map for
     * its property (the renderer layers those over the static material values), mirroring the
     * texture channels' lifecycle: present segment &rarr; put, no segment at full blend &rarr; remove.
     * Blending starts from the material's static value, so a partially blended state eases out of
     * what the material actually shows.
     */
    private void applyMaterialProp(float tick, ModelForm modelForm, PerLimbService.MaterialPropPath path, KeyframeChannel value, float blend)
    {
        String material = path.material();
        String property = path.property();
        KeyframeSegment segment = value.find(tick);
        FormMaterial staticMaterial = modelForm.materials.getMaterial(material);

        if (PerLimbService.MATERIAL_PROP_COLOR.equals(property))
        {
            if (segment != null)
            {
                Color current = staticMaterial == null ? Color.white() : staticMaterial.color.get();

                modelForm.materialColorOverrides.put(material, (Color) this.interpolateValue(value, current, segment, blend));
            }
            else if (blend >= 1F)
            {
                modelForm.materialColorOverrides.remove(material);
            }
        }
        else if (PerLimbService.MATERIAL_PROP_OVERLAY.equals(property))
        {
            if (segment != null)
            {
                Color current = staticMaterial == null ? new Color(1F, 1F, 1F, 0F) : staticMaterial.overlayColor.get();

                modelForm.materialOverlayOverrides.put(material, (Color) this.interpolateValue(value, current, segment, blend));
            }
            else if (blend >= 1F)
            {
                modelForm.materialOverlayOverrides.remove(material);
            }
        }
        else if (PerLimbService.MATERIAL_PROP_LIGHTING.equals(property))
        {
            if (segment != null)
            {
                Float current = staticMaterial == null ? 1F : staticMaterial.lighting.get();

                modelForm.materialLightingOverrides.put(material, ((Number) this.interpolateValue(value, current, segment, blend)).floatValue());
            }
            else if (blend >= 1F)
            {
                modelForm.materialLightingOverrides.remove(material);
            }
        }
        else if (PerLimbService.MATERIAL_PROP_CULLING.equals(property))
        {
            if (segment != null)
            {
                Integer current = staticMaterial == null ? 0 : staticMaterial.culling.get();

                modelForm.materialCullingOverrides.put(material, ((Number) this.interpolateValue(value, current, segment, blend)).intValue());
            }
            else if (blend >= 1F)
            {
                modelForm.materialCullingOverrides.remove(material);
            }
        }
        else
        {
            /* The PBR sliders (smoothness/metallic/sss/pixel_emission/relief), all floats. */
            if (segment != null)
            {
                Float current = staticMaterial == null ? 0F : this.pbrSlider(staticMaterial, property);
                float interpolated = ((Number) this.interpolateValue(value, current, segment, blend)).floatValue();

                modelForm.materialPbrOverrides.computeIfAbsent(material, (k) -> new HashMap<>()).put(property, interpolated);
            }
            else if (blend >= 1F)
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
        }
    }

    private float pbrSlider(FormMaterial material, String property)
    {
        return switch (property)
        {
            case PerLimbService.MATERIAL_PROP_SMOOTHNESS -> material.smoothness.get();
            case PerLimbService.MATERIAL_PROP_METALLIC -> material.metallic.get();
            case PerLimbService.MATERIAL_PROP_SSS -> material.sss.get();
            case PerLimbService.MATERIAL_PROP_PIXEL_EMISSION -> material.pixelEmission.get();
            case PerLimbService.MATERIAL_PROP_RELIEF -> material.relief.get();
            default -> 0F;
        };
    }

    private Object interpolateValue(KeyframeChannel value, Object current, KeyframeSegment segment, float blend)
    {
        if (blend < 1F)
        {
            IKeyframeFactory factory = value.getFactory();
            Object v = factory.copy(current);
            Object a = factory.copy(segment.createInterpolated());
            Object interpolated = factory.interpolate(v, v, a, a, Interpolations.LINEAR, blend);

            return factory.copy(interpolated);
        }

        return segment.createInterpolated();
    }

    private String toPosePropertyKey(String formPath)
    {
        if (formPath == null || formPath.isEmpty())
        {
            return POSE_PROPERTY;
        }

        return formPath + FormUtils.PATH_SEPARATOR + POSE_PROPERTY;
    }

    public void resetProperties(Form form)
    {
        if (form == null)
        {
            return;
        }

        for (KeyframeChannel value : this.properties.values())
        {
            BaseValueBasic property = FormUtils.getProperty(form, value.getId());

            /* Skip, don't abort: a channel whose path no longer resolves on this form (a removed body part,
             * a state authored against another form, or a pose-bone/material channel, which is not a plain
             * property at all) used to end the whole reset, leaving every property after it stuck at its
             * runtime value — i.e. the state never turned off. Which properties survived depended on the
             * HashMap iteration order, so it was never stable behaviour to begin with.
             *
             * Note the pose-bone and per-material channels still aren't undone here: applyProperty writes
             * those into the model form's pose/material runtime values, and only the plain-property branch
             * has a counterpart below. Left alone deliberately — undoing them changes how a state with bone
             * channels releases, which wants a look in-game first. */
            if (property == null)
            {
                continue;
            }

            property.setRuntimeValue(null);
        }
    }

    public void cleanUp()
    {
        Iterator<KeyframeChannel> it = this.properties.values().iterator();

        while (it.hasNext())
        {
            KeyframeChannel next = it.next();

            if (next.isEmpty())
            {
                it.remove();
                this.remove(next);
            }
        }
    }

    @Override
    public void fromData(BaseType data)
    {
        super.fromData(data);

        this.removeAll();
        this.properties.clear();

        if (!data.isMap())
        {
            return;
        }

        MapType map = data.asMap();

        for (String key : map.keys())
        {
            MapType mapType = map.getMap(key);

            if (mapType.isEmpty())
            {
                continue;
            }

            KeyframeChannel property = new KeyframeChannel(key, null);

            property.fromData(mapType);

            /* Patch 1.1.1 changes to lighting property */
            if (key.endsWith("lighting") && property.getFactory() == KeyframeFactories.BOOLEAN)
            {
                KeyframeChannel newProperty = new KeyframeChannel(key, KeyframeFactories.FLOAT);

                for (Object keyframe : property.getKeyframes())
                {
                    Keyframe kf = (Keyframe) keyframe;
                    Boolean v = (Boolean) kf.getValue();

                    newProperty.insert(kf.getTick(), v ? 1F : 0F);
                }

                property = newProperty;
            }

            /* Convert transform to pose_transform for pose bone channels */
            if (property.getFactory() == KeyframeFactories.TRANSFORM && PerLimbService.isPoseBoneChannel(key))
            {
                KeyframeChannel newChannel = new KeyframeChannel(key, KeyframeFactories.POSE_TRANSFORM);

                for (Object o : property.getKeyframes())
                {
                    Keyframe kf = (Keyframe) o;
                    Object value = kf.getValue();
                    PoseTransform newValue = new PoseTransform();

                    if (value instanceof Transform)
                    {
                        newValue.copy((Transform) value);
                    }

                    Keyframe newKf = new Keyframe(kf.getId(), KeyframeFactories.POSE_TRANSFORM, kf.getTick(), newValue);

                    newKf.getInterpolation().copy(kf.getInterpolation());
                    newChannel.add(newKf);
                }

                newChannel.sort();
                property = newChannel;
            }

            if (property.getFactory() != null)
            {
                this.properties.put(key, property);
                this.add(property);
            }
        }
    }

    @Override
    protected boolean canPersist(BaseValue value)
    {
        if (value instanceof KeyframeChannel<?> channel)
        {
            return !channel.isEmpty();
        }

        return super.canPersist(value);
    }
}
