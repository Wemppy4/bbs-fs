package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.utils.InterfaceBlur;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.resizers.Flex;
import mchorse.bbs_mod.utils.colors.Colors;
import org.joml.Vector2i;

import java.util.HashMap;
import java.util.Map;

public class UIOverlay extends UIElement
{
    private static final Map<String, Vector2i> offsets = new HashMap<>();

    /** Sizes the user dragged panels to, by class, alongside {@link #offsets}. */
    private static final Map<String, Vector2i> sizes = new HashMap<>();

    /**
     * How large a panel that didn't say how big it is may grow. The default is a share of the
     * screen, and a share of a wide one is a lot of empty box around content that doesn't grow
     * with it.
     */
    private static final int MAX_WIDTH = 420;
    private static final int MAX_HEIGHT = 340;

    /** Null means the dimming from the settings; a set value overrides it. */
    private Integer background;

    /**
     * Opens the panel in the middle of the screen. How big it is, is the panel's own business: it
     * sets its width and height in its constructor like any other element, and an axis it leaves
     * alone gets the default share of the screen.
     */
    public static UIOverlay addOverlay(UIContext context, UIOverlayPanel panel)
    {
        UIOverlay overlay = new UIOverlay();
        Flex flex = panel.getFlex();

        panel.relative(overlay).xy(0.5F, 0.5F).anchor(0.5F).bounds(overlay, 0);

        if (isUnset(flex.w))
        {
            flex.w.set(0.5F);
            flex.w.max = MAX_WIDTH;
        }

        /* Only a panel that didn't pick its height gets to measure it from its content */
        if (isUnset(flex.h))
        {
            flex.h.set(0.5F);
            flex.h.max = MAX_HEIGHT;
            panel.sizeless();
        }

        setupPanel(context, overlay, panel);

        return overlay;
    }

    private static boolean isUnset(Flex.Unit unit)
    {
        return unit.value == 0F && unit.offset == 0 && unit.target == null;
    }

    public static void setupPanel(UIContext context, UIOverlay overlay, UIOverlayPanel panel)
    {
        if (panel.hasParent())
        {
            return;
        }

        Flex flex = panel.getFlex();
        Vector2i offset = offsets.get(panel.getClass().getSimpleName());

        panel.setInitialOffset(flex.x.offset, flex.y.offset);
        panel.setupResize();

        if (offset != null)
        {
            flex.x.offset = offset.x;
            flex.y.offset = offset.y;
        }

        Vector2i size = sizes.get(panel.getClass().getSimpleName());

        /* A size the user picked outranks both the share of the screen and the cap on it */
        if (size != null && panel.isResizable())
        {
            panel.restoreSize(size);
        }

        overlay.full(context.menu.overlay);
        context.menu.overlay.add(overlay);
        overlay.add(panel);
        context.menu.overlay.resize();
    }

    public static boolean has(UIContext context)
    {
        return !context.menu.getRoot().getChildren(UIOverlayPanel.class).isEmpty();
    }

    public UIOverlay()
    {
        this.eventPropagataion(EventPropagation.BLOCK).markContainer();
    }

    public UIOverlay background(int background)
    {
        this.background = background;

        return this;
    }

    public UIOverlay noBackground()
    {
        return this.background(0);
    }

    public void closeItself()
    {
        this.removeFromParent();
        UIUtils.playClick();

        for (UIOverlayPanel element : this.getChildren(UIOverlayPanel.class))
        {
            element.removeFromParent();
            element.onClose();

            /* Save offset, and the size if the user picked one */
            Vector2i offset = new Vector2i(element.getFlex().x.offset, element.getFlex().y.offset);

            offsets.put(element.getClass().getSimpleName(), offset);

            if (element.wasResized())
            {
                Vector2i size = new Vector2i(element.getFlex().w.offset, element.getFlex().h.offset);

                sizes.put(element.getClass().getSimpleName(), size);
            }
            else
            {
                /* Sized once and then reset with Ctrl+click: back to opening at its own size */
                sizes.remove(element.getClass().getSimpleName());
            }
        }
    }

    /* Don't pass user input down the line... */

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        this.closeItself();

        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        int background = this.background == null ? BBSSettings.overlayBackground() : this.background;

        /* An overlay that asked for no background of its own gets no blur either; the setting
         * turned down to zero is a different thing — that is the dimming off, not the glass */
        if (this.background == null || Colors.getA(this.background) > 0F)
        {
            InterfaceBlur.apply();
        }

        if (Colors.getA(background) > 0F)
        {
            this.area.render(context.batcher, background);
        }

        super.render(context);
    }
}