package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.render.picker.BBSPickerRenderer;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class ModelVAORenderer
{
    /**
    /**
     * The full model-view a draw issued right now would use. Kept from 1.21.1 because callers still
     * capture the frame's model-view for their own maths (the deferred translucent queue it was
     * written for is disabled on 1.21.11).
     */
    public static Matrix4f captureModelView(MatrixStack stack)
    {
        return new Matrix4f(RenderSystem.getModelViewMatrix()).mul(stack.peek().getPositionMatrix());
    }

    /**
     * Draw a static {@link ModelVAO} through the immediate model RenderLayer. The 1.21.5+ rewrite
     * removed ShaderProgram.bind()/unbind() and the imperative uniform/sampler/fog/light setup; the
     * built-in uniforms now live in the std140 UBOs (DynamicTransforms / Projection / Fog / Lighting)
     * that {@link BBSShaders#getModelLayer()} uploads per draw. The geometry is baked CPU-side into a
     * BufferBuilder (matching the cubic immediate path) and submitted through that layer.
     *
     * <p>{@code cull} carries the model's own culling flag ({@code ModelInstance.isCulling()}); on
     * 1.21.1 it toggled the global GL cull around the draw, now it picks the layer variant.
     */
    public static void render(ModelVAO modelVAO, MatrixStack stack, float r, float g, float b, float a, int light, int overlay, boolean cull)
    {
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

        modelVAO.writeImmediate(builder, stack, r, g, b, a, light, overlay);

        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            /* Solid geometry: depth write stays on, so a deferred translucent pass still
             * self-occludes (see FormTranslucentQueue). */
            FormTranslucentQueue.submit(built,
                new BBSShaders.ModelVariant(FormTranslucentQueue.PASS_SINGLE, true, cull),
                BBSModClient.getTextures().getLastBound(), a, null,
                captureModelView(stack).getTranslation(new Vector3f()));
        }
    }

    /**
     * Draw the mesh through a picker {@link RenderPipeline} instead of a RenderLayer. The picker shaders
     * need the custom BBSPicker UBO (the Target index), which the immediate RenderLayer path cannot carry,
     * so the draw is driven by {@link BBSPickerRenderer}. The caller records the Target and Sampler0 first.
     */
    public static void renderPicking(ModelVAO modelVAO, MatrixStack stack, float r, float g, float b, float a, int light, int overlay, RenderPipeline picker)
    {
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

        modelVAO.writeImmediate(builder, stack, r, g, b, a, light, overlay);

        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            /* Identity-free: the camera is already baked into the vertices by writeImmediate, so the pass
             * gets the global model-view, the same argument the cubic/BOBJ picking draws pass. */
            BBSPickerRenderer.draw(picker, built, RenderSystem.getModelViewMatrix());
        }
    }
}
