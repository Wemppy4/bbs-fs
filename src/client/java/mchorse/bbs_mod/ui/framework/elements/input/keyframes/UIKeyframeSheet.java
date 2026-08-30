package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.settings.values.ui.ValueTrackStyles;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class UIKeyframeSheet
{
    /** What the name column draws, and the colour of the row's keyframes and its left edge. */
    public IKey title;
    public int color;

    /**
     * A row that names something rather than animating it — a body part, whose form's tracks fold
     * under it. It carries a channel because a row is drawn from one, but nothing may ever be
     * written into it: that channel belongs to no replay and would be dropped on save.
     */
    public boolean header;

    /* Meta data */
    public final String id;
    private Icon icon;

    /**
     * The title and colour the track was built with. {@link #title} and {@link #color} carry what is
     * actually drawn, which is these unless the user overrode them — see {@link #applyStyle()}.
     */
    public final IKey defaultTitle;
    public final int defaultColor;

    /**
     * Single home of the track identity rule: what a track is called in the global filters and in the
     * user's colour/name overrides. Bone tracks go by their {@code path/bone} title, everything else by
     * the last segment of its channel id, so the same kind of track shares one key across forms and films.
     *
     * Derived from the defaults, never from the overridden title — otherwise renaming a bone track would
     * move its own key out from under it.
     */
    private final String filterKey;

    public final KeyframeChannel channel;
    public final KeyframeSelection selection;
    public final BaseValueBasic property;
    public final boolean isBoneTrack;

    /** Set for sheets that have no backing form property (e.g. the IK controls track), so the editor can find the owning form. */
    public Form form;

    /**
     * Initial value for a brand-new keyframe on a property-less track (IK / physics controls). Stands in for
     * the missing {@code property.get()} seed the pose track uses, so a fresh keyframe holds a fully populated
     * container instead of an empty one — without it an empty keyframe displays the form's config values yet
     * interpolates toward the hardcoded defaults, so two "identical" keyframes silently drift apart.
     */
    public Supplier<Object> seed;

    /** The track this row draws, when it came from the catalog; null for the replay's own curated channels. */
    public final TrackDescriptor descriptor;

    /**
     * The row this one folds under, and the rows that fold under it. A bone hangs off its parent bone,
     * a material's properties off that material — so folding an arm folds the whole arm, and folding
     * a material takes its sliders with it.
     */
    public UIKeyframeSheet parent;
    public final List<UIKeyframeSheet> children = new ArrayList<>();

    public UIKeyframeSheet(TrackDescriptor track)
    {
        this(track.key(), track.title(), track.color(), track.channel(), track.property(), track.kind() == TrackKind.BONE, track);

        this.icon(track.icon());
        this.header = track.kind() == TrackKind.BODY_PART;
        this.form(track.owner());

        if (track.seed() != null)
        {
            this.seed(track.seed());
        }
    }

    public UIKeyframeSheet(int color, KeyframeChannel channel, BaseValueBasic property)
    {
        this(channel.getId(), IKey.constant(property != null ? FormUtils.getForm(property).getTrackName(channel.getId()) : channel.getId()), color, channel, property, false);
    }

    public UIKeyframeSheet(String id, IKey title, int color, KeyframeChannel channel, BaseValueBasic property)
    {
        this(id, title, color, channel, property, false);
    }

    public UIKeyframeSheet(String id, IKey title, int color, KeyframeChannel channel, BaseValueBasic property, boolean isBoneTrack)
    {
        this(id, title, color, channel, property, isBoneTrack, null);
    }

    public UIKeyframeSheet(String id, IKey title, int color, KeyframeChannel channel, BaseValueBasic property, boolean isBoneTrack, TrackDescriptor descriptor)
    {
        this.title = title;
        this.color = color;
        this.descriptor = descriptor;

        this.id = id;

        this.channel = channel;
        this.selection = new KeyframeSelection(channel);
        this.property = property;
        this.isBoneTrack = isBoneTrack;

        this.defaultTitle = title;
        this.defaultColor = color;
        this.filterKey = descriptor != null ? descriptor.filterKey() : (isBoneTrack ? title.get() : StringUtils.fileName(id));

        this.applyStyle();
    }

    /**
     * The model form whose pose this track drives, or {@code null} when it isn't a pose track at
     * all. Whose pose it is comes from the track, not from whoever owns the timeline: a body part
     * carries its own model, animations and bones, and it answers for {@code "<path>/pose"} the
     * same way the root answers for {@code "pose"}. Overlays are not it — they layer over a pose
     * rather than being one.
     */
    public ModelForm getPoseForm()
    {
        boolean isPose = this.channel.getFactory() == KeyframeFactories.POSE
            && (this.id.equals("pose") || this.id.endsWith(FormUtils.PATH_SEPARATOR + "pose"))
            && !this.id.contains("pose_overlay");

        if (!isPose || this.property == null)
        {
            return null;
        }

        return FormUtils.getForm(this.property) instanceof ModelForm modelForm ? modelForm : null;
    }

    /** The key this track is identified by in the global filters and in the user's name/colour overrides. */
    public String getFilterKey()
    {
        return this.filterKey;
    }

    /**
     * Pull the user's global name and colour for this kind of track over the built-in ones. Called when the
     * sheet is built and again whenever the overrides change, so an edit lands without rebuilding the timeline.
     */
    public void applyStyle()
    {
        ValueTrackStyles styles = BBSSettings.trackStyles;
        String name = styles == null ? "" : styles.name(this.filterKey, "");

        this.title = name.isEmpty() ? this.defaultTitle : IKey.constant(name);
        this.color = styles == null ? this.defaultColor : styles.color(this.filterKey, this.defaultColor);
    }

    /** Hang this row under another one. */
    public void setParent(UIKeyframeSheet parent)
    {
        this.parent = parent;

        if (parent != null)
        {
            parent.children.add(this);
        }
    }

    /** How many rows up the tree this one sits, which is what the name column indents by. */
    public int getDepth()
    {
        int depth = 0;

        for (UIKeyframeSheet sheet = this.parent; sheet != null; sheet = sheet.parent)
        {
            depth += 1;
        }

        return depth;
    }

    /**
     * The colour this row is drawn in. A header takes the interface's primary colour and takes it
     * <em>now</em>, not when the timeline was built: the colour is a live setting, and a value
     * copied into the row at build time would sit there stale until something rebuilt the tracks.
     */
    public int getRowColor()
    {
        return this.header ? BBSSettings.primaryColor.get() : this.color;
    }

    public UIKeyframeSheet icon(Icon icon)
    {
        this.icon = icon;

        return this;
    }

    public UIKeyframeSheet form(Form form)
    {
        this.form = form;

        return this;
    }

    public UIKeyframeSheet seed(Supplier<Object> seed)
    {
        this.seed = seed;

        return this;
    }

    public Icon getIcon()
    {
        return this.icon;
    }

    /**
     * The keyframe of this track at the given tick, created if the track has none there.
     *
     * <p>A created keyframe starts from what the track already reads at that tick &mdash; the
     * interpolated value between its neighbours, or, on an empty track, the property's current
     * value (or the track's {@link #seed}) &mdash; so bringing a keyframe into being never moves
     * anything by itself. When it lands between two keyframes it also inherits the left one's
     * interpolation, the way a hand-placed keyframe does.
     */
    public <T> Keyframe<T> ensureKeyframe(float tick)
    {
        for (Keyframe<T> keyframe : (List<Keyframe<T>>) this.channel.getKeyframes())
        {
            if (keyframe.getTick() == tick)
            {
                return keyframe;
            }
        }

        KeyframeSegment<T> segment = this.channel.find(tick);
        Keyframe<T> template = null;
        T value;

        if (segment != null)
        {
            value = segment.createInterpolated();
            template = segment.a;
        }
        else if (this.property != null)
        {
            value = (T) this.channel.getFactory().copy(this.property.get());
        }
        else if (this.seed != null)
        {
            value = (T) this.seed.get();
        }
        else
        {
            value = (T) this.channel.getFactory().createEmpty();
        }

        /* Bringing a keyframe into being changes the track itself, not a value inside it: seal the
         * channel's before-state so undo takes the keyframe away again instead of only putting back
         * whatever the edit wrote into it. Only on a real insertion — sealing on every edit would
         * make each drag frame its own undo entry. */
        this.channel.preNotify(IValueListener.FLAG_UNMERGEABLE);

        int index = this.channel.insert(tick, value);
        Keyframe<T> keyframe = (Keyframe<T>) this.channel.get(index);

        /* The selection is stored by index, so it has to be walked past the new keyframe. */
        this.selection.shiftAfterInsert(index);

        if (template != null && template != keyframe)
        {
            keyframe.copyOverExtra(template);
        }

        return keyframe;
    }

    public List<Integer> sort()
    {
        List<Keyframe> selected = this.selection.getSelected();
        List<Integer> lastSelection = new ArrayList<>(this.selection.getIndices());

        this.channel.sort();
        this.selection.clear();

        List keyframes = this.channel.getKeyframes();

        for (Keyframe keyframe : selected)
        {
            this.selection.add(keyframes.indexOf(keyframe));
        }

        return lastSelection;
    }

    public void setTickBy(float diff, boolean dirty)
    {
        for (Keyframe keyframe : this.selection.getSelected())
        {
            keyframe.setTick(keyframe.getTick() + diff, dirty);
        }
    }

    public void setDuration(float duration)
    {
        for (Keyframe keyframe : this.selection.getSelected())
        {
            keyframe.setDuration(duration);
        }
    }

    public void setValue(Object value, Object selectedValue, boolean dirty)
    {
        for (Keyframe keyframe : this.selection.getSelected())
        {
            this.setValueOn(keyframe, value, selectedValue, dirty);
        }
    }

    /**
     * Put a value edit on one keyframe of this track. A number moves by the same delta the edited
     * keyframe moved by, so several selected keyframes keep their spread; anything else is copied
     * over wholesale.
     */
    public void setValueOn(Keyframe keyframe, Object value, Object selectedValue, boolean dirty)
    {
        Number valueNumber = value instanceof Number ? (Number) value : 0D;

        if (selectedValue instanceof Double)
        {
            keyframe.setValue((double) keyframe.getValue() + valueNumber.doubleValue() - (double) selectedValue, dirty);
        }
        else if (selectedValue instanceof Float)
        {
            keyframe.setValue((float) keyframe.getValue() + valueNumber.floatValue() - (float) selectedValue, dirty);
        }
        else if (selectedValue instanceof Integer)
        {
            keyframe.setValue((int) keyframe.getValue() + valueNumber.intValue() - (int) selectedValue, dirty);
        }
        else if (selectedValue instanceof Long)
        {
            keyframe.setValue((long) keyframe.getValue() + valueNumber.longValue() - (long) selectedValue, dirty);
        }
        else
        {
            keyframe.setValue(this.channel.getFactory().copy(value), dirty);
        }
    }

    public void setInterpolation(Interpolation interpolation)
    {
        List<Keyframe> selected = this.selection.getSelected();

        if (selected.isEmpty())
        {
            return;
        }

        /* The keyframe's interpolation isn't wired into the value tree, so copying it
         * directly never reaches the undo handler. Notify through the channel (whose
         * data captures each keyframe's interpolation) so the change is recorded, and
         * mark it unmergeable — a picked interpolation is a discrete edit. */
        this.channel.preNotify(IValueListener.FLAG_UNMERGEABLE);

        for (Keyframe keyframe : selected)
        {
            keyframe.getInterpolation().copy(interpolation);
        }

        this.channel.postNotify(IValueListener.FLAG_UNMERGEABLE);
    }

    public void remove(Keyframe keyframe)
    {
        int index = this.channel.getKeyframes().indexOf(keyframe);

        if (index >= 0)
        {
            this.selection.remove(index);
            this.channel.remove(index);
        }
    }
}
