package mchorse.bbs_mod.settings.values;

import mchorse.bbs_mod.settings.values.base.BaseValue;

public interface IValueListener
{
    public static final int FLAG_DEFAULT = 0b0;
    public static final int FLAG_UNMERGEABLE = 0b1;

    /**
     * This edit is one step of an ongoing recording: it, and everything that follows until the
     * edits stop coming, belong to a single entry in the undo history.
     *
     * <p>Auto-keyframing over a playing film raises this. Without it every tick of a recording
     * would seal an entry of its own, so a couple of seconds of recording would bury the whole
     * history and leave the user pressing Ctrl+Z a hundred times to take the take back.</p>
     */
    public static final int FLAG_BATCH = 0b10;

    public void accept(BaseValue value, int flag);
}