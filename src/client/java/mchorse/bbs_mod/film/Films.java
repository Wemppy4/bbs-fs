package mchorse.bbs_mod.film;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Films
{
    /**
     * The shortest countdown a take that teleports the player onto the replay's mark
     * may have, in ticks. The teleport is a round trip through the server, and the
     * recorder must not sample the player before it lands.
     */
    private static final int TELEPORT_GRACE = 6;

    private List<BaseFilmController> controllers = new ArrayList<BaseFilmController>();
    private Recorder recorder;

    public Map<String, Map<String, Integer>> actors = new HashMap<>();

    /* Static helpers */

    public static void playFilm(String filmId, boolean withCamera)
    {
        if (ClientNetwork.isIsBBSModOnServer())
        {
            ClientNetwork.sendToggleFilm(filmId, withCamera);
        }
        else
        {
            if (BBSModClient.getFilms().has(filmId))
            {
                stopFilm(filmId);
            }
            else
            {
                ContentType.FILMS.getRepository().load(filmId, (data) ->
                {
                    MinecraftClient.getInstance().execute(() -> playFilm((Film) data, withCamera));
                });
            }
        }
    }

    public static void playFilm(Film film, boolean withCamera)
    {
        FirstPersonFilmController filmController = new FirstPersonFilmController(film);

        if (withCamera && !film.hasFirstPerson())
        {
            PlayCameraController controller = new PlayCameraController(film.camera);

            controller.getContext().entities.putAll(filmController.getEntities());
            BBSModClient.getCameraController().add(controller);
        }

        BBSModClient.getFilms().add(filmController);
    }

    public static void pauseFilm(String filmId)
    {
        if (ClientNetwork.isIsBBSModOnServer())
        {
            ClientNetwork.sendPauseFilm(filmId);
        }
        else
        {
            if (BBSModClient.getFilms().has(filmId))
            {
                togglePauseFilm(filmId);
            }
        }
    }

    public static void togglePauseFilm(String filmId)
    {
        BaseFilmController controller = BBSModClient.getFilms().getController(filmId);

        if (controller != null)
        {
            controller.togglePause();
        }
    }

    public static void stopFilm(String filmId)
    {
        Film film = BBSModClient.getFilms().remove(filmId);
        ICameraController current = BBSModClient.getCameraController().getCurrent();

        if (film != null && current instanceof PlayCameraController play)
        {
            if (play.getContext().clips == film.camera)
            {
                if (BBSModClient.getCameraController().remove(play) instanceof RunnerCameraController controller)
                {
                    controller.getContext().shutdown();
                }
            }
        }
    }

    /* Instance API */

    public BaseFilmController getController(String filmId)
    {
        for (BaseFilmController controller : this.controllers)
        {
            if (controller.film.getId().equals(filmId))
            {
                return controller;
            }
        }

        return null;
    }

    /**
     * Every film playing right now, in the order they were started.
     *
     * <p>The list itself stays BBS's: a controller is added and removed by the machinery
     * that owns it. Reading it used to need an accessor mixin, and an access widener could
     * not help — Loom applies one to Minecraft only, never to another mod.</p>
     */
    public List<BaseFilmController> getControllers()
    {
        return Collections.unmodifiableList(this.controllers);
    }

    public Recorder getRecorder()
    {
        return this.recorder;
    }

    public void startRecording(Film film, int replayId, int tick)
    {
        this.startRecording(film, replayId, tick, false);
    }

    /**
     * @param onMark whether the player should be put where the replay stands at
     *               {@code tick} first, if {@link BBSSettings#recordingTeleport} allows
     *               it. Only the film editor's "outside" button asks for this: recording
     *               straight from the world (the record key) is meant to start where the
     *               player is standing, the way it always has
     */
    public void startRecording(Film film, int replayId, int tick, boolean onMark)
    {
        Morph morph = Morph.getMorph(MinecraftClient.getInstance().player);
        Replay replay = CollectionUtils.getSafe(film.replays.getList(), replayId);

        this.recorder = new Recorder(film, morph == null ? null : morph.getForm(), replayId, tick);

        /* Stand on the mark. Recording started from the editor used to begin wherever
         * the player happened to be, so every take over an existing replay began with a
         * manual teleport (the film editor's teleport key) to the spot the replay itself
         * holds at that tick - now the take just begins there. Sent first, because the
         * teleport goes through the server and takes a couple of ticks to land */
        Vector3d mark = onMark && replay != null && BBSSettings.recordingTeleport.get()
            ? PlayerUtils.teleportToReplay(replay, tick)
            : null;

        if (mark != null)
        {
            /* Both clocks get the same countdown, so the server's action recorder
             * keeps starting alongside this one - the grace window is only there to
             * give the teleport its round trip even when the countdown is set to 0 */
            this.recorder.countdown = Math.max(this.recorder.countdown, TELEPORT_GRACE);
            this.recorder.awaitMark(mark);
        }

        if (ClientNetwork.isIsBBSModOnServer())
        {
            ClientNetwork.sendActionRecording(film.getId(), replayId, this.recorder.getTick(), this.recorder.countdown, true);
        }

        if (replay != null)
        {
            ClientNetwork.sendPlayerForm(replay.form.get());
        }
    }

    public Recorder stopRecording()
    {
        Recorder recorder = this.recorder;

        this.recorder = null;

        if (recorder != null)
        {
            for (KeyframeChannel<?> channel : recorder.keyframes.getChannels())
            {
                channel.simplify();
            }

            for (Recorder.RecordedMob mob : recorder.mobs)
            {
                for (KeyframeChannel<?> channel : mob.keyframes.getChannels())
                {
                    channel.simplify();
                }
            }

            if (ClientNetwork.isIsBBSModOnServer())
            {
                ClientNetwork.sendActionRecording(recorder.film.getId(), recorder.exception, recorder.initialTick, 0, false);
            }

            recorder.shutdown();
        }

        return recorder;
    }

    public void add(BaseFilmController controller)
    {
        this.controllers.add(controller);
    }

    /**
     * Leave the film standing in the world at {@code tick}, as the film editor was showing it &mdash;
     * see {@link FrozenFilmController}, including what {@code animated} means there. Any frame frozen
     * earlier for the same film is replaced.
     */
    public void freeze(Film film, int tick, boolean animated)
    {
        this.unfreeze(film.getId());
        this.controllers.add(new FrozenFilmController(film, tick, animated));
    }

    /**
     * Take down the film's frozen frame, leaving a film that is actually playing alone &mdash; both
     * live under the same id here, and only the frozen one is the editor's leftover.
     */
    public void unfreeze(String filmId)
    {
        this.controllers.removeIf((controller) ->
        {
            boolean frozen = controller instanceof FrozenFilmController && controller.film.getId().equals(filmId);

            if (frozen)
            {
                controller.shutdown();
            }

            return frozen;
        });
    }

    public boolean has(String filmId)
    {
        for (BaseFilmController controller : this.controllers)
        {
            if (controller.film.getId().equals(filmId))
            {
                return true;
            }
        }

        return false;
    }

    public Film remove(String id)
    {
        Iterator<BaseFilmController> it = this.controllers.iterator();

        while (it.hasNext())
        {
            BaseFilmController next = it.next();

            if (next.film.getId().equals(id))
            {
                next.shutdown();
                it.remove();

                return next.film;
            }
        }

        return null;
    }

    public void updateActors(String filmId, Map<String, Integer> actors)
    {
        this.actors.put(filmId, actors);
    }

    public void startRenderFrame(float transition)
    {
        if (this.recorder != null)
        {
            this.recorder.startRenderFrame(transition);
        }

        for (BaseFilmController controller : this.controllers)
        {
            controller.startRenderFrame(transition);
        }
    }

    public void update()
    {
        this.controllers.removeIf((film) ->
        {
            film.update();

            if (film.hasFinished())
            {
                film.shutdown();
            }

            return film.hasFinished();
        });

        if (this.recorder != null)
        {
            this.recorder.update();
        }
    }

    public void updateEndWorld()
    {
        for (BaseFilmController controller : this.controllers)
        {
            controller.updateEndWorld();
        }
    }

    public void render(WorldRenderContext context)
    {
        RenderSystem.enableDepthTest();

        for (BaseFilmController controller : this.controllers)
        {
            controller.render(context);
        }

        if (this.recorder != null)
        {
            this.recorder.render(context);
        }

        RenderSystem.disableDepthTest();
    }

    public void renderHud(Batcher2D batcher2D, float tickDelta)
    {
        Recorder recorder = BBSModClient.getFilms().getRecorder();

        if (recorder != null && BBSSettings.recordingOverlays.get())
        {
            String label = recorder.hasNotStarted() ?
                String.valueOf(TimeUtils.toSeconds(recorder.countdown)) :
                UIKeys.FILM_RECORDING.format(recorder.getTick()).get();
            int x = 5;
            int y = 5;
            int w = batcher2D.getFont().getWidth(label);

            batcher2D.box(x, y, x + 18 + w + 3, y + 16, Colors.A50);
            batcher2D.icon(Icons.SPHERE, Colors.RED | Colors.A100, x, y);
            batcher2D.textShadow(label, x + 18, y + 4);

            /* Render audio waveform (uses preview visibility setting) */
            if (BBSSettings.audioWaveformVisibleInPreview.get())
            {
                List<AudioClip> audioClips = new ArrayList<>();

                for (Clip clip : recorder.film.camera.get())
                {
                    if (clip instanceof AudioClip)
                    {
                        audioClips.add((AudioClip) clip);
                    }
                }

                int sw = MinecraftClient.getInstance().getWindow().getScaledWidth();
                int sh = MinecraftClient.getInstance().getWindow().getScaledHeight();
                w = (int) (sw * BBSSettings.audioWaveformWidth.get());
                x = sw / 2 - w / 2;
                y = sh / 2 + 100;

                int barH = BBSSettings.audioWaveformHeight.get();
                float playTick = recorder.getTick() + tickDelta;

                if (BBSSettings.audioWaveformPreviewCombined.get())
                {
                    AudioRenderer.renderPreviewCombined(batcher2D, audioClips, playTick, x, y, w, barH, sw, sh);
                }
                else
                {
                    AudioRenderer.renderAll(batcher2D, audioClips, playTick, x, y, w, barH, sw, sh);
                }
            }
        }
    }

    public void reset()
    {
        controllers.clear();

        recorder = null;
    }
}