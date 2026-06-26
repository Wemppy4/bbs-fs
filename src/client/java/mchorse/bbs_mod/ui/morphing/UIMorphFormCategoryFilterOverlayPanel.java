package mchorse.bbs_mod.ui.morphing;

import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;

import java.util.List;
import java.util.Set;

public class UIMorphFormCategoryFilterOverlayPanel extends UIOverlayPanel
{
    public UIMorphFormCategoryFilterOverlayPanel(Set<String> disabled, List<FormCategory> categories)
    {
        super(UIKeys.MORPHING_FILTER_CATEGORIES_TITLE);

        UIScrollView scrollView = UI.scrollView(4, 6);

        scrollView.full(this.content);
        this.content.add(scrollView);

        for (FormCategory category : categories)
        {
            String id = category.visible.getId();
            UIToggle toggle = new UIToggle(category::getProcessedTitle, (b) ->
            {
                if (disabled.contains(id))
                {
                    disabled.remove(id);
                }
                else
                {
                    disabled.add(id);
                }
            });

            toggle.h(UIConstants.CONTROL_HEIGHT);
            toggle.setValue(!disabled.contains(id));
            scrollView.add(toggle);
        }
    }
}
