package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The round parts of the gizmo: the three rotation rings, the view ring that always faces
 * the camera, and the sphere they sit around. Split out of {@link Gizmo} because their
 * geometry answers to something else entirely — the axes scale and thickness settings —
 * and is therefore cached rather than rebuilt, while everything else in the gizmo is drawn
 * from the current frame's state.
 *
 * <p>One instance per gizmo; it owns the cached geometry and rebuilds it when the settings move.
 *
 * <p>The cache is a CPU one. 1.21.1 held each shape in a {@code VertexBuffer} and re-drew it with a
 * different {@code RenderSystem.setShaderColor} per pass; the 1.21.5+ GPU rewrite removed both, so
 * colour has to reach the vertices themselves and the geometry is re-emitted every draw. What the
 * cache still saves is the part that actually cost — the tessellation of a 96&times;24 torus, nine
 * times a frame — while re-emitting is a transform and a copy of vertices already computed. Same
 * bargain {@link mchorse.bbs_mod.cubic.render.vao.ModelVAO} struck for cubic models.
 *
 * <p>Nothing here submits: the write methods fill a {@link BufferBuilder} the caller opened, so a
 * whole gizmo pass — rings, view ring and handle boxes alike — leaves as one draw.
 */
public class GizmoRings
{
    /** How much wider than a rotation ring the camera-facing view ring is drawn. */
    public final static float VIEW_RING_SCALE = 1.2F;

    /**
     * Out-of-plane lift for the near/far cut of a rotation ring. A ring seen face-on has an
     * in-plane dot of ~0 all the way round, so without this it would flicker between fully
     * drawn and fully culled.
     */
    private final static float RING_FACE_ON_BIAS = 0.18F;

    /** Points sampled around a ring when working out its camera-facing arc. */
    private final static int RING_OCCLUSION_SAMPLES = 90;

    /** Vertex positions of the full ring and the sphere, in triples, tessellated at identity. */
    private float[] ringGeometry;
    private float[] sphereGeometry;

    private float lastScale = -1F;
    private float lastThickness = -1F;

    /* Cached tessellation of each rotation ring's visible arc, one slot per axis. A ring's
     * geometry is a pure function of (radius, thickness, arc) — the arc only moves with the
     * camera, so on a still viewport the slot never re-tessellates, and the same vertices serve
     * the colour pass and the stencil pass of the frame (colour is written per emit). This
     * replaced re-tessellating a 96x24 torus in immediate mode NINE times per frame — the single
     * biggest FPS cost of the editor. */
    private final ArcSlot[] arcSlots = {new ArcSlot(), new ArcSlot(), new ArcSlot()};

    private final Vector2f arcScratch = new Vector2f();
    private final boolean[] occlusionScratch = new boolean[RING_OCCLUSION_SAMPLES];

    /** Scratch for the CPU transform of a cached vertex, so an emit allocates nothing. */
    private final Vector4f vertexScratch = new Vector4f();

    private static class ArcSlot
    {
        float[] geometry;
        float radius = -1F;
        float thickness;
        float start;
        float sweep;
    }

    /**
     * Rebuilds the cached geometry when the axes scale or thickness settings changed. Every draw
     * call here runs it first, so no caller has to remember to — one that forgot drew nothing at
     * all, and the miss showed up only in whichever gizmo element happened to be alone on screen.
     */
    private void update()
    {
        float scale = BBSSettings.axesScale.get();
        float thickness = BBSSettings.axesThickness.get();

        if (this.ringGeometry == null || scale != this.lastScale || thickness != this.lastThickness)
        {
            float radius = 0.22F * scale;
            float thicknessRing = 0.02F * scale * thickness;

            BufferBuilder builder = tessellate();

            Draw.arc3D(builder, new MatrixStack(), Axis.Y, radius, thicknessRing, 1F, 1F, 1F, 0F, 360F);

            this.ringGeometry = capture(builder);

            builder = tessellate();

            Draw.sphere(builder, new MatrixStack(), radius, 24, 24, 1F, 1F, 1F, 1F);

            this.sphereGeometry = capture(builder);

            this.lastScale = scale;
            this.lastThickness = thickness;
        }
    }

    /** Open a batch to tessellate cached geometry into. Never submitted — {@link #capture} eats it. */
    private static BufferBuilder tessellate()
    {
        return Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
    }

    /**
     * Take the vertex positions out of a freshly tessellated batch and drop the batch.
     *
     * <p>POSITION_COLOR is three floats then four bytes; the colour is thrown away because every
     * emit writes its own — that is the whole reason this is a CPU cache and not a GPU one.</p>
     */
    private static float[] capture(BufferBuilder builder)
    {
        BuiltBuffer built = builder.endNullable();

        if (built == null)
        {
            return new float[0];
        }

        try
        {
            /* duplicate() resets byte order to BIG_ENDIAN — same note as FormRenderCapture#capture. */
            ByteBuffer bytes = built.getBuffer().duplicate().order(ByteOrder.nativeOrder());
            int stride = VertexFormats.POSITION_COLOR.getVertexSize();
            int base = bytes.position();
            int count = bytes.remaining() / stride;
            float[] out = new float[count * 3];

            for (int i = 0; i < count; i++)
            {
                int at = base + i * stride;

                out[i * 3] = bytes.getFloat(at);
                out[i * 3 + 1] = bytes.getFloat(at + 4);
                out[i * 3 + 2] = bytes.getFloat(at + 8);
            }

            return out;
        }
        finally
        {
            built.close();
        }
    }

    /**
     * Write cached geometry into {@code builder}, transformed by {@code matrix} and painted the
     * given colour. The transform is done here rather than left to the draw because a whole gizmo
     * pass shares one batch, and each shape in it sits at a matrix of its own.
     */
    private void emit(BufferBuilder builder, float[] geometry, Matrix4f matrix, float r, float g, float b, float a)
    {
        Vector4f vertex = this.vertexScratch;

        for (int i = 0; i < geometry.length; i += 3)
        {
            vertex.set(geometry[i], geometry[i + 1], geometry[i + 2], 1F);
            matrix.transform(vertex);

            builder.vertex(vertex.x, vertex.y, vertex.z).color(r, g, b, a);
        }
    }

    /**
     * Write the cached sphere at the given model-view — used to re-draw it into the hover
     * highlight at the exact footprint it was drawn at in the viewport.
     */
    public void writeSphere(BufferBuilder builder, Matrix4f modelView, float r, float g, float b, float a)
    {
        this.update();

        this.emit(builder, this.sphereGeometry, modelView, r, g, b, a);
    }

    /**
     * Writes a rotation ring with its far half (behind the central sphere) culled, so it reads
     * like the rings in a typical 3D gizmo. The tessellated arc is cached per axis and only
     * re-tessellated when the camera actually changes what is visible.
     *
     * <p>{@code a} used to arrive as the caller's {@code RenderSystem} shader colour, which carried
     * the pass's opacity while the ring supplied only its hue; with the shader colour gone it is an
     * argument like the rest.</p>
     */
    public void writeOccluded(BufferBuilder builder, MatrixStack stack, Axis axis, float radius, float thickness, float r, float g, float b, float a)
    {
        this.update();

        Vector2f arc = this.arcScratch;

        if (!this.visibleArc(stack, axis, arc))
        {
            return;
        }

        ArcSlot slot = this.arcSlots[axis.ordinal()];

        if (slot.geometry == null
            || Float.compare(slot.radius, radius) != 0
            || Float.compare(slot.thickness, thickness) != 0
            || Float.compare(slot.start, arc.x) != 0
            || Float.compare(slot.sweep, arc.y) != 0)
        {
            /* Tessellated in the ring's own frame (a Y-axis torus); the axis turn is applied
             * to the emit matrix below, so all three axes share one shape family. */
            BufferBuilder scratch = tessellate();

            Draw.arc3D(scratch, IDENTITY, Axis.Y, radius, thickness, 1F, 1F, 1F, arc.x, arc.y);

            slot.geometry = capture(scratch);
            slot.radius = radius;
            slot.thickness = thickness;
            slot.start = arc.x;
            slot.sweep = arc.y;
        }

        Matrix4f matrix = new Matrix4f(stack.peek().getPositionMatrix());

        if (axis == Axis.X) matrix.rotateZ(MathUtils.PI / 2F);
        else if (axis == Axis.Z) matrix.rotateX(MathUtils.PI / 2F);

        this.emit(builder, slot.geometry, matrix, r, g, b, a);
    }

    /** A shared identity stack for tessellating cached geometry in local space. */
    private static final MatrixStack IDENTITY = new MatrixStack();

    /** Writes the cached ring turned to face the camera — the view (screen-space) rotation ring. */
    public void writeBillboard(BufferBuilder builder, MatrixStack stack, float r, float g, float b, float a)
    {
        this.update();

        stack.push();

        Matrix4f matrix = stack.peek().getPositionMatrix();
        Vector3f toCamera = matrix.getTranslation(new Vector3f()).negate();
        Matrix3f basis = matrix.get3x3(new Matrix3f());

        if (Math.abs(basis.determinant()) > 1.0E-8F)
        {
            basis.invert().transform(toCamera);
        }

        if (toCamera.lengthSquared() > 1.0E-8F)
        {
            toCamera.normalize();
            stack.multiply(new Quaternionf().rotationTo(0F, 1F, 0F, toCamera.x, toCamera.y, toCamera.z));
        }

        stack.scale(VIEW_RING_SCALE, VIEW_RING_SCALE, VIEW_RING_SCALE);

        this.emit(builder, this.ringGeometry, stack.peek().getPositionMatrix(), r, g, b, a);

        stack.pop();
    }

    /**
     * Computes a rotation ring's camera-facing arc — the part not hidden behind the central
     * sphere — as {@code [startDeg, sweepDeg]} in the ring's own plane (the angle convention
     * {@link Draw#arc3D} draws in). A ring seen face-on returns the full {@code 360}; an
     * edge-on ring returns roughly half. Writes the result into {@code out}; returns
     * {@code false} only in the degenerate case where the whole ring is hidden.
     */
    private boolean visibleArc(MatrixStack stack, Axis axis, Vector2f out)
    {
        Matrix4f matrix = stack.peek().getPositionMatrix();

        /* Camera position expressed in the gizmo's local frame (the inverse of
         * the model-view applied to the view-space origin), as the billboard
         * ring already does. */
        Vector3f camera = matrix.getTranslation(new Vector3f()).negate();
        Matrix3f basis = matrix.get3x3(new Matrix3f());

        if (Math.abs(basis.determinant()) > 1.0E-8F)
        {
            basis.invert().transform(camera);
        }

        /* Move it into the ring's own plane frame, matching the axis rotation
         * arc3D applies, so the arc angles line up with what it draws. */
        Quaternionf rot = new Quaternionf();

        if (axis == Axis.X) rot.rotationZ(MathUtils.PI / 2F);
        else if (axis == Axis.Z) rot.rotationX(MathUtils.PI / 2F);

        rot.conjugate().transform(camera);

        /* A ring point (unit direction in the ring's plane) is on the near side
         * of the sphere when its in-plane dot with the camera is positive; the
         * cut then lands exactly on the sphere's silhouette. The out-of-plane
         * bias lifts that cut just enough that a ring viewed face-on — where the
         * in-plane dot is ~0 all the way round — stays fully drawn. */
        float length = camera.length();
        float bias = length > 1.0E-6F ? RING_FACE_ON_BIAS * (camera.y * camera.y) / length : 0F;
        int n = RING_OCCLUSION_SAMPLES;
        boolean[] visible = this.occlusionScratch;
        int count = 0;

        for (int i = 0; i < n; i++)
        {
            float angle = (float) (i * 2D * Math.PI / n);
            float ct = (float) Math.cos(angle);
            float st = (float) Math.sin(angle);
            boolean vis = camera.x * ct + camera.z * st + bias > 0F;

            visible[i] = vis;

            if (vis) count++;
        }

        if (count == 0)
        {
            return false;
        }

        if (count == n)
        {
            out.set(0F, 360F);

            return true;
        }

        /* The visible region is one contiguous arc; find where it begins after a
         * hidden sample and how far it runs, wrapping around. */
        int hidden = 0;

        while (visible[hidden]) hidden++;

        int start = hidden;

        while (!visible[start % n]) start++;

        int run = 0;

        while (visible[(start + run) % n]) run++;

        float step = 360F / n;

        out.set(start * step, run * step);

        return true;
    }
}
