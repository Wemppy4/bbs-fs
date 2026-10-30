package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;

/**
 * The editors' markup: which of its groups show and how it draws, edited through the film
 * preview's markup button. The motion path keeps its own group ({@link ValueMotionPath}) and the
 * rig's IK goals, poles and controllers their debug switch (F5), and the size of the handles is a
 * setting of its own (shown in the settings, used by every editor); drawn by
 * {@code mchorse.bbs_mod.ui.utils.ScreenOverlay}.
 */
public class ValueMarkup extends ValueGroup
{
    public final ValueBoolean splines = new ValueBoolean("splines", true);
    public final ValueBoolean shapeKeys = new ValueBoolean("shape_keys", true);

    /* How solid the whole layer draws */
    public final ValueFloat opacity = new ValueFloat("opacity", 1F, 0.2F, 1F);

    public ValueMarkup(String id)
    {
        super(id);

        this.add(this.splines);
        this.add(this.shapeKeys);
        this.add(this.opacity);
    }
}
