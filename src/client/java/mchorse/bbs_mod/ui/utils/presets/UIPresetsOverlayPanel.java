package mchorse.bbs_mod.ui.utils.presets;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.presets.PresetManager;

public class UIPresetsOverlayPanel extends UIListOverlayPanel {

    private String cwd = "";
    private final UICopyPasteController controller;
    private final int mouseX;
    private final int mouseY;

    public UIPresetsOverlayPanel(UICopyPasteController controller, int mouseX, int mouseY)
    {
        super(UIKeys.PRESETS_TITLE, null);

        this.controller = controller;
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        this.callback = (l) ->
        {
            String pick = l.get(0);

            if ("..".equals(pick))
            {
                this.goUp();
                this.refreshList();

                return;
            }

            if (pick.endsWith("/"))
            {
                String name = pick.substring(0, pick.length() - 1);
                this.cwd = PresetManager.joinRelative(this.cwd, name);
                this.refreshList();

                return;
            }

            String id = PresetManager.joinRelative(this.cwd, pick);
            MapType load = this.controller.manager.load(id);

            if (load != null)
            {
                this.controller.getConsumer().paste(load, this.mouseX, this.mouseY);
                this.close();
            }
        };

        this.refreshList();

        UIIcon save = new UIIcon(Icons.SAVED, (b) ->
        {
            MapType type = this.controller.getSupplier().get();

            if (type != null)
            {
                UIPromptOverlayPanel pane = new UIPromptOverlayPanel(UIKeys.PRESETS_SAVE_TITLE, UIKeys.PRESETS_SAVE_DESCRIPTION, (t) ->
                {
                    this.controller.manager.save(t, type);
                    this.refreshList();
                });

                pane.text.filename();
                UIOverlay.addOverlay(this.getContext(), pane);
            }
        });

        save.setEnabled(controller.canCopy());

        UIIcon folder = new UIIcon(Icons.FOLDER, (b) ->
        {
            UIUtils.openFolder(this.controller.manager.getFolder());
        });

        save.tooltip(UIKeys.PRESETS_SAVE, Direction.LEFT);
        folder.tooltip(UIKeys.PRESETS_OPEN, Direction.LEFT);
        this.icons.add(save, folder);
    }

    private void goUp()
    {
        if (this.cwd.isEmpty())
        {
            return;
        }

        int i = this.cwd.lastIndexOf('/');

        this.cwd = i < 0 ? "" : this.cwd.substring(0, i);
    }

    private void refreshList()
    {
        this.list.list.clear();
        this.addValues(this.controller.manager.listDirectory(this.cwd));
    }
}
