package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.film.BaseFilmController;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * The life of a film being played: its scene is built, it ticks, it draws, it ends.
 *
 * <p>These are the four moments an addon that adds behaviour to a scene needs, and until now the
 * only way to have them was a mixin into the film controller — which made four of BBS's method
 * names into a contract nobody had agreed to, and broke silently the day one of them moved.</p>
 *
 * <p>Plain Fabric events rather than the addon bus on purpose: two of these run every tick and
 * every frame, and the bus finds its subscribers by reflection. An array-backed event with no
 * listeners costs an empty loop.</p>
 *
 * <p>Every controller of a film goes through here — the one playing in the world, the one in the
 * editor's preview, the recorder. Ask the controller which it is if that matters.</p>
 */
public class FilmEvents
{
    /**
     * The scene's entities have just been built, and are about to be ticked for the first time.
     * This is where something that mirrors the scene builds its own copy of it.
     */
    public static final Event<Created> CREATED = EventFactory.createArrayBacked(Created.class, (listeners) -> (controller) ->
    {
        for (Created listener : listeners)
        {
            listener.onFilmCreated(controller);
        }
    });

    /** Before the scene's entities are updated for the tick. */
    public static final Event<Tick> TICK_BEFORE = EventFactory.createArrayBacked(Tick.class, (listeners) -> (controller, ticks) ->
    {
        for (Tick listener : listeners)
        {
            listener.onFilmTick(controller, ticks);
        }
    });

    /** After the scene's entities have been updated for the tick. */
    public static final Event<Tick> TICK_AFTER = EventFactory.createArrayBacked(Tick.class, (listeners) -> (controller, ticks) ->
    {
        for (Tick listener : listeners)
        {
            listener.onFilmTick(controller, ticks);
        }
    });

    /** After the scene has been drawn into the world. */
    public static final Event<Render> RENDER_AFTER = EventFactory.createArrayBacked(Render.class, (listeners) -> (controller, context) ->
    {
        for (Render listener : listeners)
        {
            listener.onFilmRender(controller, context);
        }
    });

    /**
     * The film is over and its controller is being torn down. Whatever was built in
     * {@link #CREATED} is let go here — a listener that skips this leaks for the whole session.
     */
    public static final Event<Shutdown> SHUTDOWN = EventFactory.createArrayBacked(Shutdown.class, (listeners) -> (controller) ->
    {
        for (Shutdown listener : listeners)
        {
            listener.onFilmShutdown(controller);
        }
    });

    public static interface Created
    {
        public void onFilmCreated(BaseFilmController controller);
    }

    public static interface Tick
    {
        public void onFilmTick(BaseFilmController controller, int ticks);
    }

    public static interface Render
    {
        public void onFilmRender(BaseFilmController controller, WorldRenderContext context);
    }

    public static interface Shutdown
    {
        public void onFilmShutdown(BaseFilmController controller);
    }
}
