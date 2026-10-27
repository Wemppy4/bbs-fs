package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * The title line over a shader pack's menu. With several crumbs it's the path down the pack's
 * menu, and every crumb but the last takes back up to its screen; a muted one is a plain grey note.
 */
public class UIShaderMenuCrumbs extends UIElement
{
    public static final int HEIGHT = 16;

    private static final int PADDING = 6;
    private static final int GAP = 6;
    private static final String SEPARATOR = "›";

    public IntConsumer callback;

    private final List<String> crumbs = new ArrayList<>();
    private boolean muted;

    /* Found during render, so a click knows what's under it */
    private int hovered = -1;

    public void crumbs(List<String> crumbs, boolean muted)
    {
        this.crumbs.clear();
        this.crumbs.addAll(crumbs);
        this.muted = muted;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 0 && this.hovered >= 0 && this.callback != null && this.area.isInside(context))
        {
            UIUtils.playClick();
            this.callback.accept(this.hovered);

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        FontRenderer font = context.batcher.getFont();
        boolean inside = this.area.isInside(context);
        int x = this.area.x + PADDING;
        int textY = this.area.my(font.getHeight());
        int last = this.crumbs.size() - 1;

        this.hovered = -1;

        for (int i = 0; i <= last; i++)
        {
            if (i > 0)
            {
                context.batcher.text(SEPARATOR, x, textY, Colors.GRAY);
                x += font.getWidth(SEPARATOR) + GAP;
            }

            String label = this.crumbs.get(i);

            if (i == last)
            {
                label = font.limitToWidth(label, this.area.ex() - PADDING - x);
            }

            int w = font.getWidth(label);
            boolean hover = inside && !this.muted && i < last && context.mouseX >= x - GAP / 2 && context.mouseX < x + w + GAP / 2;
            int color = this.muted ? Colors.GRAY : (i == last ? Colors.WHITE : (hover ? Colors.LIGHTEST_GRAY : Colors.LIGHTER_GRAY));

            if (hover)
            {
                this.hovered = i;
            }

            context.batcher.textShadow(label, x, textY, color);
            x += w + GAP;
        }

        if (this.hovered >= 0)
        {
            context.requestCursor(GLFW.GLFW_HAND_CURSOR);
        }

        super.render(context);
    }
}
