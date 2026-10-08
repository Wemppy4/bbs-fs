package mchorse.bbs_mod.api.events;

import mchorse.bbs_mod.utils.clips.ClipFactory;

/**
 * Posted on both sides once BBS has registered its own camera clips — the clips of the film's
 * camera track and of the overlay tracks next to it.
 */
public class RegisterCameraClipsEvent extends BaseRegisterClipsEvent
{
    public RegisterCameraClipsEvent(ClipFactory factory)
    {
        super(factory);
    }
}
