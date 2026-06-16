package mchorse.bbs_mod.camera.controller;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;

public abstract class CameraWorkCameraController implements ICameraController
{
    protected CameraClipContext context;
    protected Position position = new Position();

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

    public Position getPosition()
    {
        return this.position;
    }

    protected void apply(Camera camera, int ticks, float transition)
    {
        if (camera != null)
        {
            this.position.set(camera);
        }

        this.context.clipData.clear();
        this.context.setup(ticks, transition);

        for (Clip clip : this.context.clips.getClips(ticks))
        {
            this.context.apply(clip, this.position);
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