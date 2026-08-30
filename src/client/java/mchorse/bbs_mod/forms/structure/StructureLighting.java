package mchorse.bbs_mod.forms.structure;

import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;

/**
 * How a structure form is lit, for both of the fake worlds a structure renders against:
 * {@link StructureRenderWorld} (block/fluid geometry) and {@link StructureWorld} (block
 * entities). They have no common supertype worth sharing — one is a {@code BlockRenderView},
 * the other a full {@code World} — but a structure that shaded its blocks one way and its
 * chests another would be a bug, so the answers live here rather than in each.
 *
 * <p>A structure is lit as if standing in open overworld daylight: full sky light, no block
 * light propagation, vanilla directional face shade. The form's own {@code lighting} property
 * and the world light around it are applied later, at replay.</p>
 */
public class StructureLighting
{
    /** Vanilla overworld directional face shade. {@code shaded == false} means the caller wants none. */
    public static float getBrightness(Direction direction, boolean shaded)
    {
        if (!shaded)
        {
            return 1F;
        }

        switch (direction)
        {
            case DOWN: return 0.5F;
            case UP: return 1F;
            case NORTH:
            case SOUTH: return 0.8F;
            default: return 0.6F;
        }
    }

    /** Full sun, no torches: baked emitters keep their glow through the light baked into vertices. */
    public static int getLightLevel(LightType type)
    {
        return type == LightType.SKY ? 15 : 0;
    }
}
