package mchorse.bbs_mod.ui.film.markers;

import mchorse.bbs_mod.film.markers.FilmMarker;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.context.UIPopup;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Name, tick and colour of a single marker, at the cursor.
 *
 * <p>Edits go straight into the marker's values: the film's undo handler is listening at the root
 * of the tree, so nothing has to be collected or applied here. Removing lives in the marker's
 * context menu on the ruler: a button at the foot of this panel read as "apply" and got clicked
 * by mistake.
 */
public class UIMarkerPopup extends UIPopup
{
    public UITextbox title;
    public UIColor color;
    public UITrackpad tick;

    private final FilmMarker marker;

    public UIMarkerPopup(FilmMarker marker)
    {
        super(UIKeys.FILM_MARKERS_TITLE);

        this.marker = marker;

        this.title = new UITextbox(100, (text) -> this.marker.title.set(text));
        this.title.placeholder(UIKeys.FILM_MARKERS_NAME);
        this.title.valueBinding(() -> this.title.setText(this.marker.title.get()));

        this.color = new UIColor((value) -> this.marker.color.set(value & Colors.RGB));
        this.color.valueBinding(() -> this.color.setColor(this.marker.color.get()));

        this.tick = new UITrackpad((value) -> this.marker.tick.set((int) Math.round(value)));
        this.tick.limit(0, Integer.MAX_VALUE, true);
        this.tick.valueBinding(() -> this.tick.setValue(this.marker.tick.get()));
        this.tick.tooltip(UIKeys.FILM_MARKERS_TICK);

        this.column.add(this.title, UI.row(this.tick, this.color));
    }
}
