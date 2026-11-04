package mchorse.bbs_mod.ui.sequence;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.sequence.FilmClip;
import mchorse.bbs_mod.film.sequence.Sequence;
import mchorse.bbs_mod.film.sequence.SequenceParts;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIWorldPreview;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.VideoExportSettings;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Records a sequence: first a piece of every film of this world whose piece is missing or stale
 * (each film once, however many clips show it), then the whole sequence out of the pieces. Or
 * only the pieces of the films asked for, when they are to be recorded anew.
 *
 * <p>While it runs, the editor around the preview is dimmed and can't be used; Escape stops it.
 */
public class UISequenceRecorder extends UIElement
{
    private static final float DIM_ALPHA = 0.6F;

    private final UISequencePanel panel;
    private final SequenceExportSession session;
    private final UIElement exit;

    private final List<FilmClip> jobs = new ArrayList<>();
    private boolean active;
    private boolean assemble;
    private int total;
    private int done;
    private String current = "";

    private VideoExportSettings settings;
    private String pictureStamp;

    public UISequenceRecorder(UISequencePanel panel)
    {
        this.panel = panel;
        this.session = new SequenceExportSession(panel);
        this.exit = new UIElement()
        {
            @Override
            protected boolean subKeyPressed(UIContext context)
            {
                if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
                {
                    UISequenceRecorder.this.cancel();

                    return true;
                }

                return super.subKeyPressed(context);
            }
        };

        this.noCulling();
    }

    public boolean isActive()
    {
        return this.active;
    }

    /**
     * Record the pieces that are missing or stale, then put the whole sequence together.
     */
    public void recordSequence()
    {
        Sequence sequence = this.panel.getData();

        if (sequence == null)
        {
            return;
        }

        List<FilmClip> stale = new ArrayList<>();

        for (FilmClip clip : sequence.getFilmClips())
        {
            if (clip.enabled.get() && clip.isInCurrentWorld() && !clip.film.get().isEmpty() && !this.panel.isRecorded(clip))
            {
                stale.add(clip);
            }
        }

        this.begin(stale, true);
    }

    /**
     * Record these clips' films anew, whatever their pieces are like: for the changes the film's
     * fingerprint doesn't see (the world, the models, the shaders).
     */
    public void recordParts(List<FilmClip> clips)
    {
        List<FilmClip> films = new ArrayList<>();

        for (FilmClip clip : clips)
        {
            if (clip.isInCurrentWorld() && !clip.film.get().isEmpty())
            {
                films.add(clip);
            }
        }

        if (!films.isEmpty())
        {
            this.begin(films, false);
        }
    }

    private void begin(List<FilmClip> clips, boolean assemble)
    {
        Sequence sequence = this.panel.getData();
        UIContext context = this.panel.getContext();

        if (sequence == null || context == null || this.active || this.session.isExporting() || BBSModClient.getVideoRecorder().isRecording())
        {
            return;
        }

        if (assemble && sequence.calculateDuration() <= 0)
        {
            return;
        }

        /* Each film once: its piece serves every clip that shows it */
        Set<String> films = new LinkedHashSet<>();

        this.jobs.clear();

        for (FilmClip clip : clips)
        {
            if (films.add(clip.film.get()))
            {
                this.jobs.add(clip);
            }
        }

        this.panel.save();
        this.settings = sequence.export.toExportSettings();
        this.pictureStamp = sequence.export.getPictureStamp();
        this.assemble = assemble;
        this.total = this.jobs.size();
        this.done = 0;
        this.active = true;

        context.menu.main.setEnabled(false);
        context.menu.overlay.add(this);
        context.menu.getRoot().add(this.exit);

        this.next();
    }

    public void cancel()
    {
        if (!this.active)
        {
            return;
        }

        this.session.setFinishedListener(null);

        if (this.session.isExporting())
        {
            this.session.cancel();
        }

        this.finish(true);
    }

    private void next()
    {
        if (!this.active)
        {
            return;
        }

        if (!this.jobs.isEmpty())
        {
            FilmClip clip = this.jobs.remove(0);

            this.current = UIFilmClipRenderer.getFilmName(clip);
            this.panel.getFilms().request(clip.film.get(), (film) -> this.recordPart(clip, film));

            return;
        }

        if (this.assemble)
        {
            this.assemble = false;
            this.recordSequence(this.panel.getData());

            return;
        }

        this.finish(false);
    }

    private void recordPart(FilmClip clip, Film film)
    {
        if (!this.active)
        {
            return;
        }

        UIContext context = this.panel.getContext();

        if (film == null)
        {
            context.notifyError(UIKeys.SEQUENCE_RECORD_NO_FILM.format(clip.film.get()));
            this.done += 1;
            this.next();

            return;
        }

        Sequence sequence = this.panel.getData();
        File folder = SequenceParts.getFolder(sequence.getId());

        folder.mkdirs();

        /* The film's previous piece makes way for the new one (the editor let go of it already) */
        String base = film.getId().replaceAll("[\\\\/:*?\"<>|]", "_");
        File previous = SequenceExportSession.findVideo(folder, base);

        this.panel.getRunner().shutdown();

        if (previous != null && !previous.delete())
        {
            base = SequenceExportSession.uniqueName(folder, base);
        }

        VideoExportSettings s = this.settings;
        VideoExportSettings part = new VideoExportSettings(s.width(), s.height(), s.frameRate(), s.motionBlur(), s.heldFrames(), s.shutter(),
            s.arguments(), s.argumentsAudio(), s.argumentsMux(), true, s.minecraftSounds(), folder.getAbsolutePath(), "{name}");
        String name = base;

        this.session.setFinishedListener((cancelled) -> this.onPartFinished(clip, film, cancelled));
        UIWorldPreview.applyExportSize(part);
        BBSRendering.scheduleAfterNextExportFrame(() ->
        {
            if (!this.active)
            {
                return;
            }

            if (!this.session.startPart(part, clip, film, name, BBSRendering.getTexture().id, BBSRendering.getVideoWidth(), BBSRendering.getVideoHeight()))
            {
                this.session.setFinishedListener(null);
                this.finish(true);
            }
        });
    }

    private void onPartFinished(FilmClip clip, Film film, boolean cancelled)
    {
        if (cancelled)
        {
            this.finish(true);

            return;
        }

        File file = this.session.getRecordedVideo();
        Sequence sequence = this.panel.getData();

        if (file != null && sequence != null)
        {
            Link link = SequenceParts.getLink(sequence.getId(), file.getName());
            String fingerprint = FilmClip.fingerprint(film);

            for (FilmClip other : sequence.getFilmClips())
            {
                if (other.isInCurrentWorld() && other.film.get().equals(clip.film.get()))
                {
                    other.setRecorded(link, fingerprint, this.pictureStamp);
                }
            }

            this.panel.refreshRecorded();
            this.panel.save();
        }

        this.done += 1;
        this.next();
    }

    /**
     * The whole sequence out of the pieces. A film without a piece (of another world, not
     * recorded there yet) comes out black, and the user hears which.
     */
    private void recordSequence(Sequence sequence)
    {
        UIContext context = this.panel.getContext();
        List<String> missing = new ArrayList<>();

        for (FilmClip clip : sequence.getFilmClips())
        {
            if (clip.enabled.get() && !clip.film.get().isEmpty() && !clip.hasPart() && !missing.contains(clip.film.get()))
            {
                missing.add(clip.film.get());
            }
        }

        if (!missing.isEmpty())
        {
            context.notifyError(UIKeys.SEQUENCE_RECORD_MISSING.format(String.join(", ", missing)));
        }

        VideoExportSettings s = this.settings;
        /* Nothing of the game is heard while the pieces are put together */
        VideoExportSettings whole = new VideoExportSettings(s.width(), s.height(), s.frameRate(), s.motionBlur(), s.heldFrames(), s.shutter(),
            s.arguments(), s.argumentsAudio(), s.argumentsMux(), s.exportAudio(), false, s.exportPath(), s.filenameFormat());

        this.current = "";
        this.session.setFinishedListener((cancelled) ->
        {
            if (cancelled)
            {
                this.finish(true);
            }
            else
            {
                File file = this.session.getRecordedVideo();

                if (file != null)
                {
                    this.panel.getContext().notifySuccess(UIKeys.SEQUENCE_RECORD_DONE.format(file.getName()));
                }

                this.next();
            }
        });
        UIWorldPreview.applyExportSize(whole);
        BBSRendering.scheduleAfterNextExportFrame(() ->
        {
            if (!this.active)
            {
                return;
            }

            if (!this.session.startSequence(whole, BBSRendering.getTexture().id, BBSRendering.getVideoWidth(), BBSRendering.getVideoHeight()))
            {
                this.session.setFinishedListener(null);
                this.finish(true);
            }
        });
    }

    private void finish(boolean cancelled)
    {
        if (!this.active)
        {
            return;
        }

        this.active = false;
        this.jobs.clear();

        UIContext context = this.panel.getContext();

        if (context != null)
        {
            context.render.postRunnable(this.exit::removeFromParent);
            context.render.postRunnable(this::removeFromParent);
            context.menu.main.setEnabled(true);

            if (cancelled)
            {
                context.notifyError(UIKeys.SEQUENCE_RECORD_CANCELLED);
            }
        }

        this.panel.preview.applySize();
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        Area viewport = this.panel.preview.getViewport();
        int color = Colors.setA(0, DIM_ALPHA);
        int w = context.menu.width;
        int h = context.menu.height;

        context.batcher.box(0, 0, w, viewport.y, color);
        context.batcher.box(0, viewport.ey(), w, h, color);
        context.batcher.box(0, viewport.y, viewport.x, viewport.ey(), color);
        context.batcher.box(viewport.ex(), viewport.y, w, viewport.ey(), color);

        this.renderProgress(context, viewport);
        this.session.update();
    }

    /**
     * What is being recorded and how far it got, over the frame's top left corner, and a line
     * along its bottom.
     */
    private void renderProgress(UIContext context, Area viewport)
    {
        float progress = this.session.getProgress();
        IKey label = this.session.isPart()
            ? UIKeys.SEQUENCE_RECORD_PART.format(this.done + 1, this.total, this.current)
            : UIKeys.SEQUENCE_RECORD_WHOLE;
        FontRenderer font = context.batcher.getFont();
        String text = label.get() + " " + (int) (progress * 100) + "%";

        context.batcher.textCard(font.limitToWidth(text, viewport.w - 10), viewport.x + 6, viewport.y + 6, Colors.WHITE, Colors.A50);
        context.batcher.box(viewport.x, viewport.ey() - 2, viewport.ex(), viewport.ey(), Colors.setA(0, 0.25F));
        context.batcher.box(viewport.x, viewport.ey() - 2, viewport.x + Math.round(viewport.w * progress), viewport.ey(), Colors.setA(Colors.RED, 1F));
    }
}
