package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.pose.Transform;

public class Anchor implements IMapSerializable
{
    public static final String NO_ATTACHMENT = "";

    /**
     * Stable id of the replay this anchor hangs off ({@link #NO_ATTACHMENT} = none). An id, not a
     * list index: the target must survive replays being reordered or removed around it.
     */
    public String replay = NO_ATTACHMENT;
    public String attachment = "";
    public boolean translate = false;
    public boolean scale = false;
    public final Transform transform = new Transform();

    /* Interpolation data */
    public Anchor previous;
    public float x;

    public Anchor()
    {}

    public Anchor(String replay, String attachment, boolean translate, boolean scale)
    {
        this.replay = replay;
        this.attachment = attachment;
        this.translate = translate;
        this.scale = scale;
    }

    public boolean hasTarget()
    {
        return !this.replay.isEmpty();
    }

    public boolean isFadeIn()
    {
        return this.previous != null && this.hasTarget() && !this.previous.hasTarget();
    }

    public boolean isFadeOut()
    {
        return this.previous != null && !this.hasTarget() && this.previous.hasTarget();
    }

    public boolean hasSameTarget(Anchor anchor)
    {
        return anchor != null
            && this.replay.equals(anchor.replay)
            && this.attachment.equals(anchor.attachment)
            && this.translate == anchor.translate
            && this.scale == anchor.scale;
    }

    public Anchor copy()
    {
        Anchor anchor = new Anchor(this.replay, this.attachment, this.translate, this.scale);

        anchor.transform.copy(this.transform);

        return anchor;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (super.equals(obj))
        {
            return true;
        }

        if (obj instanceof Anchor anchor)
        {
            return this.hasSameTarget(anchor)
                && this.transform.equals(anchor.transform);
        }

        return false;
    }

    @Override
    public void fromData(MapType data)
    {
        this.replay = data.getString("actor");
        this.attachment = data.getString("attachment");
        this.translate = data.getBool("translate", false);
        this.scale = data.getBool("scale", false);

        if (data.has("transform"))
        {
            this.transform.fromData(data.getMap("transform"));
        }
        else
        {
            this.transform.identity();
        }
    }

    @Override
    public void toData(MapType data)
    {
        data.putString("actor", this.replay);
        data.putString("attachment", this.attachment);
        data.putBool("translate", this.translate);
        data.putBool("scale", this.scale);

        if (!this.transform.isDefault())
        {
            data.put("transform", this.transform.toData());
        }
    }
}
