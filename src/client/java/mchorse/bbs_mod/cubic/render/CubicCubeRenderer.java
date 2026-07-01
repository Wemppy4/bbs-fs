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

    /* Capture pass records the rigid world corners of welded faces without drawing; the draw pass snaps to the seam.
     * It only touches welded cubes and only their welded face's four corners — not every vertex of the model. */
    private boolean captureOnly;

    /* A welded cube's faces bend within a band near the seam; drawn as two flat triangles their texture warps
     * unevenly, so the edge running ALONG the bone is split into this many segments and the bend is resolved
     * across them (the cross-bone edge stays linear, so it needs no split). Kept fairly high so a narrow falloff
     * band stays smooth instead of faceting over too few cells — cheap now that it is one direction, not N×N. */
    private static final int WELD_SUBDIVISIONS = 8;

    private final Vector3f[] rigidPos = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    private final Vector3f[] cornerPos = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    private final float[] cornerDistTarget = new float[4];
    private final float[] cornerDistSource = new float[4];
    private final float[] cornerU = new float[4];
    private final float[] cornerV = new float[4];

    /* Per-cube weld band state, set up before a welded cube's faces are subdivided. */
    private boolean weldHasTarget;
    private boolean weldHasSource;
    private float weldBandTarget;
    private float weldBandSource;

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
        if (this.captureOnly)
        {
            this.captureCube(stack, cube);

            return;
        }

        stack.push();
        moveToPivot(stack, cube.pivot);
        rotate(stack, cube.rotate);
        moveBackFromPivot(stack, cube.pivot);

        this.pickWelds(cube);

        boolean subdivide = (this.targetLayer != null && this.targetLayer.seamReady) || (this.sourceLayer != null && this.sourceLayer.seamReady);
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
        if (this.captureOnly)
        {
            /* Meshes carry no welded box faces, so the capture pass has nothing to record from them. */
            return;
        }

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
     * Draw a welded cube's face as a tessellated grid instead of two triangles. Each corner is resolved both
     * rigidly (its plain transformed position) and welded (snapped to the seam), and tagged with its distance
     * from the seam along the bone axis. Every sub-vertex interpolates that distance, turns it into a falloff
     * weight (full at the joint, fading to nothing a band away), and blends between its rigid and welded
     * position by it — so only the strip near the seam bends while the rest of the cube stays straight. The
     * weight is evaluated per sub-vertex (not interpolated from the corners, which only ever sit at distance 0
     * or the full length) so the band actually shapes the bend. Fine sub-quads are each nearly affine, so the
     * texture warps smoothly across that band instead of kinking along the diagonal of a flat trapezoid.
     */
    private void renderQuadSubdivided(BufferBuilder builder, Matrix4f matrix, ModelGroup group, ModelQuad quad, Vector3f normal)
    {
        this.weldHasTarget = this.targetLayer != null && this.targetLayer.seamReady;
        this.weldHasSource = this.sourceLayer != null && this.sourceLayer.seamReady;
        this.weldBandTarget = this.weldHasTarget ? this.targetLayer.falloff * this.targetLayer.targetAxisExtent : 0F;
        this.weldBandSource = this.weldHasSource ? this.sourceLayer.falloff * this.sourceLayer.sourceAxisExtent : 0F;

        for (int i = 0; i < 4; i++)
        {
            ModelVertex corner = quad.vertices.get(i);

            this.vertex.set(corner.vertex.x, corner.vertex.y, corner.vertex.z, 1);
            matrix.transform(this.vertex);
            this.rigidPos[i].set(this.vertex.x, this.vertex.y, this.vertex.z);

            this.cornerDistTarget[i] = this.weldHasTarget
                ? Math.abs(corner.vertex.dot(this.targetLayer.targetFaceNormal) - this.targetLayer.targetWeldPlane) : 0F;
            this.cornerDistSource[i] = this.weldHasSource
                ? Math.abs(corner.vertex.dot(this.sourceLayer.sourceFaceNormal) - this.sourceLayer.sourceWeldPlane) : 0F;

            this.snapWeldCorner(corner.vertex);

            this.cornerPos[i].set(this.vertex.x, this.vertex.y, this.vertex.z);
            this.cornerU[i] = corner.uv.x;
            this.cornerV[i] = corner.uv.y;
        }

        int nS = 1;
        int nT = 1;
        Vector3f axis = this.weldAxis();

        if (axis != null)
        {
            Vector3f c0 = quad.vertices.get(0).vertex;
            float alongS = Math.abs((quad.vertices.get(1).vertex.x - c0.x) * axis.x + (quad.vertices.get(1).vertex.y - c0.y) * axis.y + (quad.vertices.get(1).vertex.z - c0.z) * axis.z);
            float alongT = Math.abs((quad.vertices.get(3).vertex.x - c0.x) * axis.x + (quad.vertices.get(3).vertex.y - c0.y) * axis.y + (quad.vertices.get(3).vertex.z - c0.z) * axis.z);

            /* Only the edge running along the bone bends non-linearly; the other stays linear, so 1 segment is exact. */
            if (Math.max(alongS, alongT) > 1.0e-4F)
            {
                if (alongS >= alongT) nS = WELD_SUBDIVISIONS;
                else nT = WELD_SUBDIVISIONS;
            }
        }

        for (int row = 0; row < nT; row++)
        {
            for (int col = 0; col < nS; col++)
            {
                float s0 = (float) col / nS;
                float s1 = (float) (col + 1) / nS;
                float t0 = (float) row / nT;
                float t1 = (float) (row + 1) / nT;

                this.emitInterp(builder, group, s0, t0, normal);
                this.emitInterp(builder, group, s1, t0, normal);
                this.emitInterp(builder, group, s1, t1, normal);
                this.emitInterp(builder, group, s0, t0, normal);
                this.emitInterp(builder, group, s1, t1, normal);
                this.emitInterp(builder, group, s0, t1, normal);
            }
        }
    }

    /** The bone-length axis (local) the welded cube bends along, or null when this cube has no ready seam. */
    private Vector3f weldAxis()
    {
        if (this.weldHasTarget) return this.targetLayer.targetFaceNormal;
        if (this.weldHasSource) return this.sourceLayer.sourceFaceNormal;

        return null;
    }

    /**
     * Bilinearly interpolate UV and both the rigid and welded position across the four corners (s along 0->1,
     * t along 0->3), then blend the two positions by the seam weight — the falloff curve evaluated on this
     * sub-vertex's interpolated distance from the seam — so the bend stays local.
     */
    private void emitInterp(BufferBuilder builder, ModelGroup group, float s, float t, Vector3f normal)
    {
        float w = 0F;

        if (this.weldHasTarget)
        {
            float distance = bilerp(this.cornerDistTarget[0], this.cornerDistTarget[1], this.cornerDistTarget[2], this.cornerDistTarget[3], s, t);

            w = Math.max(w, falloffWeight(distance, this.weldBandTarget));
        }

        if (this.weldHasSource)
        {
            float distance = bilerp(this.cornerDistSource[0], this.cornerDistSource[1], this.cornerDistSource[2], this.cornerDistSource[3], s, t);

            w = Math.max(w, falloffWeight(distance, this.weldBandSource));
        }

        Vector3f[] r = this.rigidPos;
        Vector3f[] c = this.cornerPos;

        float rx = bilerp(r[0].x, r[1].x, r[2].x, r[3].x, s, t);
        float ry = bilerp(r[0].y, r[1].y, r[2].y, r[3].y, s, t);
        float rz = bilerp(r[0].z, r[1].z, r[2].z, r[3].z, s, t);

        float sx = bilerp(c[0].x, c[1].x, c[2].x, c[3].x, s, t);
        float sy = bilerp(c[0].y, c[1].y, c[2].y, c[3].y, s, t);
        float sz = bilerp(c[0].z, c[1].z, c[2].z, c[3].z, s, t);

        float u = bilerp(this.cornerU[0], this.cornerU[1], this.cornerU[2], this.cornerU[3], s, t);
        float v = bilerp(this.cornerV[0], this.cornerV[1], this.cornerV[2], this.cornerV[3], s, t);

        this.emit(builder, group,
            rx + (sx - rx) * w, ry + (sy - ry) * w, rz + (sz - rz) * w,
            u, v, normal);
    }

    /** Bilinear blend of four corner scalars laid out as (0,1) along the bottom edge and (3,2) along the top. */
    private static float bilerp(float c0, float c1, float c2, float c3, float s, float t)
    {
        float bottom = c0 + (c1 - c0) * s;
        float top = c3 + (c2 - c3) * s;

        return bottom + (top - bottom) * t;
    }

    /** Smoothstep falloff: 1 at the seam, 0 at or beyond {@code band}, with a smooth (no-kink) ramp between. */
    private static float falloffWeight(float distance, float band)
    {
        if (band <= 1.0e-5F)
        {
            return distance <= 1.0e-5F ? 1F : 0F;
        }

        float x = distance / band;

        if (x <= 0F) return 1F;
        if (x >= 1F) return 0F;

        return 1F - x * x * (3F - 2F * x);
    }

    /**
     * Capture pass for one cube: if it carries a welded face, record that face's four rigid world corners plus the
     * shear axis (face normal by the FULL cube matrix — the cube's modeling rotation is a legit part of the face
     * direction) and the bend axis (same normal by the BONE matrix only, so a cube's static rotate can't masquerade
     * as a fold). Only welded cubes do any transform work; every other cube returns at once, so the capture is a
     * light matrix walk over the tree, not a full per-vertex pass.
     */
    private void captureCube(MatrixStack stack, ModelCube cube)
    {
        this.pickWelds(cube);

        if (this.targetLayer == null && this.sourceLayer == null)
        {
            return;
        }

        Matrix4f bone = stack.peek().getPositionMatrix();

        stack.push();
        moveToPivot(stack, cube.pivot);
        rotate(stack, cube.rotate);
        moveBackFromPivot(stack, cube.pivot);

        Matrix4f cubeMatrix = stack.peek().getPositionMatrix();

        if (this.targetLayer != null && !this.targetLayer.targetCaptured)
        {
            WeldBinding.Layer layer = this.targetLayer;

            for (int i = 0; i < layer.targetCorners.length; i++)
            {
                cubeMatrix.transformPosition(layer.targetCorners[i], layer.capturedTargetWorld[i]);
            }

            cubeMatrix.transformDirection(layer.capturedTargetNormalWorld.set(layer.targetFaceNormal)).normalize();
            bone.transformDirection(layer.capturedTargetBoneAxis.set(layer.targetFaceNormal)).normalize();
            layer.targetCaptured = true;
        }

        if (this.sourceLayer != null && !this.sourceLayer.sourceCaptured)
        {
            WeldBinding.Layer layer = this.sourceLayer;

            for (int i = 0; i < layer.sourceCorners.length; i++)
            {
                cubeMatrix.transformPosition(layer.sourceCorners[i], layer.capturedSourceWorld[i]);
            }

            bone.transformDirection(layer.capturedSourceBoneAxis.set(layer.sourceFaceNormal)).normalize();
            layer.sourceCaptured = true;
        }

        stack.pop();
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