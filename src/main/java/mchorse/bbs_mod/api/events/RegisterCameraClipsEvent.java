package mchorse.bbs_mod.api.events;

import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.MapFactory;

/**
 * Posted on both sides once BBS has registered its own camera clips — the clips of the film's
 * camera track and of the overlay tracks next to it.
 */
public class RegisterCameraClipsEvent extends BaseRegisterClipsEvent
{
    public RegisterCameraClipsEvent(MapFactory<Clip, ClipFactoryData> factory)
    {
        super(factory);
    }
}
