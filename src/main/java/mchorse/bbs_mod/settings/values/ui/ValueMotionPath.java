package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/**
 * The film editor's motion path overlay settings, edited through the preview's motion path
 * button. Which paths there are lives in the film, on each replay; their look (a colour each,
 * dots, keys, the current frame) is fixed. Drawn by
 * {@link mchorse.bbs_mod.ui.film.controller.MotionPath} on the editors' markup, whose size and
 * opacity are the viewport settings.
 */
public class ValueMotionPath extends ValueGroup
{
    public final ValueBoolean enabled = new ValueBoolean("enabled", true);

    /* Limit the paths to a window around the current frame */
    public final ValueBoolean aroundCurrent = new ValueBoolean("around_current", false);
    public final ValueInt before = new ValueInt("before", 20, 1, 1000);
    public final ValueInt after = new ValueInt("after", 20, 1, 1000);

    public ValueMotionPath(String id)
    {
        super(id);

        this.add(this.enabled);
        this.add(this.aroundCurrent);
        this.add(this.before);
        this.add(this.after);
    }
}
