package mchorse.bbs_mod.ui.morphing.camera;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.function.Supplier;

public class ImmersiveMorphingCameraController implements ICameraController
{
    private Supplier<UIModelRenderer> modelRenderer;

    public ImmersiveMorphingCameraController(Supplier<UIModelRenderer> modelRenderer)
    {
        this.modelRenderer = modelRenderer;
    }

    @Override
    public void setup(Camera camera, float transition)
    {
        UIModelRenderer renderer = this.modelRenderer.get();
        EntityPlayerSP player = Minecraft.getMinecraft().player;

        float bodyYaw = MathUtils.toRad(Lerps.lerp(player.prevRenderYawOffset, player.renderYawOffset, transition));

        camera.position.set(player.prevPosX, player.prevPosY, player.prevPosZ);
        camera.position.lerp(new Vector3d(player.posX, player.posY, player.posZ), transition);
        camera.rotation.set(0, bodyYaw, 0);

        if (renderer == null)
        {
            Vector3f rotation = Matrices.rotation(0F, -bodyYaw);

            rotation.mul(2F);
            camera.position.add(rotation.x, rotation.y + 1F, rotation.z);
            camera.setFov(Minecraft.getMinecraft().gameSettings.fovSetting);
        }
        else
        {
            renderer.setupPosition();

            Camera rendererCamera = renderer.camera;

            Vector3f rotate = Matrices.rotate(new Vector3f().set(rendererCamera.position), 0F, -bodyYaw);

            camera.position.add(rotate);
            camera.rotation.add(rendererCamera.rotation);
            camera.fov = rendererCamera.fov;
        }
    }

    @Override
    public int getPriority()
    {
        return 100500;
    }

    @Override
    public void update()
    {}
}
