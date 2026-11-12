package mchorse.bbs_mod.ui.addons;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.addons.Addon;
import mchorse.bbs_mod.addons.Addons;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The installed BBS addons, one compact card each, with a search on top. Crashed addons go
 * first, so they are seen without scrolling.
 */
public class UIAddonsOverlayPanel extends UIOverlayPanel
{
    private static final int PADDING = 6;

    public UITextbox search;
    public UIScrollView cards;

    public UIAddonsOverlayPanel()
    {
        super(UIKeys.ADDONS_TITLE);
        this.wh(300, 290);

        this.search = new UITextbox(100, (text) -> this.fill(text));
        this.search.placeholder(UIKeys.GENERAL_SEARCH);
        this.search.relative(this.content).x(PADDING).y(PADDING).w(1F, -PADDING * 2).h(20);

        this.cards = UI.scrollView(UIConstants.MARGIN, PADDING);
        this.cards.relative(this.content).y(PADDING + 20).w(1F).h(1F, -PADDING - 20);

        this.content.add(this.search, this.cards);
        this.fill("");
    }

    private void fill(String filter)
    {
        String query = filter.toLowerCase(Locale.ROOT).trim();
        List<Addon> addons = Addons.getAll().stream()
            .filter((addon) -> query.isEmpty() || addon.getName().toLowerCase(Locale.ROOT).contains(query))
            .sorted(Comparator.comparing((Addon addon) -> !addon.hasError()).thenComparing((addon) -> addon.getName().toLowerCase(Locale.ROOT)))
            .collect(Collectors.toList());

        this.cards.removeAll();

        for (Addon addon : addons)
        {
            this.cards.add(new UIAddonCard(addon));
        }

        this.cards.resize();
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);

        this.content.area.render(context.batcher, BBSSettings.baseSurface());
    }
}
