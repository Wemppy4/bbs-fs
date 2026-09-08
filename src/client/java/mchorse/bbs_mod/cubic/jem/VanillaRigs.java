package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.forms.renderers.mob.IBBSModelPart;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.EntityModels;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The part hierarchy of Minecraft's own entity models, read straight off the game.
 *
 * <p>A {@code .jem} lists every part of an entity flat, but OptiFine hangs each one on the matching
 * vanilla bone, so a hat follows the head and a saddle follows the body even though the file says
 * nothing about it. Without that, forty-two of Fresh Animations' hundred and twenty-eight models
 * come apart: the head of every humanoid drives geometry that lives in parts of its own.</p>
 *
 * <p>{@link EntityModels#getModels()} hands over every model the game knows as a
 * {@link TexturedModelData}, built by plain code — no resources, no world, no OpenGL, no waiting for
 * anything to be ready — and {@link TexturedModelData#createModel()} turns one into a fresh tree of
 * its own, so nothing here touches the parts a renderer is drawing with. Walking it is free too:
 * {@code ModelPartMixin} already teaches every part its name and its parent. The layer an entity's
 * own model sits under is {@code new EntityModelLayer(new Identifier("cow"), "main")}, so the
 * entity's name is the key with no table to write.</p>
 *
 * <p>The rotation points come along too, but they are for the parts a pack leaves <em>empty</em> (see
 * {@link JemModelParser}). Fresh Animations keeps most vanilla parts as empty shells at a zero
 * translate and reads their positions — the villager's head is animated about the neck, the magma
 * cube's layers hang off {@code segment4.ty}, the blaze's body cancels {@code stick1.ty} — and what
 * those shells hold in OptiFine is vanilla's own pivot. A part with geometry keeps the pivot its file
 * gives it: the same pack's cow puts the body's rotation in a submodel of its own instead of on the
 * body, and vanilla's pivot would move a model that has been right all along.</p>
 */
public class VanillaRigs
{
    /** The layer an entity's own model is registered under; the rest dress it (armour, saddles, hats). */
    private static final String MAIN = "main";

    /** The height of an entity model's origin above the ground: vanilla counts pixels down from it, BBS up from the ground. */
    private static final float Y_OFFSET = 24F;

    private static Map<String, CemHierarchy> rigs;

    /**
     * The vanilla rig of an entity by its plain name (see {@link CemNames#entity}), or
     * {@link CemHierarchy#NONE} for one the game has no model for.
     */
    public static synchronized CemHierarchy of(String entity)
    {
        if (rigs == null)
        {
            rigs = build();
        }

        return rigs.getOrDefault(entity, CemHierarchy.NONE);
    }

    /** Forget the trees, so a resource reload that swapped a mod's models is picked up. */
    public static synchronized void clear()
    {
        rigs = null;
    }

    private static Map<String, CemHierarchy> build()
    {
        Map<String, CemHierarchy> rigs = new HashMap<>();

        for (Map.Entry<EntityModelLayer, TexturedModelData> entry : EntityModels.getModels().entrySet())
        {
            EntityModelLayer layer = entry.getKey();

            if (!layer.getName().equals(MAIN))
            {
                continue;
            }

            Map<String, String> parents = new LinkedHashMap<>();
            Map<String, Vector3f> pivots = new LinkedHashMap<>();
            Map<String, Vector3f> offsets = new LinkedHashMap<>();

            try
            {
                collect(entry.getValue().createModel(), null, parents, pivots, offsets, CemPartNames.of(layer.getId().getPath()), new Vector3f());
            }
            catch (Exception e)
            {
                /* One model that will not build must not cost every other entity its rig. */
                System.err.println("Vanilla rig of " + layer.getId() + " could not be read: " + e);

                continue;
            }

            if (!pivots.isEmpty())
            {
                rigs.put(layer.getId().getPath(), new CemHierarchy(parents, pivots, offsets));
            }
        }

        return rigs;
    }

    /**
     * Note every part's rest pivot, and every part below the root against the part it hangs on. The
     * parts directly under the root get no parent: they are top level in the file too, which is where
     * they should stay.
     *
     * <p>Every part is noted under the name OptiFine gives it ({@link CemPartNames}) and, where it is
     * free, under vanilla's own — a pack sometimes uses the vanilla one ({@code hat} in six of Fresh
     * Animations' models). OptiFine's goes first because the two vocabularies cross: the bee's
     * {@code body} is vanilla's {@code bone}, and its {@code torso} vanilla's {@code body}, so what
     * hangs on vanilla's body must be recorded on {@code torso}, not on {@code body}. A name is taken
     * the first time it is seen: vanilla may use one twice in different branches, and a CEM file
     * addresses parts by name alone, so there is nothing better to go on either way.</p>
     *
     * <p>A pivot is the part's rotation point in the model's own coordinates: vanilla's are local to
     * the parent, Y down from the top of a 24-pixel entity, X to the entity's left; BBS keeps them
     * absolute, Y up from the ground, X mirrored — the same turn the parser gives a file's
     * {@code translate}. Both are recorded: the absolute pivot, for a part the file keeps at the top,
     * and the offset from the parent's, for one it hangs on a parent of its own (see
     * {@link CemHierarchy#offsets}). {@code origin} carries the parent's absolute pivot down the walk.</p>
     */
    private static void collect(ModelPart part, String name, Map<String, String> parents, Map<String, Vector3f> pivots, Map<String, Vector3f> offsets, CemPartNames names, Vector3f origin)
    {
        for (Map.Entry<String, ModelPart> entry : IBBSModelPart.of(part).bbs$children().entrySet())
        {
            String child = entry.getKey();
            ModelPart childPart = entry.getValue();
            Vector3f absolute = new Vector3f(origin).add(childPart.pivotX, childPart.pivotY, childPart.pivotZ);
            Vector3f pivot = new Vector3f(-absolute.x, Y_OFFSET - absolute.y, absolute.z);
            Vector3f offset = new Vector3f(-childPart.pivotX, -childPart.pivotY, childPart.pivotZ);

            pivots.putIfAbsent(names.optifine(child), pivot);
            pivots.putIfAbsent(child, pivot);
            offsets.putIfAbsent(names.optifine(child), offset);
            offsets.putIfAbsent(child, offset);

            if (name != null)
            {
                parents.putIfAbsent(names.optifine(child), names.optifine(name));
                parents.putIfAbsent(child, name);
            }

            collect(childPart, child, parents, pivots, offsets, names, absolute);
        }
    }
}
