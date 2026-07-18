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
import net.minecraft.client.gl.ShaderProgram;
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

        /* One draw per material; bind that material's resolved texture before each. */
        for (Map.Entry<String, ModelVAO> entry : groupVaos.entrySet())
        {
            Texture texture = null;

            if (this.textureResolver != null)
            {
                Link link = this.textureResolver.apply(entry.getKey());

                if (link != null)
                {
                    texture = BBSModClient.getTextures().getTexture(link);
                    BBSModClient.getTextures().bindTexture(texture);
                }
            }

            if (texture == null)
            {
                /* No per-material override — the draw uses the form's base texture bound earlier. */
                texture = BBSModClient.getTextures().getLastBound();
            }

            if (FormTranslucentQueue.needsSplit(this.program, this.stencilMap, texture, a))
            {
                Matrix4f modelView = ModelVAORenderer.captureModelView(stack);
                Matrix3f normalMat = new Matrix3f(stack.peek().getNormalMatrix());

                FormTranslucentQueue.setPassMode(this.program, FormTranslucentQueue.PASS_OPAQUE);
                ModelVAORenderer.render(this.program, entry.getValue(), modelView, normalMat, r, g, b, a, light, this.overlay);
                FormTranslucentQueue.setPassMode(this.program, FormTranslucentQueue.PASS_SINGLE);

                FormTranslucentQueue.add(new FormTranslucentQueue.ModelVAOCommand(entry.getValue(), texture, modelView, normalMat, r, g, b, a, light, this.overlay, this.model.isCulling()));
            }
            else if (FormTranslucentQueue.needsWholeDefer(this.program, this.stencilMap, texture, a))
            {
                /* Iris: no PassMode uniform to split with, so the whole draw defers into the
                 * sorted end-of-frame pass instead of drawing now. */
                Matrix4f modelView = ModelVAORenderer.captureModelView(stack);
                Matrix3f normalMat = new Matrix3f(stack.peek().getNormalMatrix());
                ShaderProgram program = this.program;

                FormTranslucentQueue.add(new FormTranslucentQueue.ModelVAOCommand(entry.getValue(), () -> program, FormTranslucentQueue.PASS_SINGLE, true, texture, modelView, normalMat, r, g, b, a, light, this.overlay, this.model.isCulling()));
            }
            else
            {
                ModelVAORenderer.render(this.program, entry.getValue(), stack, r, g, b, a, light, this.overlay);
            }
        }

        return false;
    }
}