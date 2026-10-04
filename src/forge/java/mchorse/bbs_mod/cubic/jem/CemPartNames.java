package mchorse.bbs_mod.cubic.jem;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What OptiFine calls the parts of a vanilla entity model, where the two vocabularies differ.
 *
 * <p>A {@code .jem} addresses parts by OptiFine's names — the cow's legs are {@code leg1} to {@code leg4},
 * the horse's neck is {@code neck}, the blaze's rods {@code stick1} to {@code stick12} — while the game's
 * model has {@code right_hind_leg}, {@code head_parts} and {@code part0}. Everything that lays the vanilla
 * model over a {@code .jem} goes through this table: the rig hangs OptiFine-named parts on OptiFine-named
 * parents, and a vanilla part's pose reaches the program under the name the pack reads.</p>
 *
 * <p>Only the names that differ are listed; a name absent from an entity's table is the same on both
 * sides, and an entity nobody listed maps every name to itself. The facts are OptiFine's, as its
 * {@code cem_model.txt} documents them and as Entity Model Features implements them, checked here
 * against the 1.20.4 models; the entity is looked up by its plain name (see {@link CemNames#entity}),
 * and a family shares one table — the quadrupeds, the humanoids, the horses.</p>
 */
public final class CemPartNames
{
    /** The vocabulary of an entity nobody had to translate: every name is the same on both sides. */
    public static final CemPartNames SAME = new CemPartNames(Collections.emptyMap());

    private static final Map<String, CemPartNames> BY_ENTITY = new HashMap<>();

    static
    {
        Map<String, String> quadruped = names(
            "leg1", "right_hind_leg", "leg2", "left_hind_leg",
            "leg3", "right_front_leg", "leg4", "left_front_leg");
        Map<String, String> frontBack = names(
            "front_left_leg", "left_front_leg", "front_right_leg", "right_front_leg",
            "back_left_leg", "left_hind_leg", "back_right_leg", "right_hind_leg");
        Map<String, String> hat = names("headwear", "hat");
        Map<String, String> villager = names("headwear", "hat", "headwear2", "hat_rim", "bodywear", "jacket");

        family(quadruped, "cow", "mooshroom", "pig", "sheep", "creeper", "goat", "polar_bear", "panda", "fox", "axolotl");
        family(with(quadruped, "body2", "egg_belly"), "turtle");
        family(with(quadruped, "mane", "upper_body"), "wolf");
        family(with(quadruped, "chest_left", "left_chest", "chest_right", "right_chest"), "llama", "trader_llama");
        family(with(frontBack, "tail", "tail1"), "cat", "ocelot");
        family(frontBack, "hoglin", "zoglin", "camel");
        family(with(frontBack,
            "neck", "head_parts", "mouth", "upper_mouth",
            "child_front_left_leg", "left_front_baby_leg", "child_front_right_leg", "right_front_baby_leg",
            "child_back_left_leg", "left_hind_baby_leg", "child_back_right_leg", "right_hind_baby_leg",
            "headpiece", "head_saddle", "noseband", "mouth_saddle_wrap",
            "left_rein", "left_saddle_line", "right_rein", "right_saddle_line",
            "left_bit", "left_saddle_mouth", "right_bit", "right_saddle_mouth"),
            "horse", "skeleton_horse", "zombie_horse", "donkey", "mule");
        family(names(
            "front_left_leg", "left_front_leg", "front_right_leg", "right_front_leg",
            "middle_left_leg", "left_mid_leg", "middle_right_leg", "right_mid_leg",
            "back_left_leg", "left_hind_leg", "back_right_leg", "right_hind_leg"), "sniffer");
        family(hat, "zombie", "husk", "drowned", "giant", "skeleton", "stray", "wither_skeleton", "bogged",
            "enderman", "piglin", "piglin_brute", "zombified_piglin", "player", "player_slim");
        family(villager, "villager", "wandering_trader", "zombie_villager", "witch");
        family(names("body", "upper_body", "body_bottom", "lower_body", "left_hand", "left_arm", "right_hand", "right_arm"), "snow_golem");
        family(names("bill", "beak", "chin", "red_thing"), "chicken");
        family(names("body", "bone", "torso", "body"), "bee", "warden");
        family(numbered("stick", "part", 12), "blaze");
        family(with(numbered("segment", "cube", 8), "core", "inside_cube"), "magma_cube");
        family(names("body", "cube"), "slime");
        family(numbered("tentacle", "tentacle", 8), "squid", "glow_squid");
        family(numbered("tentacle", "tentacle", 9), "ghast");
        family(with(with(numbered("spine", "spike", 12), numbered("tail", "tail", 3)), "body", "head"), "guardian", "elder_guardian");
        family(numbered("body", "segment", 4), "endermite");
        family(with(numbered("body", "segment", 7), numbered("wing", "layer", 3)), "silverfish");
        family(names(
            "neck", "body0", "body", "body1",
            "leg1", "right_hind_leg", "leg2", "left_hind_leg",
            "leg3", "right_middle_hind_leg", "leg4", "left_middle_hind_leg",
            "leg5", "right_middle_front_leg", "leg6", "left_middle_front_leg",
            "leg7", "right_front_leg", "leg8", "left_front_leg"), "spider", "cave_spider");
        family(names("outer_left_wing", "left_wing_tip", "outer_right_wing", "right_wing_tip"), "bat");
        family(names("tail", "tail_base", "tail2", "tail_tip", "left_wing", "left_wing_base", "right_wing", "right_wing_base"), "phantom");
        family(names("jaw", "mouth"), "ravager");
        family(names("tail", "tail_fin", "fin_right", "right_fin", "fin_left", "left_fin", "fin_back", "top_fin"), "cod");
        family(names("fin_back_1", "top_front_fin", "fin_back_2", "top_back_fin", "tail", "back_fin", "fin_right", "right_fin", "fin_left", "left_fin"), "salmon");
        family(names(
            "hair_right_top", "right_top_bristle", "hair_right_middle", "right_middle_bristle", "hair_right_bottom", "right_bottom_bristle",
            "hair_left_top", "left_top_bristle", "hair_left_middle", "left_middle_bristle", "hair_left_bottom", "left_bottom_bristle"), "strider");
    }

    private final Map<String, String> toVanilla;
    private final Map<String, String> toOptifine;

    private CemPartNames(Map<String, String> toVanilla)
    {
        this.toVanilla = Collections.unmodifiableMap(new LinkedHashMap<>(toVanilla));

        Map<String, String> toOptifine = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : toVanilla.entrySet())
        {
            toOptifine.put(entry.getValue(), entry.getKey());
        }

        this.toOptifine = Collections.unmodifiableMap(toOptifine);
    }

    /** The vocabulary of an entity, by its plain name — {@link #SAME} for one nobody had to translate. */
    public static CemPartNames of(String entity)
    {
        return BY_ENTITY.getOrDefault(entity, SAME);
    }

    /** The vanilla part a {@code .jem} part stands for; the name itself where the two agree. */
    public String vanilla(String part)
    {
        return this.toVanilla.getOrDefault(part, part);
    }

    /** The name a {@code .jem} gives a vanilla part; the name itself where the two agree. */
    public String optifine(String vanillaPart)
    {
        return this.toOptifine.getOrDefault(vanillaPart, vanillaPart);
    }

    /** One table shared by every entity of a family. */
    private static void family(Map<String, String> names, String... entities)
    {
        CemPartNames family = new CemPartNames(names);

        for (String entity : entities)
        {
            BY_ENTITY.put(entity, family);
        }
    }

    /** OptiFine name, vanilla name, OptiFine name, vanilla name... */
    private static Map<String, String> names(String... pairs)
    {
        return with(Collections.emptyMap(), pairs);
    }

    private static Map<String, String> with(Map<String, String> base, String... pairs)
    {
        Map<String, String> names = new LinkedHashMap<>(base);

        for (int i = 0; i < pairs.length; i += 2)
        {
            names.put(pairs[i], pairs[i + 1]);
        }

        return names;
    }

    private static Map<String, String> with(Map<String, String> base, Map<String, String> more)
    {
        Map<String, String> names = new LinkedHashMap<>(base);

        names.putAll(more);

        return names;
    }

    /** {@code stick1} → {@code part0} up to {@code stick12} → {@code part11}: OptiFine counts from one, vanilla from zero. */
    private static Map<String, String> numbered(String optifine, String vanilla, int count)
    {
        Map<String, String> names = new LinkedHashMap<>();

        for (int i = 1; i <= count; i++)
        {
            names.put(optifine + i, vanilla + (i - 1));
        }

        return names;
    }
}
