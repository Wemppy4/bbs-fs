package mchorse.bbs_mod.ui.structures;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;

import java.util.function.Consumer;

/**
 * The one screen the structure wand opens: a name prompt and nothing else. It exists only because
 * overlays need a menu to live in — it shows the prompt as soon as it opens and closes itself
 * again the moment the prompt is done, whether the name was confirmed or dropped.
 */
public class UIStructureSaveMenu extends UIBaseMenu
{
    private final String name;
    private final Consumer<String> callback;

    public UIStructureSaveMenu(String name, Consumer<String> callback)
    {
        this.name = name;
        this.callback = callback;
    }

    @Override
    public void onOpen(UIBaseMenu oldMenu)
    {
        super.onOpen(oldMenu);

        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            L10n.lang("bbs.ui.structure_wand.save_title"),
            L10n.lang("bbs.ui.structure_wand.save_message"),
            (name) ->
            {
                if (name != null && !name.trim().isEmpty())
                {
                    this.callback.accept(name.trim());
                }
            }
        );

        panel.text.setText(this.name);
        /* Confirmed or dismissed, there is nothing else on this screen to come back to */
        panel.onClose((e) -> this.closeThisMenu());

        UIOverlay.addOverlay(this.context, panel, 240, 80);
    }
}
