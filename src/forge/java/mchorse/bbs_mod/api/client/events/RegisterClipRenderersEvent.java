package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.ui.film.clips.renderer.IUIClipRenderer;
import mchorse.bbs_mod.ui.film.clips.renderer.UIClipRenderers;
import mchorse.bbs_mod.utils.clips.Clip;

/**
 * Posted on the client once BBS has registered how its own clips draw on a timeline.
 *
 * <p>This is the strip of the clip in the timeline, not what the clip does. A clip without one
 * gets the plain strip, which is what all but two of BBS's own use.</p>
 */
public class RegisterClipRenderersEvent
{
    public void register(Class<? extends Clip> clazz, IUIClipRenderer renderer)
    {
        UIClipRenderers.register(clazz, renderer);
    }
}
