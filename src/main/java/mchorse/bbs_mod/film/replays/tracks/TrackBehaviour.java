package mchorse.bbs_mod.film.replays.tracks;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

/**
 * What one kind of track keys, and what keying it does to a form.
 *
 * <p>This is where a kind of track stops being a shape of string and becomes behaviour. Before it,
 * "what does this track do" was answered by two separate dispatchers walking the same map of
 * channels: {@code FormProperties.applyProperty} knew properties, bones and materials, while
 * {@code BaseFilmController.applyTargetOverrides} — 200 lines in client code — knew the IK, pole,
 * physics and wind tracks. Neither knew the other's kinds, and adding a kind meant teaching both.</p>
 */
public interface TrackBehaviour
{
    /**
     * The factory of this track's value, or null when the value's shape comes from the form rather
     * than from the kind of track (a plain property keys whatever its own value keys).
     */
    public default IKeyframeFactory factory(TrackId track)
    {
        return null;
    }

    /**
     * Lay this track's value at the given tick over the form.
     *
     * @param blend how present the track is, 0..1 — an animation state easing in or out applies its
     *              tracks partially; a film always applies them whole
     */
    public void apply(TrackContext context, TrackId track, KeyframeChannel channel, float tick, float blend);

    /**
     * Undo what {@link #apply} wrote, when the thing driving the track lets go (an animation state
     * releasing). A kind whose writes are already dropped by {@link #clear} every frame needs
     * nothing here.
     */
    public default void reset(Form root, TrackId track)
    {}

    /**
     * Drop the per-frame overrides this kind leaves on a model form, before the frame's tracks are
     * applied. Only the kinds that write into transient override maps and never remove from them
     * (the solver tracks) implement this — a track that was deleted, or whose keyframes ran out,
     * must not keep driving the form.
     */
    public default void clear(ModelForm form)
    {}
}
