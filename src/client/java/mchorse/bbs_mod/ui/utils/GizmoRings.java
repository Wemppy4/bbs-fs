package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * The round parts of the gizmo: the three rotation rings, the view ring that always faces
 * the camera, and the sphere they sit around. Split out of {@link Gizmo} because their
 * geometry answers to something else entirely — the axes scale and thickness settings —
 * and is therefore cached rather than rebuilt, while everything else in the gizmo is drawn
 * from the current frame's state.
 *
 * <p>One instance per gizmo; it owns the buffers and rebuilds them when the settings move.
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

    private VertexBuffer ringVbo;
    private VertexBuffer sphereVbo;

    private float lastScale = -1F;
    private float lastThickness = -1F;

    /* Cached tessellation of each rotation ring's visible arc, one slot per axis. A ring's
     * geometry is a pure function of (radius, thickness, arc) — the arc only moves with the
     * camera, so on a still viewport the slot never rebuilds, and the same buffer serves the
     * depth pass, the colour pass and the stencil pass of the frame (colour arrives through
     * the shader colour, not the vertices). This replaced re-tessellating a 96x24 torus in
     * immediate mode NINE times per frame — the single biggest FPS cost of the editor. */
    private final ArcSlot[] arcSlots = {new ArcSlot(), new ArcSlot(), new ArcSlot()};

    private final Vector2f arcScratch = new Vector2f();
    private final boolean[] occlusionScratch = new boolean[RING_OCCLUSION_SAMPLES];

    private static class ArcSlot
    {
        VertexBuffer vbo;
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

        if (this.ringVbo == null || scale != this.lastScale || thickness != this.lastThickness)
        {
            if (this.ringVbo != null)
            {
                this.ringVbo.close();
                this.sphereVbo.close();
            }

            this.ringVbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            this.sphereVbo = new VertexBuffer(VertexBuffer.Usage.STATIC);

            BufferBuilder builder = Tessellator.getInstance().getBuffer();

            float radius = 0.22F * scale;
            float thicknessRing = 0.02F * scale * thickness;

            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            Draw.arc3D(builder, new MatrixStack(), Axis.Y, radius, thicknessRing, 1F, 1F, 1F, 0F, 360F);
            this.ringVbo.bind();
            this.ringVbo.upload(builder.end());

            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            Draw.sphere(builder, new MatrixStack(), radius, 24, 24, 1F, 1F, 1F, 1F);
            this.sphereVbo.bind();
            this.sphereVbo.upload(builder.end());

            VertexBuffer.unbind();

            this.lastScale = scale;
            this.lastThickness = thickness;
        }
    }

    /**
     * Draws the cached sphere straight with the given model-view — used to re-draw it into
     * the hover mask at the exact footprint it was drawn at in the viewport.
     */
    public void drawSphere(Matrix4f modelView, Matrix4f projection)
    {
        this.update();

        this.sphereVbo.bind();
        this.sphereVbo.draw(modelView, projection, GameRenderer.getPositionColorProgram());
        VertexBuffer.unbind();
    }

    /**
     * Draws a rotation ring with its far half (behind the central sphere) culled, so it reads
     * like the rings in a typical 3D gizmo. The tessellated arc is cached per axis and only
     * rebuilt when the camera actually changes what is visible; colour is modulated through
     * the shader colour, so one buffer serves the depth, colour and stencil passes alike.
     */
    public void drawOccluded(MatrixStack stack, Axis axis, float radius, float thickness, float r, float g, float b)
    {
        this.update();

        Vector2f arc = this.arcScratch;

        if (!this.visibleArc(stack, axis, arc))
        {
            return;
        }

        ArcSlot slot = this.arcSlots[axis.ordinal()];

        if (slot.vbo == null
            || Float.compare(slot.radius, radius) != 0
            || Float.compare(slot.thickness, thickness) != 0
            || Float.compare(slot.start, arc.x) != 0
            || Float.compare(slot.sweep, arc.y) != 0)
        {
            if (slot.vbo == null)
            {
                slot.vbo = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            }

            BufferBuilder builder = Tessellator.getInstance().getBuffer();

            /* Tessellated in the ring's own frame (a Y-axis torus); the axis turn is applied
             * to the draw matrix below, so all three axes share one shape family. */
            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            Draw.arc3D(builder, IDENTITY, Axis.Y, radius, thickness, 1F, 1F, 1F, arc.x, arc.y);
            slot.vbo.bind();
            slot.vbo.upload(builder.end());
            VertexBuffer.unbind();

            slot.radius = radius;
            slot.thickness = thickness;
            slot.start = arc.x;
            slot.sweep = arc.y;
        }

        Matrix4f matrix = new Matrix4f(stack.peek().getPositionMatrix());

        if (axis == Axis.X) matrix.rotateZ(MathUtils.PI / 2F);
        else if (axis == Axis.Z) matrix.rotateX(MathUtils.PI / 2F);

        /* The caller's shader colour carries the pass's opacity (or nothing, in the depth and
         * stencil passes); fold the ring's own colour in and put things back afterwards. */
        float[] shaderColor = RenderSystem.getShaderColor();
        float pr = shaderColor[0];
        float pg = shaderColor[1];
        float pb = shaderColor[2];
        float pa = shaderColor[3];

        RenderSystem.setShaderColor(r, g, b, pa);
        slot.vbo.bind();
        slot.vbo.draw(matrix, RenderSystem.getProjectionMatrix(), GameRenderer.getPositionColorProgram());
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(pr, pg, pb, pa);
    }

    /** A shared identity stack for tessellating cached geometry in local space. */
    private static final MatrixStack IDENTITY = new MatrixStack();

    /** Draws the cached ring turned to face the camera — the view (screen-space) rotation ring. */
    public void drawBillboard(MatrixStack stack, float r, float g, float b, float a)
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

        RenderSystem.setShaderColor(r, g, b, a);
        this.ringVbo.bind();
        this.ringVbo.draw(stack.peek().getPositionMatrix(), RenderSystem.getProjectionMatrix(), GameRenderer.getPositionColorProgram());
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

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
