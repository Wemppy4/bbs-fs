package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class UIKeyframeSheet extends UIKeyframeElement
{
    /* Meta data */
    public final String id;
    private Icon icon;

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

    public UIKeyframeSheet(int color, boolean separator, KeyframeChannel channel, BaseValueBasic property)
    {
        this(channel.getId(), IKey.constant(property != null ? FormUtils.getForm(property).getTrackName(channel.getId()) : channel.getId()), color, separator, channel, property, false);
    }

    public UIKeyframeSheet(String id, IKey title, int color, boolean separator, KeyframeChannel channel, BaseValueBasic property)
    {
        this(id, title, color, separator, channel, property, false);
    }

    public UIKeyframeSheet(String id, IKey title, int color, boolean separator, KeyframeChannel channel, BaseValueBasic property, boolean isBoneTrack)
    {
        super(title, color);

        this.id = id;
        this.separator = separator;

        this.channel = channel;
        this.selection = new KeyframeSelection(channel);
        this.property = property;
        this.isBoneTrack = isBoneTrack;
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
        Number valueNumber = value instanceof Number ? (Number) value : 0D;

        for (Keyframe keyframe : this.selection.getSelected())
        {
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
