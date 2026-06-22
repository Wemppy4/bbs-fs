package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

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

    /** Highest gizmo handle id; form-part stencil ids begin right after it. */
    public final static int STENCIL_MAX = STENCIL_SCREEN;

    /** Radius of the view-plane ring relative to the per-axis rings. */
    private final static float VIEW_RING_SCALE = 1.2F;

    /** Move/scale handles shrink inside the rotation rings in combined mode. */
    private final static float COMBINED_INNER_SCALE = 0.6F;

    /** Half-size of the scale handle's end cube, in gizmo-local units (× axes scale × thickness).
     *  Based on scale/thickness rather than the per-pass line offset, so the cube is the same
     *  size in the visual and stencil passes and its hitbox matches the drawn cube exactly. */
    private final static float SCALE_CUBE_HALF = 0.032F;

    /** Half-size of the screen-space (centre) translate cube, in gizmo-local units
     *  (× axes scale × thickness). Twice the translate bars' half-thickness (the visual
     *  bar offset is {@code 0.008}), so the cube reads as a grabbable handle. Like
     *  {@link #SCALE_CUBE_HALF} it is offset-independent so the visual and stencil
     *  passes match and the hitbox lines up with the drawn cube. */
    private final static float SCREEN_CUBE_HALF = 0.016F;

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
     * orientation captured at grab time, so the ring stays put (only the pie
     * sweeps) instead of writhing as the live rotation is recomposed from euler
     * angles each frame — most visible in local/world space. */
    private final Matrix4f bakedRotationMatrix = new Matrix4f();
    private boolean hasBakedRotation;

    /* VBO caching for rotation rings to save resources */
    private VertexBuffer rotateRingVbo;
    private VertexBuffer rotateStencilRingVbo;
    private VertexBuffer rotateSphereVbo;
    private float lastScale = -1F;
    private float lastThickness = -1F;
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
        if (!this.sphereHovered || !this.hasLastSphereMatrix || !this.isSphereInteractive()
            || !UIBaseMenu.shouldRenderAxes() || this.rotateSphereVbo == null || projection == null || area == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        this.sphereHighlight.setup(Link.bbs("gizmo_sphere_highlight"));

        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        Texture texture = this.sphereHighlight.getFramebuffer().getMainTexture();

        if (texture.width != w || texture.height != h)
        {
            this.sphereHighlight.resize(w, h);
        }

        this.sphereHighlight.apply();

        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(STENCIL_TRACKBALL / 255F, 0F, 0F, 1F);
        this.rotateSphereVbo.bind();
        this.rotateSphereVbo.draw(this.lastSphereMatrix, projection, GameRenderer.getPositionColorProgram());
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.enableDepthTest();

        this.sphereHighlight.unbind();
        mc.getFramebuffer().beginWrite(true);

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
        context.batcher.texturedBox(BBSShaders::getPickerPreviewProgram, texture.id, Colors.WHITE, area.x, area.y, area.w, area.h, 0, texture.height, texture.width, 0, texture.width, texture.height);
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
                    transform.enableMode(handle.op.modeOrdinal, handle.axis, handle.axis2, drag);
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
            this.hasBakedRotation = false;

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
            || context == null || projection == null || area == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        this.setViewportScale(context.menu.height / (float) area.h);

        context.batcher.flush();

        MatrixStackUtils.cacheMatrices();
        RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_Z);

        /* Map the UI area to a framebuffer-pixel viewport, exactly as the form
         * editor's model pass does, so the gizmo renders into the preview and is
         * clipped to it by the view frustum. */
        float rx = (float) Math.round(mc.getWindow().getWidth() / (double) context.menu.width);
        float ry = (float) Math.round(mc.getWindow().getHeight() / (double) context.menu.height);
        float size = BBSModClient.getOriginalFramebufferScale();
        int vx = (int) (area.x * rx);
        int vy = (int) (mc.getWindow().getHeight() - (area.y + area.h) * ry);
        int vw = (int) (area.w * rx);
        int vh = (int) (area.h * ry);

        RenderSystem.viewport((int) (vx * size), (int) (vy * size), (int) (vw * size), (int) (vh * size));

        MatrixStack stack = new MatrixStack();
        MatrixStackUtils.multiply(stack, this.lastRenderMatrix);

        RenderSystem.disableDepthTest();
        this.drawGizmo(stack);
        RenderSystem.enableDepthTest();

        RenderSystem.viewport(0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
        MatrixStackUtils.restoreMatrices();

        /* Leave the depth state the UI expects after a 3D interlude (always-pass),
         * the same exit state as the form editor's model pass. */
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
    }

    private void drawGizmo(MatrixStack stack)
    {
        this.applyBakedRotation(stack);

        if (BBSSettings.gizmos.get())
        {
            float distanceScale = this.getAxesDistanceScale(stack);

            /* Cache the sphere's effective world radius (in
             * {@link #lastRenderMatrix}'s coordinate frame) so
             * {@link #computeScreenRadius} can report the real on-screen
             * pixel size for hover/pick distance checks. */
            this.lastSphereLocalRadius = 0.22F * BBSSettings.axesScale.get() * distanceScale;

            stack.push();
            stack.scale(distanceScale, distanceScale, distanceScale);
            this.lastSphereMatrix.set(stack.peek().getPositionMatrix());
            this.hasLastSphereMatrix = true;
            this.drawAxes(stack, 0.25F, 0.008F);
            stack.pop();
        }
        else
        {
            float distanceScale = this.getAxesDistanceScale(stack);

            stack.push();
            stack.scale(distanceScale, distanceScale, distanceScale);
            Draw.coolerAxes(stack, 0.25F, 0.008F);
            stack.pop();
        }

        this.drawInfiniteLine(stack);
    }

    private float getAxesDistanceScale(MatrixStack stack)
    {
        Vector3f cameraRelative = stack.peek().getPositionMatrix().getTranslation(new Vector3f());
        Matrix4f proj = com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix();
        float fov = proj.m33() == 0 ? (float) (2.0 * Math.atan(1.0 / proj.m11())) : BBSSettings.getFov();

        return BBSSettings.getAxesDistanceScale(cameraRelative.length(), fov) * this.viewportScale;
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

        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

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
        BufferRenderer.drawWithGlobalProgram(builder.end());
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }

    private void updateVbos()
    {
        float scale = BBSSettings.axesScale.get();
        float thickness = BBSSettings.axesThickness.get();

        if (this.rotateRingVbo == null || scale != this.lastScale || thickness != this.lastThickness)
        {
            if (this.rotateRingVbo != null)
            {
                this.rotateRingVbo.close();
                this.rotateStencilRingVbo.close();
                this.rotateSphereVbo.close();
            }

            this.rotateRingVbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            this.rotateStencilRingVbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            this.rotateSphereVbo = new VertexBuffer(VertexBuffer.Usage.STATIC);

            BufferBuilder builder = Tessellator.getInstance().getBuffer();

            float radius = 0.22F * scale;
            float thicknessRing = 0.02F * scale * thickness;
            float outlinePad = 0.015F * scale * thickness;
            float thicknessStencil = 0.05F * scale * thickness + outlinePad;

            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            Draw.arc3D(builder, new MatrixStack(), Axis.Y, radius, thicknessRing, 1F, 1F, 1F, 0F, 360F);
            this.rotateRingVbo.bind();
            this.rotateRingVbo.upload(builder.end());

            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            Draw.arc3D(builder, new MatrixStack(), Axis.Y, radius, thicknessStencil, 1F, 1F, 1F, 0F, 360F);
            this.rotateStencilRingVbo.bind();
            this.rotateStencilRingVbo.upload(builder.end());

            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            Draw.sphere(builder, new MatrixStack(), radius, 24, 24, 1F, 1F, 1F, 1F);
            this.rotateSphereVbo.bind();
            this.rotateSphereVbo.upload(builder.end());

            VertexBuffer.unbind();

            this.lastScale = scale;
            this.lastThickness = thickness;
        }
    }

    private void drawCachedRing(MatrixStack stack, VertexBuffer vbo, Axis axis, int color)
    {
        float alpha = Colors.getA(color);

        if (alpha <= 0F)
        {
            alpha = 1F;
        }

        this.drawCachedRing(stack, vbo, axis, Colors.getR(color), Colors.getG(color), Colors.getB(color), alpha);
    }

    private void drawCachedRing(MatrixStack stack, VertexBuffer vbo, Axis axis, float r, float g, float b, float a)
    {
        stack.push();
        
        if (axis == Axis.X) stack.multiply(RotationAxis.POSITIVE_Z.rotation(MathUtils.PI / 2F));
        if (axis == Axis.Z) stack.multiply(RotationAxis.POSITIVE_X.rotation(MathUtils.PI / 2F));

        RenderSystem.setShaderColor(r, g, b, a);
        vbo.bind();
        vbo.draw(stack.peek().getPositionMatrix(), RenderSystem.getProjectionMatrix(), GameRenderer.getPositionColorProgram());
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        
        stack.pop();
    }

    private void drawCachedRingBillboard(MatrixStack stack, VertexBuffer vbo, float r, float g, float b, float a)
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

        RenderSystem.setShaderColor(r, g, b, a);
        vbo.bind();
        vbo.draw(stack.peek().getPositionMatrix(), RenderSystem.getProjectionMatrix(), GameRenderer.getPositionColorProgram());
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

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
        Vector3f dragAxisDir = this.currentTransform.getDragAxisDir();

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
        float a = 0.25F;

        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Matrix4f mat = stack.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.disableCull();

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

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

            builder.vertex(mat, 0, 0, 0).color(r, g, b, a).next();
            
            if (sweepDeg > 0)
            {
                builder.vertex(mat, x1, 0, z1).color(r, g, b, a).next();
                builder.vertex(mat, x2, 0, z2).color(r, g, b, a).next();
            }
            else
            {
                builder.vertex(mat, x2, 0, z2).color(r, g, b, a).next();
                builder.vertex(mat, x1, 0, z1).color(r, g, b, a).next();
            }
        }
        
        BufferRenderer.drawWithGlobalProgram(builder.end());

        float lineThickness = 0.005F * scale;
        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        
        float endDeg = startDeg + sweepDeg;
        
        float sx = (float) Math.cos(MathUtils.toRad(startDeg)) * radius;
        float sz = (float) Math.sin(MathUtils.toRad(startDeg)) * radius;
        float ex = (float) Math.cos(MathUtils.toRad(endDeg)) * radius;
        float ez = (float) Math.sin(MathUtils.toRad(endDeg)) * radius;
        
        Vector3f p1 = new Vector3f(-sz, 0, sx).normalize().mul(lineThickness);
        
        builder.vertex(mat, p1.x, 0, p1.z).color(r, g, b, 1F).next();
        builder.vertex(mat, -p1.x, 0, -p1.z).color(r, g, b, 1F).next();
        builder.vertex(mat, sx - p1.x, 0, sz - p1.z).color(r, g, b, 1F).next();
        
        builder.vertex(mat, p1.x, 0, p1.z).color(r, g, b, 1F).next();
        builder.vertex(mat, sx - p1.x, 0, sz - p1.z).color(r, g, b, 1F).next();
        builder.vertex(mat, sx + p1.x, 0, sz + p1.z).color(r, g, b, 1F).next();
        
        Vector3f p2 = new Vector3f(-ez, 0, ex).normalize().mul(lineThickness);
        builder.vertex(mat, p2.x, 0, p2.z).color(r, g, b, 1F).next();
        builder.vertex(mat, -p2.x, 0, -p2.z).color(r, g, b, 1F).next();
        builder.vertex(mat, ex - p2.x, 0, ez - p2.z).color(r, g, b, 1F).next();
        
        builder.vertex(mat, p2.x, 0, p2.z).color(r, g, b, 1F).next();
        builder.vertex(mat, ex - p2.x, 0, ez - p2.z).color(r, g, b, 1F).next();
        builder.vertex(mat, ex + p2.x, 0, ez + p2.z).color(r, g, b, 1F).next();

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableBlend();
        
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

        int op = transform.getMode();
        Axis axis = transform.getAxis();

        if (op == 2)
        {
            if (transform.isSphereRotate()) return Handle.TRACKBALL;
            if (transform.isViewRotate()) return Handle.VIEW;
            if (axis == Axis.X) return Handle.ROTATE_X;
            if (axis == Axis.Y) return Handle.ROTATE_Y;
            if (axis == Axis.Z) return Handle.ROTATE_Z;

            return null;
        }

        if (op == 0 && transform.isScreenTranslate())
        {
            return Handle.SCREEN;
        }

        Op handleOp = op == 1 ? Op.SCALE : Op.MOVE;
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

    private void drawRotateHandles(MatrixStack stack, boolean editing, int activeOp, Handle active)
    {
        this.updateVbos();

        boolean rotating = editing && activeOp == Op.ROTATE.modeOrdinal;
        Axis activeAxis = rotating ? this.currentTransform.getAxis() : null;

        /* The 3D sphere itself is invisible — it only acts as the trackball grab
         * area. Hover feedback is a screen-space glow composited in
         * {@link #renderSphereHighlight}. */

        RenderSystem.depthFunc(GL11.GL_ALWAYS);

        if (!BBSSettings.rotateHideRings.get())
        {
            if (active == null || active == Handle.ROTATE_Z) this.drawCachedRing(stack, this.rotateRingVbo, Axis.Z, Colors.BLUE);
            if (active == null || active == Handle.ROTATE_X) this.drawCachedRing(stack, this.rotateRingVbo, Axis.X, Colors.RED);
            if (active == null || active == Handle.ROTATE_Y) this.drawCachedRing(stack, this.rotateRingVbo, Axis.Y, Colors.GREEN);
        }

        /* The screen-space (billboard) view-rotation ring is intentionally excluded from the
         * "Hide rotation rings" option, so it is always drawn regardless of that setting. */
        if (active == null || active == Handle.VIEW)
        {
            int color = Colors.LIGHTEST_GRAY;

            this.drawCachedRingBillboard(stack, this.rotateRingVbo, Colors.getR(color), Colors.getG(color), Colors.getB(color), Colors.getA(color));
        }

        if (rotating && activeAxis != null)
        {
            this.drawRotatePie(stack, activeAxis);
        }

        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }

    private void drawAxes(MatrixStack stack, float axisSize, float axisOffset)
    {
        float scale = BBSSettings.axesScale.get();
        float thickness = BBSSettings.axesThickness.get();

        boolean editing = this.currentTransform != null && this.currentTransform.isEditing();
        int activeOp = editing ? this.currentTransform.getMode() : -1;
        Handle active = this.activeDragHandle();

        boolean showMove = this.mode.shows(Op.MOVE) && (active == null || active.op == Op.MOVE || active.op == Op.SCREEN);
        boolean showScale = this.mode.shows(Op.SCALE) && (active == null || active.op == Op.SCALE);
        boolean showRotate = this.mode.shows(Op.ROTATE) && (active == null || active.op == Op.ROTATE || active.op == Op.VIEW || active.op == Op.TRACKBALL);

        axisSize *= scale * this.combinedInnerScale();
        axisOffset *= scale * thickness;

        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        boolean building = false;

        if (showRotate)
        {
            this.drawRotateHandles(stack, editing, activeOp, active);
        }

        if (showMove || showScale)
        {
            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
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

            if (active == null || active == barX) Draw.fillBox(builder, stack, 0, -axisOffset, -axisOffset, axisSize, axisOffset, axisOffset, Colors.RED);
            if (active == null || active == barY) Draw.fillBox(builder, stack, -axisOffset, 0, -axisOffset, axisOffset, axisSize, axisOffset, Colors.GREEN);
            if (active == null || active == barZ) Draw.fillBox(builder, stack, -axisOffset, -axisOffset, 0, axisOffset, axisOffset, axisSize, Colors.BLUE);

            /* Screen-space (view-plane) translate handle: a white cube at the centre,
             * twice the bars' thickness. Drawn before the planes so they overlay it,
             * and after the rotation sphere (above) so it stays visible in combined. */
            if (showMove && (active == null || active == Handle.SCREEN))
            {
                float screenHalf = SCREEN_CUBE_HALF * scale * thickness;

                Draw.fillBox(builder, stack, -screenHalf, -screenHalf, -screenHalf, screenHalf, screenHalf, screenHalf, Colors.WHITE);
            }

            float planeStart = axisSize * 0.2F;
            float planeEnd = planeStart + axisSize * 0.4F * thickness;
            float planeThickness = axisOffset * 0.5F;

            if (active == null || active == planeXZ) Draw.fillBox(builder, stack, planeStart, -planeThickness, planeStart, planeEnd, planeThickness, planeEnd, Colors.PLANE_XZ);
            if (active == null || active == planeXY) Draw.fillBox(builder, stack, planeStart, planeStart, -planeThickness, planeEnd, planeEnd, planeThickness, Colors.PLANE_XY);
            if (active == null || active == planeZY) Draw.fillBox(builder, stack, -planeThickness, planeStart, planeStart, planeThickness, planeEnd, planeEnd, Colors.PLANE_ZY);

            if (showScale)
            {
                float cubeHalf = SCALE_CUBE_HALF * scale * thickness;

                if (active == null || active == Handle.SCALE_X) Draw.fillBox(builder, stack, axisSize - cubeHalf, -cubeHalf, -cubeHalf, axisSize + cubeHalf, cubeHalf, cubeHalf, Colors.RED);
                if (active == null || active == Handle.SCALE_Y) Draw.fillBox(builder, stack, -cubeHalf, axisSize - cubeHalf, -cubeHalf, cubeHalf, axisSize + cubeHalf, cubeHalf, Colors.GREEN);
                if (active == null || active == Handle.SCALE_Z) Draw.fillBox(builder, stack, -cubeHalf, -cubeHalf, axisSize - cubeHalf, cubeHalf, cubeHalf, axisSize + cubeHalf, Colors.BLUE);
            }
        }

        /* The centre cube is decoration, not a handle, so any filtered drag hides it. */
        if (active == null && (showMove || showScale || showRotate))
        {
            if (!building)
            {
                builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
                building = true;
            }

            Draw.fillBox(builder, stack, -axisOffset, -axisOffset, -axisOffset, axisOffset, axisOffset, axisOffset, Colors.WHITE);
        }

        if (building)
        {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            RenderSystem.depthFunc(GL11.GL_ALWAYS);

            BufferRenderer.drawWithGlobalProgram(builder.end());

            RenderSystem.depthFunc(GL11.GL_LEQUAL);
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
        stack.scale(distanceScale, distanceScale, distanceScale);
        this.drawAxes(stack, map, 0.25F, 0.025F);
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
        if (BBSRendering.isIrisShadowPass() || !this.hasLastRenderMatrix
            || context == null || projection == null || area == null || !BBSSettings.gizmos.get())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        this.setViewportScale(context.menu.height / (float) area.h);

        MatrixStackUtils.cacheMatrices();
        RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_Z);

        /* Map the UI area to a framebuffer-pixel viewport, exactly as
         * renderInterface does, so the stencil matches the drawn visual pixel for
         * pixel. The pick framebuffer is sized to the window, so the same mapping
         * applies. */
        float rx = (float) Math.round(mc.getWindow().getWidth() / (double) context.menu.width);
        float ry = (float) Math.round(mc.getWindow().getHeight() / (double) context.menu.height);
        float size = BBSModClient.getOriginalFramebufferScale();
        int vx = (int) (area.x * rx);
        int vy = (int) (mc.getWindow().getHeight() - (area.y + area.h) * ry);
        int vw = (int) (area.w * rx);
        int vh = (int) (area.h * ry);

        RenderSystem.viewport((int) (vx * size), (int) (vy * size), (int) (vw * size), (int) (vh * size));

        MatrixStack stack = new MatrixStack();
        MatrixStackUtils.multiply(stack, this.lastRenderMatrix);

        this.drawStencilAxes(stack, map);

        RenderSystem.viewport(0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
        MatrixStackUtils.restoreMatrices();
    }

    private void captureRenderMatrix(MatrixStack stack)
    {
        this.lastRenderMatrix.set(stack.peek().getPositionMatrix());
        this.hasLastRenderMatrix = true;
    }

    /**
     * Freeze the gizmo orientation at grab time while an axis ring is dragged:
     * the drawing stack is rewound to {@link #bakedRotationMatrix} (the live
     * {@link #lastRenderMatrix} is left untouched for pick/projection helpers).
     * The origin is unchanged by a rotation, so only the orientation is pinned.
     */
    private void applyBakedRotation(MatrixStack stack)
    {
        if (this.isBakingRotation())
        {
            stack.peek().getPositionMatrix().set(this.bakedRotationMatrix);
        }
    }

    /**
     * Snapshot the current render orientation so the ring stays put for the
     * coming drag. Called when an axis ring rotation begins.
     */
    public void bakeRotation()
    {
        if (this.hasLastRenderMatrix)
        {
            this.bakedRotationMatrix.set(this.lastRenderMatrix);
            this.hasBakedRotation = true;
        }
    }

    private boolean isBakingRotation()
    {
        return this.hasBakedRotation
            && this.currentTransform != null
            && this.currentTransform.isEditing()
            && this.currentTransform.getMode() == Op.ROTATE.modeOrdinal
            && !this.currentTransform.isSphereRotate()
            && !this.currentTransform.isViewRotate();
    }

    private void drawAxes(MatrixStack stack, StencilMap map, float axisSize, float axisOffset)
    {
        float scale = BBSSettings.axesScale.get();
        float thickness = BBSSettings.axesThickness.get();

        Handle active = this.activeDragHandle();

        boolean showMove = this.mode.shows(Op.MOVE) && (active == null || active.op == Op.MOVE || active.op == Op.SCREEN);
        boolean showScale = this.mode.shows(Op.SCALE) && (active == null || active.op == Op.SCALE);
        boolean showRotate = this.mode.shows(Op.ROTATE) && (active == null || active.op == Op.ROTATE || active.op == Op.VIEW || active.op == Op.TRACKBALL);

        axisSize *= scale * this.combinedInnerScale();
        axisOffset *= scale * thickness;

        RenderSystem.disableDepthTest();

        if (showRotate)
        {
            this.updateVbos();

            if (!BBSSettings.rotateHideRings.get())
            {
                if (active == null || active == Handle.ROTATE_Z) this.drawCachedRing(stack, this.rotateStencilRingVbo, Axis.Z, STENCIL_ROTATE_Z / 255F, 0F, 0F, 1F);
                if (active == null || active == Handle.ROTATE_X) this.drawCachedRing(stack, this.rotateStencilRingVbo, Axis.X, STENCIL_ROTATE_X / 255F, 0F, 0F, 1F);
                if (active == null || active == Handle.ROTATE_Y) this.drawCachedRing(stack, this.rotateStencilRingVbo, Axis.Y, STENCIL_ROTATE_Y / 255F, 0F, 0F, 1F);
            }

            /* View ring stays pickable even when the rings are hidden (see drawAxes visual pass). */
            if (active == null || active == Handle.VIEW) this.drawCachedRingBillboard(stack, this.rotateStencilRingVbo, STENCIL_VIEW / 255F, 0F, 0F, 1F);
        }

        if (showMove || showScale)
        {
            BufferBuilder builder = Tessellator.getInstance().getBuffer();

            /* The bar reads as move when move is on screen (combined) and as scale
             * only when scale stands alone; the scale handle then lives on the end
             * cubes, so move and scale never share an id under the cursor. */
            Handle barX = showMove ? Handle.MOVE_X : Handle.SCALE_X;
            Handle barY = showMove ? Handle.MOVE_Y : Handle.SCALE_Y;
            Handle barZ = showMove ? Handle.MOVE_Z : Handle.SCALE_Z;
            Handle planeXZ = showMove ? Handle.MOVE_XZ : Handle.SCALE_XZ;
            Handle planeXY = showMove ? Handle.MOVE_XY : Handle.SCALE_XY;
            Handle planeZY = showMove ? Handle.MOVE_ZY : Handle.SCALE_ZY;

            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

            if (active == null || active == barX) Draw.fillBox(builder, stack, 0, -axisOffset, -axisOffset, axisSize, axisOffset, axisOffset, barX.index / 255F, 0F, 0F);
            if (active == null || active == barY) Draw.fillBox(builder, stack, -axisOffset, 0, -axisOffset, axisOffset, axisSize, axisOffset, barY.index / 255F, 0F, 0F);
            if (active == null || active == barZ) Draw.fillBox(builder, stack, -axisOffset, -axisOffset, 0, axisOffset, axisOffset, axisSize, barZ.index / 255F, 0F, 0F);
            Draw.fillBox(builder, stack, -axisOffset, -axisOffset, -axisOffset, axisOffset, axisOffset, axisOffset, 0F, 0F, 0F);

            /* Screen-space handle hitbox: drawn before the planes so they win the pick
             * where they overlap (planes overlay the cube). Matches the visual cube. */
            if (showMove && (active == null || active == Handle.SCREEN))
            {
                float screenHalf = SCREEN_CUBE_HALF * scale * thickness;

                Draw.fillBox(builder, stack, -screenHalf, -screenHalf, -screenHalf, screenHalf, screenHalf, screenHalf, STENCIL_SCREEN / 255F, 0F, 0F);
            }

            float planeStart = axisSize * 0.2F;
            float planeEnd = planeStart + axisSize * 0.4F * thickness;
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

            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            BufferRenderer.drawWithGlobalProgram(builder.end());
        }

        RenderSystem.enableDepthTest();
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
     * Kind of transform a handle drives. {@link #modeOrdinal} matches the
     * {@code mode} argument {@link UIPropTransform#enableMode(int, Axis, Axis, GizmoDrag)}
     * expects (0 translate, 1 scale, 2 rotate); VIEW and TRACKBALL are rotate
     * variants routed through their own enable* calls.
     */
    public static enum Op
    {
        MOVE(0), SCALE(1), ROTATE(2), VIEW(2), TRACKBALL(2), SCREEN(0);

        public final int modeOrdinal;

        Op(int modeOrdinal)
        {
            this.modeOrdinal = modeOrdinal;
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
        SCREEN(STENCIL_SCREEN, Op.SCREEN, null, null);

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
