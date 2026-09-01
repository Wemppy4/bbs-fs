package mchorse.bbs_mod.ui.onboarding.welcome;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode.PanelNode;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode.SplitterNode;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode.StackNode;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.onboarding.welcome.LayoutPresets.Preset;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * "Where the editor's panels go": the shipped layouts as cards, each a schematic drawn from
 * the layout tree itself — rectangles, not screenshots, so a redesign of the real panels
 * cannot date them. A click applies the layout to the film editor right away; the line
 * under the cards says what the one under the cursor is for.
 */
public class UILayoutPage extends UIWelcomePage
{
    private static final int CARD_H = 64;
    private static final int CARD_GAP = 6;
    private static final int DESCRIPTION_H = 12;

    /** The card's schematic is this much shorter than the card; the name takes the rest. */
    private static final int NAME_H = 14;

    /** Between neighbouring rectangles of a schematic. */
    private static final int SEAM = 1;

    /** A rectangle narrower or shorter than this gets no icon: it would not fit. */
    private static final int ICON_ROOM = 18;

    private final List<UILayoutCard> cards = new ArrayList<>();
    private String selected;

    public UILayoutPage()
    {
        super(UIKeys.ONBOARDING_LAYOUT_TITLE, UIKeys.ONBOARDING_LAYOUT_SLOGAN);

        UIElement row = new UIElement();
        UIElement description = new UIElement();

        row.row(CARD_GAP).height(CARD_H);

        for (Preset preset : LayoutPresets.ALL)
        {
            UILayoutCard card = new UILayoutCard(preset, (c) -> this.apply(c.preset));

            this.cards.add(card);
            row.add(card);
        }

        description.h(DESCRIPTION_H);
        description.add(new UIRenderable((context) -> this.renderDescription(context, description.area)));

        this.body.column(8).vertical().stretch();
        this.body.add(row, description);
    }

    private void apply(Preset preset)
    {
        UIContext context = this.getContext();
        MapType data = preset.getData();

        if (data == null || context == null || !(context.menu instanceof UIDashboard dashboard))
        {
            return;
        }

        UIFilmPanel film = dashboard.getPanel(UIFilmPanel.class);

        if (film != null)
        {
            film.applyLayoutPreset(data);
            this.selected = preset.id;
        }
    }

    /** What the card under the cursor is for; failing that, the chosen one. */
    private void renderDescription(UIContext context, Area area)
    {
        Preset shown = null;

        for (UILayoutCard card : this.cards)
        {
            if (card.isHovered())
            {
                shown = card.preset;

                break;
            }

            if (card.preset.id.equals(this.selected))
            {
                shown = card.preset;
            }
        }

        if (shown == null)
        {
            return;
        }

        FontRenderer font = context.batcher.getFont();
        String text = font.limitToWidth(shown.description.get(), area.w);

        context.batcher.text(text, area.mx() - font.getWidth(text) / 2, area.my() - font.getHeight() / 2, DIMMED, false);
    }

    /** One shipped layout: its schematic with the name under it. */
    private class UILayoutCard extends UIClickable<UILayoutCard>
    {
        public final Preset preset;

        public UILayoutCard(Preset preset, Consumer<UILayoutCard> callback)
        {
            super(callback);

            this.preset = preset;
        }

        public boolean isHovered()
        {
            return this.hover;
        }

        @Override
        protected UILayoutCard get()
        {
            return this;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            FontRenderer font = context.batcher.getFont();
            Area area = this.area;
            int primary = BBSSettings.primaryColor.get() & Colors.RGB;
            int schematicH = area.h - NAME_H;
            boolean current = this.preset.id.equals(UILayoutPage.this.selected);

            context.batcher.box(area.x, area.y, area.ex(), area.y + schematicH, BBSSettings.deepSurface());

            EditorLayoutNode tree = this.preset.getTree();

            if (tree != null)
            {
                this.renderNode(context, tree, area.x + SEAM, area.y + SEAM, area.w - SEAM * 2, schematicH - SEAM * 2, primary);
            }

            if (current)
            {
                context.batcher.outline(area.x, area.y, area.ex(), area.y + schematicH, Colors.A100 | primary, 2);
            }
            else if (this.hover)
            {
                context.batcher.outline(area.x, area.y, area.ex(), area.y + schematicH, Colors.setA(Colors.WHITE, 0.5F), 1);
            }

            String name = font.limitToWidth(this.preset.name.get(), area.w);

            context.batcher.text(name, area.mx() - font.getWidth(name) / 2, area.y + schematicH + 4, this.hover || current ? Colors.WHITE : DIMMED, false);
        }

        /**
         * The tree, the way the dock lays it out: a horizontal splitter stacks its halves, a
         * vertical one puts them side by side. Leaves are rectangles with the panel's icon
         * when there is room for it; a stack shows as its active panel — what the tabs hold
         * behind it is not a shape.
         */
        private void renderNode(UIContext context, EditorLayoutNode node, int x, int y, int w, int h, int primary)
        {
            if (w <= 0 || h <= 0)
            {
                return;
            }

            if (node instanceof SplitterNode splitter)
            {
                if (splitter.isHorizontal())
                {
                    int h1 = Math.round(h * splitter.getRatio());

                    this.renderNode(context, splitter.getFirst(), x, y, w, h1 - SEAM, primary);
                    this.renderNode(context, splitter.getSecond(), x, y + h1 + SEAM, w, h - h1 - SEAM, primary);
                }
                else
                {
                    int w1 = Math.round(w * splitter.getRatio());

                    this.renderNode(context, splitter.getFirst(), x, y, w1 - SEAM, h, primary);
                    this.renderNode(context, splitter.getSecond(), x + w1 + SEAM, y, w - w1 - SEAM, h, primary);
                }

                return;
            }

            String id = node instanceof PanelNode panel ? panel.getPanelId() : node instanceof StackNode stack ? stack.getActivePanelId() : null;
            int fill = "preview".equals(id) ? Colors.A50 | primary : Colors.setA(Colors.WHITE, "main".equals(id) ? 0.22F : 0.12F);

            context.batcher.box(x, y, x + w, y + h, fill);

            Icon icon = this.iconOf(id);

            if (icon != null && w >= ICON_ROOM && h >= ICON_ROOM)
            {
                context.batcher.icon(icon, Colors.setA(Colors.WHITE, 0.8F), x + w / 2F, y + h / 2F, 0.5F, 0.5F);
            }
        }

        /** The icons the dock's own tabs wear, so the schematic reads the same as the editor. */
        private Icon iconOf(String id)
        {
            if (id == null)
            {
                return null;
            }

            return switch (id)
            {
                case "preview" -> Icons.VIDEO_CAMERA;
                case "main" -> Icons.FILM;
                case "editArea" -> Icons.EDITOR;
                case "replaysList" -> Icons.LIST;
                case "replayProps" -> Icons.PROPERTIES;
                default -> null;
            };
        }
    }
}
