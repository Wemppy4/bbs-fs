package mchorse.bbs_mod.api.client.render;

import mchorse.bbs_mod.forms.renderers.FormRenderer;

/** A typed key for client-thread, renderer-owned transient state. Keep the key static, not a
 * global map of renderers: values may refer back to their form/renderer without leaking it.
 * Attachments are neither saved nor copied and do not manage native resources; those must
 * still be closed through their scene lifecycle. */
public final class RenderAttachment<T>
{
    public T get(FormRenderer<?> renderer)
    {
        return renderer.getAttachment(this);
    }

    public void set(FormRenderer<?> renderer, T value)
    {
        renderer.setAttachment(this, value);
    }

    public void remove(FormRenderer<?> renderer)
    {
        renderer.setAttachment(this, null);
    }
}
