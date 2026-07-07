package mchorse.bbs_mod.ui.forms.editors.utils;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.settings.values.ui.ValueDebugElement;
import mchorse.bbs_mod.settings.values.ui.ValueModelDebug;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact look editor for a model debug overlay (IK or physics), mirroring the
 * motion path's menu: a header with the overlay's toggle, the X-ray mode and
 * dashed lines, then a row per element — a shape (click cycles), an eye, its
 * colour and its size — and the overlay's opacity. Works on any
 * {@link ValueModelDebug}; element labels come from
 * {@code bbs.ui.model_debug.<element id>}.
 */
public class UIDebugOverlayContextMenu extends UIContextMenu
{
    public UIIcon enable;
    public UIIcon xray;
    public UIIcon dashed;
    public UITrackpad opacity;

    private ValueModelDebug config;
    private UIElement column;

    public UIDebugOverlayContextMenu(ValueModelDebug config)
    {
        this.config = config;

        this.enable = new UIIcon(() -> this.config.enabled.get() ? Icons.VISIBLE : Icons.INVISIBLE, (b) -> this.config.enabled.toggle());
        this.enable.tooltip(UIKeys.MODEL_DEBUG_ENABLED);
        this.xray = new UIIcon(Icons.FADING, (b) -> this.config.xray.toggle());
        this.xray.tooltip(UIKeys.MODEL_DEBUG_XRAY);
        this.dashed = new UIIcon(Icons.LINE, (b) -> this.config.dashed.toggle());
        this.dashed.tooltip(UIKeys.MODEL_DEBUG_DASHED);

        this.opacity = new UITrackpad((v) -> this.config.opacity.set(v.floatValue()));
        this.opacity.limit(this.config.opacity).setValue(this.config.opacity.get());

        List<UIElement> rows = new ArrayList<>();

        rows.add(UI.row(this.enable, this.xray, this.dashed));

        for (ValueDebugElement element : config.getElements())
        {
            rows.add(UI.label(L10n.lang("bbs.ui.model_debug." + element.getId())));
            rows.add(this.row(element));
        }

        rows.add(UI.label(UIKeys.MODEL_DEBUG_OPACITY));
        rows.add(this.opacity);

        this.column = UI.column(4, 8, rows.toArray(new UIElement[0]));
        this.column.relative(this).w(210);

        this.add(this.column);
        this.column.resize();
    }

    private UIElement row(ValueDebugElement element)
    {
        UIIcon visible = new UIIcon(() -> element.visible.get() ? Icons.VISIBLE : Icons.INVISIBLE, (b) -> element.visible.toggle());
        UIColor color = new UIColor((c) -> element.color.set(c));
        UITrackpad size = new UITrackpad((v) -> element.size.set(v.floatValue()));

        color.setColor(element.color.get());
        size.limit(element.size).setValue(element.size.get());

        if (element.shape == null)
        {
            return UI.row(visible, color, size);
        }

        UIIcon shape = new UIIcon(() -> shapeIcon(element.shape.get()), (b) -> element.shape.set((element.shape.get() + 1) % ValueDebugElement.SHAPES));

        shape.tooltip(UIKeys.MODEL_DEBUG_SHAPE);

        return UI.row(shape, visible, color, size);
    }

    private static Icon shapeIcon(int shape)
    {
        if (shape == ValueDebugElement.SHAPE_CUBE)
        {
            return Icons.BLOCK;
        }
        else if (shape == ValueDebugElement.SHAPE_DIAMOND)
        {
            return Icons.DIAMOND;
        }
        else if (shape == ValueDebugElement.SHAPE_RING)
        {
            return Icons.CIRCLE;
        }
        else if (shape == ValueDebugElement.SHAPE_CROSS)
        {
            return Icons.ADD;
        }

        return Icons.SPHERE;
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public void setMouse(UIContext context)
    {
        this.xy(context.mouseX(), context.mouseY())
            .wh(this.column.area.w, this.column.area.h)
            .bounds(context.menu.overlay, 5);
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);

        if (this.config.xray.get())
        {
            UIDashboardPanels.renderHighlight(context.batcher, this.xray.area, Direction.BOTTOM);
        }

        if (this.config.dashed.get())
        {
            UIDashboardPanels.renderHighlight(context.batcher, this.dashed.area, Direction.BOTTOM);
        }
    }
}
