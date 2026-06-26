package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.modules.UIAngleModule;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.film.utils.UICameraUtils;
/**
 * Idle clip panel
 *
 * This panel is responsible for editing an idle clip. This panel uses basic
 * point and angle modules for manipulating idle clip's position.
 */
public class UIIdleClip extends UIClip<IdleClip>
{
    public UIPointModule point;
    public UIAngleModule angle;

    public UIIdleClip(IdleClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.point = new UIPointModule(this.editor);
        this.angle = new UIAngleModule(this.editor);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.point, this.angle);
        this.panels.context((menu) -> UICameraUtils.positionContextMenu(menu, this.editor, this.clip.position));
    }

    @Override
    public void editClip(Position position)
    {
        this.clip.position.set(position);

        super.editClip(position);
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.point.fill(clip.position.getPoint());
        this.angle.fill(clip.position.getAngle());
    }
}