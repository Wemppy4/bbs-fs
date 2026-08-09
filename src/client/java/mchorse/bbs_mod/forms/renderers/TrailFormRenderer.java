package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.graphics.InverseView;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
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

    /* POSITION_TEXTURE / QUADS, depth-tested (the trail strip path enabled depth test + blend).
     * TODO(1.21.11 render): this is seeded from POSITION_TEX_COLOR_SNIPPET because there is no
     * position-tex-only snippet exposed; the buffer below writes POSITION_TEXTURE (no color attr) and
     * binds the trail texture globally (no sampler declared here). Verify at runtime: a dedicated
     * textured position-tex pipeline (sampler + matching vertex format) is likely needed before the
     * trail samples its texture correctly. */
    private static final RenderPipeline TRAIL_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/trail_strip"))
            .withVertexFormat(VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.QUADS)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    private static RenderLayer axesLayer;
    private static RenderLayer trailLayer;

    private static RenderLayer getAxesLayer()
    {
        if (axesLayer == null)
        {
            axesLayer = RenderLayer.of(BBSMod.MOD_ID + "_trail_axes",
                RenderSetup.builder(AXES_PIPELINE).translucent().build());
        }

        return axesLayer;
    }

    private static RenderLayer getTrailLayer()
    {
        if (trailLayer == null)
        {
            trailLayer = RenderLayer.of(BBSMod.MOD_ID + "_trail_strip",
                RenderSetup.builder(TRAIL_PIPELINE).translucent().build());
        }

        return trailLayer;
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
            outlineOffset *= scale; int i = 10;


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

        BBSModClient.getTextures().bindTexture(this.form.texture.get());

        stack.push();

        Trail last = null;
        Trail trail;
        /* The vertices below are in camera-relative world space; the GPU then applies
         * RenderSystem's global model-view, which since 1.21.1 already holds the camera view.
         * Build m so that (globalModelView * m) collapses to the pure camera view:
         * m = inv(globalModelView) * view. In the form editor the global model-view is
         * identity, so m stays the plain view and nothing changes. */
        Matrix4f m = stack.peek().getPositionMatrix();

        m.set(RenderSystem.getModelViewMatrix()).invert();
        m.mul(new Matrix4f(camInverse).invert());

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        /* TODO(1.21.11 render): the texture was previously bound globally via
         * BBSModClient.getTextures().bindTexture(...) and consumed by the position-tex shader. The
         * BBS trail pipeline does not declare a sampler, so the trail texture is not yet sampled.
         * Wire a textured pipeline (sampler + per-draw texture binding) when the picking/texture
         * foundation lands. */
        for (it = trails.iterator(); it.hasNext(); last = trail)
        {
            trail = it.next();

            if (last != null && !last.stop && !trail.stop)
            {
                double x1 = trail.top.x - baseX;
                double x2 = trail.bottom.x - baseX;
                double x3 = last.bottom.x - baseX;
                double x4 = last.top.x - baseX;

                double y1 = trail.top.y - baseY;
                double y2 = trail.bottom.y - baseY;
                double y3 = last.bottom.y - baseY;
                double y4 = last.top.y - baseY;

                double z1 = trail.top.z - baseZ;
                double z2 = trail.bottom.z - baseZ;
                double z3 = last.bottom.z - baseZ;
                double z4 = last.top.z - baseZ;

                if (loop)
                {
                    float u1 = trail.tick / length;
                    float u2 = last.tick / length;

                    builder.vertex(m, (float) x1, (float) y1, (float) z1).texture(u1, 0F);
                    builder.vertex(m, (float) x2, (float) y2, (float) z2).texture(u1, 1F);
                    builder.vertex(m, (float) x3, (float) y3, (float) z3).texture(u2, 1F);
                    builder.vertex(m, (float) x4, (float) y4, (float) z4).texture(u2, 0F);
                    /* Other side */
                    builder.vertex(m, (float) x4, (float) y4, (float) z4).texture(u2, 0F);
                    builder.vertex(m, (float) x3, (float) y3, (float) z3).texture(u2, 1F);
                    builder.vertex(m, (float) x2, (float) y2, (float) z2).texture(u1, 1F);
                    builder.vertex(m, (float) x1, (float) y1, (float) z1).texture(u1, 0F);
                }
                else
                {
                    float u1 = (current - trail.tick) / length;
                    float u2 = (current - last.tick) / length;

                    builder.vertex(m, (float) x1, (float) y1, (float) z1).texture(u1, 0F);
                    builder.vertex(m, (float) x2, (float) y2, (float) z2).texture(u1, 1F);
                    builder.vertex(m, (float) x3, (float) y3, (float) z3).texture(u2, 1F);
                    builder.vertex(m, (float) x4, (float) y4, (float) z4).texture(u2, 0F);
                    /* Other side */
                    builder.vertex(m, (float) x4, (float) y4, (float) z4).texture(u2, 0F);
                    builder.vertex(m, (float) x3, (float) y3, (float) z3).texture(u2, 1F);
                    builder.vertex(m, (float) x2, (float) y2, (float) z2).texture(u1, 1F);
                    builder.vertex(m, (float) x1, (float) y1, (float) z1).texture(u1, 0F);
                }
            }
            else
            {
                length = current - trail.tick;
            }
        }

        /* Was: setShader(getPositionTexProgram) + defaultBlendFunc + enableBlend +
         * drawWithGlobalProgram + enableDepthTest. The trail pipeline now encodes the shader,
         * translucent blend and depth test. */
        flush(builder, getTrailLayer());

        stack.pop();
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