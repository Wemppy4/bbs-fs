package mchorse.bbs_mod.api.events;

import mchorse.bbs_mod.forge.ModelTileEntity;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** Per-tick model-block callback on the logical side that owns the tile. */
public interface ModelBlockEntityUpdateCallback {
    Event EVENT = new Event();
    void update(ModelTileEntity entity);

    final class Event {
        private final CopyOnWriteArrayList<ModelBlockEntityUpdateCallback> listeners = new CopyOnWriteArrayList<>();
        private final ModelBlockEntityUpdateCallback invoker = entity -> {
            for (ModelBlockEntityUpdateCallback listener : this.listeners) listener.update(entity);
        };
        public void register(ModelBlockEntityUpdateCallback listener) { this.listeners.add(Objects.requireNonNull(listener)); }
        public ModelBlockEntityUpdateCallback invoker() { return this.invoker; }
    }
}