package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.settings.values.core.ValuePlacement;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
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
    public ValuePlacement placement = new ValuePlacement("placement", new Placement());
    public ValueInt color = new ValueInt("color", Colors.WHITE);
    public ValueBoolean fullscreen = new ValueBoolean("fullscreen", true);
    public ValueBoolean smooth = new ValueBoolean("smooth", true);
    public ValueTransform transform = new ValueTransform("transform", new Transform());

    public VideoClip()
    {
        super();

        this.add(this.placement);
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
