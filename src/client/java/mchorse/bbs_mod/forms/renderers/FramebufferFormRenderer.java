package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.FormRenderCapture;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.FramebufferPool;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Quad;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.function.Supplier;

public class FramebufferFormRenderer extends FormRenderer<FramebufferForm>
{
    private static final Quad quad = new Quad();
    private static final Quad uvQuad = new Quad();

    /* The box the nested forms render into: the whole texture, flipped in Y (a framebuffer's origin is its
     * top-left corner), 500 units of depth either way. One box for every framebuffer form, nested ones
     * included — it never depends on the form, so a nested render writing the same values over the outer
     * one's slice changes nothing. */
    private static final Matrix4f ORTHO = new Matrix4f().setOrtho(-1F, 1F, 1F, -1F, -500F, 500F);
    private static final RawProjectionMatrix PROJECTION = new RawProjectionMatrix("bbs_framebuffer_form");

    private static GpuBuffer lightsBuffer;
    private static GpuBufferSlice lights;

    public FramebufferFormRenderer(FramebufferForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        if (this.form.parts.getAll().isEmpty())
        {
            /* Nothing in it yet, so there is no picture to show - stand a figure in the cell
             * instead, at the size the video form draws its own placeholder at. */
            int size = 32;

            context.batcher.scaledIcon(Icons.PLAYER, Colors.WHITE, (x1 + x2 - size) / 2F, (y1 + y2 - size) / 2F, size);
        }
        /* TODO(1.21.11 render merge): in-UI framebuffer-form preview STUBBED (the port's HEAD renderInUI was
         * already an empty stub; the 1.21.1 body auto-merged in). It drew the body parts through a 3D
         * MatrixStack (context.batcher.getContext().getMatrices() — now a 2D Matrix3x2fStack) with
         * RenderSystem.depthFunc (removed). Needs the port's 2D->3D GUI matrix bridge + pipeline depth
         * state. Only the empty-state camera icon is shown for now. */
    }

    /**
     * How deep in nested framebuffer forms the render currently is. The profiler's timer keeps
     * a single start per subsystem, so only the outermost framebuffer runs it - an inner one
     * would restart the clock and the outer one's remainder would be lost.
     */
    private static int renderDepth;

    @Override
    public void renderBodyParts(FormRenderingContext context)
    {
        FramebufferPool pool = BBSModClient.getFramebuffers().getFormFramebuffers();
        Framebuffer framebuffer = pool.get(MathUtils.clamp(this.form.width.get(), 2, 4096), MathUtils.clamp(this.form.height.get(), 2, 4096));
        boolean outermost = renderDepth == 0;

        BBSProfiler.count(BBSProfiler.Section.FRAMEBUFFER_RENDERS);

        if (outermost)
        {
            BBSProfiler.begin(BBSProfiler.Timer.FRAMEBUFFER_FORMS);
        }

        renderDepth += 1;

        try
        {
            this.renderFramebuffer(context, framebuffer);
        }
        finally
        {
            renderDepth -= 1;
            pool.release(framebuffer);

            if (outermost)
            {
                BBSProfiler.end(BBSProfiler.Timer.FRAMEBUFFER_FORMS);
            }
        }
    }

    private void renderFramebuffer(FormRenderingContext context, Framebuffer framebuffer)
    {
        int x;
        int y;
        int width;
        int height;

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer viewport = stack.mallocInt(4);

            GL30.glGetIntegerv(GL30.GL_VIEWPORT, viewport);

            x = viewport.get(0);
            y = viewport.get(1);
            width = viewport.get(2);
            height = viewport.get(3);
        }

        int prevDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] scissorBox = new int[4];

        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);

        int cullFace = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);

        /* Snapshotted by hand, not through RenderSystem.backupProjectionMatrix(): that backup is a single
         * slot, and a framebuffer form nested inside another would overwrite the outer one's saved world
         * projection with the inner one's ortho. Put back below as they WERE, not as they usually are —
         * the projection type is also what the frame's translucency sorts by. */
        GpuBufferSlice previousLights = RenderSystem.getShaderLights();
        GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousProjectionType = RenderSystem.getProjectionType();

        GL30.glCullFace(GL30.GL_FRONT);

        RenderSystem.setShaderLights(lights());
        RenderSystem.setProjectionMatrix(PROJECTION.set(ORTHO), ProjectionType.ORTHOGRAPHIC);

        /* The programs read the model-view off this stack, and in the interface it carries the GUI's
         * translate(0, 0, -11000): with our ortho reaching only 500 units deep, every vertex of the parts
         * landed outside it and the buffer came out empty. In the world it is the identity already, so
         * nothing changes there. 1.21.1 needed an applyModelViewMatrix() to go with this; on 1.21.11 the
         * dynamic uniforms read the stack at draw time, so pushing the identity is the whole of it. */
        Matrix4fStack modelView = RenderSystem.getModelViewStack();

        modelView.pushMatrix();
        modelView.identity();

        framebuffer.apply();

        /* Whoever was drawing before us may have left a scissor box — the UI clips its
         * viewport that way — and it would clip this framebuffer's own pixels too. */
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        framebuffer.clear();

        context.stack.push();
        context.stack.peek().getPositionMatrix().identity();
        context.stack.peek().getNormalMatrix().identity();

        /* The nested forms render under an ortho projection into this framebuffer — deferring
         * their translucent pixels into the world's queue would replay them with the wrong
         * projection, so they render single-pass as before. */
        boolean queueWasActive = FormTranslucentQueue.suspend();

        /* Full bright on the way in: the quad that draws the finished picture applies the
         * caller's lightmap once, so letting it shade the parts inside the buffer too would
         * land the very same shading on them twice. */
        int light = context.light;

        context.light = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        try
        {
            BBSRendering.renderOffscreen(() -> super.renderBodyParts(context));
        }
        finally
        {
            context.light = light;

            FormTranslucentQueue.restore(queueWasActive);
        }

        context.stack.pop();

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);

        /* All four: whoever called us may have placed the viewport off the origin — the preview cache
         * does, sliding it so a list cell's on-screen box lands at its own framebuffer's corner. Putting
         * it back at (0, 0) drew the quad off that framebuffer and the cell showed nothing.
         *
         * TODO(1.21.11 render): 1.21.1 routes this through RenderSystem, not raw GL30.glViewport —
         * see Framebuffer#apply for why (Sodium 0.8+ swallows a later restore it thinks redundant).
         * RenderSystem.viewport() is gone here, so the raw call stands for now. */
        GL30.glViewport(x, y, width, height);

        if (scissorEnabled)
        {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        }
        else
        {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        modelView.popMatrix();

        RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);

        if (previousLights != null)
        {
            RenderSystem.setShaderLights(previousLights);
        }

        GL11.glCullFace(cullFace);

        boolean shading = !context.isPicking();

        /* The finished picture goes onto the quad through the same pair of layers the billboard draws
         * through: the shaded one in the world (formerly getRenderTypeEntityTranslucentProgram), the
         * unlit one while picking, where the buffer holds ids as colours and any shading would alter
         * them (formerly getPositionTexColorProgram).
         *
         * Both layers cull backfaces, because renderQuad emits the quad TWICE — once per side, with
         * opposite winding and normals — and counts on the GPU to keep the side facing the viewer. On
         * 1.21.1 that came for free from the global GL state; here it is the layer's own pipeline.
         *
         * The unlit layer is vanilla's position_tex_color, so the picking format loses the LIGHT element
         * the 1.21.1 one carried: that program never read it, and on 1.21.11 the buffer's format has to
         * be exactly the pipeline's own. */
        VertexFormat format = shading ? VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL : VertexFormats.POSITION_TEXTURE_COLOR;
        Supplier<RenderLayer> layer = shading ? BBSShaders::getBoundCulledModelLayer : BBSShaders::getBoundBillboardLayer;

        this.renderModel(framebuffer.getMainTexture(), format, layer, context.stack, context.overlay, context.light, context.color, context.getTransition(), !context.isPicking());
    }

    /**
     * Both lights along Z, one each way, as a Lighting UBO of our own.
     *
     * <p>The picture in here is meant to be flat, and the two vanilla lights are what a flat one is made
     * of — but pointing both at the camera lights only the faces that happen to look back at it. The
     * framebuffer renders under a Y-flipped ortho with front faces culled, so a two-sided quad (a
     * billboard draws both of its sides) keeps the side whose normal points away, and that side came out
     * at MINECRAFT_AMBIENT_LIGHT alone — 40% — while a one-sided model next to it stayed lit.</p>
     *
     * <p>1.21.1 said this in one setShaderLights(Vector3f, Vector3f); the 1.21.5 rewrite left only the
     * GpuBufferSlice overload, and the Lighting UBO behind it is exactly two std140 vec3s — so the two
     * directions are built here, the way the list previews build theirs (BbsFormGuiElementRenderer).</p>
     */
    private static GpuBufferSlice lights()
    {
        if (lights == null)
        {
            try (MemoryStack stack = MemoryStack.stackPush())
            {
                ByteBuffer data = Std140Builder.onStack(stack, DiffuseLighting.UBO_SIZE)
                    .putVec3(new Vector3f(0F, 0F, 1F))
                    .putVec3(new Vector3f(0F, 0F, -1F))
                    .get();

                /* usage 136 = UNIFORM | COPY_DST, mirroring DiffuseLighting's own Lighting UBO. */
                lightsBuffer = RenderSystem.getDevice().createBuffer(() -> "BBS framebuffer form lights UBO", 136, data);
                lights = lightsBuffer.slice(0, DiffuseLighting.UBO_SIZE);
            }
        }

        return lights;
    }

    private void renderModel(Texture texture, VertexFormat format, Supplier<RenderLayer> layer, MatrixStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer)
    {
        float w = texture.width;
        float h = texture.height;

        /* TL = top left, BR = bottom right*/
        Vector4f crop = new Vector4f(0, 0, 0, 0);
        float uvTLx = crop.x / w;
        float uvTLy = crop.y / h;
        float uvBRx = 1 - crop.z / w;
        float uvBRy = 1 - crop.w / h;

        uvQuad.p1.set(uvTLx, uvTLy, 0);
        uvQuad.p2.set(uvBRx, uvTLy, 0);
        uvQuad.p3.set(uvTLx, uvBRy, 0);
        uvQuad.p4.set(uvBRx, uvBRy, 0);

        /* Calculate quad's size (vertices, not UV). The scale sizes the quad the framebuffer is
         * shown on, not what is drawn into it — the body parts always fill the whole texture,
         * so raising it can't push them past the framebuffer's own edges. */
        float scale = this.form.scale.get() * 2F;
        float ratioX = (w > h ? h / w : 1F) * scale;
        float ratioY = (h > w ? w / h : 1F) * scale;
        float TLx = (uvTLx - 0.5F) * ratioY;
        float TLy = -(uvTLy - 0.5F) * ratioX;
        float BRx = (uvBRx - 0.5F) * ratioY;
        float BRy = -(uvBRy - 0.5F) * ratioX;

        quad.p1.set(TLx, TLy, 0);
        quad.p2.set(BRx, TLy, 0);
        quad.p3.set(TLx, BRy, 0);
        quad.p4.set(BRx, BRy, 0);

        this.renderQuad(format, texture, layer, matrices, overlay, light, overlayColor, transition, defer);
    }

    private void renderQuad(VertexFormat format, Texture texture, Supplier<RenderLayer> layer, MatrixStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer)
    {
        Color color = Color.white();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        MatrixStack.Entry entry = matrices.peek();

        color.mul(overlayColor);

        /* Was: lightmap.enable() + overlay.setupOverlayColor() + RenderSystem.setShader(shader).
         * Lightmap, overlay and the program all belong to the layer now — the BBS model layer declares
         * useLightmap()/useOverlay() and its pipeline is the shader.
         *
         * This bind is what the layer resolves from (getBoundCulledModelLayer reads the texture manager's
         * last bound texture), so it has to come BEFORE the layer is asked for. Still no raw bind on the
         * active unit: the draw binds its own samplers, and a raw one would land behind GlStateManager's
         * back - see BillboardFormRenderer. */
        BBSModClient.getTextures().bindTexture(texture);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, format);

        /* Front */
        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, entry, 1F);
        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, entry, 1F);
        this.fill(format, builder, matrix, quad.p1.x, quad.p1.y, color, uvQuad.p1.x, uvQuad.p1.y, overlay, light, entry, 1F);

        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, entry, 1F);
        this.fill(format, builder, matrix, quad.p4.x, quad.p4.y, color, uvQuad.p4.x, uvQuad.p4.y, overlay, light, entry, 1F);
        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, entry, 1F);

        /* Back */
        this.fill(format, builder, matrix, quad.p1.x, quad.p1.y, color, uvQuad.p1.x, uvQuad.p1.y, overlay, light, entry, -1F);
        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, entry, -1F);
        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, entry, -1F);

        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, entry, -1F);
        this.fill(format, builder, matrix, quad.p4.x, quad.p4.y, color, uvQuad.p4.x, uvQuad.p4.y, overlay, light, entry, -1F);
        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, entry, -1F);

        /* Was: defaultBlendFunc + enableBlend + BufferRenderer.drawWithGlobalProgram. Blend is encoded in
         * the layer's pipeline now, and the quad is submitted through the layer, which carries this
         * framebuffer's texture in its own Sampler0. */
        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            if (defer && FormTranslucentQueue.isActive())
            {
                /* The framebuffer's content is transparent-background by nature, so the whole quad defers
                 * into the sorted translucent pass. A flat quad has nothing to occlude itself with, so the
                 * deferred pass drops the depth write — except under a shaderpack, which reconstructs its
                 * shading from the depth buffer and would paint the backdrop's shadows over our face.
                 *
                 * The layer is resolved HERE, while this framebuffer's texture is still the bound one: at
                 * flush time the binding belongs to whoever drew last. The pool hands the same buffer to
                 * the next form of the same size, so several deferred quads end up showing the same
                 * content — a known trade-off of the pooled scheme, exactly as on 1.21.1. */
                RenderLayer deferred = BBSShaders.getBoundModelLayer(new BBSShaders.ModelVariant(
                    FormTranslucentQueue.PASS_SINGLE, BBSRendering.isIrisWorldForms(), true));
                Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
                Vector3f origin = modelView.transformPosition(matrix.getTranslation(new Vector3f()));

                FormTranslucentQueue.add(new FormTranslucentQueue.BufferCommand(deferred, FormRenderCapture.copy(built), origin));

                built.close();
            }
            else
            {
                layer.get().draw(built);
            }
        }
    }

    private VertexConsumer fill(VertexFormat format, VertexConsumer consumer, Matrix4f matrix, float x, float y, Color color, float u, float v, int overlay, int light, MatrixStack.Entry entry, float nz)
    {
        if (format == VertexFormats.POSITION_TEXTURE_COLOR)
        {
            /* The unlit path: vanilla position_tex_color reads exactly Position/UV0/Color. */
            return consumer.vertex(matrix, x, y, 0F).texture(u, v).color(color.r, color.g, color.b, color.a);
        }

        if (format == VertexFormats.POSITION_TEXTURE_LIGHT_COLOR)
        {
            return consumer.vertex(matrix, x, y, 0F).texture(u, v).light(light).color(color.r, color.g, color.b, color.a);
        }

        return consumer.vertex(matrix, x, y, 0F).color(color.r, color.g, color.b, color.a).texture(u, v).overlay(overlay).light(light).normal(entry, 0F, 0F, nz);
    }

    @Override
    public void collectMatrices(IEntity entity, MatrixStack stack, MatrixCache matrices, String prefix, float transition)
    {
        stack.push();
        this.applyTransforms(stack, true, transition);
        Matrix4f origin = new Matrix4f(stack.peek().getPositionMatrix());
        stack.pop();

        stack.push();
        this.applyTransforms(stack, false, transition);
        matrices.put(prefix, new Matrix4f(stack.peek().getPositionMatrix()), origin);

        float width = MathUtils.clamp(this.form.width.get(), 2, 4096);
        float height = MathUtils.clamp(this.form.height.get(), 2, 4096);
        float scale = this.form.scale.get();

        Matrix4f parent = new Matrix4f(stack.peek().getPositionMatrix());
        MatrixStack childStack = new MatrixStack();
        MatrixCache children = new MatrixCache();

        /* The body parts live in the framebuffer's ortho box (-1..1 across the whole texture),
         * and the quad that shows it is that box times the scale and the aspect ratio. */
        float scaleX = scale * (height > width ? width / height : 1F);
        float scaleY = scale * (width > height ? height / width : 1F);

        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Form form = part.getForm();

            if (form != null)
            {
                childStack.push();
                MatrixStackUtils.applyTransform(childStack, part.transform.get());

                FormUtilsClient.getRenderer(form).collectMatrices(entity, childStack, children, StringUtils.combinePaths(prefix, part.getId()), transition);

                childStack.pop();
            }
        }

        stack.pop();

        for (Map.Entry<String, MatrixCacheEntry> entry : children.entrySet())
        {
            MatrixCacheEntry child = entry.getValue();

            matrices.put(entry.getKey(), this.projectOrigin(parent, child.matrix(), scaleX, scaleY), this.projectOrigin(parent, child.origin(), scaleX, scaleY));
        }
    }

    private Matrix4f projectOrigin(Matrix4f parent, Matrix4f child, float scaleX, float scaleY)
    {
        if (child == null)
        {
            return null;
        }

        /* Flatten positions only: gizmo orientation and rotation sampling need a full basis. */
        Matrix4f projected = new Matrix4f(child).setTranslation(child.m30() * scaleX, child.m31() * scaleY, 0F);

        return new Matrix4f(parent).mul(projected);
    }
}
