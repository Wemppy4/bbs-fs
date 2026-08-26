package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.BBSModClient;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.cubic.weld.WeldBinding;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormMaterial;
import mchorse.bbs_mod.forms.renderers.utils.FormMaterialLevels;
import mchorse.bbs_mod.forms.renderers.utils.FormOverlay;
import mchorse.bbs_mod.forms.renderers.utils.FormPbr;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class CubicVAORenderer extends CubicCubeRenderer
{
    private ShaderProgram program;
    private ModelInstance model;
    private Function<String, Link> textureResolver;

    /**
     * Non-null puts the renderer in hybrid mode (a welded model): these groups — and any group with no baked VAO —
     * fall through to the CPU immediate path so their welded cubes can deform against a live neighbour, while every
     * other group still rides its VAO on the GPU. Null keeps the plain all-VAO behaviour for unwelded models.
     */
    private Set<ModelGroup> weldedGroups;

    public CubicVAORenderer(ShaderProgram program, ModelInstance model, int light, int overlay, StencilMap stencilMap, ShapeKeys shapeKeys, Function<String, Link> textureResolver)
    {
        super(light, overlay, stencilMap, shapeKeys);

        this.program = program;
        this.model = model;
        this.textureResolver = textureResolver;
    }

    public void setWeldedGroups(Set<ModelGroup> weldedGroups)
    {
        this.weldedGroups = weldedGroups;
    }

    @Override
    public boolean renderGroup(BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model)
    {
        Map<String, ModelVAO> groupVaos = this.model.getVaos().get(group);

        if (this.weldedGroups != null)
        {
            /* A welded bone tessellates on the CPU only while its seam actually bends — at rest it rides
             * its VAO like everything else. Groups with no VAO (shape-keyed meshes) always render immediate. */
            boolean welded = this.weldedGroups.contains(group) && WeldBinding.hasActiveSeam(this.welds, group);

            if (welded || groupVaos == null || groupVaos.isEmpty())
            {
                return super.renderGroup(builder, stack, group, model);
            }
        }

        if (groupVaos == null || groupVaos.isEmpty() || !group.visible)
        {
            return false;
        }

        float groupR = this.r * group.color.r;
        float groupG = this.g * group.color.g;
        float groupB = this.b * group.color.b;
        float groupA = this.a * group.color.a;
        int groupLight = this.light;

        if (this.stencilMap != null)
        {
            groupLight = this.stencilMap.increment ? group.index : 0;
        }
        else
        {
            int u = (int) Lerps.lerp(groupLight & '\uffff', LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, MathUtils.clamp(group.lighting, 0F, 1F));
            int v = groupLight >> 16 & '\uffff';

            groupLight = u | v << 16;
        }

        /* The material level of the material tab: color/glow/overlay/culling per material of this
         * bone's draws, layered between the form level (already in this.r/g/b/a and this.light) and
         * the bone level (group.color/lighting/overlay above). Picking keeps everything neutral
         * (light carries stencil ids there), and a hurt flash in the overlay UV wins over the color
         * overlay by design. */
        ModelForm modelForm = this.model.form instanceof ModelForm form ? form : null;
        boolean plain = this.stencilMap == null;
        boolean hurtFlash = this.overlay != OverlayTexture.DEFAULT_UV;

        /* One draw per material; bind that material's resolved texture before each. */
        for (Map.Entry<String, ModelVAO> entry : groupVaos.entrySet())
        {
            String material = entry.getKey();
            Texture texture = null;

            if (this.textureResolver != null)
            {
                Link link = this.textureResolver.apply(material);

                if (link != null)
                {
                    texture = BBSModClient.getTextures().getTexture(link);
                    texture = FormPbr.resolveAlbedo(modelForm, material, link, texture);
                    BBSModClient.getTextures().bindTexture(texture);
                }
            }

            if (texture == null)
            {
                /* No per-material override — the draw uses the form's base texture bound earlier. */
                texture = BBSModClient.getTextures().getLastBound();
            }

            float r = groupR;
            float g = groupG;
            float b = groupB;
            float a = groupA;
            int light = groupLight;
            Color overlayColor = null;
            int culling = FormMaterial.CULLING_MODEL;

            if (plain)
            {
                if (modelForm != null)
                {
                    Color materialColor = FormMaterialLevels.materialColor(modelForm, material);

                    if (materialColor != null)
                    {
                        r *= materialColor.r;
                        g *= materialColor.g;
                        b *= materialColor.b;
                        a *= materialColor.a;
                    }

                    float glow = FormMaterialLevels.materialGlow(modelForm, material);

                    if (glow > 0F)
                    {
                        int u = (int) Lerps.lerp(light & '\uffff', LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, glow);

                        light = u | (light >> 16 & '\uffff') << 16;
                    }

                    culling = FormMaterialLevels.materialCulling(modelForm, material);
                }

                if (!hurtFlash)
                {
                    overlayColor = FormOverlay.combine(modelForm, material, group);
                }
            }

            int overlay = overlayColor != null ? 0 : this.overlay;
            int previousOverlayTexture = overlayColor != null ? FormOverlay.bind(overlayColor) : 0;
            boolean modelCulling = this.model.isCulling();
            boolean cullOverride = culling != FormMaterial.CULLING_MODEL && (culling == FormMaterial.CULLING_ON) != modelCulling;

            if (cullOverride)
            {
                if (culling == FormMaterial.CULLING_ON) RenderSystem.enableCull();
                else RenderSystem.disableCull();
            }

            if (FormTranslucentQueue.needsSplit(this.program, this.stencilMap, texture, a))
            {
                Matrix4f modelView = ModelVAORenderer.captureModelView(stack);
                Matrix3f normalMat = new Matrix3f(stack.peek().getNormalMatrix());

                FormTranslucentQueue.setPassMode(this.program, FormTranslucentQueue.PASS_OPAQUE);
                ModelVAORenderer.render(this.program, entry.getValue(), modelView, normalMat, r, g, b, a, light, overlay);
                FormTranslucentQueue.setPassMode(this.program, FormTranslucentQueue.PASS_SINGLE);

                FormTranslucentQueue.add(new FormTranslucentQueue.ModelVAOCommand(entry.getValue(), texture, modelView, normalMat, r, g, b, a, light, overlay, this.model.isCulling())
                    .overlayColor(overlayColor));
            }
            else if (FormTranslucentQueue.needsWholeDefer(this.program, this.stencilMap, a))
            {
                /* A uniform colour fade defers the whole draw into the sorted end-of-frame pass
                 * with depth kept on, so the faded model still self-occludes. */
                Matrix4f modelView = ModelVAORenderer.captureModelView(stack);
                Matrix3f normalMat = new Matrix3f(stack.peek().getNormalMatrix());
                ShaderProgram program = this.program;

                FormTranslucentQueue.add(new FormTranslucentQueue.ModelVAOCommand(entry.getValue(), () -> program, FormTranslucentQueue.PASS_SINGLE, true, texture, modelView, normalMat, r, g, b, a, light, overlay, this.model.isCulling())
                    .overlayColor(overlayColor));
            }
            else
            {
                ModelVAORenderer.render(this.program, entry.getValue(), stack, r, g, b, a, light, overlay);
            }

            if (cullOverride)
            {
                if (culling == FormMaterial.CULLING_ON) RenderSystem.disableCull();
                else RenderSystem.enableCull();
            }

            if (overlayColor != null)
            {
                FormOverlay.unbind(previousOverlayTexture);
            }
        }

        return false;
    }
}