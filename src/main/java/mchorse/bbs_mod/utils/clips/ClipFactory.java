package mchorse.bbs_mod.utils.clips;

import mchorse.bbs_mod.camera.clips.ClipCategories;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.factory.MapFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The factory of clips, which answers an unknown clip type with a stand-in rather than with an
 * exception the caller has to decide what to do with. See {@link UnknownClip}.
 */
public class ClipFactory extends MapFactory<Clip, ClipFactoryData>
{
    /**
     * How a stand-in draws: nameless and grey, so it reads as "something is missing here" rather
     * than as any of the real clips.
     *
     * <p>It exists because the timeline asks the factory for a clip's icon and colour while
     * drawing, and every one of those places dereferences the answer.</p>
     */
    private static final ClipFactoryData UNKNOWN = new ClipFactoryData(Icons.NONE, Colors.GRAY);

    /** The columns of the palette of clip types, in the order they are shown. */
    private final List<Link> categories = new ArrayList<>();

    /**
     * Adds a column to the palette of clip types. Columns are shown in the order they were added,
     * and a clip type joins one with {@link ClipFactoryData#in(Link)}.
     */
    public ClipFactory category(Link id)
    {
        if (!this.categories.contains(id))
        {
            this.categories.add(id);
        }

        return this;
    }

    public List<Link> getCategories()
    {
        return Collections.unmodifiableList(this.categories);
    }

    /**
     * The column a clip type is listed in: the one its data names when this factory has such a
     * column, {@link ClipCategories#OTHER} otherwise — a clip type never falls out of the palette
     * for want of a column.
     */
    public Link getCategory(Link type)
    {
        ClipFactoryData data = this.getData(type);

        return data != null && this.categories.contains(data.category) ? data.category : ClipCategories.OTHER;
    }

    /**
     * The clip types by column, in the order of the columns and, inside one, of registration.
     * Columns with nothing in them are left out.
     */
    public Map<Link, List<Link>> getTypesByCategory()
    {
        Map<Link, List<Link>> columns = new LinkedHashMap<>();

        for (Link category : this.categories)
        {
            columns.put(category, new ArrayList<>());
        }

        columns.putIfAbsent(ClipCategories.OTHER, new ArrayList<>());

        for (Link type : this.getKeys())
        {
            columns.get(this.getCategory(type)).add(type);
        }

        columns.values().removeIf(List::isEmpty);

        return columns;
    }

    @Override
    public Clip createUnknown(Link type, MapType data)
    {
        return new UnknownClip(type);
    }

    @Override
    public ClipFactoryData getData(Clip object)
    {
        ClipFactoryData data = super.getData(object);

        return data == null ? UNKNOWN : data;
    }
}
