package mchorse.bbs_mod;

import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.clips.modifiers.TranslateClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmManager;
import mchorse.bbs_mod.film.replays.Hotbar;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ForgeFilmTest
{
    @Rule public TemporaryFolder files = new TemporaryFolder();
    @BeforeClass public static void init() { Bootstrap.register(); KeyframeFactories.setup(); }

    @Test public void savedFilmKeepsCameraLayersActorMotionAndItems()
    {
        FilmManager manager = new FilmManager(() -> files.getRoot());
        Film film = manager.create("scene");
        Replay replay = film.replays.addReplay();
        ModelForm form = new ModelForm(); form.model.set("helper_drone"); replay.form.set(form);
        replay.keyframes.x.insert(0, 2D); replay.keyframes.x.insert(20, 12D);
        replay.keyframes.y.insert(0, 4D); replay.keyframes.z.insert(0, 3D);
        ItemStack item = new ItemStack(Items.DIAMOND_SWORD); item.setStackDisplayName("BBS actor");
        replay.keyframes.hotbar.insert(0, Hotbar.of((slot) -> slot == 2 ? item : ItemStack.EMPTY));
        replay.keyframes.selectedSlot.insert(0, 2);
        IdleClip camera = new IdleClip(); camera.duration.set(40); camera.position.get().point.set(1, 4, 2);
        TranslateClip offset = new TranslateClip(); offset.duration.set(40); offset.layer.set(1); offset.translate.get().set(3, 0, 0);
        film.camera.addClip(camera); film.camera.addClip(offset);
        assertTrue(manager.save("scene", film.toData().asMap()));
        Film loaded = manager.load("scene"); assertNotNull(loaded);
        assertEquals(film.toData(), loaded.toData());
        assertEquals(40, loaded.calculateDuration());
        Replay restored = loaded.replays.getById(replay.getId()); assertNotNull(restored);
        StubEntity actor = new StubEntity(); restored.keyframes.apply(10F, actor); restored.keyframes.applyEquipment(10F, actor);
        assertEquals(7D, actor.getX(), 0.001D);
        assertEquals(4D, actor.getY(), 0.001D);
        assertTrue(ItemStack.areItemStacksEqual(item, actor.getEquipmentStack(EntityEquipmentSlot.MAINHAND)));
        CameraClipContext context = new CameraClipContext(); context.clips = loaded.camera; context.setup(10, 0F);
        Position position = new Position();
        for (Clip clip : loaded.camera.getClips(10)) context.apply(clip, position);
        assertEquals(4D, position.point.x, 0.001D); assertEquals(4D, position.point.y, 0.001D);
    }

    @Test public void nativeNbtKeepsNestedItemDataAndAllArrays()
    {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByteArray("bytes", new byte[] {-3, 0, 127});
        tag.setIntArray("ints", new int[] {Integer.MIN_VALUE, 17});
        tag.setTag("longs", new NBTTagLongArray(new long[] {Long.MIN_VALUE, Long.MAX_VALUE}));
        NBTTagList list = new NBTTagList(); NBTTagCompound child = new NBTTagCompound();
        child.setString("name", "actor"); child.setDouble("position", 1.25D); list.appendTag(child); tag.setTag("list", list);
        assertEquals(tag, DataStorageUtils.toNbt(DataStorageUtils.fromNbt(tag)));
    }
}
