package mchorse.bbs_mod.importers.types;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.importers.ImporterContext;
import mchorse.bbs_mod.l10n.keys.IKey;

import java.io.File;

public interface IImporter
{
    public IKey getName();

    public default File getDefaultFolder()
    {
        return BBSMod.getAssetsFolder();
    }

    public boolean canImport(ImporterContext context);

    public void importFiles(ImporterContext context);
}