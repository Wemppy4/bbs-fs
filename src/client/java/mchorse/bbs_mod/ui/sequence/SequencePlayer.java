package mchorse.bbs_mod.ui.sequence;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.sequence.FilmClip;
import mchorse.bbs_mod.film.sequence.Sequence;
import mchorse.bbs_mod.film.sequence.SequenceFilmController;
import mchorse.bbs_mod.network.ClientNetwork;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

/**
 * Plays the films of a sequence one after another in the world: whichever film of this world the
 * cursor is on plays live, on the tick of the film the clip shows. Its actions run on the server
 * the way the film editor runs them, so blocks get placed and actors spawned.
 *
 * <p>A film of another world, or one asked to play {@link #setPartsOnly(boolean) from its pieces
 * only}, plays nothing live: its recorded piece is shown instead (see
 * {@link SequenceCameraController}).
 *
 * <p>For recording a film's piece the player can also be told to play one film by itself, from
 * its start, whatever the cursor is on ({@link #solo(FilmClip, int)}).
 */
public class SequencePlayer
{
    private final UISequencePanel panel;

    private FilmClip clip;
    private Film film;
    private SequenceFilmController controller;
    private boolean playing;

    private FilmClip soloClip;
    private int soloTick;
    private boolean partsOnly;

    private boolean restartPending;
    private int lastRestartTick = -1;

    public SequencePlayer(UISequencePanel panel)
    {
        this.panel = panel;
    }

    /** The film clip playing live, or null when none is. */
    public FilmClip getClip()
    {
        return this.controller == null ? null : this.clip;
    }

    public Film getFilm()
    {
        return this.controller == null ? null : this.film;
    }

    public SequenceFilmController getController()
    {
        return this.controller;
    }

    public boolean isLive()
    {
        return this.controller != null;
    }

    /** The film's tick playing live. */
    public int getTick()
    {
        return this.controller == null ? 0 : this.controller.tick;
    }

    public boolean isSolo()
    {
        return this.soloClip != null;
    }

    public FilmClip getSoloClip()
    {
        return this.soloClip;
    }

    /**
     * Play this clip's film by itself from the given tick of the film, whatever the cursor is on;
     * null goes back to following the cursor.
     */
    public void solo(FilmClip clip, int tick)
    {
        this.soloClip = clip;
        this.soloTick = tick;

        this.sync();
    }

    public void setSoloTick(int tick)
    {
        this.soloTick = tick;
    }

    public boolean isPartsOnly()
    {
        return this.partsOnly;
    }

    /**
     * Show every film as its recorded piece, the films of this world too: what the recording
     * of the whole sequence puts together.
     */
    public void setPartsOnly(boolean partsOnly)
    {
        this.partsOnly = partsOnly;

        this.sync();
    }

    /**
     * Whether the given film clip plays live rather than as its piece.
     */
    public boolean canPlayLive(FilmClip clip)
    {
        return !this.partsOnly && clip.isInCurrentWorld() && !clip.film.get().isEmpty();
    }

    /**
     * Make the live film follow the cursor: start the film the cursor is on, stop the one it
     * left, and put the playing one on the tick the cursor shows.
     */
    public void sync()
    {
        Sequence sequence = this.panel.getData();
        boolean playing = this.panel.isRunning();
        FilmClip clip;
        int tick;

        if (this.soloClip != null)
        {
            clip = this.soloClip;
            tick = this.soloTick;
        }
        else
        {
            clip = sequence == null ? null : sequence.getFilmClipAt(this.panel.getCursor());
            tick = clip == null ? 0 : clip.toFilmTick(this.panel.getCursor());
        }

        Film film = clip != null && (this.soloClip != null || this.canPlayLive(clip)) ? this.panel.getFilms().get(clip.film.get()) : null;

        if (film != this.film)
        {
            this.stop();

            if (film != null)
            {
                this.start(film, tick, playing);
            }
        }

        this.clip = clip;

        if (this.controller == null)
        {
            return;
        }

        int previous = this.controller.tick;
        boolean jumped = tick != previous && tick != previous + 1;

        this.controller.tick = tick;

        if (playing != this.playing)
        {
            this.playing = playing;
            this.controller.playing = playing;
            this.notifyServer(playing ? ActionState.PLAY : ActionState.PAUSE, tick);
        }
        else if (jumped)
        {
            this.notifyServer(ActionState.SEEK, tick);
            this.restartPending = !playing;
        }
    }

    private void start(Film film, int tick, boolean playing)
    {
        this.film = film;
        this.playing = playing;
        this.controller = new SequenceFilmController(film, tick);
        this.controller.playing = playing;

        this.notifyServer(ActionState.RESTART, tick);

        if (playing)
        {
            this.notifyServer(ActionState.PLAY, tick);
        }

        this.panel.getRunner().resetFilm();
    }

    /**
     * Stop the live film, in the world and on the server.
     */
    public void stop()
    {
        if (this.controller != null)
        {
            this.notifyServer(ActionState.STOP, this.controller.tick);
            this.controller.shutdown();
            this.panel.getRunner().resetFilm();
        }

        this.controller = null;
        this.film = null;
        this.restartPending = false;
    }

    /**
     * Once a tick: the live film's actors move on, and after a jump the film's actions are
     * walked again up to where the cursor settled (see the film editor's "restart on seek").
     */
    public void update()
    {
        this.sync();

        if (this.controller == null)
        {
            return;
        }

        this.controller.update();

        int tick = this.controller.tick;
        boolean settled = tick == this.lastRestartTick;

        this.lastRestartTick = tick;

        if (this.restartPending && settled && !this.playing)
        {
            this.restartPending = false;

            if (BBSSettings.editorRestartOnSeek.get())
            {
                this.notifyServer(ActionState.RESTART, tick);
                this.controller.createEntities();
            }
        }
    }

    public void startRenderFrame(float transition)
    {
        this.sync();

        if (this.controller != null)
        {
            this.controller.startRenderFrame(transition);
        }
    }

    public void render(WorldRenderContext context)
    {
        if (this.controller != null)
        {
            this.controller.render(context);
        }
    }

    private void notifyServer(ActionState state, int tick)
    {
        if (this.film != null && ClientNetwork.isIsBBSModOnServer())
        {
            ClientNetwork.sendActionState(this.film.getId(), state, tick);
        }
    }
}
