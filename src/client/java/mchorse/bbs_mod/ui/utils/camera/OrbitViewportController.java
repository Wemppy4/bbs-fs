package mchorse.bbs_mod.ui.utils.camera;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.OrbitCamera;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.MinecraftClient;
import org.joml.Intersectiond;
import org.joml.Matrix3f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

/**
 * Flying an editor viewport by turning around what is being looked at: the left button swings
 * the camera about a pivot, the middle one slides that pivot along the plane of the screen,
 * the wheel closes in by a share of the distance, and WASD walks the pivot itself. What is
 * drawn chases where the input has put it, so nothing snaps.
 *
 * <p>None of that knows what is being looked at. A host says where its subject is (a replay of
 * a film, a model block in the world), what the viewport and its camera are, and how fast the
 * user asked to move; and it may hand the orbit an <em>anchor</em> to hang off, in which case
 * the pivot and the rotation are kept in the anchor's frame and the subject carries the camera
 * with it. With no anchor the frame is the world's and the same maths passes world values
 * through untouched.</p>
 */
public abstract class OrbitViewportController implements ICameraController
{
    public static final float PITCH_LIMIT = MathUtils.PI * 0.5F - 0.01F;
    public static final float MIN_DISTANCE = 0.5F;
    public static final float MAX_DISTANCE = 256F;

    /** How far the cursor must travel before a press counts as a drag rather than a click. */
    private static final int DRAG_THRESHOLD = 3;

    public boolean enabled;

    private boolean orbiting;
    private int orbitButton = -1;
    private final Vector2i last = new Vector2i();
    private final Vector2i pressOrigin = new Vector2i();
    private boolean dragged;

    /* The state the input drives. */
    protected final Vector2f targetRotation = new Vector2f();
    protected final Vector3f targetPivot = new Vector3f();
    protected float targetDistance;

    /* The state that is rendered, smoothly chasing the target above. */
    protected final Vector2f rotation = new Vector2f();
    protected final Vector3f pivot = new Vector3f();
    protected float distance;

    /** Whether the pivot has been placed onto the subject after a reset. */
    protected boolean positioned;

    private boolean ortho;

    /* Whether ortho was turned on by an axis snap rather than by the user, in
     * which case orbiting away from the axis turns it back off (see rotate). */
    private boolean autoOrtho;

    /* The frame the orbit is kept in; identity when it hangs off nothing. */
    protected final Vector3d anchorPosition = new Vector3d();
    protected float anchorYaw;

    private final PanState panState = new PanState();
    protected final Vector3i velocityPosition = new Vector3i();

    public OrbitViewportController()
    {
        this.reset();
    }

    /* What a host has to answer */

    protected abstract UIContext getContext();

    /** The area the viewport is drawn in, for turning the cursor into a ray. */
    protected abstract Area getViewport();

    /** The camera as it is right now, which panning drags the pivot against. */
    protected abstract Camera getViewportCamera();

    /** How fast WASD walks the pivot; the user's own speed setting. */
    protected abstract float getSpeed();

    /** Where the thing being looked at is, in world space, or null when there is none right now. */
    protected abstract Vector3f getSubjectPivot(float transition);

    /**
     * Whether there is anything to look at at all. Without one, a reset leaves the pivot in
     * front of wherever the camera stands instead of at the world origin, which could be
     * nowhere near the view.
     */
    protected abstract boolean hasSubject();

    /** Whether the pivot may be walked about right now. */
    protected boolean canMove()
    {
        return true;
    }

    /** Whether the wheel zooms right now. */
    protected boolean canZoom()
    {
        return true;
    }

    /** Which buttons begin an orbit: the left one turns, the middle one pans. */
    protected boolean canStart(UIContext context)
    {
        return context.mouseButton == 0 || context.mouseButton == 2;
    }

    /**
     * Bring the anchor up to date for this frame. An orbit that hangs off nothing leaves it
     * alone, and every value below is then already a world value.
     */
    protected void updateAnchor(float transition)
    {}

    /* Input */

    public void start(UIContext context)
    {
        if (!this.canStart(context))
        {
            return;
        }

        this.orbitButton = context.mouseButton;
        this.orbiting = true;
        this.dragged = false;
        this.last.set(context.mouseX, context.mouseY);
        this.pressOrigin.set(context.mouseX, context.mouseY);

        if (this.isPanning())
        {
            this.cachePanState(context);
        }
    }

    public boolean wasDragged()
    {
        return this.dragged;
    }

    public void stop()
    {
        this.orbiting = false;
        this.orbitButton = -1;
    }

    public boolean keyPressed(UIContext context, Area area)
    {
        if (!this.enabled || context.isFocused())
        {
            return false;
        }

        if (area.isInside(context) || (!this.velocityPosition.equals(0, 0, 0) && context.getKeyAction() == KeyAction.RELEASED))
        {
            if (!this.canMove())
            {
                return false;
            }

            int x = this.getFactor(context, Keys.FLIGHT_LEFT, Keys.FLIGHT_RIGHT, this.velocityPosition.x);
            int y = this.getFactor(context, Keys.FLIGHT_UP, Keys.FLIGHT_DOWN, this.velocityPosition.y);
            int z = this.getFactor(context, Keys.FLIGHT_FORWARD, Keys.FLIGHT_BACKWARD, this.velocityPosition.z);
            boolean changed = x != this.velocityPosition.x || y != this.velocityPosition.y || z != this.velocityPosition.z;

            this.velocityPosition.set(x, y, z);

            return changed;
        }

        return false;
    }

    protected int getFactor(UIContext context, KeyCombo positive, KeyCombo negative, int x)
    {
        if (context.isPressed(positive.getMainKey()))
        {
            return 1;
        }
        else if (context.isPressed(negative.getMainKey()))
        {
            return -1;
        }
        else if (
            (context.isReleased(positive.getMainKey()) && x > 0) ||
            (context.isReleased(negative.getMainKey()) && x < 0)
        ) {
            return 0;
        }

        return x;
    }

    public void handleOrbiting(UIContext context)
    {
        if (!this.orbiting)
        {
            return;
        }

        int x = context.mouseX;
        int y = context.mouseY;
        int dx = x - this.last.x;
        int dy = y - this.last.y;

        if (!this.dragged && (Math.abs(x - this.pressOrigin.x) > DRAG_THRESHOLD || Math.abs(y - this.pressOrigin.y) > DRAG_THRESHOLD))
        {
            this.dragged = true;
        }

        if (this.orbitButton == 2)
        {
            this.pan(context);
        }
        else
        {
            this.rotate(dx, dy);
        }

        this.last.set(x, y);
    }

    public boolean zoom(double mouseWheel)
    {
        if (!this.enabled || !this.canZoom() || mouseWheel == 0D)
        {
            return false;
        }

        float step = Window.isCtrlPressed() ? 0.22F : 0.1F;
        float factor = (float) Math.pow(1F - step, mouseWheel);

        this.targetDistance = MathUtils.clamp(this.targetDistance * factor, MIN_DISTANCE, MAX_DISTANCE);

        return true;
    }

    public boolean update(UIContext context)
    {
        if (!this.enabled)
        {
            return false;
        }

        this.applySmoothing();

        if (context.isFocused())
        {
            return false;
        }

        if (!this.canMove())
        {
            this.velocityPosition.set(0, 0, 0);

            return false;
        }

        if (this.velocityPosition.lengthSquared() > 0)
        {
            Vector3f delta = this.rotateVector(-this.velocityPosition.x, this.velocityPosition.y, -this.velocityPosition.z, this.targetRotation.y, this.targetRotation.x).mul(this.getSpeed());

            this.targetPivot.add(delta);

            return true;
        }

        return false;
    }

    private void applySmoothing()
    {
        float smoothness = BBSSettings.editorCameraSmoothness.get();

        if (smoothness <= 0F)
        {
            this.rotation.set(this.targetRotation);
            this.pivot.set(this.targetPivot);
            this.distance = this.targetDistance;

            return;
        }

        float dt = MinecraftClient.getInstance().getRenderTickCounter().getLastFrameDuration();
        float factor = MathUtils.clamp(1F - (float) Math.pow(Math.min(smoothness, 0.99F), dt), 0F, 1F);

        this.rotation.lerp(this.targetRotation, factor);
        this.pivot.lerp(this.targetPivot, factor);
        this.distance = Lerps.lerp(this.distance, this.targetDistance, factor);
    }

    protected Vector3f rotateVector(float x, float y, float z, float yaw, float pitch)
    {
        return this.rotateVector(x, y, z, yaw, pitch, BBSSettings.editorHorizontalFlight.get());
    }

    protected Vector3f rotateVector(float x, float y, float z, float yaw, float pitch, boolean horizontal)
    {
        Matrix3f rotation = new Matrix3f();
        Vector3f rotate = new Vector3f(x, y, z);

        rotation.rotateY(yaw);

        if (!horizontal)
        {
            rotation.rotateX(pitch);
        }

        rotation.transform(rotate);

        return rotate;
    }

    /* Panning */

    private boolean isPanning()
    {
        return this.orbitButton == 2;
    }

    /**
     * Panning casts a ray through the camera, and a ray needs the camera's matrices, not just
     * its position - a host whose camera is only ever given a place to stand (the world camera
     * of the dashboard) leaves them at identity, and every ray through it goes nowhere.
     */
    protected void syncRayMatrices(Camera camera)
    {}

    private void cachePanState(UIContext context)
    {
        this.panState.pivot.set(this.toWorld(new Vector3f(this.pivot)));
        this.panState.camera.copy(this.getViewportCamera());
        this.syncRayMatrices(this.panState.camera);
        this.panState.plane.set(this.panState.camera.getLookDirection()).normalize();
        this.panState.intersection.set(this.calculateOnPlane(context));
    }

    private void pan(UIContext context)
    {
        Vector3d point = this.calculateOnPlane(context);
        Vector3f pivot = new Vector3f(this.panState.pivot);

        pivot.sub((float) point.x, (float) point.y, (float) point.z);
        pivot.add((float) this.panState.intersection.x, (float) this.panState.intersection.y, (float) this.panState.intersection.z);

        this.targetPivot.set(this.toLocal(pivot));
    }

    private Vector3d calculateOnPlane(UIContext context)
    {
        Area viewport = this.getViewport();
        Vector3d vector = new Vector3d();
        Vector3f originOffset = new Vector3f();
        Vector3f direction = this.panState.camera.getMouseRay(context.mouseX, context.mouseY, viewport.x, viewport.y, viewport.w, viewport.h, originOffset);
        Vector3d origin = new Vector3d(this.panState.camera.position)
            .add(originOffset.x, originOffset.y, originOffset.z)
            .sub(this.panState.pivot.x, this.panState.pivot.y, this.panState.pivot.z);
        Vector3d destination = new Vector3d(direction).mul(Math.max(this.distance, MIN_DISTANCE) * 2F).add(origin);

        Intersectiond.intersectLineSegmentPlane(
            origin.x,
            origin.y,
            origin.z,
            destination.x,
            destination.y,
            destination.z,
            this.panState.plane.x,
            this.panState.plane.y,
            this.panState.plane.z,
            0,
            vector
        );

        return vector;
    }

    /* Turning */

    public void rotate(int dx, int dy)
    {
        if (dx == 0 && dy == 0)
        {
            return;
        }

        float orbitSpeed = OrbitCamera.dragAngleSpeed() * 4F;

        this.targetRotation.x = MathUtils.clamp(this.targetRotation.x - dy * orbitSpeed, -PITCH_LIMIT, PITCH_LIMIT);
        this.targetRotation.y -= dx * orbitSpeed;

        /* Orbiting off the axis gives the perspective back: an ortho view is
         * what an axis snap is for, but away from an axis it only costs the
         * depth cues. A projection the user picked themselves is left alone
         * (see autoOrtho). */
        if (this.autoOrtho)
        {
            this.ortho = false;
            this.autoOrtho = false;
        }
    }

    /**
     * Snap the orbit rotation so the camera ends up on the given axis side of
     * the pivot, looking at it. The axis is given in the anchor's space: for an
     * orbit that hangs off nothing that is world space, for an attached one it
     * is the subject's space, matching the axes the navigation ball displays.
     * Yaw is unwrapped to the closest turn, so the camera never spins the long
     * way around.
     *
     * The snap turns the orthographic projection on (editorOrbitAxisOrtho): an
     * axis view is asked for to read the pose as a blueprint (front, side,
     * top), and perspective skews exactly the alignment it is being read for.
     * Orbiting away turns it back off, unless the user had turned it on
     * themselves — and with the setting off nothing arms autoOrtho in the
     * first place, so the projection is left alone in both directions.
     */
    public void snapToAxis(int x, int y, int z)
    {
        float pitch;
        float yaw;

        if (y != 0)
        {
            pitch = y > 0 ? -PITCH_LIMIT : PITCH_LIMIT;
            yaw = this.targetRotation.y;
        }
        else
        {
            float twoPi = MathUtils.PI * 2F;

            pitch = 0F;
            yaw = (float) Math.atan2(x, z);
            yaw += Math.round((this.targetRotation.y - yaw) / twoPi) * twoPi;
        }

        this.targetRotation.set(pitch, yaw);

        if (!this.ortho && BBSSettings.editorOrbitAxisOrtho.get())
        {
            this.ortho = true;
            this.autoOrtho = true;
        }
    }

    public boolean isOrtho()
    {
        return this.ortho;
    }

    public void toggleOrtho()
    {
        this.ortho = !this.ortho;

        /* Toggling by hand takes the projection away from the axis snap: it
         * stays whatever the user set until they toggle it again. */
        this.autoOrtho = false;
    }

    /* The frame the orbit is kept in */

    /**
     * Yaw of the anchor (zero when it hangs off nothing), i.e. the rotation
     * between the orbit's local space and world space.
     */
    public float getAnchorYaw()
    {
        return this.anchorYaw;
    }

    protected Vector3f toWorld(Vector3f pivot)
    {
        return pivot.rotateY(this.anchorYaw).add((float) this.anchorPosition.x, (float) this.anchorPosition.y, (float) this.anchorPosition.z);
    }

    protected Vector3f toLocal(Vector3f pivot)
    {
        return pivot.sub((float) this.anchorPosition.x, (float) this.anchorPosition.y, (float) this.anchorPosition.z).rotateY(-this.anchorYaw);
    }

    /**
     * Move the orbit into another anchor without moving the camera: everything is lifted into
     * the world, the anchor is swapped by {@code write}, and everything is brought back down.
     */
    protected void rebaseAnchor(Runnable write)
    {
        this.toWorld(this.pivot);
        this.toWorld(this.targetPivot);
        this.rotation.y += this.anchorYaw;
        this.targetRotation.y += this.anchorYaw;

        write.run();

        this.toLocal(this.pivot);
        this.toLocal(this.targetPivot);
        this.rotation.y -= this.anchorYaw;
        this.targetRotation.y -= this.anchorYaw;
    }

    /* Where the camera ends up */

    @Override
    public void setup(Camera camera, float transition)
    {
        /* Re-armed every frame: BBSRendering resets it at the start of every
         * world render, so ortho turns itself off the moment the orbit stops
         * driving the camera. Shaderpacks get the ortho matrix too — Iris
         * captures gbufferProjection from the same WorldRenderer#render
         * argument the mixin replaces, so pack math built on the matrices
         * stays consistent (analytic perspective assumptions, e.g. the sky
         * direction or depth linearization, are up to the pack). */
        BBSRendering.setOrthoDistance(this.ortho ? this.distance : -1F);

        this.updateAnchor(transition);

        if (!this.positioned)
        {
            Vector3f subject = this.getSubjectPivot(transition);

            if (subject != null)
            {
                this.toLocal(subject);
                this.pivot.set(subject);
                this.targetPivot.set(subject);
                this.positioned = true;
            }
            else if (!this.hasSubject())
            {
                this.seedPivotFromCamera(camera);
                this.positioned = true;
            }
        }

        Vector3f offset = this.getOffset();

        camera.position.set(this.toWorld(new Vector3f(this.pivot)));
        camera.position.add(offset);
        camera.rotation.set(-this.rotation.x, -(this.rotation.y + this.anchorYaw), 0F);
    }

    @Override
    public int getPriority()
    {
        return 20;
    }

    public Vector3d getOrbitCenter(float transition)
    {
        return new Vector3d(this.toWorld(new Vector3f(this.pivot)));
    }

    /** Put the pivot back onto the subject, wherever the camera has wandered to. */
    public void teleportPivotToSubject()
    {
        Vector3f subject = this.getSubjectPivot(this.getCurrentTransition());

        if (subject != null)
        {
            this.targetPivot.set(this.toLocal(subject));
            this.positioned = true;
        }
    }

    private Vector3f getOffset()
    {
        return this.rotateVector(0F, 0F, 1F, this.rotation.y + this.anchorYaw, this.rotation.x, false).mul(this.distance);
    }

    /**
     * Track a camera somebody else is driving (the editor's flight), instead of driving it.
     * The pivot is kept the same distance ahead of wherever that camera ended up, so when the
     * orbit takes over again it is already centred on what is being looked at and the view
     * doesn't jump. The anchor keeps ticking meanwhile, so a subject that moved underneath
     * doesn't drag the camera along while it is being flown.
     */
    public void follow(Camera camera, float transition)
    {
        this.updateAnchor(transition);
        this.seedPivotFromCamera(camera);
        this.positioned = true;
    }

    /**
     * Place the orbit center in front of the given camera, keeping its position and rotation.
     * Used when there is nothing to focus on - leaving the pivot at the world origin could put
     * it nowhere near the view - and while something else drives the camera (see follow).
     *
     * <p>It is the exact inverse of {@link #setup(Camera, float)}: the rotation loses the
     * anchor's yaw and the pivot is brought down into the anchor's frame, so an attached orbit
     * seeds just as well as a detached one.</p>
     */
    private void seedPivotFromCamera(Camera camera)
    {
        this.targetRotation.set(-camera.rotation.x, -camera.rotation.y - this.anchorYaw);
        this.rotation.set(this.targetRotation);

        Vector3f pivot = new Vector3f((float) camera.position.x, (float) camera.position.y, (float) camera.position.z);

        pivot.sub(this.getOffset());

        this.pivot.set(this.toLocal(pivot));
        this.targetPivot.set(this.pivot);
    }

    protected float getCurrentTransition()
    {
        UIContext context = this.getContext();

        return context == null ? 0F : context.getTransition();
    }

    public void reset()
    {
        this.pivot.set(0F, 0F, 0F);
        this.targetPivot.set(0F, 0F, 0F);
        this.rotation.set(0F, MathUtils.PI);
        this.targetRotation.set(0F, MathUtils.PI);
        this.distance = 4F;
        this.targetDistance = 4F;
        this.positioned = false;
        this.orbiting = false;
        this.orbitButton = -1;
        this.velocityPosition.set(0, 0, 0);
        this.anchorPosition.set(0D, 0D, 0D);
        this.anchorYaw = 0F;
    }

    private static class PanState
    {
        private final Vector3f pivot = new Vector3f();
        private final Camera camera = new Camera();
        private final Vector3d plane = new Vector3d();
        private final Vector3d intersection = new Vector3d();
    }
}
