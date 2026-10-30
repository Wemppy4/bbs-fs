package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelData;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.cubic.data.model.ModelQuad;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
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

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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
    protected Predicate<String> materialVisibility = (material) -> true;

    /* The form-level color overlay texture is bound for the CPU draw (see ModelInstance), so
     * vertices must point at its single texel instead of the vanilla overlay UV. */
    protected boolean cpuOverlayActive;

    /* Temporary variables to avoid allocating and GC vectors */
    protected Vector3f normal = new Vector3f();
    protected Vector4f vertex = new Vector4f();

    /* Bones kept inside the bone the model is worn on: their vertices go from the stack's frame into the
     * carrier's, are pressed into its box, and come back. */
    private Set<String> carrierBones;
    private Matrix4f toCarrier;
    private Matrix4f fromCarrier;
    private CarrierClip carrier;

    private ModelVertex modelVertex = new ModelVertex();
    private ShapeKeys shapeKeys;

    /* Quads the welded band draws in their place (see WeldMesh) — left out here; null when none. */
    private WeldMesh band;

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

    public void setMaterialVisibility(Predicate<String> visibility)
    {
        this.materialVisibility = visibility;
    }

    public void setColor(float r, float g, float b, float a)
    {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    /** @param toCarrier the stack's frame (as this renderer is handed it) into the carrier bone's frame */
    public void setCarrierClip(Set<String> bones, CarrierClip carrier, Matrix4f toCarrier)
    {
        boolean on = bones != null && !bones.isEmpty() && carrier != null && toCarrier != null;

        this.carrierBones = on ? bones : null;
        this.carrier = on ? carrier : null;
        this.toCarrier = on ? toCarrier : null;
        this.fromCarrier = on ? new Matrix4f(toCarrier).invert() : null;
    }

    /** Leave out the quads this welded band draws instead. */
    public void setBand(WeldMesh band)
    {
        this.band = band;
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
        if (!this.materialVisibility.test(cube.material))
        {
            return;
        }

        stack.push();
        moveToPivot(stack, cube.pivot);
        rotate(stack, cube.rotate);
        moveBackFromPivot(stack, cube.pivot);

        for (ModelQuad quad : cube.quads)
        {
            int count = quad.vertices.size();

            if (count != 3 && count != 4 || (this.band != null && this.band.isBand(quad)))
            {
                continue;
            }

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

        stack.pop();
    }

    protected void renderMesh(BufferBuilder builder, MatrixStack stack, Model model, ModelGroup group, ModelMesh mesh)
    {
        if (!this.materialVisibility.test(mesh.material))
        {
            return;
        }

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

        if (this.carrierBones != null && this.carrierBones.contains(group.id))
        {
            this.toCarrier.transform(this.vertex);
            this.vertex.x = Math.max(this.carrier.min().x, Math.min(this.carrier.max().x, this.vertex.x));
            this.vertex.y = Math.max(this.carrier.min().y, Math.min(this.carrier.max().y, this.vertex.y));
            this.vertex.z = Math.max(this.carrier.min().z, Math.min(this.carrier.max().z, this.vertex.z));
            this.fromCarrier.transform(this.vertex);
        }

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
     * Write a posed welded band (see {@link WeldMesh#deform}) — its points are in the frame the walk started
     * in, the model's root — for the bones and materials that show, coloured and lit like the rest of the
     * bake.
     */
    public void renderWeldBand(BufferBuilder builder, WeldMesh band)
    {
        for (WeldMesh.Quad quad : band.quads)
        {
            if (!quad.group.isVisible() || !this.materialVisibility.test(quad.material))
            {
                continue;
            }

            for (int row = 0; row < quad.nT; row++)
            {
                for (int col = 0; col < quad.nS; col++)
                {
                    int i00 = quad.point(col, row);
                    int i10 = i00 + 1;
                    int i01 = quad.point(col, row + 1);
                    int i11 = i01 + 1;

                    this.emitBandPoint(builder, band, quad.group, i00);
                    this.emitBandPoint(builder, band, quad.group, i10);
                    this.emitBandPoint(builder, band, quad.group, i11);
                    this.emitBandPoint(builder, band, quad.group, i00);
                    this.emitBandPoint(builder, band, quad.group, i11);
                    this.emitBandPoint(builder, band, quad.group, i01);
                }
            }
        }
    }

    private void emitBandPoint(BufferBuilder builder, WeldMesh band, ModelGroup group, int point)
    {
        int p = point * 3;

        this.normal.set(band.normals[p], band.normals[p + 1], band.normals[p + 2]);
        this.emit(builder, group, band.positions[p], band.positions[p + 1], band.positions[p + 2], band.texCoords[point * 2], band.texCoords[point * 2 + 1], this.normal);
    }
}
