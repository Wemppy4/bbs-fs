package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelData;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.cubic.data.model.ModelQuad;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import mchorse.bbs_mod.cubic.weld.WeldBinding;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Map;

public class CubicCubeRenderer implements ICubicRenderer
{
    private final static Vector3f v1 = new Vector3f();
    private final static Vector3f v2 = new Vector3f();
    private final static Vector3f v3 = new Vector3f();

    private final static Vector3f n1 = new Vector3f();
    private final static Vector3f n2 = new Vector3f();
    private final static Vector3f n3 = new Vector3f();

    private final static Vector2f u1 = new Vector2f();
    private final static Vector2f u2 = new Vector2f();
    private final static Vector2f u3 = new Vector2f();

    private static Matrix4f modelM = new Matrix4f();
    private static Matrix3f normalM = new Matrix3f();

    protected float r = 1F;
    protected float g = 1F;
    protected float b = 1F;
    protected float a = 1F;
    protected int light;
    protected int overlay;
    protected StencilMap stencilMap;

    /* Temporary variables to avoid allocating and GC vectors */
    protected Vector3f normal = new Vector3f();
    protected Vector4f vertex = new Vector4f();

    private ModelVertex modelVertex = new ModelVertex();
    private ShapeKeys shapeKeys;

    /* Welds active for the model being rendered (null when it has none). Resolved once on the instance. */
    private List<WeldBinding> welds;

    /* Weld layers the cube currently being rendered is the target/source of — set per cube, consulted per vertex. */
    private WeldBinding.Layer targetLayer;
    private WeldBinding.Layer sourceLayer;

    /* Capture pass records the rigid world corners of welded faces without drawing; the draw pass snaps to the seam. */
    private boolean captureOnly;

    /* A welded cube's faces shear into trapezoids; drawn as two flat triangles their texture warps unevenly, so
     * they are tessellated into this many sub-quads per side and the deformation is interpolated across them. */
    private static final int WELD_SUBDIVISIONS = 4;

    private final Vector3f[] cornerPos = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    private final float[] cornerU = new float[4];
    private final float[] cornerV = new float[4];

    public static void moveToPivot(MatrixStack stack, Vector3f pivot)
    {
        stack.translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F);
    }

    public static void rotate(MatrixStack stack, Vector3f rotation)
    {
        if (rotation.x == 0 && rotation.y == 0 && rotation.z == 0)
        {
            return;
        }

        Matrix4f matrix4f = new Matrix4f();
        Matrix3f matrix3f = new Matrix3f();

        modelM.identity();
        matrix4f.identity().rotateZ(MathUtils.toRad(rotation.z));
        modelM.mul(matrix4f);

        matrix4f.identity().rotateY(MathUtils.toRad(rotation.y));
        modelM.mul(matrix4f);

        matrix4f.identity().rotateX(MathUtils.toRad(rotation.x));
        modelM.mul(matrix4f);

        normalM.identity();
        matrix3f.identity().rotateZ(MathUtils.toRad(rotation.z));
        normalM.mul(matrix3f);

        matrix3f.identity().rotateY(MathUtils.toRad(rotation.y));
        normalM.mul(matrix3f);

        matrix3f.identity().rotateX(MathUtils.toRad(rotation.x));
        normalM.mul(matrix3f);

        stack.peek().getPositionMatrix().mul(modelM);
        stack.peek().getNormalMatrix().mul(normalM);
    }

    public static void moveBackFromPivot(MatrixStack stack, Vector3f pivot)
    {
        stack.translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);
    }

    public CubicCubeRenderer(int light, int overlay, StencilMap stencilMap, ShapeKeys shapeKeys)
    {
        this.light = light;
        this.overlay = overlay;
        this.stencilMap = stencilMap;
        this.shapeKeys = shapeKeys;
    }

    public void setColor(float r, float g, float b, float a)
    {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    public void setWelds(List<WeldBinding> welds)
    {
        this.welds = welds;
    }

    public void setCaptureOnly(boolean captureOnly)
    {
        this.captureOnly = captureOnly;
    }

    @Override
    public boolean renderGroup(BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model)
    {
        for (ModelCube cube : group.cubes)
        {
            this.renderCube(builder, stack, group, cube);
        }

        for (ModelMesh mesh : group.meshes)
        {
            this.renderMesh(builder, stack, model, group, mesh);
        }

        return false;
    }

    protected void renderCube(BufferBuilder builder, MatrixStack stack, ModelGroup group, ModelCube cube)
    {
        stack.push();
        moveToPivot(stack, cube.pivot);
        rotate(stack, cube.rotate);
        moveBackFromPivot(stack, cube.pivot);

        this.pickWelds(cube);

        boolean subdivide = !this.captureOnly && (this.targetLayer != null || this.sourceLayer != null);
        Matrix4f matrix = stack.peek().getPositionMatrix();

        for (ModelQuad quad : cube.quads)
        {
            this.normal.set(quad.normal.x, quad.normal.y, quad.normal.z);
            stack.peek().getNormalMatrix().transform(this.normal);

            if (quad.vertices.size() != 4)
            {
                continue;
            }

            if (subdivide)
            {
                this.renderQuadSubdivided(builder, matrix, group, quad, this.normal);
            }
            else
            {
                this.writeVertex(builder, stack, group, quad.vertices.get(0), this.normal);
                this.writeVertex(builder, stack, group, quad.vertices.get(1), this.normal);
                this.writeVertex(builder, stack, group, quad.vertices.get(2), this.normal);
                this.writeVertex(builder, stack, group, quad.vertices.get(0), this.normal);
                this.writeVertex(builder, stack, group, quad.vertices.get(2), this.normal);
                this.writeVertex(builder, stack, group, quad.vertices.get(3), this.normal);
            }
        }

        stack.pop();
    }

    protected void renderMesh(BufferBuilder builder, MatrixStack stack, Model model, ModelGroup group, ModelMesh mesh)
    {
        this.targetLayer = null;
        this.sourceLayer = null;

        stack.push();
        moveToPivot(stack, mesh.origin);
        rotate(stack, mesh.rotate);
        moveBackFromPivot(stack, mesh.origin);

        ModelData baseData = mesh.baseData;

        for (int i = 0, c = baseData.vertices.size() / 3; i < c; i++)
        {
            v1.set(baseData.vertices.get(i * 3));
            v2.set(baseData.vertices.get(i * 3 + 1));
            v3.set(baseData.vertices.get(i * 3 + 2));

            n1.set(baseData.normals.get(i * 3));
            n2.set(baseData.normals.get(i * 3 + 1));
            n3.set(baseData.normals.get(i * 3 + 2));

            u1.set(baseData.uvs.get(i * 3));
            u2.set(baseData.uvs.get(i * 3 + 1));
            u3.set(baseData.uvs.get(i * 3 + 2));

            /* Apply shape keys */
            for (Map.Entry<String, Float> entry : this.shapeKeys.shapeKeys.entrySet())
            {
                ModelData data = mesh.data.get(entry.getKey());
                float value = entry.getValue();

                if (data != null)
                {
                    /* final = temporary + lerp(initial, current, x) - initial */
                    this.relativeShift(v1, baseData.vertices.get(i * 3), data.vertices.get(i * 3), value);
                    this.relativeShift(v2, baseData.vertices.get(i * 3 + 1), data.vertices.get(i * 3 + 1), value);
                    this.relativeShift(v3, baseData.vertices.get(i * 3 + 2), data.vertices.get(i * 3 + 2), value);

                    this.relativeShift(n1, baseData.normals.get(i * 3), data.normals.get(i * 3), value);
                    this.relativeShift(n2, baseData.normals.get(i * 3 + 1), data.normals.get(i * 3 + 1), value);
                    this.relativeShift(n3, baseData.normals.get(i * 3 + 2), data.normals.get(i * 3 + 2), value);

                    this.relativeShift(u1, baseData.uvs.get(i * 3), data.uvs.get(i * 3), value);
                    this.relativeShift(u2, baseData.uvs.get(i * 3 + 1), data.uvs.get(i * 3 + 1), value);
                    this.relativeShift(u3, baseData.uvs.get(i * 3 + 2), data.uvs.get(i * 3 + 2), value);
                }
            }

            /* Write vertices */
            this.normal.set(n1.x, n1.y, n1.z);
            stack.peek().getNormalMatrix().transform(this.normal);
            this.modelVertex.set(v1, u1, model);
            this.writeVertex(builder, stack, group, this.modelVertex, this.normal);

            this.normal.set(n2.x, n2.y, n2.z);
            stack.peek().getNormalMatrix().transform(this.normal);
            this.modelVertex.set(v2, u2, model);
            this.writeVertex(builder, stack, group, this.modelVertex, this.normal);

            this.normal.set(n3.x, n3.y, n3.z);
            stack.peek().getNormalMatrix().transform(this.normal);
            this.modelVertex.set(v3, u3, model);
            this.writeVertex(builder, stack, group, this.modelVertex, this.normal);
        }

        stack.pop();
    }

    private void relativeShift(Vector3f temp, Vector3f initial, Vector3f current, float x)
    {
        temp.x = temp.x + Lerps.lerp(initial.x, current.x, x) - initial.x;
        temp.y = temp.y + Lerps.lerp(initial.y, current.y, x) - initial.y;
        temp.z = temp.z + Lerps.lerp(initial.z, current.z, x) - initial.z;
    }

    private void relativeShift(Vector2f temp, Vector2f initial, Vector2f current, float x)
    {
        temp.x = temp.x + Lerps.lerp(initial.x, current.x, x) - initial.x;
        temp.y = temp.y + Lerps.lerp(initial.y, current.y, x) - initial.y;
    }

    /** Find the weld layers the cube being rendered is the target/source of, so capture/snap can run per vertex. */
    private void pickWelds(ModelCube cube)
    {
        this.targetLayer = null;
        this.sourceLayer = null;

        if (this.welds == null)
        {
            return;
        }

        for (WeldBinding weld : this.welds)
        {
            for (WeldBinding.Layer layer : weld.layers)
            {
                if (layer.targetCube == cube) this.targetLayer = layer;
                if (layer.sourceCube == cube) this.sourceLayer = layer;
            }
        }
    }

    protected void writeVertex(BufferBuilder builder, MatrixStack stack, ModelGroup group, ModelVertex vertex, Vector3f normal)
    {
        this.vertex.set(vertex.vertex.x, vertex.vertex.y, vertex.vertex.z, 1);
        stack.peek().getPositionMatrix().transform(this.vertex);

        if (this.captureOnly)
        {
            this.captureWeldCorner(vertex.vertex, stack.peek().getPositionMatrix());

            return;
        }

        this.snapWeldCorner(vertex.vertex);

        this.emit(builder, group, this.vertex.x, this.vertex.y, this.vertex.z, vertex.uv.x, vertex.uv.y, normal);
    }

    /** Write a single finished vertex (position, uv and normal already resolved) into the buffer. */
    private void emit(BufferBuilder builder, ModelGroup group, float x, float y, float z, float u, float v, Vector3f normal)
    {
        builder.vertex(x, y, z)
            .color(this.r * group.color.r, this.g * group.color.g, this.b * group.color.b, this.a * group.color.a)
            .texture(u, v)
            .overlay(this.overlay);

        if (this.stencilMap != null)
        {
            builder.light(stencilMap.increment ? group.index : 0, 0);
        }
        else
        {
            int lu = (int) Lerps.lerp(this.light & '\uffff', LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, MathUtils.clamp(group.lighting, 0F, 1F));
            int lv = this.light >> 16 & '\uffff';

            builder.light(lu, lv);
        }

        builder.normal(normal.x, normal.y, normal.z).next();
    }

    /**
     * Draw a welded cube's face as a tessellated grid instead of two triangles. The four corners are resolved
     * to their deformed world positions (snapped to the seam where welded), then every sub-vertex is found by
     * bilinear interpolation of those corners and their UVs. Fine sub-quads are each nearly affine, so the
     * texture warps smoothly across the bend instead of kinking along the single diagonal of a flat trapezoid.
     */
    private void renderQuadSubdivided(BufferBuilder builder, Matrix4f matrix, ModelGroup group, ModelQuad quad, Vector3f normal)
    {
        for (int i = 0; i < 4; i++)
        {
            ModelVertex corner = quad.vertices.get(i);

            this.vertex.set(corner.vertex.x, corner.vertex.y, corner.vertex.z, 1);
            matrix.transform(this.vertex);
            this.snapWeldCorner(corner.vertex);

            this.cornerPos[i].set(this.vertex.x, this.vertex.y, this.vertex.z);
            this.cornerU[i] = corner.uv.x;
            this.cornerV[i] = corner.uv.y;
        }

        int n = WELD_SUBDIVISIONS;

        for (int row = 0; row < n; row++)
        {
            for (int col = 0; col < n; col++)
            {
                float s0 = (float) col / n;
                float s1 = (float) (col + 1) / n;
                float t0 = (float) row / n;
                float t1 = (float) (row + 1) / n;

                this.emitInterp(builder, group, s0, t0, normal);
                this.emitInterp(builder, group, s1, t0, normal);
                this.emitInterp(builder, group, s1, t1, normal);
                this.emitInterp(builder, group, s0, t0, normal);
                this.emitInterp(builder, group, s1, t1, normal);
                this.emitInterp(builder, group, s0, t1, normal);
            }
        }
    }

    /** Bilinearly interpolate position and UV across the four resolved corners (s along 0->1, t along 0->3). */
    private void emitInterp(BufferBuilder builder, ModelGroup group, float s, float t, Vector3f normal)
    {
        Vector3f c0 = this.cornerPos[0];
        Vector3f c1 = this.cornerPos[1];
        Vector3f c2 = this.cornerPos[2];
        Vector3f c3 = this.cornerPos[3];

        float bx = c0.x + (c1.x - c0.x) * s;
        float by = c0.y + (c1.y - c0.y) * s;
        float bz = c0.z + (c1.z - c0.z) * s;
        float tx = c3.x + (c2.x - c3.x) * s;
        float ty = c3.y + (c2.y - c3.y) * s;
        float tz = c3.z + (c2.z - c3.z) * s;

        float bu = this.cornerU[0] + (this.cornerU[1] - this.cornerU[0]) * s;
        float bv = this.cornerV[0] + (this.cornerV[1] - this.cornerV[0]) * s;
        float tu = this.cornerU[3] + (this.cornerU[2] - this.cornerU[3]) * s;
        float tv = this.cornerV[3] + (this.cornerV[2] - this.cornerV[3]) * s;

        this.emit(builder, group,
            bx + (tx - bx) * t, by + (ty - by) * t, bz + (tz - bz) * t,
            bu + (tu - bu) * t, bv + (tv - bv) * t, normal);
    }

    /** Capture pass: record a welded face corner's rigid world position, plus the face orientation once. */
    private void captureWeldCorner(Vector3f local, Matrix4f matrix)
    {
        if (this.targetLayer != null)
        {
            int corner = WeldBinding.cornerIndex(this.targetLayer.targetCorners, local);

            if (corner != -1)
            {
                if (!this.targetLayer.targetCaptured)
                {
                    matrix.getNormalizedRotation(this.targetLayer.capturedTargetRot);
                }

                this.targetLayer.capturedTargetWorld[corner].set(this.vertex.x, this.vertex.y, this.vertex.z);
                this.targetLayer.targetCaptured = true;
            }
        }

        if (this.sourceLayer != null)
        {
            int corner = WeldBinding.cornerIndex(this.sourceLayer.sourceCorners, local);

            if (corner != -1)
            {
                if (!this.sourceLayer.sourceCaptured)
                {
                    matrix.getNormalizedRotation(this.sourceLayer.capturedSourceRot);
                }

                this.sourceLayer.capturedSourceWorld[corner].set(this.vertex.x, this.vertex.y, this.vertex.z);
                this.sourceLayer.sourceCaptured = true;
            }
        }
    }

    /** Draw pass: pull a welded corner onto the layer's seam, so both sides of the joint bend toward it. */
    private void snapWeldCorner(Vector3f local)
    {
        if (this.targetLayer != null && this.targetLayer.seamReady)
        {
            int corner = WeldBinding.cornerIndex(this.targetLayer.targetCorners, local);

            if (corner != -1)
            {
                Vector3f seam = this.targetLayer.seam[corner];

                this.vertex.set(seam.x, seam.y, seam.z, 1);

                return;
            }
        }

        if (this.sourceLayer != null && this.sourceLayer.seamReady)
        {
            int corner = WeldBinding.cornerIndex(this.sourceLayer.sourceCorners, local);

            if (corner != -1)
            {
                int target = this.sourceLayer.sourceToTarget[corner];

                if (target != -1)
                {
                    Vector3f seam = this.sourceLayer.seam[target];

                    this.vertex.set(seam.x, seam.y, seam.z, 1);
                }
            }
        }
    }
}