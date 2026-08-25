package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The path of the folder on show as clickable segments — {@code assets › models › steve} —
 * with a home icon for the root where the sources are listed. A click on the blank part of
 * the strip hands the path over to the browser's text field for typing.
 */
public class UIBreadcrumbs extends UIElement
{
    private static final int GAP = 6;
    private static final String SEPARATOR = "›";

    private record Crumb(String label, Link link)
    {}

    private final UITextureBrowser browser;
    private final List<Crumb> crumbs = new ArrayList<>();

    /* Laid out during render, so a click can find what's under it */
    private final List<Integer> starts = new ArrayList<>();
    private final List<Integer> ends = new ArrayList<>();
    private int hovered = -1;

    public UIBreadcrumbs(UITextureBrowser browser)
    {
        this.browser = browser;
    }

    public void setPath(Link path)
    {
        this.crumbs.clear();

        if (path == null || path.source.isEmpty())
        {
            return;
        }

        this.crumbs.add(new Crumb(path.source, new Link(path.source, "")));

        String walked = "";

        for (String segment : path.path.split("/"))
        {
            if (segment.isEmpty())
            {
                continue;
            }

            walked += segment + "/";
            this.crumbs.add(new Crumb(segment, new Link(path.source, walked)));
        }
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context) || context.mouseButton != 0)
        {
            return false;
        }

        if (this.hovered == -2)
        {
            this.browser.navigate(new Link("", ""));
        }
        else if (this.hovered >= 0)
        {
            this.browser.navigate(this.crumbs.get(this.hovered).link());
        }
        else
        {
            this.browser.editPath();
        }

        return true;
    }

    @Override
    public void render(UIContext context)
    {
        FontRenderer font = context.batcher.getFont();
        boolean inside = this.area.isInside(context);
        int x = this.area.x + 6;
        int textY = this.area.y + (this.area.h - font.getHeight()) / 2 + 1;
        int homeEnd = x + 16;

        this.starts.clear();
        this.ends.clear();
        this.hovered = -1;

        boolean homeHover = inside && context.mouseX < homeEnd + GAP / 2;

        if (homeHover)
        {
            this.hovered = -2;
        }

        context.batcher.icon(Icons.GLOBE, homeHover ? Colors.LIGHTEST_GRAY : Colors.WHITE, x, this.area.my() - 8);
        x = homeEnd + GAP;

        for (int i = 0; i < this.crumbs.size(); i++)
        {
            Crumb crumb = this.crumbs.get(i);
            boolean last = i == this.crumbs.size() - 1;

            context.batcher.text(SEPARATOR, x, textY, Colors.GRAY);
            x += font.getWidth(SEPARATOR) + GAP;

            int w = font.getWidth(crumb.label());
            boolean hover = inside && !homeHover && context.mouseX >= x - GAP / 2 && context.mouseX < x + w + GAP / 2;

            if (hover && !last)
            {
                this.hovered = i;
            }

            this.starts.add(x);
            this.ends.add(x + w);

            context.batcher.textShadow(crumb.label(), x, textY, last ? Colors.WHITE : (hover ? Colors.LIGHTEST_GRAY : Colors.LIGHTER_GRAY));
            x += w + GAP;
        }

        /* A read-only place (the mod's own textures) wears a lock at the end of the strip */
        if (TextureFiles.isReadOnly(this.browser.getPath()))
        {
            context.batcher.icon(Icons.LOCKED, Colors.LIGHTER_GRAY, this.area.ex() - 20, this.area.my() - 8);
        }

        if (this.hovered != -1)
        {
            context.requestCursor(GLFW.GLFW_HAND_CURSOR);
        }
        else if (inside)
        {
            context.requestCursor(GLFW.GLFW_IBEAM_CURSOR);
        }

        super.render(context);
    }
}
