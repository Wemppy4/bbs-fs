package mchorse.bbs_mod.film.sequence;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;

import java.util.Map;

/**
 * A film of a sequence, played the way F6 plays one, but on the sequence's time: whoever drives
 * it sets the tick and whether it is playing, and it may jump anywhere.
 *
 * <p>Paused it holds the tick exactly, the way the film editor's paused timeline does; a jump
 * starts what the forms simulate tick by tick (particles) anew where it landed.
 */
public class SequenceFilmController extends BaseFilmController
{
    public int tick;
    public boolean playing;

    private int lastTick;

    public SequenceFilmController(Film film, int tick)
    {
        super(film);

        this.tick = tick;
        this.lastTick = tick;

        this.createEntities();
    }

    @Override
    public Map<String, Integer> getActors()
    {
        return BBSModClient.getFilms().getActors(this.film.getId());
    }

    @Override
    public int getTick()
    {
        return this.tick;
    }

    @Override
    protected void updateEntities(int ticks)
    {
        int step = ticks - this.lastTick;

        super.updateEntities(ticks);

        this.lastTick = ticks;

        if (step != 0 && step != 1)
        {
            for (IEntity entity : this.entities.values())
            {
                if (entity.getForm() != null)
                {
                    entity.getForm().restart();
                }
            }
        }
    }

    @Override
    protected void updateEntityAndForm(IEntity entity, int tick)
    {
        if (this.playing)
        {
            super.updateEntityAndForm(entity, tick);
        }
    }

    @Override
    protected void applyReplay(Replay replay, int ticks, IEntity entity)
    {
        super.applyReplay(replay, ticks, entity);

        if (!this.playing)
        {
            /* Nothing moves, so the previous tick is this tick - otherwise the entity would be
             * drawn on its way from wherever it was before the jump. */
            entity.setPrevX(entity.getX());
            entity.setPrevY(entity.getY());
            entity.setPrevZ(entity.getZ());
            entity.setPrevYaw(entity.getYaw());
            entity.setPrevHeadYaw(entity.getHeadYaw());
            entity.setPrevBodyYaw(entity.getBodyYaw());
            entity.setPrevPitch(entity.getPitch());
        }
    }

    @Override
    protected float getTransition(IEntity entity, float transition)
    {
        return this.playing ? transition : 0F;
    }
}
