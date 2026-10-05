package mchorse.bbs_mod.utils.repos;

import mchorse.bbs_mod.data.IDataSerializable;
import mchorse.bbs_mod.data.types.MapType;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface IRepository<T extends IDataSerializable>
{
    public default T create(String id)
    {
        return this.create(id, null);
    }

    public T create(String id, MapType data);

    public void load(String id, Consumer<T> callback);

    public void save(String id, MapType data);

    public void rename(String id, String name);

    public void delete(String id);

    public void requestKeys(Consumer<Collection<String>> callback);

    /** The keys with when each was last changed, in milliseconds; 0 when that is unknown. */
    public void requestModified(Consumer<Map<String, Long>> callback);

    public default void requestBackups(String id, Consumer<Collection<String>> callback)
    {
        callback.accept(List.of());
    }

    /* Folders */

    public File getFolder();

    public void addFolder(String path, Consumer<Boolean> callback);

    public void renameFolder(String path, String name, Consumer<Boolean> callback);

    public void deleteFolder(String path, Consumer<Boolean> callback);
}