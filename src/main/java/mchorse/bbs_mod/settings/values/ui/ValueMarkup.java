package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;

/**
 * The editors' markup: which of its groups show, edited through the film preview's markup
 * button. The motion path keeps its own group ({@link ValueMotionPath}) and the rig's IK goals,
 * poles and controllers their debug switch (F5); how solid the layer draws and how big its handles
 * are are settings of their own (shown in the settings, used by every editor); drawn by
 * {@code mchorse.bbs_mod.ui.utils.ScreenOverlay}.
 */
public class ValueMarkup extends ValueGroup
{
    public final ValueBoolean splines = new ValueBoolean("splines", true);
    public final ValueBoolean shapeKeys = new ValueBoolean("shape_keys", true);

    public ValueMarkup(String id)
    {
        super(id);

        this.add(this.splines);
        this.add(this.shapeKeys);
    }
}
