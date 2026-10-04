package mchorse.bbs_mod.camera.controller;

import mchorse.bbs_mod.BBSSettings;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;


import mchorse.bbs_mod.utils.clips.Clip;

import java.util.function.Consumer;

public class RunnerCameraController extends CameraWorkCameraController
{
    /** Editor boundary; the film panel supplies the same active clip and POV controller. */
    public interface Editor
    {
        Clip getEditedCameraClip();
        boolean isCameraFree();
        void handleCamera(Camera camera, float transition);
    }

    public int ticks;

    private float cursorFraction;
    private float lastTransition;

    private Position manual;
    private final Editor panel;

    private Consumer<Boolean> callback;

    public RunnerCameraController(Editor panel, Consumer<Boolean> callback)
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
        if (this.context.playing && !playing)
        {
            this.cursorFraction = BBSSettings.editorSnapToTicks.get() ? 0F : this.getTransition(this.lastTransition);
        }

        this.context.playing = playing;

        if (this.callback != null)
        {
            this.callback.accept(this.context.playing);
        }
    }

    public void toggle(int ticks)
    {
        if (ticks != this.ticks)
        {
            this.setCursor(ticks);
        }

        this.setPlaying(!this.context.playing);
    }

    public void setCursor(float tick)
    {
        tick = Math.max(0F, tick);
        this.ticks = (int) tick;
        this.cursorFraction = tick - this.ticks;
        this.lastTransition = this.cursorFraction;
    }

    public float getTransition(float transition)
    {
        /* Resuming from a sub-tick must not move backwards before the next game tick. */
        return this.context.playing ? Math.max(this.cursorFraction, transition) : this.cursorFraction;
    }

    public float getCursor(float transition)
    {
        return this.ticks + this.getTransition(transition);
    }

    public void setManual(Position manual)
    {
        this.manual = manual;
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
            this.cursorFraction = 0F;
            this.lastTransition = 0F;

            if (this.ticks >= this.context.clips.calculateDuration())
            {
                this.setPlaying(false);
            }
        }
    }

    @Override
    protected void applyEditedClipEnd(int ticks)
    {
        if (this.context.playing || this.cursorFraction != 0F)
        {
            return;
        }

        Clip clip = this.panel.getEditedCameraClip();

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
        this.lastTransition = transition;

        if (this.manual != null)
        {
            this.manual.apply(camera);
        }
        else if (this.context.clips != null)
        {
            /* kms */
            boolean free = this.panel.isCameraFree();

            this.apply(free ? null : camera, this.ticks, this.getTransition(transition));
        }

        this.panel.handleCamera(camera, transition);
    }
}
