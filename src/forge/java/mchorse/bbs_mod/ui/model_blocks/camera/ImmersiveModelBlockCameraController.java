package mchorse.bbs_mod.ui.model_blocks.camera;

import mchorse.bbs_mod.forge.ModelTileEntity;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.util.math.BlockPos;

public class ImmersiveModelBlockCameraController implements ICameraController
{
    private UIModelRenderer modelRenderer;
    private ModelTileEntity modelBlock;

    public ImmersiveModelBlockCameraController(UIModelRenderer modelRenderer, ModelTileEntity modelBlock)
    {
        this.modelRenderer = modelRenderer;
        this.modelBlock = modelBlock;
    }

    @Override
    public void setup(Camera camera, float transition)
    {
        UIModelRenderer renderer = this.modelRenderer;
        Transform transform = this.modelBlock.getProperties().getTransform();

        renderer.setupPosition();

        BlockPos pos = this.modelBlock.getPos();

        camera.position.set(pos.getX() + transform.translate.x + 0.5D, pos.getY() + transform.translate.y, pos.getZ() + transform.translate.z + 0.5D);
        camera.rotation.set(0, 0, 0);

        Camera rendererCamera = renderer.camera;

        camera.position.add(rendererCamera.position);
        camera.rotation.add(rendererCamera.rotation);
        camera.fov = rendererCamera.fov;
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