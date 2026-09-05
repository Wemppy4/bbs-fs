package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.math.Variable;

import java.util.List;

/**
 * The per-instance half of a CEM animation: what persists between frames for ONE animated instance
 * (a form renderer — a replay, a mob, a UI preview), while the {@link CemAnimation} program (parser,
 * statements, bone bindings) is shared by every instance of the model. The program loads these slots
 * into its {@code var.*}/{@code varb.*} variables before evaluating a frame and stores them back after,
 * so two instances of the same model never see each other's smoothing/drag state, and a UI preview never
 * bleeds into the film.
 */
public class CemState
{
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
