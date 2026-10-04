package mchorse.bbs_mod.cubic.model.config;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;

/**
 * A model's optional look-at config (the {@code look_at} block of config.json): the head bone turns to
 * track the viewer. An empty {@link #head} means no look-at — the model treats it as absent, and the
 * config skips writing the block.
 */
public class LookAtValue extends ValueGroup
{
    public final ValueString head = new ValueString("head", "");
    public final ValueBoolean pitch = new ValueBoolean("pitch", true);
    public final ValueFloat headLimit = new ValueFloat("head_limit", 45F);

    public LookAtValue(String id)
    {
        super(id);

        this.add(this.head);
        this.add(this.pitch);
        this.add(this.headLimit);
    }

    public boolean isActive()
    {
        return !this.head.get().trim().isEmpty();
    }
}
