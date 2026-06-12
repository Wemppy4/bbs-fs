package mchorse.bbs_mod.settings.ui;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.value.ValueKeyCombo;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.HashMap;
import java.util.Map;

public class UISettingsOverlayPanel extends UIOverlayPanel
{
    private static final int SIDE_WIDTH = 130;

    public UITextbox search;
    public UIScrollView sections;
    public UIScrollView options;

    private Settings settings;
    private ValueGroup category;
    private UIIcon currentModule;
    private String filter = "";

    private final Map<String, UIIcon> moduleButtons = new HashMap<>();

    public UISettingsOverlayPanel()
    {
        super(UIKeys.CONFIG_TITLE);

        this.search = new UITextbox(100, (str) ->
        {
            this.filter = str.toLowerCase().trim();
            this.refresh();
        });
        this.search.placeholder(UIKeys.GENERAL_SEARCH);
        this.search.relative(this.content).x(6).y(8).w(SIDE_WIDTH - 12).h(20);

        this.sections = new UIScrollView(ScrollDirection.VERTICAL);
        this.sections.relative(this.content).x(4).y(34).w(SIDE_WIDTH - 8).h(1F, -38);
        this.sections.column(2).vertical().stretch().scroll().padding(2).height(16);

        this.options = new UIScrollView(ScrollDirection.VERTICAL);
        this.options.scroll.scrollSpeed = 51;
        this.options.relative(this.content).x(SIDE_WIDTH).y(0).w(1F, -SIDE_WIDTH).h(1F);
        this.options.column().scroll().vertical().stretch().padding(8).height(20);

        UIIcon defaultButton = null;
        String defaultMod = null;

        for (Settings settings : BBSMod.getSettings().modules.values())
        {
            UIIcon icon = new UIIcon(settings.icon, (b) -> this.selectConfig(settings.getId(), b));

            icon.tooltip(L10n.lang(UIValueFactory.getTitleKey(settings)), Direction.LEFT);
            this.icons.add(icon);
            this.moduleButtons.put(settings.getId(), icon);

            if (defaultButton == null || settings.getId().equals("bbs"))
            {
                defaultButton = icon;
                defaultMod = settings.getId();
            }
        }

        this.content.add(this.search, this.sections, this.options);

        this.selectConfig(defaultMod, defaultButton);
        this.markContainer();
    }

    public boolean isCurrent(ValueGroup category)
    {
        return this.filter.isEmpty() && this.category == category;
    }

    public void selectConfig(String mod, UIIcon button)
    {
        this.settings = BBSMod.getSettings().modules.get(mod);
        this.currentModule = button;
        this.filter = "";
        this.search.setText("");

        this.buildSections();
    }

    public void showCategory(String mod, String categoryId)
    {
        this.selectConfig(mod, this.moduleButtons.get(mod));

        if (this.settings != null)
        {
            ValueGroup group = this.settings.categories.get(categoryId);

            if (group != null)
            {
                this.selectCategory(group);
            }
        }
    }

    public void selectCategory(ValueGroup category)
    {
        this.category = category;

        if (!this.filter.isEmpty())
        {
            this.filter = "";
            this.search.setText("");
        }

        this.refresh();
    }

    private void buildSections()
    {
        this.sections.removeAll();
        this.category = null;

        for (ValueGroup category : this.settings.categories.values())
        {
            if (!category.isVisible())
            {
                continue;
            }

            if (this.category == null)
            {
                this.category = category;
            }

            this.sections.add(new UISectionButton(this, category));
        }

        this.sections.resize();
        this.refresh();
    }

    public void refresh()
    {
        if (this.settings == null)
        {
            return;
        }

        this.options.removeAll();

        if (this.filter.isEmpty())
        {
            if (this.category != null)
            {
                this.options.add(new UISectionHeader(this, this.category));
                this.appendValues(this.category, false);
            }
        }
        else
        {
            boolean first = true;

            for (ValueGroup category : this.settings.categories.values())
            {
                if (!category.isVisible() || !this.hasMatch(category))
                {
                    continue;
                }

                UISectionHeader header = new UISectionHeader(this, category);

                if (!first)
                {
                    header.marginTop(16);
                }

                this.options.add(header);
                this.appendValues(category, true);

                first = false;
            }
        }

        this.options.resize();
    }

    public void applyVideoPreset(int width, int height)
    {
        BBSSettings.videoArguments.set(BBSSettings.DEFAULT_FFMPEG_ARGUMENTS);
        BBSSettings.videoWidth.set(width);
        BBSSettings.videoHeight.set(height);
        BBSSettings.videoFrameRate.set(60);

        this.refresh();
    }

    public void swapVideoResolution()
    {
        int width = BBSSettings.videoWidth.get();

        BBSSettings.videoWidth.set(BBSSettings.videoHeight.get());
        BBSSettings.videoHeight.set(width);

        this.refresh();
    }

    private void appendValues(ValueGroup category, boolean filtered)
    {
        for (BaseValue value : category.getAll())
        {
            if (!this.isValueVisible(value) || (filtered && !this.matches(value)))
            {
                continue;
            }

            for (UIElement element : UIValueMap.create(value, this))
            {
                this.options.add(element);
            }
        }
    }

    private boolean hasMatch(ValueGroup category)
    {
        for (BaseValue value : category.getAll())
        {
            if (this.isValueVisible(value) && this.matches(value))
            {
                return true;
            }
        }

        return false;
    }

    private boolean matches(BaseValue value)
    {
        IKey key = L10n.lang(UIValueFactory.getValueLabelKey(value));

        if (value instanceof ValueKeyCombo combo)
        {
            key = combo.get().label;
        }

        String label = key.get().toLowerCase();

        return label.contains(this.filter) || value.getId().toLowerCase().contains(this.filter);
    }

    private boolean isValueVisible(BaseValue value)
    {
        if (!value.isVisible())
        {
            return false;
        }

        if (value == BBSSettings.editorPreviewCustomWidth || value == BBSSettings.editorPreviewCustomHeight)
        {
            return BBSSettings.editorPreviewSizeMode.get() == 1;
        }
        else if (value == BBSSettings.editorPreviewResolutionScale)
        {
            return BBSSettings.editorPreviewSizeMode.get() == 2;
        }

        return true;
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);

        int x = this.content.area.x;
        int y = this.content.area.y;
        int ey = this.content.area.ey();

        context.batcher.box(x, y, x + SIDE_WIDTH, ey, BBSSettings.chromeSurface());
        context.batcher.box(x + SIDE_WIDTH, y, x + SIDE_WIDTH + 1, ey, BBSSettings.dividerColor());

        if (this.currentModule != null)
        {
            this.currentModule.area.render(context.batcher, BBSSettings.primaryColor(Colors.A100));
        }
    }

    /**
     * A clickable section row in the left list — icon plus localized title,
     * highlighted with the menu gradient when it's the active section.
     */
    public static class UISectionButton extends UIClickable<UISectionButton>
    {
        private final UISettingsOverlayPanel panel;
        private final ValueGroup category;
        private final IKey label;

        public UISectionButton(UISettingsOverlayPanel panel, ValueGroup category)
        {
            super(null);

            this.panel = panel;
            this.category = category;
            this.label = L10n.lang(UIValueFactory.getCategoryTitleKey(category));
            this.callback = (b) -> panel.selectCategory(category);

            this.tooltip(L10n.lang(UIValueFactory.getCategoryTooltipKey(category)), Direction.RIGHT);
        }

        @Override
        protected UISectionButton get()
        {
            return this;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            Icon icon = this.category.icon != null ? this.category.icon : this.panel.settings.icon;

            if (this.panel.isCurrent(this.category))
            {
                int color = BBSSettings.primaryColor.get();

                context.batcher.box(this.area.x, this.area.y, this.area.x + 2, this.area.ey(), Colors.A100 | color);
                context.batcher.gradientHBox(this.area.x + 2, this.area.y, this.area.ex(), this.area.ey(), Colors.A75 | color, color);
            }
            else if (this.hover)
            {
                this.area.render(context.batcher, Colors.setA(Colors.WHITE, 0.1F));
            }

            context.batcher.icon(icon, Colors.WHITE, this.area.x + 5, this.area.my(), 0F, 0.5F);

            FontRenderer font = context.batcher.getFont();
            String label = font.limitToWidth(this.label.get(), this.area.w - 28);

            context.batcher.text(label, this.area.x + 23, this.area.my(font.getHeight()), Colors.WHITE, true);
        }
    }

    /**
     * The page header above a section's options — icon, title and a divider
     * underline.
     */
    public static class UISectionHeader extends UIElement
    {
        private final ValueGroup category;
        private final IKey label;

        public UISectionHeader(UISettingsOverlayPanel panel, ValueGroup category)
        {
            this.category = category;
            this.label = L10n.lang(UIValueFactory.getCategoryTitleKey(category));

            this.h(18);

            if (category.getId().equals("video"))
            {
                UIIcon presets = new UIIcon(Icons.FILM, (b) -> b.getContext().replaceContextMenu((menu) ->
                {
                    menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_720p, () -> panel.applyVideoPreset(1280, 720));
                    menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_1080P, () -> panel.applyVideoPreset(1920, 1080));
                    menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_SHORTS_1080P, () -> panel.applyVideoPreset(1080, 1920));
                    menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_1440P, () -> panel.applyVideoPreset(2560, 1440));
                    menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_4K, () -> panel.applyVideoPreset(3840, 2160));
                }));
                UIIcon swap = new UIIcon(Icons.REFRESH, (b) -> panel.swapVideoResolution());

                swap.tooltip(UIKeys.VIDEO_SETTINGS_SWAP);
                swap.wh(16, 16);
                presets.tooltip(UIKeys.GENERAL_PRESETS);
                presets.wh(16, 16);

                UIElement row = UI.row(swap, presets);

                row.relative(this).x(1F, -4).y(0.5F, -1).wh(32, 16).anchor(1F, 0.5F);
                this.add(row);
            }
        }

        @Override
        public void render(UIContext context)
        {
            FontRenderer font = context.batcher.getFont();
            int x = this.area.x;

            if (this.category.icon != null)
            {
                context.batcher.icon(this.category.icon, Colors.WHITE, x, this.area.my() - 1, 0F, 0.5F);
                x += 20;
            }

            context.batcher.text(this.label.get(), x, this.area.my(font.getHeight()) - 1, Colors.WHITE, true);
            context.batcher.box(this.area.x, this.area.ey() - 1, this.area.ex(), this.area.ey(), BBSSettings.dividerColor());

            super.render(context);
        }
    }
}
