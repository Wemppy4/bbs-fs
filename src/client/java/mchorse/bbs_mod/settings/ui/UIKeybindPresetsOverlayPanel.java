package mchorse.bbs_mod.settings.ui;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.context.UIPromptPopup;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeybindPresets;

import java.io.IOException;

public class UIKeybindPresetsOverlayPanel extends UIOverlayPanel
{
    private final Settings settings;
    private final Runnable onLoaded;
    private final UIStringList list;

    public UIKeybindPresetsOverlayPanel(Settings settings, Runnable onLoaded)
    {
        super(L10n.lang("bbs.ui.config.keybind-presets.title"));
        this.wh(320, 240);

        this.settings = settings;
        this.onLoaded = onLoaded;
        this.list = new UIStringList((selection) -> {});
        this.list.background();
        this.list.emptyState(L10n.lang("bbs.ui.config.keybind-presets.empty"));
        this.list.relative(this.content).xy(6, 6).w(1F, -12).h(1F, -12);
        this.list.context((menu) ->
        {
            menu.action(Icons.SAVE, L10n.lang("bbs.ui.config.keybind-presets.save"), this::promptSave);

            int index = this.list.getHoveredIndex(this.getContext());

            if (index >= 0 && index < this.list.getList().size())
            {
                String name = this.list.getList().get(index);

                this.list.setCurrent(name);
                menu.action(Icons.CHECKMARK, L10n.lang("bbs.ui.config.keybind-presets.apply"), () -> this.apply(name));
                menu.action(Icons.TRASH, L10n.lang("bbs.ui.config.keybind-presets.delete"), () -> this.confirmDelete(name));
            }
        });
        this.content.add(this.list);
    }

    @Override
    protected void onAdd(UIElement parent)
    {
        super.onAdd(parent);

        this.reload(null);
    }

    private void reload(String selected)
    {
        try
        {
            this.list.clear();
            this.list.add(KeybindPresets.names());

            if (selected != null) this.list.setCurrentScroll(selected);
        }
        catch (IOException e) { this.error(); }
    }

    private void promptSave()
    {
        UIPromptPopup prompt = new UIPromptPopup(
            L10n.lang("bbs.ui.config.keybind-presets.save"),
            L10n.lang("bbs.ui.config.keybind-presets.name"),
            (input) ->
            {
                String name = input.trim();

                if (name.isEmpty()) return;

                try
                {
                    if (KeybindPresets.exists(name))
                    {
                        UIOverlay.addOverlay(this.getContext(), new UIConfirmOverlayPanel(
                            L10n.lang("bbs.ui.config.keybind-presets.save"),
                            L10n.lang("bbs.ui.config.keybind-presets.overwrite").format(name),
                            (confirmed) -> { if (confirmed) this.save(name); }).verb(UIKeys.GENERAL_SAVE));
                    }
                    else this.save(name);
                }
                catch (IOException e) { this.error(); }
            });

        prompt.verb(UIKeys.GENERAL_SAVE);

        this.getContext().replaceContextMenu(prompt);
    }

    private void save(String name)
    {
        try
        {
            KeybindPresets.save(name, this.settings);
            this.reload(name);
        }
        catch (IOException e) { this.error(); }
    }

    private void apply(String name)
    {
        try
        {
            if (KeybindPresets.load(name, this.settings))
            {
                this.onLoaded.run();
                this.close();
            }
            else this.error();
        }
        catch (IOException e) { this.error(); }
    }

    private void confirmDelete(String name)
    {
        UIOverlay.addOverlay(this.getContext(), new UIConfirmOverlayPanel(
            L10n.lang("bbs.ui.config.keybind-presets.delete-title"),
            L10n.lang("bbs.ui.config.keybind-presets.delete-confirm").format(name),
            (confirmed) ->
            {
                if (!confirmed) return;

                try
                {
                    KeybindPresets.delete(name);
                    this.reload(null);
                }
                catch (IOException e) { this.error(); }
            }).verb(UIKeys.GENERAL_REMOVE).danger());
    }

    private void error()
    {
        this.getContext().notifyError(L10n.lang("bbs.ui.config.keybind-presets.error"));
    }
}
