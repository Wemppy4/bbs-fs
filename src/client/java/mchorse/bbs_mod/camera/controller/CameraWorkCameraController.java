package mchorse.bbs_mod.camera.controller;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;

public abstract class CameraWorkCameraController implements ICameraController
{
    protected CameraClipContext context;
    protected Position position = new Position();
    /* Whether a clip set where the camera stands on the last applied tick, rather than the
     * position being only what the camera already was (no clip there, or only modifiers) */
    protected boolean placed;

    public CameraWorkCameraController()
    {
        this.context = new CameraClipContext();
    }

    public CameraWorkCameraController setWork(Clips clips)
    {
        this.context.clips = clips;

        return this;
    }

    public CameraClipContext getContext()
    {
        return this.context;
    }

    /** The clips whose curves (sun, brightness, weather...) set how the world looks */
    public CameraClipContext getCurveContext()
    {
        return this.context;
    }

    public Position getPosition()
    {
        return this.position;
    }

    /** Whether the film has a camera of its own on the current tick, so {@link #getPosition()} is where it stands */
    public boolean isPlaced()
    {
        return this.context.clips != null && this.placed;
    }

    protected void apply(Camera camera, int ticks, float transition)
    {
        if (camera != null)
        {
            this.position.set(camera);
        }

        this.context.clipData.clear();
        this.context.setup(ticks, transition);
        this.placed = false;

        for (Clip clip : this.context.clips.getClips(ticks))
        {
            if (this.context.apply(clip, this.position) && clip.enabled.get() && clip instanceof CameraClip cameraClip && cameraClip.placesCamera())
            {
                this.placed = true;
            }
        }

        AudioClientClip.manageSounds(this.context);

        /* After sound management, since applyLast() re-runs context.setup() */
        this.applyEditedClipEnd(ticks);

        this.context.currentLayer = 0;

        if (camera != null)
        {
            this.position.apply(camera);
        }
    }

    /**
     * Hook for letting the clip that's currently being edited render its final
     * state (at {@code relativeTick == duration}) when the cursor sits on the
     * clip's exclusive end boundary, which {@link Clips#getClips(int)} excludes.
     * No-op during playback; only the editor preview controller overrides it.
     */
    protected void applyEditedClipEnd(int ticks)
    {}

    @Override
    public int getPriority()
    {
        return 10;
    }
}