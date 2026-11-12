package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.SceneDepth;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.Map;
import java.util.function.BiPredicate;

/**
 * The selection outline the editors share: a mask the size of their preview, where the picked
 * form is drawn the way the pick pass draws it (each part in its pick id's colour, with its
 * depth), and a pass over the preview ringing either one part of it (the edited bone) or all of
 * it, in the interface's main colour. Optionally what the world's depth ({@link SceneDepth})
 * puts in front of the mask isn't ringed.
 *
 * <p>Draw the mask between {@link #begin} and {@link #end}, rendering the form with
 * {@link #stencilMap} under whatever projection the preview uses (the mask covers the same
 * picture); then {@link #draw} the ring over the preview in the interface pass.</p>
 */
public class OutlineMask
{
    public final StencilMap stencilMap = new StencilMap();

    private int fbo;
    private int color;
    private int depth;
    private int width;
    private int height;
    private boolean failed;
    private boolean drawn;

    /* What begin() found, put back by end() */
    private final int[] viewport = new int[4];
    private int previousDraw;
    private int previousRead;
    private boolean depthTest;
    private int depthFunc;

    /** Whether the ring can be drawn at all: the shader compiled and the buffers could be made. */
    public static boolean isAvailable()
    {
        return BBSShaders.getSelectionOutlineProgram() != null;
    }

    /** Whether a mask was drawn since the last {@link #invalidate}, for a host that redraws it only on change. */
    public boolean isDrawn()
    {
        return this.drawn;
    }

    public void invalidate()
    {
        this.drawn = false;
    }

    /** Start drawing the mask, {@code w} by {@code h} pixels; false (and no {@link #end}) when it can't be made. */
    public boolean begin(int w, int h)
    {
        if (this.failed || !this.ensure(w, h))
        {
            return false;
        }

        this.previousDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        this.previousRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        this.depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        this.depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, this.viewport);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.fbo);
        RenderSystem.viewport(0, 0, w, h);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        GL30.glClearBufferfv(GL11.GL_COLOR, 0, new float[] {0F, 0F, 0F, 0F});
        GL30.glClearBufferfv(GL11.GL_DEPTH, 0, new float[] {1F});

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LESS);

        this.stencilMap.setup();
        this.stencilMap.setIncrement(true);

        return true;
    }

    public void end()
    {
        RenderSystem.depthFunc(this.depthFunc);

        if (!this.depthTest)
        {
            RenderSystem.disableDepthTest();
        }

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.previousRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.previousDraw);
        RenderSystem.viewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);

        this.drawn = true;
    }

    /** The pick id the last mask drew a part with, among those {@code picked} accepts; 0 for none. */
    public int indexOf(BiPredicate<Form, String> picked)
    {
        for (Map.Entry<Integer, Pair<Form, String>> entry : this.stencilMap.indexMap.entrySet())
        {
            Pair<Form, String> part = entry.getValue();

            if (part.a != null && !part.b.isEmpty() && picked.test(part.a, part.b))
            {
                return entry.getKey();
            }
        }

        return 0;
    }

    /**
     * Ring the part drawn with {@code index} (or the whole mask when it's 0) over {@code area},
     * the preview the mask covers. With {@code projection} (the one the mask was drawn with), what
     * the world's fresh depth puts in front isn't ringed; null rings everything drawn.
     */
    public void draw(UIContext context, Area area, int index, Matrix4f projection)
    {
        ShaderProgram program = BBSShaders.getSelectionOutlineProgram();

        if (program == null || !this.drawn)
        {
            return;
        }

        boolean scene = projection != null && SceneDepth.isFresh();
        float[] sceneViewport = SceneDepth.getViewport();
        int outline = BBSSettings.primaryColor(Colors.A100);

        set(program, "OutlineColor", Colors.getR(outline), Colors.getG(outline), Colors.getB(outline), 1F);
        set(program, "Texel", 1F / this.width, 1F / this.height);
        set(program, "MaskDepth", scene ? projection.m22() : 0F, scene ? projection.m32() : 0F);
        set(program, "SceneDepth", SceneDepth.getA(), SceneDepth.getB());
        set(program, "SceneViewport", sceneViewport[0], sceneViewport[1], sceneViewport[2], sceneViewport[3]);

        GlUniform useScene = program.getUniform("UseScene");
        GlUniform selected = program.getUniform("SelectedIndex");

        if (useScene != null) useScene.set(scene ? 1F : 0F);
        if (selected != null) selected.set(index);

        context.batcher.flush();

        Matrix4f matrix = context.batcher.getContext().getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        /* The mask is drawn bottom up, the interface top down */
        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        builder.vertex(matrix, area.x, area.y, 0F).texture(0F, 1F).next();
        builder.vertex(matrix, area.x, area.ey(), 0F).texture(0F, 0F).next();
        builder.vertex(matrix, area.ex(), area.ey(), 0F).texture(1F, 0F).next();
        builder.vertex(matrix, area.ex(), area.y, 0F).texture(1F, 1F).next();

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.setShaderTexture(0, this.color);
        RenderSystem.setShaderTexture(1, this.depth);
        RenderSystem.setShaderTexture(2, scene ? SceneDepth.getTexture() : this.depth);
        RenderSystem.setShader(() -> program);
        BufferRenderer.drawWithGlobalProgram(builder.end());
        RenderSystem.enableCull();
    }

    private static void set(ShaderProgram program, String name, float... values)
    {
        GlUniform uniform = program.getUniform(name);

        if (uniform == null)
        {
            return;
        }

        if (values.length == 2) uniform.set(values[0], values[1]);
        else if (values.length == 4) uniform.set(values[0], values[1], values[2], values[3]);
    }

    private boolean ensure(int w, int h)
    {
        if (this.fbo != 0 && this.width == w && this.height == h)
        {
            return true;
        }

        int previousDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

        this.delete();

        this.width = w;
        this.height = h;
        this.color = texture(w, h, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        this.depth = texture(w, h, GL11.GL_DEPTH_COMPONENT, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT);

        this.fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, this.color, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, this.depth, 0);

        boolean complete = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);

        if (!complete)
        {
            org.slf4j.LoggerFactory.getLogger(OutlineMask.class).warn("The selection outline is unavailable ({}x{} mask).", w, h);
            this.delete();
            this.failed = true;
        }

        this.drawn = false;

        return complete;
    }

    private static int texture(int w, int h, int internal, int format, int type)
    {
        int texture = GlStateManager._genTexture();

        RenderSystem.bindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internal, w, h, 0, format, type, (java.nio.ByteBuffer) null);
        RenderSystem.bindTexture(0);

        return texture;
    }

    private void delete()
    {
        if (this.fbo != 0) GL30.glDeleteFramebuffers(this.fbo);
        if (this.color != 0) GlStateManager._deleteTexture(this.color);
        if (this.depth != 0) GlStateManager._deleteTexture(this.depth);

        this.fbo = this.color = this.depth = 0;
        this.width = this.height = 0;
    }
}
