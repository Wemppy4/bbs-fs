package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.resizers.layout.LabelRowResizer;
import mchorse.bbs_mod.utils.colors.Colors;

public class UI
{
    public static UIElement row(UIElement... elements)
    {
        return row(UIConstants.MARGIN, elements);
    }

    public static UIElement row(int margin, UIElement... elements)
    {
        return row(margin, 0, elements);
    }

    public static UIElement row(int margin, int padding, UIElement... elements)
    {
        return row(margin, padding, 0, elements);
    }

    public static UIElement row(int margin, int padding, int height, UIElement... elements)
    {
        UIElement element = new UIElement();

        element.row(margin).padding(padding).height(height);
        element.add(elements);

        return element;
    }

    public static UIElement column(UIElement... elements)
    {
        return column(UIConstants.MARGIN, elements);
    }

    public static UIElement column(int margin, UIElement... elements)
    {
        return column(margin, 0, elements);
    }

    public static UIElement column(int margin, int padding, UIElement... elements)
    {
        return column(margin, padding, 0, elements);
    }

    public static UIElement column(int margin, int padding, int height, UIElement... elements)
    {
        UIElement element = new UIElement();

        element.column(margin).vertical().stretch().padding(padding).height(height);
        element.add(elements);

        return element;
    }

    public static UILabel label(IKey label)
    {
        return label(label, Batcher2D.getDefaultTextRenderer().getHeight());
    }

    public static UILabel label(IKey label, int height)
    {
        return label(label, height, Colors.WHITE);
    }

    public static UILabel label(IKey label, int height, int color)
    {
        UILabel element = new UILabel(label, color);

        element.h(height);

        return element;
    }

    /**
     * A compact "label : control" row on a single line — the name fills the left,
     * the control keeps a fixed width and pins to the right, so control left edges
     * line up into one divider column across rows. In a panel too narrow for the
     * name to read beside the control, the name moves on its own line above and
     * the control takes the whole width (see {@link LabelRowResizer}).
     *
     * Use it for a single compact control (trackpad, dropdown, textbox);
     * multi-element groups (X/Y/Z, colors) stay stacked under a plain label.
     */
    public static UIElement labelRow(IKey label, UIElement element)
    {
        return labelRow(label, UIConstants.VALUE_WIDTH, element);
    }

    public static UIElement labelRow(IKey label, int controlWidth, UIElement element)
    {
        return labelRow(label(label, UIConstants.CONTROL_HEIGHT).labelAnchor(0, 0.5F), controlWidth, element, true);
    }

    /**
     * The same grid as {@link #labelRow(IKey, UIElement)}, but the left side is an
     * element rather than a name — for a row whose label slot does something itself
     * (a toggle that names itself, say). Its control still pins to the shared
     * divider column, so such a row lines up with the plain label rows around it
     * instead of spanning the full width on its own. It never stacks: such a slot
     * is small already, and on a line of its own it would no longer read as the
     * control's name.
     */
    public static UIElement labelRow(UIElement label, UIElement element)
    {
        return labelRow(label, UIConstants.VALUE_WIDTH, element);
    }

    public static UIElement labelRow(UIElement label, int controlWidth, UIElement element)
    {
        return labelRow(label, controlWidth, element, false);
    }

    private static UIElement labelRow(UIElement label, int controlWidth, UIElement element, boolean stack)
    {
        UIElement row = new UIElement();

        LabelRowResizer.apply(row, controlWidth, stack);
        row.add(label, element);

        return row;
    }

    public static UIScrollView scrollView(UIElement... elements)
    {
        return scrollView(UIConstants.MARGIN, elements);
    }

    public static UIScrollView scrollView(int margin, UIElement... elements)
    {
        return scrollView(margin, 0, elements);
    }

    public static UIScrollView scrollView(int margin, int padding, UIElement... elements)
    {
        return scrollView(margin, padding, 0, elements);
    }

    public static UIScrollView scrollView(int margin, int padding, int width, UIElement... elements)
    {
        UIScrollView scrollView = new UIScrollView();

        scrollView.column(margin).vertical().stretch().scroll().width(width).padding(padding);
        scrollView.add(elements);

        return scrollView;
    }

    /** The verbs of a list — add, duplicate, remove — as a row of compact icons over it (the replay list's idiom). */
    public static UIElement strip(UIIcon... icons)
    {
        return strip(UIConstants.CONTROL_HEIGHT, icons);
    }

    public static UIElement strip(int size, UIIcon... icons)
    {
        UIElement strip = new UIElement();

        strip.row(0).height(size);

        for (UIIcon icon : icons)
        {
            icon.wh(size, size);
            strip.add(icon);
        }

        return strip;
    }
}
