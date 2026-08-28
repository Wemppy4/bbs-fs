package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.pose.Transform;

public class ImageOverlay
{
    public Link texture;
    public int x;
    public int y;
    public float windowX;
    public float windowY;
    public float anchorX;
    public float anchorY;
    public float scale;
    public int color;
    public boolean fullscreen;
    public boolean smooth;

    public Transform transform;
    public float factor;

    public void update(Link texture, int x, int y, float scale, int color, boolean fullscreen, boolean smooth)
    {
        this.texture = texture;
        this.x = x;
        this.y = y;
        this.scale = scale;
        this.color = color;
        this.fullscreen = fullscreen;
        this.smooth = smooth;
    }

    public void updateWindow(float x, float y)
    {
        this.windowX = x;
        this.windowY = y;
    }

    public void updateAnchor(float x, float y)
    {
        this.anchorX = x;
        this.anchorY = y;
    }

    public void updateTransform(Transform transform, float factor)
    {
        this.transform = transform;
        this.factor = factor;
    }
}
