package mchorse.bbs_mod.ui.film.clips.actions;

import mchorse.bbs_mod.actions.types.DamageActionClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UIConstants;

public class UIDamageActionClip extends UIActionClip<DamageActionClip>
{
    public UITrackpad damage;

    public UIDamageActionClip(DamageActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.damage = new UITrackpad((v) -> this.editor.editMultiple(this.clip.damage, (damage) -> damage.set(v.floatValue())));
        this.damage.limit(0F);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.ACTIONS_ATTACK_DAMAGE, this.damage).marginTop(UIConstants.SECTION_GAP));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.damage.setValue(this.clip.damage.get());
    }
}