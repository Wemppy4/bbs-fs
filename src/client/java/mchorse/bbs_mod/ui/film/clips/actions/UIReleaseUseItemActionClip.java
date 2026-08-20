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

        this.hand = new UIToggle(UIKeys.ACTIONS_ITEM_MAIN_HAND, (b) -> this.editor.editMultiple(this.clip.hand, (hand) -> hand.set(b.getValue())));
        this.charge = new UITrackpad((v) -> this.editor.editMultiple(this.clip.charge, (charge) -> charge.set(v.intValue())));
        this.charge.limit(this.clip.charge);
        this.itemStack = new UIItemStack((stack) -> this.editor.editMultiple(this.clip.itemStack, (itemStack) -> itemStack.set(stack)));
        this.projectile = new UIItemStack((stack) -> this.editor.editMultiple(this.clip.projectile, (projectile) -> projectile.set(stack)));
        this.riptide = new UIToggle(UIKeys.ACTIONS_ITEM_RIPTIDE, (b) -> this.editor.editMultiple(this.clip.riptide, (riptide) -> riptide.set(b.getValue())));
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

    @Override
    public void fillData()
    {
        super.fillData();

        this.hand.setValue(this.clip.hand.get());
        this.riptide.setValue(this.clip.riptide.get());
        this.charge.setValue(this.clip.charge.get());
        this.itemStack.setStack(this.clip.itemStack.get());
        this.projectile.setStack(this.clip.projectile.get());
    }
}
