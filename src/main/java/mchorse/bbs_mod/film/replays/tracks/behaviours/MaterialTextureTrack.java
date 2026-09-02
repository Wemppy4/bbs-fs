package mchorse.bbs_mod.film.replays.tracks.behaviours;

import mchorse.bbs_mod.film.replays.tracks.TrackBehaviour;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/** The texture override of one material: a link, which has no in-between states, so it never blends. */
public class MaterialTextureTrack implements TrackBehaviour
{
    @Override
    public IKeyframeFactory factory(TrackId track)
    {
        return KeyframeFactories.LINK;
    }

    @Override
    public void apply(TrackContext context, TrackId track, KeyframeChannel channel, float tick, float blend)
    {
        if (!(FormUtils.getForm(context.root(), track.formPath()) instanceof ModelForm modelForm))
        {
            return;
        }

        KeyframeSegment segment = channel.find(tick);

        if (segment != null)
        {
            modelForm.materialTextureOverrides.put(track.subject(), (Link) segment.createInterpolated());
        }
        else if (blend >= 1F)
        {
            modelForm.materialTextureOverrides.remove(track.subject());
        }
    }

    /*
     * No reset: the override map is shared with whoever else drives this material, and a release
     * happens after every render of the form (see BoneTrack for the full reasoning). The track takes
     * its own override back when it runs out of keyframes, which is what off-the-end means here.
     */
}
