package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.forms.renderers.mob.IBBSModelPart;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.EntityModels;

import java.util.Collections;
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
 * <p><b>Only the hierarchy, deliberately.</b> Vanilla knows the rotation points too, and a pack's own
 * are sometimes wrong (Fresh Animations' villager keeps its head at zero and animates it about the
 * neck), but they are also sometimes deliberately different: the same pack's cow puts the body's
 * rotation in a submodel of its own instead of on the body, so vanilla's pivot would move a model
 * that has been correct all along. Pivots stay with the hand-checked table in {@link CemHierarchy},
 * which is where a mistake can be seen and fixed one entity at a time.</p>
 */
public class VanillaRigs
{
    /** The layer an entity's own model is registered under; the rest dress it (armour, saddles, hats). */
    private static final String MAIN = "main";

    /**
     * What OptiFine calls a vanilla part when the two disagree, so a rig read off the game reaches the
     * parts a {@code .jem} actually has.
     *
     * <p>Both names are recorded against the same parent rather than one replacing the other: a pack may
     * use either, and a name no model has costs nothing. Measured over Fresh Animations and its
     * extensions, {@code headwear} is a top-level part in 37 of their models, {@code headwear2} and
     * {@code bodywear} in nine each, so these three carry the whole humanoid family. Names the two
     * vocabularies already share — {@code nose} in twenty models, {@code arms} in thirteen — need
     * nothing.</p>
     */
    private static final Map<String, String> ALIASES = Map.of(
        "hat", "headwear",
        "hat_rim", "headwear2",
        "jacket", "bodywear"
    );

    private static Map<String, CemHierarchy> rigs;

    /** The vanilla rig of an entity by name, or {@link CemHierarchy#NONE} for one the game has no model for. */
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

            try
            {
                collect(entry.getValue().createModel(), null, parents);
            }
            catch (Exception e)
            {
                /* One model that will not build must not cost every other entity its rig. */
                System.err.println("Vanilla rig of " + layer.getId() + " could not be read: " + e);

                continue;
            }

            if (!parents.isEmpty())
            {
                rigs.put(layer.getId().getPath(), new CemHierarchy(parents, Collections.emptyMap()));
            }
        }

        return rigs;
    }

    /**
     * Note every part below the root against the part it hangs on. The parts directly under the root
     * get no entry: they are top level in the file too, which is where they should stay.
     *
     * <p>A name is taken the first time it is seen. Vanilla may use one twice in different branches,
     * and a CEM file addresses parts by name alone, so there is nothing better to go on either way.
     * Every part is also noted under the name OptiFine gives it, where that differs — see
     * {@link #ALIASES}.</p>
     */
    private static void collect(ModelPart part, String name, Map<String, String> parents)
    {
        for (Map.Entry<String, ModelPart> entry : IBBSModelPart.of(part).bbs$children().entrySet())
        {
            if (name != null)
            {
                String child = entry.getKey();
                String alias = ALIASES.get(child);

                parents.putIfAbsent(child, name);

                /* A pack that renamed the child renamed its parent the same way, so the alias is
                 * recorded against the parent's alias where there is one. */
                if (alias != null)
                {
                    parents.putIfAbsent(alias, ALIASES.getOrDefault(name, name));
                }
            }

            collect(entry.getValue(), entry.getKey(), parents);
        }
    }
}
