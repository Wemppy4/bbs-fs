package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.graphics.MatrixStack;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** Ordered native dispatch of the original alternative film-gizmo placement event. */
public final class FilmGizmoEvents
{
    public static final DrawEvent DRAW = new DrawEvent();
    public interface Draw { boolean draw(FilmControllerContext context, StencilMap stencil, MatrixStack stack); }
    public static final class DrawEvent
    {
        private final CopyOnWriteArrayList<Draw> listeners = new CopyOnWriteArrayList<>();
        private final Draw invoker = (context, stencil, stack) ->
        {
            for (Draw listener : this.listeners) if (listener.draw(context, stencil, stack)) return true;
            return false;
        };
        public void register(Draw listener) { this.listeners.add(Objects.requireNonNull(listener)); }
        public Draw invoker() { return this.invoker; }
    }
    private FilmGizmoEvents() {}
}