package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.render.picker.BBSPickerRenderer;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Quad;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.function.Supplier;

public class BillboardFormRenderer extends FormRenderer<BillboardForm>
{
    private static final Quad quad = new Quad();
    private static final Quad uvQuad = new Quad();

    private static final Matrix4f matrix = new Matrix4f();

    public BillboardFormRenderer(BillboardForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        /* List/icon preview: submit a special GUI element so the quad draws off-screen during the GUI prepare
         * phase (two-phase GUI drops a direct immediate draw here). BbsFormGuiElementRenderer calls back into
         * renderUIPreview inside the FBO render pass — same path as ModelForm. */
        this.submitUIPreview(context, x1, y1, x2, y2);
    }

    @Override
    public void renderUIPreview(MatrixStack stack, float angle, float transition, int x1, int y1, int x2, int y2)
    {
        /* The base renderer pre-translated the stack to the cell (centre, 0.85*height down) + scale(f,f,-f);
         * apply the rest of the original getUIMatrix framing here, then the original billboard post-ops + draw
         * (identical to render3D's draw, which is confirmed working in-world). */
        Matrix4f uiMatrix = getUIPreviewMatrix(angle, y1, y2);

        this.applyTransforms(uiMatrix, transition);

        stack.push();

        MatrixStackUtils.multiply(stack, uiMatrix);
        stack.translate(0F, 1F, 0F);
        stack.scale(1.5F, 1.5F, 1.5F);
        stack.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());

        VertexFormat format = VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL;

        /* The shading (POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL) path uses the culled BBS model
         * layer (formerly GameRenderer::getRenderTypeEntityTranslucentProgram, drawn with the global
         * GL culling vanilla keeps on) — see the note in render3D. The preview stack carries a
         * negative Z scale, which flips both faces' winding at once, so culling still keeps the one
         * turned towards the viewer. */
        this.renderModel(format, BBSShaders::getBoundCulledModelLayer, null, false,
            stack,
            OverlayTexture.DEFAULT_UV, LightmapTextureManager.MAX_LIGHT_COORDINATE, Colors.WHITE,
            transition
        );

        stack.pop();
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        boolean shading = this.form.shading.get();

        if (BBSRendering.isIrisShadersEnabled())
        {
            shading = true;
        }

        /* The shaded path draws through the BBS model layer (directional light + lightmap, formerly
         * GameRenderer::getRenderTypeEntityTranslucentProgram). The no-shading path draws at full
         * texture brightness through the unlit billboard layer on vanilla's position_tex_color —
         * the same program the 1.21.1 no-shading path used. Picking still goes through
         * BBSPickerRenderer, not here.
         *
         * Both layers cull backfaces, because renderQuad emits the quad TWICE — once per side, with
         * opposite winding and opposite normals — and expects the GPU to keep only the side facing
         * the viewer. That is what the 1.21.1 draws got for free from the global GL state (vanilla
         * keeps culling on; only LabelFormRenderer and non-culling cubic models turned it off, and
         * the deferred billboard command carried cull = true). Drawn without culling, the back face
         * lands second on the exact same depth, LEQUAL lets it through, and the visible side is the
         * one lit from behind: mix_light 0.40 against the front face's ~1.0. */
        if (context.isPicking())
        {
            /* Picking draws the same quad through the picker pipeline, which writes the object index
             * instead of the texture (it still samples Sampler0 for the alpha cutout, so a cropped-out
             * corner isn't pickable). setupTarget records the index into the BBSPicker UBO, exactly where
             * the 1.21.1 getShader(...) call set the Target uniform. The no-shading picker keeps the
             * 1.21.1 POSITION_TEXTURE_LIGHT_COLOR layout — the visible unlit path dropped LIGHT when it
             * moved onto vanilla's position_tex_color, the picker shader still declares it. */
            this.setupTarget(context);

            VertexFormat pickFormat = shading ? VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL : VertexFormats.POSITION_TEXTURE_LIGHT_COLOR;
            RenderPipeline picker = shading ? BBSShaders.getPickerBillboardProgram() : BBSShaders.getPickerBillboardNoShadingProgram();

            this.renderModel(pickFormat, null, picker, false, context.stack, context.overlay, context.light, context.color, context.getTransition());

            return;
        }

        VertexFormat format = shading ? VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL : VertexFormats.POSITION_TEXTURE_COLOR;
        Supplier<RenderLayer> layer = shading ? BBSShaders::getBoundCulledModelLayer : BBSShaders::getBoundBillboardLayer;

        this.renderModel(format, layer, null, shading, context.stack, context.overlay, context.light, context.color, context.getTransition());
    }

    private void renderModel(VertexFormat format, Supplier<RenderLayer> shader, RenderPipeline picker, boolean deferrable, MatrixStack matrices, int overlay, int light, int overlayColor, float transition)
    {
        Link t = this.form.texture.get();

        if (t == null)
        {
            return;
        }

        Texture texture = BBSModClient.getTextures().getTexture(t);

        float w = texture.width;
        float h = texture.height;
        float ow = w;
        float oh = h;

        /* TL = top left, BR = bottom right*/
        Vector4f crop = this.form.crop.get();
        float uvTLx = crop.x / w;
        float uvTLy = crop.y / h;
        float uvBRx = 1 - crop.z / w;
        float uvBRy = 1 - crop.w / h;

        uvQuad.p1.set(uvTLx, uvTLy, 0);
        uvQuad.p2.set(uvBRx, uvTLy, 0);
        uvQuad.p3.set(uvTLx, uvBRy, 0);
        uvQuad.p4.set(uvBRx, uvBRy, 0);

        float uvFinalTLx = uvTLx;
        float uvFinalTLy = uvTLy;
        float uvFinalBRx = uvBRx;
        float uvFinalBRy = uvBRy;

        if (this.form.resizeCrop.get())
        {
            uvFinalTLx = uvFinalTLy = 0F;
            uvFinalBRx = uvFinalBRy = 1F;

            w = w - crop.x - crop.z;
            h = h - crop.y - crop.w;
        }

        /* Calculate quad's size (vertices, not UV) */
        float ratioX = w > h ? h / w : 1F;
        float ratioY = h > w ? w / h : 1F;
        float TLx = (uvFinalTLx - 0.5F) * ratioY;
        float TLy = -(uvFinalTLy - 0.5F) * ratioX;
        float BRx = (uvFinalBRx - 0.5F) * ratioY;
        float BRy = -(uvFinalBRy - 0.5F) * ratioX;

        quad.p1.set(TLx, TLy, 0);
        quad.p2.set(BRx, TLy, 0);
        quad.p3.set(TLx, BRy, 0);
        quad.p4.set(BRx, BRy, 0);

        float offsetX = this.form.offsetX.get();
        float offsetY = this.form.offsetY.get();
        float rotation = this.form.rotation.get();

        if (offsetX != 0F || offsetY != 0F || rotation != 0F)
        {
            float centerX = (crop.x + (ow - crop.z)) / 2F / ow;
            float centerY = (crop.y + (oh - crop.w)) / 2F / ow;

            matrix.identity()
                .translate(centerX, centerY, 0)
                .rotateZ(MathUtils.toRad(rotation))
                .translate(offsetX / ow, offsetY / oh, 0)
                .translate(-centerX, -centerY, 0);

            uvQuad.transform(matrix);
        }

        this.renderQuad(format, texture, shader, picker, deferrable, matrices, overlay, light, overlayColor, transition);
    }

    private void renderQuad(VertexFormat format, Texture texture, Supplier<RenderLayer> shader, RenderPipeline picker, boolean deferrable, MatrixStack matrices, int overlay, int light, int overlayColor, float transition)
    {
        Color color = new Color().set(overlayColor, true);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        MatrixStack.Entry entry = matrices.peek();

        FormColorBlend.blend(color, this.form.color.get(), this.form.additiveColor.get());

        if (this.form.billboard.get())
        {
            MatrixStackUtils.billboard(matrices);
        }

        /* Was: lightmap.enable() + overlay.setupOverlayColor() + RenderSystem.setShader(finalShader).
         * Lightmap/overlay are now bound by the RenderLayer (the BBS model layer uses
         * useLightmap()/useOverlay()); the shader is the layer's RenderPipeline. */
        BBSModClient.getTextures().bindTexture(texture);

        texture.bind();
        texture.setFilterMipmap(this.form.linear.get(), this.form.mipmap.get());

        /* After the bind, never before: the layer is resolved from the last bound texture, so that
         * the layer carries this texture in its own Sampler0 (as the cubic/VAO paths already do).
         * A layer with no texture only ever worked because the immediate draw happened while the
         * global GL binding still pointed at it — deferred through the item command queue, that
         * binding is long gone by execution time and the billboard samples whatever is left.
         * Picking has no layer: the picker pipeline is driven by BBSPickerRenderer, which binds
         * Sampler0 itself from the same texture. */
        RenderLayer layer = picker == null ? shader.get() : null;

        if (picker != null)
        {
            BBSPickerRenderer.setSampler0(texture);
        }

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

        /* Was: defaultBlendFunc + enableBlend + BufferRenderer.drawWithGlobalProgram. Blend is now
         * encoded in the layer's pipeline; submit the built buffer through the layer, which carries
         * this billboard's texture in its own Sampler0 (resolved after the bind above). */
        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            if (picker != null)
            {
                /* The camera is already folded into the vertices (they were built against the stack's
                 * position matrix), so the pass only needs the global model-view — the same argument the
                 * cubic/BOBJ picking draws pass. */
                BBSPickerRenderer.draw(picker, built, RenderSystem.getModelViewMatrix());
            }
            else if (deferrable)
            {
                /* A flat quad has no self-occlusion to preserve, so its deferred pass drops the depth
                 * write — that is what keeps two billboards from hiding each other once the sort has
                 * ordered them. Only the shaded path can defer: the unlit one draws through vanilla's
                 * position_tex_color, which has no PASS_MODE split to make an opaque pass out of.
                 *
                 * Under a shaderpack the depth write comes back, because the reason to drop it is gone
                 * and the cost of dropping it is severe. Gone: the pack owns transparency there, so the
                 * sorted deferred pass never runs (FormTranslucentQueue#needsSplit) and there is no sort
                 * for the missing depth to protect. Severe: a deferred pack reconstructs its shading —
                 * shadows above all — from the depth buffer, so a billboard absent from it is shaded as
                 * whatever stands BEHIND it, and the shadow of that grass or wall is painted over the
                 * billboard's face. Writing depth is also just what the vanilla cutout entity pipeline
                 * this draw now mirrors does. */
                boolean depthWrite = BBSRendering.isIrisWorldForms();

                FormTranslucentQueue.submit(built,
                    new BBSShaders.ModelVariant(FormTranslucentQueue.PASS_SINGLE, depthWrite, true),
                    texture, color.a, null,
                    new Matrix4f(RenderSystem.getModelViewMatrix()).transformPosition(matrix.getTranslation(new Vector3f())));
            }
            else
            {
                layer.draw(built);
            }
        }

        texture.setFilterMipmap(false, false);
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
}
