package mchorse.bbs_mod.ui.film.clips.actions;

import mchorse.bbs_mod.actions.types.chat.CommandActionClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
public class UICommandActionClip extends UIActionClip<CommandActionClip>
{
    public UITextarea command;

    public UICommandActionClip(CommandActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.command = this.bind(new UITextarea<>((t) -> this.clip.command.set(t)), () -> this.command.setText(this.clip.command.get()));
        this.command.background().wrap().padding(6).h(90);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.ACTIONS_COMMAND_COMMAND, this.command));
    }
}