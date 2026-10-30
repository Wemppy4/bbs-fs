package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.api.client.render.DeformedMesh;
import mchorse.bbs_mod.api.client.render.ModelMeshReplacement;
import mchorse.bbs_mod.BBSModClient;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.vao.IModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.cubic.render.vao.WeldBandVAO;
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

    /* Hybrid mode's two halves run as separate walks: the draw pass every pass (VAOs and welded bands ride
     * the GPU), the bake pass only when the CPU geometry cache misses. The CPU set is decided once per bake
     * and handed in, so both walks agree on which bones are which even when the cache serves an older bake. */
    private Set<ModelGroup> cpuGroups;
    private boolean bakePass;

    /* The posed welded band and the frame it was computed in (the model's root), drawn with each bone. */
    private WeldBandVAO.Entry band;
    private Matrix4f bandModelView;
    private Matrix3f bandNormal;

    /* The frame an addon's replacement meshes are in (the model's root), when this draw has one. */
    private Matrix4f meshModelView;
    private Matrix3f meshNormal;

    public CubicVAORenderer(ShaderProgram program, ModelInstance model, int light, int overlay, StencilMap stencilMap, ShapeKeys shapeKeys, Function<String, Link> textureResolver)
    {
        super(light, overlay, stencilMap, shapeKeys);

        this.program = program;
        this.model = model;
        this.textureResolver = textureResolver;
        this.setMaterialVisibility((material) -> FormMaterialLevels.materialVisible(model.form instanceof ModelForm form ? form : null, material));
    }

    /** Bones that tessellate on the CPU (no VAO): the bake pass emits only these, the draw pass skips their VAOs. */
    public void setCpuGroups(Set<ModelGroup> cpuGroups)
    {
        this.cpuGroups = cpuGroups;
    }

    public void setBakePass(boolean bakePass)
    {
        this.bakePass = bakePass;
    }

    /** Draw each bone's part of the welded band too; its points are in the frame of {@code modelView}, the model's root. */
    public void setPosedBand(WeldBandVAO.Entry band, Matrix4f modelView, Matrix3f normal)
    {
        this.band = band;
        this.bandModelView = modelView;
        this.bandNormal = normal;
    }

    /**
     * Draw this form without the cubes an addon hides, and its meshes with their bones; their points are
     * in the frame of {@code modelView}, the model's root.
     */
    public void setMeshes(ModelMeshReplacement meshes, Matrix4f modelView, Matrix3f normal)
    {
        this.setMeshes(meshes);
        this.meshModelView = modelView;
        this.meshNormal = normal;
    }

    @Override
    public boolean renderGroup(BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model)
    {
        boolean cpu = this.cpuGroups != null && this.cpuGroups.contains(group);

        if (this.bakePass)
        {
            return cpu && super.renderGroup(builder, stack, group, model);
        }

        if (!group.isVisible())
        {
            return false;
        }

        Map<String, ModelVAO> groupVaos = cpu ? null : this.model.getVaos().get(group);

        if (groupVaos != null)
        {
            for (Map.Entry<String, ModelVAO> entry : groupVaos.entrySet())
            {
                IModelVAO vao = this.meshes == null ? entry.getValue() : entry.getValue().without((cube) -> this.meshes.hides(group, cube));

                if (vao != null)
                {
                    this.drawMaterial(stack, group, entry.getKey(), vao, null, null);
                }
            }
        }

        if (this.band != null)
        {
            for (WeldBandVAO.Draw draw : this.band.draws(group))
            {
                if (this.drawMaterial(stack, group, draw.material, draw, this.bandModelView, this.bandNormal))
                {
                    draw.lend();
                }
            }
        }

        if (this.meshes != null && this.meshModelView != null)
        {
            for (DeformedMesh mesh : this.meshes.meshes(group))
            {
                if (this.drawMaterial(stack, group, mesh.material, mesh.prepare(), this.meshModelView, this.meshNormal))
                {
                    mesh.lend();
                }
            }
        }

        return false;
    }

    /**
     * One draw of a bone in one material, with that material's texture bound. {@code modelView} null draws in
     * the bone's frame (its VAO), else in the given one (the band's root frame). Returns whether the draw was
     * deferred to the translucent queue.
     */
    private boolean drawMaterial(MatrixStack stack, ModelGroup group, String material, IModelVAO vao, Matrix4f modelView, Matrix3f normalMat)
    {
        if (!this.materialVisibility.test(material))
        {
            return false;
        }

        float r = this.r * group.color.r;
        float g = this.g * group.color.g;
        float b = this.b * group.color.b;
        float a = this.a * group.color.a;
        int light = this.light;

        if (this.stencilMap != null)
        {
            light = this.stencilMap.increment ? group.index : 0;
        }
        else
        {
            int u = (int) Lerps.lerp(light & '￿', LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, MathUtils.clamp(group.lighting, 0F, 1F));
            int v = light >> 16 & '￿';

            light = u | v << 16;
        }

        /* The material level of the material tab: color/glow/overlay/culling per material of this
         * bone's draws, layered between the form level (already in this.r/g/b/a and this.light) and
         * the bone level (group.color/lighting/overlay above). Picking keeps everything neutral
         * (light carries stencil ids there), and a hurt flash in the overlay UV wins over the color
         * overlay by design. */
        ModelForm modelForm = this.model.form instanceof ModelForm form ? form : null;
        boolean plain = this.stencilMap == null;
        boolean hurtFlash = this.overlay != OverlayTexture.DEFAULT_UV;
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
                    int u = (int) Lerps.lerp(light & '￿', LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, glow);

                    light = u | (light >> 16 & '￿') << 16;
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
        boolean deferred = true;

        if (cullOverride)
        {
            if (culling == FormMaterial.CULLING_ON) RenderSystem.enableCull();
            else RenderSystem.disableCull();
        }

        if (FormTranslucentQueue.needsSplit(this.program, this.stencilMap, texture, a))
        {
            Matrix4f captured = modelView != null ? modelView : ModelVAORenderer.captureModelView(stack);
            Matrix3f capturedNormal = new Matrix3f(normalMat != null ? normalMat : stack.peek().getNormalMatrix());

            FormTranslucentQueue.setPassMode(this.program, FormTranslucentQueue.PASS_OPAQUE);
            ModelVAORenderer.render(this.program, vao, captured, capturedNormal, r, g, b, a, light, overlay);
            FormTranslucentQueue.setPassMode(this.program, FormTranslucentQueue.PASS_SINGLE);

            FormTranslucentQueue.add(new FormTranslucentQueue.ModelVAOCommand(vao, texture, captured, capturedNormal, r, g, b, a, light, overlay, this.model.isCulling())
                .overlayColor(overlayColor));
        }
        else if (FormTranslucentQueue.needsWholeDefer(this.program, this.stencilMap, a))
        {
            /* A uniform colour fade defers the whole draw into the sorted end-of-frame pass
             * with depth kept on, so the faded model still self-occludes. */
            Matrix4f captured = modelView != null ? modelView : ModelVAORenderer.captureModelView(stack);
            Matrix3f capturedNormal = new Matrix3f(normalMat != null ? normalMat : stack.peek().getNormalMatrix());
            ShaderProgram program = this.program;

            FormTranslucentQueue.add(new FormTranslucentQueue.ModelVAOCommand(vao, () -> program, FormTranslucentQueue.PASS_SINGLE, true, texture, captured, capturedNormal, r, g, b, a, light, overlay, this.model.isCulling())
                .overlayColor(overlayColor));
        }
        else
        {
            if (modelView != null)
            {
                ModelVAORenderer.render(this.program, vao, modelView, normalMat, r, g, b, a, light, overlay);
            }
            else
            {
                ModelVAORenderer.render(this.program, vao, stack, r, g, b, a, light, overlay);
            }

            deferred = false;
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

        return deferred;
    }
}
