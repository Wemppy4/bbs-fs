package mchorse.bbs_mod.ui.film.controller;

import org.joml.Intersectiond;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;

public class OrbitFilmCameraController implements ICameraController
{
    private static final float PITCH_LIMIT = MathUtils.PI * 0.5F - 0.01F;
    private static final float MIN_DISTANCE = 0.5F;
    private static final float MAX_DISTANCE = 256F;
    private static final int DRAG_THRESHOLD = 3;

    private final UIFilmController controller;

    public boolean enabled;

    private boolean orbiting;
    private int orbitButton = -1;
    private final Vector2i last = new Vector2i();
    private final Vector2i pressOrigin = new Vector2i();
    private boolean dragged;

    /* The state the input drives. */
    private final Vector2f targetRotation = new Vector2f();
    private final Vector3f targetPivot = new Vector3f();
    private float targetDistance;

    /* The state that is rendered, smoothly chasing the target above. */
    private final Vector2f rotation = new Vector2f();
    private final Vector3f pivot = new Vector3f();
    private float distance;

    /* Whether the pivot has been placed onto the subject after a reset. */
    private boolean positioned;

    /*
     * When attached, pivot and rotation are stored relative to the selected
     * replay's anchor (interpolated position + body yaw), and world values are
     * composed on the fly. Detached, the anchor is identity, so the same math
     * passes world values through untouched. Rebasing between anchors preserves
     * the world state, so attaching, detaching and switching replays never
     * moves the camera.
     */
    private boolean attached = true;
    private Replay anchorReplay;
    private final Vector3d anchorPosition = new Vector3d();
    private float anchorYaw;

    private final PanState panState = new PanState();
    protected final Vector3i velocityPosition = new Vector3i();

    public OrbitFilmCameraController(UIFilmController controller)
    {
        this.controller = controller;
        this.reset();
    }

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
            if (BBSSettings.editorOrbitMovementRequiresFlight.get() && !this.controller.panel.isFlying())
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
        if (!this.enabled || this.controller.panel.isFlying() || mouseWheel == 0D)
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

        if (BBSSettings.editorOrbitMovementRequiresFlight.get() && !this.controller.panel.isFlying())
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

        float dt = MinecraftClient.getInstance().getLastFrameDuration();
        float factor = MathUtils.clamp(1F - (float) Math.pow(Math.min(smoothness, 0.99F), dt), 0F, 1F);

        this.rotation.lerp(this.targetRotation, factor);
        this.pivot.lerp(this.targetPivot, factor);
        this.distance = Lerps.lerp(this.distance, this.targetDistance, factor);
    }

    protected float getSpeed()
    {
        return this.controller.panel.dashboard.orbit.getSpeed();
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

    private Vector3d calculateOnPlane(UIContext context)
    {
        Area viewport = this.controller.panel.preview.getViewport();
        Vector3d vector = new Vector3d();
        Vector3d origin = new Vector3d(this.panState.camera.position).sub(this.panState.pivot.x, this.panState.pivot.y, this.panState.pivot.z);
        Vector3d destination = new Vector3d(
            this.panState.camera.getMouseDirection(context.mouseX, context.mouseY, viewport.x, viewport.y, viewport.w, viewport.h)
        ).mul(Math.max(this.distance, MIN_DISTANCE) * 2F).add(origin);

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

    @Override
    public void setup(Camera camera, float transition)
    {
        this.updateAnchor(transition);

        if (!this.positioned)
        {
            Vector3f replay = this.getReplayPivot(transition);

            if (replay != null)
            {
                this.toLocal(replay);
                this.pivot.set(replay);
                this.targetPivot.set(replay);
                this.positioned = true;
            }
            else if (this.hasNoReplays())
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

    public void teleportPivotToReplay()
    {
        Vector3f replay = this.getReplayPivot(this.getCurrentTransition());

        if (replay != null)
        {
            this.targetPivot.set(this.toLocal(replay));
            this.positioned = true;
        }
    }

    public boolean isAttached()
    {
        return this.attached;
    }

    public void toggleAttachment()
    {
        this.attached = !this.attached;
        this.updateAnchor(this.getCurrentTransition());
    }

    private void updateAnchor(float transition)
    {
        Replay target = null;
        IEntity entity = null;

        if (this.attached)
        {
            target = this.controller.panel.replayEditor.getReplay();
            entity = target == null ? null : this.resolveEntity(target);

            if (entity == null)
            {
                target = this.anchorReplay;
                entity = target == null ? null : this.resolveEntity(target);
            }

            if (entity == null)
            {
                target = null;
            }
        }

        if (target != this.anchorReplay)
        {
            this.rebase(target, entity, transition);
        }
        else if (entity != null)
        {
            this.writeAnchor(entity, transition);
        }
    }

    private void rebase(Replay replay, IEntity entity, float transition)
    {
        this.toWorld(this.pivot);
        this.toWorld(this.targetPivot);
        this.rotation.y += this.anchorYaw;
        this.targetRotation.y += this.anchorYaw;

        this.anchorReplay = replay;

        if (entity == null)
        {
            this.anchorPosition.set(0D, 0D, 0D);
            this.anchorYaw = 0F;
        }
        else
        {
            this.writeAnchor(entity, transition);
        }

        this.toLocal(this.pivot);
        this.toLocal(this.targetPivot);
        this.rotation.y -= this.anchorYaw;
        this.targetRotation.y -= this.anchorYaw;
    }

    private void writeAnchor(IEntity entity, float transition)
    {
        this.anchorPosition.set(
            Lerps.lerp(entity.getPrevX(), entity.getX(), transition),
            Lerps.lerp(entity.getPrevY(), entity.getY(), transition),
            Lerps.lerp(entity.getPrevZ(), entity.getZ(), transition)
        );
        this.anchorYaw = MathUtils.toRad(-Lerps.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), transition));
    }

    private IEntity resolveEntity(Replay replay)
    {
        Film film = this.controller.panel.getData();

        if (film == null)
        {
            return null;
        }

        int index = film.replays.getList().indexOf(replay);

        return index < 0 ? null : this.controller.getEntities().get(index);
    }

    private Vector3f toWorld(Vector3f pivot)
    {
        return pivot.rotateY(this.anchorYaw).add((float) this.anchorPosition.x, (float) this.anchorPosition.y, (float) this.anchorPosition.z);
    }

    private Vector3f toLocal(Vector3f pivot)
    {
        return pivot.sub((float) this.anchorPosition.x, (float) this.anchorPosition.y, (float) this.anchorPosition.z).rotateY(-this.anchorYaw);
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
        this.anchorReplay = null;
        this.anchorPosition.set(0D, 0D, 0D);
        this.anchorYaw = 0F;
    }

    private Vector3f getReplayPivot(float transition)
    {
        OrbitTarget target = this.getOrbitTarget(transition);

        return target == null ? null : new Vector3f((float) target.position.x, (float) target.position.y, (float) target.position.z);
    }

    private boolean hasNoReplays()
    {
        return this.controller.panel.getData() == null || this.controller.panel.getData().replays.getList().isEmpty();
    }

    /**
     * When there is nothing to focus on, place the orbit center in front of the
     * current camera (keeping its position and rotation), instead of leaving it at
     * the world origin which could be nowhere near the view.
     */
    private void seedPivotFromCamera(Camera camera)
    {
        this.targetRotation.set(-camera.rotation.x, -camera.rotation.y);
        this.rotation.set(this.targetRotation);

        Vector3f forward = this.rotateVector(0F, 0F, -1F, this.rotation.y, this.rotation.x, false).mul(this.distance);

        this.pivot.set((float) camera.position.x, (float) camera.position.y, (float) camera.position.z).add(forward);
        this.targetPivot.set(this.pivot);
    }

    private OrbitTarget getOrbitTarget(float transition)
    {
        IEntity entity = this.controller.getCurrentEntity();

        if (entity == null)
        {
            return null;
        }

        float renderYaw = MathUtils.toRad(-Lerps.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), transition) + 180F);
        Form form = entity.getForm();
        double h = entity.getPickingHitbox().h / 2;
        double x = Lerps.lerp(entity.getPrevX(), entity.getX(), transition);
        double y = Lerps.lerp(entity.getPrevY(), entity.getY(), transition) + h;
        double z = Lerps.lerp(entity.getPrevZ(), entity.getZ(), transition);

        if (form != null)
        {
            MatrixCache map = FormUtilsClient.getRenderer(form).collectMatrices(entity, transition);
            String group = "anchor";

            if (form instanceof ModelForm modelForm)
            {
                ModelInstance model = ModelFormRenderer.getModel(modelForm);

                if (model != null)
                {
                    String anchor = model.getAnchor();

                    group = anchor.isEmpty() ? group : anchor;
                }
            }

            Matrix4f anchor = map.get(group).matrix();

            if (anchor != null)
            {
                Anchor v = form.anchor.get();
                Matrix4f defaultMatrix = BaseFilmController.getMatrixForRenderWithRotation(entity, x, y, z, transition);
                Pair<Matrix4f, Float> totalMatrix = BaseFilmController.getTotalMatrix(this.controller.getEntities(), v, defaultMatrix, x, y, z, transition, 0);

                if (totalMatrix.a != null)
                {
                    defaultMatrix = totalMatrix.a;
                }

                defaultMatrix.mul(anchor);

                Vector3f translate = defaultMatrix.getTranslation(Vectors.TEMP_3F);

                x += translate.x;
                y += translate.y;
                z += translate.z;
            }
        }

        return new OrbitTarget(new Vector3d(x, y, z), renderYaw);
    }

    private boolean canStart(UIContext context)
    {
        if (this.controller.panel.isFlying())
        {
            return context.mouseButton == 0;
        }

        return context.mouseButton == 0 || context.mouseButton == 2;
    }

    private boolean isPanning()
    {
        return this.orbitButton == 2;
    }

    private void cachePanState(UIContext context)
    {
        this.panState.pivot.set(this.toWorld(new Vector3f(this.pivot)));
        this.panState.camera.copy(this.controller.panel.getCamera());
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

    private void rotate(int dx, int dy)
    {
        float orbitSpeed = this.controller.panel.dashboard.orbit.getAngleSpeed() * 4F;

        this.targetRotation.x = MathUtils.clamp(this.targetRotation.x - dy * orbitSpeed, -PITCH_LIMIT, PITCH_LIMIT);
        this.targetRotation.y -= dx * orbitSpeed;
    }

    private Vector3f getOffset()
    {
        return this.rotateVector(0F, 0F, 1F, this.rotation.y + this.anchorYaw, this.rotation.x, false).mul(this.distance);
    }

    private float getCurrentTransition()
    {
        UIContext context = this.controller.getContext();

        return context == null ? 0F : context.getTransition();
    }

    private record OrbitTarget(Vector3d position, float renderYaw)
    {}

    private static class PanState
    {
        private final Vector3f pivot = new Vector3f();
        private final Camera camera = new Camera();
        private final Vector3d plane = new Vector3d();
        private final Vector3d intersection = new Vector3d();
    }
}
