package mchorse.bbs_mod.ui.film.clips.actions;

import mchorse.bbs_mod.actions.types.item.UseItemActionClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
public class UIUseItemActionClip extends UIActionClip<UseItemActionClip>
{
    public UIToggle hand;
    public UIItemStack itemStack;

    public UIUseItemActionClip(UseItemActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.hand = new UIToggle(UIKeys.ACTIONS_ITEM_MAIN_HAND, (b) -> this.clip.hand.set(b.getValue()));
        this.itemStack = new UIItemStack((stack) -> this.clip.itemStack.set(stack));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.hand);
        this.panels.add(this.section(UIKeys.ACTIONS_ITEM_STACK, this.itemStack));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.hand.setValue(this.clip.hand.get());
        this.itemStack.setStack(this.clip.itemStack.get());
    }
}