package mchorse.bbs_mod.ui.film.controller;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.FilmEntityRenderer;
import mchorse.bbs_mod.film.FilmTarget;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.graphics.SceneDepth;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * A thin outline around the film's selected actor, the way 3D programs mark what is selected.
 * The actor alone is drawn again into a mask the size of the preview, the way the pick pass
 * draws it (its silhouette and its depth); a pass over the preview then rings what of the mask
 * is seen — the parts the world's own depth ({@link SceneDepth}) puts behind a block or another
 * actor get no outline. The mask is only redrawn when something it depends on changed.
 */
public class SelectionOutline
{
    /** Redraw at least this often (in frames), for what the key below doesn't see (a pose edit from a field). */
    private static final int HEARTBEAT = 10;

    private final UIFilmController controller;
    private final StencilMap stencilMap = new StencilMap();

    private int fbo;
    private int color;
    private int depth;
    private int width;
    private int height;
    private boolean failed;

    /* What the mask was drawn for */
    private final Matrix4f lastView = new Matrix4f();
    private final Matrix4f lastProjection = new Matrix4f();
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY;
    private int lastCursor;
    private int lastReplay;
    private FilmTarget lastTarget;
    private int framesSince;
    private boolean drawn;

    public SelectionOutline(UIFilmController controller)
    {
        this.controller = controller;
    }

    public void render(UIContext context)
    {
        ShaderProgram program = BBSShaders.getSelectionOutlineProgram();
        UIFilmController controller = this.controller;
        WorldRenderContext world = controller.worldRenderContext();
        IEntity entity = controller.getCurrentEntity();
        Replay replay = controller.panel.replayEditor.getReplay();

        if (!BBSSettings.editorSelectionOutline.get() || program == null || this.failed || world == null
            || entity == null || replay == null || controller.panel.isFlying() || controller.getControlled() != null
            || controller.getPovMode() == UIFilmController.CAMERA_MODE_FIRST_PERSON)
        {
            this.drawn = false;

            return;
        }

        Area viewport = controller.panel.preview.getViewport();
        float scale = BBSModClient.getGUIScale();
        int w = Math.max(1, Math.round(viewport.w * scale));
        int h = Math.max(1, Math.round(viewport.h * scale));

        BBSProfiler.begin(BBSProfiler.Timer.OUTLINE);

        if (this.ensure(w, h))
        {
            if (this.needsRedraw(context, w, h))
            {
                this.drawMask(world, entity, replay, w, h);
            }

            this.drawOutline(context, program, viewport);
        }

        BBSProfiler.end(BBSProfiler.Timer.OUTLINE);
    }

    private boolean needsRedraw(UIContext context, int w, int h)
    {
        UIFilmController controller = this.controller;
        int cursor = controller.panel.getCursor();
        int replay = controller.getCurrentReplayIndex();
        FilmTarget target = controller.getEditTarget();

        this.framesSince += 1;

        boolean unchanged = this.drawn
            && !controller.isPlaying()
            && this.framesSince < HEARTBEAT
            && context.mouseX == this.lastMouseX
            && context.mouseY == this.lastMouseY
            && cursor == this.lastCursor
            && replay == this.lastReplay
            && target.equals(this.lastTarget)
            && controller.panel.lastView.equals(this.lastView)
            && controller.panel.lastProjection.equals(this.lastProjection);

        if (unchanged)
        {
            return false;
        }

        this.framesSince = 0;
        this.lastMouseX = context.mouseX;
        this.lastMouseY = context.mouseY;
        this.lastCursor = cursor;
        this.lastReplay = replay;
        this.lastTarget = target;
        this.lastView.set(controller.panel.lastView);
        this.lastProjection.set(controller.panel.lastProjection);

        return true;
    }

    /** The selected actor alone into the mask, through the pick pass's own path and matrices. */
    private void drawMask(WorldRenderContext world, IEntity entity, Replay replay, int w, int h)
    {
        UIFilmController controller = this.controller;
        int previousDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];

        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.fbo);
        RenderSystem.viewport(0, 0, w, h);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        GL30.glClearBufferfv(GL11.GL_COLOR, 0, new float[] {0F, 0F, 0F, 0F});
        GL30.glClearBufferfv(GL11.GL_DEPTH, 0, new float[] {1F});

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LESS);

        MatrixStackUtils.cacheMatrices();
        RenderSystem.setProjectionMatrix(controller.panel.lastProjection, VertexSorter.BY_Z);
        RenderSystem.setInverseViewRotationMatrix(new Matrix3f(controller.panel.lastView).invert());

        MatrixStack stack = world.matrixStack();

        stack.push();
        stack.loadIdentity();
        MatrixStackUtils.multiply(stack, controller.panel.lastView);

        this.stencilMap.setup();
        this.stencilMap.setIncrement(true);

        FilmEntityRenderer.renderEntity(FilmControllerContext.instance
            .setup(controller.getEntities(), entity, replay, world)
            .transition(controller.isPlaying() ? world.tickDelta() : 0)
            .stencil(this.stencilMap)
            .relative(replay.relative.get()));

        stack.pop();
        MatrixStackUtils.restoreMatrices();

        RenderSystem.depthFunc(GL11.GL_ALWAYS);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);

        this.drawn = true;
    }

    /** The ring around what of the mask is seen, over the preview. */
    private void drawOutline(UIContext context, ShaderProgram program, Area viewport)
    {
        boolean scene = SceneDepth.isFresh();
        float[] sceneViewport = SceneDepth.getViewport();
        Matrix4f projection = this.controller.panel.lastProjection;
        int outline = BBSSettings.primaryColor(Colors.A100);

        set(program, "OutlineColor", Colors.getR(outline), Colors.getG(outline), Colors.getB(outline), 1F);
        set(program, "Texel", 1F / this.width, 1F / this.height);
        set(program, "MaskDepth", projection.m22(), projection.m32());
        set(program, "SceneDepth", SceneDepth.getA(), SceneDepth.getB());
        set(program, "SceneViewport", sceneViewport[0], sceneViewport[1], sceneViewport[2], sceneViewport[3]);

        GlUniform useScene = program.getUniform("UseScene");

        if (useScene != null)
        {
            useScene.set(scene ? 1F : 0F);
        }

        context.batcher.flush();

        Matrix4f matrix = context.batcher.getContext().getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        /* The mask is drawn bottom up, the interface top down */
        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        builder.vertex(matrix, viewport.x, viewport.y, 0F).texture(0F, 1F).next();
        builder.vertex(matrix, viewport.x, viewport.ey(), 0F).texture(0F, 0F).next();
        builder.vertex(matrix, viewport.ex(), viewport.ey(), 0F).texture(1F, 0F).next();
        builder.vertex(matrix, viewport.ex(), viewport.y, 0F).texture(1F, 1F).next();

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
            org.slf4j.LoggerFactory.getLogger(SelectionOutline.class).warn("The selected actor's outline is unavailable ({}x{} mask).", w, h);
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
