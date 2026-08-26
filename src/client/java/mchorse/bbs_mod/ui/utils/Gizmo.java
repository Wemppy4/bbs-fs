package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.client.render.OffscreenTarget;
import net.minecraft.client.gl.MappableRingBuffer;

import java.util.OptionalInt;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.render.picker.BBSPickerRenderer;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.drag.DragStrategy;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class Gizmo
{
    /* Every pickable gizmo handle owns a distinct stencil id so the combined
     * mode can show move/scale/rotate at once and a pick unambiguously names
     * both the operation and the axis. {@link Handle} ties these together;
     * single-operation modes simply render a subset of them. {@link #STENCIL_MAX}
     * stays the highest id so form parts (which begin right after it) never
     * collide with a handle. */
    public final static int STENCIL_X = 1;
    public final static int STENCIL_Y = 2;
    public final static int STENCIL_Z = 3;
    public final static int STENCIL_XZ = 4;
    public final static int STENCIL_XY = 5;
    public final static int STENCIL_ZY = 6;
    public final static int STENCIL_SCALE_X = 7;
    public final static int STENCIL_SCALE_Y = 8;
    public final static int STENCIL_SCALE_Z = 9;
    public final static int STENCIL_SCALE_XZ = 10;
    public final static int STENCIL_SCALE_XY = 11;
    public final static int STENCIL_SCALE_ZY = 12;
    public final static int STENCIL_ROTATE_X = 13;
    public final static int STENCIL_ROTATE_Y = 14;
    public final static int STENCIL_ROTATE_Z = 15;
    public final static int STENCIL_TRACKBALL = 16;
    public final static int STENCIL_VIEW = 17;
    /** Screen-space translate handle: the big centre cube that grabs in the view plane. */
    public final static int STENCIL_SCREEN = 18;
    /** Uniform-scale handle: the centre cube in scale mode that scales all three axes at once. */
    public final static int STENCIL_SCALE_ALL = 19;

    /** Highest gizmo handle id; form-part stencil ids begin right after it. */
    public final static int STENCIL_MAX = STENCIL_SCALE_ALL;

    /** Radius of the view-plane ring relative to the per-axis rings. */
    private final static float VIEW_RING_SCALE = 1.2F;

    /** Move/scale handles shrink inside the rotation rings in combined mode. */
    private final static float COMBINED_INNER_SCALE = 0.6F;

    /** How much a ring is allowed to reach past the sphere's silhouette so a
     *  ring seen face-on still draws in full. {@code 0} would cut every ring
     *  dead on the silhouette (a face-on ring, sitting exactly on it, would
     *  flicker to half); a small value keeps face-on rings whole while a tilted
     *  ring's far half still ends right at the silhouette. */
    private final static float RING_FACE_ON_BIAS = 0.18F;

    /** Angular resolution used to find a ring's camera-facing (visible) arc. */
    private final static int RING_OCCLUSION_SAMPLES = 180;

    /** Half-size of the scale handle's end cube, in gizmo-local units (× axes scale × thickness).
     *  Based on scale/thickness rather than the per-pass line offset, so the cube is the same
     *  size in the visual and stencil passes and its hitbox matches the drawn cube exactly. */
    private final static float SCALE_CUBE_HALF = 0.032F;

    /** Half-size of the centre cube shared by the screen-space (view-plane) translate
     *  handle and the uniform (three-axis) scale handle, in gizmo-local units
     *  (× axes scale × thickness). Deliberately large so the centre reads as an easy
     *  grab target. Like {@link #SCALE_CUBE_HALF} it is offset-independent so the visual
     *  and stencil passes match and the hitbox lines up with the drawn cube. */
    private final static float SCREEN_CUBE_HALF = 0.03F;

    /* POSITION_COLOR / TRIANGLES, no depth test — the gizmo handles, rings, sphere, infinite line and
     * rotate-pie were all originally drawn under RenderSystem.depthFunc(GL_ALWAYS) so they read on top of
     * the model. The 1.21.5 GPU rewrite removed RenderSystem.setShader / GameRenderer.getPositionColorProgram
     * / BufferRenderer.drawWithGlobalProgram / VertexBuffer, so geometry is now built into a BufferBuilder
     * and submitted through this RenderLayer (same approach as mchorse.bbs_mod.graphics.Draw, whose public
     * fillBox/arc3D/sphere builders this class reuses). Self-contained here to keep the fix isolated. */
    private static final RenderPipeline GIZMO_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/gizmo_position_color_no_depth"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    private static RenderLayer gizmoLayer;

    /* ---- Interface (UI) pass state ----
     * While set, the gizmo is being drawn from renderInterface/renderStencilInterface: geometry is
     * built from the CAPTURED full model-view (lastRenderMatrix seeds the stack), so the flushes must
     * apply an IDENTITY model-view and the interface projection instead of the world pass' globals,
     * and the visual flush routes into the off-screen interface target rather than the world layer. */
    private static boolean interfacePass;
    private static GpuTextureView interfaceTarget;
    private static Matrix4f interfaceProjection;
    private static boolean interfaceDrew;
    private static MappableRingBuffer interfaceProjectionRing;

    /** Off-screen colour the interface-pass visual renders into, blitted premultiplied over the viewport. */
    private final OffscreenTarget interfaceBuffer = new OffscreenTarget("bbs_gizmo_interface");

    public final static Gizmo INSTANCE = new Gizmo();

    private Mode mode = Mode.COMBINED;
    /** The mode to return to when combined mode is toggled off. */
    private Mode previousMode = Mode.TRANSLATE;

    private int index;
    private int mouseX;
    private int mouseY;

    private UIPropTransform currentTransform;

    /* Snapshot of the matrix stack at the moment the gizmo is rendered.
     * Combined with a camera (whose view matrix matches the one applied to
     * the stack during rendering) this lets us recover the gizmo's true
     * world position without having to thread it through every call site. */
    private final Matrix4f lastRenderMatrix = new Matrix4f();
    private boolean hasLastRenderMatrix;

    /* While an axis ring is dragged the whole gizmo is drawn from the
     * orientation captured at the gesture's first draw, so the ring stays put
     * (only the pie sweeps) instead of writhing as the live rotation is
     * recomposed from euler angles each frame — most visible in local/world
     * space. Keyed to the gesture itself so it can never be forgotten by an
     * edit entry point and re-freezes when the axis is switched mid-edit. */
    private final Matrix4f bakedRotationMatrix = new Matrix4f();
    private DragStrategy bakedGesture;

    /** The frame the handles were last placed in ({@link #reorientForSpace}), or
     *  {@code null} when the placement was left untouched. Only the draw passes
     *  read it, to flatten {@link TransformSpace#VIEW} onto the eye ray
     *  ({@link #applyViewShear}); the drag math takes its frames from
     *  {@link GizmoDrag} as before. */
    private TransformSpace lastSpace;

    /* 1.21.11: the 1.21.1 ring/sphere VertexBuffer cache is gone — VertexBuffer was removed by the
     * GPU-pipeline rewrite, so the rings are rebuilt immediate-mode each frame through the gizmo
     * RenderLayer instead (same geometry, same parameters). The lastScale/lastThickness fields that
     * kept that cache honest went with it. */

    /** World-space radius the sphere is drawn at, expressed in
     *  the local coordinate frame {@link #lastRenderMatrix} describes
     *  (i.e. already includes axesScale and the per-frame distanceScale).
     *  Captured in {@link #render} so {@link #computeScreenRadius} can
     *  project an edge point and report the sphere's real pixel size. */
    private float lastSphereLocalRadius;

    /** Model-view the sphere is actually drawn with this frame (origin frame
     *  times the per-frame distanceScale). Reused by {@link #renderSphereHighlight}
     *  to re-draw the sphere into a mask at the exact same on-screen footprint. */
    private final Matrix4f lastSphereMatrix = new Matrix4f();
    private boolean hasLastSphereMatrix;

    /** Sphere-only mask the hover highlight is composited from. The sphere is
     *  kept out of the pick stencil (one pixel can't be both "bone" and "sphere",
     *  and the deferred bone-vs-sphere pick needs both), so its highlight gets a
     *  private buffer it can own outright. */
    private final StencilFormFramebuffer sphereHighlight = new StencilFormFramebuffer();

    /** Driven by {@link GizmoInteraction}'s per-frame hover pass. When true the
     *  sphere highlight is composited over the viewport (the screen-space hover
     *  overlay, the same look bones/handles get from the pick stencil). */
    private boolean sphereHovered;

    /** Per-frame on-screen size compensation, {@code menu.height / viewportArea.h}.
     *  {@link #getAxesDistanceScale} otherwise keeps the gizmo a constant fraction
     *  of its viewport, so it shrinks in a small preview (the film) versus a
     *  full-screen editor (forms); this factor makes it a constant fraction of the
     *  window instead, i.e. the same on-screen size in every editor. Each viewport
     *  sets it via {@link #setViewportScale} before BOTH its visual and stencil
     *  pass so the drawn gizmo and its pick hitbox scale together. */
    private float viewportScale = 1F;

    private Gizmo()
    {}

    private static RenderLayer getGizmoLayer()
    {
        if (gizmoLayer == null)
        {
            gizmoLayer = RenderLayer.of(BBSMod.MOD_ID + "_gizmo_position_color",
                RenderSetup.builder(GIZMO_PIPELINE).translucent().build());
        }

        return gizmoLayer;
    }

    /**
     * The gizmo opacity setting, folded into the vertex alpha of every visual draw.
     *
     * <p>Until 1.21.5 the modulator rode on {@code RenderSystem.setShaderColor}, which
     * tinted the whole pass at once; the GPU-pipeline rewrite removed it, so each draw
     * now carries the factor itself. Only the visual passes take it — the pick stencil
     * writes IDs, not colour.
     */
    private static float opacity()
    {
        return BBSSettings.gizmoOpacity.get();
    }

    /**
     * {@link Draw#fillBox} with the gizmo opacity folded in. Mirrors the int overload's
     * "no alpha byte means opaque" convention, since the axis colours ({@link Colors#RED}
     * and friends) are stored without one.
     */
    private static void fillBox(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, int color)
    {
        float alpha = Colors.getA(color);

        if (alpha <= 0F)
        {
            alpha = 1F;
        }

        Draw.fillBox(builder, stack, x1, y1, z1, x2, y2, z2,
            Colors.getR(color), Colors.getG(color), Colors.getB(color), alpha * opacity());
    }

    /** Start a POSITION_COLOR / TRIANGLES buffer for the gizmo geometry. */
    private static BufferBuilder begin()
    {
        return Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
    }

    /** Finish a buffer and submit it through the always-on-top gizmo layer (no-op on an empty buffer). */
    private static void flush(BufferBuilder builder)
    {
        BuiltBuffer built = builder.endNullable();

        if (built == null)
        {
            return;
        }

        if (interfacePass)
        {
            drawInterface(built);
        }
        else
        {
            getGizmoLayer().draw(built);
        }
    }

    /**
     * The interface-pass flush: a manual render pass into the off-screen interface target with the
     * viewport's projection and an identity model-view (the captured full model-view is baked into
     * the vertices). The first flush of the pass clears the target to transparent; the translucent
     * blend against that produces premultiplied content, which the blit un-doubles (see
     * {@code texturedBoxPremultiplied}). Modeled on {@link BBSPickerRenderer#drawColorId}.
     */
    private static void drawInterface(BuiltBuffer buffer)
    {
        GpuDevice device = RenderSystem.getDevice();
        CommandEncoder encoder = device.createCommandEncoder();

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
            .write(new Matrix4f(), new Vector4f(1F, 1F, 1F, 1F), new Vector3f(), new Matrix4f());

        if (interfaceProjectionRing == null)
        {
            interfaceProjectionRing = new MappableRingBuffer(() -> "bbs:gizmo_interface_projection", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, 64);
        }

        interfaceProjectionRing.rotate();

        GpuBuffer projection = interfaceProjectionRing.getBlocking();

        try (GpuBuffer.MappedView view = encoder.mapBuffer(projection, false, true))
        {
            Std140Builder.intoBuffer(view.data()).putMat4f(interfaceProjection);
        }

        VertexFormat format = GIZMO_PIPELINE.getVertexFormat();
        GpuBuffer vertexBuffer = format.uploadImmediateVertexBuffer(buffer.getBuffer());
        RenderSystem.ShapeIndexBuffer sequential = RenderSystem.getSequentialBuffer(buffer.getDrawParameters().mode());
        GpuBuffer indexBuffer = sequential.getIndexBuffer(buffer.getDrawParameters().indexCount());
        VertexFormat.IndexType indexType = sequential.getIndexType();

        try (RenderPass pass = encoder.createRenderPass(() -> "bbs:gizmo_interface",
            interfaceTarget, interfaceDrew ? OptionalInt.empty() : OptionalInt.of(0x00000000)))
        {
            interfaceDrew = true;

            pass.setPipeline(GIZMO_PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("Projection", projection.slice(0L, 64));
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.setIndexBuffer(indexBuffer, indexType);
            pass.drawIndexed(0, 0, buffer.getDrawParameters().indexCount(), 1);
        }
        finally
        {
            buffer.close();
        }
    }

    /**
     * Finish a buffer and submit it into the off-screen picking target instead of the visible viewport (no-op on
     * an empty buffer). Used only by the stencil ({@link #renderStencil}) pass: the geometry carries each handle's
     * stencil id in its red channel, and {@link BBSPickerRenderer#drawColorId} routes it through a manual render
     * pass into {@link StencilFormFramebuffer}'s colour texture — the faithful 1.21.5+ replacement for the
     * original {@code getPositionColorProgram} + {@code BufferRenderer.drawWithGlobalProgram} stencil draw, which
     * can no longer reach the hand-bound FBO. The global model-view (the visible gizmo's {@link RenderLayer}
     * applies it; the per-vertex stack pose is already baked in) is handed through so the pick aligns 1:1 with
     * the visible handles.
     */
    private static void flushPick(BufferBuilder builder)
    {
        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            /* In the interface pass the captured full model-view is baked into the vertices, so the
             * pass applies identity; the world pass keeps handing the global through as before. */
            BBSPickerRenderer.drawColorId(GIZMO_PIPELINE, built, interfacePass ? new Matrix4f() : RenderSystem.getModelViewMatrix());
        }
    }

    /**
     * Reconstruct the world-space origin of the gizmo from the most recent
     * render matrix and the camera that drove that render. The stack at
     * render time is {@code view * translate(-cam.pos) * gizmoChain}, so
     * undoing the view rotation and adding camera position yields the real
     * world coordinates.
     */
    public boolean computeWorldOrigin(Camera camera, Vector3d out)
    {
        if (!this.hasLastRenderMatrix)
        {
            return false;
        }

        Matrix4f undoView = new Matrix4f(camera.view).invert().mul(this.lastRenderMatrix);
        Vector3f cameraRelative = undoView.getTranslation(new Vector3f());

        out.set(
            camera.position.x + cameraRelative.x,
            camera.position.y + cameraRelative.y,
            camera.position.z + cameraRelative.z
        );

        return true;
    }

    /**
     * Recover the gizmo's world-space axes from the latest render matrix and
     * camera. Columns of {@code out} become the unit-length world directions
     * of the gizmo's X/Y/Z handles. Returns {@code false} if the gizmo hasn't
     * been rendered yet, in which case the caller should skip ray-based
     * dragging.
     */
    public boolean computeWorldAxes(Camera camera, Matrix3f out)
    {
        if (!this.hasLastRenderMatrix)
        {
            return false;
        }

        Matrix4f undoView = new Matrix4f(camera.view).invert().mul(this.lastRenderMatrix);

        out.set(undoView.get3x3(new Matrix3f()));

        Vector3f col = new Vector3f();

        for (int i = 0; i < 3; i++)
        {
            out.getColumn(i, col);

            float lenSq = col.lengthSquared();

            if (lenSq < 1.0E-12F)
            {
                return false;
            }

            col.div((float) Math.sqrt(lenSq));
            out.setColumn(i, col);
        }

        return true;
    }

    public Mode getMode()
    {
        return this.mode;
    }

    /** The active drag's on-screen readout (angle / offset / scale delta), or
     *  {@code null} when nothing is being dragged. See
     *  {@link UIPropTransform#getDragReadout()}. */
    public String getDragReadout()
    {
        return this.currentTransform == null ? null : this.currentTransform.getDragReadout();
    }


    public void setSphereHovered(boolean hovered)
    {
        this.sphereHovered = hovered;
    }

    /**
     * Set this frame's on-screen size compensation ({@code menu.height /
     * viewportArea.h}). Call before the visual and stencil pass of the gizmo's
     * viewport, with the same value for both, so the drawn gizmo and its pick
     * hitbox stay the same constant on-screen size across editors.
     */
    public void setViewportScale(float viewportScale)
    {
        this.viewportScale = viewportScale > 0F && Float.isFinite(viewportScale) ? viewportScale : 1F;
    }

    /** The trackball sphere shows in the dedicated rotate mode and in combined. */
    public boolean hasSphere()
    {
        return this.mode == Mode.ROTATE || this.mode == Mode.COMBINED;
    }

    public boolean isSphereInteractive()
    {
        if (!BBSSettings.gizmos.get() || !BBSSettings.rotate3dSphere.get())
        {
            return false;
        }

        if (!this.hasSphere())
        {
            return false;
        }

        if (this.currentTransform != null && this.currentTransform.isEditing() && !this.currentTransform.isSphereRotate())
        {
            return false;
        }

        return true;
    }

    public boolean isSphereDragging()
    {
        return this.currentTransform != null && this.currentTransform.isEditing() && this.currentTransform.isSphereRotate();
    }

    /** World-space radius the rotate sphere was last drawn at ({@code 0} until rendered). */
    public float getSphereWorldRadius()
    {
        return this.hasLastRenderMatrix ? this.lastSphereLocalRadius : 0F;
    }

    /**
     * Project the gizmo's origin onto the viewport in pixel space and
     * write the result into {@code out}. Returns {@code false} when the
     * gizmo hasn't been rendered yet or the origin sits behind the
     * camera ({@code clip.w <= 0}) — caller should skip the hover check.
     *
     * <p>{@link #lastRenderMatrix} already encodes
     * {@code view * translate(-cam) * gizmoChain}, so left-multiplying
     * by the projection matrix yields clip space directly. NDC → pixel
     * mapping then accounts for the inverted Y between OpenGL NDC
     * (Y up) and screen coordinates (Y down).
     */
    public boolean computeScreenCenter(Matrix4f projection, float areaX, float areaY, float areaW, float areaH, Vector2f out)
    {
        if (!this.hasLastRenderMatrix)
        {
            return false;
        }

        Matrix4f mvp = new Matrix4f(projection).mul(this.lastRenderMatrix);
        Vector4f clip = mvp.transform(new Vector4f(0F, 0F, 0F, 1F));

        if (clip.w <= 0F)
        {
            return false;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;

        out.x = areaX + (ndcX * 0.5F + 0.5F) * areaW;
        out.y = areaY + (1F - (ndcY * 0.5F + 0.5F)) * areaH;

        return true;
    }

    /**
     * Effective pixel radius of the rotate-mode sphere on screen, so the
     * hover/pick disc in {@link mchorse.bbs_mod.ui.film.controller.UIFilmController}
     * matches the sphere's actual visual size at the current camera
     * distance and axes scale.
     *
     * <p>Projects three local-axis edge points
     * ({@code (r,0,0)}, {@code (0,r,0)}, {@code (0,0,r)}) onto the
     * viewport and returns the largest pixel distance from the
     * projected centre — covers all camera orientations without
     * needing a true ellipse-from-sphere derivation. Returns {@code 0}
     * when the gizmo hasn't been rendered yet, the centre is behind
     * the camera, or the sphere radius hasn't been captured.
     */
    public float computeScreenRadius(Matrix4f projection, float areaX, float areaY, float areaW, float areaH)
    {
        if (!this.hasLastRenderMatrix || this.lastSphereLocalRadius <= 0F)
        {
            return 0F;
        }

        Vector2f center = new Vector2f();

        if (!this.computeScreenCenter(projection, areaX, areaY, areaW, areaH, center))
        {
            return 0F;
        }

        Matrix4f mvp = new Matrix4f(projection).mul(this.lastRenderMatrix);
        float r = this.lastSphereLocalRadius;
        float[] xs = {r, 0F, 0F};
        float[] ys = {0F, r, 0F};
        float[] zs = {0F, 0F, r};
        float maxSq = 0F;

        for (int i = 0; i < 3; i++)
        {
            Vector4f clip = mvp.transform(new Vector4f(xs[i], ys[i], zs[i], 1F));

            if (clip.w <= 0F) continue;

            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            float px = areaX + (ndcX * 0.5F + 0.5F) * areaW;
            float py = areaY + (1F - (ndcY * 0.5F + 0.5F)) * areaH;
            float dx = px - center.x;
            float dy = py - center.y;
            float d = dx * dx + dy * dy;

            if (d > maxSq) maxSq = d;
        }

        return (float) Math.sqrt(maxSq);
    }

    /**
     * Composite the trackball sphere's hover highlight over the viewport, the
     * same screen-space overlay bones and handles get from the pick stencil.
     *
     * <p>The sphere can't share the pick stencil (its pixels would erase the
     * bone ids the deferred bone-vs-sphere pick reads), so it gets a private
     * mask: re-draw the sphere — at the exact matrix it was rendered with this
     * frame ({@link #lastSphereMatrix}) and the viewport's projection — into
     * {@link #sphereHighlight} carrying {@link #STENCIL_TRACKBALL} as its id,
     * then run the picker-preview shader so only those pixels light up.
     *
     * <p>Called from each {@link GizmoViewport}'s GUI overlay pass via
     * {@link GizmoInteraction#renderSphereHighlight}; {@code projection}/{@code area}
     * are the same pair {@link #computeScreenCenter} uses, so the mask lands on
     * the sphere's footprint regardless of mask resolution.
     */
    public void renderSphereHighlight(UIContext context, Matrix4f projection, Area area)
    {
        if (!this.sphereHovered || !this.hasLastSphereMatrix || !this.hasSphere()
            || context == null || projection == null || area == null
            || !BBSSettings.gizmos.get() || BBSRendering.isIrisShadowPass())
        {
            return;
        }

        float scale = BBSModClient.getGUIScale();
        int w = Math.max(1, Math.round(area.w * scale));
        int h = Math.max(1, Math.round(area.h * scale));

        /* The sphere itself is invisible (1.21.1 removed its tint and left it as the trackball grab area),
         * so the hover feedback is this glow: the same sphere re-drawn at the model-view it was last drawn
         * with, into an off-screen target, then composited over the viewport through the recorded GUI path.
         * Drawing it straight onto the framebuffer would be overpainted by the deferred GUI flush. */
        int color = BBSSettings.stencilHighlightColor.get();
        MatrixStack stack = new MatrixStack();
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        /* Drawn with the SAME matrix/radius pair that defines the pick disc: computeScreenRadius projects
         * lastSphereLocalRadius through lastRenderMatrix. lastSphereMatrix would double-count the per-frame
         * distanceScale, which is already folded into lastSphereLocalRadius — the glow then came out larger
         * than the area that actually grabs, so the disc read as "too small for the sphere". */
        Draw.sphere(builder, stack, this.lastSphereLocalRadius, 24, 24,
            Colors.getR(color), Colors.getG(color), Colors.getB(color), Colors.getA(color));

        /* The gizmo owns a highlight target of its own (sphereHighlight): the bone highlight of whichever
         * viewport is hosting it renders into ITS target in the same frame, and both blits are recorded, so
         * sharing one texture would leave both showing the last write. */
        GpuTextureView target = this.sphereHighlight.ensureHighlightTarget(w, h);

        if (BBSPickerRenderer.drawGeometryHighlight(builder.endNullable(), target, this.lastRenderMatrix, projection))
        {
            int vw = this.sphereHighlight.getHighlightWidth();
            int vh = this.sphereHighlight.getHighlightHeight();

            context.batcher.texturedBox(this.sphereHighlight.getHighlightGlId(), Colors.WHITE,
                area.x, area.y, area.w, area.h, 0, vh, vw, 0, vw, vh);
        }
    }

    /**
     * Set the persistent gizmo mode. Returns {@code true} iff the mode
     * actually changed — callers (notably the tool-switch hotkey
     * helper) use this to distinguish a real switch from a no-op press
     * on the already-active tool.
     */
    public boolean setMode(Mode mode)
    {
        if (!BBSSettings.gizmos.get())
        {
            return false;
        }

        boolean same = this.mode == mode;

        this.mode = mode;

        return !same;
    }

    /**
     * Toggle the combined mode: entering it remembers the mode left behind so a
     * second press returns there. This is the only way out of combined, since
     * in that mode the G/S/R hotkeys run their operation without switching the
     * displayed handles.
     */
    public boolean toggleCombined()
    {
        if (this.mode == Mode.COMBINED)
        {
            return this.setMode(this.previousMode);
        }

        Mode previous = this.mode;

        if (this.setMode(Mode.COMBINED))
        {
            this.previousMode = previous;

            return true;
        }

        return false;
    }

    public boolean start(int index, int mouseX, int mouseY, UIPropTransform transform)
    {
        return this.start(index, mouseX, mouseY, transform, null);
    }

    public boolean start(int index, int mouseX, int mouseY, UIPropTransform transform, GizmoDrag drag)
    {
        if (!BBSSettings.gizmos.get())
        {
            return false;
        }

        Handle handle = Handle.byIndex(index);

        if (handle == null)
        {
            return false;
        }

        this.index = index;
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        this.currentTransform = transform;

        if (transform != null)
        {
            switch (handle.op)
            {
                case MOVE:
                case SCALE:
                case ROTATE:
                    transform.enableMode(handle.op.transformOp, handle.axis, handle.axis2, drag);
                    break;
                case SCALE_ALL:
                    transform.enableUniformScale(drag);
                    break;
                case SCREEN:
                    transform.enableScreenTranslate(drag);
                    break;
                case TRACKBALL:
                    if (BBSSettings.rotate3dSphere.get()) transform.enableSphereRotate(drag);
                    break;
                case VIEW:
                    transform.enableViewRotate(drag);
                    break;
            }
        }

        return true;
    }

    public void trackTransform(UIPropTransform transform)
    {
        this.currentTransform = transform;
    }

    public void clearTrackedTransform(UIPropTransform transform)
    {
        if (this.currentTransform == transform)
        {
            this.currentTransform = null;
            this.bakedGesture = null;

            if (this.index < STENCIL_X || this.index > STENCIL_MAX)
            {
                this.index = -1;
            }
        }
    }

    public void stop()
    {
        this.index = -1;

        if (this.currentTransform != null)
        {
            this.currentTransform.acceptChanges();
        }

        this.currentTransform = null;
    }

    public void render(MatrixStack stack)
    {
        if (BBSRendering.isIrisShadowPass())
        {
            return;
        }

        stack.push();
        MatrixStackUtils.scaleBack(stack);
        this.captureRenderMatrix(stack);
        this.drawGizmo(stack);
        stack.pop();
    }

    /**
     * Capture the gizmo's model-view for the deferred interface-pass visual
     * ({@link #renderInterface}) without drawing anything in the caller's world
     * / 3D pass. The visual moved out of the world pass so its translucent parts
     * (the rotation sphere, the sweep pie, the view ring) composite through the
     * UI pipeline instead of the world shaders, which did not blend them.
     */
    public void captureVisual(MatrixStack stack)
    {
        if (BBSRendering.isIrisShadowPass())
        {
            return;
        }

        stack.push();
        MatrixStackUtils.scaleBack(stack);
        this.captureRenderMatrix(stack);
        stack.pop();
    }

    /**
     * Draw the gizmo's visual over a {@link GizmoViewport} in the UI pass, from
     * the model-view captured this frame ({@link #lastRenderMatrix}, set by
     * {@link #captureVisual} or {@link #renderStencil}).
     *
     * <p>It draws straight onto the main framebuffer through the UI pipeline with
     * the GL viewport set to {@code area} — the same setup the form editor's
     * model pass uses ({@link mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer}).
     * This fixes the transparency the world shaders mangled (the whole point of
     * the move) and places the gizmo correctly: the film world is itself
     * rendered into that same {@code area}, and {@code projection} maps NDC onto
     * the area, so the gizmo lines up with the model and stays inside the
     * preview (the frustum clips it to the viewport rect). It is NOT rendered
     * to an off-screen buffer and blitted, the way the pick stencil and sphere
     * highlight are: those are opaque masks, but the rotation pie is translucent,
     * and an intermediate buffer applies its alpha twice (once on draw, once on
     * blit), leaving it nearly invisible.
     *
     * <p>The projection is applied before drawing because
     * {@link #getAxesDistanceScale} reads it back from {@link RenderSystem} to
     * keep the gizmo a constant on-screen size.
     */
    public void renderInterface(UIContext context, Matrix4f projection, Area area)
    {
        if (BBSRendering.isIrisShadowPass() || !this.hasLastRenderMatrix
            || context == null || projection == null || area == null || area.w <= 0 || area.h <= 0)
        {
            return;
        }

        /* The 1.21.5 rewrite removed the global projection/viewport swap the 1.21.1 UI pass rode
         * on, so the pass is rebuilt on manual render passes: the gizmo draws into an off-screen
         * target sized to the viewport (its own "GL viewport"), each flush binding the viewport's
         * projection, and the result is composited back through the RECORDED premultiplied blit —
         * an immediate draw onto the main framebuffer would be overpainted by the deferred GUI
         * (the film preview itself is a recorded element). Drawing from the UI pass rather than the
         * world pass is also what keeps the gizmo visible under a shaderpack: the pack's composite
         * overwrites world-phase draws that don't go through its programs, while manual passes at
         * UI time run after it — the same reason the stencil picking survived shaders all along. */
        MinecraftClient mc = MinecraftClient.getInstance();

        this.setViewportScale(context.menu.height / (float) area.h);

        double scaleFactor = BBSModClient.getGUIScale();
        int tw = Math.max(1, (int) (area.w * scaleFactor));
        int th = Math.max(1, (int) (area.h * scaleFactor));

        interfaceTarget = this.interfaceBuffer.ensure(tw, th);
        interfaceProjection = new Matrix4f(projection);
        interfacePass = true;
        interfaceDrew = false;

        try
        {
            MatrixStack stack = new MatrixStack();

            MatrixStackUtils.multiply(stack, this.lastRenderMatrix);
            this.drawGizmo(stack);
        }
        finally
        {
            interfacePass = false;
            interfaceTarget = null;
        }

        if (interfaceDrew)
        {
            context.batcher.texturedBoxPremultiplied(this.interfaceBuffer.getGlId(), Colors.WHITE,
                area.x, area.y, area.w, area.h, 0, th, tw, 0, tw, th);
        }
    }

    private void drawGizmo(MatrixStack stack)
    {
        this.applyBakedRotation(stack);

        float distanceScale = this.getAxesDistanceScale(stack);

        stack.push();
        this.applyViewShear(stack);
        stack.scale(distanceScale, distanceScale, distanceScale);

        if (BBSSettings.gizmos.get())
        {
            /* Cache the sphere's effective world radius (in
             * {@link #lastRenderMatrix}'s coordinate frame) so
             * {@link #computeScreenRadius} can report the real on-screen
             * pixel size for hover/pick distance checks. */
            this.lastSphereLocalRadius = 0.22F * BBSSettings.axesScale.get() * distanceScale;

            this.lastSphereMatrix.set(modelView(stack));
            this.hasLastSphereMatrix = true;
            this.drawOccludedGizmo(stack);
        }
        else
        {
            Draw.coolerAxes(stack, 0.25F, 0.008F);
        }

        stack.pop();

        /* Deliberately outside the shear: the constraint guide is a world-space line
         * showing the axis the drag actually slides along, and that axis comes from
         * {@link GizmoDrag#frameBasis} — the unsheared frame. */
        this.drawInfiniteLine(stack);
    }

    /**
     * Flatten the handles' third axis onto the eye ray while they are drawn in
     * {@link TransformSpace#VIEW}, so a screen-space tool reads as one wherever it
     * sits in the frame.
     *
     * <p>VIEW places the handles on the camera's own axes
     * ({@link GizmoDrag#stackBasisForSpace}), which makes them PARALLEL to the screen
     * but not FACING it: under perspective a gizmo away from the centre is seen a
     * little from the side. Measured at a 70&deg; FOV, the Z bar — a dot dead centre —
     * grows to about three quarters of a handle's length by the corner of the frame,
     * and the billboarded view ring goes a quarter oval and drifts off the origin,
     * while the axis rings beside it stay perfect circles. That mismatch is the whole
     * "not quite straight on" look.
     *
     * <p>Replacing the third column with the unit ray from the gizmo back to the eye
     * cancels exactly that, and nothing else: the Z bar collapses to a point at every
     * screen position, everything drawn in the screen plane (the rings, the billboard,
     * the plane quads) projects perfectly circular and concentric, and the X/Y bars
     * keep the exact horizontal/vertical they already had, since their columns are not
     * touched. At the centre the eye ray IS the camera's Z, so the frame is the
     * identity again and nothing jumps as the gizmo crosses the middle. The column
     * stays unit length, so {@link MatrixStackUtils#scaleBack} is unaffected, and the
     * determinant stays positive (~0.82 at the corner), so depth order and winding hold.
     *
     * <p>Only the DRAWING frame is sheared, and both draw passes take it, so the pick
     * stencil keeps matching the visual pixel for pixel. {@link #lastRenderMatrix} is
     * captured before this runs, so the gizmo's world axes, the drag frames and the
     * pick projections all keep the orthonormal camera basis they had.
     */
    private void applyViewShear(MatrixStack stack)
    {
        if (this.lastSpace != TransformSpace.VIEW)
        {
            return;
        }

        Matrix4f matrix = stack.peek().getPositionMatrix();
        Vector3f toCamera = matrix.getTranslation(new Vector3f()).negate();

        if (toCamera.lengthSquared() < 1.0E-8F)
        {
            return;
        }

        toCamera.normalize();

        matrix.m20(toCamera.x);
        matrix.m21(toCamera.y);
        matrix.m22(toCamera.z);
    }

    /**
     * Draw the opaque gizmo handles with real depth so nearer parts hide farther
     * ones — the solid look a typical 3D gizmo has, instead of every bar, plane
     * and ring bleeding over each other flat.
     *
     * <p>The gizmo still sits on top of the scene: a first depth-only pass stamps
     * every handle pixel to the far plane (via {@code depthRange(1,1)}), so the
     * model's own depth can't occlude the gizmo; the real pass then draws the
     * handles against that clean slate with an ordinary depth test, sorting them
     * among themselves. The translucent sweep pie stays on top of everything and
     * writes no depth, so it can't punch holes in the handles.
     */
    private void drawOccludedGizmo(MatrixStack stack)
    {
        /* TODO(1.21.11 render): the 1.21.1 depth-sorted handle pass (a depth-only prime at the far
         * plane, then a LEQUAL pass so nearer bars/planes/rings hide farther ones) was driven purely
         * by RenderSystem depthMask/depthFunc/colorMask/depthRange + setShaderColor, all removed by
         * the GPU-pipeline rewrite — that state now belongs to the RenderPipeline. The port's gizmo
         * pipeline is NO_DEPTH (the original depthFunc(GL_ALWAYS) behaviour), so the handles draw
         * flat-on-top as they always have on this branch. Restoring the sorted look means a second,
         * depth-tested gizmo pipeline plus a depth-prime variant. The opacity modulator
         * (BBSSettings.gizmoOpacity) also rode on setShaderColor; it is carried per-draw now, see
         * {@link #opacity()}. */
        this.drawAxes(stack, 0.25F, 0.008F);
        this.drawRotatePieIfActive(stack);
    }

    /**
     * Draw the rotation sweep pie when an axis ring is being dragged. Split out
     * of the handle pass so it can be composited last, on top of and without
     * disturbing the depth-sorted handles.
     */
    private void drawRotatePieIfActive(MatrixStack stack)
    {
        UIPropTransform transform = this.currentTransform;

        if (transform == null || !transform.isEditing() || transform.getOp() != TransformOp.ROTATE)
        {
            return;
        }

        if (transform.isSphereRotate())
        {
            return;
        }

        if (transform.isViewRotate())
        {
            this.drawViewPie(stack);

            return;
        }

        Axis axis = transform.getAxis();

        if (axis != null)
        {
            this.drawRotatePie(stack, axis);
        }
    }

    /**
     * Sweep pie for the view (screen-plane) ring. Built straight from the cursor's
     * screen angles using the gizmo's local directions that map to screen right and
     * down, so it starts exactly under the grab, its leading edge follows the cursor,
     * and — being in the gizmo's own (distance-scaled) frame — its radius rides the
     * ring at any FOV.
     */
    private void drawViewPie(MatrixStack stack)
    {
        float sweepRad = this.currentTransform.getViewScreenSweepRad();

        if (Math.abs(sweepRad) < 1.0E-4F)
        {
            return;
        }

        Matrix4f mat = stack.peek().getPositionMatrix();

        /* The screen-plane derivation needs the FULL model-view: in the film editor the camera
         * rotation lives in the global model-view (the stack's base is identity), so a basis from
         * the stack alone put the pie in a world-space plane — "the pie doesn't face the camera".
         * The vertices still bake only the stack (the layer/pass applies the rest). */
        Matrix3f basis = modelView(stack).get3x3(new Matrix3f());

        if (Math.abs(basis.determinant()) < 1.0E-8F)
        {
            return;
        }

        /* Local directions that map EXACTLY onto the pixel axes, derived instead of guessed
         * (the previous +Y/-Y/negation shuffle went wrong three times in a row):
         *
         *   - screenAngle() is atan2(dy_pixel, dx_pixel) with pixel Y DOWN;
         *   - the projection maps view +Y to NDC +Y, and the NDC->pixel mapping flips
         *     (see computeScreenCenter: out.y uses 1 - (ndcY*0.5+0.5)) — so pixel down = view -Y;
         *   - a drawn point must therefore sit at view offset (cos, -sin) * Rv for its pixel
         *     angle to equal its pie angle: basis*right = (Rv,0,0), basis*down = (0,-Rv,0),
         *     i.e. right/down are inverse-transformed axes SCALED, not normalized.
         *
         * Rv (the view-space radius) uses cbrt|det| as the frame's uniform scale. Unlike
         * normalizing the inverse-transformed vectors — which collapses toward a line when the
         * basis is sheared (the VIEW-space transform mode shears it along the eye ray; that was
         * the "pie goes flat at an angle" symptom) — the determinant survives shear, so the pie
         * keeps the ring's size and stays a disc in every space. */
        float scale = BBSSettings.axesScale.get();
        float radius = 0.22F * scale * VIEW_RING_SCALE;
        float uniformScale = (float) Math.cbrt(Math.abs(basis.determinant()));
        float viewRadius = radius * uniformScale;

        Matrix3f inverse = basis.invert();
        Vector3f right = inverse.transform(new Vector3f(1F, 0F, 0F)).mul(viewRadius);
        Vector3f down = inverse.transform(new Vector3f(0F, -1F, 0F)).mul(viewRadius);

        float startRad = this.currentTransform.getViewGrabScreenAngle();

        int color = Colors.LIGHTEST_GRAY;
        float r = Colors.getR(color);
        float g = Colors.getG(color);
        float b = Colors.getB(color);
        float fillAlpha = 0.25F * opacity();
        float edgeAlpha = opacity();

        /* Blend and no-cull are encoded by the gizmo pipeline the flush below submits to. */
        /* No sign fold: viewScreenSweepRad() is already the PIXEL-space sweep (ViewRotateDrag
         * accumulates the screen delta and folds ROTATE_SIGN twice — once in, once out), and the
         * basis above maps pie angles 1:1 onto pixel angles. Start edge sits under the grab
         * cursor, leading edge under the live cursor. */
        int segments = Math.max(2, (int) (Math.abs(sweepRad) / (float) (2D * Math.PI) * 64F));
        float step = sweepRad / segments;
        Vector3f p1 = new Vector3f();
        Vector3f p2 = new Vector3f();

        BufferBuilder builder = begin();

        for (int i = 0; i < segments; i++)
        {
            this.pieRim(p1, right, down, startRad + step * i, 1F);
            this.pieRim(p2, right, down, startRad + step * (i + 1), 1F);

            builder.vertex(mat, 0, 0, 0).color(r, g, b, fillAlpha);
            builder.vertex(mat, p1.x, p1.y, p1.z).color(r, g, b, fillAlpha);
            builder.vertex(mat, p2.x, p2.y, p2.z).color(r, g, b, fillAlpha);
        }

        flush(builder);

        /* Bright radial edges at the grab angle and the leading angle, like the axis pie.
         * right/down already carry the radius, so radius/thickness are in ring units here. */
        float thickness = 0.005F / 0.22F / VIEW_RING_SCALE;
        builder = begin();
        this.pieEdge(builder, mat, right, down, startRad, 1F, thickness, r, g, b, edgeAlpha);
        this.pieEdge(builder, mat, right, down, startRad + sweepRad, 1F, thickness, r, g, b, edgeAlpha);
        flush(builder);
    }

    /** Point at screen angle {@code angle} and {@code radius} in the screen right/down
     *  basis, written into {@code out}. */
    private void pieRim(Vector3f out, Vector3f right, Vector3f down, float angle, float radius)
    {
        float c = (float) Math.cos(angle) * radius;
        float s = (float) Math.sin(angle) * radius;

        out.set(right.x * c + down.x * s, right.y * c + down.y * s, right.z * c + down.z * s);
    }

    /** One radial boundary line of the view pie: a thin quad from centre to the rim at
     *  screen {@code angle}, built from the screen right/down basis. */
    private void pieEdge(BufferBuilder builder, Matrix4f mat, Vector3f right, Vector3f down, float angle, float radius, float thickness, float r, float g, float b, float a)
    {
        Vector3f rim = new Vector3f();
        Vector3f perp = new Vector3f();

        this.pieRim(rim, right, down, angle, radius);
        this.pieRim(perp, right, down, angle + (float) (Math.PI / 2D), thickness);

        builder.vertex(mat, perp.x, perp.y, perp.z).color(r, g, b, a);
        builder.vertex(mat, -perp.x, -perp.y, -perp.z).color(r, g, b, a);
        builder.vertex(mat, rim.x - perp.x, rim.y - perp.y, rim.z - perp.z).color(r, g, b, a);

        builder.vertex(mat, perp.x, perp.y, perp.z).color(r, g, b, a);
        builder.vertex(mat, rim.x - perp.x, rim.y - perp.y, rim.z - perp.z).color(r, g, b, a);
        builder.vertex(mat, rim.x + perp.x, rim.y + perp.y, rim.z + perp.z).color(r, g, b, a);
    }

    private float getAxesDistanceScale(MatrixStack stack)
    {
        Vector3f cameraRelative = stack.peek().getPositionMatrix().getTranslation(new Vector3f());

        /* TODO(1.21.11 render): RenderSystem.getProjectionMatrix() was removed (1.21.5; only a
         * GpuBufferSlice accessor remains), so the perspective FOV can no longer be derived from the
         * live projection. Fall back to the configured FOV until the projection is threaded through
         * the new pipeline. The viewport scale (merged from 1.21.1) still applies. */
        return BBSSettings.getAxesDistanceScale(cameraRelative.length()) * this.viewportScale;
    }

    private void drawInfiniteLine(MatrixStack stack)
    {
        int debugIndex = this.index;

        if ((debugIndex < STENCIL_X || debugIndex > STENCIL_ZY) && this.currentTransform != null)
        {
            debugIndex = this.currentTransform.getDebugLineStencilIndex();
        }

        if (debugIndex < STENCIL_X || debugIndex > STENCIL_ZY)
        {
            return;
        }

        BufferBuilder builder = begin();

        float size = 10000F;
        float t = 0.005F;

        if (debugIndex == STENCIL_X || debugIndex == STENCIL_XZ || debugIndex == STENCIL_XY)
        {
            fillBox(builder, stack, -size, -t, -t, size, t, t, Colors.RED);
        }

        if (debugIndex == STENCIL_Y || debugIndex == STENCIL_XY || debugIndex == STENCIL_ZY)
        {
            fillBox(builder, stack, -t, -size, -t, t, size, t, Colors.GREEN);
        }

        if (debugIndex == STENCIL_Z || debugIndex == STENCIL_XZ || debugIndex == STENCIL_ZY)
        {
            fillBox(builder, stack, -t, -t, -size, t, t, size, Colors.BLUE);
        }

        flush(builder);
    }

    /* Full modelview — view * translate(-cam) * gizmoChain — folding in the global model-view that the
     * world render keeps outside the stack (since 1.21.1 stack.peek() alone no longer carries the camera
     * rotation; in the form editor the camera lives in the stack and this is a no-op). Used ONLY by
     * {@link #captureRenderMatrix} for the drag/pick math (computeWorldOrigin/Axes/ScreenCenter). The
     * actual geometry draws bake stack.peek() per-vertex and let the gizmo RenderLayer apply the active
     * global model-view itself (same as the move/scale handles always did), so they must NOT fold it in
     * again here. */
    private static Matrix4f modelView(MatrixStack stack)
    {
        Matrix4f pose = new Matrix4f(stack.peek().getPositionMatrix());

        /* In the interface pass the stack is seeded from the CAPTURED full model-view, and the
         * global one holds whatever the UI left there — folding it in would double the camera. */
        return interfacePass ? pose : new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose);
    }

    /**
     * Compute a rotation ring's camera-facing arc — the part not hidden behind
     * the central sphere — as {@code [startDeg, sweepDeg]} in the ring's own
     * plane (the angle convention {@link Draw#arc3D} draws in). A ring seen
     * face-on returns the full {@code 360}; an edge-on ring returns roughly
     * half. Writes the result into {@code out}; returns {@code false} only in
     * the degenerate case where the whole ring is hidden.
     */
    private boolean visibleRingArc(MatrixStack stack, Axis axis, float radius, Vector2f out)
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
        boolean[] visible = new boolean[n];
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

    /**
     * Draw a rotation ring with its far half (behind the central sphere) culled,
     * so it reads like the rings in a typical 3D gizmo. Immediate mode, since the
     * visible arc changes with the camera every frame.
     *
     * <p>1.21.11: submitted through the gizmo {@link RenderLayer} ({@link #begin}/{@link #flush})
     * instead of {@code setShader(getPositionColorProgram)} + {@code drawWithGlobalProgram}.
     */
    private void drawOccludedRing(MatrixStack stack, Axis axis, float radius, float thickness, float r, float g, float b, float a)
    {
        Vector2f arc = new Vector2f();

        if (!this.visibleRingArc(stack, axis, radius, arc))
        {
            return;
        }

        BufferBuilder builder = begin();

        Draw.arc3D(builder, stack, axis, radius, thickness, r, g, b, arc.x, arc.y, a);
        flush(builder);
    }

    /**
     * The screen-space (billboard) view-rotation ring: turned to face the camera and enlarged past the
     * per-axis rings. 1.21.11 rebuilds it immediate-mode each frame (the 1.21.1 cached VertexBuffer is
     * gone), so unlike the cached draw it inherits the layer's global model-view and must NOT fold it in.
     */
    private void drawBillboardRing(MatrixStack stack, float radius, float thickness, float r, float g, float b, float a)
    {
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

        BufferBuilder builder = begin();

        Draw.arc3D(builder, stack, Axis.Y, radius, thickness, r, g, b, 0F, 360F, a);
        flush(builder);

        stack.pop();
    }

    private void drawRotatePie(MatrixStack stack, Axis axis)
    {
        if (this.currentTransform == null || this.currentTransform.getDrag() == null) return;

        float scale = BBSSettings.axesScale.get();
        float radius = 0.22F * scale;

        Vector3f initialVec = this.currentTransform.getInitialDragRingVec();

        Vector3f axisX = this.currentTransform.getDrag().gizmoWorldAxes.getColumn(0, new Vector3f());
        Vector3f axisY = this.currentTransform.getDrag().gizmoWorldAxes.getColumn(1, new Vector3f());
        Vector3f axisZ = this.currentTransform.getDrag().gizmoWorldAxes.getColumn(2, new Vector3f());
        /* The ring's actual world rotation axis in the active space — the same
         * basis the ring is drawn in (Gizmo.reorientForSpace) and the drag turns
         * about. The axis comes from the GESTURE itself (its anchored turn axis),
         * so the pie can never disagree with the rotation — the drawn frame axis
         * and the real turn axis differ on the channel path (PARENT / the pole
         * fallback), where cubic models flip the channels' X/Z response. */
        DragStrategy ringGesture = this.ringDragGesture();
        Vector3f dragAxisDir = ringGesture != null ? ringGesture.ringAxisDir() : null;

        if (dragAxisDir == null)
        {
            dragAxisDir = this.currentTransform.getDrag().frameBasis(this.currentTransform.getSpace()).getColumn(axis.ordinal(), new Vector3f());
        }

        float gx = initialVec.dot(axisX);
        float gy = initialVec.dot(axisY);
        float gz = initialVec.dot(axisZ);

        float px = 0;
        float pz = 0;
        float sweepDir = 1;

        if (axis == Axis.Y)
        {
            px = gx;
            pz = gz;
            sweepDir = Math.signum(dragAxisDir.dot(new Vector3f(axisY).mul(-1)));
        }
        else if (axis == Axis.X)
        {
            px = gy;
            pz = gz;
            sweepDir = Math.signum(dragAxisDir.dot(axisX));
        }
        else if (axis == Axis.Z)
        {
            px = gx;
            pz = -gy;
            sweepDir = Math.signum(dragAxisDir.dot(new Vector3f(axisZ).mul(-1)));
        }

        if (sweepDir == 0) sweepDir = 1;

        /* The ring is baked static for the whole drag (see applyBakedRotation),
         * so the pie grows from the fixed grab angle in every space — no
         * counter-rotation to cancel a live-rotating frame is needed. */
        float startDeg = MathUtils.toDeg((float) Math.atan2(pz, px));
        float sweepDeg = this.currentTransform.getAccumulatedRotateDeg() * sweepDir;

        stack.push();

        if (axis == Axis.X) stack.multiply(RotationAxis.POSITIVE_Z.rotation(MathUtils.PI / 2F));
        if (axis == Axis.Z) stack.multiply(RotationAxis.POSITIVE_X.rotation(MathUtils.PI / 2F));

        int color = axis == Axis.X ? Colors.RED : (axis == Axis.Y ? Colors.GREEN : Colors.BLUE);
        float r = Colors.getR(color);
        float g = Colors.getG(color);
        float b = Colors.getB(color);
        float a = 0.25F * opacity();
        float edgeAlpha = opacity();

        Matrix4f mat = stack.peek().getPositionMatrix();

        /* Blend, no-depth and no-cull all live in the gizmo pipeline now (the original toggled them via
         * RenderSystem.enableBlend/depthFunc(GL_ALWAYS)/disableCull). */
        BufferBuilder builder = begin();

        int segments = Math.max(12, (int) (Math.abs(sweepDeg) / 360F * 64F));
        float step = sweepDeg / segments;

        for (int i = 0; i < segments; i++)
        {
            float a1 = MathUtils.toRad(startDeg + step * i);
            float a2 = MathUtils.toRad(startDeg + step * (i + 1));

            float x1 = (float) Math.cos(a1) * radius;
            float z1 = (float) Math.sin(a1) * radius;
            float x2 = (float) Math.cos(a2) * radius;
            float z2 = (float) Math.sin(a2) * radius;

            builder.vertex(mat, 0, 0, 0).color(r, g, b, a);

            if (sweepDeg > 0)
            {
                builder.vertex(mat, x1, 0, z1).color(r, g, b, a);
                builder.vertex(mat, x2, 0, z2).color(r, g, b, a);
            }
            else
            {
                builder.vertex(mat, x2, 0, z2).color(r, g, b, a);
                builder.vertex(mat, x1, 0, z1).color(r, g, b, a);
            }
        }

        flush(builder);

        float lineThickness = 0.005F * scale;
        builder = begin();

        float endDeg = startDeg + sweepDeg;

        float sx = (float) Math.cos(MathUtils.toRad(startDeg)) * radius;
        float sz = (float) Math.sin(MathUtils.toRad(startDeg)) * radius;
        float ex = (float) Math.cos(MathUtils.toRad(endDeg)) * radius;
        float ez = (float) Math.sin(MathUtils.toRad(endDeg)) * radius;

        Vector3f p1 = new Vector3f(-sz, 0, sx).normalize().mul(lineThickness);

        builder.vertex(mat, p1.x, 0, p1.z).color(r, g, b, edgeAlpha);
        builder.vertex(mat, -p1.x, 0, -p1.z).color(r, g, b, edgeAlpha);
        builder.vertex(mat, sx - p1.x, 0, sz - p1.z).color(r, g, b, edgeAlpha);

        builder.vertex(mat, p1.x, 0, p1.z).color(r, g, b, edgeAlpha);
        builder.vertex(mat, sx - p1.x, 0, sz - p1.z).color(r, g, b, edgeAlpha);
        builder.vertex(mat, sx + p1.x, 0, sz + p1.z).color(r, g, b, edgeAlpha);

        Vector3f p2 = new Vector3f(-ez, 0, ex).normalize().mul(lineThickness);
        builder.vertex(mat, p2.x, 0, p2.z).color(r, g, b, edgeAlpha);
        builder.vertex(mat, -p2.x, 0, -p2.z).color(r, g, b, edgeAlpha);
        builder.vertex(mat, ex - p2.x, 0, ez - p2.z).color(r, g, b, edgeAlpha);

        builder.vertex(mat, p2.x, 0, p2.z).color(r, g, b, edgeAlpha);
        builder.vertex(mat, ex - p2.x, 0, ez - p2.z).color(r, g, b, edgeAlpha);
        builder.vertex(mat, ex + p2.x, 0, ez + p2.z).color(r, g, b, edgeAlpha);

        flush(builder);

        stack.pop();
    }

    /**
     * The handle the live edit is grabbing, or {@code null} when nothing should
     * be filtered out: no edit is running, or the hide-inactive-handles setting
     * is off. Both draw passes show only this handle when it is present. A
     * two-axis rotation has no handle of its own, so the primary ring stands in.
     */
    private Handle activeDragHandle()
    {
        UIPropTransform transform = this.currentTransform;

        if (!BBSSettings.hideInactiveHandles.get() || transform == null || !transform.isEditing())
        {
            return null;
        }

        TransformOp op = transform.getOp();
        Axis axis = transform.getAxis();

        if (op == TransformOp.ROTATE)
        {
            if (transform.isSphereRotate()) return Handle.TRACKBALL;
            if (transform.isViewRotate()) return Handle.VIEW;
            if (axis == Axis.X) return Handle.ROTATE_X;
            if (axis == Axis.Y) return Handle.ROTATE_Y;
            if (axis == Axis.Z) return Handle.ROTATE_Z;

            return null;
        }

        if (op == TransformOp.TRANSLATE && transform.isScreenTranslate())
        {
            return Handle.SCREEN;
        }

        if (op == TransformOp.SCALE && transform.isScaleAll())
        {
            return Handle.SCALE_ALL;
        }

        Op handleOp = op == TransformOp.SCALE ? Op.SCALE : Op.MOVE;
        Axis axis2 = transform.getAxis2();

        for (Handle handle : Handle.values())
        {
            if (handle.op != handleOp)
            {
                continue;
            }

            boolean matches = axis2 == null
                ? handle.axis == axis && handle.axis2 == null
                : (handle.axis == axis && handle.axis2 == axis2) || (handle.axis == axis2 && handle.axis2 == axis);

            if (matches)
            {
                return handle;
            }
        }

        return null;
    }

    /**
     * Factor the move/scale handles shrink by so they nest inside the rotation
     * rings in combined mode. With "hide rotation rings" on there is nothing to
     * nest inside, so they keep their full (larger) size.
     */
    private float combinedInnerScale()
    {
        return this.mode == Mode.COMBINED && !BBSSettings.rotateHideRings.get() ? COMBINED_INNER_SCALE : 1F;
    }

    private void drawRotateHandles(MatrixStack stack, Handle active)
    {
        float scale = BBSSettings.axesScale.get();
        float thickness = BBSSettings.axesThickness.get();

        /* Faithful to the original cached-VBO geometry (updateVbos): VertexBuffer was removed in the
         * 1.21.5 GPU rewrite, so the ring/sphere are rebuilt immediately each frame via Draw's public
         * arc3D/sphere builders with the same parameters instead of being cached. */
        float radius = 0.22F * scale;
        float thicknessRing = 0.02F * scale * thickness;

        /* The 3D sphere itself is invisible — it only acts as the trackball grab
         * area. Hover feedback is a screen-space glow composited in
         * {@link #renderSphereHighlight}. Depth state is owned by the caller
         * ({@link #drawOccludedGizmo}) so the handles sort against each other. */

        /* IK owns this bone's rotation: the rings render washed-out as the
         * visible "not yours to turn" cue, matching the rotation strategies'
         * refusal to start there (the pads still edit the FK channels). */
        boolean constrained = this.currentTransform != null && this.currentTransform.isRotationConstrained();

        /* Always-on-top depth (original RenderSystem.depthFunc(GL_ALWAYS)) is encoded by the gizmo
         * pipeline's NO_DEPTH_TEST. Draw.arc3D applies the same per-axis orientation the cached ring
         * used (X → rotateZ 90°, Z → rotateX 90°, Y → none). */
        if (!BBSSettings.rotateHideRings.get())
        {
            float ringAlpha = opacity();

            if (active == null || active == Handle.ROTATE_Z) this.drawOccludedRing(stack, Axis.Z, radius, thicknessRing, dimmed(Colors.getR(Colors.BLUE), constrained), dimmed(Colors.getG(Colors.BLUE), constrained), dimmed(Colors.getB(Colors.BLUE), constrained), ringAlpha);
            if (active == null || active == Handle.ROTATE_X) this.drawOccludedRing(stack, Axis.X, radius, thicknessRing, dimmed(Colors.getR(Colors.RED), constrained), dimmed(Colors.getG(Colors.RED), constrained), dimmed(Colors.getB(Colors.RED), constrained), ringAlpha);
            if (active == null || active == Handle.ROTATE_Y) this.drawOccludedRing(stack, Axis.Y, radius, thicknessRing, dimmed(Colors.getR(Colors.GREEN), constrained), dimmed(Colors.getG(Colors.GREEN), constrained), dimmed(Colors.getB(Colors.GREEN), constrained), ringAlpha);
        }

        /* The screen-space (billboard) view-rotation ring is intentionally excluded from the
         * "Hide rotation rings" option, so it is always drawn regardless of that setting. */
        if (active == null || active == Handle.VIEW)
        {
            int color = Colors.LIGHTEST_GRAY;
            float alpha = Colors.getA(color) * opacity() * (constrained ? 0.35F : 1F);

            this.drawBillboardRing(stack, radius, thicknessRing, Colors.getR(color), Colors.getG(color), Colors.getB(color), alpha);
        }
    }

    /** Washes a ring colour channel toward flat gray for IK-owned rotations. */
    private static float dimmed(float channel, boolean constrained)
    {
        return constrained ? channel * 0.25F + 0.3F : channel;
    }

    private void drawAxes(MatrixStack stack, float axisSize, float axisOffset)
    {
        float scale = BBSSettings.axesScale.get();
        float thickness = BBSSettings.axesThickness.get();

        Handle active = this.activeDragHandle();

        boolean showMove = this.mode.shows(Op.MOVE) && (active == null || active.op == Op.MOVE || active.op == Op.SCREEN);
        boolean showScale = this.mode.shows(Op.SCALE) && (active == null || active.op == Op.SCALE || active.op == Op.SCALE_ALL);
        boolean showRotate = this.mode.shows(Op.ROTATE) && (active == null || active.op == Op.ROTATE || active.op == Op.VIEW || active.op == Op.TRACKBALL);

        axisSize *= scale * this.combinedInnerScale();
        axisOffset *= scale * thickness;

        BufferBuilder builder = null;
        boolean building = false;

        if (showRotate)
        {
            this.drawRotateHandles(stack, active);
        }

        if (showMove || showScale)
        {
            builder = begin();
            building = true;

            /* The bars and planes read as move when move is on screen and as
             * scale only when scale stands alone — the same identity the pick
             * stencil assigns, so the hide-inactive filter matches what a grab
             * of that element actually drives. */
            Handle barX = showMove ? Handle.MOVE_X : Handle.SCALE_X;
            Handle barY = showMove ? Handle.MOVE_Y : Handle.SCALE_Y;
            Handle barZ = showMove ? Handle.MOVE_Z : Handle.SCALE_Z;
            Handle planeXZ = showMove ? Handle.MOVE_XZ : Handle.SCALE_XZ;
            Handle planeXY = showMove ? Handle.MOVE_XY : Handle.SCALE_XY;
            Handle planeZY = showMove ? Handle.MOVE_ZY : Handle.SCALE_ZY;

            if (active == null || active == barX) fillBox(builder, stack, 0, -axisOffset, -axisOffset, axisSize, axisOffset, axisOffset, Colors.RED);
            if (active == null || active == barY) fillBox(builder, stack, -axisOffset, 0, -axisOffset, axisOffset, axisSize, axisOffset, Colors.GREEN);
            if (active == null || active == barZ) fillBox(builder, stack, -axisOffset, -axisOffset, 0, axisOffset, axisOffset, axisSize, Colors.BLUE);

            /* Screen-space (view-plane) translate handle: a white cube at the centre, twice the bars'
             * thickness. Drawn before the planes so they overlay it, and after the rotation sphere (above)
             * so it stays visible in combined. */
            if (showMove && (active == null || active == Handle.SCREEN))
            {
                float screenHalf = SCREEN_CUBE_HALF * scale * thickness;

                fillBox(builder, stack, -screenHalf, -screenHalf, -screenHalf, screenHalf, screenHalf, screenHalf, Colors.WHITE);
            }

            /* Uniform-scale handle: the same centre cube, shown in scale mode only when
             * move isn't (in combined the centre is the translate handle), so the pick
             * is never ambiguous between the two. */
            if (showScale && !showMove && (active == null || active == Handle.SCALE_ALL))
            {
                float scaleAllHalf = SCREEN_CUBE_HALF * scale * thickness;

                fillBox(builder, stack, -scaleAllHalf, -scaleAllHalf, -scaleAllHalf, scaleAllHalf, scaleAllHalf, scaleAllHalf, Colors.WHITE);
            }

            /* The plane quad's footprint is a fixed fraction of the axis length,
             * independent of axesThickness — thickness only fattens the bars and
             * the flat slab depth, not how big the two-axis plane reads. */
            float planeStart = axisSize * 0.2F;
            float planeEnd = planeStart + axisSize * 0.2F;
            float planeThickness = axisOffset * 0.5F;

            if (active == null || active == planeXZ) fillBox(builder, stack, planeStart, -planeThickness, planeStart, planeEnd, planeThickness, planeEnd, Colors.PLANE_XZ);
            if (active == null || active == planeXY) fillBox(builder, stack, planeStart, planeStart, -planeThickness, planeEnd, planeEnd, planeThickness, Colors.PLANE_XY);
            if (active == null || active == planeZY) fillBox(builder, stack, -planeThickness, planeStart, planeStart, planeThickness, planeEnd, planeEnd, Colors.PLANE_ZY);

            if (showScale)
            {
                float cubeHalf = SCALE_CUBE_HALF * scale * thickness;

                if (active == null || active == Handle.SCALE_X) fillBox(builder, stack, axisSize - cubeHalf, -cubeHalf, -cubeHalf, axisSize + cubeHalf, cubeHalf, cubeHalf, Colors.RED);
                if (active == null || active == Handle.SCALE_Y) fillBox(builder, stack, -cubeHalf, axisSize - cubeHalf, -cubeHalf, cubeHalf, axisSize + cubeHalf, cubeHalf, Colors.GREEN);
                if (active == null || active == Handle.SCALE_Z) fillBox(builder, stack, -cubeHalf, -cubeHalf, axisSize - cubeHalf, cubeHalf, cubeHalf, axisSize + cubeHalf, Colors.BLUE);
            }
        }

        /* The centre cube is decoration, not a handle, so any filtered drag hides it. */
        if (active == null && (showMove || showScale || showRotate))
        {
            if (!building)
            {
                builder = begin();
                building = true;
            }

            fillBox(builder, stack, -axisOffset, -axisOffset, -axisOffset, axisOffset, axisOffset, axisOffset, Colors.WHITE);
        }

        if (building)
        {
            flush(builder);
        }
    }

    public void renderStencil(MatrixStack stack, StencilMap map)
    {
        if (BBSRendering.isIrisShadowPass())
        {
            return;
        }

        if (!BBSSettings.gizmos.get())
        {
            return;
        }

        stack.push();
        MatrixStackUtils.scaleBack(stack);
        this.captureRenderMatrix(stack);
        this.drawStencilAxes(stack, map);
        stack.pop();
    }

    /**
     * Draw the gizmo handles as stencil IDs into the currently bound picking
     * framebuffer, from a stack already positioned at the gizmo origin. Shared by
     * the world-pass {@link #renderStencil} and the UI-pass
     * {@link #renderStencilInterface}.
     */
    private void drawStencilAxes(MatrixStack stack, StencilMap map)
    {
        this.applyBakedRotation(stack);

        float distanceScale = this.getAxesDistanceScale(stack);

        stack.push();
        /* Same VIEW flattening as the visual pass, or the hitboxes would sit on the
         * unsheared handles and picking would drift with the distance from centre. */
        this.applyViewShear(stack);
        stack.scale(distanceScale, distanceScale, distanceScale);
        /* Same axisOffset as the visual pass (Gizmo#drawGizmo) so the pick hitbox
         * matches the drawn handles instead of overhanging them. */
        this.drawAxes(stack, map, 0.25F, 0.008F);
        stack.pop();
    }

    /**
     * Draw the gizmo's pick stencil over a {@link GizmoViewport} in the UI pass,
     * from the model-view captured this frame ({@link #lastRenderMatrix}, set by
     * {@link #captureVisual}). This is the stencil counterpart of
     * {@link #renderInterface}: it uses the identical viewport / projection /
     * matrix setup, so the handle IDs land on exactly the pixels the visual
     * draws and picking lines up with what the user sees, instead of being
     * rendered in the world pass on a separate frame of reference.
     *
     * <p>The caller binds the picking framebuffer before this call (and reads it
     * back / unbinds afterwards); it must also flush the UI batcher first, since
     * this does not (the bound framebuffer is the pick buffer, not the screen).
     */
    public void renderStencilInterface(UIContext context, Matrix4f projection, Area area, StencilMap map)
    {
        if (BBSRendering.isIrisShadowPass() || !this.hasLastRenderMatrix || !BBSSettings.gizmos.get()
            || context == null || projection == null || area == null)
        {
            return;
        }

        /* The stencil counterpart of {@link #renderInterface}: identical stack (the captured full
         * model-view) and projection, so the handle IDs land on exactly the pixels the visual draws.
         * The caller has the picking framebuffer bound (BBSPickerRenderer.setRenderTarget), so the
         * flushes only need the projection override + the interface-pass identity model-view. */
        this.setViewportScale(context.menu.height / (float) area.h);

        BBSPickerRenderer.setProjectionOverride(projection);
        interfacePass = true;

        try
        {
            MatrixStack stack = new MatrixStack();

            MatrixStackUtils.multiply(stack, this.lastRenderMatrix);
            this.drawStencilAxes(stack, map);
        }
        finally
        {
            interfacePass = false;
            BBSPickerRenderer.setProjectionOverride(null);
        }
    }

    private void captureRenderMatrix(MatrixStack stack)
    {
        /* The drag/pick math (computeWorldOrigin, computeWorldAxes, computeScreenCenter)
         * needs the FULL modelview — view * translate(-cam) * gizmoChain. Since 1.21.1 the
         * world render keeps the camera view in RenderSystem's global model-view and hands
         * the stack an identity base, so stack.peek() alone no longer carries the camera
         * rotation. modelView() folds the global model-view back in (and is a no-op in the
         * form editor, where the camera already lives in the stack), giving the same
         * coordinate frame the gizmo is actually drawn in. */
        this.lastRenderMatrix.set(modelView(stack));
        this.hasLastRenderMatrix = true;
    }

    /**
     * Bake a transform-space reorientation into the gizmo's drawing frame, in
     * place, BEFORE it is captured &mdash; so the visual ({@link #renderInterface})
     * and the pick stencil, both rebuilt from the captured frame, stay in
     * lockstep. The frame's origin (its translation) is kept and only its axes
     * are replaced with {@link GizmoDrag#stackBasisForSpace} &mdash; the drawn
     * twin of the frame the drags slide/turn in ({@link GizmoDrag#frameBasis}).
     * {@link TransformSpace#LOCAL} leaves the bone's own axes untouched, and
     * {@link TransformSpace#PARENT} keeps the placed frame too: the non-local
     * placement matrix is the cache's origin flavour &mdash; the bone's frame
     * BEFORE its own rotation, which is exactly the parent frame. Call right
     * after the gizmo origin is multiplied onto the stack and before
     * {@link #render}/{@link #renderStencil}/{@link #captureVisual}.
     *
     * <p>{@code globalAxes} is what {@link TransformSpace#GLOBAL} aligns to
     * (see {@link GizmoDrag#globalWorldAxes}); pass the same axes the drag gets
     * or the handles would be drawn off the frame they slide in. {@code null}
     * is the plain world axes, which is what the hosts without a scene use.
     */
    public void reorientForSpace(MatrixStack stack, TransformSpace space, Matrix4f cameraView, Matrix3f globalAxes)
    {
        /* Remembered for the draw passes ({@link #applyViewShear}). Without a camera
         * nothing is reoriented, so the handles keep their placement frame and the
         * remembered space must not claim otherwise. */
        this.lastSpace = cameraView == null ? null : space;

        if (space == null || space == TransformSpace.LOCAL || space == TransformSpace.PARENT || cameraView == null)
        {
            return;
        }

        Matrix4f matrix = stack.peek().getPositionMatrix();
        Vector3f translation = matrix.getTranslation(new Vector3f());
        Matrix3f basis = new Matrix3f(GizmoDrag.stackBasisForSpace(space, cameraView, globalAxes));

        /* The basis is the FINAL model-view orientation the handles should render with. In the
         * form editor the stack itself carries the camera (global model-view identity), so it can
         * be written straight in. The film's world pass keeps the camera rotation in the GLOBAL
         * model-view and hands a camera-relative stack — writing the view-composed basis there
         * gets the camera folded in a SECOND time at draw, so Global/World handles came out turned
         * by the camera and the VIEW shear flattened onto a wrong ray ("2D, not facing the
         * camera"). Pre-multiplying the global's inverse makes the composed result exactly the
         * basis in both conventions: G * (G⁻¹ * basis) = basis; G = identity in the form editor. */
        Matrix3f global = RenderSystem.getModelViewMatrix().get3x3(new Matrix3f());

        if (Math.abs(global.determinant()) > 1.0E-8F)
        {
            global.invert().mul(basis, basis);
        }

        matrix.set(new Matrix4f(basis).setTranslation(translation));
    }

    /**
     * Freeze the gizmo orientation while an axis ring is dragged. The first
     * draw of a gesture snapshots the stack — still at the grab orientation,
     * since the drag hasn't written anything by then — and every later draw
     * (visual and stencil alike) rewinds to that snapshot. Keying the snapshot
     * to the gesture makes the freeze self-maintaining: no edit entry point
     * has to remember to bake, and switching the axis mid-edit re-freezes at
     * the restored orientation. The live {@link #lastRenderMatrix} is left
     * untouched for pick/projection helpers.
     */
    private void applyBakedRotation(MatrixStack stack)
    {
        DragStrategy gesture = this.ringDragGesture();

        if (gesture == null)
        {
            this.bakedGesture = null;

            return;
        }

        if (this.bakedGesture != gesture)
        {
            this.bakedRotationMatrix.set(stack.peek().getPositionMatrix());
            this.bakedGesture = gesture;
        }

        stack.peek().getPositionMatrix().set(this.bakedRotationMatrix);
    }

    /**
     * The live rotation gesture the gizmo should freeze its rings for, or
     * {@code null} when none: the sphere and the view ring own their whole
     * orientation and want the live frame instead.
     */
    private DragStrategy ringDragGesture()
    {
        UIPropTransform transform = this.currentTransform;

        if (transform == null
            || !transform.isEditing()
            || transform.getOp() != TransformOp.ROTATE
            || transform.isSphereRotate()
            || transform.isViewRotate())
        {
            return null;
        }

        return transform.getStrategy();
    }

    /**
     * Picking-pass mirror of {@link #drawRotateHandles}: the per-axis rotation rings and the billboard view ring,
     * with each handle's stencil id baked into the red channel instead of its display colour, submitted into the
     * off-screen picking target via {@link #flushPick}. Geometry params (radius/thickness, billboard orientation,
     * {@link #VIEW_RING_SCALE}) are identical to the visual pass so the pick overlaps the visible rings 1:1.
     *
     * <p>The trackball sphere is intentionally absent — a pixel can't be both "bone" and "sphere", so the sphere
     * is picked by a screen-space disc test in {@link GizmoInteraction} rather than the stencil. The rotate pie is
     * a drag visualisation, not a handle, so it is excluded too.
     */
    /** Picking twin of {@link #drawOccludedRing}: the same camera-facing arc, with the handle id in red. */
    private void drawOccludedRingStencil(MatrixStack stack, Axis axis, float radius, float thickness, float id)
    {
        Vector2f arc = new Vector2f();

        if (!this.visibleRingArc(stack, axis, radius, arc))
        {
            return;
        }

        BufferBuilder builder = begin();

        Draw.arc3D(builder, stack, axis, radius, thickness, id, 0F, 0F, arc.x, arc.y);
        flushPick(builder);
    }

    private void drawRotateHandlesStencil(MatrixStack stack)
    {
        float scale = BBSSettings.axesScale.get();
        float thickness = BBSSettings.axesThickness.get();

        float radius = 0.22F * scale;
        float thicknessRing = 0.02F * scale * thickness;

        if (!BBSSettings.rotateHideRings.get())
        {
            /* Cut the far half of each ring away, exactly as the visual does (drawOccludedRing). Drawing
             * FULL rings here made the pick disagree with what is on screen: the hidden half still claimed
             * its pixels, and since those run across the middle of the gizmo they covered the trackball
             * sphere's disc — so a click meant for the sphere was answered by a ring, and the sphere
             * behaved as if it sat behind everything. */
            this.drawOccludedRingStencil(stack, Axis.Z, radius, thicknessRing, STENCIL_ROTATE_Z / 255F);
            this.drawOccludedRingStencil(stack, Axis.X, radius, thicknessRing, STENCIL_ROTATE_X / 255F);
            this.drawOccludedRingStencil(stack, Axis.Y, radius, thicknessRing, STENCIL_ROTATE_Y / 255F);
        }

        /* The screen-space (billboard) view-rotation ring stays pickable even with "Hide rotation rings" on,
         * exactly as the visual pass keeps it drawn. Orientation derived from stack.peek() as there. */
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

        BufferBuilder builder = begin();

        Draw.arc3D(builder, stack, Axis.Y, radius, thicknessRing, STENCIL_VIEW / 255F, 0F, 0F);
        flushPick(builder);

        stack.pop();
    }

    /**
     * The picking (stencil) pass: re-draw the gizmo handles with each handle's stencil id encoded in the red
     * channel, into the off-screen {@link StencilFormFramebuffer} (via {@link #flushPick}), so the read-back pixel
     * under the cursor names the hovered handle. Mirrors the visual {@link #drawAxes(MatrixStack, float, float)}
     * pass exactly (same show/hide logic, same geometry) — only the colours (id vs display) and the submission
     * target (off-screen vs viewport) differ. Faithful to the 1.21.1 stencil pass; the only forced change is the
     * submission path (a manual render pass in place of the removed {@code getPositionColorProgram} immediate
     * draw). {@code map} is unused here (handle pairs are seeded separately by {@link StencilMap#setup}); kept for
     * signature parity with {@link #renderStencil}.
     */
    private void drawAxes(MatrixStack stack, StencilMap map, float axisSize, float axisOffset)
    {
        float scale = BBSSettings.axesScale.get();
        float thickness = BBSSettings.axesThickness.get();

        Handle active = this.activeDragHandle();

        boolean showMove = this.mode.shows(Op.MOVE) && (active == null || active.op == Op.MOVE || active.op == Op.SCREEN);
        boolean showScale = this.mode.shows(Op.SCALE) && (active == null || active.op == Op.SCALE || active.op == Op.SCALE_ALL);
        boolean showRotate = this.mode.shows(Op.ROTATE) && (active == null || active.op == Op.ROTATE || active.op == Op.VIEW || active.op == Op.TRACKBALL);

        axisSize *= scale * this.combinedInnerScale();
        axisOffset *= scale * thickness;

        if (showRotate)
        {
            this.drawRotateHandlesStencil(stack);
        }

        if (showMove || showScale)
        {
            /* The bar reads as move when move is shown (combined) and as scale only when scale stands alone; the
             * scale handle then lives on the end cubes, so move and scale never share an id under the cursor. */
            Handle barX = showMove ? Handle.MOVE_X : Handle.SCALE_X;
            Handle barY = showMove ? Handle.MOVE_Y : Handle.SCALE_Y;
            Handle barZ = showMove ? Handle.MOVE_Z : Handle.SCALE_Z;
            Handle planeXZ = showMove ? Handle.MOVE_XZ : Handle.SCALE_XZ;
            Handle planeXY = showMove ? Handle.MOVE_XY : Handle.SCALE_XY;
            Handle planeZY = showMove ? Handle.MOVE_ZY : Handle.SCALE_ZY;

            BufferBuilder builder = begin();

            Draw.fillBox(builder, stack, 0, -axisOffset, -axisOffset, axisSize, axisOffset, axisOffset, barX.index / 255F, 0F, 0F);
            Draw.fillBox(builder, stack, -axisOffset, 0, -axisOffset, axisOffset, axisSize, axisOffset, barY.index / 255F, 0F, 0F);
            Draw.fillBox(builder, stack, -axisOffset, -axisOffset, 0, axisOffset, axisOffset, axisSize, barZ.index / 255F, 0F, 0F);

            /* Centre cube as id 0 (black): a dead zone, exactly like the original — the bars' shared origin must
             * not pick as any single axis. Drawn before the planes/cubes so they overlay it where they meet. */
            Draw.fillBox(builder, stack, -axisOffset, -axisOffset, -axisOffset, axisOffset, axisOffset, axisOffset, 0F, 0F, 0F);

            /* Screen-space handle hitbox: before the planes so they win where they overlap. Matches the visual cube. */
            if (showMove && (active == null || active == Handle.SCREEN))
            {
                float screenHalf = SCREEN_CUBE_HALF * scale * thickness;

                Draw.fillBox(builder, stack, -screenHalf, -screenHalf, -screenHalf, screenHalf, screenHalf, screenHalf, STENCIL_SCREEN / 255F, 0F, 0F);
            }

            /* Uniform-scale hitbox: matches the visual centre cube in scale-only mode. */
            if (showScale && !showMove && (active == null || active == Handle.SCALE_ALL))
            {
                float scaleAllHalf = SCREEN_CUBE_HALF * scale * thickness;

                Draw.fillBox(builder, stack, -scaleAllHalf, -scaleAllHalf, -scaleAllHalf, scaleAllHalf, scaleAllHalf, scaleAllHalf, STENCIL_SCALE_ALL / 255F, 0F, 0F);
            }

            /* The plane quad's footprint is a fixed fraction of the axis length,
             * independent of axesThickness — thickness only fattens the bars and
             * the flat slab depth, not how big the two-axis plane reads. */
            float planeStart = axisSize * 0.2F;
            float planeEnd = planeStart + axisSize * 0.2F;
            float planeThickness = axisOffset * 0.5F;

            if (active == null || active == planeXZ) Draw.fillBox(builder, stack, planeStart, -planeThickness, planeStart, planeEnd, planeThickness, planeEnd, planeXZ.index / 255F, 0F, 0F);
            if (active == null || active == planeXY) Draw.fillBox(builder, stack, planeStart, planeStart, -planeThickness, planeEnd, planeEnd, planeThickness, planeXY.index / 255F, 0F, 0F);
            if (active == null || active == planeZY) Draw.fillBox(builder, stack, -planeThickness, planeStart, planeStart, planeThickness, planeEnd, planeEnd, planeZY.index / 255F, 0F, 0F);

            if (showScale)
            {
                float cubeHalf = SCALE_CUBE_HALF * scale * thickness;

                if (active == null || active == Handle.SCALE_X) Draw.fillBox(builder, stack, axisSize - cubeHalf, -cubeHalf, -cubeHalf, axisSize + cubeHalf, cubeHalf, cubeHalf, STENCIL_SCALE_X / 255F, 0F, 0F);
                if (active == null || active == Handle.SCALE_Y) Draw.fillBox(builder, stack, -cubeHalf, axisSize - cubeHalf, -cubeHalf, cubeHalf, axisSize + cubeHalf, cubeHalf, STENCIL_SCALE_Y / 255F, 0F, 0F);
                if (active == null || active == Handle.SCALE_Z) Draw.fillBox(builder, stack, -cubeHalf, -cubeHalf, axisSize - cubeHalf, cubeHalf, cubeHalf, axisSize + cubeHalf, STENCIL_SCALE_Z / 255F, 0F, 0F);
            }

            flushPick(builder);
        }
    }

    public static enum Mode
    {
        TRANSLATE, SCALE, ROTATE, COMBINED;

        public boolean shows(Op op)
        {
            switch (this)
            {
                case TRANSLATE:
                    return op == Op.MOVE || op == Op.SCREEN;
                case SCALE:
                    return op == Op.SCALE;
                case ROTATE:
                    return op == Op.ROTATE || op == Op.VIEW || op == Op.TRACKBALL;
                case COMBINED:
                    return op == Op.MOVE || op == Op.SCALE || op == Op.ROTATE || op == Op.VIEW || op == Op.SCREEN;
                default:
                    return false;
            }
        }
    }

    /**
     * Kind of transform a handle drives. {@link #transformOp} is the operation
     * {@link UIPropTransform#enableMode(TransformOp, Axis, Axis, GizmoDrag)}
     * expects; VIEW and TRACKBALL are rotate variants routed through their own
     * enable* calls, and SCALE_ALL is the uniform (three-axis) scale variant
     * routed through its own enable call.
     */
    public static enum Op
    {
        MOVE(TransformOp.TRANSLATE),
        SCALE(TransformOp.SCALE),
        SCALE_ALL(TransformOp.SCALE),
        ROTATE(TransformOp.ROTATE),
        VIEW(TransformOp.ROTATE),
        TRACKBALL(TransformOp.ROTATE),
        SCREEN(TransformOp.TRANSLATE);

        public final TransformOp transformOp;

        Op(TransformOp transformOp)
        {
            this.transformOp = transformOp;
        }
    }

    /**
     * A single pickable handle: its stencil id plus the operation and axes it
     * stands for. {@link #start} resolves a picked stencil id straight to one
     * of these and dispatches the matching transform — no dependence on the
     * active display {@link Mode}.
     */
    public static enum Handle
    {
        MOVE_X(STENCIL_X, Op.MOVE, Axis.X, null),
        MOVE_Y(STENCIL_Y, Op.MOVE, Axis.Y, null),
        MOVE_Z(STENCIL_Z, Op.MOVE, Axis.Z, null),
        MOVE_XZ(STENCIL_XZ, Op.MOVE, Axis.X, Axis.Z),
        MOVE_XY(STENCIL_XY, Op.MOVE, Axis.X, Axis.Y),
        MOVE_ZY(STENCIL_ZY, Op.MOVE, Axis.Z, Axis.Y),
        SCALE_X(STENCIL_SCALE_X, Op.SCALE, Axis.X, null),
        SCALE_Y(STENCIL_SCALE_Y, Op.SCALE, Axis.Y, null),
        SCALE_Z(STENCIL_SCALE_Z, Op.SCALE, Axis.Z, null),
        SCALE_XZ(STENCIL_SCALE_XZ, Op.SCALE, Axis.X, Axis.Z),
        SCALE_XY(STENCIL_SCALE_XY, Op.SCALE, Axis.X, Axis.Y),
        SCALE_ZY(STENCIL_SCALE_ZY, Op.SCALE, Axis.Z, Axis.Y),
        ROTATE_X(STENCIL_ROTATE_X, Op.ROTATE, Axis.X, null),
        ROTATE_Y(STENCIL_ROTATE_Y, Op.ROTATE, Axis.Y, null),
        ROTATE_Z(STENCIL_ROTATE_Z, Op.ROTATE, Axis.Z, null),
        TRACKBALL(STENCIL_TRACKBALL, Op.TRACKBALL, null, null),
        VIEW(STENCIL_VIEW, Op.VIEW, null, null),
        SCREEN(STENCIL_SCREEN, Op.SCREEN, null, null),
        SCALE_ALL(STENCIL_SCALE_ALL, Op.SCALE_ALL, null, null);

        public final int index;
        public final Op op;
        public final Axis axis;
        public final Axis axis2;

        Handle(int index, Op op, Axis axis, Axis axis2)
        {
            this.index = index;
            this.op = op;
            this.axis = axis;
            this.axis2 = axis2;
        }

        public static Handle byIndex(int index)
        {
            for (Handle handle : values())
            {
                if (handle.index == index)
                {
                    return handle;
                }
            }

            return null;
        }
    }
}
