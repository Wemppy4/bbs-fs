package mchorse.bbs_mod.ui.framework;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.UIDrawContext;

import java.util.ArrayList;
import java.util.List;

public class UIRenderingContext
{
    public Batcher2D batcher;

    private List<Runnable> runnables = new ArrayList<>();

    public UIRenderingContext(UIDrawContext context)
    {
        this.batcher = new Batcher2D(context);
    }

    /* Rendering context implementations */

    public TextureManager getTextures()
    {
        return BBSModClient.getTextures();
    }

    public void postRunnable(Runnable runnable)
    {
        this.runnables.add(runnable);
    }

    public void executeRunnables()
    {
        for (Runnable runnable : this.runnables)
        {
            runnable.run();
        }

        this.runnables.clear();
    }
}
