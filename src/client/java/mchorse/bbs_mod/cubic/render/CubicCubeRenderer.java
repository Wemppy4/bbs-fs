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

import java.util.ArrayList;
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

    /* The form-level color overlay texture is bound for the CPU draw (see ModelInstance), so
     * vertices must point at its single texel instead of the vanilla overlay UV. */
    protected boolean cpuOverlayActive;

    /* Temporary variables to avoid allocating and GC vectors */
    protected Vector3f normal = new Vector3f();
    protected Vector4f vertex = new Vector4f();

    private ModelVertex modelVertex = new ModelVertex();
    private ShapeKeys shapeKeys;

    /* Welds active for the model being rendered (null when it has none). Resolved once on the instance. */
    protected List<WeldBinding> welds;

    /* ALL weld layers the cube currently being rendered is the target/source of — set per cube, consulted
     * per vertex. Lists, not single slots: one cube legitimately carries several welds (a pelvis cube with
     * both thighs welded in, a bone welded up to its parent and down to its child on opposite faces). */
    private final List<WeldBinding.Layer> targetLayers = new ArrayList<>();
    private final List<WeldBinding.Layer> sourceLayers = new ArrayList<>();

    /* Capture pass records the rigid world corners of welded faces without drawing; the draw pass snaps to the seam.
     * It only touches welded cubes and only their welded face's four corners — not every vertex of the model. */
    private boolean captureOnly;

    /* A welded cube's faces bend within a band near the seam; drawn as two flat triangles their texture warps
     * unevenly, so the edge running ALONG the bone is split into this many segments and the bend is resolved
     * across them (the cross-bone edge stays linear, so it needs no split). Kept fairly high so a narrow falloff
     * band stays smooth instead of faceting over too few cells — cheap now that it is one direction, not N×N. */
    private static final int WELD_SUBDIVISIONS = 8;

    /* Since coordinates are in /16 model units, this catches vertices lying on a welded plane without
     * grabbing anything a texel away. */
    private static final float WELD_PLANE_EPS = 1.0e-4F;

    private final Vector3f[] rigidPos = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    private final Vector3f[] cornerNormal = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    private final float[] cornerU = new float[4];
    private final float[] cornerV = new float[4];
    private final Vector3f seamPosition = new Vector3f();

    /* Per-cube seam-ready snaps (one per active layer x role), pooled so no per-frame allocation:
     * nearSeam culls by them, snapWeldCorner pulls vertices by them, the subdivided path blends by them. */
    private final List<WeldSnap> snapPool = new ArrayList<>();
    private int snapCount;

    /** One weld snap of the cube being rendered: a seam-ready layer plus this cube's role in it. */
    private static class WeldSnap
    {
        private WeldBinding.Layer layer;
        private boolean source;

        /* This role's local face plane and falloff band, unpacked from the layer for the per-vertex checks. */
        private Vector3f faceNormal;
        private float weldPlane;
        private float band;

        /* Distance of the current quad's corners from the weld plane, filled by renderQuadSubdivided. */
        private final float[] cornerDist = new float[4];

        /* THIS seam's world displacement at each corner (seam minus rigid; zero off its plane), filled by
         * renderQuadSubdivided. Kept per snap so each seam bends the quad only by its OWN displacement —
         * one shared "snapped surface" bleeds one seam's motion into the other's falloff band (a knee cube
         * welded to leg and foot wiggled near the leg when only the foot bent). */
        private final Vector3f[] cornerDisp = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    }

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

    public void setCpuOverlayActive(boolean active)
    {
        this.cpuOverlayActive = active;
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
        this.collectSnaps();

        boolean subdivide = this.snapCount > 0;

        for (ModelQuad quad : cube.quads)
        {
            int count = quad.vertices.size();

            if (count != 3 && count != 4)
            {
                continue;
            }

            /* Only quads within a seam's bend band tessellate — everything further is rigid anyway, so it
             * takes the plain path (most of a cube's quads are far ones). */
            if (subdivide && this.nearSeam(quad))
            {
                this.renderQuadSubdivided(builder, stack, group, quad);
            }
            else
            {
                this.writeVertex(builder, stack, group, quad.vertices.get(0));
                this.writeVertex(builder, stack, group, quad.vertices.get(1));
                this.writeVertex(builder, stack, group, quad.vertices.get(2));

                if (count == 4)
                {
                    this.writeVertex(builder, stack, group, quad.vertices.get(0));
                    this.writeVertex(builder, stack, group, quad.vertices.get(2));
                    this.writeVertex(builder, stack, group, quad.vertices.get(3));
                }
            }
        }

        stack.pop();
    }

    /**
     * Turn the picked layers into the per-cube snap list: one entry per seam-ready layer and role, with the
     * role's local plane and band unpacked. Entries are pooled and reused across cubes.
     */
    private void collectSnaps()
    {
        this.snapCount = 0;

        for (WeldBinding.Layer layer : this.targetLayers)
        {
            if (layer.seamReady)
            {
                this.addSnap(layer, false, layer.targetFaceNormal, layer.targetWeldPlane, layer.falloff * layer.targetAxisExtent);
            }
        }

        for (WeldBinding.Layer layer : this.sourceLayers)
        {
            if (layer.seamReady)
            {
                this.addSnap(layer, true, layer.sourceFaceNormal, layer.sourceWeldPlane, layer.falloff * layer.sourceAxisExtent);
            }
        }
    }

    private void addSnap(WeldBinding.Layer layer, boolean source, Vector3f faceNormal, float weldPlane, float band)
    {
        if (this.snapCount == this.snapPool.size())
        {
            this.snapPool.add(new WeldSnap());
        }

        WeldSnap snap = this.snapPool.get(this.snapCount++);

        snap.layer = layer;
        snap.source = source;
        snap.faceNormal = faceNormal;
        snap.weldPlane = weldPlane;
        snap.band = band;
    }

    /**
     * Whether any of the quad's corners sits within a seam's bend band; on-plane vertices always count.
     * Checking corners is exact, not approximate: a cube sits entirely on one side of its own welded
     * plane, so the distance over a planar quad is affine and takes its minimum at a corner.
     */
    private boolean nearSeam(ModelQuad quad)
    {
        for (ModelVertex vertex : quad.vertices)
        {
            for (int i = 0; i < this.snapCount; i++)
            {
                WeldSnap snap = this.snapPool.get(i);

                if (Math.abs(vertex.vertex.dot(snap.faceNormal) - snap.weldPlane) <= snap.band + WELD_PLANE_EPS)
                {
                    return true;
                }
            }
        }

        return false;
    }

    protected void renderMesh(BufferBuilder builder, MatrixStack stack, Model model, ModelGroup group, ModelMesh mesh)
    {
        if (this.captureOnly)
        {
            /* Meshes carry no welded box faces, so the capture pass has nothing to record from them. */
            return;
        }

        this.targetLayers.clear();
        this.sourceLayers.clear();
        this.snapCount = 0;

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

    /** Find ALL the weld layers the cube being rendered is the target/source of, so capture/snap can run per vertex. */
    private void pickWelds(ModelCube cube)
    {
        this.targetLayers.clear();
        this.sourceLayers.clear();

        if (this.welds == null)
        {
            return;
        }

        for (WeldBinding weld : this.welds)
        {
            for (WeldBinding.Layer layer : weld.layers)
            {
                if (layer.targetCube == cube) this.targetLayers.add(layer);
                if (layer.sourceCube == cube) this.sourceLayers.add(layer);
            }
        }
    }

    /** Write a cube vertex with its own normal, transformed per vertex. */
    protected void writeVertex(BufferBuilder builder, MatrixStack stack, ModelGroup group, ModelVertex vertex)
    {
        this.normal.set(vertex.normal.x, vertex.normal.y, vertex.normal.z);
        stack.peek().getNormalMatrix().transform(this.normal);

        this.writeVertex(builder, stack, group, vertex, this.normal);
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
            .overlay(this.cpuOverlayActive ? 0 : this.overlay);

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
     * Draw a welded cube's face as a tessellated grid instead of two triangles. Each corner is resolved
     * rigidly, and every seam records its OWN displacement at the corners on its plane (zero elsewhere).
     * Every sub-vertex then adds each seam's interpolated displacement scaled by that seam's falloff weight
     * (full at the joint, fading to nothing a band away) — so each seam bends only the strip near itself
     * while the rest of the cube stays straight. The weight is evaluated per sub-vertex (not interpolated
     * from the corners, which only ever sit at distance 0 or the full length) so the band actually shapes
     * the bend. Fine sub-quads are each nearly affine, so the texture warps smoothly across that band
     * instead of kinking along the diagonal of a flat trapezoid. Normals interpolate from the corners' own
     * normals, so curved strips keep their smooth shading.
     */
    private void renderQuadSubdivided(BufferBuilder builder, MatrixStack stack, ModelGroup group, ModelQuad quad)
    {
        Matrix4f matrix = stack.peek().getPositionMatrix();
        Matrix3f normalMatrix = stack.peek().getNormalMatrix();
        int count = quad.vertices.size();

        for (int i = 0; i < 4; i++)
        {
            /* A triangle rides through as a quad with its last corner doubled. */
            ModelVertex corner = quad.vertices.get(Math.min(i, count - 1));

            this.vertex.set(corner.vertex.x, corner.vertex.y, corner.vertex.z, 1);
            matrix.transform(this.vertex);
            this.rigidPos[i].set(this.vertex.x, this.vertex.y, this.vertex.z);

            this.cornerNormal[i].set(corner.normal);
            normalMatrix.transform(this.cornerNormal[i]);

            for (int k = 0; k < this.snapCount; k++)
            {
                WeldSnap snap = this.snapPool.get(k);

                snap.cornerDist[i] = Math.abs(corner.vertex.dot(snap.faceNormal) - snap.weldPlane);
            }

            /* Each snap's own displacement at this corner: seam minus rigid on its plane, zero elsewhere.
             * The first on-plane snap claims the corner — the same priority snapWeldCorner uses. */
            boolean claimed = false;

            for (int k = 0; k < this.snapCount; k++)
            {
                WeldSnap snap = this.snapPool.get(k);
                Vector3f disp = snap.cornerDisp[i];

                if (!claimed && snap.cornerDist[i] < WELD_PLANE_EPS)
                {
                    Vector3f seam = snap.source ? snap.layer.seamAtSource(corner.vertex, this.seamPosition) : snap.layer.seamAtTarget(corner.vertex, this.seamPosition);

                    disp.set(seam).sub(this.rigidPos[i]);
                    claimed = true;
                }
                else
                {
                    disp.set(0F, 0F, 0F);
                }
            }

            this.cornerU[i] = corner.uv.x;
            this.cornerV[i] = corner.uv.y;
        }

        int nS = 1;
        int nT = 1;
        Vector3f c0 = quad.vertices.get(0).vertex;
        Vector3f cS = quad.vertices.get(1).vertex;
        Vector3f cT = quad.vertices.get(Math.min(3, count - 1)).vertex;

        /* Per snap, only the edge running along ITS bone axis bends non-linearly; the other stays linear, so
         * 1 segment is exact. Snaps on different faces can pull different edges — then both directions split. */
        for (int k = 0; k < this.snapCount; k++)
        {
            Vector3f axis = this.snapPool.get(k).faceNormal;
            float alongS = Math.abs((cS.x - c0.x) * axis.x + (cS.y - c0.y) * axis.y + (cS.z - c0.z) * axis.z);
            float alongT = Math.abs((cT.x - c0.x) * axis.x + (cT.y - c0.y) * axis.y + (cT.z - c0.z) * axis.z);

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

                this.emitInterp(builder, group, s0, t0);
                this.emitInterp(builder, group, s1, t0);
                this.emitInterp(builder, group, s1, t1);
                this.emitInterp(builder, group, s0, t0);
                this.emitInterp(builder, group, s1, t1);
                this.emitInterp(builder, group, s0, t1);
            }
        }
    }

    /**
     * Bilinearly interpolate UV and the rigid position across the four corners (s along 0->1, t along 0->3),
     * then add EACH seam's interpolated displacement scaled by that seam's OWN falloff weight — the falloff
     * curve evaluated on this sub-vertex's interpolated distance from that seam. Per-seam, not a shared
     * max-weighted "snapped surface": a shared surface bleeds one seam's motion into the other seam's band
     * on a cube welded at both ends, so bending only the foot wiggled the knee's leg-side band too.
     */
    private void emitInterp(BufferBuilder builder, ModelGroup group, float s, float t)
    {
        Vector3f[] r = this.rigidPos;

        float x = bilerp(r[0].x, r[1].x, r[2].x, r[3].x, s, t);
        float y = bilerp(r[0].y, r[1].y, r[2].y, r[3].y, s, t);
        float z = bilerp(r[0].z, r[1].z, r[2].z, r[3].z, s, t);

        for (int i = 0; i < this.snapCount; i++)
        {
            WeldSnap snap = this.snapPool.get(i);
            float distance = bilerp(snap.cornerDist[0], snap.cornerDist[1], snap.cornerDist[2], snap.cornerDist[3], s, t);
            float w = falloffWeight(distance, snap.band);

            if (w > 0F)
            {
                Vector3f[] d = snap.cornerDisp;

                x += w * bilerp(d[0].x, d[1].x, d[2].x, d[3].x, s, t);
                y += w * bilerp(d[0].y, d[1].y, d[2].y, d[3].y, s, t);
                z += w * bilerp(d[0].z, d[1].z, d[2].z, d[3].z, s, t);
            }
        }

        float u = bilerp(this.cornerU[0], this.cornerU[1], this.cornerU[2], this.cornerU[3], s, t);
        float v = bilerp(this.cornerV[0], this.cornerV[1], this.cornerV[2], this.cornerV[3], s, t);
        Vector3f[] n = this.cornerNormal;

        this.normal.set(
            bilerp(n[0].x, n[1].x, n[2].x, n[3].x, s, t),
            bilerp(n[0].y, n[1].y, n[2].y, n[3].y, s, t),
            bilerp(n[0].z, n[1].z, n[2].z, n[3].z, s, t)
        ).normalize();

        this.emit(builder, group, x, y, z, u, v, this.normal);
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

        if (this.targetLayers.isEmpty() && this.sourceLayers.isEmpty())
        {
            return;
        }

        Matrix4f bone = stack.peek().getPositionMatrix();

        stack.push();
        moveToPivot(stack, cube.pivot);
        rotate(stack, cube.rotate);
        moveBackFromPivot(stack, cube.pivot);

        Matrix4f cubeMatrix = stack.peek().getPositionMatrix();

        for (WeldBinding.Layer layer : this.targetLayers)
        {
            if (layer.targetCaptured)
            {
                continue;
            }

            for (int i = 0; i < layer.targetCorners.length; i++)
            {
                cubeMatrix.transformPosition(layer.targetCorners[i], layer.capturedTargetWorld[i]);
            }

            cubeMatrix.transformDirection(layer.capturedTargetNormalWorld.set(layer.targetFaceNormal)).normalize();
            bone.transformDirection(layer.capturedTargetBoneAxis.set(layer.targetFaceNormal)).normalize();
            layer.targetCaptured = true;
        }

        for (WeldBinding.Layer layer : this.sourceLayers)
        {
            if (layer.sourceCaptured)
            {
                continue;
            }

            for (int i = 0; i < layer.sourceCorners.length; i++)
            {
                cubeMatrix.transformPosition(layer.sourceCorners[i], layer.capturedSourceWorld[i]);
            }

            bone.transformDirection(layer.capturedSourceBoneAxis.set(layer.sourceFaceNormal)).normalize();
            layer.sourceCaptured = true;
        }

        stack.pop();
    }

    /**
     * Draw pass: pull a vertex lying on a welded plane onto the layer's seam — bilinear over the welded
     * face's rect, so inset geometry at the joint rides the seam too, not only the four exact corners.
     */
    private void snapWeldCorner(Vector3f local)
    {
        for (int i = 0; i < this.snapCount; i++)
        {
            WeldSnap snap = this.snapPool.get(i);

            if (Math.abs(local.dot(snap.faceNormal) - snap.weldPlane) < WELD_PLANE_EPS)
            {
                Vector3f seam = snap.source ? snap.layer.seamAtSource(local, this.seamPosition) : snap.layer.seamAtTarget(local, this.seamPosition);

                this.vertex.set(seam.x, seam.y, seam.z, 1);

                return;
            }
        }
    }
}