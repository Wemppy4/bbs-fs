package mchorse.bbs_mod.ui.utils.keys;

import mchorse.bbs_mod.graphics.window.InputCodes;

public enum KeyAction
{
    PRESSED, RELEASED, REPEAT;

    public static KeyAction get(int action)
    {
        if (action == InputCodes.PRESS)
        {
            return PRESSED;
        }
        else if (action == InputCodes.REPEAT)
        {
            return REPEAT;
        }

        return RELEASED;
    }
}