package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.math.Variable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code var.*}/{@code varb.*} of one entity: the values an OptiFine pack keeps between frames,
 * addressed by name because they outlive any single program.
 *
 * <p>OptiFine scopes these to the entity, not to the model, and packs build on that — a model writes
 * a value and another model of the same entity reads it. It is the only channel Fresh Moves' cape has
 * to the body ({@code player.jem} publishes {@code var.player_body_rx = body.rx}, {@code
 * player_cape.jem} reads it and nothing else), and Fresh Animations leans on it in fifty of its
 * hundred-odd models — the sheep's undercoat takes eight values off the wool, the wolf's collar and
 * armour take theirs off the wolf. Give every model its own store and those models animate against
 * zeroes: the cape hangs at a fixed angle however the player runs.</p>
 *
 * <p>By name, not by slot: two programs share only the names they have in common, and each still
 * loads exactly the variables it declared. The frame clock stays per model
 * ({@link CemState}) — sharing that instead would hand one model the frame's
 * {@code frame_time} and leave the other integrating against zero.</p>
 */
public class CemVariables
{
    /**
     * A plain map: a program has a few dozen entity variables and reads them once per frame, so the
     * boxing costs nothing next to evaluating the statements, and it keeps this package free of
     * dependencies the rest of {@code main} does not use.
     */
    private final Map<String, Double> values = new HashMap<>();

    /** Push the stored values into a program's variables before it evaluates a frame. */
    public void load(List<Variable> variables)
    {
        for (Variable variable : variables)
        {
            /* An unwritten CEM variable holds 0. */
            variable.set(this.values.getOrDefault(variable.getName(), 0D));
        }
    }

    /** Take back what the frame left in a program's variables. */
    public void store(List<Variable> variables)
    {
        for (Variable variable : variables)
        {
            this.values.put(variable.getName(), variable.doubleValue());
        }
    }

    /** Forget everything: the next frame starts the way the first one did. */
    public void clear()
    {
        this.values.clear();
    }
}
