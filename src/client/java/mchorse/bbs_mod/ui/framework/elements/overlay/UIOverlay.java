package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
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
     * How large a panel opened without a size may grow. The default is a share of the screen, and a
     * share of a wide one is a lot of empty box around content that doesn't grow with it.
     */
    private static final int MAX_WIDTH = 420;
    private static final int MAX_HEIGHT = 340;

    /** Null means the dimming from the settings; a set value overrides it. */
    private Integer background;

    public static UIOverlay addOverlay(UIContext context, UIOverlayPanel panel)
    {
        UIOverlay overlay = new UIOverlay();
        int width = panel.getPreferredWidth();

        panel.relative(overlay).xy(0.5F, 0.5F).wh(0.5F, 0.5F).anchor(0.5F).bounds(overlay, 0);

        if (width > 0)
        {
            panel.w(width);
        }
        else
        {
            panel.getFlex().w.max = MAX_WIDTH;
        }

        panel.getFlex().h.max = MAX_HEIGHT;
        panel.sizeless();

        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlay(UIContext context, UIOverlayPanel panel, float w, float h)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).xy(0.5F, 0.5F).wh(w, h).anchor(0.5F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlay(UIContext context, UIOverlayPanel panel, int w, int h)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).xy(0.5F, 0.5F).wh(w, h).anchor(0.5F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlay(UIContext context, UIOverlayPanel panel, int w, float h)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).xy(0.5F, 0.5F).w(w).h(h).anchor(0.5F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlayLeft(UIContext context, UIOverlayPanel panel, int w)
    {
        return addOverlayLeft(context, panel, w, 10);
    }

    public static UIOverlay addOverlayLeft(UIContext context, UIOverlayPanel panel, int w, int padding)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).x(padding).y(padding).w(w).h(1F, -padding * 2).anchor(0F, 0F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlayRight(UIContext context, UIOverlayPanel panel, int w)
    {
        return addOverlayRight(context, panel, w, 10);
    }

    public static UIOverlay addOverlayRight(UIContext context, UIOverlayPanel panel, int w, int padding)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).x(1F, -padding).y(padding).w(w).h(1F, -padding * 2).anchor(1F, 0F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
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
            flex.w.max = flex.h.max = 0;
            flex.w.set(0F, size.x);
            flex.h.set(0F, size.y);
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

        if (Colors.getA(background) > 0F)
        {
            this.area.render(context.batcher, background);
        }

        super.render(context);
    }
}