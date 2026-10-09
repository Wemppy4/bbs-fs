package mchorse.bbs_mod.network;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ByteType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmManager;
import mchorse.bbs_mod.utils.repos.RepositoryOperation;
import java.io.File;
import java.io.IOException;

/** File operations run on the server thread after its editor permission check. */
public final class FilmRepositoryOperations
{
    public static BaseType apply(FilmManager films, RepositoryOperation operation, MapType request)
    {
        if (films == null) throw new IllegalStateException("No film repository is open");

        switch (operation)
        {
            case LOAD:
                String id = path(films, request.getString("id"), false);
                Film film = films.exists(id) ? films.load(id) : null;
                return film == null ? new ByteType(false) : film.toData();
            case SAVE:
                String saveId = path(films, request.getString("id"), false);
                if (!request.has("data") || !request.get("data").isMap())
                    throw new IllegalArgumentException("Film data must be a map");
                return new ByteType(films.save(saveId, request.getMap("data")));
            case RENAME:
                return new ByteType(films.rename(path(films, request.getString("from"), false), path(films, request.getString("to"), false)));
            case DELETE:
                return new ByteType(films.delete(path(films, request.getString("id"), false)));
            case KEYS:
                /* With dates when asked for them; a plain list for whoever asks without */
                if (request.getBool("modified"))
                {
                    MapType modified = new MapType();
                    films.getModified().forEach(modified::putLong);
                    return modified;
                }
                return DataStorageUtils.stringListToData(films.getKeys());
            case BACKUPS:
                return DataStorageUtils.stringListToData(films.getBackupKeys(path(films, request.getString("id"), false)));
            case ADD_FOLDER:
                return new ByteType(films.addFolder(path(films, request.getString("folder"), true)));
            case RENAME_FOLDER:
                return new ByteType(films.renameFolder(path(films, request.getString("from"), true), path(films, request.getString("to"), true)));
            case DELETE_FOLDER:
                return new ByteType(films.deleteFolder(path(films, request.getString("folder"), true)));
            default:
                throw new IllegalArgumentException("Unknown repository operation");
        }
    }

    private static String path(FilmManager films, String id, boolean folder)
    {
        if (id == null || id.isEmpty() || id.length() > 1024 || id.startsWith("/") || id.indexOf('\\') >= 0 || id.indexOf(':') >= 0 || id.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Invalid film repository path");
        for (String part : id.split("/", -1))
        {
            if (part.equals(".") || part.equals("..")) throw new IllegalArgumentException("Relative film repository path");
        }
        if (!folder && id.endsWith("/")) throw new IllegalArgumentException("Expected a film, not a folder");
        try
        {
            File root = films.getFolder().getCanonicalFile();
            File target = (folder ? films.getFolder(id) : films.getFile(id)).getCanonicalFile();
            if (target.equals(root) || !target.toPath().startsWith(root.toPath()))
                throw new IllegalArgumentException("Film path leaves its repository");
        }
        catch (IOException error)
        {
            throw new IllegalArgumentException("Cannot resolve film repository path", error);
        }
        return id;
    }

    private FilmRepositoryOperations() {}
}
