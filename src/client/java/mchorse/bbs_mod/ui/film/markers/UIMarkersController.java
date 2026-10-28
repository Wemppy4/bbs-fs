package mchorse.bbs_mod.ui.film.markers;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.markers.FilmMarker;
import mchorse.bbs_mod.film.markers.FilmMarkers;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelineCanvas;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;

import java.util.List;
import java.util.function.Supplier;

/**
 * Draws the film's markers, extending their lines across the timeline when its grid is enabled,
 * and answers which one the mouse is over on the ruler strip. A marker is drawn like the playhead
 * ({@link UITimelineCanvas#renderCursor}) in its own colour, its title on the card at the top.
 *
 * <p>Not a widget: the ruler belongs to the timeline that painted it ({@code UIClips},
 * {@code UIFilmKeyframes}), and every one of them measures ticks with its own {@link Scale} and its
 * own clip offset. So this takes the geometry as arguments and keeps only what is genuinely its
 * own — which marker is being dragged and where it has been dragged to. One instance per timeline,
 * all of them reading the same {@link FilmMarkers} off the film.
 *
 * <p>The markers go on <em>after</em> the ruler's notches rather than under them: a notch is a
 * measuring aid and a marker is an author's note, so where the two land on the same pixel the note
 * wins. That is the opposite of the audio waveform and clip gradient, which are backdrop.
 */
public class UIMarkersController
{
    /** How far from a marker's line the mouse still counts as being on it. */
    public static final int HIT_DISTANCE = 4;

    private final Supplier<FilmMarkers> markers;

    private FilmMarker dragged;
    private int dragTick;

    public UIMarkersController(Supplier<FilmMarkers> markers)
    {
        this.markers = markers;
    }

    public FilmMarkers getMarkers()
    {
        return this.markers.get();
    }

    /* Dragging */

    public FilmMarker getDragged()
    {
        return this.dragged;
    }

    public boolean isDragging()
    {
        return this.dragged != null;
    }

    public int getDragTick()
    {
        return this.dragTick;
    }

    public void beginDrag(FilmMarker marker)
    {
        this.dragged = marker;
        this.dragTick = marker.tick.get();
    }

    public void dragTo(int tick)
    {
        this.dragTick = Math.max(0, tick);
    }

    public void stopDrag()
    {
        this.dragged = null;
    }

    /**
     * Where a marker is drawn right now: its own tick, unless it is the one being dragged, in which
     * case the gesture's tick. The value is only written back when the gesture ends, so a drag
     * across the timeline is one undo step rather than one per pixel.
     */
    public int getDisplayTick(FilmMarker marker)
    {
        return marker == this.dragged ? this.dragTick : marker.tick.get();
    }

    /* Hit testing */

    /**
     * The ruler strip of <em>this</em> timeline: bounded horizontally too, or two timelines stacked
     * at the same height would both answer for a mouse sitting over one of them.
     */
    public boolean isInRuler(Area area, int mouseX, int mouseY)
    {
        return mouseX >= area.x && mouseX < area.ex()
            && mouseY >= area.y && mouseY < TimelineRulerRenderer.getRulerBottom(area);
    }

    /**
     * @return The marker under the mouse, or {@code null}. Only the ruler strip counts — below it
     * the timeline's own content has the click.
     */
    public FilmMarker getMarkerAt(Area area, Scale scale, int mouseX, int mouseY, int clipOffset)
    {
        FilmMarkers markers = this.getMarkers();

        if (markers == null || !this.isInRuler(area, mouseX, mouseY))
        {
            return null;
        }

        FilmMarker closest = null;
        int closestDistance = HIT_DISTANCE + 1;

        for (FilmMarker marker : markers.getList())
        {
            int x = (int) scale.to(this.getDisplayTick(marker) - clipOffset);
            int distance = Math.abs(mouseX - x);

            if (distance <= HIT_DISTANCE && distance < closestDistance)
            {
                closest = marker;
                closestDistance = distance;
            }
        }

        return closest;
    }

    /* Rendering */

    public void render(UIContext context, Area area, Scale scale, int clipOffset)
    {
        FilmMarkers markers = this.getMarkers();

        if (markers == null || markers.getList().isEmpty())
        {
            return;
        }

        int rulerBottom = TimelineRulerRenderer.getRulerBottom(area);

        if (rulerBottom <= area.y)
        {
            return;
        }

        List<FilmMarker> sorted = markers.getSorted();
        FontRenderer font = context.batcher.getFont();
        FilmMarker hovered = this.getMarkerAt(area, scale, context.mouseX, context.mouseY, clipOffset);
        int lineBottom = BBSSettings.editorTimelineGrid.get() ? area.ey() : rulerBottom;

        context.batcher.clipBox(area.x, area.y, area.ex(), lineBottom, context);

        for (int i = 0, c = sorted.size(); i < c; i++)
        {
            FilmMarker marker = sorted.get(i);
            int x = (int) scale.to(this.getDisplayTick(marker) - clipOffset);

            if (x < area.x || x >= area.ex())
            {
                continue;
            }

            String title = marker.title.get();
            boolean active = marker == hovered || marker == this.dragged;

            /* Titles are dropped rather than overlapped: past a certain density the ruler is a row
             * of lines and the panel is where names get read. The hovered one always shows. */
            if (!active && i + 1 < c)
            {
                int next = (int) scale.to(this.getDisplayTick(sorted.get(i + 1)) - clipOffset);

                if (font.getWidth(title) + 6 > next - x)
                {
                    title = "";
                }
            }

            UITimelineCanvas.renderCursor(context, title, area, x, lineBottom, marker.color.get(), true);
        }

        context.batcher.unclip(context);
    }
}
