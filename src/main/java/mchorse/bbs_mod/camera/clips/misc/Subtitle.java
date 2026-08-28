package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.pose.Transform;

public class Subtitle
{
    public String label = "";
    public Placement placement;
    public int color;
    public boolean textShadow;
    public int backgroundColor;
    public float backgroundOffset;
    public float shadow;
    public boolean shadowOpaque;

    public Transform transform;
    public float factor;

    public int lineHeight;
    public int maxWidth;

    public Link image;
    public boolean imageRight;
    public float imageScale;

    public void update(String label, Placement placement, int color, boolean textShadow)
    {
        this.label = label;
        this.placement = placement;
        this.color = color;
        this.textShadow = textShadow;
    }

    public void updateBackground(int backgroundColor, float backgroundOffset, float shadow, boolean shadowOpaque)
    {
        this.backgroundColor = backgroundColor;
        this.backgroundOffset = backgroundOffset;
        this.shadow = shadow;
        this.shadowOpaque = shadowOpaque;
    }

    public void updateTransform(Transform transform, float factor)
    {
        this.transform = transform;
        this.factor = factor;
    }

    public void updateConstraints(int lineHeight, int maxWidth)
    {
        this.lineHeight = lineHeight;
        this.maxWidth = maxWidth;
    }

    public void updateImage(Link image, boolean imageRight, float imageScale)
    {
        this.image = image;
        this.imageRight = imageRight;
        this.imageScale = imageScale;
    }
}
