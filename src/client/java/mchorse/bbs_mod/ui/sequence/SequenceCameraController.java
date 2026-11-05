package mchorse.bbs_mod.ui.sequence;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.clips.misc.VideoClientClip;
import mchorse.bbs_mod.camera.clips.misc.VideoClip;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.sequence.FilmClip;
import mchorse.bbs_mod.film.sequence.Sequence;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * What a sequence plays on the camera while its editor is on screen.
 *
 * <p>The film playing live (see {@link SequencePlayer}) places the camera with its own camera
 * clips, on the film's tick, and plays its own sound and pictures (in {@link #getFilmContext()}).
 * A film shown as its recorded piece is a full screen video clip made for it, under the
 * sequence's own clips - the sound, the pictures and the subtitles over all the films - which
 * play on the sequence's tick (in {@link #getContext()}).
 */
public class SequenceCameraController extends CameraWorkCameraController
{
    private final UISequencePanel panel;

    private final CameraClipContext filmContext = new CameraClipContext();
    private final Position filmPosition = new Position();
    private boolean filmAudio = true;

    /** The film clips shown as their recorded piece, each as a video clip of the piece */
    private final Map<FilmClip, VideoClientClip> parts = new IdentityHashMap<>();

    public SequenceCameraController(UISequencePanel panel)
    {
        this.panel = panel;
        this.context.playing = false;
    }

    /** The live film's camera clips, with their sound and pictures. */
    public CameraClipContext getFilmContext()
    {
        return this.filmContext;
    }

    /**
     * The live film changed: what the previous one was playing (its sound, its videos) stops.
     */
    public void resetFilm()
    {
        this.filmContext.shutdown();
        this.filmContext.clipData.clear();
        this.filmContext.clips = null;
    }

    /**
     * Everything stops: the live film's sound, the pieces and the sequence's own sound.
     */
    public void shutdown()
    {
        this.resetFilm();
        this.context.shutdown();

        for (VideoClientClip part : this.parts.values())
        {
            part.shutdown(this.context);
        }

        this.parts.clear();
        this.context.clipData.clear();
    }

    /**
     * The pieces' video clips, the way they are put over each other in the frame: for the
     * recording's sound track.
     */
    public List<VideoClientClip> getParts()
    {
        return new ArrayList<>(this.parts.values());
    }

    @Override
    public void setup(Camera camera, float transition)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player != null)
        {
            camera.set(mc.player, MathUtils.toRad(mc.options.getFov().getValue()));
        }

        boolean playing = this.panel.isRunning();
        float partial = playing ? transition : 0F;
        SequencePlayer player = this.panel.getPlayer();

        this.filmContext.clipData.clear();

        if (player.isLive())
        {
            this.applyFilm(camera, player, playing, partial);
        }

        this.context.clipData.clear();
        this.context.playing = playing;

        Sequence sequence = this.panel.getData();

        if (sequence == null || player.isSolo())
        {
            return;
        }

        int ticks = this.panel.getCursor();

        /* A piece under the film playing live would cover it */
        int minLayer = player.isLive() ? player.getClip().layer.get() + 1 : Integer.MIN_VALUE;

        this.context.setup(ticks, partial);
        this.updateParts(sequence, player, ticks, minLayer);

        /* The pieces first, lower layers under higher ones: the sequence's pictures go over them */
        List<VideoClientClip> parts = new ArrayList<>(this.parts.values());

        parts.sort((a, b) -> Integer.compare(a.layer.get(), b.layer.get()));

        for (VideoClientClip part : parts)
        {
            this.context.apply(part, this.position);
        }

        for (Clip clip : sequence.clips.getClips(ticks))
        {
            if (!(clip instanceof FilmClip))
            {
                this.context.apply(clip, this.position);
            }
        }

        this.context.currentLayer = 0;

        AudioClientClip.manageSounds(this.context);
    }

    /**
     * The live film's camera clips on the film's tick: the camera goes where they put it, and
     * their sound plays unless the clip has the film's sound off.
     */
    private void applyFilm(Camera camera, SequencePlayer player, boolean playing, float partial)
    {
        Film film = player.getFilm();
        boolean audio = player.isSolo() || player.getClip().filmAudio.get();
        int tick = player.getTick();

        if (audio != this.filmAudio)
        {
            this.filmContext.shutdown();
            this.filmAudio = audio;
        }

        this.filmContext.clips = film.camera;
        this.filmContext.entities = player.getController().getEntities();
        this.filmContext.playing = playing;
        this.filmContext.setup(tick, partial);
        this.filmPosition.set(camera);

        for (Clip clip : film.camera.getClips(tick))
        {
            if (!audio && clip instanceof AudioClip && !(clip instanceof VideoClip))
            {
                continue;
            }

            this.filmContext.apply(clip, this.filmPosition);
        }

        this.filmContext.currentLayer = 0;

        AudioClientClip.manageSounds(this.filmContext);

        this.filmPosition.apply(camera);
    }

    /**
     * Keep a full screen video clip of its piece for every film clip shown as one, following the
     * film clip's place, trim and sound; the ones no longer needed stop.
     */
    private void updateParts(Sequence sequence, SequencePlayer player, int ticks, int minLayer)
    {
        List<FilmClip> needed = new ArrayList<>();

        for (FilmClip clip : sequence.getFilmClips())
        {
            if (clip.enabled.get() && !player.canPlayLive(clip) && clip.getPartLink() != null && clip.layer.get() >= minLayer)
            {
                needed.add(clip);
            }
        }

        Iterator<Map.Entry<FilmClip, VideoClientClip>> it = this.parts.entrySet().iterator();

        while (it.hasNext())
        {
            Map.Entry<FilmClip, VideoClientClip> entry = it.next();

            if (!needed.contains(entry.getKey()))
            {
                entry.getValue().shutdown(this.context);
                it.remove();
            }
        }

        for (FilmClip clip : needed)
        {
            VideoClientClip part = this.parts.computeIfAbsent(clip, (k) -> new VideoClientClip());
            Link link = clip.getPartLink();

            if (!link.equals(part.audio.get()))
            {
                part.shutdown(this.context);
                part.audio.set(link);
            }

            part.layer.set(clip.layer.get());
            part.tick.set(clip.tick.get());
            part.duration.set(clip.duration.get());
            part.offset.set(clip.offset.get());
            part.volume.set(clip.filmAudio.get() ? 1F : 0F);
            part.fullscreen.set(true);

            /* The fades only matter on the clip being shown */
            if (clip.isInside(ticks))
            {
                part.envelope.copy(clip.envelope);
            }
        }
    }
}
