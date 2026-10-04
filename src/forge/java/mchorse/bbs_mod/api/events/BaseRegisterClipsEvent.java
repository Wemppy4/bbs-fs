package mchorse.bbs_mod.api.events;

import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.MapFactory;

/**
 * The shared body of the clip registration events.
 *
 * <p>{@link RegisterCameraClipsEvent} and {@link RegisterActionClipsEvent} are siblings rather
 * than one extending the other, for the same reason the settings events are — see
 * {@link BaseRegisterSettingsEvent}. Subscribe to this class to be called for both.</p>
 */
public abstract class BaseRegisterClipsEvent
{
    public final MapFactory<Clip, ClipFactoryData> factory;

    public BaseRegisterClipsEvent(MapFactory<Clip, ClipFactoryData> factory)
    {
        this.factory = factory;
    }
}
