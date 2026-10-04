package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.ui.forms.editors.utils.UIFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;

/** After the model and its picking pass, before returning to 2D. Runs for both plain
 * and pickable form previews. Restore any GL state changed by an overlay. */
public final class FormPreviewEvents
{
    public static final OrderedEvent<Overlay> OVERLAY = new OrderedEvent<>(
        listeners -> (renderer, context) ->
        { for (Overlay listener : listeners) listener.render(renderer, context); });
    public interface Overlay { void render(UIFormRenderer renderer, UIContext context); }
    private FormPreviewEvents() {}
}
