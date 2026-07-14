package mchorse.bbs_mod.ui.selectors;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.selectors.EntitySelector;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.utils.UIDataUtils;

import java.util.List;
import java.util.function.Consumer;

public class UISelectorList extends UIList<EntitySelector>
{
    public UISelectorList(Consumer<List<EntitySelector>> callback)
    {
        super(callback);
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        if (this.list.isEmpty())
        {
            UIDataUtils.renderRightClickHere(context, this.area);
        }
    }

    @Override
    protected String elementToString(UIContext context, int i, EntitySelector element)
    {
        String id = "-";

        if (element.entity != null)
        {
            id = element.name.isEmpty() ? element.entity.toString() : element.entity.toString() + " - " + element.name;
        }

        return id;
    }

    @Override
    protected void renderElementPart(UIContext context, EntitySelector element, int i, int x, int y, boolean hover, boolean selected)
    {
        super.renderElementPart(context, element, i, x, y, hover, selected);

        Form form = element.form;

        if (form != null && BBSSettings.listModelPreview.get())
        {
            x += this.area.w - 30;

            context.batcher.clip(x, y, 40, 20, context);

            y -= 10;

            FormUtilsClient.renderUI(form, context, x, y, x + 40, y + 40);

            context.batcher.unclip(context);
        }
    }
}