package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.InverseView;
import mchorse.bbs_mod.graphics.ModelPreviewRenderer;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.camera.Camera;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.Factor;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Intersectiond;
import org.joml.Matrix3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/**
 * Model renderer GUI element
 *
 * This base class can be used for full screen model viewer.
 */
public abstract class UIModelRenderer extends UIElement
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Vector3d vec = new Vector3d();
    private static Matrix3d mat = new Matrix3d();

    protected IEntity entity = new StubEntity();

    protected int timer;
    protected int dragging;

    public Camera camera = new Camera();

    public Vector3f pos = new Vector3f();
    public Factor distance = new Factor(0, 0, 100, (x) -> Math.pow(x, 2) / 100D);
    public boolean grid = true;

    private Vector3d cachedPlaneIntersection = new Vector3d();
    private Vector3f cachedPos = new Vector3f();
    private Camera cachedCamera = new Camera();
    private Vector3d plane = new Vector3d();
    private float lastX;
    private float lastY;

    private long tick;
    private Matrix4f transform = new Matrix4f();

    /* In-panel 3D preview off-screen target (1.21.11): the model is rendered into its own colour+depth
     * textures via a vanilla entity RenderLayer, then blitted back into the GUI. */
    private final ModelPreviewRenderer preview = new ModelPreviewRenderer();
    protected int viewportW;
    protected int viewportH;

    /* Off-screen preview texture rendered during the world phase (renderModelToTexture), blitted during
     * the GUI phase (renderModel). previewGlId < 0 means "nothing rendered this frame -> skip blit". */
    private int previewGlId = -1;
    private int previewVw;
    private int previewVh;

    /* Editor-preview diffuse lighting (faithful to 1.21.1 commit 0affc6c4): the original called
     * RenderSystem.setupLevelDiffuseLighting(new Vector3f(0, 0.85, -1).normalize(),
     * new Vector3f(0, 0.85, 1).normalize(), this.camera.view) right before renderUserModel. That 3-arg
     * overload was removed in 1.21.5+ (only setShaderLights(GpuBufferSlice) remains; DiffuseLighting's
     * Lighting UBO is two std140 vec3s). So we reproduce it the same way BbsFormGuiElementRenderer.lights()
     * does for the LIST previews: a persistent two-vec3 Lighting UBO bound via RenderSystem.setShaderLights.
     * The buffer is allocated lazily once, but RE-WRITTEN every frame because the original transformed the
     * two directions by the LIVE camera.view (so the light stays world-fixed as the model is orbited). */
    private static final Vector3f LIGHT_A = new Vector3f(0F, 0.85F, -1F).normalize();
    private static final Vector3f LIGHT_B = new Vector3f(0F, 0.85F, 1F).normalize();

    private GpuBuffer lightsBuffer;
    private GpuBufferSlice lights;
    private final Vector3f lightDirA = new Vector3f();
    private final Vector3f lightDirB = new Vector3f();

    public UIModelRenderer()
    {
        super();

        this.reset();
    }

    public void setTransform(Matrix4f transform)
    {
        this.transform = transform;
    }

    /**
     * The orthonormal axes of the frame the preview is actually drawn in &mdash;
     * what {@link mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace#GLOBAL}
     * means for anything rendered here. {@link #transform} is multiplied onto the
     * stack before the model AND before the grid ({@link #renderModel}), so it is
     * that frame, not the camera's, that reads as "the world" to the user: the
     * floor grid turns with it. Identity in a plain preview, so GLOBAL stays the
     * flat scene axes there; the model block's immersive editing sets it to the
     * BLOCK's own transform, and then GLOBAL follows the block the way the film's
     * follows the replay. Scale is divided out &mdash; these are directions, and
     * a scaled block must not stretch the gizmo's frame.
     */
    public Matrix3f getSceneAxes()
    {
        return MatrixStackUtils.stripScale(this.transform).get3x3(new Matrix3f());
    }

    /**
     * Lift a matrix out of the previewed form's own frame into the frame the
     * preview is drawn in &mdash; i.e. apply {@link #transform}, exactly as
     * {@link #renderModel} does before the model reaches the stack.
     *
     * <p>The gizmo needs this because its two halves are recovered from
     * different places: the drawn origin and axes come back out of the RENDER
     * matrix (so they already carry the transform), while the drag's Jacobian
     * and rotation axes are SAMPLED from the editor's own bone matrices (which
     * do not). With a plain preview the transform is the identity and the two
     * agree by accident; inside a rotated model block they would disagree by
     * the block's rotation, and every drag would run off the handles. Scale is
     * deliberately kept &mdash; a Jacobian must map local units to the scene's
     * real distances.
     */
    public Matrix4f toSceneMatrix(Matrix4f matrix)
    {
        return new Matrix4f(this.transform).mul(matrix);
    }

    public void setRotation(float yaw, float pitch)
    {
        this.camera.rotation.y = MathUtils.toRad(yaw);
        this.camera.rotation.x = MathUtils.toRad(pitch);
    }

    public void setPosition(float x, float y, float z)
    {
        this.pos.set(x, y, z);
    }

    public void setDistance(int distanceX)
    {
        this.distance.setX(distanceX);
    }

    public void setEntity(IEntity entity)
    {
        this.entity = entity;
    }

    public IEntity getEntity()
    {
        return this.entity;
    }

    public void reset()
    {
        this.setDistance(15);
        this.setPosition(0, 1, 0);
        this.setRotation(0, 0);
    }

    public boolean isDragging()
    {
        return this.dragging != 0;
    }

    public boolean isDraggingPosition()
    {
        return this.dragging == 2;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.isDragging() && this.area.isInside(context) && (context.mouseButton == 0 || context.mouseButton == 2))
        {
            this.dragging = Window.isShiftPressed() || context.mouseButton == 2 ? 2 : 1;
            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            this.cachedPos.set(this.pos);
            this.cachedCamera.copy(this.camera);
            this.plane.set(0, 0, 1);
            this.rotateVector(this.plane);

            this.cachedPlaneIntersection = this.calculateOnPlane(context);
        }

        return false;
    }

    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        if (this.area.isInside(context) && !this.isDragging())
        {
            int x = Integer.compare(-(int) context.mouseWheel, 0);

            if (Window.isCtrlPressed())
            {
                x *= 8;
            }

            this.distance.setX(this.distance.getX() + x);
        }

        return super.subMouseScrolled(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        this.dragging = 0;

        return super.subMouseReleased(context);
    }

    @Override
    public void render(UIContext context)
    {
        this.updateLogic(context);

        context.batcher.clip(this.area, context);
        this.renderModel(context);
        context.batcher.unclip(context);

        super.render(context);
    }

    private void updateLogic(UIContext context)
    {
        long tick = context.getTick();
        long i = tick - this.tick;

        if (i > 10)
        {
            i = 10;
        }

        while (i > 0)
        {
            this.update();
            i --;
        }

        this.tick = tick;
    }

    /**
     * Update logic
     */
    protected void update()
    {
        this.timer += 1;
        this.entity.setAge(this.timer);
    }

    /**
     * Render the model into the OFF-SCREEN preview texture. Called during the world phase (driven by
     * UIScreen.renderInWorld), OUTSIDE the two-phase-GUI recording window: the immediate entity
     * RenderLayer.draw it issues opens its own GPU render pass, and during Screen.render the GUI's
     * colour-write mask has alpha disabled (which would zero the FBO alpha). Doing it here (pre-GUI)
     * avoids both; Screen.render then only RECORDS the blit (see {@link #renderModel}, which isolates
     * the blit on its own root layer so it composites correctly).
     */
    public void renderModelToTexture(UIContext context)
    {
        this.setupPosition();
        this.setupViewport(context);

        InverseView.set(new Matrix3f(this.camera.view).invert());

        int vw = this.viewportW;
        int vh = this.viewportH;

        this.previewGlId = -1;

        if (vw > 0 && vh > 0)
        {
            /* Tell Iris the main target is unbound for this pass. It runs during the world phase (see
             * UIScreen#renderInWorld), and the preview draws through a VANILLA entity RenderLayer —
             * which is exactly the kind a shaderpack claims. Iris would swap in the pack's entity
             * program, and the last thing that program does when it sets up is bind the pack's own
             * G-buffer, overriding the outputColorTextureOverride the preview just installed. The
             * viewport geometry then lands in the WORLD's buffers, carrying the preview's GUI
             * projection with it: the panel shows nothing while a huge smeared copy of the model
             * appears in the world. Iris gates the swap on this flag
             * (shouldOverrideShaders = isRenderingWorld && isMainBound), so clearing it keeps the
             * vanilla program — and the preview's own framebuffer. Same lever, same reason, as
             * FramebufferFormRenderer. */
            boolean worldFormsWere = BBSRendering.suspendWorldForms();

            ModelPreviewRenderer.ACTIVE = true;
            this.preview.begin(vw, vh, this.camera.projection);

            /* Restore the editor-preview diffuse lighting that the 1.21.11 port dropped (faithful to commit
             * 0affc6c4). ModelPreviewRenderer.begin() just bound the vanilla ENTITY_IN_UI inventory preset
             * (lights from below/front), so without this the in-panel model renders dark until some other
             * path re-sets the GUI diffuse lighting (e.g. on window-focus loss). Bind the BBS editor-preview
             * directions explicitly, AFTER begin() so it overrides that preset, BEFORE the grid/model draw. */
            RenderSystem.setShaderLights(this.editorLights());

            try
            {
                /* Ground grid first (depth-tested, so the model occludes the lines behind it), then the model
                 * — matches the original 1.21.1 renderModel ordering (grid drawn before renderUserModel). */
                if (this.grid)
                {
                    this.renderGrid(context);
                }

                this.renderUserModel(context);
            }
            catch (Exception e)
            {
                /* The MODEL geometry already drew into the FBO before any failure, so the preview still
                 * blits — but everything queued AFTER the model in renderUserModel (the gizmo visual and
                 * its pick stencil) is lost with it. Swallowing this blind made that indistinguishable
                 * from "the gizmo does not render": the editor just silently loses its overlays every
                 * frame. Report it once per distinct failure so the cause is visible without drowning
                 * the log at 60 fps. */
                reportPreviewFailure(e);
            }
            finally
            {
                this.preview.end();
                ModelPreviewRenderer.ACTIVE = false;
                ModelPreviewRenderer.TEXTURE = null;

                /* After end(): the overrides are gone, so the main target really is the bound one again. */
                BBSRendering.restoreWorldForms(worldFormsWere);
            }

            this.previewGlId = this.preview.getColorGlId();
            this.previewVw = vw;
            this.previewVh = vh;
        }
    }

    /** Distinct preview failures already reported, so a per-frame throw logs once instead of every frame. */
    private static final Set<String> reportedPreviewFailures = new HashSet<>();

    /**
     * Log a swallowed preview-pass failure the first time each distinct one is seen. The editor keeps
     * running (the model still blits), but the overlays that were queued behind the failure are gone,
     * so this must not stay invisible.
     */
    private static void reportPreviewFailure(Exception e)
    {
        StackTraceElement[] trace = e.getStackTrace();
        String key = e.getClass().getName() + ":" + e.getMessage()
            + (trace.length > 0 ? "@" + trace[0] : "");

        if (reportedPreviewFailures.add(key))
        {
            LOGGER.error("[BBS preview] model preview pass failed — the gizmo visual and its pick "
                + "stencil were dropped for this frame", e);
        }
    }

    /**
     * GUI phase: blit the texture rendered earlier this frame by {@link #renderModelToTexture} (world
     * phase) and process viewport input. The blit is a normal recorded {@code drawTexture}, so it
     * composites like any icon (V-flipped: FBO origin is bottom-up).
     */
    private void renderModel(UIContext context)
    {
        if (this.previewGlId >= 0 && this.previewVw > 0 && this.previewVh > 0)
        {
            /* Isolate the FBO blit on its own root layer: prior panel chrome (recorded into earlier root
             * layers) then composites strictly BEHIND it, and later UI strictly in front, instead of the
             * blit landing on a bounds-intersection sub-layer that sibling chrome overpaints. This is the
             * deterministic version of what previously only happened by luck when a hovered tooltip
             * appended a late root layer (GuiRenderState painter-order; see Batcher2D.newRootLayer). */
            context.batcher.newRootLayer();
            /* The preview FBO is cleared to transparent black, so everything drawn into it — the model, and
             * the gizmo's translucent parts (the sweep pie's fill, the IK/physics overlay colours) — is
             * premultiplied. Blitting it with ordinary alpha blending applied alpha twice and darkened all
             * of them. */
            context.batcher.texturedBoxPremultiplied(this.previewGlId, Colors.WHITE,
                this.area.x, this.area.y, this.area.w, this.area.h,
                0, this.previewVh, this.previewVw, 0, this.previewVw, this.previewVh);
            context.batcher.newRootLayer();
        }

        this.processInputs(context);
    }

    /**
     * Build the per-vertex {@link MatrixStack} pre-loaded with the camera model-view
     * ({@code camera.view (rotation) * translate(-position) * transform}). The cubic geometry
     * (CubicCubeRenderer) transforms its model-space vertices by this stack, so positions end up in
     * VIEW space; {@link ModelPreviewRenderer#begin} keeps the global model-view identity and supplies
     * only the perspective projection. This matches vanilla world entity rendering (camera baked into the
     * per-vertex position matrix, ModelViewMat ~identity) and the old 1.21.1 BBS recipe. Subclasses must
     * use this for the {@code FormRenderingContext} stack instead of a fresh identity {@code MatrixStack}.
     */
    protected MatrixStack createCameraStack()
    {
        MatrixStack stack = new MatrixStack();

        MatrixStackUtils.multiply(stack, this.camera.view);
        stack.translate(-this.camera.position.x, -this.camera.position.y, -this.camera.position.z);
        MatrixStackUtils.multiply(stack, this.transform);

        return stack;
    }

    /**
     * The editor-preview Lighting UBO, re-uploaded each frame with the two {@link #LIGHT_A}/{@link #LIGHT_B}
     * directions transformed by the LIVE {@code camera.view} (direction-only). This reproduces the original
     * {@code RenderSystem.setupLevelDiffuseLighting(LIGHT_A, LIGHT_B, camera.view)} of commit 0affc6c4 with the
     * 1.21.11 API: vanilla's Lighting UBO is exactly two std140 vec3s ({@link DiffuseLighting#UBO_SIZE}) and the
     * only public binder left is {@link RenderSystem#setShaderLights(GpuBufferSlice)} — see the in-tree LIST-preview
     * twin {@code BbsFormGuiElementRenderer.lights()}. The {@link GpuBuffer} is allocated lazily once (no per-frame
     * allocation); only its contents are rewritten, because the directions depend on the per-frame camera view.
     */
    private GpuBufferSlice editorLights()
    {
        /* Bind the light directions RAW (fixed/world space), NOT transformed by camera.view: at runtime the
         * camera.view-transformed variant lit the model from the wrong angle. The in-tree LIST-preview twin
         * (BbsFormGuiElementRenderer.lights()) likewise binds its directions untransformed, so lit normals here
         * are evaluated against the same fixed light the thumbnails use, independent of orbit. */
        this.lightDirA.set(LIGHT_A);
        this.lightDirB.set(LIGHT_B);

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            ByteBuffer data = Std140Builder.onStack(stack, DiffuseLighting.UBO_SIZE)
                .putVec3(this.lightDirA)
                .putVec3(this.lightDirB)
                .get();

            if (this.lightsBuffer == null)
            {
                /* usage 136 = UNIFORM | COPY_DST, mirroring DiffuseLighting's own Lighting UBO and the
                 * in-tree BbsFormGuiElementRenderer.lights() buffer. */
                this.lightsBuffer = RenderSystem.getDevice().createBuffer(() -> "BBS editor preview lights UBO", 136, data);
                this.lights = this.lightsBuffer.slice(0, DiffuseLighting.UBO_SIZE);
            }
            else
            {
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.lights, data);
            }
        }

        return this.lights;
    }

    protected void processInputs(UIContext context)
    {
        int mouseX = context.mouseX;
        int mouseY = context.mouseY;

        if (this.isDragging())
        {
            if (this.isDraggingPosition())
            {
                if (this.lastX != context.mouseX || this.lastY != context.mouseY)
                {
                    Vector3d newPoint = this.calculateOnPlane(context);

                    this.pos.set(this.cachedPos);
                    this.pos.sub((float) newPoint.x, (float) newPoint.y, (float) newPoint.z);
                    this.pos.add((float) this.cachedPlaneIntersection.x, (float) this.cachedPlaneIntersection.y, (float) this.cachedPlaneIntersection.z);

                    this.lastX = mouseX;
                    this.lastY = mouseY;
                }
            }
            else
            {
                this.camera.rotation.y -= MathUtils.toRad(this.lastX - mouseX);
                this.camera.rotation.x -= MathUtils.toRad(this.lastY - mouseY);

                this.lastX = mouseX;
                this.lastY = mouseY;
            }
        }
    }

    public void setupPosition()
    {
        this.camera.position.set(this.pos);

        vec.set(0, 0, -this.distance.getValue());
        this.rotateVector(vec);

        this.camera.position.x += vec.x;
        this.camera.position.y += vec.y;
        this.camera.position.z += vec.z;
    }

    private Vector3d calculateOnPlane(UIContext context)
    {
        Vector3d vector = new Vector3d();
        Vector3d origin = new Vector3d(this.cachedCamera.position).sub(this.cachedPos);
        Vector3d destination = new Vector3d(this.cachedCamera.getMouseDirection(context.mouseX, context.mouseY, this.area.x, this.area.y, this.area.w, this.area.h)).mul(this.distance.getValue() * 2).add(origin);
        Intersectiond.intersectLineSegmentPlane(origin.x, origin.y, origin.z, destination.x, destination.y, destination.z, this.plane.x, this.plane.y, this.plane.z, 0, vector);

        return vector;
    }

    private void rotateVector(Vector3d vec)
    {
        mat.identity().rotateX(this.camera.rotation.x);
        mat.transform(vec);
        mat.identity().rotateY(MathUtils.PI - this.camera.rotation.y);
        mat.transform(vec);
    }

    protected void setupViewport(UIContext context)
    {
        /* TODO(1.21.11 render): GL11.glClear(GL_DEPTH_BUFFER_BIT) + RenderSystem.viewport(...) disabled.
         * Depth clear and viewport scoping must go through the new framebuffer/RenderPass model; the
         * per-element scissor/viewport rect (vx/vy/vw/vh below) is still computed for when that lands. */
        MinecraftClient mc = MinecraftClient.getInstance();

        /* Measure against the REAL window, not the film/video framebuffer. This preview renders in the
         * world phase (BBSRendering.renderingWorld == true); if the film system also has customSize set
         * (an open camera/replay/action editor), Window.getWidth()/getHeight() are mixin-overridden to the
         * VIDEO resolution (canReplaceFramebuffer() == customSize && renderingWorld) — which would size this
         * UI panel's FBO to the video resolution instead of its on-screen pixels. Clear renderingWorld for
         * the measurement so the Window getters report the real screen size (pre-world-phase behaviour). */
        boolean prevRenderingWorld = BBSRendering.renderingWorld;
        BBSRendering.renderingWorld = false;

        int vw;
        int vh;

        try
        {
            float rx = (float) Math.round(mc.getWindow().getWidth() / (double) context.menu.width);
            float ry = (float) Math.round(mc.getWindow().getHeight() / (double) context.menu.height);

            int vx = (int) (this.area.x * rx);
            int vy = (int) (mc.getWindow().getHeight() - (this.area.y + this.area.h) * ry);

            vw = (int) (this.area.w * rx);
            vh = (int) (this.area.h * ry);
        }
        finally
        {
            BBSRendering.renderingWorld = prevRenderingWorld;
        }

        this.viewportW = vw;
        this.viewportH = vh;

        this.camera.updatePerspectiveProjection(vw, vh);
        this.camera.updateView();
    }

    /**
     * Draw your model here
     */
    protected abstract void renderUserModel(UIContext context);

    /**
     * Render block of grass under the model (which signify where
     * located the ground below the model)
     *
     * <p>1.21.11 port: faithful to the original 11x11 ground grid (coloured Z/X centre axes blue/red, the rest
     * grey). Two forced deviations from the 1.21.1 code: (1) the 3D position matrix is taken from
     * {@link #createCameraStack()} — the same {@code camera.view * translate(-pos) * transform} stack baked
     * into the model vertices — instead of {@code DrawContext.getMatrices()} (now 2D); (2) the flush goes
     * through the BBS POSITION_COLOR DEBUG_LINES pipeline ({@link Draw#flushLines}) since
     * {@code RenderSystem.setShader} + {@code BufferRenderer.drawWithGlobalProgram} were removed in 1.21.5.
     * Called inside {@link #renderModelToTexture}'s {@link ModelPreviewRenderer} pass, so it draws into the
     * off-screen preview FBO with the perspective projection already set and global model-view identity.</p>
     */
    protected void renderGrid(UIContext context)
    {
        Matrix4f matrix4f = this.createCameraStack().peek().getPositionMatrix();

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (int x = 0; x <= 10; x ++)
        {
            if (x == 0)
            {
                builder.vertex(matrix4f, x - 5, 0, -5).color(0F, 0F, 1F, 1F);
                builder.vertex(matrix4f, x - 5, 0, 5).color(0F, 0F, 1F, 1F);
            }
            else
            {
                builder.vertex(matrix4f, x - 5, 0, -5).color(0.25F, 0.25F, 0.25F, 1F);
                builder.vertex(matrix4f, x - 5, 0, 5).color(0.25F, 0.25F, 0.25F, 1F);
            }
        }

        for (int x = 0; x <= 10; x ++)
        {
            if (x == 0)
            {
                builder.vertex(matrix4f, -5, 0, x - 5).color(1F, 0F, 0F, 1F);
                builder.vertex(matrix4f, 5, 0, x - 5).color(1F, 0F, 0F, 1F);
            }
            else
            {
                builder.vertex(matrix4f, -5, 0, x - 5).color(0.25F, 0.25F, 0.25F, 1F);
                builder.vertex(matrix4f, 5, 0, x - 5).color(0.25F, 0.25F, 0.25F, 1F);
            }
        }

        Draw.flushLines(builder);
    }
}