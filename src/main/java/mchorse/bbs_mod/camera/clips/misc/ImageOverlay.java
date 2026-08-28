package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.pose.Transform;

public class ImageOverlay
{
    public Link texture;
    public Placement placement;
    public int color;
    public boolean fullscreen;
    public boolean smooth;

    public Transform transform;
    public float factor;

    public void update(Link texture, Placement placement, int color, boolean fullscreen, boolean smooth)
    {
        this.texture = texture;
        this.placement = placement;
        this.color = color;
        this.fullscreen = fullscreen;
        this.smooth = smooth;
    }

    public void updateTransform(Transform transform, float factor)
    {
        this.transform = transform;
        this.factor = factor;
    }
}
