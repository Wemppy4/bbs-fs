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

    /** Angular length of the pointed ends where a ring disappears behind the sphere. */
    private final static float RING_TAPER_DEGREES = 22F;

    /** Sides of the move arrows' cone. */
    private final static int CONE_SEGMENTS = 16;

    private VertexBuffer ringVbo;
    private VertexBuffer sphereVbo;

    /* The arrow tips and the centre handle: unit or size-keyed meshes drawn by matrix and
     * shader colour, like the rings, so no pass tessellates them per frame. */
    private VertexBuffer coneVbo;
    private final TorusSlot centreRing = new TorusSlot();
    private final TorusSlot centreDisc = new TorusSlot();

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

        this.visibleArc(stack, axis, arc);

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
            Draw.arc3D(builder, IDENTITY, Axis.Y, radius, thickness, 1F, 1F, 1F, arc.x, arc.y,
                64, 12, arc.y < 360F ? RING_TAPER_DEGREES : 0F);
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
        this.drawFacing(stack, this.ringVbo, VIEW_RING_SCALE, r, g, b, a);
    }

    /**
     * The move arrow's tip: a cone along {@code axis} (towards {@code sign}), its base {@code base}
     * from the origin. One unit cone serves every arrow of every pass, stretched by the draw matrix,
     * so the arrows cost three buffer draws a pass and are never re-tessellated.
     */
    public void drawCone(MatrixStack stack, Axis axis, float sign, float base, float length, float radius, float r, float g, float b, float a)
    {
        if (this.coneVbo == null)
        {
            this.coneVbo = new VertexBuffer(VertexBuffer.Usage.STATIC);

            BufferBuilder builder = Tessellator.getInstance().getBuffer();

            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            buildUnitCone(builder);
            this.coneVbo.bind();
            this.coneVbo.upload(builder.end());
            VertexBuffer.unbind();
        }

        Vector3f direction = new Vector3f(axis == Axis.X ? sign : 0F, axis == Axis.Y ? sign : 0F, axis == Axis.Z ? sign : 0F);
        Matrix4f matrix = new Matrix4f(stack.peek().getPositionMatrix())
            .translate(direction.x * base, direction.y * base, direction.z * base)
            .rotate(new Quaternionf().rotationTo(0F, 1F, 0F, direction.x, direction.y, direction.z))
            .scale(radius, length, radius);

        this.drawTinted(this.coneVbo, matrix, r, g, b, a);
    }

    /**
     * The centre handle: a camera-facing circle {@code thickness} wide, or, for the pick pass, the
     * whole disc it encloses, so a click inside the circle grabs it too. Cached until its size changes.
     */
    public void drawCentre(MatrixStack stack, float radius, float thickness, boolean filled, float r, float g, float b, float a)
    {
        VertexBuffer vbo;

        if (filled)
        {
            float outer = radius + thickness * 0.5F;

            /* A torus whose tube reaches its own axis is a flat disc seen face-on. */
            vbo = this.centreDisc.get(outer * 0.5F, outer);
        }
        else
        {
            vbo = this.centreRing.get(radius, thickness);
        }

        this.drawFacing(stack, vbo, 1F, r, g, b, a);
    }

    /** Draws a Y-axis mesh turned to face the camera, {@code scale} times its size. */
    private void drawFacing(MatrixStack stack, VertexBuffer vbo, float scale, float r, float g, float b, float a)
    {
        stack.push();

        Matrix4f matrix = stack.peek().getPositionMatrix();
        Vector3f toCamera = matrix.getTranslation(new Vector3f()).negate();
        /* Screen-sized handles have a tiny world scale close to the camera.
         * Test/invert relative to that scale, not an absolute determinant. */
        Matrix3f inverse = GizmoJacobian.inverse(matrix.get3x3(new Matrix3f()));

        if (inverse.isFinite() && inverse.determinant() != 0F)
        {
            inverse.transform(toCamera);
        }

        if (toCamera.lengthSquared() > 1.0E-8F)
        {
            toCamera.normalize();
            stack.multiply(new Quaternionf().rotationTo(0F, 1F, 0F, toCamera.x, toCamera.y, toCamera.z));
        }

        stack.scale(scale, scale, scale);

        this.drawTinted(vbo, stack.peek().getPositionMatrix(), r, g, b, a);

        stack.pop();
    }

    /** Draws a white mesh in the given colour (alpha included), then puts the caller's shader colour back. */
    private void drawTinted(VertexBuffer vbo, Matrix4f matrix, float r, float g, float b, float a)
    {
        float[] shaderColor = RenderSystem.getShaderColor();
        float pr = shaderColor[0];
        float pg = shaderColor[1];
        float pb = shaderColor[2];
        float pa = shaderColor[3];

        RenderSystem.setShaderColor(r, g, b, a);
        vbo.bind();
        vbo.draw(matrix, RenderSystem.getProjectionMatrix(), GameRenderer.getPositionColorProgram());
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(pr, pg, pb, pa);
    }

    /** A cone of radius 1 and height 1 standing on the origin along +Y, wound outwards like {@link Draw#fillBox}. */
    private static void buildUnitCone(BufferBuilder builder)
    {
        for (int i = 0; i < CONE_SEGMENTS; i++)
        {
            double a1 = Math.PI * 2D * i / CONE_SEGMENTS;
            double a2 = Math.PI * 2D * (i + 1) / CONE_SEGMENTS;
            float x1 = (float) Math.cos(a1);
            float z1 = (float) Math.sin(a1);
            float x2 = (float) Math.cos(a2);
            float z2 = (float) Math.sin(a2);

            builder.vertex(0F, 1F, 0F).color(1F, 1F, 1F, 1F).next();
            builder.vertex(x2, 0F, z2).color(1F, 1F, 1F, 1F).next();
            builder.vertex(x1, 0F, z1).color(1F, 1F, 1F, 1F).next();

            builder.vertex(0F, 0F, 0F).color(1F, 1F, 1F, 1F).next();
            builder.vertex(x1, 0F, z1).color(1F, 1F, 1F, 1F).next();
            builder.vertex(x2, 0F, z2).color(1F, 1F, 1F, 1F).next();
        }
    }

    /** A camera-facing torus cached by its size: rebuilt only when the size asked for changes. */
    private static class TorusSlot
    {
        private VertexBuffer vbo;
        private float radius = -1F;
        private float thickness;

        private VertexBuffer get(float radius, float thickness)
        {
            if (this.vbo == null || Float.compare(this.radius, radius) != 0 || Float.compare(this.thickness, thickness) != 0)
            {
                if (this.vbo == null)
                {
                    this.vbo = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
                }

                BufferBuilder builder = Tessellator.getInstance().getBuffer();

                builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
                Draw.arc3D(builder, IDENTITY, Axis.Y, radius, thickness, 1F, 1F, 1F, 0F, 360F, 32, 8);
                this.vbo.bind();
                this.vbo.upload(builder.end());
                VertexBuffer.unbind();

                this.radius = radius;
                this.thickness = thickness;
            }

            return this.vbo;
        }
    }

    /**
     * Computes a rotation ring's camera-facing arc — the part not hidden behind the central
     * sphere — as {@code [startDeg, sweepDeg]} in the ring's own plane (the angle convention
     * {@link Draw#arc3D} draws in). A ring seen face-on returns the full {@code 360}; an
     * edge-on ring returns roughly half. Writes the result into {@code out}.
     */
    private void visibleArc(MatrixStack stack, Axis axis, Vector2f out)
    {
        Matrix4f matrix = stack.peek().getPositionMatrix();

        /* Camera position expressed in the gizmo's local frame (the inverse of
         * the model-view applied to the view-space origin), as the billboard
         * ring already does. */
        Vector3f camera = matrix.getTranslation(new Vector3f()).negate();
        Matrix3f inverse = GizmoJacobian.inverse(matrix.get3x3(new Matrix3f()));

        if (inverse.isFinite() && inverse.determinant() != 0F)
        {
            inverse.transform(camera);
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
        float inPlane = (float) Math.hypot(camera.x, camera.z);

        if (inPlane <= bias || length <= 1.0E-6F)
        {
            out.set(0F, 360F);

            return;
        }

        /* Solve the silhouette crossing directly so the pointed ends move smoothly
         * with the camera instead of jumping between four-degree samples. */
        double center = Math.atan2(camera.z, camera.x);
        double halfSweep = Math.acos(-bias / inPlane);
        float start = (float) Math.toDegrees(center - halfSweep);

        out.set((start % 360F + 360F) % 360F, (float) Math.toDegrees(2D * halfSweep));
    }
}
