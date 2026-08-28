package mchorse.bbs_mod.ui.film.clips.widgets;

import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

import java.util.function.Consumer;

/**
 * The shared editor of an overlay clip's {@link Placement}: a 3x3 preset grid
 * (sets the window point and the anchor together) plus fine tuning trackpads.
 * Every change hands a copy of the edited placement to the callback.
 */
public class UIPlacement
{
    public UIAnchorGrid grid;
    public UITrackpad windowX;
    public UITrackpad windowY;
    public UITrackpad offsetX;
    public UITrackpad offsetY;
    public UITrackpad anchorX;
    public UITrackpad anchorY;
    public UITrackpad scaleX;
    public UITrackpad scaleY;

    private Placement placement = new Placement();
    private Placement defaultPlacement;
    private Consumer<Placement> callback;

    public UIPlacement(Placement defaultPlacement, Consumer<Placement> callback)
    {
        this.defaultPlacement = defaultPlacement;
        this.callback = callback;

        this.grid = new UIAnchorGrid((x, y) ->
        {
            this.placement.windowX = x;
            this.placement.windowY = y;
            this.placement.anchorX = x;
            this.placement.anchorY = y;

            this.fillFields();
            this.emit();
        });

        this.windowX = new UITrackpad((v) ->
        {
            this.placement.windowX = v.floatValue();
            this.updateGrid();
            this.emit();
        });
        this.windowY = new UITrackpad((v) ->
        {
            this.placement.windowY = v.floatValue();
            this.updateGrid();
            this.emit();
        });
        this.offsetX = new UITrackpad((v) ->
        {
            this.placement.offsetX = v.floatValue();
            this.emit();
        });
        this.offsetY = new UITrackpad((v) ->
        {
            this.placement.offsetY = v.floatValue();
            this.emit();
        });
        this.anchorX = new UITrackpad((v) ->
        {
            this.placement.anchorX = v.floatValue();
            this.updateGrid();
            this.emit();
        });
        this.anchorY = new UITrackpad((v) ->
        {
            this.placement.anchorY = v.floatValue();
            this.updateGrid();
            this.emit();
        });
        this.scaleX = new UITrackpad((v) ->
        {
            this.placement.scaleX = v.floatValue();

            if (isChained())
            {
                this.placement.scaleY = this.placement.scaleX;
                this.scaleY.setValue(this.placement.scaleY);
            }

            this.emit();
        });
        this.scaleX.limit(0);
        this.scaleY = new UITrackpad((v) ->
        {
            this.placement.scaleY = v.floatValue();

            if (isChained())
            {
                this.placement.scaleX = this.placement.scaleY;
                this.scaleX.setValue(this.placement.scaleX);
            }

            this.emit();
        });
        this.scaleY.limit(0);
        this.scaleX.tooltip(UIKeys.CAMERA_PANELS_PLACEMENT_SCALE_SHIFT, Direction.BOTTOM);
        this.scaleY.tooltip(UIKeys.CAMERA_PANELS_PLACEMENT_SCALE_SHIFT, Direction.BOTTOM);

        this.grid.context((menu) -> menu.action(Icons.REFRESH, UIKeys.GENERAL_RESET, this::reset));
    }

    /**
     * Both scale axes move together, unless shift is held — the modifier
     * every scale editor shares: these trackpads AND the preview gizmo's
     * handles. Read live, so shift can be pressed and released mid-drag.
     */
    public static boolean isChained()
    {
        return !Window.isShiftPressed();
    }

    private void reset()
    {
        this.placement.set(this.defaultPlacement);
        this.fillFields();
        this.emit();
    }

    /**
     * The fields a clip panel's placement section is made of.
     */
    public UIElement[] fields()
    {
        return new UIElement[] {
            this.grid,
            UI.label(UIKeys.CAMERA_PANELS_PLACEMENT_POSITION), UI.row(this.windowX, this.windowY),
            UI.label(UIKeys.CAMERA_PANELS_PLACEMENT_OFFSET), UI.row(this.offsetX, this.offsetY),
            UI.label(UIKeys.CAMERA_PANELS_PLACEMENT_ANCHOR), UI.row(this.anchorX, this.anchorY),
            UI.label(UIKeys.CAMERA_PANELS_PLACEMENT_SCALE), UI.row(this.scaleX, this.scaleY)
        };
    }

    public void setPlacement(Placement placement)
    {
        this.placement.set(placement);
        this.fillFields();
    }

    private void fillFields()
    {
        this.windowX.setValue(this.placement.windowX);
        this.windowY.setValue(this.placement.windowY);
        this.offsetX.setValue(this.placement.offsetX);
        this.offsetY.setValue(this.placement.offsetY);
        this.anchorX.setValue(this.placement.anchorX);
        this.anchorY.setValue(this.placement.anchorY);
        this.scaleX.setValue(this.placement.scaleX);
        this.scaleY.setValue(this.placement.scaleY);
        this.updateGrid();
    }

    /**
     * The grid highlights a preset only while the window and the anchor sit
     * exactly on the same 3x3 point.
     */
    private void updateGrid()
    {
        Placement p = this.placement;
        boolean onGrid = p.windowX == p.anchorX && p.windowY == p.anchorY
            && p.windowX * 2F == (int) (p.windowX * 2F)
            && p.windowY * 2F == (int) (p.windowY * 2F)
            && p.windowX >= 0F && p.windowX <= 1F
            && p.windowY >= 0F && p.windowY <= 1F;

        if (onGrid)
        {
            this.grid.setValue(p.windowX, p.windowY);
        }
        else
        {
            this.grid.setValue(-1F, -1F);
        }
    }

    private void emit()
    {
        if (this.callback != null)
        {
            this.callback.accept(this.placement.copy());
        }
    }
}
