package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.ui.film.UIFilmPreview;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

import java.util.function.Function;

/**
 * Posted on the client before any film editor is opened, for an addon that wants a layer of its
 * own over the editor's preview — one that takes the mouse, not just draws.
 *
 * <p>The element becomes a child of the preview, so it is laid out and receives input the way
 * everything else in the editor does.</p>
 */
public class RegisterPreviewOverlaysEvent
{
    /**
     * @param factory makes the element. It is asked once per film editor, since a preview is
     *                built anew every time one is opened.
     */
    public void register(Function<UIFilmPreview, UIElement> factory)
    {
        UIFilmPreview.registerOverlay(factory);
    }
}
