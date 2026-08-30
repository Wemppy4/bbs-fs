package mchorse.bbs_mod.ui.framework.elements.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.OrbitCamera;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Intersectiond;
import org.joml.Matrix3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Model renderer GUI element
 *
 * This base class can be used for full screen model viewer.
 *
 * <p>It is flown the way the film's orbit is flown, and for the same reason the two exist at
 * all: the left button swings the view around what is being looked at, the middle one (or Shift
 * with the left) slides it along the plane of the screen, the wheel closes in by a share of the
 * distance rather than by a fixed step, and what is drawn chases where the input has put it
 * instead of snapping there. Everything one learns in the film viewport is worth the same in a
 * preview.</p>
 */
public abstract class UIModelRenderer extends UIElement
{
    private static Vector3d vec = new Vector3d();
    private static Matrix3d mat = new Matrix3d();

    /** As far as the view may be tipped: straight up and down is where the maths gives out. */
    private static final float PITCH_LIMIT = MathUtils.PI * 0.5F - 0.01F;

    private static final float MIN_DISTANCE = 0.5F;
    private static final float MAX_DISTANCE = 256F;

    /** How much of the distance one notch of the wheel eats; Ctrl takes a bigger bite. */
    private static final float ZOOM_STEP = 0.1F;
    private static final float ZOOM_STEP_FAST = 0.22F;

    protected IEntity entity = new StubEntity();

    protected int timer;
    protected int dragging;

    public Camera camera = new Camera();

    public Vector3f pos = new Vector3f();
    public boolean grid = true;

    /* What the input drives, and - in pos, camera.rotation and distance - what is drawn chasing it */
    private final Vector2f targetRotation = new Vector2f();
    private final Vector3f targetPos = new Vector3f();
    private float distance;
    private float targetDistance;

    private Vector3d cachedPlaneIntersection = new Vector3d();
    private Vector3f cachedPos = new Vector3f();
    private Camera cachedCamera = new Camera();
    private Vector3d plane = new Vector3d();
    private float lastX;
    private float lastY;

    private long tick;
    private Matrix4f transform = new Matrix4f();

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
        this.targetRotation.set(MathUtils.clamp(MathUtils.toRad(pitch), -PITCH_LIMIT, PITCH_LIMIT), MathUtils.toRad(yaw));
        this.camera.rotation.x = this.targetRotation.x;
        this.camera.rotation.y = this.targetRotation.y;
    }

    public void setPosition(float x, float y, float z)
    {
        this.pos.set(x, y, z);
        this.targetPos.set(x, y, z);
    }

    /** Slide the view onto a point, letting the smoothing carry it there. */
    public void focus(float x, float y, float z)
    {
        this.targetPos.set(x, y, z);
    }

    public void setDistance(float distance)
    {
        this.distance = MathUtils.clamp(distance, MIN_DISTANCE, MAX_DISTANCE);
        this.targetDistance = this.distance;
    }

    public IEntity getEntity()
    {
        return this.entity;
    }

    public void reset()
    {
        this.setDistance(2.25F);
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

            this.cachedPos.set(this.targetPos);
            this.cachedCamera.copy(this.camera);
            this.plane.set(0, 0, 1);
            this.rotateVector(this.plane);

            this.cachedPlaneIntersection = this.calculateOnPlane(context);
        }

        return false;
    }

    /** The wheel closes in by a share of the distance, so it is as fine up close as it is far out. */
    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        if (this.area.isInside(context) && !this.isDragging() && context.mouseWheel != 0)
        {
            float step = Window.isCtrlPressed() ? ZOOM_STEP_FAST : ZOOM_STEP;
            float factor = (float) Math.pow(1F - step, context.mouseWheel);

            this.targetDistance = MathUtils.clamp(this.targetDistance * factor, MIN_DISTANCE, MAX_DISTANCE);

            return true;
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

    /**
     * What is drawn walks towards what the input has set, by the share of the way a frame is
     * worth. With the smoothing turned off it simply is what the input set.
     */
    private void applySmoothing()
    {
        float smoothness = BBSSettings.editorCameraSmoothness.get();

        if (smoothness <= 0F)
        {
            this.camera.rotation.x = this.targetRotation.x;
            this.camera.rotation.y = this.targetRotation.y;
            this.pos.set(this.targetPos);
            this.distance = this.targetDistance;

            return;
        }

        float dt = MinecraftClient.getInstance().getLastFrameDuration();
        float factor = MathUtils.clamp(1F - (float) Math.pow(Math.min(smoothness, 0.99F), dt), 0F, 1F);

        this.camera.rotation.x = Lerps.lerp(this.camera.rotation.x, this.targetRotation.x, factor);
        this.camera.rotation.y = Lerps.lerp(this.camera.rotation.y, this.targetRotation.y, factor);
        this.pos.lerp(this.targetPos, factor);
        this.distance = Lerps.lerp(this.distance, this.targetDistance, factor);
    }

    private void updateLogic(UIContext context)
    {
        this.applySmoothing();

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
        RenderSystem.depthFunc(GL11.GL_LEQUAL);

        this.setupPosition();
        this.setupViewport(context);

        MatrixStack stack = context.render.batcher.getContext().getMatrices();

        /* Cache the global stuff */
        MatrixStackUtils.cacheMatrices();

        RenderSystem.setProjectionMatrix(this.camera.projection, VertexSorter.BY_Z);
        RenderSystem.setInverseViewRotationMatrix(new Matrix3f(this.camera.view).invert());

        /* Rendering begins... */
        stack.push();
        MatrixStackUtils.multiply(stack, this.camera.view);
        stack.translate(-this.camera.position.x, -this.camera.position.y, -this.camera.position.z);
        MatrixStackUtils.multiply(stack, this.transform);

        RenderSystem.setupLevelDiffuseLighting(
            new Vector3f(0, 0.85F, -1).normalize(),
            new Vector3f(0, 0.85F, 1).normalize(),
            this.camera.view
        );

        if (this.grid)
        {
            this.renderGrid(context);
        }

        this.renderUserModel(context);

        DiffuseLighting.disableGuiDepthLighting();

        stack.pop();

        /* Return back to orthographic projection */
        MinecraftClient mc = MinecraftClient.getInstance();

        RenderSystem.viewport(0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
        MatrixStackUtils.restoreMatrices();

        RenderSystem.depthFunc(GL11.GL_ALWAYS);

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

                    this.targetPos.set(this.cachedPos);
                    this.targetPos.sub((float) newPoint.x, (float) newPoint.y, (float) newPoint.z);
                    this.targetPos.add((float) this.cachedPlaneIntersection.x, (float) this.cachedPlaneIntersection.y, (float) this.cachedPlaneIntersection.z);

                    this.lastX = mouseX;
                    this.lastY = mouseY;
                }
            }
            else
            {
                float angle = OrbitCamera.dragAngleSpeed() * 4F;

                this.targetRotation.y += (mouseX - this.lastX) * angle;
                this.targetRotation.x = MathUtils.clamp(this.targetRotation.x + (mouseY - this.lastY) * angle, -PITCH_LIMIT, PITCH_LIMIT);

                this.lastX = mouseX;
                this.lastY = mouseY;
            }
        }
    }

    public void setupPosition()
    {
        this.camera.position.set(this.pos);

        vec.set(0, 0, -this.distance);
        this.rotateVector(vec);

        this.camera.position.x += vec.x;
        this.camera.position.y += vec.y;
        this.camera.position.z += vec.z;
    }

    private Vector3d calculateOnPlane(UIContext context)
    {
        Vector3d vector = new Vector3d();
        Vector3d origin = new Vector3d(this.cachedCamera.position).sub(this.cachedPos);
        Vector3d destination = new Vector3d(this.cachedCamera.getMouseDirection(context.mouseX, context.mouseY, this.area.x, this.area.y, this.area.w, this.area.h)).mul(this.distance * 2D).add(origin);
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
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        int[] viewport = UIUtils.viewportArea(this.area);

        this.camera.updatePerspectiveProjection(viewport[2], viewport[3]);
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
        Matrix4f matrix4f = context.batcher.getContext().getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        builder.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (int x = 0; x <= 10; x ++)
        {
            if (x == 0)
            {
                builder.vertex(matrix4f, x - 5, 0, -5).color(0F, 0F, 1F, 1F).next();
                builder.vertex(matrix4f, x - 5, 0, 5).color(0F, 0F, 1F, 1F).next();
            }
            else
            {
                builder.vertex(matrix4f, x - 5, 0, -5).color(0.25F, 0.25F, 0.25F, 1F).next();
                builder.vertex(matrix4f, x - 5, 0, 5).color(0.25F, 0.25F, 0.25F, 1F).next();
            }
        }

        for (int x = 0; x <= 10; x ++)
        {
            if (x == 0)
            {
                builder.vertex(matrix4f, -5, 0, x - 5).color(1F, 0F, 0F, 1F).next();
                builder.vertex(matrix4f, 5, 0, x - 5).color(1F, 0F, 0F, 1F).next();
            }
            else
            {
                builder.vertex(matrix4f, -5, 0, x - 5).color(0.25F, 0.25F, 0.25F, 1F).next();
                builder.vertex(matrix4f, 5, 0, x - 5).color(0.25F, 0.25F, 0.25F, 1F).next();
            }
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());
    }
}