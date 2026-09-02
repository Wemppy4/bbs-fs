package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.drag.DragStrategy;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.EnumSet;
import java.util.function.Supplier;

public class Gizmo
{
    /* Every pickable gizmo handle owns a distinct stencil id, so the gizmo can show
     * move, scale and rotate at once and a pick unambiguously names both the
     * operation and the axis. {@link Handle} ties these together; hiding an
     * {@link Element} simply drops its handles from both passes. {@link #STENCIL_MAX}
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
    /** Uniform-scale handle: the centre cube that scales all three axes at once. */
    public final static int STENCIL_SCALE_ALL = 19;

    /** Highest gizmo handle id; form-part stencil ids begin right after it. */
    public final static int STENCIL_MAX = STENCIL_SCALE_ALL;

    /** Radius of the view-plane ring relative to the per-axis rings. */

    /** Move/scale handles shrink so they nest inside the rotation rings. */
    private final static float INNER_SCALE = 0.6F;

    /** How much a ring is allowed to reach past the sphere's silhouette so a
     *  ring seen face-on still draws in full. {@code 0} would cut every ring
     *  dead on the silhouette (a face-on ring, sitting exactly on it, would
     *  flicker to half); a small value keeps face-on rings whole while a tilted
     *  ring's far half still ends right at the silhouette. */

    /** Angular resolution used to find a ring's camera-facing (visible) arc. */

    /** Half-size of the scale handle's end cube, in gizmo-local units (× axes scale × thickness).
     *  Based on scale/thickness rather than the per-pass line offset, so the cube is the same
     *  size in the visual and stencil passes and its hitbox matches the drawn cube exactly. */
    /** Axis bar length before the scale and ring-nesting factors, in gizmo-local units. */
    private final static float AXIS_SIZE = 0.25F;

    /** Half-thickness of the axis bars before the scale and thickness settings. */
    private final static float AXIS_OFFSET = 0.008F;

    private final static float SCALE_CUBE_HALF = 0.032F;

    /** Half-size of the centre cube shared by the screen-space (view-plane) translate
     *  handle and the uniform (three-axis) scale handle, in gizmo-local units
     *  (× axes scale × thickness). Deliberately large so the centre reads as an easy
     *  grab target. Like {@link #SCALE_CUBE_HALF} it is offset-independent so the visual
     *  and stencil passes match and the hitbox lines up with the drawn cube. */
    private final static float SCREEN_CUBE_HALF = 0.03F;

    public final static Gizmo INSTANCE = new Gizmo();

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

    /** The rings, the view ring and the sphere, with their settings-driven geometry cache. */
    private final GizmoRings rings = new GizmoRings();
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
     *  {@link #getDistanceScale} otherwise keeps the gizmo a constant fraction
     *  of its viewport, so it shrinks in a small preview (the film) versus a
     *  full-screen editor (forms); this factor makes it a constant fraction of the
     *  window instead, i.e. the same on-screen size in every editor. Each viewport
     *  sets it via {@link #setViewportScale} before BOTH its visual and stencil
     *  pass so the drawn gizmo and its pick hitbox scale together. */
    private float viewportScale = 1F;

    /** What the edited target can actually accept this frame ({@link HandleMask}).
     *  Captured together with the render matrix, because both draw passes run
     *  later, in the UI pass, and must be laid out from the same description as
     *  the world pass that placed the gizmo. Reset to {@link HandleMask#ALL} by
     *  the plain capture calls, so a restricted target cannot leak its mask into
     *  the next editor's gizmo. */
    private HandleMask mask = HandleMask.ALL;

    private Gizmo()
    {}

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

    public boolean isSphereInteractive()
    {
        if (!BBSSettings.gizmos.get() || !Element.SPHERE.isVisible() || !this.mask.allows(Op.TRACKBALL))
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

    /** World-space radius of the rotate sphere as the CAMERA sizes it ({@code 0} until
     *  rendered) — what a camera-space ray has to hit to grab the ball the user sees.
     *  {@link GizmoLens} shrinks the drawn sphere by its own zoom on top of this; only
     *  the screen helpers, which project through the lens, put that back. */
    public float getSphereWorldRadius()
    {
        return this.hasLastRenderMatrix ? this.lastSphereLocalRadius : 0F;
    }

    /**
     * Clip-space chain the gizmo is really drawn with, so what the screen helpers
     * measure is what the user sees: the gizmo's own lens ({@link GizmoLens}) when
     * it is on this frame, and the camera's plain projection when it is not — the
     * inactive lens is the identity swap, so this is one path, not two.
     *
     * <p>The origin projects onto the same pixel either way (the lens is built to
     * put it there), so the hover centre is unchanged; the sphere's radius is not,
     * which is exactly why the pick disc has to be measured through the lens too.
     */
    private Matrix4f lensMvp(Matrix4f cameraProjection, GizmoLens lens)
    {
        lens.set(cameraProjection, this.lastRenderMatrix);

        return new Matrix4f(lens.projection).mul(lens.viewDelta).mul(this.lastRenderMatrix);
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

        Matrix4f mvp = this.lensMvp(projection, new GizmoLens());
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
     * Effective pixel radius of the rotation sphere on screen, so the
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

        GizmoLens lens = new GizmoLens();
        Matrix4f mvp = this.lensMvp(projection, lens);
        /* The stored radius is the camera-sized one; the drawn sphere is that shrunk
         * by the lens, so put the shrink back before projecting through it. */
        float r = this.lastSphereLocalRadius * lens.scale;
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
    /* What the hover mask currently holds, so a still sphere is not re-drawn every hovered
     * frame — the mask used to be a window-sized clear plus a full-viewport composite per
     * frame; now it is the sphere's own rectangle, re-drawn only when that moved. */
    private final Matrix4f lastMaskMatrix = new Matrix4f();
    private final Matrix4f lastMaskProjection = new Matrix4f();
    private int lastMaskX;
    private int lastMaskY;
    private int lastMaskW;
    private int lastMaskH;
    private boolean maskValid;

    public void renderSphereHighlight(UIContext context, Matrix4f projection, Area area)
    {
        if (!this.sphereHovered || !this.hasLastSphereMatrix || !this.isSphereInteractive()
            || !UIBaseMenu.shouldRenderAxes() || projection == null || area == null)
        {
            return;
        }

        /* The highlight only ever lights the sphere's own footprint, so both the mask and the
         * composite live in that footprint's rectangle rather than the whole viewport. */
        Vector2f center = new Vector2f();

        if (!this.computeScreenCenter(projection, area.x, area.y, area.w, area.h, center))
        {
            return;
        }

        float radius = this.computeScreenRadius(projection, area.x, area.y, area.w, area.h);

        if (radius <= 0F)
        {
            return;
        }

        int margin = 4;
        int rectX = Math.max(area.x, (int) Math.floor(center.x - radius) - margin);
        int rectY = Math.max(area.y, (int) Math.floor(center.y - radius) - margin);
        int rectEndX = Math.min(area.ex(), (int) Math.ceil(center.x + radius) + margin);
        int rectEndY = Math.min(area.ey(), (int) Math.ceil(center.y + radius) + margin);
        int rw = rectEndX - rectX;
        int rh = rectEndY - rectY;

        if (rw <= 0 || rh <= 0)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        float pixelScale = mc.getWindow().getFramebufferWidth() / (float) Math.max(1, context.menu.width);
        int pw = Math.max(1, Math.min(512, Math.round(rw * pixelScale)));
        int ph = Math.max(1, Math.min(512, Math.round(rh * pixelScale)));
        float scaleX = pw / (float) rw;
        float scaleY = ph / (float) rh;

        this.sphereHighlight.setup(Link.bbs("gizmo_sphere_highlight"));

        Texture texture = this.sphereHighlight.getFramebuffer().getMainTexture();

        /* Grow-only, so a pixel of rectangle jitter doesn't reallocate the texture per frame. */
        if (texture.width < pw || texture.height < ph)
        {
            this.sphereHighlight.resize(Math.max(texture.width, pw), Math.max(texture.height, ph));
        }

        boolean moved = !this.maskValid
            || this.lastMaskX != rectX || this.lastMaskY != rectY
            || this.lastMaskW != pw || this.lastMaskH != ph
            || !this.lastMaskMatrix.equals(this.lastSphereMatrix)
            || !this.lastMaskProjection.equals(projection);

        if (moved)
        {
            context.batcher.flush();

            boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

            if (scissor)
            {
                GlStateManager._disableScissorTest();
            }

            int[] previousViewport = UIUtils.currentViewport();

            this.sphereHighlight.getFramebuffer().bind();
            this.sphereHighlight.getFramebuffer().clear();

            /* The sphere projects in the viewport's NDC; the viewport is oversized and offset so
             * the rectangle's slice of it lands on the mask (GUI y runs down, GL y runs up). */
            GL11.glViewport(
                Math.round(-(rectX - area.x) * scaleX),
                Math.round(-(area.ey() - rectY - rh) * scaleY),
                Math.round(area.w * scaleX),
                Math.round(area.h * scaleY)
            );

            /* The sphere matrix was captured with the lens already applied to it, so the
             * mask has to be projected through the lens as well or it lands somewhere
             * else entirely. An inactive lens hands the camera projection straight back. */
            GizmoLens lens = new GizmoLens();

            lens.set(projection, this.lastRenderMatrix);

            RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(STENCIL_TRACKBALL / 255F, 0F, 0F, 1F);
            this.rings.drawSphere(this.lastSphereMatrix, lens.projection);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            RenderSystem.enableDepthTest();

            this.sphereHighlight.unbind();

            /* beginWrite(false) + an explicitly saved viewport: the "main" framebuffer
             * is the video one while a film renders, so letting it set the viewport
             * would resize the UI's. */
            mc.getFramebuffer().beginWrite(false);
            UIUtils.restoreViewport(previousViewport);

            if (scissor)
            {
                GlStateManager._enableScissorTest();
            }

            this.lastMaskMatrix.set(this.lastSphereMatrix);
            this.lastMaskProjection.set(projection);
            this.lastMaskX = rectX;
            this.lastMaskY = rectY;
            this.lastMaskW = pw;
            this.lastMaskH = ph;
            this.maskValid = true;
        }

        ShaderProgram previewProgram = BBSShaders.getPickerPreviewProgram();
        GlUniform target = previewProgram.getUniform("Target");

        if (target != null)
        {
            target.set(STENCIL_TRACKBALL);
        }

        GlUniform highlight = previewProgram.getUniform("HighlightColor");

        if (highlight != null)
        {
            int color = BBSSettings.stencilHighlightColor.get();

            highlight.set(Colors.getR(color), Colors.getG(color), Colors.getB(color), Colors.getA(color));
        }

        RenderSystem.enableBlend();
        context.batcher.texturedBox(BBSShaders::getPickerPreviewProgram, texture.id, Colors.WHITE, rectX, rectY, rw, rh, 0, ph, pw, 0, texture.width, texture.height);
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

        /* The mask already keeps a forbidden handle out of both draw passes, so a pick
         * can't name one; the hotkey walk and any programmatic start bypass the stencil
         * though, so the refusal lives here too. */
        if (handle == null || !this.mask.allows(handle))
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
                    if (Element.SPHERE.isVisible()) transform.enableSphereRotate(drag);
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

    /** The transform a gesture is currently running on, or {@code null}. Its owner may
     *  well be off screen (the replay-root gizmo has no fields at all), which is why
     *  {@link GizmoInteraction#update} drives it from here rather than from a render. */
    public UIPropTransform getTrackedTransform()
    {
        return this.currentTransform;
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
        this.render(stack, HandleMask.ALL);
    }

    public void render(MatrixStack stack, HandleMask mask)
    {
        if (BBSRendering.isIrisShadowPass())
        {
            return;
        }

        this.mask = mask == null ? HandleMask.ALL : mask;

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
        this.captureVisual(stack, HandleMask.ALL);
    }

    public void captureVisual(MatrixStack stack, HandleMask mask)
    {
        if (BBSRendering.isIrisShadowPass())
        {
            return;
        }

        this.mask = mask == null ? HandleMask.ALL : mask;

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
     * {@link #getDistanceScale} reads it back from {@link RenderSystem} to
     * keep the gizmo a constant on-screen size.
     */
    public void renderInterface(UIContext context, Matrix4f projection, Area area)
    {
        if (BBSRendering.isIrisShadowPass() || !this.hasLastRenderMatrix
            || context == null || projection == null || area == null)
        {
            return;
        }

        this.setViewportScale(context.menu.height / (float) area.h);

        context.batcher.flush();

        MatrixStackUtils.cacheMatrices();
        RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_Z);

        /* Map the UI area to a framebuffer-pixel viewport, exactly as the form
         * editor's model pass does, so the gizmo renders into the preview and is
         * clipped to it by the view frustum. */
        int[] previousViewport = UIUtils.currentViewport();

        UIUtils.viewportArea(area);

        MatrixStack stack = new MatrixStack();
        MatrixStackUtils.multiply(stack, this.lastRenderMatrix);

        RenderSystem.disableDepthTest();
        this.drawGizmo(stack);
        RenderSystem.enableDepthTest();

        UIUtils.restoreViewport(previousViewport);
        MatrixStackUtils.restoreMatrices();

        /* Leave the depth state the UI expects after a 3D interlude (always-pass),
         * the same exit state as the form editor's model pass. */
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
    }

    private void drawGizmo(MatrixStack stack)
    {
        this.applyBakedRotation(stack);

        /* Read before the lens goes in: the distance scale takes the SCENE's angle
         * (that is what it compensates), and the lens then rescales by the ratio of
         * the two, which keeps the on-screen size put in both settings modes. */
        float cameraScale = this.getDistanceScale(stack);

        /* The lens rewrites this entry's model-view in place; keep the camera's
         * copy underneath it for the constraint guide, which is drawn without it. */
        stack.push();

        GizmoLens lens = new GizmoLens();
        LensSwap swap = this.applyLens(stack, lens);
        float distanceScale = cameraScale * lens.scale;

        stack.push();
        this.applyViewShear(stack, lens);
        stack.scale(distanceScale, distanceScale, distanceScale);

        if (BBSSettings.gizmos.get())
        {
            /* Cache the sphere's world radius (in {@link #lastRenderMatrix}'s
             * coordinate frame) so {@link #computeScreenRadius} can report the real
             * on-screen pixel size for hover/pick distance checks.
             *
             * Stored WITHOUT the lens's shrink, i.e. the radius the sphere would have
             * had under the camera. The trackball drag intersects it with a camera ray
             * ({@code ArcballDrag}), which would otherwise grab a ball a fifth of the
             * drawn one at a wide FOV; the screen helpers put the lens back on when
             * they project it. */
            this.lastSphereLocalRadius = 0.22F * BBSSettings.axesScale.get() * cameraScale;

            this.lastSphereMatrix.set(modelView(stack));
            this.hasLastSphereMatrix = true;
            this.drawOccludedGizmo(stack);
        }
        else
        {
            Draw.coolerAxes(stack, 0.25F, 0.008F);
        }

        stack.pop();

        this.restoreLens(swap);
        stack.pop();

        /* Deliberately outside the shear AND outside the lens: the constraint guide is
         * a world-space line showing the axis the drag actually slides along, and that
         * axis comes from {@link GizmoDrag#frameBasis} — the unsheared camera frame the
         * drag itself solves in. The lens is exact only at the gizmo's origin; a line
         * 10000 blocks long runs off to a different vanishing point through it, and
         * since the lens is rebuilt from the gizmo's position every frame, the guide
         * swung as the drag moved the gizmo. Drawn by the camera it is pinned to the
         * axis the model really slides along. */
        this.drawInfiniteLine(stack);
    }

    /**
     * Swap the gizmo's own lens in for the scene camera's, for the duration of one
     * draw pass: the projection on {@link RenderSystem} and the pass's own copy of
     * the gizmo's model-view, which the view swing is prepended to.
     *
     * <p>Both draw passes take it, so the pick stencil keeps matching the visual
     * pixel for pixel; {@link #lastRenderMatrix} is left alone, so the gizmo's world
     * axes and the pick projections still describe the plain camera frame and the
     * drag rebuilds the same lens for itself from them ({@link GizmoDrag#setup}).
     *
     * @return what was displaced, to hand back to {@link #restoreLens}, or
     *         {@code null} when the lens came out inactive and nothing was swapped.
     */
    private LensSwap applyLens(MatrixStack stack, GizmoLens lens)
    {
        LensSwap swap = new LensSwap(new Matrix4f(RenderSystem.getProjectionMatrix()), RenderSystem.getVertexSorting());

        if (!lens.set(swap.projection(), stack.peek().getPositionMatrix()))
        {
            return null;
        }

        RenderSystem.setProjectionMatrix(lens.projection, VertexSorter.BY_Z);

        Matrix4f position = stack.peek().getPositionMatrix();

        position.set(new Matrix4f(lens.viewDelta).mul(position));

        Matrix3f normal = stack.peek().getNormalMatrix();

        normal.set(lens.viewDelta.get3x3(new Matrix3f()).mul(normal));

        return swap;
    }

    /**
     * Undo {@link #applyLens}'s projection swap; {@code null} means it never happened.
     * The world pass draws the gizmo mid-scene, so the sorting the projection was set
     * with goes back too — a lens must not leave the frame it borrowed sorting
     * translucency differently than it found it.
     */
    private void restoreLens(LensSwap swap)
    {
        if (swap != null)
        {
            RenderSystem.setProjectionMatrix(swap.projection(), swap.sorting());
        }
    }

    /** The {@link RenderSystem} projection state one draw pass borrowed for its lens. */
    private record LensSwap(Matrix4f projection, VertexSorter sorting)
    {}

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
     *
     * <p>This is the fallback for a gizmo drawn through the camera's own lens. With
     * {@link GizmoLens} active the camera is swung onto the gizmo instead, which makes
     * the eye ray the frame's own third axis — the shear would have nothing left to
     * correct, and {@link #reorientForSpace} has already handed the frame the swing's
     * inverse so the handles come out exactly square to the screen.
     */
    private void applyViewShear(MatrixStack stack, GizmoLens lens)
    {
        if (this.lastSpace != TransformSpace.VIEW || lens.active)
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
        float opacity = BBSSettings.gizmoOpacity.get();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        GL11.glDepthRange(1D, 1D);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.colorMask(false, false, false, false);
        this.drawAxes(stack);

        GL11.glDepthRange(0D, 1D);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);

        /* The gizmo opacity rides on the shader colour's alpha, which the
         * position_color program multiplies into every vertex — so blend must be
         * on for it to show (at opacity 1 this is just opaque, no change). */
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1F, 1F, 1F, opacity);
        this.drawAxes(stack);

        /* The sweep pie overlays the handles and must not write depth. */
        RenderSystem.depthMask(false);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.setShaderColor(1F, 1F, 1F, opacity);
        GizmoPie.draw(stack, this.currentTransform, this.ringDragGesture());
        RenderSystem.depthMask(true);

        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }

    private float getDistanceScale(MatrixStack stack)
    {
        Vector3f cameraRelative = stack.peek().getPositionMatrix().getTranslation(new Vector3f());
        Matrix4f proj = com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix();
        float fov = proj.m33() == 0 ? (float) (2.0 * Math.atan(1.0 / proj.m11())) : BBSSettings.getFov();

        return BBSSettings.getGizmoDistanceScale(cameraRelative.length(), fov) * this.viewportScale;
    }

    /** The constraint guide: a world-space line along the dragged axis, drawn by the
     *  scene camera (see {@link #drawGizmo}) and outside the gizmo's distance scale. */
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

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        float size = 10000F;
        float t = 0.005F;

        if (debugIndex == STENCIL_X || debugIndex == STENCIL_XZ || debugIndex == STENCIL_XY)
        {
            Draw.fillBox(builder, stack, -size, -t, -t, size, t, t, Colors.RED);
        }
        
        if (debugIndex == STENCIL_Y || debugIndex == STENCIL_XY || debugIndex == STENCIL_ZY)
        {
            Draw.fillBox(builder, stack, -t, -size, -t, t, size, t, Colors.GREEN);
        }
        
        if (debugIndex == STENCIL_Z || debugIndex == STENCIL_XZ || debugIndex == STENCIL_ZY)
        {
            Draw.fillBox(builder, stack, -t, -t, -size, t, t, size, Colors.BLUE);
        }

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        { net.minecraft.client.render.BuiltBuffer __bbsBuilt = builder.endNullable(); if (__bbsBuilt != null) BufferRenderer.drawWithGlobalProgram(__bbsBuilt); }
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }

    /* Cached gizmo geometry is uploaded as VBOs, so unlike the immediate-mode
     * handles (which inherit the global model-view) the cached draws must fold
     * in {@link RenderSystem#getModelViewMatrix()} themselves. In the form editor
     * that matrix is identity (the camera lives in the stack), but in the film
     * editor it carries the world camera, so omitting it left the rings adrift. */
    private static Matrix4f modelView(MatrixStack stack)
    {
        return new Matrix4f(RenderSystem.getModelViewMatrix()).mul(stack.peek().getPositionMatrix());
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
     * rings. With the rings hidden there is nothing to nest inside, so they keep
     * their full (larger) size.
     */
    private float innerScale()
    {
        return Element.ROTATE.isVisible() && this.mask.allows(Op.ROTATE) ? INNER_SCALE : 1F;
    }

    /** Washes a ring colour channel toward flat gray for IK-owned rotations. */
    private static float dimmed(float channel, boolean constrained)
    {
        return constrained ? channel * 0.25F + 0.3F : channel;
    }

    public void renderStencil(MatrixStack stack)
    {
        this.renderStencil(stack, HandleMask.ALL);
    }

    public void renderStencil(MatrixStack stack, HandleMask mask)
    {
        if (BBSRendering.isIrisShadowPass())
        {
            return;
        }

        if (!BBSSettings.gizmos.get())
        {
            return;
        }

        this.mask = mask == null ? HandleMask.ALL : mask;

        stack.push();
        MatrixStackUtils.scaleBack(stack);
        this.captureRenderMatrix(stack);
        this.drawStencilAxes(stack);
        stack.pop();
    }

    /**
     * Draw the gizmo handles as stencil IDs into the currently bound picking
     * framebuffer, from a stack already positioned at the gizmo origin. Shared by
     * the world-pass {@link #renderStencil} and the UI-pass
     * {@link #renderStencilInterface}.
     */
    private void drawStencilAxes(MatrixStack stack)
    {
        this.applyBakedRotation(stack);

        float distanceScale = this.getDistanceScale(stack);
        /* Same lens as the visual pass, or the hitboxes would sit on the handles as
         * the camera sees them and picking would drift with the distance from centre. */
        GizmoLens lens = new GizmoLens();
        LensSwap swap = this.applyLens(stack, lens);

        distanceScale *= lens.scale;

        stack.push();
        this.applyViewShear(stack, lens);
        stack.scale(distanceScale, distanceScale, distanceScale);
        this.drawStencilHandles(stack);
        stack.pop();

        this.restoreLens(swap);
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
    public void renderStencilInterface(UIContext context, Matrix4f projection, Area area)
    {
        if (BBSRendering.isIrisShadowPass() || !this.hasLastRenderMatrix
            || context == null || projection == null || area == null || !BBSSettings.gizmos.get())
        {
            return;
        }

        this.setViewportScale(context.menu.height / (float) area.h);

        MatrixStackUtils.cacheMatrices();
        RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_Z);

        /* Map the UI area to a framebuffer-pixel viewport, exactly as
         * renderInterface does, so the stencil matches the drawn visual pixel for
         * pixel. The pick framebuffer is sized to the window, so the same mapping
         * applies. */
        int[] previousViewport = UIUtils.currentViewport();

        UIUtils.viewportArea(area);

        MatrixStack stack = new MatrixStack();
        MatrixStackUtils.multiply(stack, this.lastRenderMatrix);

        this.drawStencilAxes(stack);

        UIUtils.restoreViewport(previousViewport);
        MatrixStackUtils.restoreMatrices();
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
     * Frame boundary: drop the captured placement, so a gizmo is drawn only where something
     * placed it THIS frame — which is what every reader of it already assumes.
     *
     * <p>The capture used to be set once and never cleared, and this is a singleton shared by
     * every editor. So the film's last bone position survived into the form editor and the
     * model-block panel, which draw from the capture in the UI pass ({@link #renderInterface}):
     * the gizmo appeared at a place belonging to a scene that was no longer on screen. Placing
     * and drawing are always the same frame — the world pass captures, the UI pass draws — so
     * forgetting at the boundary costs a live gizmo nothing.
     */
    public void forgetPlacement()
    {
        this.hasLastRenderMatrix = false;
        this.hasLastSphereMatrix = false;
        this.lastSphereLocalRadius = 0F;
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
        Matrix3f basis = GizmoDrag.stackBasisForSpace(space, cameraView, globalAxes);

        /* VIEW means "square to the screen", and with the lens on, the screen is the
         * lens's, not the camera's: pre-cancel its view swing here so the draw passes
         * multiply it back out and the handles land exactly axis-aligned on screen.
         * The lens's own predicate decides, so the frame and the draw agree on whether
         * this frame has a lens — and the swing is read off the placement's translation,
         * which is the gizmo's view-space position, the same value the lens builds from. */
        if (space == TransformSpace.VIEW && GizmoLens.canFrame(RenderSystem.getProjectionMatrix(), translation))
        {
            Matrix4f delta = new Matrix4f();

            if (GizmoLens.viewDelta(translation, delta))
            {
                basis = delta.get3x3(new Matrix3f()).transpose().mul(basis);
            }
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
     * Receives the gizmo's box-shaped elements — bars, plane quads, the centre cubes and the
     * scale cubes. The geometry is written once, in {@link Gizmo#collectHandles}, and painted
     * twice: the visual pass by the colour it is handed, the pick pass by the handle's id.
     */
    private interface HandleSink
    {
        void box(Handle handle, float x1, float y1, float z1, float x2, float y2, float z2, int color);

        /**
         * The centre cube the pick pass masks the bars with, so a click in the middle of the
         * gizmo lands on nothing rather than on whichever bar runs through it. Comes right
         * after the bars and before anything that overlays the centre, which is why it is a
         * step of the walk and not something the pass draws on its own. The visual pass has
         * no use for it — its own centre cube is decoration, drawn last.
         */
        default void centreMask(float half) {}
    }

    /** The same, for the rotation rings: the three axis ones and the camera-facing view ring. */
    private interface RingSink
    {
        void ring(Handle handle, Axis axis, float radius, float thickness, int color);

        void viewRing(Handle handle, int color);
    }

    /**
     * Walks the rotation rings that should be on screen right now, in draw order.
     *
     * <p>Shared by the visual pass and the pick pass so a ring's radius and thickness — and
     * which rings exist at all — are decided in ONE place. They used to be two copies kept in
     * step by hand, with a comment in the pick pass reminding whoever changed one to change
     * the other; a slip there means clicking a ring that is not where it is drawn.
     */
    private void collectRings(Layout layout, RingSink sink)
    {
        Handle active = layout.active;

        /* The 3D sphere itself is invisible — it only acts as the trackball grab area. Hover
         * feedback is a screen-space glow composited in {@link #renderSphereHighlight}. */

        if (layout.showRings)
        {
            float scale = BBSSettings.axesScale.get();
            float radius = 0.22F * scale;
            float ringThickness = 0.02F * scale * BBSSettings.axesThickness.get();

            /* A target may own only some of the three rotation axes (a replay's root turns
             * about Y and pitches about X, but has nowhere to put roll), so each ring is
             * filtered on its own axis rather than the group as a whole. */
            if (layout.mask.allowsRotateAxis(Axis.Z) && (active == null || active == Handle.ROTATE_Z)) sink.ring(Handle.ROTATE_Z, Axis.Z, radius, ringThickness, Colors.BLUE);
            if (layout.mask.allowsRotateAxis(Axis.X) && (active == null || active == Handle.ROTATE_X)) sink.ring(Handle.ROTATE_X, Axis.X, radius, ringThickness, Colors.RED);
            if (layout.mask.allowsRotateAxis(Axis.Y) && (active == null || active == Handle.ROTATE_Y)) sink.ring(Handle.ROTATE_Y, Axis.Y, radius, ringThickness, Colors.GREEN);
        }

        /* The screen-space (billboard) view-rotation ring hides on its own element, not with
         * the axis rings — the two are separate settings. */
        if (layout.showViewRing)
        {
            sink.viewRing(Handle.VIEW, Colors.LIGHTEST_GRAY);
        }
    }

    /**
     * Walks the move/scale elements that should be on screen right now, in draw order. Same
     * bargain as {@link #collectRings}: one description of where every handle sits, so the
     * drawn gizmo and its pick hitboxes cannot drift apart.
     */
    private void collectHandles(Layout layout, HandleSink sink)
    {
        Handle active = layout.active;
        boolean showMove = layout.showMove;
        boolean showScale = layout.showScale;
        float axisSize = layout.axisSize;
        float axisOffset = layout.axisOffset;
        float scale = layout.scale;
        float thickness = layout.thickness;
        float planeSize = layout.planeSize;

        /* The bars and planes read as move when move is on screen and as scale only when
         * scale stands alone — so a grab of that element drives what its colour promised,
         * and move and scale never share an id under the cursor. */
        Handle barX = showMove ? Handle.MOVE_X : Handle.SCALE_X;
        Handle barY = showMove ? Handle.MOVE_Y : Handle.SCALE_Y;
        Handle barZ = showMove ? Handle.MOVE_Z : Handle.SCALE_Z;
        Handle planeXZ = showMove ? Handle.MOVE_XZ : Handle.SCALE_XZ;
        Handle planeXY = showMove ? Handle.MOVE_XY : Handle.SCALE_XY;
        Handle planeZY = showMove ? Handle.MOVE_ZY : Handle.SCALE_ZY;

        if (active == null || active == barX) sink.box(barX, 0, -axisOffset, -axisOffset, axisSize, axisOffset, axisOffset, Colors.RED);
        if (active == null || active == barY) sink.box(barY, -axisOffset, 0, -axisOffset, axisOffset, axisSize, axisOffset, Colors.GREEN);
        if (active == null || active == barZ) sink.box(barZ, -axisOffset, -axisOffset, 0, axisOffset, axisOffset, axisSize, Colors.BLUE);

        sink.centreMask(axisOffset);

        /* Screen-space (view-plane) translate handle: a white cube at the centre, twice the
         * bars' thickness. Drawn before the planes so they overlay it, and after the rotation
         * rings so it stays visible when they are on screen too. */
        if (showMove && layout.mask.allows(Op.SCREEN) && (active == null || active == Handle.SCREEN))
        {
            float screenHalf = SCREEN_CUBE_HALF * scale * thickness;

            sink.box(Handle.SCREEN, -screenHalf, -screenHalf, -screenHalf, screenHalf, screenHalf, screenHalf, Colors.WHITE);
        }

        /* Uniform-scale handle: the same centre cube, shown only when move isn't (with
         * both on screen the centre is the translate handle), so the pick is never
         * ambiguous between the two. */
        if (showScale && !showMove && layout.mask.allows(Op.SCALE_ALL) && (active == null || active == Handle.SCALE_ALL))
        {
            float scaleAllHalf = SCREEN_CUBE_HALF * scale * thickness;

            sink.box(Handle.SCALE_ALL, -scaleAllHalf, -scaleAllHalf, -scaleAllHalf, scaleAllHalf, scaleAllHalf, scaleAllHalf, Colors.WHITE);
        }

        /* The plane quad's footprint is a fraction of the axis length, independent of
         * axesThickness — thickness only fattens the bars and the flat slab depth, not how
         * big the two-axis plane reads. Its own setting grows it outwards from a fixed start,
         * so a bigger plane is easier to grab without walking away from the origin. */
        float planeStart = axisSize * 0.2F;
        float planeEnd = planeStart + axisSize * 0.2F * planeSize;
        float planeThickness = axisOffset * 0.5F;

        if (active == null || active == planeXZ) sink.box(planeXZ, planeStart, -planeThickness, planeStart, planeEnd, planeThickness, planeEnd, Colors.PLANE_XZ);
        if (active == null || active == planeXY) sink.box(planeXY, planeStart, planeStart, -planeThickness, planeEnd, planeEnd, planeThickness, Colors.PLANE_XY);
        if (active == null || active == planeZY) sink.box(planeZY, -planeThickness, planeStart, planeStart, planeThickness, planeEnd, planeEnd, Colors.PLANE_ZY);

        if (showScale)
        {
            float cubeHalf = SCALE_CUBE_HALF * scale * thickness;

            if (active == null || active == Handle.SCALE_X) sink.box(Handle.SCALE_X, axisSize - cubeHalf, -cubeHalf, -cubeHalf, axisSize + cubeHalf, cubeHalf, cubeHalf, Colors.RED);
            if (active == null || active == Handle.SCALE_Y) sink.box(Handle.SCALE_Y, -cubeHalf, axisSize - cubeHalf, -cubeHalf, cubeHalf, axisSize + cubeHalf, cubeHalf, Colors.GREEN);
            if (active == null || active == Handle.SCALE_Z) sink.box(Handle.SCALE_Z, -cubeHalf, -cubeHalf, axisSize - cubeHalf, cubeHalf, cubeHalf, axisSize + cubeHalf, Colors.BLUE);
        }
    }

    /**
     * What the gizmo shows this frame and at what size: which groups are on screen, which
     * handle a running drag filters everything down to, and the settings-resolved dimensions.
     * Taken once and used by both passes, so the pick can never be laid out from different
     * numbers than the drawing — that used to be six lines copied into each.
     */
    private final class Layout
    {
        final Handle active = Gizmo.this.activeDragHandle();

        /* Settings say what the user wants to see, the mask says what the target can
         * accept at all — a handle needs both to reach the screen and the cursor. */
        final HandleMask mask = Gizmo.this.mask;

        final boolean showRings = Element.ROTATE.isVisible() && mask.allows(Op.ROTATE) && (this.active == null || this.active.op == Op.ROTATE);
        final boolean showViewRing = Element.VIEW_ROTATE.isVisible() && mask.allows(Op.VIEW) && (this.active == null || this.active.op == Op.VIEW);

        final boolean showMove = Element.TRANSLATE.isVisible() && mask.allows(Op.MOVE) && (this.active == null || this.active.op == Op.MOVE || this.active.op == Op.SCREEN);
        final boolean showScale = Element.SCALE.isVisible() && mask.allows(Op.SCALE) && (this.active == null || this.active.op == Op.SCALE || this.active.op == Op.SCALE_ALL);
        final boolean showRotate = this.showRings || this.showViewRing;

        final float scale = BBSSettings.axesScale.get();
        final float thickness = BBSSettings.axesThickness.get();

        final float planeSize = BBSSettings.gizmoPlaneSize.get();

        final float axisSize = AXIS_SIZE * this.scale * Gizmo.this.innerScale();
        final float axisOffset = AXIS_OFFSET * this.scale * this.thickness;

        boolean showsBoxes()
        {
            return this.showMove || this.showScale;
        }
    }

    /** Draws the gizmo for the eye: the walks above, painted in the handles' own colours. */
    private void drawAxes(MatrixStack stack)
    {
        Layout layout = new Layout();

        Handle active = layout.active;
        float axisOffset = layout.axisOffset;

        boolean building = false;
        BufferBuilder builder = null;

        if (layout.showRotate)
        {
            /* IK owns this bone's rotation: the rings render washed-out as the visible "not
             * yours to turn" cue, matching the rotation strategies' refusal to start there
             * (the pads still edit the FK channels). */
            boolean constrained = this.currentTransform != null && this.currentTransform.isRotationConstrained();

            /* Depth state is owned by the caller ({@link #drawOccludedGizmo}) so the handles
             * sort against each other. */
            this.collectRings(layout, new RingSink()
            {
                @Override
                public void ring(Handle handle, Axis axis, float radius, float ringThickness, int color)
                {
                    Gizmo.this.rings.drawOccluded(stack, axis, radius, ringThickness,
                        dimmed(Colors.getR(color), constrained),
                        dimmed(Colors.getG(color), constrained),
                        dimmed(Colors.getB(color), constrained));
                }

                @Override
                public void viewRing(Handle handle, int color)
                {
                    /* This VBO ring sets the shader colour itself, so the opacity modulator
                     * doesn't reach it — fold it into the alpha here instead. */
                    float alpha = Colors.getA(color) * BBSSettings.gizmoOpacity.get() * (constrained ? 0.35F : 1F);

                    Gizmo.this.rings.drawBillboard(stack, Colors.getR(color), Colors.getG(color), Colors.getB(color), alpha);
                }
            });
        }

        if (layout.showsBoxes())
        {
            builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            building = true;

            BufferBuilder boxes = builder;

            this.collectHandles(layout, (handle, x1, y1, z1, x2, y2, z2, color) ->
                Draw.fillBox(boxes, stack, x1, y1, z1, x2, y2, z2, color));
        }

        /* The centre cube is decoration, not a handle, so any filtered drag hides it — but
         * nothing else does. With every element switched off it is all that is left, and it
         * has to be: the gizmo's origin is where the selection is, and losing that marker
         * means losing sight of what is being edited.
         *
         * Standing alone it takes the size the centre normally reads at — the screen-translate
         * cube's. At the bar thickness it would be a speck: that size is chosen to sit in the
         * crook of three axis bars, and with the bars gone there is nothing to be small against. */
        if (active == null)
        {
            float centreHalf = layout.showsBoxes() || layout.showRotate
                ? axisOffset
                : SCREEN_CUBE_HALF * layout.scale * layout.thickness;

            if (!building)
            {
                builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
                building = true;
            }

            Draw.fillBox(builder, stack, -centreHalf, -centreHalf, -centreHalf, centreHalf, centreHalf, centreHalf, Colors.WHITE);
        }

        if (building)
        {
            /* Depth func/mask is owned by {@link #drawOccludedGizmo} so bars, planes and cubes
             * depth-sort against the rings and each other. Re-assert the opacity modulator:
             * the billboard view ring above sets the shader colour itself and leaves it opaque. */
            RenderSystem.setShaderColor(1F, 1F, 1F, BBSSettings.gizmoOpacity.get());
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            BufferRenderer.drawWithGlobalProgram(builder.end());
        }
    }

    /**
     * Draws the same gizmo for the picker: the same walks, painted with each handle's stencil
     * id instead of its colour, so what the cursor lands on is by construction what the eye
     * sees. Ids go in the red channel, the way the pick buffer is read back.
     */
    private void drawStencilHandles(MatrixStack stack)
    {
        Layout layout = new Layout();

        RenderSystem.disableDepthTest();

        if (layout.showRotate)
        {
            this.collectRings(layout, new RingSink()
            {
                @Override
                public void ring(Handle handle, Axis axis, float radius, float ringThickness, int color)
                {
                    Gizmo.this.rings.drawOccluded(stack, axis, radius, ringThickness, handle.index / 255F, 0F, 0F);
                }

                @Override
                public void viewRing(Handle handle, int color)
                {
                    Gizmo.this.rings.drawBillboard(stack, handle.index / 255F, 0F, 0F, 1F);
                }
            });
        }

        if (layout.showsBoxes())
        {
            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

            this.collectHandles(layout, new HandleSink()
            {
                @Override
                public void box(Handle handle, float x1, float y1, float z1, float x2, float y2, float z2, int color)
                {
                    Draw.fillBox(builder, stack, x1, y1, z1, x2, y2, z2, handle.index / 255F, 0F, 0F);
                }

                @Override
                public void centreMask(float half)
                {
                    Draw.fillBox(builder, stack, -half, -half, -half, half, half, half, 0F, 0F, 0F);
                }
            });

            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            { net.minecraft.client.render.BuiltBuffer __bbsBuilt = builder.endNullable(); if (__bbsBuilt != null) BufferRenderer.drawWithGlobalProgram(__bbsBuilt); }
        }

        RenderSystem.enableDepthTest();
    }

    /**
     * The parts the gizmo is made of. It always carries all of them — there are no
     * display modes any more — and each one's setting decides only whether it reaches
     * the screen and the cursor. Both draw passes read the same flags, so a hidden
     * element is out of the pick stencil too and cannot be grabbed by mistake.
     *
     * <p>Deliberately NOT read by the G/S/R hotkey walk: the keyboard has its own
     * cycle setting ({@code translate/scale/rotate_hotkey_order}), and hiding, say,
     * every rotation element would otherwise leave no way to rotate by key at all.
     */
    public static enum Element
    {
        /** Move: the axis bars, the two-axis planes and the screen-plane centre cube. */
        TRANSLATE(() -> BBSSettings.gizmoShowTranslate),
        /** Scale: the cubes at the ends of the axes and the uniform-scale centre cube. */
        SCALE(() -> BBSSettings.gizmoShowScale),
        /** The three axis rotation rings. */
        ROTATE(() -> BBSSettings.gizmoShowRotate),
        /** The camera-facing (billboard) rotation ring. */
        VIEW_ROTATE(() -> BBSSettings.gizmoShowViewRotate),
        /** The free-rotation sphere in the middle (trackball / arcball). */
        SPHERE(() -> BBSSettings.gizmoShowSphere);

        /* A supplier rather than the value itself: this enum may well be initialised
         * before BBSSettings#register has filled its fields in. */
        private final Supplier<ValueBoolean> setting;

        Element(Supplier<ValueBoolean> setting)
        {
            this.setting = setting;
        }

        public boolean isVisible()
        {
            ValueBoolean value = this.setting.get();

            return value == null || value.get();
        }
    }

    /**
     * Which handles the edited target can accept at all, as opposed to which ones the
     * user chose to see ({@link Element}). The two are separate questions: a setting
     * hides a handle the target could have driven, a mask drops one the target has
     * nowhere to write &mdash; a replay's root has no scale and no roll, so those
     * handles must not be drawn, must not reach the pick stencil and must not start a
     * gesture, whatever the settings say.
     *
     * <p>Passed to the capture calls rather than kept as a mode, so it travels with the
     * frame it describes and both passes read the same one.
     */
    public static final class HandleMask
    {
        /** No restriction &mdash; a full {@link mchorse.bbs_mod.utils.pose.Transform} target. */
        public static final HandleMask ALL = new HandleMask(EnumSet.allOf(Op.class), EnumSet.allOf(Axis.class));

        private final EnumSet<Op> ops;
        private final EnumSet<Axis> rotateAxes;

        public static HandleMask of(EnumSet<Op> ops, EnumSet<Axis> rotateAxes)
        {
            return new HandleMask(ops, rotateAxes);
        }

        private HandleMask(EnumSet<Op> ops, EnumSet<Axis> rotateAxes)
        {
            this.ops = EnumSet.copyOf(ops);
            this.rotateAxes = EnumSet.copyOf(rotateAxes);
        }

        public boolean allows(Op op)
        {
            return this.ops.contains(op);
        }

        public boolean allowsRotateAxis(Axis axis)
        {
            return this.rotateAxes.contains(axis);
        }

        /** Whether a picked or hotkeyed handle may start a gesture: its operation must be
         *  allowed, and an axis ring's axis must be too. */
        public boolean allows(Handle handle)
        {
            if (handle == null || !this.allows(handle.op))
            {
                return false;
            }

            return handle.op != Op.ROTATE || this.allowsRotateAxis(handle.axis);
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
     * of these and dispatches the matching transform.
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
