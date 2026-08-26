package mchorse.bbs_mod.ui.framework.elements.input;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.color.UIColorPicker;
import mchorse.bbs_mod.ui.framework.elements.input.color.UIColorPresets;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

/**
 * Color GUI element
 *
 * This class is responsible for providing a way to edit colors, this element
 * itself is not editing the color, the picker element is the one that does color editing
 */
public class UIColor extends UIElement
{
    public UIColorPicker picker;
    public boolean label = true;
    public Direction direction;

    private UIElement target;

    public UIColor(Consumer<Integer> callback)
    {
        super();

        /* The picker sizes itself around its own contents the first time it lays out */
        this.picker = new UIColorPicker(callback);

        this.direction(Direction.BOTTOM).h(UIConstants.CONTROL_HEIGHT);
    }

    public UIColor withTarget(UIElement target)
    {
        this.target = target;

        return this;
    }

    public UIColor withAlpha()
    {
        this.picker.editAlpha();

        return this;
    }

    public UIColor direction(Direction direction)
    {
        this.direction = direction;
        this.picker.anchor(1 - direction.anchorX, 1 - direction.anchorY);

        return this;
    }

    public UIColor onTop()
    {
        return this.direction(Direction.TOP);
    }

    public UIColor noLabel()
    {
        this.label = false;

        return this;
    }

    public void setColor(int color)
    {
        this.picker.setColor(color);
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            if (!this.picker.hasParent())
            {
                UIElement target = this.popupTarget(context);

                target.add(this.picker);
                this.picker.setup(this.popupX(context), this.popupY(context));
                this.picker.bounds(context.menu.main, 2);
                this.picker.resize();
            }
            else
            {
                this.picker.removeFromParent();
            }

            return true;
        }

        /* The presets are the quick way past the picker, so they hang off the other button */
        if (this.area.isInside(context) && context.mouseButton == 1)
        {
            this.openPresets(context);

            return true;
        }

        return super.subMouseClicked(context);
    }

    /* Where a popup of this swatch goes: beside it, on the side {@link #direction} points at */

    private UIElement popupTarget(UIContext context)
    {
        return this.target == null ? context.menu.overlay : this.target;
    }

    private int popupX(UIContext context)
    {
        return context.globalX(this.area.x(this.direction.anchorX) + 2 * this.direction.factorX);
    }

    private int popupY(UIContext context)
    {
        return context.globalY(this.area.y(this.direction.anchorY) + 2 * this.direction.factorY);
    }

    private void openPresets(UIContext context)
    {
        UIColorPresets presets = new UIColorPresets(this.picker::applyOpaqueColor);
        UIElement target = this.popupTarget(context);

        /* One popup of this swatch at a time */
        this.picker.removeFromParent();

        presets.anchor(1 - this.direction.anchorX, 1 - this.direction.anchorY);
        target.add(presets);
        presets.setup(this.popupX(context), this.popupY(context));
        presets.bounds(context.menu.main, 2);
        presets.resize();
    }

    @Override
    public void render(UIContext context)
    {
        int padding = 0;

        this.picker.renderRect(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.ey());

        if (this.area.isInside(context))
        {
            this.area.render(context.batcher, Colors.A12, padding);
        }

        if (this.label)
        {
            FontRenderer font = context.batcher.getFont();
            String label = this.picker.color.stringify(this.picker.editAlpha);

            context.batcher.textCard(label, this.area.mx(font.getWidth(label)), this.area.my(font.getHeight() - 1), Colors.WHITE, Colors.A25, 1);
        }

        this.renderLockedArea(context);

        super.render(context);
    }
}
