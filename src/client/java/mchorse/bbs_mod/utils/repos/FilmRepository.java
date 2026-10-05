package mchorse.bbs_mod.utils.repos;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.network.ClientNetwork;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class FilmRepository implements IRepository<Film>
{
    @Override
    public Film create(String id, MapType data)
    {
        Film film = new Film();

        film.setId(id);

        if (data != null)
        {
            film.fromData(data);
        }

        return film;
    }

    @Override
    public void load(String id, Consumer<Film> callback)
    {
        ClientNetwork.sendManagerDataLoad(id, (data) ->
        {
            if (data.isMap())
            {
                callback.accept(this.create(id, data.asMap()));
            }
            else
            {
                callback.accept(null);
            }
        });
    }

    @Override
    public void save(String id, MapType data)
    {
        MapType mapType = new MapType();

        mapType.putString("id", id);
        mapType.put("data", data);

        ClientNetwork.sendManagerData(-1, RepositoryOperation.SAVE, mapType);
    }

    @Override
    public void rename(String id, String name)
    {
        MapType mapType = new MapType();

        mapType.putString("from", id);
        mapType.putString("to", name);

        ClientNetwork.sendManagerData(-1, RepositoryOperation.RENAME, mapType);
    }

    @Override
    public void delete(String id)
    {
        MapType mapType = new MapType();

        mapType.putString("id", id);

        ClientNetwork.sendManagerData(-1, RepositoryOperation.DELETE, mapType);
    }

    @Override
    public void requestKeys(Consumer<Collection<String>> callback)
    {
        MapType mapType = new MapType();

        ClientNetwork.sendManagerData(RepositoryOperation.KEYS, mapType, (data) ->
        {
            if (!data.isList())
            {
                return;
            }

            List<String> list = new ArrayList<>();

            for (BaseType element : data.asList())
            {
                list.add(element.asString());
            }

            callback.accept(list);
        });
    }

    @Override
    public void requestModified(Consumer<Map<String, Long>> callback)
    {
        MapType mapType = new MapType();

        mapType.putBool("modified", true);

        ClientNetwork.sendManagerData(RepositoryOperation.KEYS, mapType, (data) ->
        {
            if (!data.isMap())
            {
                return;
            }

            MapType map = data.asMap();
            Map<String, Long> modified = new HashMap<>();

            for (String key : map.keys())
            {
                modified.put(key, map.getLong(key));
            }

            callback.accept(modified);
        });
    }

    @Override
    public File getFolder()
    {
        return null;
    }

    @Override
    public void requestBackups(String id, Consumer<Collection<String>> callback)
    {
        MapType request = new MapType();
        request.putString("id", id);

        ClientNetwork.sendManagerData(RepositoryOperation.BACKUPS, request, (data) ->
        {
            List<String> backups = new ArrayList<>();

            if (data.isList())
            {
                for (BaseType entry : data.asList())
                {
                    backups.add(entry.asString());
                }
            }

            callback.accept(backups);
        });
    }

    @Override
    public void addFolder(String path, Consumer<Boolean> callback)
    {
        MapType mapType = new MapType();

        mapType.putString("folder", path);

        ClientNetwork.sendManagerData(RepositoryOperation.ADD_FOLDER, mapType, (data) ->
        {
            if (data.isNumeric())
            {
                callback.accept(data.asNumeric().boolValue());
            }
        });
    }

    @Override
    public void renameFolder(String path, String name, Consumer<Boolean> callback)
    {
        MapType mapType = new MapType();

        mapType.putString("from", path);
        mapType.putString("to", name);

        ClientNetwork.sendManagerData(RepositoryOperation.RENAME_FOLDER, mapType, (data) ->
        {
            if (data.isNumeric())
            {
                callback.accept(data.asNumeric().boolValue());
            }
        });
    }

    @Override
    public void deleteFolder(String path, Consumer<Boolean> callback)
    {
        MapType mapType = new MapType();

        mapType.putString("folder", path);

        ClientNetwork.sendManagerData(RepositoryOperation.DELETE_FOLDER, mapType, (data) ->
        {
            if (data.isNumeric())
            {
                callback.accept(data.asNumeric().boolValue());
            }
        });
    }
}