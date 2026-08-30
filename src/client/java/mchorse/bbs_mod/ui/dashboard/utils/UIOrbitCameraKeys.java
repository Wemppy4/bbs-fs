package mchorse.bbs_mod.ui.dashboard.utils;

import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;

import java.util.function.Supplier;

/** An element that only listens for keys: the orbit camera's keybinds, above the open panel's own. */
public class UIOrbitCameraKeys implements IUIElement
{
    private UIDashboard dashboard;
    private Supplier<Boolean> enabled;

    public UIOrbitCameraKeys(UIDashboard dashboard)
    {
        this.dashboard = dashboard;
    }

    public void setEnabled(Supplier<Boolean> enabled)
    {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled()
    {
        boolean enabled = this.enabled == null || this.enabled.get();

        return enabled && this.dashboard.orbitUI.isEnabled();
    }

    @Override
    public IUIElement keyPressed(UIContext context)
    {
        if (context.isFocused())
        {
            return null;
        }

        if (this.dashboard.getPanels().panel instanceof IUIOrbitKeysHandler handler && handler.handleKeyPressed(context))
        {
            return this;
        }

        return this.dashboard.orbitUI.getControl() && this.dashboard.orbit.keyPressed(context) ? this : null;
    }
}
