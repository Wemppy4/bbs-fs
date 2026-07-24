package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class KeyframeSelection
{
    private static final List<Integer> tmpIndices = new ArrayList<>();

    private KeyframeChannel channel;
    private Set<Integer> selected = new LinkedHashSet<>();

    private List<Keyframe> tmp = new ArrayList<>();

    public KeyframeSelection(KeyframeChannel channel)
    {
        this.channel = channel;
    }

    public boolean hasAny()
    {
        return !this.selected.isEmpty();
    }

    public boolean has(int index)
    {
        return this.selected.contains(index);
    }

    public boolean has(Keyframe keyframe)
    {
        return this.selected.contains(this.channel.getKeyframes().indexOf(keyframe));
    }

    public void all()
    {
        this.selected.clear();

        for (int i = 0, c = this.channel.getKeyframes().size(); i < c; i++)
        {
            this.selected.add(i);
        }
    }

    public void after(float tick, int direction)
    {
        this.selected.clear();

        List keyframes = this.channel.getKeyframes();

        if (direction < 0)
        {
            for (int i = 0; i < keyframes.size(); i++)
            {
                Keyframe keyframe = (Keyframe) keyframes.get(i);

                if (keyframe.getTick() > tick) return;

                this.selected.add(i);
            }
        }
        else
        {
            for (int i = keyframes.size() - 1; i >= 0; i--)
            {
                Keyframe keyframe = (Keyframe) keyframes.get(i);

                if (keyframe.getTick() < tick) return;

                this.selected.add(i);
            }
        }
    }

    public void clear()
    {
        this.selected.clear();
    }

    public void add(int i)
    {
        this.selected.add(i);
    }

    public void add(Keyframe keyframe)
    {
        int index = this.channel.getKeyframes().indexOf(keyframe);

        if (index >= 0)
        {
            this.add(index);
        }
    }

    public void addAll(Collection<Integer> selected)
    {
        this.selected.addAll(selected);
    }

    public void remove(int index)
    {
        this.selected.remove(index);
    }

    public void removeSelected()
    {
        tmpIndices.clear();
        tmpIndices.addAll(this.selected);
        tmpIndices.sort(Comparator.reverseOrder());

        for (Integer index : tmpIndices)
        {
            this.channel.remove(index);
        }

        this.selected.clear();
    }

    /**
     * The selection stores indices, so a channel edited underneath it (a removed keyframe, an
     * undone insertion) can leave indices that no longer resolve. Skip those, like
     * {@link #getSelected()} does, instead of reporting "nothing selected" on the first stale one.
     */
    public Keyframe getFirst()
    {
        for (Integer integer : this.selected)
        {
            Keyframe keyframe = this.channel.get(integer);

            if (keyframe != null)
            {
                return keyframe;
            }
        }

        return null;
    }

    public List<Keyframe> getSelected()
    {
        this.tmp.clear();

        for (Integer index : this.selected)
        {
            Keyframe keyframe = this.channel.get(index);

            if (keyframe != null)
            {
                this.tmp.add(keyframe);
            }
        }

        return this.tmp;
    }

    public Collection<Integer> getIndices()
    {
        return Collections.unmodifiableSet(this.selected);
    }
}