package mchorse.bbs_mod.utils.clips;

import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.factory.MapFactory;

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
