package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** Ordered native dispatch of the original per-form BEFORE/AFTER events. */
public final class FormRenderEvents
{
    public static final RenderEvent BEFORE = new RenderEvent();
    public static final RenderEvent AFTER = new RenderEvent();

    public interface Render { void onFormRender(Form form, FormRenderingContext context); }

    public static final class RenderEvent
    {
        private final CopyOnWriteArrayList<Render> listeners = new CopyOnWriteArrayList<>();
        private final Render invoker = (form, context) ->
        {
            for (Render listener : this.listeners) listener.onFormRender(form, context);
        };
        public void register(Render listener) { this.listeners.add(Objects.requireNonNull(listener, "listener")); }
        public Render invoker() { return this.invoker; }
    }

    private FormRenderEvents() {}
}
