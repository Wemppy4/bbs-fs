package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

public class CurveClientClip extends CurveClip
{
    public CurveClientClip()
    {}

    @Override
    protected void breakDownClip(Clip original, int offset)
    {
        super.breakDownClip(original, offset);

        for (KeyframeChannel<?> channel : this.channels.getAllKeyframeChannels())
        {
            breakDownTrimAfterSplit(channel, offset);
        }

        CurveClip curveClip = (CurveClip) original;

        for (KeyframeChannel<?> channel : curveClip.channels.getAllKeyframeChannels())
        {
            breakDownTrimOriginalTail(channel, offset);
        }
    }

    /** Shift keyframes after splitting: drop everything before tick 0 on the new clip. */
    private static void breakDownTrimAfterSplit(KeyframeChannel<?> channel, int offset)
    {
        channel.moveX(-offset);

        var segment = channel.find(0);

        if (segment != null)
        {
            while (segment.a != channel.get(0))
            {
                channel.remove(0);
            }
        }
    }

    /** On the source clip, drop keyframes after the split point. */
    private static void breakDownTrimOriginalTail(KeyframeChannel<?> channel, int offset)
    {
        var segment = channel.find(offset);

        if (segment == null)
        {
            return;
        }

        while (segment.b != channel.get(channel.getKeyframes().size() - 1))
        {
            channel.remove(channel.getKeyframes().size() - 1);
        }
    }

    @Override
    protected Clip create()
    {
        return new CurveClientClip();
    }
}