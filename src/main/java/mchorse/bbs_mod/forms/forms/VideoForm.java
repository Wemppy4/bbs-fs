package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * A billboard that plays a video file: the quad machinery (crop, color,
 * billboarding, shading) is inherited, only the texture comes from a video
 * decoder instead of a picture. The playback clock is the carrier entity's
 * age, so scrubbing and export stay frame exact.
 */
public class VideoForm extends BillboardForm
{
    public static final Icon VIDEO_ICON = Icons.VIDEO_CAMERA;

    public final ValueLink video = new ValueLink("video", null);
    public final ValueBoolean loop = new ValueBoolean("loop", true);
    public final ValueFloat videoOffset = new ValueFloat("videoOffset", 0F);

    public VideoForm()
    {
        super();

        /* The quad's own texture is dead here — the renderer takes the frame from
         * the decoder instead, so it must not offer a track that does nothing. */
        this.texture.invisible();

        /* Looping is an authoring switch, like the render layer. */
        this.loop.invisible();

        this.add(this.video);
        this.add(this.loop);
        this.add(this.videoOffset);
    }

    @Override
    public String getDefaultDisplayName()
    {
        Link link = this.video.get();

        return link == null ? "video" : link.toString();
    }

    @Override
    public Icon getIcon()
    {
        return VIDEO_ICON;
    }
}
