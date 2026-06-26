package mchorse.bbs_mod.ui.film.clips.actions;

import mchorse.bbs_mod.actions.types.chat.CommandActionClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
public class UICommandActionClip extends UIActionClip<CommandActionClip>
{
    public UITextbox command;

    public UICommandActionClip(CommandActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.command = new UITextbox(10000, (t) -> this.clip.command.set(t));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.ACTIONS_COMMAND_COMMAND, this.command));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.command.setText(this.clip.command.get());
    }
}