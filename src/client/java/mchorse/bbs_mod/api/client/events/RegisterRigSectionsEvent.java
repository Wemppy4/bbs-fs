package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.ui.forms.editors.panels.UIRigFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.rig.UIRigSection;

import java.util.function.Function;

/** Adds sections to the rig tab of the model form editor, after BBS's own steps and before the
 * limits. The factory runs once per panel; a section extends {@link UIRigSection} through its
 * addon constructor, fills itself for the selected bone in {@code fill} and keeps its data on the
 * form. Return null to add nothing to a panel. */
public class RegisterRigSectionsEvent
{
    public void register(Function<UIRigFormPanel, UIRigSection> factory)
    {
        UIRigFormPanel.registerSectionExtension(factory);
    }
}
