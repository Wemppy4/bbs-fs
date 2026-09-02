package mchorse.bbs_mod.ui.film.clips.actions;

import mchorse.bbs_mod.actions.types.item.ReleaseUseItemActionClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;

public class UIReleaseUseItemActionClip extends UIActionClip<ReleaseUseItemActionClip>
{
    public UIToggle hand;
    public UIToggle riptide;
    public UITrackpad charge;
    public UIItemStack itemStack;
    public UIItemStack projectile;

    public UIReleaseUseItemActionClip(ReleaseUseItemActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.hand = this.toggle(UIKeys.ACTIONS_ITEM_MAIN_HAND, this.clip.hand);
        this.charge = this.trackpad(this.clip.charge);
        this.charge.limit(this.clip.charge);
        this.itemStack = this.itemStack(this.clip.itemStack);
        this.projectile = this.itemStack(this.clip.projectile);
        this.riptide = this.toggle(UIKeys.ACTIONS_ITEM_RIPTIDE, this.clip.riptide);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.hand);
        this.panels.add(this.riptide);
        this.panels.add(this.section(UIKeys.ACTIONS_ITEM_CHARGE, this.charge));
        this.panels.add(this.section(UIKeys.ACTIONS_ITEM_STACK, this.itemStack));
        this.panels.add(this.section(UIKeys.ACTIONS_ITEM_PROJECTILE, this.projectile));
    }
}
