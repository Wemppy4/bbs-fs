package mchorse.bbs_mod.ui.dashboard.panels.landing;

import mchorse.bbs_mod.settings.values.core.ValueRecentData.Entry;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The landing screen's list of what was opened last. A click opens the row under the cursor;
 * the arrow keys walk the list and Enter opens the chosen one, so the way back into yesterday's
 * work needs no mouse.
 *
 * <p>A row is the document's icon, its name, the folder it sits in and how long ago it was
 * opened — the name in white, the rest dimmed. Hover and selection look the way they do in
 * every other list of the mod.</p>
 */
public class UIRecentDataList extends UIList<Entry>
{
    public static final int ROW = 20;

    private static final int ICON_X = 2;
    private static final int TEXT_X = 22;
    private static final int GAP = 6;
    private static final int RIGHT_PADDING = 6;

    private final Function<String, Icon> icons;

    public UIRecentDataList(Consumer<List<Entry>> callback, Function<String, Icon> icons)
    {
        super(callback);

        this.icons = icons;
        this.scroll.scrollItemSize = ROW;
    }

    public Entry getEntryAtCursor(UIContext context)
    {
        int index = this.getIndexAtCursor(context);

        return this.exists(index) ? this.list.get(index) : null;
    }

    /** How long ago a moment was, in the shortest words that still read: "5 min", "yesterday", "3 wk". */
    public static String ago(long time)
    {
        long minutes = Math.max(0L, System.currentTimeMillis() - time) / 60_000L;
        long hours = minutes / 60L;
        long days = hours / 24L;

        if (minutes < 1L)
        {
            return UIKeys.PANELS_LANDING_TIME_NOW.get();
        }

        if (hours < 1L)
        {
            return UIKeys.PANELS_LANDING_TIME_MINUTES.format(minutes).get();
        }

        if (days < 1L)
        {
            return UIKeys.PANELS_LANDING_TIME_HOURS.format(hours).get();
        }

        if (days < 2L)
        {
            return UIKeys.PANELS_LANDING_TIME_YESTERDAY.get();
        }

        if (days < 7L)
        {
            return UIKeys.PANELS_LANDING_TIME_DAYS.format(days).get();
        }

        if (days < 30L)
        {
            return UIKeys.PANELS_LANDING_TIME_WEEKS.format(days / 7L).get();
        }

        return UIKeys.PANELS_LANDING_TIME_MONTHS.format(days / 30L).get();
    }

    @Override
    protected String elementToString(UIContext context, int i, Entry element)
    {
        return element.id;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        /* Someone is typing somewhere; the arrows are theirs. */
        if (context.isFocused() || this.list.isEmpty() || context.getKeyAction() == KeyAction.RELEASED)
        {
            return false;
        }

        int key = context.getKeyCode();

        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP)
        {
            int index = this.getIndex();
            int last = this.list.size() - 1;
            int next;

            if (index < 0)
            {
                next = key == GLFW.GLFW_KEY_DOWN ? 0 : last;
            }
            else
            {
                next = key == GLFW.GLFW_KEY_DOWN ? Math.min(index + 1, last) : Math.max(index - 1, 0);
            }

            this.setIndex(next);
            this.scroll.scrollIntoView(next * ROW);

            return true;
        }

        if (key == GLFW.GLFW_KEY_ENTER && context.getKeyAction() == KeyAction.PRESSED)
        {
            return this.pick(this.getIndex());
        }

        return false;
    }

    @Override
    protected void renderElementPart(UIContext context, Entry element, int i, int x, int y, boolean hover, boolean selected)
    {
        FontRenderer font = context.batcher.getFont();
        DataPath path = new DataPath(element.id);
        DataPath parent = path.getParent();
        String folder = parent.strings.isEmpty() ? "" : parent.toString() + "/";
        String ago = ago(element.time);

        int muted = Colors.setA(Colors.WHITE, 0.5F);
        int textY = y + (ROW - font.getHeight()) / 2 + 1;
        int right = x + this.area.w - RIGHT_PADDING - (this.scroll.hasScrollbar() ? this.scroll.getScrollbarWidth() : 0);
        int agoW = font.getWidth(ago);
        int textX = x + TEXT_X;

        context.batcher.icon(this.icons.apply(element.id), x + ICON_X, y + ROW / 2, 0F, 0.5F);
        context.batcher.text(ago, right - agoW, textY, muted, false);

        String name = font.limitToWidth(path.getLast(), right - agoW - GAP - textX);

        context.batcher.text(name, textX, textY, hover ? Colors.HIGHLIGHT : Colors.WHITE, false);

        if (!folder.isEmpty())
        {
            int folderX = textX + font.getWidth(name) + GAP;
            int folderW = right - agoW - GAP - folderX;

            if (folderW > font.getWidth("..."))
            {
                context.batcher.text(font.limitToWidth(folder, folderW), folderX, textY, muted, false);
            }
        }
    }
}
