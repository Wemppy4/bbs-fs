package mchorse.bbs_mod.cubic.jem;

import org.joml.Vector3f;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a .jem does not say about its parts and OptiFine takes from the vanilla model: the part
 * hierarchy, and the rotation points of the parts the file leaves empty.
 *
 * <p>A {@code .jem} lists every entity {@code part} as a flat top-level entry, but OptiFine attaches
 * each part to the matching <em>vanilla</em> entity bone, so the real parent-child structure comes
 * from the vanilla model — not from the {@code .jem} nesting. That structure matters for animation: a
 * part that is a vanilla child of {@code head} must follow the head. Likewise an empty part's rotation
 * point is the vanilla one: a pack keeps most vanilla parts as empty shells at a zero translate and
 * reads their positions — Fresh Animations animates the villager's head about a neck it never draws —
 * and what such a shell holds in OptiFine is vanilla's own pivot. The parser applies the pivots to
 * empty parts only; a part with geometry stays where its file put it.</p>
 *
 * <p>Read off the game by {@code VanillaRigs}; a model's {@code config.json} lays its own
 * {@code cem_parents} over it — see {@link #withParents}.</p>
 */
public final class CemHierarchy
{
    public static final CemHierarchy NONE = new CemHierarchy(Collections.emptyMap(), Collections.emptyMap());

    /** Child part &rarr; parent part. */
    public final Map<String, String> parents;

    /** Part &rarr; rest pivot (model pixels, Y up, X mirrored — the parser's convention) for the parts the file leaves empty. */
    public final Map<String, Vector3f> pivots;

    public CemHierarchy(Map<String, String> parents, Map<String, Vector3f> pivots)
    {
        this.parents = Collections.unmodifiableMap(new LinkedHashMap<>(parents));
        this.pivots = Collections.unmodifiableMap(new LinkedHashMap<>(pivots));
    }

    /** This hierarchy with the given child &rarr; parent entries laid over its own (a model's {@code cem_parents}). */
    public CemHierarchy withParents(Map<String, String> overrides)
    {
        if (overrides == null || overrides.isEmpty())
        {
            return this;
        }

        Map<String, String> merged = new LinkedHashMap<>(this.parents);

        merged.putAll(overrides);

        return new CemHierarchy(merged, this.pivots);
    }

    public boolean isEmpty()
    {
        return this.parents.isEmpty() && this.pivots.isEmpty();
    }
}
