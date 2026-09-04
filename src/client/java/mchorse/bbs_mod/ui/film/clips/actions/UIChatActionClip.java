package mchorse.bbs_mod.ui.film.clips.actions;

import mchorse.bbs_mod.actions.types.chat.ChatActionClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
public class UIChatActionClip extends UIActionClip<ChatActionClip>
{
    public UITextbox message;

    public UIChatActionClip(ChatActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.message = this.textbox(1000, this.clip.message);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.ACTIONS_CHAT_MESSAGE, this.message));
    }
}