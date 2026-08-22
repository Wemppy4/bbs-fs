package mchorse.bbs_mod.film.replays.tracks.behaviours;

import mchorse.bbs_mod.film.replays.tracks.TrackBehaviour;
import mchorse.bbs_mod.film.replays.tracks.TrackBlend;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;

/**
 * A plain form property: the track drives the property's runtime value, which is what the form
 * reports while something is animating it. Off the end of the track the runtime value is dropped
 * and the property shows its own again.
 */
public class PropertyTrack implements TrackBehaviour
{
    @Override
    public void apply(TrackContext context, TrackId track, KeyframeChannel channel, float tick, float blend)
    {
        BaseValueBasic property = FormUtils.getProperty(context.root(), track.toKey());

        if (property == null)
        {
            return;
        }

        KeyframeSegment segment = channel.find(tick);

        if (segment != null)
        {
            property.setRuntimeValue(TrackBlend.value(channel, property.get(), segment, blend));
        }
        else if (blend >= 1F)
        {
            property.setRuntimeValue(null);
        }
    }

    @Override
    public void reset(Form root, TrackId track)
    {
        BaseValueBasic property = FormUtils.getProperty(root, track.toKey());

        /* A track whose path no longer resolves on this form (a removed body part, or a state authored
         * against another form) is skipped, not thrown on. */
        if (property != null)
        {
            property.setRuntimeValue(null);
        }
    }
}
