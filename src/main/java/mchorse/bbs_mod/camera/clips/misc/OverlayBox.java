package mchorse.bbs_mod.camera.clips.misc;

/**
 * The rectangle (in frame units, transform not included) an overlay actually
 * occupied when it was last rendered. The preview's placement gizmo draws its
 * frame around this.
 */
public class OverlayBox
{
    public float x;
    public float y;
    public float w;
    public float h;

    /**
     * The unit frame's width at the moment the box was written. The gizmo maps
     * between units and the screen through THIS value rather than recomputing
     * it - the renderer and the UI run against different framebuffers, and
     * recomputing could disagree with what was actually drawn.
     */
    public float unitWidth;

    public void set(float x, float y, float w, float h, float unitWidth)
    {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.unitWidth = unitWidth;
    }
}
