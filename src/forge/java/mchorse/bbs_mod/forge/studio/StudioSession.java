package mchorse.bbs_mod.forge.studio;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.*;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.ReplayEntity;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import java.io.File;
import java.util.*;

/** Editing document, file operations and history, independent of screen layout. */
public final class StudioSession
{
    public static StudioSession current;
    public final FilmManager manager;
    public Film film;
    public FilmRuntime runtime;
    public String selectedReplay = "", status = "";
    public Clip selectedClip;
    public boolean dirty, recording;
    private Replay recordingReplay;
    private MCEntity recorder;
    private final Deque<MapType> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

    public StudioSession()
    {
        Minecraft mc = Minecraft.getMinecraft();
        File root = mc.getIntegratedServer() == null ? new File(BBSMod.getAssetsFolder().getParentFile(), "client/films")
            : new File(mc.getIntegratedServer().getWorld(0).getSaveHandler().getWorldDirectory(), "bbs/films");
        manager = new FilmManager(() -> root);
        film = manager.create(unique("film")); film.stampCreationTimeNow();
        runtime = new FilmRuntime(film);
    }
    public static StudioSession get()
    {
        if (current == null) current = new StudioSession();
        return current;
    }
    public static void unload()
    {
        if (current != null)
        {
            current.stopRecording();
            if (current.dirty) current.save();
            current.runtime.close(); current = null;
        }
    }
    public List<String> files()
    {
        List<String> files = new ArrayList<>(manager.getKeys()); files.removeIf(s -> s.endsWith("/")); Collections.sort(files); return files;
    }
    public String unique(String prefix)
    {
        String name = prefix; int i = 2;
        while (manager.exists(name)) name = prefix + "_" + i++;
        return name;
    }
    public static String validName(String name)
    {
        name = name.trim();
        if (!name.matches("[\\p{L}\\p{N}_ .-]+") || name.equals(".") || name.equals("..") || name.endsWith("."))
            throw new IllegalArgumentException("Use a file name without path separators");
        return name;
    }
    public boolean save()
    {
        if (!manager.save(film.getId(), film.toData().asMap())) { status = "Save failed (see log)"; return false; }
        dirty = false; status = "Saved: " + film.getId(); return true;
    }
    public void saveAs(String name)
    {
        name = validName(name);
        if (!name.equals(film.getId()) && manager.exists(name)) throw new IllegalArgumentException("A film with this name already exists");
        film.setId(name); save();
    }
    public void newFilm(String name)
    {
        name = validName(name);
        if (manager.exists(name)) throw new IllegalArgumentException("A film with this name already exists");
        if (dirty && !save()) return;
        stopRecording(); runtime.close();
        film = manager.create(name); film.stampCreationTimeNow(); runtime = new FilmRuntime(film);
        selectedReplay = ""; selectedClip = null; undo.clear(); redo.clear(); dirty = true; status = "New film: " + name;
    }
    public void load(String name)
    {
        if (dirty && !save()) return;
        Film loaded = manager.load(name);
        if (loaded == null) throw new IllegalStateException("Could not load film (see log)");
        stopRecording(); runtime.close(); film = loaded; runtime = new FilmRuntime(film);
        selectedReplay = film.replays.getList().isEmpty() ? "" : film.replays.getList().get(0).getId();
        selectedClip = null; undo.clear(); redo.clear(); dirty = false; status = "Opened: " + name;
    }
    public Replay replay() { return film.replays.getById(selectedReplay); }
    public Form previewForm()
    {
        ReplayEntity actor = runtime.actors.get(selectedReplay);
        return actor == null ? (replay() == null ? null : replay().form.get()) : actor.getForm();
    }
    public void checkpoint()
    {
        undo.push(film.toData().asMap()); while (undo.size() > 60) undo.removeLast(); redo.clear(); dirty = true;
    }
    public void changed()
    {
        int tick = runtime.tick; boolean camera = runtime.cameraEnabled;
        runtime.playing = false; runtime.seek(tick); runtime.camera(camera); dirty = true;
    }
    public void history(boolean forward)
    {
        Deque<MapType> from = forward ? redo : undo, to = forward ? undo : redo;
        if (from.isEmpty()) return;
        stopRecording(); to.push(film.toData().asMap()); film.fromData(from.pop()); selectedClip = null; changed();
        status = forward ? "Redo" : "Undo";
    }
    public Replay addActor(String model)
    {
        checkpoint();
        Replay replay = film.replays.addReplay(); ModelForm form = new ModelForm(); form.model.set(model);
        replay.form.set(form); replay.label.set("Actor " + film.replays.getList().size());
        EntityPlayer player = Minecraft.getMinecraft().player;
        Vec3d look = player.getLookVec();
        replay.keyframes.x.insert(0, player.posX + look.x * 3); replay.keyframes.y.insert(0, player.posY);
        replay.keyframes.z.insert(0, player.posZ + look.z * 3); replay.keyframes.grounded.insert(0, 1D);
        selectedReplay = replay.getId(); selectedClip = null; changed(); return replay;
    }
    public void duplicateActor()
    {
        Replay source = replay(); if (source == null) return;
        checkpoint(); Replay copy = film.replays.addReplay(); copy.fromData(source.toData());
        copy.label.set(source.getName() + " copy"); selectedReplay = copy.getId(); changed();
    }
    public void deleteActor()
    {
        Replay replay = replay(); if (replay == null) return;
        checkpoint(); film.replays.remove(replay); selectedReplay = ""; changed();
    }
    public KeyframeClip addCamera()
    {
        checkpoint(); KeyframeClip clip = new KeyframeClip(); clip.tick.set(runtime.tick); clip.duration.set(100);
        clip.title.set("Camera " + (film.camera.get().size() + 1));
        film.camera.addClip(clip); selectedClip = clip; captureCamera(clip, 0); changed(); return clip;
    }
    public void captureCamera(KeyframeClip clip, int time)
    {
        EntityPlayer player = Minecraft.getMinecraft().player;
        clip.x.insert(time, player.posX); clip.y.insert(time, player.posY + player.getEyeHeight()); clip.z.insert(time, player.posZ);
        clip.yaw.insert(time, (double) player.rotationYaw); clip.pitch.insert(time, (double) player.rotationPitch);
        clip.roll.insert(time, 0D); clip.fov.insert(time, (double) Minecraft.getMinecraft().gameSettings.fovSetting);
    }
    public void startRecording()
    {
        if (recording) { stopRecording(); return; }
        Replay replay = replay(); if (replay == null) replay = addActor("player/steve");
        checkpoint(); runtime.camera(false); runtime.playing = false;
        recordingReplay = replay; recorder = new MCEntity(Minecraft.getMinecraft().player);
        recording = true; Minecraft.getMinecraft().displayGuiScreen(null); status = "Recording: F6 to stop";
    }
    public void stopRecording()
    {
        if (!recording) return;
        recording = false; recordingReplay = null; recorder = null; changed(); status = "Recording captured";
    }
    public void update()
    {
        if (recording)
        {
            recordingReplay.keyframes.record(runtime.tick, recorder, null); recorder.update(); runtime.tick++; dirty = true;
        }
        else runtime.update();
    }
}
