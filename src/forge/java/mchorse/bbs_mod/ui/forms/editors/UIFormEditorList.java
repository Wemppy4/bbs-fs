package mchorse.bbs_mod.ui.forms.editors;

import mchorse.bbs_mod.ui.forms.IUIFormList;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.InputCodes;

public class UIFormEditorList extends UIFormList
{
    public UIFormEditorList(IUIFormList palette)
    {
        super(palette);

        this.edit.removeFromParent();
        this.eventPropagataion(EventPropagation.BLOCK_INSIDE).markContainer();
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(InputCodes.KEY_ESCAPE))
        {
            this.palette.exit();
        }

        return true;
    }

    @Override
    public void render(UIContext context)
    {
        this.area.render(context.batcher, BBSSettings.deepSurface());

        super.render(context);
    }
}
