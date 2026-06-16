package mchorse.bbs_mod.camera.controller;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.utils.clips.Clip;

import java.util.function.Consumer;

public class RunnerCameraController extends CameraWorkCameraController
{
    public int ticks;

    private Position manual;
    private UIFilmPanel panel;

    private Consumer<Boolean> callback;

    public RunnerCameraController(UIFilmPanel panel, Consumer<Boolean> callback)
    {
        super();

        this.panel = panel;
        this.callback = callback;
        this.context.playing = false;
    }

    public boolean isRunning()
    {
        return this.context.playing;
    }

    public void setPlaying(boolean playing)
    {
        this.context.playing = playing;

        if (this.callback != null)
        {
            this.callback.accept(this.context.playing);
        }
    }

    public void toggle(int ticks)
    {
        this.setPlaying(!this.context.playing);

        this.ticks = ticks;
    }

    public void setManual(Position manual)
    {
        this.manual = manual;

        if (manual != null && this.panel.getController().getPovMode() != UIFilmController.CAMERA_MODE_FREE)
        {
            manual.copy(this.position);
        }
    }

    @Override
    public void update()
    {
        if (this.context.playing && this.manual == null)
        {
            if (this.context.clips == null)
            {
                this.setPlaying(false);
                return;
            }

            this.ticks += 1;

            if (this.ticks >= this.context.clips.calculateDuration())
            {
                this.setPlaying(false);
            }
        }
    }

    @Override
    protected void applyEditedClipEnd(int ticks)
    {
        if (this.context.playing)
        {
            return;
        }

        Clip clip = this.panel.cameraEditor.getClip();

        /* When editing a camera clip and the cursor rests on its exclusive end
         * boundary, show (and thus allow editing of) the clip's final point /
         * keyframe — which otherwise belongs to the next clip's first frame. */
        if (clip instanceof CameraClip cameraClip && ticks == clip.tick.get() + clip.duration.get())
        {
            cameraClip.applyLast(this.context, this.position);
        }
    }

    @Override
    public void setup(Camera camera, float transition)
    {
        if (this.manual != null)
        {
            this.manual.apply(camera);
        }
        else if (this.context.clips != null)
        {
            /* kms */
            boolean free = this.panel.getController().getPovMode() == UIFilmController.CAMERA_MODE_FREE;

            this.apply(free ? null : camera, this.ticks, this.context.playing ? transition : 0F);
        }

        this.panel.getController().handleCamera(camera, transition);
    }
}
