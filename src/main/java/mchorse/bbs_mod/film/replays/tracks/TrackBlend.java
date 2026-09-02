package mchorse.bbs_mod.film.replays.tracks;

import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

/** How a partially applied track mixes into what the form already shows. */
public class TrackBlend
{
    /**
     * The track's value at this segment, eased from {@code current} by {@code blend}. At full blend
     * the segment's own value is handed over untouched — blending starts from what the form actually
     * shows, so a state easing out glides back to it instead of jumping.
     */
    public static Object value(KeyframeChannel channel, Object current, KeyframeSegment segment, float blend)
    {
        if (blend >= 1F)
        {
            return segment.createInterpolated();
        }

        IKeyframeFactory factory = channel.getFactory();
        Object from = factory.copy(current);
        Object to = factory.copy(segment.createInterpolated());

        return factory.copy(factory.interpolate(from, from, to, to, Interpolations.LINEAR, blend));
    }
}
