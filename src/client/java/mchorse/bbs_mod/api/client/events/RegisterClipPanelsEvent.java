package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.utils.clips.Clip;

/**
 * Posted on the client once BBS has registered the editor panels of its own clips.
 *
 * <p>Register the class the clip actually has at runtime: BBS swaps four of its own camera clips
 * for client-side subclasses that can play sound and video, and the panel is looked up by the
 * exact class.</p>
 */
public class RegisterClipPanelsEvent
{
    public <T extends Clip> void register(Class<T> clazz, UIClip.IUIClipFactory<T> factory)
    {
        UIClip.register(clazz, factory);
    }
}
