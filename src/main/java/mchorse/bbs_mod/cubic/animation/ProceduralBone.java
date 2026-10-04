package mchorse.bbs_mod.cubic.animation;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import java.util.Map;

/** Missing entries preserve legacy names; an explicitly empty entry disables the role. */
public enum ProceduralBone
{
    HEAD("head"), TORSO("torso"), RIGHT_ARM("right_arm"), LEFT_ARM("left_arm"),
    RIGHT_LEG("right_leg"), LEFT_LEG("left_leg"), ANCHOR("anchor");

    public final String id;

    ProceduralBone(String id)
    {
        this.id = id;
    }

    public String resolve(IModel model, Map<String, String> assignments)
    {
        if (assignments.containsKey(this.id)) return assignments.get(this.id);
        if (this == TORSO)
        {
            if (!(model instanceof Model)) return "";
            return model.getAllGroupKeys().contains("torso") ? "torso" : "body";
        }
        return this.id;
    }

    public String detect(IModel model)
    {
        String name = this.resolve(model, java.util.Collections.emptyMap());
        return model.getAllGroupKeys().contains(name) ? name : "";
    }
}
