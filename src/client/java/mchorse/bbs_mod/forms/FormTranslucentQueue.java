package mchorse.bbs_mod.forms;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;
import mchorse.bbs_mod.cubic.render.vao.IModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Deferred translucency for forms.
 *
 * <p>Form renderers draw immediately, in entity iteration order, with depth writes on — so a
 * semi-transparent pixel drawn early would occlude anything drawn later behind it. Instead,
 * draws whose texture (or color) has semi-transparency run twice: an immediate opaque pass
 * (shader keeps only opaque texels, which write depth), and a command enqueued here. At the
 * end of the frame — after every form has drawn — {@link #flush()} replays the commands
 * sorted far-to-near with depth writes off, so translucent pixels blend over everything
 * without ever hiding it.</p>
 *
 * <p>Commands replay finished draw calls with matrices captured at enqueue time — they never
 * re-run animation, IK or physics.</p>
 */
public class FormTranslucentQueue
{
    public static final int PASS_SINGLE = 0;
    public static final int PASS_OPAQUE = 1;
    public static final int PASS_TRANSLUCENT = 2;

    private static final List<DrawCommand> commands = new ArrayList<>();
    private static boolean active;

    /**
     * Camera-space origin of the form currently being drawn through the buffered vertex
     * consumer path (blocks, items) — its translucent layers can't know their position from
     * the camera-space vertices alone. Non-null also acts as the opt-in for deferring those
     * layers; picking and UI paths never set it.
     */
    private static Vector3f sortOrigin;

    /** Non-null while a group is being recorded: added commands collect here instead of the queue. */
    private static GroupCommand group;

    public static void setSortOrigin(Vector3f origin)
    {
        sortOrigin = origin;
    }

    public static Vector3f getSortOrigin()
    {
        return sortOrigin;
    }

    public static boolean isGroupOpen()
    {
        return group != null;
    }

    /**
     * Start recording a group: until {@link #endGroup()}, added commands collect into one
     * composite command that replays them in insertion order at flush. For forms whose parts
     * depend on each other's depth (a label's text against its background) — the group sorts
     * against other forms as a whole, while its internals keep their original draw order.
     */
    public static void beginGroup(Vector3f cameraSpaceOrigin, boolean cull)
    {
        group = new GroupCommand(cameraSpaceOrigin, cull);
        sortOrigin = new Vector3f(cameraSpaceOrigin);
    }

    public static void endGroup()
    {
        GroupCommand finished = group;

        group = null;
        sortOrigin = null;

        if (finished != null && !finished.children.isEmpty())
        {
            add(finished);
        }
    }

    public static boolean isActive()
    {
        /* The Iris shadow pass re-renders the scene into the shadow map mid-frame: forms there
         * must draw immediately (the shadow map needs their full geometry), and nothing may
         * enqueue — the queue belongs to the main pass. */
        return active && !BBSRendering.isIrisShadowPass();
    }

    /**
     * Whether a draw with this shader/texture/alpha must split into opaque + deferred
     * translucent passes. False outside an active queue scope (UI previews, first-person arm),
     * during picking (the stencil needs every pixel), and on shaders without the PassMode
     * uniform (vanilla programs — the Iris path).
     */
    public static boolean needsSplit(ShaderProgram shader, StencilMap stencilMap, Texture texture, float alpha)
    {
        boolean translucent = alpha < 1F || (texture != null && texture.hasTranslucency());

        return translucent && isActive() && stencilMap == null && shader.getUniform("PassMode") != null;
    }

    /**
     * The Iris path: its patched vanilla programs have no PassMode uniform, so the draw can't
     * split into opaque/translucent halves — instead the whole draw defers as-is. The far-to-near
     * sort alone fixes the between-model blending order, and depth writes stay on during the
     * replay so the model still occludes itself correctly.
     */
    public static boolean needsWholeDefer(ShaderProgram shader, StencilMap stencilMap, Texture texture, float alpha)
    {
        boolean translucent = alpha < 1F || (texture != null && texture.hasTranslucency());

        return translucent && isActive() && stencilMap == null && shader.getUniform("PassMode") == null
            && BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld();
    }

    public static void setPassMode(ShaderProgram shader, int mode)
    {
        GlUniform uniform = shader.getUniform("PassMode");

        if (uniform != null)
        {
            uniform.set(mode);
        }
    }

    public static void add(DrawCommand command)
    {
        if (group != null && command != group)
        {
            group.children.add(command);
        }
        else if (active)
        {
            commands.add(command);
        }
        else
        {
            /* No scope to defer into — draw right away so no pixels are lost. */
            command.draw();
            command.release();
        }
    }

    public static void begin()
    {
        release();

        active = true;
    }

    /**
     * Temporarily deactivate the queue: nested offscreen renders (framebuffer forms) run under
     * their own projection mid-frame and must not enqueue into the world's flush. Returns the
     * previous state for {@link #restore(boolean)}.
     */
    public static boolean suspend()
    {
        boolean wasActive = active;

        active = false;

        return wasActive;
    }

    public static void restore(boolean wasActive)
    {
        active = wasActive;
    }

    /**
     * Draw all deferred translucent commands, far to near, without depth writes. Deactivates
     * the queue — later draws (the first-person hand) fall back to single-pass rendering.
     */
    public static void flush()
    {
        active = false;

        if (commands.isEmpty())
        {
            return;
        }

        commands.sort((a, b) -> Float.compare(b.distanceSq, a.distanceSq));

        GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;

        gameRenderer.getLightmapTextureManager().enable();
        gameRenderer.getOverlayTexture().setupOverlayColor();

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (DrawCommand command : commands)
        {
            /* Split-pass commands hold only semi-transparent texels and never write depth;
             * whole-draw commands (the Iris path) carry the entire mesh, so they keep depth
             * writes for correct self-occlusion — the sort already ordered them between models. */
            RenderSystem.depthMask(command.depthWrite);

            if (command.cull)
            {
                RenderSystem.enableCull();
            }
            else
            {
                RenderSystem.disableCull();
            }

            try
            {
                command.draw();
            }
            catch (Exception e)
            {}
            finally
            {
                command.release();
            }
        }

        commands.clear();

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();

        gameRenderer.getLightmapTextureManager().disable();
        gameRenderer.getOverlayTexture().teardownOverlayColor();
    }

    /** Drop any leftover commands (e.g. a frame whose flush point never ran) and free their resources. */
    private static void release()
    {
        for (DrawCommand command : commands)
        {
            command.release();
        }

        commands.clear();

        /* A group left open by an aborted render must not leak into the next frame. */
        if (group != null)
        {
            group.release();
            group = null;
        }

        sortOrigin = null;
    }

    public static abstract class DrawCommand
    {
        public final float distanceSq;
        public final boolean cull;
        public final boolean depthWrite;

        /** The origin must be camera-space (a captured model-view translation) — it's the sort key. */
        protected DrawCommand(Vector3f cameraSpaceOrigin, boolean cull, boolean depthWrite)
        {
            this.distanceSq = cameraSpaceOrigin.lengthSquared();
            this.cull = cull;
            this.depthWrite = depthWrite;
        }

        public abstract void draw();

        public void release()
        {}
    }

    /**
     * Replays a cubic model group's static VAO. The two-pass form draws the BBS model shader's
     * translucent pass without depth writes; the whole-draw form (Iris) replays the entire draw
     * with its captured program and keeps depth writes.
     */
    public static class ModelVAOCommand extends DrawCommand
    {
        private final IModelVAO vao;
        private final Supplier<ShaderProgram> shader;
        private final int passMode;
        private final Texture texture;
        private final Matrix4f modelView;
        private final Matrix3f normalMat;
        private final float r, g, b, a;
        private final int light;
        private final int overlay;

        /** The two-pass translucent replay (BBS model shader). */
        public ModelVAOCommand(IModelVAO vao, Texture texture, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay, boolean cull)
        {
            this(vao, BBSShaders::getModel, PASS_TRANSLUCENT, false, texture, modelView, normalMat, r, g, b, a, light, overlay, cull);
        }

        public ModelVAOCommand(IModelVAO vao, Supplier<ShaderProgram> shader, int passMode, boolean depthWrite, Texture texture, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay, boolean cull)
        {
            super(modelView.getTranslation(new Vector3f()), cull, depthWrite);

            this.vao = vao;
            this.shader = shader;
            this.passMode = passMode;
            this.texture = texture;
            this.modelView = modelView;
            this.normalMat = normalMat;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.light = light;
            this.overlay = overlay;
        }

        @Override
        public void draw()
        {
            ShaderProgram shader = this.shader.get();

            if (this.texture != null)
            {
                BBSModClient.getTextures().bindTexture(this.texture);
            }

            setPassMode(shader, this.passMode);
            ModelVAORenderer.render(shader, this.vao, this.modelView, this.normalMat, this.r, this.g, this.b, this.a, this.light, this.overlay);
            setPassMode(shader, PASS_SINGLE);
        }
    }

    /**
     * Replays a BOBJ mesh. Its VBO holds CPU-skinned vertices and is shared between actors
     * using the same model, so the command keeps an armature snapshot: if someone re-skinned
     * the VBO since capture (the upload counter moved), it re-uploads from the snapshot first.
     */
    public static class BOBJCommand extends DrawCommand
    {
        private final BOBJModelVAO vao;
        private final Supplier<ShaderProgram> shader;
        private final int passMode;
        private final Matrix4f[] armatureSnapshot;
        private final int uploadCount;
        private final Texture texture;
        private final Matrix4f modelView;
        private final Matrix3f normalMat;
        private final float r, g, b, a;
        private final int light;
        private final int overlay;

        /** The two-pass translucent replay (BBS model shader). */
        public BOBJCommand(BOBJModelVAO vao, Matrix4f[] armatureSnapshot, int uploadCount, Texture texture, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay, boolean cull)
        {
            this(vao, BBSShaders::getModel, PASS_TRANSLUCENT, false, armatureSnapshot, uploadCount, texture, modelView, normalMat, r, g, b, a, light, overlay, cull);
        }

        public BOBJCommand(BOBJModelVAO vao, Supplier<ShaderProgram> shader, int passMode, boolean depthWrite, Matrix4f[] armatureSnapshot, int uploadCount, Texture texture, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay, boolean cull)
        {
            super(modelView.getTranslation(new Vector3f()), cull, depthWrite);

            this.vao = vao;
            this.shader = shader;
            this.passMode = passMode;
            this.armatureSnapshot = armatureSnapshot;
            this.uploadCount = uploadCount;
            this.texture = texture;
            this.modelView = modelView;
            this.normalMat = normalMat;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.light = light;
            this.overlay = overlay;
        }

        @Override
        public void draw()
        {
            ShaderProgram shader = this.shader.get();

            if (this.texture != null)
            {
                BBSModClient.getTextures().bindTexture(this.texture);
            }

            if (this.vao.getUploadCount() != this.uploadCount)
            {
                this.vao.updateMesh(null, this.armatureSnapshot);
            }

            setPassMode(shader, this.passMode);
            this.vao.render(shader, this.modelView, this.normalMat, this.r, this.g, this.b, this.a, null, this.light, this.overlay);
            setPassMode(shader, PASS_SINGLE);
        }
    }

    /**
     * A recorded sequence of commands that replays as one unit in its original internal order.
     * Sorts against other commands by its own origin; children's origins are ignored.
     */
    public static class GroupCommand extends DrawCommand
    {
        private final List<DrawCommand> children = new ArrayList<>();

        public GroupCommand(Vector3f cameraSpaceOrigin, boolean cull)
        {
            /* Depth writes stay on: the parts depend on each other's depth (text over its
             * background), and the far-to-near sort already ordered the group among forms. */
            super(cameraSpaceOrigin, cull, true);
        }

        @Override
        public void draw()
        {
            for (DrawCommand child : this.children)
            {
                child.draw();
            }
        }

        @Override
        public void release()
        {
            for (DrawCommand child : this.children)
            {
                child.release();
            }
        }
    }

    /**
     * Replays a buffered vanilla render layer (translucent block/item geometry, label text).
     * The layer's own startDrawing binds its texture and shader — with Iris that routes
     * through its override naturally.
     */
    public static class RenderLayerCommand extends DrawCommand
    {
        private final net.minecraft.client.render.RenderLayer layer;
        private final VertexBuffer buffer;
        private final Matrix4f modelView;

        public RenderLayerCommand(net.minecraft.client.render.RenderLayer layer, VertexBuffer buffer, Matrix4f modelView, Vector3f cameraSpaceOrigin, boolean depthWrite)
        {
            super(cameraSpaceOrigin, true, depthWrite);

            this.layer = layer;
            this.buffer = buffer;
            this.modelView = modelView;
        }

        @Override
        public void draw()
        {
            this.layer.startDrawing();

            /* startDrawing applied the layer's own write mask — re-assert ours. */
            RenderSystem.depthMask(this.depthWrite);

            this.buffer.bind();
            this.buffer.draw(this.modelView, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
            VertexBuffer.unbind();

            this.layer.endDrawing();
        }

        @Override
        public void release()
        {
            this.buffer.close();
        }
    }

    /**
     * Replays geometry captured into a retained {@link VertexBuffer} (the CPU model path and
     * billboards). The buffer is owned by the command and freed after the flush.
     */
    public static class VertexBufferCommand extends DrawCommand
    {
        private final VertexBuffer buffer;
        private final Supplier<ShaderProgram> shader;
        private final Texture texture;
        private final Matrix4f modelView;
        private final Matrix3f normalMat;
        private final Runnable preDraw;
        private final Runnable postDraw;

        public VertexBufferCommand(VertexBuffer buffer, Supplier<ShaderProgram> shader, Texture texture, Matrix4f modelView, Matrix3f normalMat, Vector3f cameraSpaceOrigin, boolean cull, Runnable preDraw, Runnable postDraw)
        {
            super(cameraSpaceOrigin, cull, false);

            this.buffer = buffer;
            this.shader = shader;
            this.texture = texture;
            this.modelView = modelView;
            this.normalMat = normalMat;
            this.preDraw = preDraw;
            this.postDraw = postDraw;
        }

        @Override
        public void draw()
        {
            ShaderProgram program = this.shader.get();

            if (this.texture != null)
            {
                BBSModClient.getTextures().bindTexture(this.texture);
            }

            if (this.preDraw != null)
            {
                this.preDraw.run();
            }

            if (this.normalMat != null)
            {
                GlUniform normalUniform = program.getUniform("NormalMat");

                if (normalUniform != null)
                {
                    normalUniform.set(this.normalMat);
                }
            }

            setPassMode(program, PASS_TRANSLUCENT);

            this.buffer.bind();
            this.buffer.draw(this.modelView, RenderSystem.getProjectionMatrix(), program);
            VertexBuffer.unbind();

            setPassMode(program, PASS_SINGLE);

            if (this.postDraw != null)
            {
                this.postDraw.run();
            }
        }

        @Override
        public void release()
        {
            this.buffer.close();
        }
    }
}
