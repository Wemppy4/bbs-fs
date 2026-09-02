package mchorse.bbs_mod.ui.structures;

import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;

import java.util.function.BiConsumer;

/**
 * The one screen the structure wand opens: the save dialog and nothing else. It exists only because
 * overlays need a menu to live in — it shows the dialog as soon as it opens and closes itself
 * again the moment the dialog is done, whether the save was confirmed or dropped.
 */
public class UIStructureSaveMenu extends UIBaseMenu
{
    private static final int WIDTH = 520;
    private static final int HEIGHT = 300;

    private final String name;
    private final BiConsumer<String, Boolean> callback;

    public UIStructureSaveMenu(String name, BiConsumer<String, Boolean> callback)
    {
        this.name = name;
        this.callback = callback;
    }

    @Override
    public void onOpen(UIBaseMenu oldMenu)
    {
        super.onOpen(oldMenu);

        UIStructureSavePanel panel = new UIStructureSavePanel(this.name, this.callback);

        /* Confirmed or dismissed, there is nothing else on this screen to come back to */
        panel.onClose((e) -> this.closeThisMenu());

        UIOverlay.addOverlay(this.context, panel, WIDTH, HEIGHT);
    }
}
