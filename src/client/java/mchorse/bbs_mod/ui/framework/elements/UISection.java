package mchorse.bbs_mod.ui.framework.elements;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

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

    public UILabel title;
    public UIElement fields;

    private boolean expanded = true;

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
        this.title.background(() -> Colors.A50 | BBSSettings.primaryColor.get());
        this.title.h(HEADER_HEIGHT);

        this.fields = new UIElement();
        this.fields.column().stretch().vertical().height(20);

        this.column(UIConstants.MARGIN).stretch().vertical();
        this.add(this.title, this.fields);
    }

    public UISection title(IKey title)
    {
        this.title.label = title;

        return this;
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

        this.resizeParent();
    }

    public void resizeParent()
    {
        if (this.getParent() != null)
        {
            this.getParent().resize();
        }
    }

    private void renderHeader(UIContext context, UILabel title)
    {
        Area area = title.area;
        FontRenderer font = context.batcher.getFont();
        int background = title.backgroundColor == null ? title.background : title.backgroundColor.get();

        context.batcher.box(area.x, area.y, area.ex(), area.ey(), background);

        this.renderArrow(context, area.ex() - 4 - ARROW_SIZE / 2F, area.my());

        int textX = area.x + 4;
        String label = font.limitToWidth(title.label.get(), area.ex() - 4 - ARROW_SIZE - 4 - textX);

        context.batcher.textShadow(label, textX, area.my() - font.getHeight() / 2, title.color);
    }

    /**
     * Draw {@link Icons#ARROW_SMALL} centred at {@code cx}/{@code cy}, rotated for the open state.
     */
    private void renderArrow(UIContext context, float cx, float cy)
    {
        MatrixStack matrices = context.batcher.getContext().getMatrices();

        matrices.push();
        matrices.translate(cx, cy, 0F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(this.expanded ? 90F : 0F), 0F, 0F, 0F);
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
