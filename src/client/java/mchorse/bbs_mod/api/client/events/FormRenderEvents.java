package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Around the drawing of one form — every form BBS draws, anywhere it draws one.
 *
 * <p>This is the single funnel all form rendering goes through, which is why addons were mixing
 * into it. Both events fire even when the form's renderer throws: BBS reports the failure and
 * lets the frame through, and an addon that pushed something in {@code BEFORE} must get its
 * {@code AFTER} to pop it again.</p>
 *
 * <p>Plain Fabric events rather than the addon bus: this runs for every form of every frame.</p>
 */
public class FormRenderEvents
{
    public static final Event<Render> BEFORE = EventFactory.createArrayBacked(Render.class, (listeners) -> (form, context) ->
    {
        for (Render listener : listeners)
        {
            listener.onFormRender(form, context);
        }
    });

    public static final Event<Render> AFTER = EventFactory.createArrayBacked(Render.class, (listeners) -> (form, context) ->
    {
        for (Render listener : listeners)
        {
            listener.onFormRender(form, context);
        }
    });

    public static interface Render
    {
        public void onFormRender(Form form, FormRenderingContext context);
    }
}
