package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.graphics.window.InputCodes;

public class InterpolationUtils
{
    public static void setupKeybind(IInterp interp, ContextAction action, IKey category)
    {
        String key = interp.getKey();

        if (key.endsWith("_in"))
        {
            action.key(category, InputCodes.fromNative(interp.getKeyCode()), InputCodes.KEY_LEFT_SHIFT);
        }
        else if (key.endsWith("_out"))
        {
            action.key(category, InputCodes.fromNative(interp.getKeyCode()), InputCodes.KEY_LEFT_CONTROL);
        }
        else
        {
            action.key(category, InputCodes.fromNative(interp.getKeyCode()));
        }
    }

    public static IKey getName(IInterp interp)
    {
        return UIKeys.C_INTERPOLATION.get(interp.getKey());
    }
}