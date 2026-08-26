package mchorse.bbs_mod.ui.framework.elements;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

import java.util.Map;
import java.util.function.Consumer;

/**
 * A collapsible section: a clickable header bar (a fold arrow and a title sitting
 * inside a tinted card) and a body of fields that toggles open and closed. Put any
 * parameters into {@link #fields} and stack sections inside a scroll view. State is
 * purely in-memory and resets when the panel is rebuilt.
 */
public class UISection extends UIElement
{
    private static final int HEADER_HEIGHT = 10;
    private static final int ARROW_SIZE = 8;
    /** Icon column of a header that has one: 16px icon plus the gap to the title. */
    private static final int ICON_WIDTH = 20;

    private static final Area HEADER = new Area();

    public UILabel title;
    public UIElement fields;

    private boolean expanded = true;
    private Consumer<UISection> callback;

    public UISection()
    {
        this(IKey.EMPTY);
    }

    public UISection(IKey title)
    {
        super();

        this.title = new UILabel(title)
        {
            @Override
            public void render(UIContext context)
            {
                UISection.this.renderHeader(context, this);
            }
        };
        this.title.h(HEADER_HEIGHT);

        this.fields = new UIElement();
        this.fields.column().stretch().vertical().height(20);

        this.column(UIConstants.MARGIN).stretch().vertical().padding(4);
        this.add(this.title, this.fields);
    }

    public UISection title(IKey title)
    {
        this.title.label = title;

        return this;
    }

    /**
     * Notified whenever the fold state changes, so an owner can remember it —
     * panels are rebuilt from scratch on many editor actions, and a section
     * that always came back at its default would undo the user's fold.
     */
    public UISection onToggle(Consumer<UISection> callback)
    {
        this.callback = callback;

        return this;
    }

    /**
     * Keep the fold in the owner's map across rebuilds: open as last left there
     * ({@code defaultExpanded} on first sight) and write every toggle back under
     * {@code key}. The map lives with the owner, so nothing here is static.
     */
    public UISection remember(Map<String, Boolean> folds, String key, boolean defaultExpanded)
    {
        this.setExpanded(folds.getOrDefault(key, defaultExpanded));

        return this.onToggle((section) -> folds.put(key, section.isExpanded()));
    }

    public boolean isExpanded()
    {
        return this.expanded;
    }

    public void toggle()
    {
        this.setExpanded(!this.expanded);
    }

    public void setExpanded(boolean expanded)
    {
        if (this.expanded == expanded)
        {
            return;
        }

        this.expanded = expanded;

        if (expanded)
        {
            this.add(this.fields);
        }
        else
        {
            this.fields.removeFromParent();
        }

        if (this.callback != null)
        {
            this.callback.accept(this);
        }

        this.resizeParent();
    }

    public void resizeParent()
    {
        if (this.getParent() != null)
        {
            this.getParent().resize();
        }
    }

    @Override
    public void render(UIContext context)
    {
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.raisedSurface());

        /* The block is the raised (light) surface, so inputs inside it drop to the deep surface to
         * stay readable - mirroring how the film editor scopes lightInputs for its dark panels. */
        boolean lightInputs = BBSSettings.lightInputs;

        BBSSettings.lightInputs = false;

        try
        {
            super.render(context);
        }
        finally
        {
            BBSSettings.lightInputs = lightInputs;
        }
    }

    private void renderHeader(UIContext context, UILabel title)
    {
        Area header = title.area;

        /* The 10px header has always set its text one row below true centre; an area one pixel
         * taller reproduces that through the shared centring without moving anything. */
        HEADER.set(header.x, header.y, header.w, header.h + 1);
        renderHeader(context, HEADER, title.label, null, this.expanded, title.color);
    }

    /**
     * The one header look, usable without a section: an optional icon on the left, the
     * title next to it, and - when {@code expanded} is given - the fold arrow on the right.
     * Icon and text centre on the area's height, so callers shape the area to say where
     * "centre" is.
     */
    public static void renderHeader(UIContext context, Area area, IKey title, Icon icon, Boolean expanded, int color)
    {
        FontRenderer font = context.batcher.getFont();
        int x = area.x;
        int right = area.ex();

        if (icon != null)
        {
            context.batcher.icon(icon, Colors.WHITE, x, area.my(), 0F, 0.5F);
            x += ICON_WIDTH;
        }

        if (expanded != null)
        {
            renderArrow(context, right - ARROW_SIZE / 2F, area.my(), expanded);
            right -= ARROW_SIZE + 2;
        }

        String label = font.limitToWidth(title.get(), right - x);

        context.batcher.textShadow(label, x, area.my(font.getHeight()), color);
    }

    /**
     * Draw {@link Icons#ARROW_SMALL} centred at {@code cx}/{@code cy}, rotated for the open state.
     */
    public static void renderArrow(UIContext context, float cx, float cy, boolean expanded)
    {
        MatrixStack matrices = context.batcher.getContext().getMatrices();

        matrices.push();
        matrices.translate(cx, cy, 0F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(expanded ? 90F : 0F), 0F, 0F, 0F);
        context.batcher.icon(Icons.ARROW_SMALL, Colors.WHITE, 0, 0, 0.5F, 0.5F);
        matrices.pop();
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.title.area.isInside(context))
        {
            this.toggle();

            return true;
        }

        return super.subMouseClicked(context);
    }
}
