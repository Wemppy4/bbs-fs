package mchorse.bbs_mod.ui.film.clips.actions;

import mchorse.bbs_mod.actions.types.chat.ChatActionClip;
import mchorse.bbs_mod.l10n.keys.IKey;
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

        this.message = new UITextbox(1000, (t) -> this.clip.message.set(t));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.ACTIONS_CHAT_MESSAGE, this.message));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.message.setText(this.clip.message.get());
    }
}