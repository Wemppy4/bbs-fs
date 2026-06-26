package mchorse.bbs_mod.ui.film.clips.modules;

import mchorse.bbs_mod.camera.values.ValueAngle;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.utils.UICameraUtils;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;

public class UIAngleModule extends UISection
{
    public UITrackpad yaw;
    public UITrackpad pitch;
    public UITrackpad roll;
    public UITrackpad fov;

    public ValueAngle angle;

    protected IUIClipsDelegate editor;

    public UIAngleModule(IUIClipsDelegate editor)
    {
        super(UIKeys.CAMERA_PANELS_ANGLE);

        this.editor = editor;

        this.yaw = new UITrackpad((v) -> BaseValue.edit(this.angle, (value) -> value.get().yaw = v.floatValue()));
        this.yaw.tooltip(UIKeys.CAMERA_PANELS_YAW);

        this.pitch = new UITrackpad((v) -> BaseValue.edit(this.angle, (value) -> value.get().pitch = v.floatValue()));
        this.pitch.tooltip(UIKeys.CAMERA_PANELS_PITCH);

        this.roll = new UITrackpad((v) -> BaseValue.edit(this.angle, (value) -> value.get().roll = v.floatValue()));
        this.roll.tooltip(UIKeys.CAMERA_PANELS_ROLL);

        this.fov = new UITrackpad((v) -> BaseValue.edit(this.angle, (value) -> value.get().fov = v.floatValue()));
        this.fov.tooltip(UIKeys.CAMERA_PANELS_FOV);

        this.fields.add(this.yaw, this.pitch, this.roll, this.fov);
    }

    public UIAngleModule contextMenu()
    {
        this.context((menu) -> UICameraUtils.angleContextMenu(menu, this.editor, this.angle));

        return this;
    }

    public void fill(ValueAngle angle)
    {
        this.angle = angle;

        this.yaw.setValue(angle.get().yaw);
        this.pitch.setValue(angle.get().pitch);
        this.roll.setValue(angle.get().roll);
        this.fov.setValue(angle.get().fov);
    }
}
