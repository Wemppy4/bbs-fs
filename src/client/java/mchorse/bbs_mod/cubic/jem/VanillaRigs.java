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

            try
            {
                collect(entry.getValue().createModel(), null, parents, CemPartNames.of(layer.getId().getPath()));
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
     * <p>Every part is noted under the name OptiFine gives it ({@link CemPartNames}) and, where it is
     * free, under vanilla's own — a pack sometimes uses the vanilla one ({@code hat} in six of Fresh
     * Animations' models). OptiFine's goes first because the two vocabularies cross: the bee's
     * {@code body} is vanilla's {@code bone}, and its {@code torso} vanilla's {@code body}, so what
     * hangs on vanilla's body must be recorded on {@code torso}, not on {@code body}. A name is taken
     * the first time it is seen: vanilla may use one twice in different branches, and a CEM file
     * addresses parts by name alone, so there is nothing better to go on either way.</p>
     */
    private static void collect(ModelPart part, String name, Map<String, String> parents, CemPartNames names)
    {
        for (Map.Entry<String, ModelPart> entry : IBBSModelPart.of(part).bbs$children().entrySet())
        {
            if (name != null)
            {
                String child = entry.getKey();

                parents.putIfAbsent(names.optifine(child), names.optifine(name));
                parents.putIfAbsent(child, name);
            }

            collect(entry.getValue(), entry.getKey(), parents, names);
        }
    }
}
