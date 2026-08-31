package mchorse.bbs_mod.api.events;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public interface ModelBlockEntityUpdateCallback
{
    public static Event<ModelBlockEntityUpdateCallback> EVENT = EventFactory.createArrayBacked(ModelBlockEntityUpdateCallback.class, (listeners) ->
    {
        return (entity) ->
        {
            for (ModelBlockEntityUpdateCallback listener : listeners)
            {
                listener.update(entity);
            }
        };
    });

    public void update(ModelBlockEntity entity);
}