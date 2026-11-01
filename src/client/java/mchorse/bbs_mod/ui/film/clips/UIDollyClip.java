package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.overwrite.DollyClip;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.modules.UIAngleModule;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.film.utils.UICameraUtils;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIDollyClip extends UIClip<DollyClip>
{
    public UIPointModule point;
    public UIAngleModule angle;

    public UITrackpad distance;
    public UIIcon reverse;
    public UIIcon interp;
    public UIButton fromLook;

    public UITrackpad yaw;
    public UITrackpad pitch;

    public UIDollyClip(DollyClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.point = this.bind(new UIPointModule(editor), () -> this.point.fill(this.clip.position.getPoint()));
        this.angle = this.bind(new UIAngleModule(editor), () -> this.angle.fill(this.clip.position.getAngle()));
        this.distance = this.trackpad(this.clip.distance);
        this.distance.tooltip(UIKeys.CAMERA_PANELS_DOLLY_DISTANCE);
        this.reverse = new UIIcon(Icons.REVERSE, (b) -> this.reverse());
        this.reverse.tooltip(UIKeys.CAMERA_PANELS_DOLLY_REVERSE);
        this.yaw = this.trackpad(this.clip.yaw);
        this.yaw.tooltip(UIKeys.CAMERA_PANELS_DOLLY_YAW);
        this.pitch = this.trackpad(this.clip.pitch);
        this.pitch.tooltip(UIKeys.CAMERA_PANELS_DOLLY_PITCH);

        this.interp = interpIcon(() -> this.clip.interp);
        this.fromLook = new UIButton(UIKeys.CAMERA_PANELS_DOLLY_FROM_LOOK, (b) -> this.fromLook());
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.positionTools(this.clip.position));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_DOLLY_TITLE,
            labelRow(UIKeys.CAMERA_PANELS_DOLLY_LENGTH, this.interp, this.distance),
            labelRow(UIKeys.CAMERA_PANELS_YAW, this.yaw),
            labelRow(UIKeys.CAMERA_PANELS_PITCH, this.pitch),
            UI.row(UIConstants.MARGIN, 0, 0, this.fromLook, this.reverse.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT))));
        this.panels.add(this.point, this.angle);
        this.panels.context((menu) -> UICameraUtils.positionContextMenu(menu, this.editor, this.clip.position));
    }

    /** Lays the rail along where the clip's camera looks. */
    private void fromLook()
    {
        Angle angle = this.clip.position.getAngle().get();

        this.clip.yaw.set(angle.yaw);
        this.clip.pitch.set(angle.pitch);

        this.fillData();
    }

    private void reverse()
    {
        Position position = new Position();

        this.clip.applyLast(new CameraClipContext(), position);
        this.clip.position.set(position);
        this.clip.distance.set(-this.clip.distance.get());

        this.fillData();
    }

    @Override
    public void editClip(Position position)
    {
        this.clip.position.set(position);
        this.clip.yaw.set(position.angle.yaw);
        this.clip.pitch.set(position.angle.pitch);

        super.editClip(position);
    }
}