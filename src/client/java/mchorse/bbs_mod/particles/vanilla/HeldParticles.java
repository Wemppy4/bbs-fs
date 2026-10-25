package mchorse.bbs_mod.particles.vanilla;

import net.minecraft.client.particle.Particle;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla particles a form has let out into the world, whose time still
 * follows the form's.
 *
 * <p>The world's particle manager ticks and draws them like any other, with
 * two exceptions. They stand still while the form's own time does but the
 * form is still on screen &mdash; a paused film. And the form takes them all
 * back at once ({@link #clear()}) when its time jumps. A form that stopped
 * ticking AND drawing is gone (film closed, actor removed, projectile hit),
 * and its particles live out like any vanilla ones.
 *
 * <p>Everything is told apart on the particle manager's own ticks
 * ({@link #advance()}), the clock the particles run on. The forms tick on
 * either side of it: actors of a film after it, entities of the world before.
 */
public class HeldParticles
{
    /**
     * How many particle ticks a drawn frame keeps the particles standing still,
     * so a low frame rate (several ticks per frame) doesn't let them creep on.
     */
    private static final int FRAME_GRACE_TICKS = 4;

    private static long clock;

    private final List<Particle> particles = new ArrayList<>();

    private long ticked;
    private long drawn;
    private float transition;

    private long decidedAt = -1;
    private boolean stopped;

    /**
     * The particle manager ticks: called before it ticks any particle.
     */
    public static void advance()
    {
        clock += 1;
    }

    /**
     * The form's time moved on a tick, so its particles go on with it.
     */
    public void tick()
    {
        this.ticked = clock;

        this.particles.removeIf((particle) -> !particle.isAlive());
    }

    /**
     * The form was drawn, at this point between two ticks of its time.
     */
    public void draw(float transition)
    {
        this.drawn = clock;
        this.transition = transition;
    }

    /**
     * Whether the form's time stands still: drawn since its last tick. A form
     * that is gone gets no newer frame than its last tick, so it doesn't count.
     * Decided once per particle tick and kept for the frames after it, so the
     * particles are drawn the way they were (not) ticked.
     */
    public boolean isStopped()
    {
        if (this.decidedAt != clock)
        {
            this.decidedAt = clock;
            this.stopped = this.drawn > this.ticked && clock - this.drawn <= FRAME_GRACE_TICKS;
        }

        return this.stopped;
    }

    /**
     * Where between two ticks a stopped form stands, for drawing its particles
     * there rather than wherever the world is between its own ticks.
     */
    public float getTransition()
    {
        return this.transition;
    }

    public void add(Particle particle)
    {
        ((IHeldParticle) particle).bbs$setHolder(this);

        this.particles.add(particle);
    }

    public void clear()
    {
        for (Particle particle : this.particles)
        {
            particle.markDead();
        }

        this.particles.clear();
    }
}
