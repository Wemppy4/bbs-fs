package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.api.client.render.ModelMeshReplacement;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.forms.forms.ModelForm;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** Draw some cubes of a cubic model form differently: asked on every draw of the form (every pass,
 * picking included), after its pose is evaluated. The first listener returning a replacement owns
 * the draw; null draws the model as baked, which is also the fast path when nobody listens. */
public final class ModelMeshEvents
{
    public static final Event<Replace> REPLACE = EventFactory.createArrayBacked(Replace.class,
        listeners -> (form, model, transition) ->
        {
            for (Replace listener : listeners)
            {
                ModelMeshReplacement replacement = listener.replace(form, model, transition);

                if (replacement != null) return replacement;
            }

            return null;
        });

    public interface Replace
    {
        ModelMeshReplacement replace(ModelForm form, Model model, float transition);
    }

    private ModelMeshEvents() {}
}
