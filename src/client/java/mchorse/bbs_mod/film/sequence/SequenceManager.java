package mchorse.bbs_mod.film.sequence;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.manager.BaseManager;

import java.io.File;
import java.util.function.Supplier;

public class SequenceManager extends BaseManager<Sequence>
{
    public SequenceManager(Supplier<File> folder)
    {
        super(folder);

        this.backUps = true;
    }

    /**
     * A new sequence starts with the export settings of the settings' "Video" category; a loaded,
     * duplicated or pasted one keeps the ones it has.
     */
    @Override
    protected Sequence createData(String id, MapType mapType)
    {
        Sequence sequence = new Sequence();

        if (mapType != null)
        {
            sequence.fromData(mapType);
        }
        else
        {
            sequence.export.copyGlobal();
        }

        return sequence;
    }
}
