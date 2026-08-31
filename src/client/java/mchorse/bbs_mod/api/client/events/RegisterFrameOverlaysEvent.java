package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.ui.film.FrameOverlays;

/**
 * Posted on the client once BBS has registered what it draws over the finished frame.
 *
 * <p>Needed only for something the image and subtitle families cannot draw. A clip that just
 * wants a picture in the frame adds its overlay to the image family from {@code applyClip} and is
 * drawn without any of this.</p>
 */
public class RegisterFrameOverlaysEvent
{
    public void register(FrameOverlays.IFrameOverlayRenderer renderer)
    {
        FrameOverlays.register(renderer);
    }
}
