package mchorse.bbs_mod.api.events;

import mchorse.bbs_mod.utils.clips.ClipFactory;

/**
 * The shared body of the clip registration events.
 *
 * <p>{@link RegisterCameraClipsEvent} and {@link RegisterActionClipsEvent} are siblings rather
 * than one extending the other, for the same reason the settings events are — see
 * {@link BaseRegisterSettingsEvent}. Subscribe to this class to be called for both.</p>
 *
 * <p>The palette a user picks a new clip from is split into columns. Add a column of your own
 * with {@code factory.category(Link)} and put a clip type in it with
 * {@code new ClipFactoryData(...).in(Link)}; its title is the language key
 * {@code bbs.ui.camera.clip_categories.<id>}. A clip type that names no column, or one that was
 * never added, is listed under "Other".</p>
 */
public abstract class BaseRegisterClipsEvent
{
    public final ClipFactory factory;

    public BaseRegisterClipsEvent(ClipFactory factory)
    {
        this.factory = factory;
    }
}
