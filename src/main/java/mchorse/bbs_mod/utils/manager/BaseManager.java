package mchorse.bbs_mod.utils.manager;

import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.migration.IDataMigration;
import mchorse.bbs_mod.data.migration.SaveVersion;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.manager.storage.IDataStorage;
import mchorse.bbs_mod.utils.manager.storage.JSONLikeStorage;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

/**
 * Base JSON manager which loads and saves different data
 * structures based upon Data API
 *
 * <p>This is the boundary where a document becomes a file, so it is also where the
 * {@link SaveVersion} is stamped on the way out and the migration ladder is walked on the way in.
 */
public abstract class BaseManager <T extends ValueGroup> extends FolderManager<T>
{
    private static final Logger LOGGER = LogUtils.getLogger();

    protected IDataStorage storage = new JSONLikeStorage();
    protected boolean backUps;

    public BaseManager(Supplier<File> folder)
    {
        super(folder);
    }

    @Override
    public final T create(String id, MapType data)
    {
        T object = this.createData(id, data);

        object.setId(id);

        return object;
    }

    protected abstract T createData(String id, MapType mapType);

    /**
     * The migration ladder for this kind of document, in ascending order (it gets sorted anyway).
     * Empty means the format of this kind has never changed.
     */
    protected List<IDataMigration> getMigrations()
    {
        return Collections.emptyList();
    }

    @Override
    public T load(String id)
    {
        try
        {
            MapType mapType = this.storage.load(this.getFile(id));

            if (!this.upgrade(id, mapType))
            {
                return null;
            }

            return this.create(id, mapType);
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to load \"" + id + "\" from " + this.getFile(id) + "!", e);
        }

        return null;
    }

    /**
     * Walk a just-read document up to {@link SaveVersion#CURRENT}, in place.
     *
     * <p>A document from a <em>newer</em> build is refused rather than read: this build has no idea
     * what its unknown keys mean, and {@code fromData} would quietly drop every one of them — so
     * opening it and saving it back would silently strip the file down to what this build happens
     * to understand. Refusing keeps the file on disk intact.
     *
     * @return whether the document may be read at all
     */
    private boolean upgrade(String id, MapType data)
    {
        int version = SaveVersion.read(data);

        if (version > SaveVersion.CURRENT)
        {
            LOGGER.error(
                "Refusing to load \"" + id + "\": it was saved by a newer version of BBS (format " +
                version + ", this build understands " + SaveVersion.CURRENT + "). Opening it here " +
                "would drop everything this build doesn't know about. Update the mod to open it."
            );

            return false;
        }

        List<IDataMigration> migrations = this.getMigrations().stream()
            .filter((migration) -> migration.getVersion() >= version)
            .sorted(Comparator.comparingInt(IDataMigration::getVersion))
            .toList();

        if (!migrations.isEmpty())
        {
            LOGGER.info("Converting \"" + id + "\" from format " + version + " to " + SaveVersion.CURRENT + "...");

            for (IDataMigration migration : migrations)
            {
                migration.migrate(data);
            }
        }

        return true;
    }

    @Override
    public boolean save(String id, MapType data)
    {
        File file = this.getFile(id);

        SaveVersion.stamp(data);

        try
        {
            if (this.backUps)
            {
                String path = file.getParentFile().getAbsolutePath();
                Date date = new Date();
                String backupFileName = new SimpleDateFormat("yyyy_MM_dd_HH").format(date);

                if (BBSSettings.editorMinutesBackup.get())
                {
                    String minutes = new SimpleDateFormat("mm").format(date);
                    int m = (int) Math.floor(Integer.parseInt(minutes) / 10F);

                    backupFileName += "_" + m + "0";
                }

                String filename = StringUtils.fileName(id);
                File backupFile = new File(path, "_" + filename + "/" + filename + "." + backupFileName + this.getExtension());

                backupFile.getParentFile().mkdirs();

                if (file.exists())
                {
                    Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to back up \"" + id + "\" before saving!", e);
        }

        try
        {
            this.storage.save(file, data);

            return true;
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to save \"" + id + "\" to " + file + "!", e);
        }

        return false;
    }
}