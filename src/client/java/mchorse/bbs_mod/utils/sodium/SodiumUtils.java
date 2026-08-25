package mchorse.bbs_mod.utils.sodium;

import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexSodiumConsumer;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.render.VertexConsumer;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class SodiumUtils
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /* Sodium's options entry point is reached by reflection on purpose.
     *
     * SodiumClientMod.options() keeps its name and owner across versions, but its
     * RETURN TYPE was renamed — net.caffeinemc.mods.sodium.client.gui.SodiumGameOptions
     * in 0.6.x, ...gui.SodiumOptions in 0.8.x. The return type is part of a method
     * reference in the constant pool, so a direct call compiled against one version
     * dies with NoSuchMethodError on the other. The workspace builds against 0.6.13
     * (0.8.x cannot run in the yarn dev workspace), while users run whatever Sodium
     * they have, so the call has to survive both.
     *
     * The field names below are byte-for-byte the same in both versions (verified by
     * javap on 0.6.13 and 0.8.12), which is why looking them up by name works. If a
     * future version renames them too, resolution fails, the feature turns itself off
     * and logs once — it must never take the game down. */
    private static boolean resolved;
    private static boolean available;

    private static Method optionsMethod;
    private static Field performanceField;
    private static Field blockFaceCullingField;
    private static Field fogOcclusionField;

    private static boolean savedBlockFaceCulling;
    private static boolean savedFogOcclusion;
    private static boolean disabled;

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
        if (!resolve())
        {
            return;
        }

        try
        {
            Object performance = getPerformanceSettings();

            if (performance == null)
            {
                return;
            }

            savedBlockFaceCulling = blockFaceCullingField.getBoolean(performance);
            savedFogOcclusion = fogOcclusionField.getBoolean(performance);

            /* Armed BEFORE the writes: if one of them throws, the restore below
             * still has to put back whatever did get through. */
            disabled = true;

            blockFaceCullingField.setBoolean(performance, false);
            fogOcclusionField.setBoolean(performance, false);
        }
        catch (Throwable t)
        {
            disable(t);
        }
    }

    public static void restorePointCameraCulling()
    {
        /* Only ever write back values this class actually saved. */
        if (!disabled)
        {
            return;
        }

        disabled = false;

        if (!available)
        {
            return;
        }

        try
        {
            Object performance = getPerformanceSettings();

            if (performance == null)
            {
                return;
            }

            blockFaceCullingField.setBoolean(performance, savedBlockFaceCulling);
            fogOcclusionField.setBoolean(performance, savedFogOcclusion);
        }
        catch (Throwable t)
        {
            disable(t);
        }
    }

    private static Object getPerformanceSettings() throws Exception
    {
        Object options = optionsMethod.invoke(null);

        return options == null ? null : performanceField.get(options);
    }

    private static boolean resolve()
    {
        if (resolved)
        {
            return available;
        }

        resolved = true;

        try
        {
            Class<?> clientMod = Class.forName("net.caffeinemc.mods.sodium.client.SodiumClientMod");

            optionsMethod = clientMod.getMethod("options");

            /* The declared return type IS the options class, whatever it is called
             * in this version — no need to name it. */
            performanceField = optionsMethod.getReturnType().getField("performance");

            Class<?> performanceClass = performanceField.getType();

            blockFaceCullingField = performanceClass.getField("useBlockFaceCulling");
            fogOcclusionField = performanceClass.getField("useFogOcclusion");

            available = true;
        }
        catch (Throwable t)
        {
            LOGGER.warn("[BBS] Sodium's performance options could not be resolved, "
                + "leaving its point-camera culling alone under ortho", t);

            available = false;
        }

        return available;
    }

    /** Log once and stop touching Sodium — this runs every frame. */
    private static void disable(Throwable t)
    {
        if (available)
        {
            LOGGER.warn("[BBS] failed to toggle Sodium's point-camera culling, giving up on it", t);
        }

        available = false;
    }
}
