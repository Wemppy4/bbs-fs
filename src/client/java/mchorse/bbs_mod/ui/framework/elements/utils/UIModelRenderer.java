package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.graphics.InverseView;
import mchorse.bbs_mod.graphics.ModelPreviewRenderer;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.Factor;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import org.joml.Intersectiond;
import org.joml.Matrix3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Model renderer GUI element
 *
 * This base class can be used for full screen model viewer.
 */
public abstract class UIModelRenderer extends UIElement
{
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

    public UIModelRenderer()
    {
        super();

        this.reset();
    }

    public void setTransform(Matrix4f transform)
    {
        this.transform = transform;
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

        /* PROBE(1.21.11 render, diagnostic): GREEN 2px OUTLINE of the panel rect via the proven 2D path
         * (context.fill). Fires for ANY model/form preview. Reveals the true panel bounds without obscuring
         * the interior, so the magenta FBO blit (UIPickableFormRenderer, inset) shows inside the frame when
         * the FBO-texture blit path composites. Remove once preview is real. */
        {
            int ax = this.area.x, ay = this.area.y, aw = this.area.w, ah = this.area.h, t = 2, g = 0xff00ff00;
            context.batcher.box(ax, ay, ax + aw, ay + t, g);
            context.batcher.box(ax, ay + ah - t, ax + aw, ay + ah, g);
            context.batcher.box(ax, ay, ax + t, ay + ah, g);
            context.batcher.box(ax + aw - t, ay, ax + aw, ay + ah, g);
        }

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
     * Draw currently edited model
     */
    private void renderModel(UIContext context)
    {
        this.setupPosition();
        this.setupViewport(context);

        InverseView.set(new Matrix3f(this.camera.view).invert());

        int vw = this.viewportW;
        int vh = this.viewportH;

        /* 1.21.11 render: the imperative 3D-into-GUI draw moved to an off-screen target (the 1.21.5 GPU
         * rewrite removed RenderSystem.setProjectionMatrix(Matrix4f)/raw-FBO binding, and the 1.21.6 two-phase
         * GUI defers compositing). Model-view = camera.view (rotation) * translate(-position) * transform,
         * exactly as the old global RenderSystem model-view; per-model transforms are baked into the vertices.
         * The colour texture is then blitted back into the deferred GUI (V-flipped, FBO origin is bottom-up). */
        if (vw > 0 && vh > 0)
        {
            Matrix4f modelView = new Matrix4f(this.camera.view);

            modelView.translate(-(float) this.camera.position.x, -(float) this.camera.position.y, -(float) this.camera.position.z);
            modelView.mul(this.transform);

            ModelPreviewRenderer.ACTIVE = true;
            this.preview.begin(vw, vh, this.camera.projection, modelView);

            try
            {
                this.renderUserModel(context);
            }
            finally
            {
                this.preview.end();
                ModelPreviewRenderer.ACTIVE = false;
                ModelPreviewRenderer.TEXTURE = null;
            }

            context.batcher.texturedBox(this.preview.getColorGlId(), Colors.WHITE,
                this.area.x, this.area.y, this.area.w, this.area.h,
                0, vh, vw, 0, vw, vh);
        }

        this.processInputs(context);
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

        float rx = (float) Math.round(mc.getWindow().getWidth() / (double) context.menu.width);
        float ry = (float) Math.round(mc.getWindow().getHeight() / (double) context.menu.height);
        float size = BBSModClient.getOriginalFramebufferScale();

        int vx = (int) (this.area.x * rx);
        int vy = (int) (mc.getWindow().getHeight() - (this.area.y + this.area.h) * ry);
        int vw = (int) (this.area.w * rx);
        int vh = (int) (this.area.h * ry);

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
     */
    protected void renderGrid(UIContext context)
    {
        /* TODO(1.21.11 render): ground-grid line render disabled. It built a POSITION_COLOR/DEBUG_LINES
         * BufferBuilder (11x11 grid with coloured X/Z centre axes) drawn via RenderSystem.setShader +
         * GameRenderer.getPositionColorProgram + BufferRenderer.drawWithGlobalProgram, all removed in the
         * 1.21.5 GPU rewrite, and it read a 3D position matrix from DrawContext.getMatrices() (now 2D).
         * Re-emit through the new POSITION_COLOR line pipeline once the foundation lands. */
    }
}