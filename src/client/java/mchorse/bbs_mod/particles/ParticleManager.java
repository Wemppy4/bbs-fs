package mchorse.bbs_mod.particles;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.manager.BaseManager;
import mchorse.bbs_mod.utils.manager.storage.JSONLikeStorage;

import java.io.File;
import java.util.function.Supplier;

public class ParticleManager extends BaseManager<ParticleScheme>
{
    public ParticleManager(Supplier<File> folder)
    {
        super(folder);

        this.storage = new JSONLikeStorage().json();
    }

    @Override
    protected ParticleScheme createData(String id, MapType data)
    {
        ParticleScheme scheme = new ParticleScheme();

        if (data != null)
        {
            try
            {
                System.out.println("Parsing \"" + id + "\" particle effect.");

                ParticleScheme.PARSER.fromData(scheme, data);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            scheme.setup();
        }
        else
        {
            scheme.setup();
        }

        return scheme;
    }
}
