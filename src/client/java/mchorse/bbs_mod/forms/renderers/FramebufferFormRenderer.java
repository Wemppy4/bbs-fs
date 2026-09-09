package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
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
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.Map;

public class FramebufferFormRenderer extends FormRenderer<FramebufferForm>
{
    private static final Quad quad = new Quad();
    private static final Quad uvQuad = new Quad();

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

        /* TODO(1.21.11 render): RenderSystem.shaderLightDirections / setShaderLights(Vector3f,Vector3f) /
         * getProjectionMatrix / setProjectionMatrix / getVertexSorting / applyModelViewMatrix were removed
         * by the 1.21.5 GPU pipeline rewrite (lighting is now a GpuBufferSlice, projection lives in
         * RenderSystem's dynamic uniforms). The 1.21.1 code saved the two shader light directions +
         * projection matrix, switched to two opposed Z lights (a billboard inside the buffer is lit from
         * its own back side, see BbsFormGuiElementRenderer#lights) and a Y-flipped ortho while rendering
         * the inner forms, applied the identity model-view (in the interface the applied matrix is the
         * GUI's translate(0, 0, -11000), which pushed every vertex out of the ±500 ortho), then restored
         * them below. Re-implement once the framebuffer render path is rebuilt on the new pipeline
         * foundation. The pure-GL state around it — cull face, scissor, viewport — is saved and restored
         * here as it was. */
        GL30.glCullFace(GL30.GL_FRONT);

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

        /* TODO(1.21.11 render): restore shader lights + projection here (see above). */
        GL11.glCullFace(cullFace);

        boolean shading = !context.isPicking();
        VertexFormat format = shading ? VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL : VertexFormats.POSITION_TEXTURE_LIGHT_COLOR;
        /* TODO(1.21.11 render): the composite still needs a pipeline. 1.21.1 picked one per pass —
         * shading ? getRenderTypeEntityTranslucentProgram : getPositionTexColorProgram — and both
         * accessors are gone. The equivalents here are BBSShaders.getBoundModelLayer() and
         * getBoundBillboardLayer(); until renderQuad submits through one the composite draws nothing. */
        this.renderModel(framebuffer.getMainTexture(), format, context.stack, context.overlay, context.light, context.color, context.getTransition(), !context.isPicking());
    }

    private void renderModel(Texture texture, VertexFormat format, MatrixStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer)
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

        this.renderQuad(format, texture, matrices, overlay, light, overlayColor, transition, defer);
    }

    private void renderQuad(VertexFormat format, Texture texture, MatrixStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer)
    {
        Color color = Color.white();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        MatrixStack.Entry entry = matrices.peek();

        color.mul(overlayColor);

        /* TODO(1.21.11 render): lightmap/overlay enable + RenderSystem.setShader were removed; lightmap,
         * overlay and the shader program are now bound through the RenderPipeline/RenderLayer samplers. */

        BBSModClient.getTextures().bindTexture(texture);

        /* No raw bind here: the draw binds its own samplers, and a bind on whatever unit is
         * active would land behind GlStateManager's back - see BillboardFormRenderer. */
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

        /* TODO(1.21.11 render): blend state now pipeline-encoded; BufferRenderer.drawWithGlobalProgram was
         * removed. The built quad must be submitted via a RenderLayer/RenderPipeline draw (e.g.
         * someRenderLayer.draw(builtBuffer)). For now we build then discard the buffer so it compiles and
         * does not leak; the framebuffer composite is a no-op until the pipeline path is wired up. */
        net.minecraft.client.render.BuiltBuffer __bbsBuilt = builder.endNullable();

        if (__bbsBuilt != null)
        {
            __bbsBuilt.close();
        }

        /* TODO(1.21.11 render): lightmap/overlay teardown was here; now pipeline-encoded. */
    }

    private VertexConsumer fill(VertexFormat format, VertexConsumer consumer, Matrix4f matrix, float x, float y, Color color, float u, float v, int overlay, int light, MatrixStack.Entry entry, float nz)
    {
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
