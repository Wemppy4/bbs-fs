package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.render.picker.BBSPickerRenderer;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.renderers.utils.FormOverlay;
import mchorse.bbs_mod.graphics.ModelPreviewRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public class BOBJModelVAO
{
    public BOBJLoader.CompiledData data;
    public BOBJArmature armature;

    private int count;
    private List<int[]> visibleRanges;

    /* CPU-skinned mesh, recomputed every frame in updateMesh and emitted in render */
    private float[] tmpVertices;
    private float[] tmpNormals;
    private int[] tmpLight;

    /**
     * Bumped on every VBO upload. The VBO is shared between actors using the same model, so a
     * deferred translucent command compares this against the value it captured to know whether
     * someone re-skinned the mesh since — and re-uploads from its armature snapshot if so.
     */
    private int uploadCount;

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

    public int getUploadCount()
    {
        return this.uploadCount;
    }

    /** A deep copy of the armature's current skinning matrices, for deferred re-uploads. */
    public Matrix4f[] snapshotArmature()
    {
        Matrix4f[] matrices = this.armature.matrices;
        Matrix4f[] snapshot = new Matrix4f[matrices.length];

        for (int i = 0; i < matrices.length; i++)
        {
            snapshot[i] = matrices[i] == null ? null : new Matrix4f(matrices[i]);
        }

        return snapshot;
    }

    /** Null is the common case where every bone is visible. */
    public boolean[] snapshotVisibility()
    {
        boolean[] visible = null;

        for (int i = 0; i < this.armature.orderedBones.size(); i++)
        {
            if (!this.armature.orderedBones.get(i).visible)
            {
                if (visible == null)
                {
                    visible = new boolean[this.armature.orderedBones.size()];
                    java.util.Arrays.fill(visible, true);
                }

                visible[i] = false;
            }
        }

        return visible;
    }

    /* What the VBO currently holds: the armature pose it was skinned from plus the mode bits
     * that shape the upload (picking bakes bone ids into the light attribute, Iris adds
     * tangents). The VBO is shared by every actor on this model, so two actors alternating
     * still re-skin — but one actor across the passes of a frame, and across frames in which
     * it did not move, skins once. */
    private static final long NO_KEY = Long.MIN_VALUE;

    private long uploadedKey = NO_KEY;
    private int uploadedMode = -1;

    /** A content key of the armature's skinning matrices — computed once per render, shared by every mesh. */
    public static long armatureKey(BOBJArmature armature)
    {
        long key = 1469598103934665603L;

        for (Matrix4f matrix : armature.matrices)
        {
            key = key * 31 + (matrix == null ? 0 : matrix.hashCode());
        }

        for (var bone : armature.orderedBones)
        {
            key = key * 31 + (bone.visible ? 1 : 0);
        }

        return key;
    }

    /**
     * Update this mesh. This method is responsible for applying matrix transformations to vertices
     * and normals according to its bone owners and these bone influences. The skinned result is kept
     * on the CPU (tmpVertices/tmpNormals/tmpLight) and emitted into a BufferBuilder in {@link #render}.
     */
    public void updateMesh(StencilMap stencilMap)
    {
        this.updateMesh(stencilMap, armatureKey(this.armature));
    }

    /** Skin and upload unless the VBO already holds exactly this pose in this mode. */
    public void updateMesh(StencilMap stencilMap, long key)
    {
        int mode = (stencilMap == null ? 0 : (stencilMap.increment ? 2 : 1)) | (BBSRendering.isIrisShadersEnabled() ? 4 : 0);

        if (key != NO_KEY && key == this.uploadedKey && mode == this.uploadedMode)
        {
            BBSProfiler.count(BBSProfiler.Section.BOBJ_SKINS_SKIPPED);

            return;
        }

        this.updateMesh(stencilMap, this.armature.matrices);

        this.uploadedKey = key;
        this.uploadedMode = mode;
    }

    /** Skin from an explicit matrix set (a deferred command's snapshot); the VBO's pose is then unknown. */
    public void updateMesh(StencilMap stencilMap, Matrix4f[] matrices)
    {
        this.updateMesh(stencilMap, matrices, this.snapshotVisibility());
    }

    public void updateMesh(StencilMap stencilMap, Matrix4f[] matrices, boolean[] visible)
    {
        this.uploadedKey = NO_KEY;
        this.updateVisibleRanges(visible);

        BBSProfiler.count(BBSProfiler.Section.BOBJ_SKINS);

        Vector4f sum = new Vector4f();
        Vector4f result = new Vector4f(0F, 0F, 0F, 0F);
        Vector3f sumNormal = new Vector3f();
        Vector3f resultNormal = new Vector3f();

        float[] oldVertices = this.data.posData;
        float[] newVertices = this.tmpVertices;
        float[] oldNormals = this.data.normData;
        float[] newNormals = this.tmpNormals;

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

        this.processData(newVertices, newNormals, matrices);
    }

    private void updateVisibleRanges(boolean[] visible)
    {
        this.visibleRanges = null;

        if (visible == null)
        {
            return;
        }

        this.visibleRanges = new ArrayList<>();
        int start = 0;

        for (int i = 0; i < this.count; i += 3)
        {
            boolean shown = true;

            /* Omit the whole triangle if a hidden bone influences any of its vertices. */
            for (int w = i * 4; w < (i + 3) * 4 && shown; w++)
            {
                if (this.data.weightData[w] > 0 && !visible[this.data.boneIndexData[w]])
                {
                    shown = false;
                }
            }

            if (!shown)
            {
                if (start < i)
                {
                    this.visibleRanges.add(new int[] {start, i - start});
                }

                start = i + 3;
            }
        }

        if (start < this.count)
        {
            this.visibleRanges.add(new int[] {start, this.count - start});
        }
    }

    protected void processData(float[] newVertices, float[] newNormals, Matrix4f[] matrices)
    {}

    /**
     * Emit the CPU-skinned mesh through the immediate model RenderLayer. The stack position/normal
     * matrices are baked into the vertices CPU-side (exactly like {@link mchorse.bbs_mod.cubic.render.CubicCubeRenderer#writeVertex}),
     * and the model pipeline (blend/depth/cull) + lightmap/overlay samplers are encoded by
     * {@link BBSShaders#getModelLayer()}. Replaces the removed ShaderProgram bind + raw-GL VAO draw.
     *
     * <p>{@code cull} carries the model's own culling flag ({@code ModelInstance.isCulling()}); on
     * 1.21.1 it toggled the global GL cull around the draw, now it picks the layer variant.
     */
    public void render(MatrixStack stack, float r, float g, float b, float a, StencilMap stencilMap, int light, int overlay, boolean cull)
    {
        this.render(stack, r, g, b, a, stencilMap, light, overlay, cull, null);
    }

    /** The same draw with a colour overlay on it (null = none); see {@code FormOverlay}. */
    public void render(MatrixStack stack, float r, float g, float b, float a, StencilMap stencilMap, int light, int overlay, boolean cull, Color tint)
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

        /* A hidden bone drops the triangles it moves: the spans updateVisibleRanges left are the ones
         * to emit, which is the same skip the raw-GL path made by drawing arrays span by span. */
        List<int[]> spans = this.visibleRanges == null ? List.of(new int[] {0, this.count}) : this.visibleRanges;

        for (int[] span : spans)
        {
            for (int i = span[0], end = span[0] + span[1]; i < end; i++)
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
                /* In-panel form/replay list preview, keyed on the adopted model texture (the branch is also what
                 * restores the per-mesh texture for the idle preview path — the immediate-VAO port dropped the
                 * textureResolver bind). Draws through the BBS model layer, not vanilla entityCutoutNoCull: CUTOUT
                 * has no blending, so the form's colour alpha read as "lighter" instead of transparent and cliffed
                 * into invisibility at the 0.1 discard — see the matching branch in ModelInstance.render. */
                FormOverlay.withOverlay(BBSShaders.getBoundModelLayer(BBSShaders.ModelVariant.SINGLE.withCull(cull)), tint != null).draw(built);
            }
            else
            {
                /* Solid geometry: depth write stays on, so a deferred translucent pass still
                 * self-occludes (see FormTranslucentQueue). */
                FormTranslucentQueue.submit(built,
                    new BBSShaders.ModelVariant(FormTranslucentQueue.PASS_SINGLE, true, cull),
                    BBSModClient.getTextures().getLastBound(), a, stencilMap,
                    ModelVAORenderer.captureModelView(stack).getTranslation(new Vector3f()), tint != null);
            }
        }
    }
}
