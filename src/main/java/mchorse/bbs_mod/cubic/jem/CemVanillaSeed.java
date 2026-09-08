package mchorse.bbs_mod.cubic.jem;

import java.util.HashMap;
import java.util.Map;

/**
 * The vanilla frame's part poses, handed to a CEM program as the values its model variables start
 * from — see {@link CemAnimation}.
 *
 * <p>OptiFine evaluates a pack's statements over the model vanilla just posed, so a part's
 * {@code tx}, {@code rx} and {@code visible} arrive holding vanilla's pivot, angle and flag of that
 * frame. These are those numbers as the {@code ModelPart} fields hold them — pivots in pixels down from
 * the model's origin and local to the vanilla parent, angles in radians — keyed by the name the pack
 * uses. A part also carries its absolute pivot, for a part the file keeps at top level while vanilla
 * hangs it on a parent the file lacks.</p>
 *
 * <p>Filled by the stage every frame and read by the program in the same frame; the slots persist, so a
 * frame allocates nothing.</p>
 */
public class CemVanillaSeed
{
    /** One part's pose this frame. */
    public static class Part
    {
        /** The pivot, local to the vanilla parent. */
        public float tx, ty, tz;

        /** The pivot, accumulated up the vanilla parents. */
        public float ax, ay, az;

        /** The rotation, in radians. */
        public float rx, ry, rz;

        public float sx, sy, sz;
        public boolean visible;
    }

    private final Map<String, Part> parts = new HashMap<>();

    /** The slot for a part, made on first use and kept. */
    public Part part(String name)
    {
        return this.parts.computeIfAbsent(name, (key) -> new Part());
    }

    /** The pose of a part by the name the pack uses, or null when vanilla has no such part. */
    public Part get(String name)
    {
        return this.parts.get(name);
    }
}
