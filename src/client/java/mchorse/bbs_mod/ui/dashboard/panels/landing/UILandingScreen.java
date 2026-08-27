package mchorse.bbs_mod.ui.dashboard.panels.landing;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueRecentData.Entry;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What an empty tab shows: a menu on the left — new, the list, the folder, the community links —
 * and on the right what was opened last, so the way back into yesterday's work is one click.
 *
 * <p>Nothing here changes files. Renaming, removing, folders, duplicates all live in the data
 * manager the list entry leads to; this screen only opens things.</p>
 */
public class UILandingScreen extends UIElement
{
    private static final int CARD_W = 440;
    private static final int CARD_H = 360;

    /** The banner is the top half of the card, exactly. */
    private static final int BANNER_H = CARD_H / 2;
    private static final int BANNER_MARGIN = 12;
    private static final int PADDING = 10;
    private static final int HEADER_H = 16;
    private static final int HEADER_MARGIN = 6;
    private static final int MENU_W = 150;
    private static final int GUTTER = 20;
    private static final int GROUP_GAP = 10;

    private static final int RECENT_X = PADDING + MENU_W + GUTTER;
    private static final int CONTENT_Y = BANNER_H + BANNER_MARGIN;
    private static final int LIST_Y = CONTENT_Y + HEADER_H + HEADER_MARGIN;

    /** Section titles sit back a little; the entries under them are what the eye is for. */
    private static final int DIMMED = Colors.setA(Colors.WHITE, 0.7F);
    private static final int MUTED = Colors.setA(Colors.WHITE, 0.5F);

    private static final Link BANNER = Link.assets("textures/banners/bg.png");

    /* Where the community lives; the same in every language, so not in the language files */
    private static final String DISCORD_LINK = "https://discord.gg/66mVb7Ezjj";
    private static final String TUTORIALS_LINK = "https://www.youtube.com/watch?v=yY5uE3PVd5Y&list=PLM5Z4FJ0AVdw";
    private static final String WIKI_LINK = "https://github.com/Wemppy4/bbs-fs/wiki";

    private final ILandingHost host;
    private final LandingBackdrop backdrop = new LandingBackdrop();
    private final UIElement card;
    private final UIElement banner;
    private final UIElement menu;
    private final UILandingRow folder;
    private final UIRecentDataList recent;

    /** Ids the repository reported last; null until it answered, when nothing is filtered out. */
    private Set<String> known;

    public UILandingScreen(ILandingHost host)
    {
        this.host = host;

        /* Centered, with the backdrop showing all around it */
        this.card = new UIElement();
        this.card.relative(this).xy(0.5F, 0.5F).wh(CARD_W, CARD_H).anchor(0.5F);

        this.banner = new UIElement();
        this.banner.relative(this.card).xy(0, 0).w(1F).h(BANNER_H);
        this.banner.add(new UIRenderable((context) -> this.renderBanner(context, this.banner.area)));

        UILabel title = UI.label(host.getTitle()).color(DIMMED);
        title.labelAnchor(0, 0.5F);
        title.relative(this.card).xy(PADDING, CONTENT_Y).w(MENU_W).h(HEADER_H);

        UILabel recentTitle = UI.label(UIKeys.PANELS_LANDING_RECENT).color(DIMMED);
        recentTitle.labelAnchor(0, 0.5F);
        recentTitle.relative(this.card).xy(RECENT_X, CONTENT_Y).w(CARD_W - RECENT_X - PADDING).h(HEADER_H);

        /* The version sits in the corner of the banner, the way a splash screen wears it */
        UILabel version = UI.label(IKey.constant(getVersion())).color(DIMMED);
        version.labelAnchor(1F, 0.5F);
        version.relative(this.banner).x(1F, -HEADER_MARGIN).y(1F, -HEADER_MARGIN).w(MENU_W).h(HEADER_H).anchor(1F, 1F);

        /* The menu: what leads into the editor first, what leads out of it after a gap */
        IKey createLabel = host.getCreateLabel();
        UILandingRow list = new UILandingRow(Icons.MORE, host.getListLabel(), (b) -> host.openDataManager());
        UIElement gap = new UIElement();
        UILandingRow discord = new UILandingRow(Icons.DISCORD, IKey.constant("Discord"), (b) -> UIUtils.openWebLink(DISCORD_LINK));
        UILandingRow tutorials = new UILandingRow(Icons.PLAY, UIKeys.SUPPORTERS_TUTORIALS, (b) -> UIUtils.openWebLink(TUTORIALS_LINK));
        UILandingRow wiki = new UILandingRow(Icons.HELP, UIKeys.SUPPORTERS_WIKI, (b) -> UIUtils.openWebLink(WIKI_LINK));

        this.folder = new UILandingRow(Icons.FOLDER, UIKeys.PANELS_CONTEXT_OPEN, (b) -> this.openFolder());

        gap.h(GROUP_GAP);

        List<UIElement> rows = new ArrayList<>();

        /* Panels backed by assets (the model editor) and by files (the audio editor) have nothing
         * to create; there the list is the way in, and it wears the accent instead */
        if (createLabel == null)
        {
            list.accent();
        }
        else
        {
            UILandingRow create = new UILandingRow(Icons.ADD, createLabel, (b) -> host.addNewData(this.getContext()));

            create.accent();
            rows.add(create);
        }

        rows.add(list);
        rows.add(this.folder);
        rows.add(gap);
        rows.add(discord);
        rows.add(tutorials);
        rows.add(wiki);

        this.menu = UI.column(0, rows.toArray(new UIElement[0]));
        this.menu.relative(this.card).xy(PADDING, LIST_Y).w(MENU_W).h(1F, -(LIST_Y + PADDING));

        this.recent = new UIRecentDataList((entries) -> this.open(entries.get(0)), host::getTabIcon);
        this.recent.relative(this.card).xy(RECENT_X, LIST_Y).w(CARD_W - RECENT_X - PADDING).h(1F, -(LIST_Y + PADDING));
        this.recent.context(this::fillRecentMenu);

        this.card.add(new UIRenderable((context) -> this.renderCard(context, this.card.area)));
        this.card.add(this.banner, title, recentTitle, version, this.menu);
        this.card.add(new UIRenderable(this::renderEmptyHint), this.recent);
        this.add(new UIRenderable(this::renderBackdrop), this.card);

        this.refresh();
    }

    /** "BBS FS 2.6.0" — the mod's own version, without the Minecraft version the build appends. */
    private static String getVersion()
    {
        return FabricLoader.getInstance().getModContainer(BBSMod.MOD_ID)
            .map((mod) ->
            {
                String version = mod.getMetadata().getVersion().getFriendlyString();
                int dash = version.lastIndexOf('-');

                return "BBS FS " + (dash > 0 ? version.substring(0, dash) : version);
            })
            .orElse("BBS FS");
    }

    @Override
    public void setVisible(boolean visible)
    {
        boolean wasVisible = this.isVisible();

        super.setVisible(visible);

        if (visible && !wasVisible)
        {
            this.refresh();
            this.host.requestNames();
        }
    }

    /** The repository answered: whatever it no longer has drops out of the list. */
    public void fillNames(Collection<String> names)
    {
        this.known = new HashSet<>(names);

        this.refresh();
    }

    /**
     * Rebuild from the registry. The list is drawn from the settings right away, without waiting
     * for the repository — over the network that answer takes a moment, and the screen must not
     * flash empty every time a tab is emptied.
     */
    private void refresh()
    {
        boolean hasFolder = this.host.getDataFolder() != null;

        if (this.folder.isVisible() != hasFolder)
        {
            this.folder.setVisible(hasFolder);
            this.menu.resize();
        }

        List<Entry> entries = new ArrayList<>();

        for (Entry entry : BBSSettings.recentData.get(this.host.getRecentType()))
        {
            if (this.known == null || this.known.contains(entry.id))
            {
                entries.add(entry);
            }
        }

        this.recent.setList(entries);
        this.recent.deselect();
    }

    private void open(Entry entry)
    {
        this.host.pickData(entry.id);
    }

    private void openFolder()
    {
        File folder = this.host.getDataFolder();

        if (folder != null)
        {
            UIUtils.openFolder(folder);
        }
    }

    private void fillRecentMenu(ContextMenuManager menu)
    {
        Entry entry = this.recent.getEntryAtCursor(this.getContext());

        if (entry == null)
        {
            return;
        }

        menu.action(this.host.getTabIcon(entry.id), UIKeys.PANELS_LANDING_OPEN, () -> this.open(entry));
        menu.action(Icons.MORE, UIKeys.PANELS_LANDING_SHOW_IN_MANAGER, () -> this.host.showInList(entry.id));
        menu.action(Icons.REMOVE, UIKeys.PANELS_LANDING_FORGET, () -> this.forget(entry));
    }

    private void forget(Entry entry)
    {
        BBSSettings.recentData.forget(this.host.getRecentType(), entry.id);
        this.refresh();
    }

    /* Rendering */

    private void renderBackdrop(UIContext context)
    {
        this.backdrop.render(context, this.area);
    }

    private void renderCard(UIContext context, Area area)
    {
        int bg = BBSSettings.raisedSurface();
        int border = BBSSettings.color(BBSSettings.dividerColor(), Colors.A12);
        int accent = BBSSettings.primaryColor.get();

        context.batcher.dropShadow(area.x, area.y, area.ex(), area.ey(), 14, Colors.A50, 0);
        context.batcher.box(area.x, area.y, area.ex(), area.ey(), bg);
        context.batcher.outline(area.x, area.y, area.ex(), area.ey(), border);

        /* Accent seam under the banner — brightest at the center, fading toward the card edges */
        int sepY = area.y + BANNER_H;
        int mid = area.mx();

        context.batcher.gradientHBox(area.x, sepY, mid, sepY + 2, Colors.setA(accent, 0F), Colors.A100 | accent);
        context.batcher.gradientHBox(mid, sepY, area.ex(), sepY + 2, Colors.A100 | accent, Colors.setA(accent, 0F));
    }

    private void renderEmptyHint(UIContext context)
    {
        if (!this.recent.getList().isEmpty())
        {
            return;
        }

        FontRenderer font = context.batcher.getFont();
        Area area = this.recent.area;
        List<String> lines = font.wrap(UIKeys.PANELS_LANDING_RECENT_EMPTY.get(), area.w - PADDING * 2);
        int lineH = font.getHeight() + 2;
        int y = area.my() - lines.size() * lineH / 2;

        for (String line : lines)
        {
            context.batcher.text(line, area.mx() - font.getWidth(line) / 2, y, MUTED, false);

            y += lineH;
        }
    }

    private void renderBanner(UIContext context, Area area)
    {
        Link bannerLink = BANNER;
        Texture texture = BBSModClient.getTextures().getTexture(bannerLink);

        if (texture == null)
        {
            return;
        }

        float texW = texture.width;
        float texH = texture.height;
        float areaW = area.w;
        float areaH = area.h;

        float texAspect = texW / texH;
        float areaAspect = areaW / areaH;

        float u1;
        float u2;
        float v1;
        float v2;

        if (areaAspect > texAspect)
        {
            float cropH = texW / areaAspect;

            u1 = 0;
            u2 = texW;
            v1 = (texH - cropH) * 0.5F;
            v2 = v1 + cropH;
        }
        else
        {
            float cropW = texH * areaAspect;

            u1 = (texW - cropW) * 0.5F;
            u2 = u1 + cropW;
            v1 = 0;
            v2 = texH;
        }

        context.batcher.texturedBox(texture, Colors.WHITE, area.x, area.y, area.w, area.h, u1, v1, u2, v2, texture.width, texture.height);
    }
}
