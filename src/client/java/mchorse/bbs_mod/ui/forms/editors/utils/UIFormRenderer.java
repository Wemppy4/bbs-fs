package mchorse.bbs_mod.ui.forms.editors.utils;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;

public class UIFormRenderer extends UIModelRenderer
{
    public Form form;

    @Override
    protected void renderUserModel(UIContext context)
    {
        if (this.form == null)
        {
            return;
        }

        /* 1.21.11 render: seed the per-vertex MatrixStack with the camera model-view (createCameraStack)
         * so the cubic geometry lands in view space; the global model-view stays identity + perspective
         * projection is set in ModelPreviewRenderer.begin. */
        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.PREVIEW, this.entity, this.createCameraStack(), LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, context.getTransition())
            .camera(this.camera)
            .modelRenderer();

        FormUtilsClient.render(this.form, formContext);
    }
}