package mchorse.bbs_mod;

import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class ForgeAnimationTest
{
    @BeforeClass public static void init() { KeyframeFactories.setup(); }

    @Test public void savedTracksApplyAndResetOnJava8() throws Exception
    {
        ModelForm form = new ModelForm();
        form.transform.get().translate.x = 2F;
        FormProperties original = new FormProperties("properties");
        KeyframeChannel<Transform> transform = original.getOrCreate(form, TrackId.property("", "transform"));
        Transform end = new Transform(); end.translate.x = 10F;
        transform.insert(0, new Transform()); transform.insert(20, end);
        KeyframeChannel<PoseTransform> bone = original.getOrCreate(form, TrackId.bone("", "head"));
        PoseTransform rotated = new PoseTransform(); rotated.rotate.z = 1F;
        bone.insert(0, new PoseTransform()); bone.insert(20, rotated);

        FormProperties loaded = new FormProperties("properties");
        loaded.fromData(DataToString.mapFromString(DataToString.toString(original.toData())));
        assertEquals(original.toData(), loaded.toData());
        loaded.applyProperties(form, 10F);
        assertEquals(5F, form.transform.get().translate.x, 0.0001F);
        assertEquals(0.5F, form.pose.get().getOrCreate("head").rotate.z, 0.0001F);
        loaded.applyProperties(form, 0F);
        assertEquals(0F, form.transform.get().translate.x, 0.0001F);
        loaded.resetProperties(form);
        assertEquals(2F, form.transform.get().translate.x, 0.0001F);
    }

    @Test public void trackRecordIsDesugaredAndRetainsMapKeyEquality()
    {
        TrackId id = TrackId.bone("", "head");
        TrackId copy = TrackId.parse(id.toKey());
        assertEquals(id, copy); assertEquals(id.hashCode(), copy.hashCode());
        assertEquals(Object.class, TrackId.class.getSuperclass());
        java.util.Map<TrackId, String> values = new java.util.HashMap<>();
        values.put(id, "head"); assertEquals("head", values.get(copy));
        assertTrue(System.getProperty("java.version").startsWith("1.8."));
    }
}
