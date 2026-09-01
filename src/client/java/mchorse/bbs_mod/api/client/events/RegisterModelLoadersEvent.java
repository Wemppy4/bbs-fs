package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.cubic.model.loaders.IModelLoader;

import java.util.function.Supplier;

/**
 * Posted on the client before the model manager is built, so an addon can teach BBS to read a
 * model format of its own.
 */
public class RegisterModelLoadersEvent
{
    /**
     * @param loader makes the loader. It is asked again on every asset reload, which rebuilds the
     *               list of loaders from scratch.
     */
    public void register(Supplier<IModelLoader> loader)
    {
        ModelManager.registerLoader(loader);
    }
}
