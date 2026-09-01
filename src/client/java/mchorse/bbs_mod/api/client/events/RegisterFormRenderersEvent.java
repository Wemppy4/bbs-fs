package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;

/**
 * Posted on the client once BBS has registered the renderers of its own forms.
 *
 * <p>A renderer is looked up by the form's exact class, then by its super classes — so a form
 * extending one of BBS's own inherits its renderer until it registers one of its own.</p>
 */
public class RegisterFormRenderersEvent
{
    public <T extends Form> void register(Class<T> clazz, FormUtilsClient.IFormRendererFactory<T> factory)
    {
        FormUtilsClient.register(clazz, factory);
    }
}
