package mchorse.bbs_mod.api;

/**
 * An addon that can be switched off without restarting the game.
 *
 * <p>Implemented by the addon's own entry point object (the {@code bbs-addon} or the
 * {@code bbs-client-addon} one). The addons window then shows a working switch for it and calls
 * {@link #setAddonEnabled(boolean)} when the user flips it. What "off" means is up to the addon:
 * stop drawing, stop reacting, hide its panels. BBS keeps no state of its own, so an addon that
 * wants the switch to survive a restart saves it itself, and {@link #isAddonEnabled()} is asked
 * every time the window is drawn.</p>
 *
 * <p>Addons without it still show the switch, always on, which does nothing.</p>
 *
 * <p>Since API version 3.</p>
 */
public interface BBSAddonSwitch
{
    public boolean isAddonEnabled();

    public void setAddonEnabled(boolean enabled);
}
