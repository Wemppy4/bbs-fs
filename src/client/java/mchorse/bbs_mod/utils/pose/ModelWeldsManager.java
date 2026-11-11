package mchorse.bbs_mod.utils.pose;

import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.presets.DataManager;

/** The weld presets of a model: every weld of it at once, saved by name in {@code welds.json} in its folder. */
public class ModelWeldsManager extends DataManager
{
    public static final ModelWeldsManager INSTANCE = new ModelWeldsManager();

    @Override
    protected Link getFile(String group)
    {
        return Link.assets(ModelManager.MODELS_PREFIX + group + "/welds.json");
    }
}
