package mchorse.bbs_mod.ui.dashboard.panels;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UICRUDOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public abstract class UICRUDDashboardPanel extends UIEditorDashboardPanel
{
    public UIIcon openOverlay;

    public final UICRUDOverlayPanel overlay;

    public UICRUDDashboardPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.overlay = this.createOverlayPanel();
        this.openOverlay = new UIIcon(Icons.MORE, (b) ->
        {
            UIOverlay.addOverlay(this.getContext(), this.overlay, 200, 0.9F);
        });

        this.actions().menu(this.openOverlay);

        this.keys().register(Keys.OPEN_DATA_MANAGER, this::openDataManager);
    }

    protected void openDataManager()
    {
        UIContext context = this.getContext();

        if (context != null)
        {
            this.openOverlay.clickItself(context);
        }
    }

    protected abstract UICRUDOverlayPanel createOverlayPanel();

    protected abstract IKey getTitle();

    public abstract void pickData(String id);
}
