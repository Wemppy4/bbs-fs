package mchorse.bbs_mod.cubic.render.vao;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import mchorse.bbs_mod.graphics.shader.ShaderProgram;
import mchorse.bbs_mod.graphics.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;

public class BOBJModelVAO
{
    public BOBJLoader.CompiledData data;
    public BOBJArmature armature;

    private int vao;
    private int count;
    private List<int[]> visibleRanges;

    /* GL buffers */
    public int vertexBuffer;
    public int normalBuffer;
    public int lightBuffer;
    public int texCoordBuffer;
    private int tangentBuffer,midUvBuffer;

    private float[] tmpVertices;
    private float[] tmpNormals;
    private float[] tmpTangents;
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

        this.initBuffers();
    }

    /**
     * Initiate buffers. This method is responsible for allocating 
     * buffers for the data to be passed to VBOs and also generating the 
     * VBOs themselves. 
     */
    private void initBuffers()
    {
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try
        {
            this.vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(this.vao);
            this.vertexBuffer = GL15.glGenBuffers();
            this.normalBuffer = GL15.glGenBuffers();
            this.lightBuffer = GL15.glGenBuffers();
            this.texCoordBuffer = GL15.glGenBuffers();
            this.tangentBuffer = GL15.glGenBuffers();
            this.midUvBuffer = GL15.glGenBuffers();
            this.count = this.data.normData.length / 3;
            this.tmpVertices = new float[this.data.posData.length];
            this.tmpNormals = new float[this.data.normData.length];
            this.tmpTangents = mchorse.bbs_mod.graphics.ModelTangents.calculate(this.data.posData,this.data.normData,this.data.texData);
            this.tmpLight = new int[this.count * 2];

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vertexBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, ModelBuffers.wrap(this.data.posData), GL15.GL_DYNAMIC_DRAW);
            GL20.glVertexAttribPointer(Attributes.POSITION, 3, GL11.GL_FLOAT, false, 0, 0L);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.normalBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, ModelBuffers.wrap(this.data.normData), GL15.GL_DYNAMIC_DRAW);
            GL20.glVertexAttribPointer(Attributes.NORMAL, 3, GL11.GL_FLOAT, false, 0, 0L);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.lightBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, ModelBuffers.wrap(this.tmpLight), GL15.GL_DYNAMIC_DRAW);
            GL30.glVertexAttribIPointer(Attributes.LIGHTMAP_UV, 2, GL11.GL_INT, 0, 0L);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.texCoordBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, ModelBuffers.wrap(this.data.texData), GL15.GL_STATIC_DRAW);
            GL20.glVertexAttribPointer(Attributes.TEXTURE_UV, 2, GL11.GL_FLOAT, false, 0, 0L);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,this.tangentBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,ModelBuffers.wrap(this.tmpTangents),GL15.GL_DYNAMIC_DRAW);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,this.midUvBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,ModelBuffers.wrap(mchorse.bbs_mod.graphics.ModelTangents.midUvs(this.data.texData)),GL15.GL_STATIC_DRAW);
        }
        catch (RuntimeException error) { this.delete(); throw error; }
        finally
        {
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
        }
    }

    /**
     * Clean up resources which were used by this  
     */
    public void delete()
    {
        GL30.glDeleteVertexArrays(this.vao);

        GL15.glDeleteBuffers(this.vertexBuffer);
        GL15.glDeleteBuffers(this.normalBuffer);
        GL15.glDeleteBuffers(this.lightBuffer);
        GL15.glDeleteBuffers(this.texCoordBuffer);
        GL15.glDeleteBuffers(this.tangentBuffer);
        GL15.glDeleteBuffers(this.midUvBuffer);
        this.vao = this.vertexBuffer = this.normalBuffer = this.lightBuffer = this.texCoordBuffer = this.tangentBuffer = this.midUvBuffer = 0;
    }

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
     * Update this mesh. This method is responsible for applying
     * matrix transformations to vertices and normals according to its
     * bone owners and these bone influences.
     */
    public void updateMesh(StencilMap stencilMap)
    {
        this.updateMesh(stencilMap, armatureKey(this.armature));
    }

    /** Skin and upload unless the VBO already holds exactly this pose in this mode. */
    public void updateMesh(StencilMap stencilMap, long key)
    {
        int mode = stencilMap == null ? 0 : (stencilMap.increment ? 2 : 1);

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

        this.uploadCount += 1;

        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try
        {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vertexBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, ModelBuffers.wrap(newVertices), GL15.GL_DYNAMIC_DRAW);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.normalBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, ModelBuffers.wrap(newNormals), GL15.GL_DYNAMIC_DRAW);
            mchorse.bbs_mod.graphics.ModelTangents.calculate(this.tmpTangents,newVertices,newNormals,this.data.texData);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,this.tangentBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,ModelBuffers.wrap(this.tmpTangents),GL15.GL_DYNAMIC_DRAW);
            if (stencilMap != null)
            {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.lightBuffer);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, ModelBuffers.wrap(this.tmpLight), GL15.GL_DYNAMIC_DRAW);
            }
        }
        finally { GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer); }
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

    public void render(ShaderProgram shader, MatrixStack stack, float r, float g, float b, float a, StencilMap stencilMap, int light, int overlay)
    {
        this.render(shader, ModelVAORenderer.captureModelView(stack), stack.peek().getNormalMatrix(), r, g, b, a, stencilMap, light, overlay);
    }

    public void render(ShaderProgram shader, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, StencilMap stencilMap, int light, int overlay)
    {
        if (stencilMap == null && shader.isWorldModel())
        {
            try (mchorse.bbs_mod.graphics.OptiFineModelRenderer.Scope pass = mchorse.bbs_mod.graphics.OptiFineModelRenderer.begin(
                modelView,mchorse.bbs_mod.graphics.render.RenderSystem.getProjectionMatrix(),r,g,b,a,light,overlay))
            {
                mchorse.bbs_mod.graphics.OptiFineModelRenderer.arrays(this.vertexBuffer,this.normalBuffer,this.texCoordBuffer,this.tangentBuffer,this.midUvBuffer);
                if (this.visibleRanges == null) GL11.glDrawArrays(GL11.GL_TRIANGLES,0,this.count);
                else for (int[] range : this.visibleRanges) GL11.glDrawArrays(GL11.GL_TRIANGLES,range[0],range[1]);
            }
            return;
        }
        GL20.glVertexAttrib4f(Attributes.COLOR, r, g, b, a);
        GL30.glVertexAttribI2i(Attributes.OVERLAY_UV, overlay & '\uffff', overlay >> 16 & '\uffff');
        GL30.glVertexAttribI2i(Attributes.LIGHTMAP_UV, light & '\uffff', light >> 16 & '\uffff');

        int currentVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int currentElementArrayBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);

        ModelVAORenderer.setupUniforms(shader, modelView, normalMat);

        shader.bind();
        try
        {
        GL30.glBindVertexArray(this.vao);

        GL20.glEnableVertexAttribArray(Attributes.POSITION);
        GL20.glEnableVertexAttribArray(Attributes.TEXTURE_UV);
        GL20.glEnableVertexAttribArray(Attributes.NORMAL);

        if (stencilMap != null) GL20.glEnableVertexAttribArray(Attributes.LIGHTMAP_UV);

        if (this.visibleRanges == null)
        {
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, this.count);
        }
        else
        {
            for (int[] range : this.visibleRanges)
            {
                GL11.glDrawArrays(GL11.GL_TRIANGLES, range[0], range[1]);
            }
        }

        GL20.glDisableVertexAttribArray(Attributes.POSITION);
        GL20.glDisableVertexAttribArray(Attributes.TEXTURE_UV);
        GL20.glDisableVertexAttribArray(Attributes.NORMAL);

        if (stencilMap != null) GL20.glDisableVertexAttribArray(Attributes.LIGHTMAP_UV);
        }
        finally
        {
            GL30.glBindVertexArray(this.vao);
            GL20.glDisableVertexAttribArray(Attributes.LIGHTMAP_UV);
            shader.unbind();
            GL30.glBindVertexArray(currentVAO);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, currentElementArrayBuffer);
        }
    }
}
