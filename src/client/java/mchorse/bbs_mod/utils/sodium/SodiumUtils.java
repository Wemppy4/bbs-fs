package mchorse.bbs_mod.utils.sodium;

import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexSodiumConsumer;
import mchorse.bbs_mod.utils.colors.Color;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import net.minecraft.client.render.VertexConsumer;

/**
 * The Sodium half of the shader-mod coupling, restored for the 1.21.11 branch.
 *
 * <p>Touches Sodium classes directly, so it must only be loaded behind
 * {@code BBSRendering.isSodiumLoaded()} — same lazy-class gate IrisUtils uses.
 * 0.8.x renamed {@code SodiumGameOptions} to {@link SodiumOptions}; the two
 * performance flags this class toggles survived the rename unchanged.
 */
public class SodiumUtils
{
    private static boolean savedBlockFaceCulling;
    private static boolean savedFogOcclusion;

    public static VertexConsumer createVertexBuffer(VertexConsumer b, Color color)
    {
        return new RecolorVertexSodiumConsumer(b, color);
    }

    /**
     * Turn off Sodium's point-camera culling heuristics for the frame (the
     * orthographic projection breaks their assumptions): the per-section block
     * face culling judges face visibility from the camera POINT, which drops
     * visible faces near the screen edges under parallel sightlines, and the
     * fog occlusion culls whole sections beyond the fog range. The in-memory
     * options are read back every render call, so a per-frame toggle is
     * enough, and Sodium only persists them from its own settings screen.
     */
    public static void disablePointCameraCulling()
    {
        SodiumOptions.PerformanceSettings performance = SodiumClientMod.options().performance;

        savedBlockFaceCulling = performance.useBlockFaceCulling;
        savedFogOcclusion = performance.useFogOcclusion;

        performance.useBlockFaceCulling = false;
        performance.useFogOcclusion = false;
    }

    public static void restorePointCameraCulling()
    {
        SodiumOptions.PerformanceSettings performance = SodiumClientMod.options().performance;

        performance.useBlockFaceCulling = savedBlockFaceCulling;
        performance.useFogOcclusion = savedFogOcclusion;
    }
}
