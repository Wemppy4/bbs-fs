package mchorse.bbs_mod.ui.film.clips.actions;

import mchorse.bbs_mod.actions.types.AttackActionClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
public class UIAttackActionClip extends UIActionClip<AttackActionClip>
{
    public UITrackpad damage;

    public UIAttackActionClip(AttackActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.damage = this.trackpad(this.clip.damage).limit(0F);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.ACTIONS_ATTACK_DAMAGE, this.damage));
    }
}