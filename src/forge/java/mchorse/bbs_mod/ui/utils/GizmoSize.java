package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4fc;

/** Uniform gizmo scale measured in UI pixels using the scene projection. */
public final class GizmoSize
{
    /* Pixels per unit of handle geometry, before the user's axes scale. */
    private static final float PIXELS_PER_UNIT = 80F;

    private GizmoSize()
    {}

    public static float getScale(Matrix4fc modelView, Matrix4fc projection, float viewportHeight)
    {
        if (BBSSettings.gizmoKeepScreenSize != null && !BBSSettings.gizmoKeepScreenSize.get())
        {
            return 1F;
        }

        if (!(viewportHeight > 0F) || !Float.isFinite(viewportHeight))
        {
            viewportHeight = Minecraft.getMinecraft().displayHeight / BBSModClient.getGUIScale();
        }

        return projectedScale(modelView, projection, viewportHeight, PIXELS_PER_UNIT);
    }

    /** Convert a target pixel length to world units at the handle's origin.
     * Camera-up displacement supplies the vertical projection derivative, so
     * perspective depth, orthographic zoom and off-centre frustums all use the
     * same calculation. Object rotation/scale never enter the measurement. */
    public static float projectedScale(Matrix4fc modelView, Matrix4fc projection, float viewportHeight, float pixels)
    {
        float x = modelView.m30();
        float y = modelView.m31();
        float z = modelView.m32();
        float clipY = projection.m01() * x + projection.m11() * y + projection.m21() * z + projection.m31();
        float clipW = projection.m03() * x + projection.m13() * y + projection.m23() * z + projection.m33();
        float derivative = Math.abs(projection.m11() * clipW - clipY * projection.m13());
        float scale = 2F * pixels * clipW * clipW / (viewportHeight * derivative);

        return Float.isFinite(scale) ? Math.max(scale, 0.0001F) : 0.0001F;
    }
}
