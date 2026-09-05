package mchorse.bbs_mod.cubic.jem;

import java.util.Collections;
import java.util.Map;

/**
 * Vanilla part hierarchies for OptiFine CEM models.
 *
 * <p>A {@code .jem} lists every entity {@code part} as a flat top-level entry, but OptiFine attaches
 * each part to the matching <em>vanilla</em> entity bone, so the real parent-child structure comes
 * from the vanilla model — not from the {@code .jem} nesting. That structure matters for animation: a
 * part that is a vanilla child of {@code body} must follow the body and must NOT carry the top-level
 * entity-origin offset. Fresh Animations authors against exactly this vanilla hierarchy.</p>
 *
 * <p>This maps a {@code .jem} file name (the entity id, e.g. {@code allay}) to the parent overrides
 * that reparent the affected flat top-level parts onto their vanilla parent. Only parts whose vanilla
 * parent differs from "root" need an entry; everything else stays a top-level bone. The data is
 * curated per entity (derived from OptiFine's {@code ModelAdapter} part maps plus the vanilla model
 * trees) and verified visually, because the vanilla tree alone does not always match what an animation
 * pack assumes.</p>
 */
public final class CemHierarchy
{
    private CemHierarchy()
    {}

    /** Child-part to parent-part overrides for an entity, or an empty map if none/unknown. */
    public static Map<String, String> forEntity(String name)
    {
        if (name == null)
        {
            return Collections.emptyMap();
        }

        return switch (name)
        {
            /* Allay: the arms are vanilla children of the body (the wings/head geometry already lives
             * in the body's submodels, so only the arms are flat top-level parts that need reparenting). */
            case "allay" -> Map.of(
                "right_arm", "body",
                "left_arm", "body"
            );
            default -> Collections.emptyMap();
        };
    }
}
