package mchorse.bbs_mod.ui.forms.editors.utils;

import mchorse.bbs_mod.api.client.events.FormPreviewEvents;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;

public class UIFormRenderer extends UIModelRenderer
{
    public Form form;

    @Override
    protected void renderUserModelOverlay(UIContext context)
    {
        FormPreviewEvents.OVERLAY.invoker().render(this, context);
    }

    @Override
    protected void renderUserModel(UIContext context)
    {
        if (this.form == null)
        {
            return;
        }

        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.PREVIEW, this.entity, context.batcher.getContext().getMatrices(), 0x00f000f0, 10 << 16, context.getTransition())
            .camera(this.camera)
            .modelRenderer(context.getTick());

        FormUtilsClient.render(this.form, formContext);
    }
}
