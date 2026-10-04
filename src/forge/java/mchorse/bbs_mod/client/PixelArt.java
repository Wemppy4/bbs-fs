package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.shader.ShaderProgram;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Original fractional UI sampling, using the native 1.12 client vertex arrays. */
public final class PixelArt
{
    private static boolean drawingUI;
    private PixelArt() {}
    public static boolean isEnabled() { return BBSSettings.pixelArtSmoothing.get(); }
    public static void setDrawingUI(boolean drawing) { drawingUI = drawing; }

    /** Font atlases are RGBA on this port, including the decoded Unihex glyphs. */
    public static ShaderProgram bind(boolean text)
    {
        if (!isEnabled() || text && !drawingUI) return null;
        ShaderProgram shader = BBSShaders.getNativePixelArtProgram();
        if (shader == null) return null;
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager.setActiveTexture(active);
        shader.addSampler("Sampler0", texture);
        shader.bind();
        return shader;
    }
}
