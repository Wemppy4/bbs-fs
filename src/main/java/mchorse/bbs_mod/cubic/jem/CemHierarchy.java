package mchorse.bbs_mod.cubic.jem;

import org.joml.Vector3f;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a .jem does not say about its parts and OptiFine takes from the vanilla model: the part
 * hierarchy and, where the file's own {@code translate} is not the pivot, the pivot.
 *
 * <p>A {@code .jem} lists every entity {@code part} as a flat top-level entry, but OptiFine attaches
 * each part to the matching <em>vanilla</em> entity bone, so the real parent-child structure comes
 * from the vanilla model — not from the {@code .jem} nesting. That structure matters for animation: a
 * part that is a vanilla child of {@code head} must follow the head. Likewise a part's rotation point is
 * the vanilla one; a file usually writes {@code translate = -pivot} (what Blockbench exports), but not
 * always — Fresh Animations' villager keeps {@code head} at {@code [0, 0, 0]} while animating it about
 * the neck. Fresh Animations authors against exactly this vanilla rig.</p>
 *
 * <p>{@link #forEntity} maps a {@code .jem} file name (the entity id, e.g. {@code villager}) to the
 * parent overrides that reparent flat top-level parts onto their vanilla parent, plus pivot overrides
 * for parts whose translate cannot be trusted. Only what differs from the flat file needs an entry. The
 * data is curated per entity (from OptiFine's {@code ModelAdapter} part maps plus the vanilla model
 * trees) and verified against the animation packs, because the vanilla tree alone does not always
 * match what a pack assumes. A model's {@code config.json} lays its own {@code cem_parents} over the
 * table — see {@link #withParents}.</p>
 */
public final class CemHierarchy
{
    public static final CemHierarchy NONE = new CemHierarchy(Collections.emptyMap(), Collections.emptyMap());

    /** Child part &rarr; parent part. */
    public final Map<String, String> parents;

    /** Part &rarr; pivot (model pixels, Y up) that replaces the one derived from the file's translate. */
    public final Map<String, Vector3f> pivots;

    public CemHierarchy(Map<String, String> parents, Map<String, Vector3f> pivots)
    {
        this.parents = Collections.unmodifiableMap(new LinkedHashMap<>(parents));
        this.pivots = Collections.unmodifiableMap(new LinkedHashMap<>(pivots));
    }

    /** The built-in vanilla hierarchy for an entity, or {@link #NONE} when unknown. */
    public static CemHierarchy forEntity(String name)
    {
        if (name == null)
        {
            return NONE;
        }

        return switch (name)
        {
            /* Allay: the arms are vanilla children of the body (the wings/head geometry already lives
             * in the body's submodels, so only the arms are flat top-level parts that need reparenting). */
            case "allay" -> new CemHierarchy(Map.of(
                "right_arm", "body",
                "left_arm", "body"
            ), Collections.emptyMap());

            /* Villager: the hat (headwear, with its rim headwear2) and the nose hang off the head, the
             * jacket (bodywear) off the body. The head itself has no boxes and a zero translate in the
             * packs, while its rotation point is the vanilla neck. */
            case "villager" -> new CemHierarchy(Map.of(
                "headwear", "head",
                "headwear2", "headwear",
                "nose", "head",
                "bodywear", "body"
            ), Map.of(
                "head", new Vector3f(0F, 24F, 0F)
            ));

            default -> NONE;
        };
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
