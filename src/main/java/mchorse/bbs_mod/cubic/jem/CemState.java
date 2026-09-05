package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.math.Variable;

import java.util.Arrays;
import java.util.List;

/**
 * The per-instance half of a CEM animation: what persists between frames for ONE animated instance
 * (a form renderer — a replay, a mob, a UI preview), while the {@link CemAnimation} program (parser,
 * statements, bone bindings) is shared by every instance of the model. The program loads these slots
 * into its {@code var.*}/{@code varb.*} variables before evaluating a frame and stores them back after,
 * so two instances of the same model never see each other's smoothing/drag state, and a UI preview never
 * bleeds into the film.
 *
 * <p>The instance clock lives here too — see {@link #advance}.</p>
 */
public class CemState
{
    /** Ticks a forward jump may span before the state re-seeds instead of stepping — the bone physics' catch-up limit. */
    static final double MAX_TICK_CATCHUP = 4;

    /** Longest {@code frame_time} (seconds) handed to the animation. */
    static final double MAX_FRAME_TIME = 0.5;

    /** {@code frame_counter} wraps here — OptiFine's period, divisible by every small cycle length. */
    private static final int FRAME_COUNTER_PERIOD = 27720;

    /** {@code var.*}/{@code varb.*} values, indexed like the program's entity variable list. */
    final double[] values;

    /** Entity clock (age + partial tick) of the last advanced frame; NaN until the first. */
    double lastFrameStamp = Double.NaN;

    /** Wall-clock fallback for instances without an entity clock (UI previews). */
    long lastNanos;

    int frameCounter;

    CemState(int variables)
    {
        this.values = new double[variables];
    }

    /**
     * Advance the instance clock to a frame and return its {@code frame_time} (seconds since the previous
     * frame). {@code stamp} is the entity clock, age + partial tick, or NaN when there is none (a UI
     * preview), which falls back to wall time and counts every call as a frame.
     *
     * <p>A frame is stepped from the previous one only while the stamp moves forward by at most
     * {@link #MAX_TICK_CATCHUP} ticks. Backwards by a tick or more (a scrub, a film restart resetting the
     * age) or further forward than that, and the state is re-seeded: the {@code var.*} drags start from
     * zero, so a frame reached by playing from the start comes out the same however many times it is
     * rendered — the rule the bone physics follows. Playing up to a frame and jumping to it still differ;
     * re-simulating the gap is not done.</p>
     *
     * <p>A repeated stamp, or one within the same tick but earlier (a matrix capture sampling another
     * partial tick), is another pass over the same frame: {@code frame_time} 0 and the counter untouched,
     * so the pass reproduces the frame instead of stepping it.</p>
     */
    double advance(double stamp)
    {
        if (Double.isNaN(stamp))
        {
            long now = System.nanoTime();
            double frameTime = this.lastNanos == 0 ? 0 : Math.min(MAX_FRAME_TIME, (now - this.lastNanos) / 1.0e9D);

            this.lastNanos = now;
            this.frameCounter = (this.frameCounter + 1) % FRAME_COUNTER_PERIOD;

            return frameTime;
        }

        double frameTime = 0;

        if (!Double.isNaN(this.lastFrameStamp))
        {
            double delta = stamp - this.lastFrameStamp;

            if (delta <= 0 && delta > -1)
            {
                return 0;
            }

            if (delta < 0 || delta > MAX_TICK_CATCHUP)
            {
                this.reseed();
            }
            else
            {
                frameTime = Math.min(MAX_FRAME_TIME, delta / 20D);
            }
        }

        this.lastFrameStamp = stamp;
        this.frameCounter = (this.frameCounter + 1) % FRAME_COUNTER_PERIOD;

        return frameTime;
    }

    /** Forget everything the animation accumulated: the next frame starts the way the first one did. */
    void reseed()
    {
        Arrays.fill(this.values, 0D);
        this.frameCounter = 0;
    }

    /** Push the persisted values into the program's shared variables before a frame is evaluated. */
    void load(List<Variable> variables)
    {
        for (int i = 0; i < this.values.length; i++)
        {
            variables.get(i).set(this.values[i]);
        }
    }

    /** Pull the values the frame left in the shared variables back into this instance. */
    void store(List<Variable> variables)
    {
        for (int i = 0; i < this.values.length; i++)
        {
            this.values[i] = variables.get(i).doubleValue();
        }
    }
}
