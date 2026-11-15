package mchorse.bbs_mod.camera.clips.overwrite;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.values.ValuePosition;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;

public class IdleClip extends CameraClip
{
    public final ValuePosition position = new ValuePosition("position");

    public IdleClip()
    {
        super();

        this.add(this.position);
    }

    @Override
    public void fromCamera(Camera camera)
    {
        this.position.get().set(camera);
    }

    @Override
    public boolean placesCamera()
    {
        return true;
    }

    @Override
    public void applyClip(ClipContext context, Position position)
    {
        position.copy(this.position.get());
    }

    @Override
    protected Clip create()
    {
        return new IdleClip();
    }
}