package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;

/**
 * Plays back a video file: the picture goes on screen the way an image clip's does,
 * while the audio track rides the inherited audio clip machinery (the {@link #audio}
 * link points at the video file itself).
 */
public class VideoClip extends AudioClip
{
    public ValueInt x = new ValueInt("x", 0);
    public ValueInt y = new ValueInt("y", 0);
    public ValueFloat windowX = new ValueFloat("windowX", 0.5F);
    public ValueFloat windowY = new ValueFloat("windowY", 0.5F);
    public ValueFloat anchorX = new ValueFloat("anchorX", 0.5F);
    public ValueFloat anchorY = new ValueFloat("anchorY", 0.5F);
    public ValueFloat scale = new ValueFloat("scale", 1F);
    public ValueInt color = new ValueInt("color", Colors.WHITE);
    public ValueBoolean fullscreen = new ValueBoolean("fullscreen", true);
    public ValueBoolean smooth = new ValueBoolean("smooth", true);
    public ValueTransform transform = new ValueTransform("transform", new Transform());

    public VideoClip()
    {
        super();

        this.add(this.x);
        this.add(this.y);
        this.add(this.windowX);
        this.add(this.windowY);
        this.add(this.anchorX);
        this.add(this.anchorY);
        this.add(this.scale);
        this.add(this.color);
        this.add(this.fullscreen);
        this.add(this.smooth);
        this.add(this.transform);
    }

    @Override
    protected Clip create()
    {
        return new VideoClip();
    }
}
