package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.settings.values.ui.ValueMotionPath;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

public class UIMotionPathContextMenu extends UIContextMenu
{
    public UIIcon enable;
    public UIIcon gradient;
    public UIIcon around;

    public UIColor color;
    public UITrackpad width;

    public UIColor pastColor;
    public UIColor futureColor;

    public UIIcon frames;
    public UITrackpad frameSize;

    public UIIcon keyframes;
    public UIColor keyframeColor;
    public UITrackpad keyframeSize;

    public UIIcon current;
    public UIColor currentColor;
    public UITrackpad currentSize;

    public UITrackpad before;
    public UITrackpad after;

    private UIElement column;

    private UIFilmPanel panel;
    private ValueMotionPath motionPath;

    public UIMotionPathContextMenu(UIFilmPanel panel, ValueMotionPath motionPath)
    {
        this.panel = panel;
        this.motionPath = motionPath;

        this.enable = new UIIcon(() -> this.motionPath.enabled.get() ? Icons.VISIBLE : Icons.INVISIBLE, (b) -> this.motionPath.enabled.toggle());
        this.enable.tooltip(UIKeys.FILM_CONTROLLER_MOTION_PATH_TITLE);
        this.gradient = new UIIcon(Icons.GRAPH, (b) -> this.motionPath.gradient.toggle());
        this.gradient.tooltip(UIKeys.FILM_CONTROLLER_MOTION_PATH_GRADIENT);
        this.around = new UIIcon(Icons.MAXIMIZE, (b) -> this.motionPath.aroundCurrent.toggle());
        this.around.tooltip(UIKeys.FILM_CONTROLLER_MOTION_PATH_AROUND);

        this.color = new UIColor((c) -> this.motionPath.color.set(c));
        this.color.setColor(this.motionPath.color.get());
        this.width = new UITrackpad((v) -> this.motionPath.width.set(v.floatValue()));
        this.width.limit(0.005D, 0.5D, false).setValue(this.motionPath.width.get());

        this.pastColor = new UIColor((c) -> this.motionPath.pastColor.set(c));
        this.pastColor.setColor(this.motionPath.pastColor.get());
        this.futureColor = new UIColor((c) -> this.motionPath.futureColor.set(c));
        this.futureColor.setColor(this.motionPath.futureColor.get());

        this.frames = new UIIcon(() -> this.motionPath.frames.get() ? Icons.VISIBLE : Icons.INVISIBLE, (b) -> this.motionPath.frames.toggle());
        this.frames.tooltip(UIKeys.FILM_CONTROLLER_MOTION_PATH_FRAMES);
        this.frameSize = new UITrackpad((v) -> this.motionPath.frameSize.set(v.floatValue()));
        this.frameSize.limit(0.005D, 0.5D, false).setValue(this.motionPath.frameSize.get());

        this.keyframes = new UIIcon(() -> this.motionPath.keyframes.get() ? Icons.VISIBLE : Icons.INVISIBLE, (b) -> this.motionPath.keyframes.toggle());
        this.keyframes.tooltip(UIKeys.FILM_CONTROLLER_MOTION_PATH_KEYFRAMES);
        this.keyframeColor = new UIColor((c) -> this.motionPath.keyframeColor.set(c));
        this.keyframeColor.setColor(this.motionPath.keyframeColor.get());
        this.keyframeSize = new UITrackpad((v) -> this.motionPath.keyframeSize.set(v.floatValue()));
        this.keyframeSize.limit(0.005D, 0.5D, false).setValue(this.motionPath.keyframeSize.get());

        this.current = new UIIcon(() -> this.motionPath.current.get() ? Icons.VISIBLE : Icons.INVISIBLE, (b) -> this.motionPath.current.toggle());
        this.current.tooltip(UIKeys.FILM_CONTROLLER_MOTION_PATH_CURRENT);
        this.currentColor = new UIColor((c) -> this.motionPath.currentColor.set(c));
        this.currentColor.setColor(this.motionPath.currentColor.get());
        this.currentSize = new UITrackpad((v) -> this.motionPath.currentSize.set(v.floatValue()));
        this.currentSize.limit(0.005D, 0.5D, false).setValue(this.motionPath.currentSize.get());

        this.before = new UITrackpad((v) -> this.motionPath.before.set(v.intValue()));
        this.before.limit(1, 1000, true).setValue(this.motionPath.before.get());
        this.after = new UITrackpad((v) -> this.motionPath.after.set(v.intValue()));
        this.after.limit(1, 1000, true).setValue(this.motionPath.after.get());

        this.column = UI.column(4, 8,
            UI.row(this.enable, this.gradient, this.around),
            UI.label(UIKeys.FILM_CONTROLLER_MOTION_PATH_LINE), UI.row(this.color, this.width),
            UI.label(UIKeys.FILM_CONTROLLER_MOTION_PATH_GRADIENT), UI.row(this.pastColor, this.futureColor),
            UI.label(UIKeys.FILM_CONTROLLER_MOTION_PATH_FRAMES), UI.row(this.frames, this.frameSize),
            UI.label(UIKeys.FILM_CONTROLLER_MOTION_PATH_KEYFRAMES), UI.row(this.keyframes, this.keyframeColor, this.keyframeSize),
            UI.label(UIKeys.FILM_CONTROLLER_MOTION_PATH_CURRENT), UI.row(this.current, this.currentColor, this.currentSize),
            UI.label(UIKeys.FILM_CONTROLLER_MOTION_PATH_RANGE), UI.row(this.before, this.after)
        );
        this.column.relative(this).w(190);

        this.add(this.column);
        this.column.resize();
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

        if (this.motionPath.gradient.get())
        {
            UIDashboardPanels.renderHighlight(context.batcher, this.gradient.area, Direction.BOTTOM);
        }

        if (this.motionPath.aroundCurrent.get())
        {
            UIDashboardPanels.renderHighlight(context.batcher, this.around.area, Direction.BOTTOM);
        }
    }
}
