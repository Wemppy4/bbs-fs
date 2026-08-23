package mchorse.bbs_mod.film.replays.tracks;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.function.Supplier;

/**
 * One track a form offers: its address and channel, whose it is, what it looks like in a timeline,
 * and where it sits in the tree of tracks.
 *
 * <p>The list of these is what a form <em>has</em> — which is a question about the form, not about a
 * timeline. It used to be answered by the timeline builder itself, in three separate copies (the
 * replay editor, the animation state editor, and the per-form track filter, which built a throwaway
 * {@code FormProperties} just to ask), each knowing a slightly different subset.</p>
 *
 * @param id       address of the track
 * @param channel  its keyframes, taken from the record's tracks (made on the spot if absent)
 * @param owner    form the track belongs to
 * @param title    what a timeline calls it
 * @param icon     icon a timeline draws next to it
 * @param color    colour a timeline draws it in, before the user's own override
 * @param property the value behind the track: a real form property, or a stand-in carrying the type
 *                 and starting value for the kinds that have no property of their own (bones,
 *                 materials). Null for the solver tracks, whose value is a container the editor
 *                 builds by hand.
 * @param seed     value a brand-new keyframe starts at, when it must not simply be the empty value.
 *                 Null means the empty value (or the property's) is right.
 * @param parent   track this one folds under — a bone under the bone it hangs off, a material's
 *                 properties under that material's texture track. Null for a track that stands on
 *                 its own. How far in a track is drawn follows from this chain, so there is nothing
 *                 to keep in step with it.
 */
public record TrackDescriptor(
    TrackId id,
    KeyframeChannel channel,
    Form owner,
    IKey title,
    Icon icon,
    int color,
    BaseValueBasic property,
    Supplier<Object> seed,
    TrackId parent
)
{
    public TrackDescriptor(TrackId id, KeyframeChannel channel, Form owner, IKey title, Icon icon, int color, BaseValueBasic property)
    {
        this(id, channel, owner, title, icon, color, property, null, null);
    }

    public TrackDescriptor seed(Supplier<Object> seed)
    {
        return new TrackDescriptor(this.id, this.channel, this.owner, this.title, this.icon, this.color, this.property, seed, this.parent);
    }

    /** Fold this track under another one. */
    public TrackDescriptor under(TrackId parent)
    {
        return new TrackDescriptor(this.id, this.channel, this.owner, this.title, this.icon, this.color, this.property, this.seed, parent);
    }

    /**
     * What this kind of track is called in the track filters and in the user's colour/name overrides.
     * Bone tracks go by their {@code path/bone} title, everything else by the last segment of its
     * address, so the same kind of track shares one key across forms and films.
     */
    public String filterKey()
    {
        return this.kind() == TrackKind.BONE ? this.title.get() : StringUtils.fileName(this.key());
    }

    public TrackKind kind()
    {
        return this.id.kind();
    }

    public String key()
    {
        return this.id.toKey();
    }
}
