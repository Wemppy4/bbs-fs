package mchorse.bbs_mod.ui.film.markers;

import mchorse.bbs_mod.film.markers.FilmMarker;
import mchorse.bbs_mod.film.markers.FilmMarkers;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Name, colour and tick of a single marker, plus the way to get rid of it.
 *
 * <p>Edits go straight into the marker's values: the film's undo handler is listening at the root
 * of the tree, so nothing has to be collected or applied here.
 */
public class UIMarkerOverlayPanel extends UIOverlayPanel
{
    public UITextbox title;
    public UIColor color;
    public UITrackpad tick;
    public UIButton remove;

    private final FilmMarkers markers;
    private final FilmMarker marker;

    public UIMarkerOverlayPanel(FilmMarkers markers, FilmMarker marker)
    {
        super(UIKeys.FILM_MARKERS_TITLE);

        this.markers = markers;
        this.marker = marker;

        this.title = new UITextbox(100, (text) -> this.marker.title.set(text));
        this.title.placeholder(UIKeys.FILM_MARKERS_NAME);
        this.title.valueBinding(() -> this.title.setText(this.marker.title.get()));

        this.color = new UIColor((value) -> this.marker.color.set(value & Colors.RGB));
        this.color.valueBinding(() -> this.color.setColor(this.marker.color.get()));

        this.tick = new UITrackpad((value) -> this.marker.tick.set((int) Math.round(value)));
        this.tick.limit(0, Integer.MAX_VALUE, true);
        this.tick.valueBinding(() -> this.tick.setValue(this.marker.tick.get()));

        this.remove = new UIButton(UIKeys.FILM_MARKERS_REMOVE, (b) ->
        {
            this.markers.remove(this.marker);
            this.close();
        });

        UIElement column = UI.column(
            UI.label(UIKeys.FILM_MARKERS_NAME),
            this.title,
            UI.label(UIKeys.FILM_MARKERS_TICK).marginTop(6),
            this.tick,
            UI.label(UIKeys.FILM_MARKERS_COLOR).marginTop(6),
            this.color,
            this.remove.marginTop(10)
        );

        column.relative(this.content).xy(6, 6).w(1F, -12).h(1F, -12);

        this.content.add(column);

        /* The picker lives next to the overlay, not inside it, so it would outlive the panel */
        this.onClose((e) -> this.color.picker.removeFromParent());
    }
}
