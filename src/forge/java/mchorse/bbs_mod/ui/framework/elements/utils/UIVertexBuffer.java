package mchorse.bbs_mod.ui.framework.elements.utils;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** BBS quad buffer backed by the native 1.12 vertex uploader. */
public class UIVertexBuffer extends BufferBuilder
{
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();
    private static final UIVertexBuffer IMMEDIATE = new UIVertexBuffer(262144);
    public static UIVertexBuffer immediate() { return IMMEDIATE; }
    public UIVertexBuffer(int size) { super(size); }
    @Override public UIVertexBuffer color(float r, float g, float b, float a)
    {
        super.color(r, g, b, a);
        return this;
    }
    public UIVertexBuffer vertex(Matrix4f matrix, float x, float y, float z)
    {
        pos(matrix.m00() * x + matrix.m10() * y + matrix.m20() * z + matrix.m30(),
            matrix.m01() * x + matrix.m11() * y + matrix.m21() * z + matrix.m31(),
            matrix.m02() * x + matrix.m12() * y + matrix.m22() * z + matrix.m32());
        return this;
    }
    public UIVertexBuffer color(int argb)
    {
        super.color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24);
        return this;
    }
    public UIVertexBuffer texture(float u, float v) { tex(u, v); return this; }
    @Override public UIVertexBuffer normal(float x, float y, float z)
    {
        super.normal(x, y, z);
        return this;
    }
    public void next() { endVertex(); }
    public void draw()
    {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableAlpha();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        boolean textured = getVertexFormat().hasUvOffset(0);
        if (textured) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
        finishDrawing();
        mchorse.bbs_mod.graphics.shader.ShaderProgram shader = textured ? mchorse.bbs_mod.client.PixelArt.bind(false) : null;
        try { UPLOADER.draw(this); }
        finally { if (shader != null) shader.unbind(); }
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.color(1, 1, 1, 1);
    }
}
