package mchorse.bbs_mod.api.client.events;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Ordered client callbacks. Registration may occur while a callback is being dispatched. */
public final class OrderedEvent<T>
{
    private final List<T> listeners = new CopyOnWriteArrayList<>();
    private final T invoker;

    public OrderedEvent(Function<List<T>, T> factory)
    {
        this.invoker = factory.apply(this.listeners);
    }

    public void register(T listener) { this.listeners.add(Objects.requireNonNull(listener)); }
    public T invoker() { return this.invoker; }
}
