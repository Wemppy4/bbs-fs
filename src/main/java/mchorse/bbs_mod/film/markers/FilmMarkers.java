package mchorse.bbs_mod.film.markers;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueStableList;
import mchorse.bbs_mod.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The film's markers, in no particular order — {@link #getSorted()} is what drawing and navigation
 * ask for.
 *
 * <p>Stable ids rather than positions: undo addresses a marker's own fields by path
 * ({@code markers/<id>/title}), and a positional id would point that path at a neighbour the
 * moment another marker is inserted before it.
 */
public class FilmMarkers extends ValueStableList<FilmMarker>
{
    public FilmMarkers(String id)
    {
        super(id);
    }

    /**
     * @param numbered The name with a {@code %s} for the number, like "Marker %s". The number is one
     * past the count, moved on while another marker has that name already: after a delete the count
     * lags behind the numbers handed out.
     */
    public FilmMarker addMarker(int tick, IKey numbered)
    {
        FilmMarker marker = new FilmMarker("");
        int number = this.list.size() + 1;

        while (this.hasTitle(numbered.format(number).get()))
        {
            number += 1;
        }

        marker.tick.set(Math.max(0, tick));
        marker.title.set(numbered.format(number).get());
        marker.color.set(FilmMarker.randomBrightColor());

        this.preNotify();
        this.add(marker);
        this.postNotify();

        return marker;
    }

    public void remove(FilmMarker marker)
    {
        int index = CollectionUtils.getIndex(this.list, marker);

        if (CollectionUtils.inRange(this.list, index))
        {
            this.preNotify();
            this.list.remove(index);
            this.postNotify();
        }
    }

    private boolean hasTitle(String title)
    {
        for (FilmMarker marker : this.list)
        {
            if (marker.title.get().equals(title))
            {
                return true;
            }
        }

        return false;
    }

    public FilmMarker getById(String id)
    {
        return (FilmMarker) this.get(id);
    }

    /**
     * @return The marker sitting exactly on the given tick, or {@code null}.
     */
    public FilmMarker getAt(int tick)
    {
        for (FilmMarker marker : this.list)
        {
            if (marker.tick.get() == tick)
            {
                return marker;
            }
        }

        return null;
    }

    public List<FilmMarker> getSorted()
    {
        List<FilmMarker> sorted = new ArrayList<>(this.list);

        sorted.sort(Comparator.comparingInt((marker) -> marker.tick.get()));

        return sorted;
    }

    /**
     * @return The nearest marker's tick after the given one, or the given one when there is none —
     * the same "stay put at the edge" contract as {@link mchorse.bbs_mod.utils.clips.Clips#findNextTick(int)}.
     */
    public int findNextTick(int tick)
    {
        int output = Integer.MAX_VALUE;

        for (FilmMarker marker : this.list)
        {
            int markerTick = marker.tick.get();

            if (markerTick > tick)
            {
                output = Math.min(output, markerTick);
            }
        }

        return output == Integer.MAX_VALUE ? tick : output;
    }

    public int findPreviousTick(int tick)
    {
        int output = Integer.MIN_VALUE;

        for (FilmMarker marker : this.list)
        {
            int markerTick = marker.tick.get();

            if (markerTick < tick)
            {
                output = Math.max(output, markerTick);
            }
        }

        return output == Integer.MIN_VALUE ? tick : output;
    }

    @Override
    protected FilmMarker create(String id)
    {
        return new FilmMarker(id);
    }
}
