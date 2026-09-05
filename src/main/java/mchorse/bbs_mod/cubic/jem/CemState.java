package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.math.Variable;

import java.util.List;

/**
 * The frame clock of ONE animated instance (a form renderer — a replay, a mob, a UI preview), plus the
 * {@link CemVariables} that instance reads and writes, while the {@link CemAnimation} program (parser,
 * statements, bone bindings) is shared by every instance of the model.
 *
 * <p>The two halves have deliberately different scopes. The variables belong to the <em>entity</em>, so
 * every CEM model on it sees the same values — OptiFine's contract, and what carries Fresh Moves' cape
 * along with the body. The clock stays per instance: a shared one would hand the frame's real {@code
 * frame_time} to whichever model rendered first and leave the rest integrating against zero, so the body
 * would freeze whenever the cape happened to be drawn ahead of it.</p>
 *
 * <p>See {@link #advance} for the clock's rules.</p>
 */
public class CemState
{
    /** Ticks a forward jump may span before the state re-seeds instead of stepping — the bone physics' catch-up limit. */
    static final double MAX_TICK_CATCHUP = 4;

    /** Longest {@code frame_time} (seconds) handed to the animation. */
    static final double MAX_FRAME_TIME = 0.5;

    /** {@code frame_counter} wraps here — OptiFine's period, divisible by every small cycle length. */
    private static final int FRAME_COUNTER_PERIOD = 27720;

    /** Where this instance's {@code var.*}/{@code varb.*} live — the entity's store, shared with its other CEM models. */
    private final CemVariables variables;

    /** Entity clock (age + partial tick) of the last advanced frame; NaN until the first. */
    double lastFrameStamp = Double.NaN;

    /** Wall-clock fallback for instances without an entity clock (UI previews). */
    long lastNanos;

    int frameCounter;

    public CemState(CemVariables variables)
    {
        this.variables = variables;
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

    /**
     * Forget everything the animation accumulated: the next frame starts the way the first one did.
     * It clears the entity's whole store, so every CEM model on it restarts together — which is right,
     * they animate one timeline.
     */
    void reseed()
    {
        this.variables.clear();
        this.frameCounter = 0;
    }

    /** Push the persisted values into the program's variables before a frame is evaluated. */
    void load(List<Variable> variables)
    {
        this.variables.load(variables);
    }

    /** Pull the values the frame left in the program's variables back into the store. */
    void store(List<Variable> variables)
    {
        this.variables.store(variables);
    }
}
