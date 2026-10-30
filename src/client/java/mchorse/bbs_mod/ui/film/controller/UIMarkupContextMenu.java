package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.values.ui.ValueMarkup;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.utils.UI;

/**
 * The film preview's markup button: which groups of the editors' markup show — the motion path,
 * the rig's IK goals, poles and controllers (the rig's debug, F5), splines, shape key handles —
 * how solid it draws and how big its handles are, and the motion path's own settings one click on.
 */
public class UIMarkupContextMenu extends UIContextMenu
{
    public UIToggle motionPath;
    public UIToggle ik;
    public UIToggle splines;
    public UIToggle shapeKeys;

    public UISliderTrackpad opacity;
    public UISliderTrackpad size;

    public UIButton pathSettings;

    private UIElement column;

    public UIMarkupContextMenu(UIFilmPanel panel)
    {
        ValueMarkup markup = BBSSettings.editorMarkup;
        var path = panel.getController().getMotionPath();

        this.motionPath = new UIToggle(UIKeys.FILM_CONTROLLER_MOTION_PATH_TITLE, path.enabled.get(), (b) -> path.enabled.set(b.getValue()));
        this.ik = new UIToggle(UIKeys.MARKUP_IK, BBSSettings.debugOverlay.get(), (b) -> BBSSettings.debugOverlay.set(b.getValue()));
        this.splines = new UIToggle(UIKeys.MARKUP_SPLINES, markup.splines.get(), (b) -> markup.splines.set(b.getValue()));
        this.shapeKeys = new UIToggle(UIKeys.MARKUP_SHAPE_KEYS, markup.shapeKeys.get(), (b) -> markup.shapeKeys.set(b.getValue()));

        this.opacity = new UISliderTrackpad((v) -> BBSSettings.markupOpacity.set(v.floatValue()));
        this.opacity.limit(BBSSettings.markupOpacity).setValue(BBSSettings.markupOpacity.get());
        this.size = new UISliderTrackpad((v) -> BBSSettings.markupHandleSize.set(v.floatValue()));
        this.size.limit(BBSSettings.markupHandleSize).setValue(BBSSettings.markupHandleSize.get());

        this.pathSettings = new UIButton(UIKeys.MARKUP_PATH_SETTINGS, (b) ->
            this.getContext().replaceContextMenu(new UIMotionPathContextMenu(panel, path)));

        this.column = UI.column(4, 8,
            UI.label(UIKeys.MARKUP_TITLE),
            this.motionPath, this.ik, this.splines, this.shapeKeys,
            UI.label(UIKeys.MARKUP_OPACITY), this.opacity,
            UI.label(UIKeys.MARKUP_SIZE), this.size,
            this.pathSettings
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
}
