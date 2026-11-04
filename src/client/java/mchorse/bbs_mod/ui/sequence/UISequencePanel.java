package mchorse.bbs_mod.ui.sequence;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.sequence.FilmClip;
import mchorse.bbs_mod.film.sequence.Sequence;
import mchorse.bbs_mod.film.sequence.SequenceFilms;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.film.FrameOverlays;
import mchorse.bbs_mod.ui.film.IUIClipsOwner;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.UIUndoKeys;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.WorldScope;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.undo.IUndo;
import mchorse.bbs_mod.utils.undo.UndoManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.utils.colors.Colors;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The sequence editor: films of a video put in order on one timeline, the way Unreal's Sequencer
 * lines up its shots. The films of this world are listed on the left, the preview shows the
 * sequence the way it is recorded, the timeline under it holds the films with the sound, the
 * pictures and the subtitles over them, and the picked clip's panel is on the right.
 */
public class UISequencePanel extends UIDataDashboardPanel<Sequence> implements IUIClipsOwner
{
    private static final int LEFT_WIDTH = 160;
    private static final int RIGHT_WIDTH = 200;
    private static final float PREVIEW_HEIGHT = 0.6F;

    public UIElement left;
    public UISearchList<String> films;
    public UISequencePreview preview;
    public UIClipsPanel timeline;
    public UIElement clipArea;
    public UISequenceRecorder recorder;

    private final Camera camera = new Camera();
    private final SequenceCameraController runner = new SequenceCameraController(this);
    private final SequenceFilms filmCache = new SequenceFilms();
    private final SequencePlayer player = new SequencePlayer(this);

    private int cursor;
    private boolean playing;

    /** Whether each film clip's piece is current, asked for on every frame and so checked once a second */
    private final Map<FilmClip, Boolean> recorded = new IdentityHashMap<>();
    private long recordedCheckedAt;

    /* Undo by whole-sequence snapshots: a sequence is a handful of clips, so comparing its data
     * twice a second costs nothing, and any change to it becomes one step. */
    private UndoManager<Sequence> undoManager;
    private MapType undoSnapshot;
    private final Timer undoCheckTimer = new Timer(400);
    private boolean applyingUndo;

    public UISequencePanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.left = new UIElement();
        this.left.relative(this.editor).w(LEFT_WIDTH).h(1F);

        this.films = new UISearchList<>(new UIStringList(null)
        {
            @Override
            protected boolean onOpen(String item)
            {
                UISequencePanel.this.addFilmClip(item);

                return true;
            }
        });
        this.films.label(UIKeys.GENERAL_SEARCH).full(this.left).x(4).y(4).w(1F, -8).h(1F, -8);
        this.films.list.tooltip(UIKeys.SEQUENCE_FILMS_ADD_TOOLTIP);
        this.films.list.context((menu) ->
        {
            String film = this.films.list.getCurrentFirst();

            if (film != null)
            {
                menu.action(Icons.ADD, UIKeys.SEQUENCE_FILMS_ADD, () -> this.addFilmClip(film));
            }
        });
        this.left.add(this.films);

        this.preview = new UISequencePreview(this);
        this.preview.relative(this.editor).x(LEFT_WIDTH).w(1F, -LEFT_WIDTH - RIGHT_WIDTH).h(PREVIEW_HEIGHT);

        this.clipArea = new UIElement();
        this.clipArea.relative(this.editor).x(1F, -RIGHT_WIDTH).w(RIGHT_WIDTH).h(1F);

        this.timeline = new UIClipsPanel(this, BBSModClient.getSequenceClipPalette()).target(this.clipArea);
        this.timeline.relative(this.editor).x(LEFT_WIDTH).y(PREVIEW_HEIGHT).w(1F, -LEFT_WIDTH - RIGHT_WIDTH).h(1F - PREVIEW_HEIGHT);

        this.editor.add(this.left, this.preview, this.timeline, this.clipArea);

        this.recorder = new UISequenceRecorder(this);

        this.timeline.clips.context((menu) ->
        {
            List<FilmClip> films = new ArrayList<>();

            for (Clip clip : this.timeline.clips.getClipsFromSelection())
            {
                if (clip instanceof FilmClip film && film.isInCurrentWorld() && !film.film.get().isEmpty())
                {
                    films.add(film);
                }
            }

            if (!films.isEmpty())
            {
                menu.action(Icons.REFRESH, UIKeys.SEQUENCE_RERECORD, () -> this.recorder.recordParts(films));
            }
        });

        this.overlay.namesList.setFileIcon(Icons.GALLERY);

        IKey editor = UIKeys.CAMERA_EDITOR_KEYS_EDITOR_TITLE;

        this.keys().register(Keys.PLAUSE, this::togglePlayback).active(() -> this.data != null).category(editor);
        this.keys().register(Keys.NEXT, () -> this.setCursor(this.getCursor() + 1)).active(() -> this.data != null).category(editor);
        this.keys().register(Keys.PREV, () -> this.setCursor(this.getCursor() - 1)).active(() -> this.data != null).category(editor);
        this.keys().register(Keys.NEXT_CLIP, () -> this.setCursor(this.data.clips.findNextTick(this.getCursor()))).active(() -> this.data != null).category(editor);
        this.keys().register(Keys.PREV_CLIP, () -> this.setCursor(this.data.clips.findPreviousTick(this.getCursor()))).active(() -> this.data != null).category(editor);
        this.keys().register(Keys.JUMP_FORWARD, () -> this.setCursor(this.getCursor() + BBSSettings.editorJump.get())).active(() -> this.data != null).category(editor);
        this.keys().register(Keys.JUMP_BACKWARD, () -> this.setCursor(this.getCursor() - BBSSettings.editorJump.get())).active(() -> this.data != null).category(editor);

        this.add(new UIUndoKeys(this::undo, this::redo).full(this));

        this.setUndoId("sequence_panel");
        this.timeline.setUndoId("sequence_timeline");

        this.fill(null);
        this.mountLanding();

        this.onAppear(this::enterEditing);
        this.onDisappear(this::leaveEditing);
    }

    public SequenceFilms getFilms()
    {
        return this.filmCache;
    }

    public SequencePlayer getPlayer()
    {
        return this.player;
    }

    /* Being on screen */

    private void enterEditing()
    {
        /* See UIFilmPanel.enterEditing: the dashboard may be built without being shown */
        if (this.getContext() == null)
        {
            return;
        }

        /* A film may have been edited since this editor was last looked at */
        this.filmCache.clear();
        this.refreshRecorded();
        this.requestFilms();

        BBSRendering.setCustomSize(true);
        MorphRenderer.hidePlayer = true;

        BBSModClient.getCameraController().add(this.runner);
        this.preview.applySize();
    }

    private void leaveEditing()
    {
        this.recorder.cancel();
        this.setPlaying(false);
        this.player.stop();

        BBSRendering.setCustomSize(false);
        MorphRenderer.hidePlayer = false;

        BBSModClient.getCameraController().remove(this.runner);
        this.runner.shutdown();
    }

    @Override
    public boolean canPause()
    {
        return false;
    }

    @Override
    public boolean canRefresh()
    {
        return false;
    }

    /* The films of this world */

    private void requestFilms()
    {
        ContentType.FILMS.getRepository().requestKeys((keys) ->
        {
            List<String> films = new ArrayList<>();

            for (String key : keys)
            {
                if (!key.endsWith("/"))
                {
                    films.add(key);
                }
            }

            this.films.list.clear();
            this.films.list.add(films);
            this.films.list.sort();
            this.films.filter(this.films.search.getText(), true);
        });
    }

    /**
     * Put a film of this world at the end of the sequence's films, as long as the film runs.
     */
    public void addFilmClip(String id)
    {
        if (this.data == null || id == null || id.isEmpty())
        {
            return;
        }

        this.filmCache.request(id, (film) ->
        {
            if (film == null || this.data == null)
            {
                return;
            }

            FilmClip clip = new FilmClip();
            int end = 0;

            for (FilmClip other : this.data.getFilmClips())
            {
                if (other.layer.get() == 0)
                {
                    end = Math.max(end, other.tick.get() + other.duration.get());
                }
            }

            clip.film.set(id);
            clip.world.set(WorldScope.current());
            clip.tick.set(end);
            clip.duration.set(SequenceFilms.getDuration(film));

            this.data.clips.addClip(clip);
            this.timeline.clips.setSelected(clip);
            this.timeline.pickClip(clip);
            this.setCursor(end);
        });
    }

    /**
     * Open the clip's film in the film editor.
     */
    public void openFilm(FilmClip clip)
    {
        if (clip == null || !clip.isInCurrentWorld() || clip.film.get().isEmpty())
        {
            return;
        }

        UIFilmPanel panel = this.dashboard.getPanel(UIFilmPanel.class);
        SequenceSound sound = new SequenceSound(this.data, clip);

        this.save();
        panel.setSequenceSound(sound.isEmpty() ? null : sound);
        this.dashboard.setPanel(panel);
        panel.pickData(clip.film.get());

        if (!sound.isEmpty())
        {
            panel.getContext().notifyInfo(UIKeys.SEQUENCE_SOUND_IN_FILM.format(this.data.getId()));
        }
    }

    /**
     * Double clicking a film clip opens its film.
     */
    @Override
    public void openClip(Clip clip)
    {
        if (!(clip instanceof FilmClip film) || film.film.get().isEmpty())
        {
            return;
        }

        if (film.isInCurrentWorld())
        {
            this.openFilm(film);
        }
        else
        {
            this.getContext().notifyError(UIKeys.SEQUENCE_CLIP_OTHER_WORLD);
        }
    }

    /* Recorded pieces */

    /**
     * Whether the clip's recorded piece shows its film as it is now, at the sequence's picture
     * settings. A film of another world can't be looked at, so for it only the settings count.
     */
    public boolean isRecorded(FilmClip clip)
    {
        if (this.data == null)
        {
            return false;
        }

        long now = System.currentTimeMillis();

        if (now - this.recordedCheckedAt > 1000L)
        {
            this.recorded.clear();
            this.recordedCheckedAt = now;
        }

        return this.recorded.computeIfAbsent(clip, this::checkRecorded);
    }

    private boolean checkRecorded(FilmClip clip)
    {
        String picture = this.data.export.getPictureStamp();

        if (!clip.isInCurrentWorld())
        {
            return clip.isRecorded(null, picture);
        }

        String fingerprint = this.filmCache.getFingerprint(clip.film.get());

        return fingerprint != null && clip.isRecorded(fingerprint, picture);
    }

    /**
     * Forget what is known about the pieces, after they were recorded or the films changed.
     */
    public void refreshRecorded()
    {
        this.recorded.clear();
    }

    /* Data */

    @Override
    public ContentType getType()
    {
        return ContentType.SEQUENCES;
    }

    @Override
    public IKey getTitle()
    {
        return UIKeys.SEQUENCE_TITLE;
    }

    @Override
    public IKey getCreateLabel()
    {
        return UIKeys.SEQUENCE_LANDING_NEW;
    }

    @Override
    public IKey getListLabel()
    {
        return UIKeys.SEQUENCE_LANDING_LIST;
    }

    @Override
    public IKey getNewTabLabel()
    {
        return UIKeys.SEQUENCE_TABS_NEW_TAB;
    }

    @Override
    public Icon getTabIcon(String id)
    {
        return id == null ? Icons.SEARCH : Icons.GALLERY;
    }

    @Override
    protected void fillData(Sequence data)
    {
        this.setPlaying(false);

        /* A fresh sequence starts a fresh history; the re-bind an undo itself performs keeps it. */
        if (!this.applyingUndo)
        {
            this.undoManager = data == null ? null : new UndoManager<>(100);
            this.undoSnapshot = data == null ? null : data.toData().asMap();
            this.cursor = 0;
        }

        this.player.stop();
        this.runner.shutdown();
        this.runner.setWork(data == null ? null : data.clips);
        this.timeline.setClips(data == null ? null : data.clips);
        this.timeline.pickClip(null);
    }

    /* Undo */

    public void undo()
    {
        if (this.data != null && this.undoManager != null && this.undoManager.undo(this.data))
        {
            UIUtils.playClick();
        }
    }

    public void redo()
    {
        if (this.data != null && this.undoManager != null && this.undoManager.redo(this.data))
        {
            UIUtils.playClick();
        }
    }

    private void checkUndo()
    {
        if (this.data == null || this.undoManager == null || !this.undoCheckTimer.checkRepeat())
        {
            return;
        }

        MapType current = this.data.toData().asMap();

        if (!current.equals(this.undoSnapshot))
        {
            this.undoManager.pushUndo(new SnapshotUndo(this.undoSnapshot, current));
            this.undoSnapshot = current;
        }
    }

    private void applySnapshot(MapType state)
    {
        this.applyingUndo = true;
        this.data.fromData(state.copy());
        this.fillData(this.data);
        this.undoSnapshot = state;
        this.applyingUndo = false;
    }

    /* Playback */

    @Override
    public int getCursor()
    {
        return this.cursor;
    }

    @Override
    public void setCursor(int tick)
    {
        this.cursor = Math.max(0, tick);
    }

    @Override
    public float getCursor(float transition)
    {
        return this.cursor + (this.playing ? transition : 0F);
    }

    @Override
    public boolean isRunning()
    {
        return this.playing;
    }

    @Override
    public void togglePlayback()
    {
        if (this.data == null)
        {
            return;
        }

        if (!this.playing && this.cursor >= this.data.calculateDuration())
        {
            this.setCursor(0);
        }

        this.setPlaying(!this.playing);
    }

    protected void setPlaying(boolean playing)
    {
        this.playing = playing;
    }

    @Override
    public void update()
    {
        super.update();

        if (this.playing && this.player.isSolo())
        {
            /* A film recorded by itself runs on its own time, not on the sequence's */
            this.player.setSoloTick(this.player.getSoloTick() + 1);
        }
        else if (this.playing && this.data != null)
        {
            if (this.cursor + 1 >= this.data.calculateDuration())
            {
                this.setPlaying(false);
            }
            else
            {
                this.cursor += 1;
            }
        }

        this.player.update();
        this.checkUndo();
    }

    @Override
    public void startRenderFrame(float tickDelta)
    {
        super.startRenderFrame(tickDelta);

        this.player.startRenderFrame(tickDelta);
    }

    @Override
    public void renderInWorld(WorldRenderContext context)
    {
        super.renderInWorld(context);

        RenderSystem.enableDepthTest();
        this.player.render(context);
        RenderSystem.disableDepthTest();
    }

    /**
     * What goes over the rendered frame: the sequence's pictures and subtitles. Called by the
     * renderer once the world is drawn, so it is in the frame that gets recorded.
     */
    public void renderFrameOverlays(MatrixStack stack, Batcher2D batcher)
    {
        if (this.data == null)
        {
            return;
        }

        if (this.player.isLive())
        {
            FrameOverlays.render(stack, batcher, this.runner.getFilmContext());

            /* The film clip's fades take the film to black */
            FilmClip clip = this.player.getClip();

            if (!this.player.isSolo())
            {
                float factor = clip.envelope.factorEnabled(clip.duration.get(), this.getCursor(MinecraftClient.getInstance().getTickDelta()) - clip.tick.get());

                if (factor < 1F)
                {
                    fillFrame(batcher, Colors.setA(0, 1F - factor));
                }
            }
        }
        else
        {
            /* Nothing of the world belongs in the frame: a film shown as its piece, or none */
            fillFrame(batcher, Colors.A100);
        }

        if (!this.player.isSolo())
        {
            FrameOverlays.render(stack, batcher, this.runner.getContext());
        }
    }

    /**
     * Cover the whole rendered frame with the colour.
     */
    private static void fillFrame(Batcher2D batcher, int color)
    {
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());

        RenderSystem.setProjectionMatrix(new Matrix4f().ortho(0, 1, 1, 0, -100, 100), VertexSorter.BY_Z);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        batcher.box(0, 0, 1, 1, color);
        batcher.flush();

        RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_Z);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }

    /**
     * What the preview says over a frame that has no film to show: the film under the cursor
     * is of another world and has no piece yet. Null when there is nothing to say.
     */
    public String getPlaceholder()
    {
        if (this.data == null || this.player.isLive() || this.player.isSolo())
        {
            return null;
        }

        FilmClip clip = this.data.getFilmClipAt(this.cursor);

        if (clip == null || clip.film.get().isEmpty() || clip.getPartLink() != null || this.player.canPlayLive(clip))
        {
            return null;
        }

        return UIKeys.SEQUENCE_NOT_RECORDED.format(UIFilmClipRenderer.getFilmName(clip), clip.world.get()).get();
    }

    /* IUIClipsOwner */

    @Override
    public Film getFilm()
    {
        return null;
    }

    @Override
    public Camera getCamera()
    {
        return this.camera;
    }

    @Override
    public SequenceCameraController getRunner()
    {
        return this.runner;
    }

    @Override
    public boolean isFlying()
    {
        return false;
    }

    @Override
    public void setFlight(boolean flight)
    {}

    @Override
    public boolean canUseKeybinds()
    {
        return this.data != null;
    }

    @Override
    public void markLastUndoNoMerging()
    {}

    @Override
    public void pickClip(Clip clip, UIClipsPanel panel)
    {}

    /** One undoable step: the sequence's data before and after. */
    private class SnapshotUndo implements IUndo<Sequence>
    {
        private final MapType before;
        private final MapType after;

        public SnapshotUndo(MapType before, MapType after)
        {
            this.before = before;
            this.after = after;
        }

        @Override
        public IUndo<Sequence> noMerging()
        {
            return this;
        }

        @Override
        public boolean isMergeable(IUndo<Sequence> undo)
        {
            return false;
        }

        @Override
        public void merge(IUndo<Sequence> undo)
        {}

        @Override
        public void undo(Sequence context)
        {
            UISequencePanel.this.applySnapshot(this.before);
        }

        @Override
        public void redo(Sequence context)
        {
            UISequencePanel.this.applySnapshot(this.after);
        }
    }
}
