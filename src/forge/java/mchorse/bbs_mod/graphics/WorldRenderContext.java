package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import java.nio.FloatBuffer;

/** One BBS frame boundary over Forge's actual GL camera. All matrices are borrowed
 * for this frame; drawing CPU-transformed vertices must use a scoped identity GL view. */
public final class WorldRenderContext
{
    private final Camera camera;
    private final MatrixStack stack;
    private final ICamera frustum;
    private final float transition;

    public WorldRenderContext(Camera camera, MatrixStack stack, ICamera frustum, float transition)
    {
        this.camera = camera;
        this.stack = stack;
        this.frustum = frustum;
        this.transition = transition;
    }
    public Camera camera() { return this.camera; }
    public MatrixStack matrixStack() { return this.stack; }
    public ICamera frustum() { return this.frustum; }
    public float tickDelta() { return this.transition; }
    public Matrix4f projection() { return this.camera.projection; }

    /** Capture during Forge's world pass, before replacing either native matrix.
     * The inverse view translation includes eye height, third-person collision and
     * view bobbing. Merely subtracting the view entity's eyes would apply those twice. */
    public static WorldRenderContext capture(float transition)
    {
        Minecraft mc = Minecraft.getMinecraft();
        RenderManager manager = mc.getRenderManager();
        FloatBuffer values = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, values);
        Matrix4f nativeView = new Matrix4f().set(values);
        values.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, values);
        Camera camera = new Camera();
        Entity view = mc.getRenderViewEntity();
        if (view != null) camera.set(view, MathUtils.toRad(mc.gameSettings.fovSetting), transition);
        camera.projection.set(values);
        Vector3f offset = new Matrix4f(nativeView).invert().getTranslation(new Vector3f());
        camera.position.set(manager.viewerPosX + offset.x, manager.viewerPosY + offset.y, manager.viewerPosZ + offset.z);
        camera.view.set(nativeView).setTranslation(0, 0, 0);
        MatrixStack stack = new MatrixStack();
        stack.peek().getPositionMatrix().set(camera.view);
        stack.peek().getNormalMatrix().set(camera.view).invert().transpose();
        Frustum frustum = new Frustum();
        frustum.setPosition(manager.viewerPosX, manager.viewerPosY, manager.viewerPosZ);
        return new WorldRenderContext(camera, stack, frustum, transition);
    }
}
