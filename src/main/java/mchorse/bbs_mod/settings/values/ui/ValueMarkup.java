package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;

/**
 * The editors' markup: which of its groups show and how it draws, edited through the film
 * preview's markup button. The motion path keeps its own group ({@link ValueMotionPath}) and the
 * rig's IK goals, poles and controllers their debug switch (F5); drawn by
 * {@code mchorse.bbs_mod.ui.utils.overlay.ScreenOverlay}.
 */
public class ValueMarkup extends ValueGroup
{
    public final ValueBoolean splines = new ValueBoolean("splines", true);
    public final ValueBoolean shapeKeys = new ValueBoolean("shape_keys", true);

    /* How solid the whole layer draws and how big its handles are */
    public final ValueFloat opacity = new ValueFloat("opacity", 1F, 0.2F, 1F);
    public final ValueFloat size = new ValueFloat("size", 1F, 0.5F, 2F);

    public ValueMarkup(String id)
    {
        super(id);

        this.add(this.splines);
        this.add(this.shapeKeys);
        this.add(this.opacity);
        this.add(this.size);
    }
}
