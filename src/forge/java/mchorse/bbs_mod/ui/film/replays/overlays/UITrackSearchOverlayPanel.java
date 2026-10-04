package mchorse.bbs_mod.ui.film.replays.overlays;

import mchorse.bbs_mod.film.replays.tracks.TrackSearchEntry;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.graphics.window.InputCodes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/** Keyboard-first navigator for the current replay. */
public class UITrackSearchOverlayPanel extends UIOverlayPanel
{
    public static void open(UIContext context, List<TrackSearchEntry> entries, String currentPath,
        List<String> recent, BiPredicate<TrackSearchEntry, Boolean> navigate)
    {
        UITrackSearchOverlayPanel panel = new UITrackSearchOverlayPanel(entries, currentPath, recent, (entry, create) ->
        {
            if (navigate.test(entry, create))
            {
                recent.remove(entry.identity());
                recent.add(0, entry.identity());
                if (recent.size() > 20) recent.remove(recent.size() - 1);
            }
        });
        var overlay = mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay.addOverlay(context, panel, 420, 338);
        panel.relative(overlay).xy(0.5F, 0.5F).anchor(0.5F);
        panel.getFlex().w.max = Math.max(1, overlay.area.w - 12);
        panel.getFlex().h.max = Math.max(1, overlay.area.h - 12);
        overlay.resize();
    }

    private final UITextbox search;
    private final UIList<TrackSearchEntry> results;
    private final List<TrackSearchEntry> entries;
    private final String currentPath;
    private final List<String> recent;
    private final BiConsumer<TrackSearchEntry, Boolean> navigate;

    public UITrackSearchOverlayPanel(List<TrackSearchEntry> entries, String currentPath,
        List<String> recent, BiConsumer<TrackSearchEntry, Boolean> navigate)
    {
        super(L10n.lang("bbs.ui.film.track_search.title"));
        this.entries = entries;
        this.currentPath = currentPath;
        this.recent = recent;
        this.navigate = navigate;

        this.results = new UIList<TrackSearchEntry>(selected ->
        {
            if (!selected.isEmpty()) this.choose(selected.get(0), Window.isCtrlPressed());
        })
        {
            @Override
            protected boolean canScaleRows()
            {
                return false;
            }

            @Override
            protected int rowColor(TrackSearchEntry entry)
            {
                return entry.color();
            }

            @Override
            public void render(UIContext context)
            {
                super.render(context);
                if (this.getList().isEmpty())
                {
                    String label = context.batcher.getFont().limitToWidth(L10n.lang("bbs.ui.film.track_search.empty").get(), Math.max(0, this.area.w - 16));
                    context.batcher.textShadow(label, this.area.x + 8, this.area.y + 10, Colors.setA(Colors.WHITE, 0.6F));
                }
            }

            @Override
            protected void renderElementPart(UIContext context, TrackSearchEntry entry, int i, int x, int y, boolean hover, boolean selected)
            {
                var font = context.batcher.getFont();
                int textX = x + ROW_PADDING + ICON_SLOT + ICON_GAP;
                int right = x + this.area.w - 8;
                int width = Math.max(0, right - textX);
                int textY = y + (this.rowHeight() - font.getHeight()) / 2;
                boolean lit = hover || selected;
                RowStyle.swatch(context.batcher, x, y, this.rowHeight(), entry.color());
                if (entry.icon() != null)
                {
                    context.batcher.icon(entry.icon(), RowStyle.iconColor(lit), x + ROW_PADDING + ICON_SLOT / 2F,
                        y + this.rowHeight() / 2F, 0.5F, 0.5F);
                }
                String label = entry.title();
                int pathBudget = Math.max(0, width - Math.min(font.getWidth(label), width / 2) - 12);
                String path = font.limitToWidth(entry.location(), pathBudget);
                int pathWidth = font.getWidth(path);
                label = font.limitToWidth(label, Math.max(0, width - pathWidth - (pathWidth > 0 ? 12 : 0)));
                context.batcher.textShadow(label, textX, textY, RowStyle.textColor(lit));
                context.batcher.textShadow(path, right - pathWidth, textY, Colors.setA(Colors.WHITE, lit ? 0.7F : 0.5F));
            }
        };
        this.results.scroll.scrollItemSize = 20;
        this.results.relative(this.content).xy(6, 32).w(1F, -12).h(1F, -38);

        this.search = new UITextbox(256, this::refresh);
        this.search.placeholder(L10n.lang("bbs.ui.film.track_search.placeholder"));
        this.search.relative(this.content).xy(6, 6).w(1F, -12).h(20);
        this.content.add(this.results, this.search);
        this.refresh("");
    }

    private void refresh(String text)
    {
        this.results.setList(new ArrayList<>(TrackSearchEntry.find(this.entries, text, this.currentPath, this.recent)));
        this.results.setIndex(0);
        this.results.scroll.setScroll(0);
    }

    private void choose(TrackSearchEntry entry, boolean createKeyframe)
    {
        this.close();
        this.navigate.accept(entry, createKeyframe);
    }

    @Override
    protected void onAdd(UIElement parent)
    {
        super.onAdd(parent);
        UIContext context = this.getContext();
        this.onClose(event -> context.unfocus());
        context.focus(this.search);
    }

    @Override
    protected IUIElement childrenKeyPressed(UIContext context)
    {
        /* Handle navigation before the focused textbox can consume Escape or arrows. */
        if (context.isPressed(InputCodes.KEY_ESCAPE)
            || (context.isPressed(Keys.FILM_TRACK_SEARCH.getMainKey()) && Keys.FILM_TRACK_SEARCH.isHeld()))
        {
            this.close();
            return this;
        }
        if (context.isPressed(InputCodes.KEY_ENTER) || context.isPressed(InputCodes.KEY_KP_ENTER))
        {
            int index = this.results.getIndex();
            if (index >= 0 && index < this.results.getList().size()) this.choose(this.results.getList().get(index), Window.isCtrlPressed());
            return this;
        }
        if (context.isHeld(InputCodes.KEY_DOWN) || context.isHeld(InputCodes.KEY_UP))
        {
            int direction = context.isHeld(InputCodes.KEY_DOWN) ? 1 : -1;
            int index = Math.max(0, Math.min(this.results.getList().size() - 1, this.results.getIndex() + direction));
            this.results.setIndex(index);
            this.results.scroll.scrollIntoView(index * this.results.rowHeight());
            return this;
        }
        return super.childrenKeyPressed(context);
    }

    @Override
    public void close()
    {
        this.getContext().unfocus();
        super.close();
    }
}
