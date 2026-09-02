package mchorse.bbs_mod.ui.film.clips.actions;

import mchorse.bbs_mod.actions.types.item.ItemDropActionClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;import mchorse.bbs_mod.ui.utils.UI;

public class UIItemDropActionClip extends UIActionClip<ItemDropActionClip>
{
    public UITrackpad posX;
    public UITrackpad posY;
    public UITrackpad posZ;
    public UITrackpad velocityX;
    public UITrackpad velocityY;
    public UITrackpad velocityZ;
    public UIToggle relative;
    public UIItemStack itemStack;

    public UIItemDropActionClip(ItemDropActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.posX = this.trackpad(this.clip.posX);
        this.posY = this.trackpad(this.clip.posY);
        this.posZ = this.trackpad(this.clip.posZ);
        this.velocityX = this.trackpad(this.clip.velocityX);
        this.velocityY = this.trackpad(this.clip.velocityY);
        this.velocityZ = this.trackpad(this.clip.velocityZ);
        this.relative = this.toggle(UIKeys.CAMERA_PANELS_RELATIVE, this.clip.relative);
        this.itemStack = this.itemStack(this.clip.itemStack);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.ACTIONS_ITEM_POSITION, UI.row(this.posX, this.posY, this.posZ), UI.row(this.relative)));
        this.panels.add(this.section(UIKeys.ACTIONS_ITEM_VELOCITY, UI.row(this.velocityX, this.velocityY, this.velocityZ)));
        this.panels.add(this.section(UIKeys.ACTIONS_ITEM_STACK, this.itemStack));
    }
}