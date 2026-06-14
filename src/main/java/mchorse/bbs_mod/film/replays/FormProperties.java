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
import mchorse.bbs_mod.resources.Link;
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

        return null;
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

            if (property == null)
            {
                return;
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
