package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.model.config.WeldValue;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;
import java.util.function.Consumer;

/**
 * The welds' list: a row reads {@code bone -> bone} with each end's welded side drawn as the very icon
 * the side picker names it by, rather than spelled out as a "/top" suffix. The icons are why the rows
 * are taller than a plain list's.
 */
public class UIWeldList extends UIEntryList<WeldValue>
{
    /** Tall enough for a 16px side icon with a little air around it. */
    public static final int ROW_HEIGHT = 22;

    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 2;
    private static final String ARROW = " → ";

    public UIWeldList(Consumer<List<WeldValue>> callback)
    {
        super(callback, UIWeldList::weldName);

        this.scroll.scrollItemSize = ROW_HEIGHT;
    }

    /** What a weld joins, as text: {@code bone/face -> bone/face}, a "?" for a bone not picked yet. */
    private static String weldName(WeldValue weld)
    {
        return weldEnd(weld.sourceBone.get(), weld.sourceFace.get()) + ARROW + weldEnd(weld.targetBone.get(), weld.targetFace.get());
    }

    private static String weldEnd(String bone, String face)
    {
        String name = bone.isEmpty() ? "?" : bone;

        return face.isEmpty() ? name : name + "/" + face;
    }

    @Override
    protected void renderElementPart(UIContext context, WeldValue element, int i, int x, int y, boolean hover, boolean selected)
    {
        FontRenderer font = context.batcher.getFont();
        int color = this.rowColor(element, hover);
        int textY = y + (this.scroll.scrollItemSize - font.getHeight()) / 2;
        int iconY = y + this.scroll.scrollItemSize / 2;
        int arrow = font.getWidth(ARROW);
        int x0 = x + this.rowContentX(element);

        /* Both ends get the same slice of what's left once the arrow and the two side icons are paid for. */
        int name = Math.max(0, this.rowContentEnd(x) - x0 - arrow - 2 * (ICON_GAP + ICON_SIZE)) / 2;
        int cursor = this.renderEnd(context, element.sourceBone.get(), element.sourceFace.get(), x0, textY, iconY, name, color);

        /* The arrow only separates the two ends, so it stays quiet whatever the row reads in. */
        context.batcher.textShadow(ARROW, cursor, textY, Colors.GRAY);
        this.renderEnd(context, element.targetBone.get(), element.targetFace.get(), cursor + arrow, textY, iconY, name, color);
    }

    /** One end of the weld: the bone, then the side it's welded by. Returns where it stopped drawing. */
    private int renderEnd(UIContext context, String bone, String face, int x, int textY, int iconY, int width, int color)
    {
        FontRenderer font = context.batcher.getFont();
        String label = font.limitToWidth(bone.isEmpty() ? "?" : bone, width);
        Icon icon = UIModelConfigEditor.faceIcon(face);

        context.batcher.textShadow(label, x, textY, color);

        x += font.getWidth(label);

        if (icon != null)
        {
            context.batcher.icon(icon, Colors.A100 | color, x + ICON_GAP, iconY, 0F, 0.5F);

            x += ICON_GAP + ICON_SIZE;
        }

        return x;
    }
}
