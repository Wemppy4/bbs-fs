package mchorse.bbs_mod.ui.sequence;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.MinecraftClient;

/**
 * What a sequence plays on the camera while its editor is on screen: its own clips (the sound,
 * the pictures and the subtitles over the films) on the editor's cursor.
 */
public class SequenceCameraController extends CameraWorkCameraController
{
    private final UISequencePanel panel;

    public SequenceCameraController(UISequencePanel panel)
    {
        this.panel = panel;
        this.context.playing = false;
    }

    @Override
    public void setup(Camera camera, float transition)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player != null)
        {
            camera.set(mc.player, MathUtils.toRad(mc.options.getFov().getValue()));
        }

        this.context.playing = this.panel.isRunning();

        if (this.context.clips != null)
        {
            this.apply(camera, this.panel.getCursor(), this.panel.isRunning() ? transition : 0F);
        }
    }
}
