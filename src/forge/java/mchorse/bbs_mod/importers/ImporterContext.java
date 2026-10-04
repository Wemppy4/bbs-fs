package mchorse.bbs_mod.importers;

import mchorse.bbs_mod.importers.types.IImporter;

import java.io.File;
import java.util.List;

public class ImporterContext
{
    public List<File> files;
    private File destination;

    public ImporterContext(List<File> files, File destination)
    {
        this.files = files;
        this.destination = destination;
    }

    public File getDestination(IImporter importer)
    {
        File folder = this.destination == null ? importer.getDefaultFolder() : this.destination;
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalStateException("Could not create import directory: " + folder);
        return folder;
    }
}