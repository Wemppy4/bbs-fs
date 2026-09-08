package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.behaviours.MaterialPropTrack;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.ValueMaterials;
import mchorse.bbs_mod.forms.renderers.utils.FormMaterialLevels;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/** Standalone checks; run main with the test runtime classpath. */
public class MaterialVisibilityTest
{
    public static void main(String[] args)
    {
        BBSSettings.recordingPoseTransformOverlays = new ValueInt("pose_transform_overlays", 0);
        ModelForm form = new ModelForm();
        String material = "Material.001";

        check(FormMaterialLevels.materialVisible(form, material), "Untouched materials must be visible");
        form.materials.getOrCreate(material).visible.set(false);
        check(!FormMaterialLevels.materialVisible(form, material), "Static visibility must hide the material");
        check(FormMaterialLevels.materialVisible(form, "Other"), "Other materials must remain visible");

        ValueMaterials restored = new ValueMaterials("materials");
        restored.fromData(form.materials.toData());
        check(!restored.getMaterial(material).visible.get(), "Hidden materials must survive save/load");
        ValueMaterials copied = new ValueMaterials("materials");
        copied.copy(restored);
        check(!copied.getMaterial(material).visible.get(), "Copy must retain visibility");
        MapType oldData = new MapType();
        oldData.put(material, new MapType());
        restored.fromData(oldData);
        check(restored.getMaterial(material).visible.get(), "Old materials must default to visible");

        TrackId id = TrackId.materialProp("", material, TrackId.MATERIAL_PROP_VISIBLE);
        check(id.equals(TrackId.parse(id.toKey())), "Track names must round-trip with dots in the material name");
        MaterialPropTrack track = new MaterialPropTrack();
        check(track.factory(id) == KeyframeFactories.BOOLEAN, "Visibility must use stepped boolean keys");
        KeyframeChannel<Boolean> channel = new KeyframeChannel<>(id.toKey(), KeyframeFactories.BOOLEAN);
        channel.insert(0, true);
        channel.insert(10, false);

        track.apply(TrackContext.of(form), id, channel, 5F, 1F);
        check(FormMaterialLevels.materialVisible(form, material), "Animation must override static visibility");
        track.apply(TrackContext.of(form), id, channel, 10F, 1F);
        check(!FormMaterialLevels.materialVisible(form, material), "The next key must hide the material");

        form.materials.getMaterial(material).visible.set(true);
        KeyframeChannel<Boolean> empty = new KeyframeChannel<>(id.toKey(), KeyframeFactories.BOOLEAN);
        track.apply(TrackContext.of(form), id, empty, 0F, 1F);
        check(!form.materialVisibilityOverrides.containsKey(material), "An empty channel must release its override");
        check(FormMaterialLevels.materialVisible(form, material), "Removing an override must restore static visibility");

        System.out.println("Material visibility checks passed");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }
}
