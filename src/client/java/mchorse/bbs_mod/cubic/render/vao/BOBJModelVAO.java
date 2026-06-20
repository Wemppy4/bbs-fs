package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.render.picker.BBSPickerRenderer;
import mchorse.bbs_mod.graphics.ModelPreviewRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.joml.Matrices;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class BOBJModelVAO
{
    public BOBJLoader.CompiledData data;
    public BOBJArmature armature;

    private int count;

    /* CPU-skinned mesh, recomputed every frame in updateMesh and emitted in render */
    private float[] tmpVertices;
    private float[] tmpNormals;
    private int[] tmpLight;

    public BOBJModelVAO(BOBJLoader.CompiledData data)
    {
        this.data = data;
        this.armature = this.data.mesh.armature;

        this.count = this.data.normData.length / 3;
        this.tmpVertices = new float[this.data.posData.length];
        this.tmpNormals = new float[this.data.normData.length];
        this.tmpLight = new int[this.count * 2];
    }

    /**
     * Previously this freed the raw-GL VAO/VBOs. The skinned mesh now draws through the immediate
     * BufferBuilder path (see {@link #render}), so there is nothing GPU-side to free here anymore.
     */
    public void delete()
    {}

    /**
     * Update this mesh. This method is responsible for applying matrix transformations to vertices
     * and normals according to its bone owners and these bone influences. The skinned result is kept
     * on the CPU (tmpVertices/tmpNormals/tmpLight) and emitted into a BufferBuilder in {@link #render}.
     */
    public void updateMesh(StencilMap stencilMap)
    {
        Vector4f sum = new Vector4f();
        Vector4f result = new Vector4f(0F, 0F, 0F, 0F);
        Vector3f sumNormal = new Vector3f();
        Vector3f resultNormal = new Vector3f();

        float[] oldVertices = this.data.posData;
        float[] newVertices = this.tmpVertices;
        float[] oldNormals = this.data.normData;
        float[] newNormals = this.tmpNormals;

        Matrix4f[] matrices = this.armature.matrices;

        for (int i = 0, c = this.count; i < c; i++)
        {
            int count = 0;
            float maxWeight = -1;
            int lightBone = -1;

            for (int w = 0; w < 4; w++)
            {
                float weight = this.data.weightData[i * 4 + w];

                if (weight > 0)
                {
                    int index = this.data.boneIndexData[i * 4 + w];

                    sum.set(oldVertices[i * 3], oldVertices[i * 3 + 1], oldVertices[i * 3 + 2], 1F);
                    matrices[index].transform(sum);
                    result.add(sum.mul(weight));

                    sumNormal.set(oldNormals[i * 3], oldNormals[i * 3 + 1], oldNormals[i * 3 + 2]);
                    Matrices.TEMP_3F.set(matrices[index]).transform(sumNormal);
                    resultNormal.add(sumNormal.mul(weight));

                    count++;

                    if (weight > maxWeight)
                    {
                        lightBone = index;
                        maxWeight = weight;
                    }
                }
            }

            if (count == 0)
            {
                result.set(oldVertices[i * 3], oldVertices[i * 3 + 1], oldVertices[i * 3 + 2], 1F);
                resultNormal.set(oldNormals[i * 3], oldNormals[i * 3 + 1], oldNormals[i * 3 + 2]);
            }

            result.x /= result.w;
            result.y /= result.w;
            result.z /= result.w;

            newVertices[i * 3] = result.x;
            newVertices[i * 3 + 1] = result.y;
            newVertices[i * 3 + 2] = result.z;

            newNormals[i * 3] = resultNormal.x;
            newNormals[i * 3 + 1] = resultNormal.y;
            newNormals[i * 3 + 2] = resultNormal.z;

            result.set(0F, 0F, 0F, 0F);
            resultNormal.set(0F, 0F, 0F);

            if (stencilMap != null)
            {
                this.tmpLight[i * 2] = Math.max(0, stencilMap.increment ? lightBone : 0);
                this.tmpLight[i * 2 + 1] = 0;
            }
        }

        this.processData(newVertices, newNormals);
    }

    protected void processData(float[] newVertices, float[] newNormals)
    {}

    /**
     * Emit the CPU-skinned mesh through the immediate model RenderLayer. The stack position/normal
     * matrices are baked into the vertices CPU-side (exactly like {@link mchorse.bbs_mod.cubic.render.CubicCubeRenderer#writeVertex}),
     * and the model pipeline (blend/depth/cull) + lightmap/overlay samplers are encoded by
     * {@link BBSShaders#getModelLayer()}. Replaces the removed ShaderProgram bind + raw-GL VAO draw.
     */
    public void render(MatrixStack stack, float r, float g, float b, float a, StencilMap stencilMap, int light, int overlay)
    {
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

        Matrix4f position = stack.peek().getPositionMatrix();
        Matrix3f normalMatrix = stack.peek().getNormalMatrix();

        float[] vertices = this.tmpVertices;
        float[] normals = this.tmpNormals;
        float[] texData = this.data.texData;

        Vector4f vertex = new Vector4f();
        Vector3f normal = new Vector3f();

        int lu = light & 0xffff;
        int lv = light >> 16 & 0xffff;

        for (int i = 0; i < this.count; i++)
        {
            vertex.set(vertices[i * 3], vertices[i * 3 + 1], vertices[i * 3 + 2], 1F);
            position.transform(vertex);

            normal.set(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2]);
            normalMatrix.transform(normal);

            int u = lu;
            int v = lv;

            if (stencilMap != null)
            {
                u = this.tmpLight[i * 2];
                v = this.tmpLight[i * 2 + 1];
            }

            builder.vertex(vertex.x, vertex.y, vertex.z)
                .color(r, g, b, a)
                .texture(texData[i * 2], texData[i * 2 + 1])
                .overlay(overlay)
                .light(u, v)
                .normal(normal.x, normal.y, normal.z);
        }

        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            if (stencilMap != null)
            {
                /* Picking: each bone's index is packed into the per-vertex LIGHT.x above (updateMesh).
                 * Route through the picker_models pipeline (Target + UV2.x sub-index -> index colour) into the
                 * StencilFormFramebuffer target, same as the cubic immediate path. Target/Sampler0 are set by
                 * ModelFormRenderer before the render; model-view is identity (camera baked into the vertices). */
                BBSPickerRenderer.draw(BBSShaders.getPickerModelsProgram(), built, RenderSystem.getModelViewMatrix());
            }
            else if (ModelPreviewRenderer.ACTIVE && ModelPreviewRenderer.TEXTURE != null)
            {
                /* In-panel form/replay list preview: bind the adopted model texture and draw through a vanilla
                 * entity cutout layer, mirroring the cubic immediate path in ModelInstance.render. This restores
                 * the 1.21.1 per-mesh textureResolver bind (BBSModClient.getTextures().bindTexture before each
                 * vao.render) which the immediate-VAO port dropped, so the texture shows on the idle/non-selected
                 * preview path too (not only on hover/selected). */
                RenderLayers.entityCutoutNoCull(ModelPreviewRenderer.TEXTURE).draw(built);
            }
            else
            {
                BBSShaders.getModelLayer().draw(built);
            }
        }
    }
}
