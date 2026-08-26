package mchorse.bbs_mod.ui.forms.categories;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.CubicLoader;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageFolderOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.File;
import java.io.IOException;

public class UIModelFormCategory extends UIFormCategory
{
    public UIModelFormCategory(FormCategory category, UIFormList list)
    {
        super(category, list);

        this.context((menu) ->
        {
            if (!(this.getContextForm() instanceof ModelForm modelForm) || this.isGroupContext())
            {
                return;
            }

            menu.action(Icons.UPLOAD, UIKeys.FORMS_CATEGORIES_CONTEXT_EXPORT_MODEL, () ->
            {
                ModelInstance model = ModelFormRenderer.getModel(modelForm);

                if (model != null)
                {
                    MapType map = CubicLoader.toData(model);

                    try
                    {
                        File path = BBSMod.getAssetsPath(ModelManager.MODELS_PREFIX + modelForm.model.get() + "/exported._bbs.json");

                        IOUtils.writeText(path, DataToString.toString(map, true));

                        UIMessageFolderOverlayPanel overlayPanel = new UIMessageFolderOverlayPanel(
                            UIKeys.FORMS_CATEGORIES_CONTEXT_EXPORT_MODEL_TITLE,
                            UIKeys.FORMS_CATEGORIES_CONTEXT_EXPORT_MODEL_DESCRIPTION,
                            path.getParentFile()
                        );

                        UIOverlay.addOverlay(this.getContext(), overlayPanel);
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            });
        });
    }
}