package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.importers.Importers;
import mchorse.bbs_mod.importers.types.IImporter;

/**
 * Posted on the client once BBS has registered its own file importers — what happens to a file
 * dragged into an assets folder.
 */
public class RegisterImportersEvent
{
    public void register(IImporter importer)
    {
        Importers.register(importer);
    }
}
