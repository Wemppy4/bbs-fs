package mchorse.bbs_mod.film;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;

import java.util.Map;

/**
 * The film's frame left standing in the world after the film editor's screen goes away.
 *
 * <p>It is the stopped editor without the editor: the same replay entities, the same properties, at
 * the one tick the cursor was sitting on. The tick itself never moves &mdash; nothing here advances
 * it &mdash; so what stays in the world is the frame that was on screen.
 *
 * <p>Whether the forms keep <em>living</em> on that tick is the editor's own call, carried over in
 * {@code animated}: the film editor's "freeze when paused" toggle decides the very same thing for a
 * stopped timeline (see {@code UIFilmController#isPaused}, which is that toggle inverted, and how
 * {@code FilmEditorController} reads it through {@code isPlaying}). Frozen, the frame is a statue;
 * animated, the forms run their own clock and idle away exactly as they did with the editor open.
 *
 * <p>Deliberately blind to the film's actor entities ({@link #getActors()} returns {@code null}):
 * those are real entities and the player, steered by the server only while the editor holds them,
 * and a frozen frame that kept driving them would keep re-applying a stale tick's position and
 * rotation &mdash; pinning the player in place after they left the UI. The replays themselves stay:
 * an actor's body is drawn from its keyframes like every other replay's, so a replay flagged as an
 * actor is left standing in the frame even though its entity left with the editor.
 */
public class FrozenFilmController extends BaseFilmController
{
    private final int tick;
    private final boolean animated;

    public FrozenFilmController(Film film, int tick, boolean animated)
    {
        super(film);

        this.tick = tick;
        this.animated = animated;

        this.createEntities();
    }

    @Override
    public Map<String, Integer> getActors()
    {
        return null;
    }

    @Override
    public int getTick()
    {
        return this.tick;
    }

    /**
     * When frozen, the pose belongs to the tick and not to the clock: updating the form would walk
     * its animation states forward and the frame would drift off what the editor was showing.
     */
    @Override
    protected void updateEntityAndForm(IEntity entity, int tick)
    {
        if (this.animated)
        {
            super.updateEntityAndForm(entity, tick);
        }
    }

    @Override
    protected void applyReplay(Replay replay, int ticks, IEntity entity)
    {
        super.applyReplay(replay, ticks, entity);

        if (!this.animated)
        {
            /* Nothing moves, so the previous tick is this tick — otherwise the entity would render
             * interpolating towards its keyframed spot from wherever it was created. Animated, the
             * entity's own update keeps that snapshot, same as in the editor. */
            entity.setPrevX(entity.getX());
            entity.setPrevY(entity.getY());
            entity.setPrevZ(entity.getZ());
            entity.setPrevYaw(entity.getYaw());
            entity.setPrevHeadYaw(entity.getHeadYaw());
            entity.setPrevBodyYaw(entity.getBodyYaw());
            entity.setPrevPitch(entity.getPitch());
        }
    }

    /**
     * Frozen, properties must resolve at the captured tick exactly, not at tick + partial, or the
     * pose would sit a fraction of a tick ahead of the one the editor drew. Animated, the partial
     * has to come through &mdash; it is what makes the forms move between ticks.
     */
    @Override
    protected float getTransition(IEntity entity, float transition)
    {
        return this.animated ? transition : 0F;
    }
}
