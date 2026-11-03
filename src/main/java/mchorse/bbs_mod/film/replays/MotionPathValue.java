package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/**
 * One motion path the animator asked to keep on screen: which point of the replay it follows
 * ({@code "root"}, {@code "anchor"}, {@code "bone:<path>"} or {@code "spline:<path>"}) and the
 * colour it was given when added, kept so that removing a path doesn't recolour the others.
 * Editor data: playback and export never read it.
 */
public class MotionPathValue extends ValueGroup
{
    public final ValueString target = new ValueString("target", "");
    public final ValueInt color = new ValueInt("color", 0);

    public MotionPathValue(String id)
    {
        super(id);

        this.add(this.target);
        this.add(this.color);
    }

    public static class Paths extends ValueList<MotionPathValue>
    {
        public Paths(String id)
        {
            super(id);
        }

        @Override
        protected MotionPathValue create(String id)
        {
            return new MotionPathValue(id);
        }

        public MotionPathValue find(String target)
        {
            for (MotionPathValue path : this.getAllTyped())
            {
                if (path.target.get().equals(target))
                {
                    return path;
                }
            }

            return null;
        }
    }
}
