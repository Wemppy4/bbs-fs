package dev.qualet.aihelper.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.SoundBuffer;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.markers.FilmMarker;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.film.markers.UIMarkerOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIRenderingContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.clips.Clip;
import org.lwjgl.opengl.GL11;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.function.Consumer;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/** Real clip widget and Value tree on disposable data. This is not a film editor. */
public final class OriginalClipsProbe extends UIBaseMenu implements IUIClipsDelegate
{
    private final Film film = new Film();
    private final Camera camera = new Camera();
    private final UIClips timeline = new UIClips(this, BBSMod.getFactoryCameraClips());
    private final Link sound = Link.assets("audio/__aihelper_clips.wav");
    private Clip selected;
    private int cursor;
    private int changes;
    private int fills;
    private int gestureEnds;
    private boolean running;
    private boolean flight;
    private final boolean oldSnapTicks = BBSSettings.editorSnapToTicks.get();
    private final boolean oldSnapRuler = BBSSettings.editorSnapToMarkers.get();
    private final boolean oldSnapFilm = BBSSettings.editorSnapToFilmMarkers.get();

    public OriginalClipsProbe() throws Exception
    {
        BBSSettings.editorSnapToTicks.set(true);
        BBSSettings.editorSnapToMarkers.set(false);
        BBSSettings.editorSnapToFilmMarkers.set(true);
        this.add("idle", "Перемещение", 10, 20, 0);
        this.add("idle", "Сосед", 60, 20, 0);
        Clip audio = this.add("audio", "Звуковая волна", 0, 80, 1);
        ((AudioClip) audio).audio.set(this.sound);
        audio.envelope.enabled.set(true);
        audio.envelope.fadeIn.set(8F);
        audio.envelope.fadeOut.set(12F);
        this.createWave();
        FilmMarker marker = this.film.markers.addMarker(30);
        marker.title.set("Монтаж");
        marker.color.set(0xffc825);
        this.timeline.relative(this.main).xy(8, 34).w(1F, -16).h(1F, -42);
        this.timeline.setClips(this.film.camera);
        this.main.add(this.timeline);
        this.film.postCallback((value, flag) -> this.changes++);
    }

    private Clip add(String type, String title, int tick, int duration, int layer)
    {
        Clip clip = BBSMod.getFactoryCameraClips().create(Link.bbs(type));
        clip.title.set(title);
        clip.tick.set(tick);
        clip.duration.set(duration);
        clip.layer.set(layer);
        this.film.camera.addClip(clip);
        return clip;
    }

    private void createWave() throws Exception
    {
        File file = BBSMod.getAssetsPath(this.sound.path);
        file.getParentFile().mkdirs();
        int sampleRate = 22050;
        int frames = sampleRate * 4;
        ByteBuffer bytes = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++)
        {
            double seconds = i / (double) sampleRate;
            double envelope = 0.25 + 0.65 * Math.abs(Math.sin(seconds * Math.PI));
            bytes.putShort((short) (Math.sin(seconds * 440 * Math.PI * 2) * envelope * 25000));
        }
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(bytes.array()), format, frames))
        {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file);
        }
        BBSModClient.getSounds().deleteSound(this.sound);
    }

    @Override public boolean canPause() { return false; }
    @Override public void resize(int width, int height)
    {
        super.resize(width, height);
        this.timeline.getXAxis().view(0, 120);
    }
    @Override protected void preRenderMenu(UIRenderingContext render)
    {
        render.batcher.box(0, 0, this.width, this.height, BBSSettings.baseSurface());
        render.batcher.text("BBS FS — исходные клипы и маркеры (проверка)", 8, 14, -1);
    }
    @Override public void onClose(UIBaseMenu nextMenu)
    {
        super.onClose(nextMenu);
        BBSSettings.editorSnapToTicks.set(this.oldSnapTicks);
        BBSSettings.editorSnapToMarkers.set(this.oldSnapRuler);
        BBSSettings.editorSnapToFilmMarkers.set(this.oldSnapFilm);
        BBSModClient.getSounds().stop(this.sound);
    }

    @Override public Film getFilm() { return this.film; }
    @Override public Camera getCamera() { return this.camera; }
    @Override public Clip getClip() { return this.selected; }
    @Override public void pickClip(Clip clip) { this.selected = clip; }
    @Override public int getCursor() { return this.cursor; }
    @Override public void setCursor(int tick) { this.cursor = Math.max(0, tick); }
    @Override public void setFlight(boolean flight) { this.flight = flight; }
    @Override public boolean isFlying() { return this.flight; }
    @Override public boolean isRunning() { return this.running; }
    @Override public void togglePlayback() { this.running = !this.running; }
    @Override public boolean canUseKeybinds() { return this.overlay.getChildren().isEmpty(); }
    @Override public void fillData() { this.fills++; }
    @Override public void embedView(UIElement element) { this.timeline.embedView(element); }
    @Override public void markLastUndoNoMerging() { this.gestureEnds++; }

    @Override public <T extends BaseValue> void editMultiple(T property, Consumer<T> consumer)
    {
        DataPath path = property.getRelativePath(this.getClip());
        if (path == null) { consumer.accept(property); return; }
        for (Clip clip : this.timeline.getClipsFromSelection())
        {
            BaseValue value = clip.findRecursively(path);
            if (value != null && value.getClass() == property.getClass()) consumer.accept((T) value);
        }
    }
    @Override public void editMultiple(ValueInt property, int value)
    {
        int delta = value - property.get();
        List<Clip> clips = this.timeline.getClipsFromSelection();
        for (Clip clip : clips)
        {
            ValueInt target = (ValueInt) clip.get(property.getId());
            if (target.get() + delta < target.getMin() || target.get() + delta > target.getMax()) return;
        }
        for (Clip clip : clips)
        {
            ValueInt target = (ValueInt) clip.get(property.getId());
            target.set(target.get() + delta);
        }
    }

    public JsonObject snapshot()
    {
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.addProperty("changes", this.changes);
        result.addProperty("fills", this.fills);
        result.addProperty("gestureEnds", this.gestureEnds);
        result.addProperty("cursor", this.cursor);
        result.addProperty("glError", GL11.glGetError());
        result.addProperty("width", this.width);
        result.addProperty("height", this.height);
        result.addProperty("mouseX", this.context.mouseX);
        result.addProperty("mouseY", this.context.mouseY);
        result.addProperty("contextMenu", this.context.contextMenu != null);
        result.addProperty("verticalScroll", this.timeline.vertical.getScroll());
        result.addProperty("zoom", this.timeline.getXAxis().getZoom());
        result.addProperty("shift", this.timeline.getXAxis().getShift());
        result.addProperty("tick0X", this.timeline.toGraphX(0));
        result.addProperty("tick100X", this.timeline.toGraphX(100));
        result.add("area", area(this.timeline.area));
        int layerHeight = this.timeline.toLayerY(0) - this.timeline.toLayerY(1);
        result.addProperty("layerHeight", layerHeight);
        JsonArray layers = new JsonArray();
        for (int i = 0; i < 20; i++)
        {
            JsonObject layer = new JsonObject();
            layer.addProperty("layer", i);
            layer.addProperty("y", this.timeline.toLayerY(i) + layerHeight / 2);
            layers.add(layer);
        }
        result.add("layers", layers);
        JsonArray clips = new JsonArray();
        for (Clip clip : this.film.camera.get())
        {
            int index = this.film.camera.getIndex(clip);
            JsonObject item = new JsonObject();
            item.addProperty("index", index);
            item.addProperty("type", BBSMod.getFactoryCameraClips().getType(clip).toString());
            item.addProperty("title", clip.title.get());
            item.addProperty("tick", clip.tick.get());
            item.addProperty("duration", clip.duration.get());
            item.addProperty("layer", clip.layer.get());
            item.addProperty("enabled", clip.enabled.get());
            item.addProperty("selected", this.timeline.hasSelected(index));
            item.addProperty("x", this.timeline.toGraphX(clip.tick.get()));
            item.addProperty("right", this.timeline.toGraphX(clip.tick.get() + clip.duration.get()));
            item.addProperty("y", this.timeline.toLayerY(clip.layer.get()) + layerHeight / 2);
            clips.add(item);
        }
        result.add("clips", clips);
        JsonArray markers = new JsonArray();
        for (FilmMarker marker : this.film.markers.getList())
        {
            JsonObject item = new JsonObject();
            item.addProperty("id", marker.getId());
            item.addProperty("title", marker.title.get());
            item.addProperty("tick", marker.tick.get());
            item.addProperty("color", marker.color.get());
            item.addProperty("x", this.timeline.toGraphX(marker.tick.get()));
            item.addProperty("y", this.timeline.area.y + 5);
            markers.add(item);
        }
        result.add("markers", markers);
        if (this.context.contextMenu instanceof UISimpleContextMenu)
        {
            UISimpleContextMenu menu = (UISimpleContextMenu) this.context.contextMenu;
            JsonArray actions = new JsonArray();
            for (int i = 0; i < menu.actions.getList().size(); i++)
            {
                ContextAction action = menu.actions.getList().get(i);
                JsonObject item = new JsonObject();
                item.addProperty("label", action.label.get());
                item.addProperty("x", menu.actions.area.x + 10);
                item.addProperty("y", menu.actions.area.y + i * menu.actions.rowHeight() + menu.actions.rowHeight() / 2 - (int) menu.actions.scroll.getScroll());
                actions.add(item);
            }
            result.add("menu", actions);
        }
        List<UIMarkerOverlayPanel> editors = this.overlay.getChildren(UIMarkerOverlayPanel.class);
        if (!editors.isEmpty())
        {
            UIMarkerOverlayPanel editor = editors.get(0);
            JsonObject item = new JsonObject();
            item.add("title", area(editor.title.area));
            item.add("tick", area(editor.tick.area));
            item.add("remove", area(editor.remove.area));
            item.add("close", area(editor.close.area));
            result.add("markerEditor", item);
        }
        SoundBuffer buffer = BBSModClient.getSounds().get(this.sound, true);
        result.addProperty("waveform", buffer != null && buffer.getWaveform() != null && buffer.getWaveform().isCreated());
        result.addProperty("waveDuration", buffer == null ? 0 : buffer.getDuration());
        Film restored = new Film();
        restored.fromData(this.film.toData());
        result.addProperty("roundtrip", restored.toData().equals(this.film.toData()));
        return result;
    }

    private static JsonObject area(Area area)
    {
        JsonObject result = new JsonObject();
        result.addProperty("x", area.x); result.addProperty("y", area.y);
        result.addProperty("w", area.w); result.addProperty("h", area.h);
        return result;
    }
}
