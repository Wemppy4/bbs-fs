package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.cubic.weld.WeldBinding;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class CubicVAORenderer extends CubicCubeRenderer
{
    private ModelInstance model;
    private Function<String, Link> textureResolver;

    // TODO(1.21.11 render merge): per-material ModelVAO texture resolver — re-port against pipeline API
    // (was: 1.21.1 ctor took a ShaderProgram program + Function<String,Link> textureResolver and stored the
    //  resolver to bind each material's texture before its draw; HEAD draws every material through the pipeline
    //  RenderLayer with no per-material shader/texture bind).
    public CubicVAORenderer(ModelInstance model, int light, int overlay, StencilMap stencilMap, ShapeKeys shapeKeys)
    {
        super(light, overlay, stencilMap, shapeKeys);

        this.model = model;
    }

    /**
     * Non-null puts the renderer in hybrid mode (a welded model): these groups — and any group with no baked
     * VAO — fall through to the CPU immediate path so their welded cubes can deform against a live neighbour,
     * while every other group still rides its VAO on the GPU. Null keeps the plain all-VAO behaviour.
     */
    private Set<ModelGroup> weldedGroups;

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
            int u = (int) Lerps.lerp(light & '\uffff', LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, MathUtils.clamp(group.lighting, 0F, 1F));
            int v = light >> 16 & '\uffff';

            light = u | v << 16;
        }

        /* One draw per material (multi-material ModelVAO map from the 1.21.1 merge). */
        // TODO(1.21.11 render merge): per-material ModelVAO texture resolver — re-port against pipeline API
        // (was: 1.21.1 resolved textureResolver.apply(entry.getKey()) per material and bound it via
        //  BBSModClient.getTextures().bindTexture(link) before drawing, using the ModelVAORenderer.render(program, ...)
        //  overload; HEAD draws every material through the pipeline RenderLayer with no per-material texture bind).
        for (Map.Entry<String, ModelVAO> entry : groupVaos.entrySet())
        {
            ModelVAORenderer.render(entry.getValue(), stack, r, g, b, a, light, this.overlay, this.model.isCulling());
        }

        return false;
    }
}