package mchorse.bbs_mod.cubic.jem;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * What a {@code .jem}'s file name says about the entity it dresses.
 *
 * <p>A pack names a file after the entity and then decorates the name: the young ({@code cow_baby}),
 * the climate variant ({@code cold_cow}), a numbered alternative ({@code villager2}), a layer over the
 * entity ({@code sheep_wool}, {@code drowned_outer}, {@code horse_armor}). The game knows the entity
 * alone, so everything that asks the game about a model — the rig, the model of a stand-in — asks under
 * the plain name.</p>
 *
 * <p>A size ({@code puffer_fish_big}) or a pattern ({@code tropical_fish_a}) is not a decoration: the game
 * has a model of its own for each, so those names stay as they are. So does a skull's
 * ({@code head_creeper}) — it is a block's model, not the creeper's.</p>
 */
public final class CemNames
{
    /** The young variant's marker, anywhere in the name: {@code pig_baby_saddle} is a pig. */
    private static final String BABY = "_baby";

    /** Climate variants of the same entity. */
    private static final String[] CLIMATES = {"cold_", "warm_"};

    /**
     * Suffixes naming a layer drawn over the entity from its own bones — the wool over a sheep, the
     * armour over a horse, the outer skin of a stray — which is to say a material of the entity's
     * model rather than a model of its own; see {@link #layer}.
     */
    private static final String[] MATERIAL_LAYERS = {
        "_outer", "_saddle", "_armor", "_decor", "_patch", "_collar", "_wool", "_undercoat", "_harness", "_ropes"
    };

    /**
     * Every suffix naming a layer over an entity rather than an entity — the material layers and the
     * charge over a creeper, which vanilla draws by a rendering of its own and stays a model of its
     * own. Taken off one after another, so {@code sheep_wool_undercoat} comes back to {@code sheep}.
     */
    private static final String[] LAYERS = Stream.concat(Arrays.stream(MATERIAL_LAYERS), Stream.of("_charge")).toArray(String[]::new);

    /** A layer file's place: the model it is a layer of, and the layer's name — its material. */
    public record Layer(String base, String name)
    {}

    private CemNames()
    {}

    /**
     * The base model and the material name for a layer file, or null for a model of its own:
     * {@code sheep_wool} → ({@code sheep}, {@code wool}), {@code sheep_wool_undercoat} →
     * ({@code sheep}, {@code wool_undercoat}), {@code pig_baby_saddle} → ({@code pig_baby},
     * {@code saddle}) — the young are a model of their own, so the layer goes to theirs.
     */
    public static Layer layer(String file)
    {
        String name = file;
        String layer = null;

        for (boolean stripped = true; stripped; )
        {
            stripped = false;

            for (String suffix : MATERIAL_LAYERS)
            {
                if (name.endsWith(suffix) && name.length() > suffix.length())
                {
                    String part = suffix.substring(1);

                    layer = layer == null ? part : part + "_" + layer;
                    name = name.substring(0, name.length() - suffix.length());
                    stripped = true;
                }
            }
        }

        return layer == null ? null : new Layer(name, layer);
    }

    /** The entity a model file dresses: {@code cold_cow_baby} → {@code cow}, {@code drowned_outer} → {@code drowned}, {@code villager2} → {@code villager}. */
    public static String entity(String file)
    {
        String name = stripDigits(file.replace(BABY, ""));

        for (String climate : CLIMATES)
        {
            if (name.startsWith(climate))
            {
                name = name.substring(climate.length());
            }
        }

        for (boolean stripped = true; stripped; )
        {
            stripped = false;

            for (String suffix : LAYERS)
            {
                if (name.endsWith(suffix) && name.length() > suffix.length())
                {
                    name = stripDigits(name.substring(0, name.length() - suffix.length()));
                    stripped = true;
                }
            }
        }

        return name;
    }

    /** Whether the file dresses the young of the entity. */
    public static boolean baby(String file)
    {
        return file.contains(BABY);
    }

    /** The name without a trailing number. */
    public static String stripDigits(String name)
    {
        int end = name.length();

        while (end > 0 && Character.isDigit(name.charAt(end - 1)))
        {
            end -= 1;
        }

        return name.substring(0, end);
    }
}
