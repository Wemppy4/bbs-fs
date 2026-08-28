package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.video.VideoPlayer;

public class VideoFormRenderer extends BillboardFormRenderer<VideoForm>
{
    /**
     * The UI preview (a frozen frame) and the world (the carrier's running clock)
     * ask for DIFFERENT timestamps every frame. They must not share a decoder:
     * two alternating targets never settle, and the async seek would starve both.
     * The preview runs on its own player keyed by this object.
     */
    private final Object uiPlayerKey = new Object();

    private float seconds;
    private boolean still;

    public VideoFormRenderer(VideoForm form)
    {
        super(form);
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        VideoForm form = this.form;
        int age = context.entity == null ? 0 : context.entity.getAge();

        this.seconds = (age + context.getTransition()) / 20F * form.speed.get() + form.videoOffset.get();
        this.still = false;

        super.render3D(context);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        /* Previews show a frozen frame - no decoder runs for list icons */
        this.seconds = this.form.videoOffset.get();
        this.still = true;

        super.renderInUI(context, x1, y1, x2, y2);

        if (this.getTexture() == null)
        {
            /* No file picked, or the first frame is still decoding - show SOMETHING */
            int size = 32;

            context.batcher.scaledIcon(Icons.VIDEO_CAMERA, Colors.WHITE, (x1 + x2 - size) / 2, (y1 + y2 - size) / 2, size);
        }
    }

    @Override
    protected Texture getTexture()
    {
        VideoForm form = this.form;
        Link link = form.video.get();

        if (link == null)
        {
            return null;
        }

        VideoPlayer player = BBSModClient.getVideos().getPlayer(this.still ? this.uiPlayerKey : this, link);

        if (player == null)
        {
            return null;
        }

        float time = this.seconds;

        if (form.loop.get() && player.getDuration() > 0F)
        {
            time = time % player.getDuration();

            if (time < 0F)
            {
                time += player.getDuration();
            }
        }

        Texture texture = player.getFrame(time);

        if (texture != null && this.still)
        {
            /* The frame IS on the texture - the preview's ffmpeg process has nothing
             * left to do. Stopping before the frame arrived would kill the decode
             * mid-flight and the preview would never load. */
            player.stop();
        }

        return texture;
    }
}
