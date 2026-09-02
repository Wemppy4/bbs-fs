package mchorse.bbs_mod.ui.film.utils.undo;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.tooltips.TooltipPlacement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.undo.CompoundUndo;
import mchorse.bbs_mod.utils.undo.IUndo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class UIUndoList <T> extends UIList<IUndo<T>>
{
    public UIUndoList(Consumer<List<IUndo<T>>> callback)
    {
        super(callback);

        this.background();
        this.tooltip(IKey.EMPTY);
    }

    @Override
    protected String elementToString(UIContext context, int i, IUndo<T> element)
    {
        if (element instanceof ValueChangeUndo undo)
        {
            return undo.name.toString();
        }
        else if (element instanceof CompoundUndo<T> compoundUndo)
        {
            List<String> keys = new ArrayList<>();

            for (IUndo<T> undo : compoundUndo.getUndos())
            {
                if (undo instanceof ValueChangeUndo valueUndo)
                {
                    keys.add(valueUndo.name.toString());
                }
            }

            String prefix = StringUtils.findCommonPrefix(keys);

            if (prefix.endsWith("."))
            {
                prefix = prefix.substring(0, prefix.length() - 1);
            }

            return prefix + " (" + compoundUndo.getUndos().size() + ")";
        }

        return super.elementToString(context, i, element);
    }

    @Override
    public void renderTooltip(UIContext context, Area area)
    {
        int index = this.getHoveredIndex(context);
        IUndo<T> safe = CollectionUtils.getSafe(this.getList(), index);

        if (safe != null)
        {
            String label = this.elementToString(context, index, safe);
            FontRenderer font = context.batcher.getFont();

            /* The card is the text plus its 3px padding on each side */
            Area card = TooltipPlacement.nearMouse(context, font.getWidth(label) + 6, font.getHeight() + 6, 2, true, 0, Area.SHARED);

            context.batcher.textCard(label, card.x + 3, card.y + 3);
        }
    }
}