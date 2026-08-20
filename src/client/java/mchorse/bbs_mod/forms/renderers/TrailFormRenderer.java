package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.graphics.InverseView;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class TrailFormRenderer extends FormRenderer<TrailForm> implements ITickable
{
    private int tick;
    private final Map<FormRenderType, ArrayDeque<Trail>> record = new HashMap<>();

    /* ----------------------------------------------------------------------------------------
     * 1.21.11 render: RenderSystem.setShader + BufferRenderer.drawWithGlobalProgram were removed.
     * Immediate-mode geometry is now built into a BufferBuilder, finished into a BuiltBuffer and
     * submitted through a RenderLayer carrying a RenderPipeline. These BBS-owned pipelines replace
     * the old GameRenderer::getPositionColorProgram / getPositionTexProgram usage.
     * ---------------------------------------------------------------------------------------- */
    private static final BlendFunction BLEND = BlendFunction.TRANSLUCENT;

    /* POSITION_COLOR / TRIANGLES, no depth test (the axes path did RenderSystem.disableDepthTest()). */
    private static final RenderPipeline AXES_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/trail_axes"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    /* The strip itself no longer owns a pipeline. Its old one was seeded from
     * POSITION_TEX_COLOR_SNIPPET while the buffer wrote POSITION_TEXTURE — the shader's Color
     * attribute was never fed, and a disabled GL attribute reads the current generic value
     * ((0,0,0,1) by default, and whatever a driver last latched otherwise): the trail drew BLACK and
     * shimmered with unrelated draws as the camera moved. It also declared no Sampler0, leaving the
     * texture to the stale global binding, and withCull(false) kept BOTH of the strip's explicit
     * front/back faces alive on equal depth. All three are the billboard's already-fixed diseases,
     * so the strip now draws exactly like the billboard: the unlit textured billboard layer
     * (vanilla position_tex_color, full brightness, cull on, texture in the layer's own Sampler0),
     * and the culled model layer under a shaderpack — see render3D. */

    private static RenderLayer axesLayer;

    private static RenderLayer getAxesLayer()
    {
        if (axesLayer == null)
        {
            axesLayer = RenderLayer.of(BBSMod.MOD_ID + "_trail_axes",
                RenderSetup.builder(AXES_PIPELINE).translucent().build());
        }

        return axesLayer;
    }

    /** Finish a buffer and submit it through the given layer (no-op on an empty buffer). */
    private static void flush(BufferBuilder builder, RenderLayer layer)
    {
        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            /* TODO(1.21.11 render): verify at runtime. RenderLayer.draw uploads + draws with the
             * layer pipeline; previously this was BufferRenderer.drawWithGlobalProgram. */
            layer.draw(built);
        }
    }

    public TrailFormRenderer(TrailForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        Texture texture = context.render.getTextures().getTexture(this.form.texture.get());

        float min = Math.min(texture.width, texture.height);
        int ow = (x2 - x1) - 4;
        int oh = (y2 - y1) - 4;

        int w = (int) ((texture.width / min) * ow);
        int h = (int) ((texture.height / min) * ow);

        int x = x1 + (ow - w) / 2 + 2;
        int y = y1 + (oh - h) / 2 + 2;

        context.batcher.fullTexturedBox(texture, x, y, w, h);
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        super.render3D(context);

        if (BBSRendering.isIrisShadowPass() || context.type == FormRenderType.ITEM_INVENTORY)
        {
            return;
        }

        if (context.modelRenderer || context.ui)
        {
            MatrixStack stack = context.stack;
            float scale = BBSSettings.axesScale.get();
            float axisSize = 1F;
            float axisOffset = 0.01F;
            float outlineSize = 1.01F;
            float outlineOffset = 0.02F;

            axisOffset *= scale;
            outlineOffset *= scale;

            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

            Draw.fillBox(builder, stack, -outlineOffset, -outlineSize, -outlineOffset, outlineOffset, outlineSize, outlineOffset, 0, 0, 0);
            Draw.fillBox(builder, stack, -axisOffset, -axisSize, -axisOffset, axisOffset, axisSize, axisOffset, 0, 1, 0);

            /* Was: RenderSystem.setShader(getPositionColorProgram) + disableDepthTest +
             * BufferRenderer.drawWithGlobalProgram. The no-depth POSITION_COLOR pipeline now
             * encodes both the shader and the disabled depth test. */
            flush(builder, getAxesLayer());

            return;
        }

        if (!BBSRendering.isRenderingWorld())
        {
            return;
        }

        MatrixStack stack = context.stack;
        Matrix4f camInverse = new Matrix4f(InverseView.get());

        Camera camera = context.camera;
        double baseX = camera.position.x;
        double baseY = camera.position.y;
        double baseZ = camera.position.z;

        float current = (float) this.tick + context.transition;
        ArrayDeque<Trail> trails = this.record.computeIfAbsent(context.type, (k) -> new ArrayDeque<>());

        if (!this.form.paused.get())
        {
            /* Since 1.21.1 the camera view lives in RenderSystem's global model-view and the
             * stack base is identity, so fold it back in to recover the FULL model-view
             * (view * translate(-cam) * formChain). Without it camInverse over-rotates the
             * sampled point and the recorded world position ends up depending on the camera,
             * which makes the trail smear whenever the camera moves rather than the object. */
            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(stack.peek().getPositionMatrix());

            Vector4f top = new Vector4f(0F, 1F, 0F, 1F);
            Vector4f bottom = new Vector4f(0F, -1F, 0F, 1F);

            modelView.transform(top);
            modelView.transform(bottom);
            camInverse.transform(top);
            camInverse.transform(bottom);

            top.mul(1F / top.w);
            bottom.mul(1F / bottom.w);

            Trail record = new Trail();

            record.tick = current;
            record.top = new Vector3d(top.x + baseX, top.y + baseY, top.z + baseZ);
            record.bottom = new Vector3d(bottom.x + baseX, bottom.y + baseY, bottom.z + baseZ);
            record.stop = new Vector3f(top.x - bottom.x, top.y - bottom.y, top.z - bottom.z).lengthSquared() < 1.0E-4D;

            trails.addLast(record);
        }

        boolean loop = this.form.loop.get();
        float length = this.form.length.get();
        float end = current - length;
        Iterator<Trail> it = trails.iterator();
        boolean render = false;
        boolean lastStop = true;

        while (it.hasNext())
        {
            Trail trail = it.next();

            if (trail.tick < end)
            {
                it.remove();
            }
            else
            {
                render |= !trail.stop && !lastStop;
                lastStop = trail.stop;
            }
        }

        if (!render || trails.size() <= 1 || !(length > 0.001D))
        {
            return;
        }

        Texture texture = BBSModClient.getTextures().getTexture(this.form.texture.get());

        /* Bind through the BBS texture manager BEFORE resolving a layer: both layers below are
         * resolved from the last bound texture, so the strip's texture rides in the layer's own
         * Sampler0 (the billboard's rule — a stale global binding is all a sampler-less layer had). */
        BBSModClient.getTextures().bindTexture(texture);

        /* Under a shaderpack only draws carrying the pack's programs survive the end-of-frame
         * composite, so the strip follows the billboard: the full entity format through the culled
         * world model layer. Everywhere else it keeps the 1.21.1 look — vanilla position_tex_color
         * at full texture brightness, no directional light, no lightmap. Both layers cull, because
         * the strip emits every segment TWICE (opposite winding, opposite normals) and expects the
         * GPU to drop the side facing away — drawn without culling the back lands on equal depth,
         * LEQUAL lets it through, and the two sides double-blend and shimmer with the viewpoint. */
        boolean packed = BBSRendering.isIrisWorldForms();
        VertexFormat format = packed ? VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL : VertexFormats.POSITION_TEXTURE_COLOR;

        stack.push();

        Trail last = null;
        Trail trail;

        /* The form's camera-relative world position, taken before the stack top is repurposed below —
         * it becomes the camera-space sort origin if the packed draw ends up deferred. */
        Vector3f origin = stack.peek().getPositionMatrix().getTranslation(new Vector3f());

        /* The vertices below are in camera-relative world space; the GPU then applies
         * RenderSystem's global model-view, which since 1.21.1 already holds the camera view.
         * Build m so that (globalModelView * m) collapses to the pure camera view:
         * m = inv(globalModelView) * view. In the form editor the global model-view is
         * identity, so m stays the plain view and nothing changes. */
        Matrix4f m = stack.peek().getPositionMatrix();

        m.set(RenderSystem.getModelViewMatrix()).invert();
        m.mul(new Matrix4f(camInverse).invert());

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, format);

        for (it = trails.iterator(); it.hasNext(); last = trail)
        {
            trail = it.next();

            if (last != null && !last.stop && !trail.stop)
            {
                float x1 = (float) (trail.top.x - baseX);
                float x2 = (float) (trail.bottom.x - baseX);
                float x3 = (float) (last.bottom.x - baseX);
                float x4 = (float) (last.top.x - baseX);

                float y1 = (float) (trail.top.y - baseY);
                float y2 = (float) (trail.bottom.y - baseY);
                float y3 = (float) (last.bottom.y - baseY);
                float y4 = (float) (last.top.y - baseY);

                float z1 = (float) (trail.top.z - baseZ);
                float z2 = (float) (trail.bottom.z - baseZ);
                float z3 = (float) (last.bottom.z - baseZ);
                float z4 = (float) (last.top.z - baseZ);

                float u1;
                float u2;

                if (loop)
                {
                    u1 = trail.tick / length;
                    u2 = last.tick / length;
                }
                else
                {
                    u1 = (current - trail.tick) / length;
                    u2 = (current - last.tick) / length;
                }

                /* World-space face normal of the segment (the model shader feeds raw normals into
                 * its world-oriented light mix). Degenerate segments keep a harmless up vector. */
                float ax = x2 - x1;
                float ay = y2 - y1;
                float az = z2 - z1;
                float bx = x3 - x1;
                float by = y3 - y1;
                float bz = z3 - z1;
                float nx = ay * bz - az * by;
                float ny = az * bx - ax * bz;
                float nz = ax * by - ay * bx;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

                if (len > 1.0E-6F)
                {
                    nx /= len;
                    ny /= len;
                    nz /= len;
                }
                else
                {
                    nx = nz = 0F;
                    ny = 1F;
                }

                /* Front (two triangles of the 1-2-3-4 quad) */
                this.fill(format, builder, m, x1, y1, z1, u1, 0F, nx, ny, nz);
                this.fill(format, builder, m, x2, y2, z2, u1, 1F, nx, ny, nz);
                this.fill(format, builder, m, x3, y3, z3, u2, 1F, nx, ny, nz);

                this.fill(format, builder, m, x1, y1, z1, u1, 0F, nx, ny, nz);
                this.fill(format, builder, m, x3, y3, z3, u2, 1F, nx, ny, nz);
                this.fill(format, builder, m, x4, y4, z4, u2, 0F, nx, ny, nz);

                /* Back (reversed winding, reversed normal; culling keeps the side facing the viewer) */
                this.fill(format, builder, m, x4, y4, z4, u2, 0F, -nx, -ny, -nz);
                this.fill(format, builder, m, x3, y3, z3, u2, 1F, -nx, -ny, -nz);
                this.fill(format, builder, m, x2, y2, z2, u1, 1F, -nx, -ny, -nz);

                this.fill(format, builder, m, x4, y4, z4, u2, 0F, -nx, -ny, -nz);
                this.fill(format, builder, m, x2, y2, z2, u1, 1F, -nx, -ny, -nz);
                this.fill(format, builder, m, x1, y1, z1, u1, 0F, -nx, -ny, -nz);
            }
            else
            {
                length = current - trail.tick;
            }
        }

        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            if (packed)
            {
                /* The submit resolves the culled world model layer from the bound texture and lets
                 * the queue decide (under a pack that is a single immediate draw — the pack owns
                 * transparency, see FormTranslucentQueue#needsSplit). */
                FormTranslucentQueue.submit(built,
                    new BBSShaders.ModelVariant(FormTranslucentQueue.PASS_SINGLE, true, true),
                    texture, 1F, null,
                    new Matrix4f(RenderSystem.getModelViewMatrix()).transformPosition(origin));
            }
            else
            {
                BBSShaders.getBoundBillboardLayer().draw(built);
            }
        }

        stack.pop();
    }

    /**
     * One strip vertex in the active format: the unlit path is vanilla position_tex_color (exactly
     * what the 1.21.1 trail drew with — full brightness, no colour property to apply), the packed
     * path is the model format the shaderpack's entity program reads. Trail geometry is unlit by
     * nature, so the packed path goes out at full lightmap brightness.
     */
    private void fill(VertexFormat format, BufferBuilder builder, Matrix4f m, float x, float y, float z, float u, float v, float nx, float ny, float nz)
    {
        if (format == VertexFormats.POSITION_TEXTURE_COLOR)
        {
            builder.vertex(m, x, y, z).texture(u, v).color(1F, 1F, 1F, 1F);

            return;
        }

        builder.vertex(m, x, y, z)
            .color(1F, 1F, 1F, 1F)
            .texture(u, v)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
            .normal(nx, ny, nz);
    }

    @Override
    public void tick(IEntity entity)
    {
        this.tick += 1;
    }

    public static class Trail
    {
        public float tick;
        public Vector3d top;
        public Vector3d bottom;
        public boolean stop;
    }
}