package mchorse.bbs_mod.cubic.model.config;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.repos.IRepository;

import java.io.File;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * Repository backing the Model Editor onto {@link mchorse.bbs_mod.cubic.model.ModelManager}. Models are
 * assets (a {@code config.json} next to geometry and textures), so only listing, loading and saving the
 * config are supported. Creating, renaming and deleting models is not yet a thing (that arrives with
 * in-editor geometry), so those stay no-ops and their buttons are disabled in the editor.
 */
public class ModelManagerRepository implements IRepository<ModelConfig>
{
    @Override
    public ModelConfig create(String id, MapType data)
    {
        return null;
    }

    @Override
    public void load(String id, Consumer<ModelConfig> callback)
    {
        if (callback != null)
        {
            ModelInstance instance = BBSModClient.getModels().getModel(id);

            callback.accept(instance == null ? null : instance.config);
        }
    }

    @Override
    public void save(String id, MapType data)
    {
        BBSModClient.getModels().saveConfig(id, data);
    }

    @Override
    public void rename(String id, String name)
    {}

    @Override
    public void delete(String id)
    {}

    @Override
    public void requestKeys(Consumer<Collection<String>> callback)
    {
        if (callback != null)
        {
            callback.accept(BBSModClient.getModels().getAvailableKeys());
        }
    }

    @Override
    public File getFolder()
    {
        return new File(BBSMod.getAssetsFolder(), "models");
    }

    @Override
    public void addFolder(String path, Consumer<Boolean> callback)
    {
        if (callback != null)
        {
            callback.accept(false);
        }
    }

    @Override
    public void renameFolder(String path, String name, Consumer<Boolean> callback)
    {
        if (callback != null)
        {
            callback.accept(false);
        }
    }

    @Override
    public void deleteFolder(String path, Consumer<Boolean> callback)
    {
        if (callback != null)
        {
            callback.accept(false);
        }
    }
}
