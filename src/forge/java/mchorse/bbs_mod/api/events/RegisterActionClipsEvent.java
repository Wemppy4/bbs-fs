package mchorse.bbs_mod.api.events;

import mchorse.bbs_mod.utils.clips.ClipFactory;

/**
 * Posted on both sides once BBS has registered its own action clips — the clips of a replay's
 * action track.
 */
public class RegisterActionClipsEvent extends BaseRegisterClipsEvent
{
    public RegisterActionClipsEvent(ClipFactory factory)
    {
        super(factory);
    }
}
