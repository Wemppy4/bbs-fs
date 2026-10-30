package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/**
 * The film editor's motion path overlay settings, edited through the preview's
 * motion path button (see the onion skin feature it mirrors). Drawn by
 * {@link mchorse.bbs_mod.ui.film.controller.MotionPath} on the editors' markup, whose
 * size and opacity are the viewport settings.
 */
public class ValueMotionPath extends ValueGroup
{
    public final ValueBoolean enabled = new ValueBoolean("enabled", false);

    /* The interpolated curve */
    public final ValueInt color = new ValueInt("color", 0xff45c8ff);

    /* A dot on every tick */
    public final ValueBoolean frames = new ValueBoolean("frames", true);

    /* A marker on every position keyframe */
    public final ValueBoolean keyframes = new ValueBoolean("keyframes", true);
    public final ValueInt keyframeColor = new ValueInt("keyframe_color", 0xffffffff);

    /* A highlight on the current frame */
    public final ValueBoolean current = new ValueBoolean("current", true);
    public final ValueInt currentColor = new ValueInt("current_color", 0xffff9e3d);

    /* Time gradient: the curve fades from the past colour through the line
     * colour at the current frame to the future colour */
    public final ValueBoolean gradient = new ValueBoolean("gradient", true);
    public final ValueInt pastColor = new ValueInt("past_color", 0xff1b3a52);
    public final ValueInt futureColor = new ValueInt("future_color", 0xffe6f6ff);

    /* Limit the path to a window around the current frame */
    public final ValueBoolean aroundCurrent = new ValueBoolean("around_current", false);
    public final ValueInt before = new ValueInt("before", 20, 1, 1000);
    public final ValueInt after = new ValueInt("after", 20, 1, 1000);

    public ValueMotionPath(String id)
    {
        super(id);

        this.add(this.enabled);
        this.add(this.color);
        this.add(this.frames);
        this.add(this.keyframes);
        this.add(this.keyframeColor);
        this.add(this.current);
        this.add(this.currentColor);
        this.add(this.gradient);
        this.add(this.pastColor);
        this.add(this.futureColor);
        this.add(this.aroundCurrent);
        this.add(this.before);
        this.add(this.after);
    }
}
