package mchorse.bbs_mod.camera;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.utils.Factor;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.joml.Matrices;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix3f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4i;

/**
 * Orbit camera class.
 *
 * This generic orbit camera class can be used in multiple ways where orbit-like
 * camera control is needed.
 */
public class OrbitCamera
{
    public Vector3d position = new Vector3d();
    public Vector3f rotation = new Vector3f();
    public final Factor speed = new Factor(20, 1, 40, (x) ->
    {
        if (x <= 10) return x / 100D;
        else if (x <= 20) return (x - 10) / 10D;
        else if (x <= 30) return (x - 20) / 1D;

        return (x - 30) * 10D;
    });
    public float fov;

    /* The state the input drives; position/rotation/fov smoothly chase these. */
    protected final Vector3d targetPosition = new Vector3d();
    protected final Vector3f targetRotation = new Vector3f();
    protected float targetFov;

    protected int dragging = -1;
    protected int lastX;
    protected int lastY;

    protected float low = 0.05F;
    protected float normal = 0.25F;
    protected float high = 1F;

    protected Vector3d finalPosition = new Vector3d();

    protected Vector3i velocityPosition = new Vector3i();
    protected Vector4i velocityAngle = new Vector4i();

    protected boolean fovRoll = true;

    public void setFovRoll(boolean fovRoll)
    {
        this.fovRoll = fovRoll;
    }

    public void reset()
    {
        this.velocityPosition.set(0);
        this.velocityAngle.set(0);
    }

    public void setup(Camera camera)
    {
        this.position.set(camera.position);
        this.rotation.set(camera.rotation);
        this.fov = camera.fov;

        this.syncTarget();
    }

    /**
     * Snap the smoothing target onto the current state, so the camera doesn't
     * glide when it's being synced rather than driven by input.
     */
    protected void syncTarget()
    {
        this.targetPosition.set(this.position);
        this.targetRotation.set(this.rotation);
        this.targetFov = this.fov;
    }

    public Vector3i getVelocityPosition()
    {
        return this.velocityPosition;
    }

    public Vector4i getVelocityAngle()
    {
        return this.velocityAngle;
    }

    public int canStart(UIContext context)
    {
        if (context.mouseButton == 0)
        {
            return 0;
        }

        if (this.fovRoll)
        {
            if (context.mouseButton == 1)
            {
                return 1;
            }

            if (context.mouseButton == 2)
            {
                return 2;
            }
        }

        return -1;
    }

    public void from(Position position)
    {
        this.reset();

        this.position.set(position.point.x, position.point.y, position.point.z);
        this.rotation.set(MathUtils.toRad(position.angle.pitch), MathUtils.toRad(position.angle.yaw), MathUtils.toRad(position.angle.roll));
        this.fov = MathUtils.toRad(position.angle.fov);

        Vector3d vector = new Vector3d(this.getFinalPosition()).sub(this.position);

        this.position.sub(vector);

        vector = this.getFinalPosition();
        position.point.set(vector.x, vector.y, vector.z);

        this.syncTarget();
    }

    public void apply(Position position)
    {
        Vector3d vector = this.getFinalPosition();

        position.point.set(vector.x, vector.y, vector.z);
        position.angle.set(MathUtils.toDeg(this.rotation.y), MathUtils.toDeg(this.rotation.x), MathUtils.toDeg(this.rotation.z), MathUtils.toDeg(this.fov));
    }

    public void cache(int mouseX, int mouseY)
    {
        this.lastX = mouseX;
        this.lastY = mouseY;
    }

    public void start(int mouseX, int mouseY)
    {
        this.start(0, mouseX, mouseY);
    }

    public void start(int mouseButton, int mouseX, int mouseY)
    {
        this.dragging = mouseButton;
        this.lastX = mouseX;
        this.lastY = mouseY;
    }

    public void release()
    {
        this.dragging = -1;
    }

    public boolean isDragging()
    {
        return this.dragging >= 0;
    }

    /**
     * Calculate the final position.
     */
    public Vector3d getFinalPosition()
    {
        return this.finalPosition.set(this.position);
    }

    /**
     * Get looking direction vector.
     */
    public Vector3f getLook()
    {
        return Matrices.rotation(this.rotation.x, MathUtils.PI - this.rotation.y);
    }

    public float getAngleSpeed()
    {
        float factor = Window.isCtrlPressed() ? this.high : (Window.isAltPressed() ? this.low : this.normal);

        return 1 / 80F * BBSSettings.editorCameraAngleSpeed.get() * factor;
    }

    public float getSpeed()
    {
        float factor = Window.isCtrlPressed() ? this.high : (Window.isAltPressed() ? this.low : this.normal);

        return factor * (float) this.speed.getValue() * 0.25F * BBSSettings.editorCameraSpeed.get();
    }

    protected Vector3f rotateVector(float x, float y, float z)
    {
        Matrix3f rotation = new Matrix3f();
        Vector3f rotate = new Vector3f(x, y, z);

        rotation.rotateY(MathUtils.PI - this.rotation.y);

        if (!BBSSettings.editorHorizontalFlight.get())
        {
            rotation.rotateX(this.rotation.x);
        }

        rotation.transform(rotate);

        return rotate;
    }

    /**
     * Drag the mouse Should be called in rendering.
     */
    public boolean drag(int mouseX, int mouseY)
    {
        float angleFactor = this.getAngleSpeed();

        if (this.dragging == 0)
        {
            int x = mouseX - this.lastX;
            int y = mouseY - this.lastY;

            if (x != 0 || y != 0)
            {
                this.targetRotation.x += y * angleFactor;
                this.targetRotation.y += x * angleFactor;

                this.lastX = mouseX;
                this.lastY = mouseY;
            }

            return true;
        }
        else if (this.fovRoll)
        {
            if (this.dragging == 1)
            {
                int x = mouseX - this.lastX;

                if (x != 0)
                {
                    this.targetRotation.z += x * angleFactor;

                    this.lastX = mouseX;
                    this.lastY = mouseY;
                }

                return true;
            }
            else if (this.dragging == 2)
            {
                int y = mouseY - this.lastY;

                if (y != 0)
                {
                    this.targetFov += y * angleFactor;

                    this.lastX = mouseX;
                    this.lastY = mouseY;
                }

                return true;
            }
        }

        return false;
    }

    public boolean scroll(int scroll)
    {
        int factor = this.speed.getX();

        this.speed.addX(scroll);

        return this.speed.getX() != factor;
    }

    public boolean keyPressed(UIContext context)
    {
        int x = this.getFactor(context, Keys.FLIGHT_LEFT, Keys.FLIGHT_RIGHT, this.velocityPosition.x);
        int y = this.getFactor(context, Keys.FLIGHT_UP, Keys.FLIGHT_DOWN, this.velocityPosition.y);
        int z = this.getFactor(context, Keys.FLIGHT_FORWARD, Keys.FLIGHT_BACKWARD, this.velocityPosition.z);

        int pitch = this.getFactor(context, Keys.FLIGHT_TILT_UP, Keys.FLIGHT_TILT_DOWN, this.velocityAngle.x);
        int yaw = this.getFactor(context, Keys.FLIGHT_PAN_LEFT, Keys.FLIGHT_PAN_RIGHT, this.velocityAngle.y);
        boolean changed = x != this.velocityPosition.x || y != this.velocityPosition.y || z != this.velocityPosition.z || pitch != this.velocityAngle.x || yaw != this.velocityAngle.y;

        this.velocityPosition.set(x, y, z);
        this.velocityAngle.x = pitch;
        this.velocityAngle.y = yaw;

        return changed;
    }

    protected int getFactor(UIContext context, KeyCombo positive, KeyCombo negative, int x)
    {
        if (context.isPressed(positive.getMainKey()))
        {
            x = 1;
        }
        else if (context.isPressed(negative.getMainKey()))
        {
            x = -1;
        }
        else if (
            (context.isReleased(positive.getMainKey()) && x > 0) ||
            (context.isReleased(negative.getMainKey()) && x < 0)
        ) {
            x = 0;
        }

        return x;
    }

    protected int getFactor(UIContext context, int positive, int negative, int x)
    {
        if (context.isPressed(positive))
        {
            x = 1;
        }
        else if (context.isPressed(negative))
        {
            x = -1;
        }
        else if ((context.isReleased(positive) && x > 0) || (context.isReleased(negative) && x < 0))
        {
            x = 0;
        }

        return x;
    }

    public boolean update(UIContext context)
    {
        this.applySmoothing();

        if (context.isFocused())
        {
            return false;
        }

        boolean changed = false;

        if (this.velocityPosition.lengthSquared() > 0)
        {
            float lastFrameDuration = MinecraftClient.getInstance().getRenderTickCounter().getLastFrameDuration() * 5F;

            this.targetPosition.add(this.rotateVector(this.velocityPosition.x, 0, this.velocityPosition.z)
                .add(0, this.velocityPosition.y, 0)
                .mul(this.getSpeed() * lastFrameDuration));

            changed = true;
        }

        if (this.velocityAngle.lengthSquared() > 0)
        {
            float angleSpeed = -this.getAngleSpeed() * (this.getSpeed() * (1F / this.normal));

            this.targetRotation.x += this.velocityAngle.x * angleSpeed;
            this.targetRotation.y += this.velocityAngle.y * angleSpeed;

            changed = true;
        }

        return changed;
    }

    /**
     * Ease the rendered position/rotation/fov toward the input-driven target,
     * frame-rate independently. Smoothness 0 snaps (instant, legacy behaviour).
     */
    protected void applySmoothing()
    {
        float smoothness = BBSSettings.editorCameraSmoothness.get();

        if (smoothness <= 0F)
        {
            this.position.set(this.targetPosition);
            this.rotation.set(this.targetRotation);
            this.fov = this.targetFov;

            return;
        }

        float dt = MinecraftClient.getInstance().getLastFrameDuration();
        float factor = MathUtils.clamp(1F - (float) Math.pow(Math.min(smoothness, 0.99F), dt), 0F, 1F);

        this.position.lerp(this.targetPosition, factor);
        this.rotation.lerp(this.targetRotation, factor);
        this.fov = Lerps.lerp(this.fov, this.targetFov, factor);
    }
}