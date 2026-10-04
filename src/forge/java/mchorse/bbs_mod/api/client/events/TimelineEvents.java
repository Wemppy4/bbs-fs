package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.DoubleToIntFunction;

/** Overlay shared by clip and actor-keyframe timelines. The coordinate mapper accepts
 * absolute film ticks and includes clip offset, scroll and zoom. Area is borrowed read-only. */
public final class TimelineEvents
{
    public static final OverlayEvent OVERLAY = new OverlayEvent();

    public interface Overlay { void render(Film film, UIContext context, Area area, DoubleToIntFunction toX); }

    /** Native listener dispatch; preserves registration order and invocation snapshots. */
    public static final class OverlayEvent
    {
        private final CopyOnWriteArrayList<Overlay> listeners = new CopyOnWriteArrayList<>();
        private final Overlay invoker = (film, context, area, toX) ->
        {
            for (Overlay listener : this.listeners)
            {
                listener.render(film, context, area, toX);
            }
        };

        public void register(Overlay listener)
        {
            this.listeners.add(Objects.requireNonNull(listener, "listener"));
        }

        public Overlay invoker()
        {
            return this.invoker;
        }
    }

    private TimelineEvents() {}
}
