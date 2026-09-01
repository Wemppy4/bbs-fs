package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.audio.AudioReader;
import mchorse.bbs_mod.camera.clips.misc.VideoClientClip;
import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.widgets.UIPlacement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.video.VideoPlayer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class UIVideoClip extends UIAudioClip<VideoClientClip>
{
    public UIToggle loop;
    public UIToggle fullscreen;
    public UIToggle smooth;
    public UIColor color;
    public UIPlacement placement;
    public UIPropTransform transform;

    public UIVideoClip(VideoClientClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    public static List<Link> getVideoLinks()
    {
        List<Link> links = new ArrayList<>();

        for (Link link : BBSMod.getProvider().getLinksFromPath(Link.assets("video")))
        {
            if (AudioReader.isVideo(link.path.toLowerCase()))
            {
                links.add(link);
            }
        }

        return links;
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.pickAudio = new UIButton(UIKeys.CAMERA_PANELS_VIDEO_PICK, (b) ->
        {
            UIStringOverlayPanel panel = UIStringOverlayPanel.links(UIKeys.CAMERA_PANELS_VIDEO_PICK, getVideoLinks(), (l) -> this.clip.audio.set(l));

            UIOverlay.addOverlay(this.getContext(), panel.set(this.clip.audio.get()));
        });

        this.openFolder = new UIIcon(Icons.FOLDER, (b) ->
        {
            File folder = new File(BBSMod.getAssetsFolder(), "video");

            folder.mkdirs();
            UIUtils.openFolder(folder);
        });

        this.loop = this.toggle(UIKeys.CAMERA_PANELS_VIDEO_LOOP, this.clip.loop);
        this.fullscreen = this.toggle(UIKeys.CAMERA_PANELS_IMAGE_FULLSCREEN, this.clip.fullscreen);
        this.smooth = this.toggle(UIKeys.CAMERA_PANELS_IMAGE_SMOOTH, this.clip.smooth);
        this.color = this.color(this.clip.color).withAlpha();
        this.placement = this.placement(this.clip.placement, new Placement());
        this.transform = this.transform(this.clip.transform);
    }

    @Override
    protected double getMediaDuration(Link link)
    {
        VideoPlayer player = BBSModClient.getVideos().get(link);

        if (player != null)
        {
            player.ensureProbed();
        }

        return player != null && player.isValid() ? player.getDuration() : 0D;
    }

    @Override
    protected IKey getMediaTitle()
    {
        return UIKeys.C_CLIP.get("bbs:video");
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_IMAGE, this.loop, this.fullscreen, this.smooth, this.color));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_PLACEMENT, this.placement.fields()));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_TRANSFORM, this.transform));
    }
}
