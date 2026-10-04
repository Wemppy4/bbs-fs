package mchorse.bbs_mod.camera;

import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class Camera
{
    public Matrix4f projection = new Matrix4f();
    public Matrix4f view = new Matrix4f();
    public float fov;
    public float near = 0.01F;
    public float far = 300F;

    public Vector3d position = new Vector3d();
    public Vector3f rotation = new Vector3f();

    private Vector3f relative = new Vector3f();

    public Camera()
    {
        this.setFov(70);
    }

    public void setFov(float degrees)
    {
        this.fov = MathUtils.toRad(degrees);
    }

    public void setFarNear(float near, float far)
    {
        this.near = near;
        this.far = far;
    }

    public Vector3f getLookDirection()
    {
        return Matrices.rotation(this.rotation.x, MathUtils.PI - this.rotation.y);
    }

    public Vector3f getMouseDirection(int mx, int my, int vx, int vy, int w, int h)
    {
        return CameraUtils.getMouseDirection(this.projection, this.view, mx, my, vx, vy, w, h);
    }

    public Vector3f getMouseDirectionNormalized(float mx, float my)
    {
        return CameraUtils.getMouseDirection(this.projection, this.view, mx, my);
    }

    /**
     * Projection-agnostic picking ray; see {@link CameraUtils#getMouseRay}.
     * The origin offset is relative to {@link #position}.
     */
    public Vector3f getMouseRay(int mx, int my, int vx, int vy, int w, int h, Vector3f originOffset)
    {
        return CameraUtils.getMouseRay(this.projection, this.view, mx, my, vx, vy, w, h, originOffset);
    }

    public Vector3f getRelative(Vector3d vector)
    {
        return this.getRelative(vector.x, vector.y, vector.z);
    }

    public Vector3f getRelative(double x, double y, double z)
    {
        return this.relative.set((float) (x - this.position.x), (float) (y - this.position.y), (float) (z - this.position.z));
    }

    public void updatePerspectiveProjection(int width, int height)
    {
        this.projection.identity().perspective(this.fov, width / (float) height, this.near, this.far);
    }

    public void updateOrthoProjection(int width, int height)
    {
        this.projection.identity().ortho(-width, width, -height, height, this.near, this.far);
    }

    public Matrix4f updateView()
    {
        return this.view.identity()
            .rotateZ(this.rotation.z)
            .rotateX(this.rotation.x)
            .rotateY(this.rotation.y);
    }

    public void copy(Camera camera)
    {
        this.projection.set(camera.projection);
        this.view.set(camera.view);
        this.fov = camera.fov;
        this.near = camera.near;
        this.far = camera.far;
        this.position.set(camera.position);
        this.rotation.set(camera.rotation);
    }

    /** Sample the same entity interpolation used by Forge's world renderer. */
    public void set(Entity cameraEntity, float fov, float transition)
    {
        Vec3d eyePos = cameraEntity.getPositionEyes(transition);
        float yaw = cameraEntity.prevRotationYaw + (cameraEntity.rotationYaw - cameraEntity.prevRotationYaw) * transition;
        float pitch = cameraEntity.prevRotationPitch + (cameraEntity.rotationPitch - cameraEntity.prevRotationPitch) * transition;

        this.position.set(eyePos.x, eyePos.y, eyePos.z);
        this.rotation.set(MathUtils.toRad(pitch), MathUtils.toRad(yaw + 180F), 0F);
        this.fov = fov;
    }
    public void set(Entity cameraEntity, float fov)
    {
        Vec3d eyePos = cameraEntity.getPositionEyes(1F);

        this.position.set(eyePos.x, eyePos.y, eyePos.z);
        this.rotation.set(MathUtils.toRad(cameraEntity.rotationPitch), MathUtils.toRad(cameraEntity.getRotationYawHead() + 180F), 0);
        this.fov = fov;
    }
}