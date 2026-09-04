package mchorse.bbs_mod.ui.dashboard.utils;

import mchorse.bbs_mod.camera.OrbitCamera;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.utils.Area;


public class UIOrbitCamera implements IUIElement
{
    /** The key free look holds the pointer by - see {@link Window#setCursorHidden}. */
    private static final String POINTER = "flight_free_look";

    public OrbitCamera orbit = new OrbitCamera();
    private boolean control;
    private boolean enabled = true;
    private boolean freeLook;
    private boolean pickUpMouse;

    public boolean canControl()
    {
        return this.control;
    }

    public boolean getControl()
    {
        return this.control;
    }

    public void setControl(boolean control)
    {
        this.control = control;

        if (!control)
        {
            /* Nothing is being flown any more, so the mouse is nobody's to hold. */
            this.setFreeLook(false);
        }
    }

    public boolean isFreeLook()
    {
        return this.freeLook;
    }

    /**
     * Free look turns the camera with the mouse itself, no button held down. The pointer goes
     * with it - hidden, so the view can keep turning past the edge of the screen, which is the
     * whole point of not having to let go and drag again.
     *
     * <p>Whoever owns the flight decides when this is on, since it is the one that knows what
     * else wants the mouse - see {@link mchorse.bbs_mod.ui.film.UIFilmPanel#render}.</p>
     */
    public void setFreeLook(boolean freeLook)
    {
        if (this.freeLook == freeLook)
        {
            return;
        }

        /* A button that is still held belongs to whatever it was pressed on - the flight icon,
         * most of the time - so the takeover waits for it to be let go. Otherwise that element
         * never hears the release, and stays stuck pressed. The caller asks every frame, so
         * this only defers the takeover by as long as the button is down. */
        if (freeLook && (Window.isMouseButtonPressed(0) || Window.isMouseButtonPressed(1) || Window.isMouseButtonPressed(2)))
        {
            return;
        }

        this.freeLook = freeLook;
        this.orbit.setFreeLook(freeLook);

        /* Taking the pointer over moves it, and handing it back moves it again - either jump
         * would be read as a swing of the camera, so the next frame only picks the mouse up. */
        this.pickUpMouse = true;

        Window.setCursorHidden(POINTER, freeLook);
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    @Override
    public IUIElement mouseClicked(UIContext context)
    {
        int i = this.orbit.canStart(context);

        if (i >= 0)
        {
            this.orbit.start(i, context.mouseX, context.mouseY);

            return this;
        }

        return null;
    }

    @Override
    public IUIElement mouseScrolled(UIContext context)
    {
        if (!this.control)
        {
            return null;
        }

        return this.orbit.scroll((int) context.mouseWheel) ? this : null;
    }

    @Override
    public IUIElement mouseReleased(UIContext context)
    {
        this.orbit.release();

        /* Free look takes over again from where the button's drag left the mouse, rather than
         * reading the gap between the two as one swing of the camera. */
        if (this.freeLook)
        {
            this.orbit.cache(context.mouseX, context.mouseY);
        }

        return null;
    }

    @Override
    public void render(UIContext context)
    {
        if (!this.control)
        {
            this.orbit.cache(context.mouseX, context.mouseY);

            return;
        }

        if (this.freeLook)
        {
            /* Asked for every frame rather than once: Minecraft gives the pointer back
             * whenever a screen opens, and a flight outlives that. */
            Window.setCursorHidden(POINTER, true);
        }

        if (this.pickUpMouse)
        {
            this.orbit.cache(context.mouseX, context.mouseY);

            this.pickUpMouse = false;
        }

        this.orbit.drag(context.mouseX, context.mouseY);
        this.orbit.update(context);
    }

    /* Unimplemented GUI element methods */

    @Override
    public boolean isEnabled()
    {
        return this.enabled;
    }

    @Override
    public boolean canBeRendered(Area area)
    {
        return true;
    }
}
