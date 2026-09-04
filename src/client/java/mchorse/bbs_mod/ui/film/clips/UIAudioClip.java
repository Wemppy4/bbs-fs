package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.audio.SoundBuffer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UISoundOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.io.File;

public class UIAudioClip <T extends AudioClip> extends UIClip<T>
{
    public UIButton pickAudio;
    public UIIcon openFolder;
    public UIIcon extendDuration;
    public UITrackpad offset;
    public UISliderTrackpad volume;

    public UIAudioClip(T clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.pickAudio = new UIButton(UIKeys.CAMERA_PANELS_AUDIO_PICK_AUDIO, (b) ->
        {
            UISoundOverlayPanel panel = new UISoundOverlayPanel((l) -> this.clip.audio.set(l), this.getContext());

            UIOverlay.addOverlay(this.getContext(), panel.set(this.clip.audio.get()));
        });

        this.openFolder = new UIIcon(Icons.FOLDER, (b) ->
        {
            Link link = this.clip.audio.get();
            File file = BBSMod.getAudioFolder();

            if (link != null)
            {
                File audioFile = BBSMod.getProvider().getFile(link);

                if (audioFile.exists())
                {
                    file = audioFile.getParentFile();
                }
            }

            UIUtils.openFolder(file);
        });

        this.extendDuration = new UIIcon(Icons.RIGHTLOAD, (b) -> this.extendToMediaDuration());
        this.extendDuration.tooltip(UIKeys.CAMERA_PANELS_AUDIO_EXTEND_DURATION);

        this.offset = this.trackpad(this.clip.offset);
        this.offset.integer();

        this.volume = this.bind(new UISliderTrackpad((v) -> this.clip.volume.set(v.floatValue())), () -> this.volume.setValue(this.clip.volume.get()));
        this.volume.limit(this.clip.volume).values(0.05F, 0.01F, 0.2F).increment(0.1F).tooltip(UIKeys.CAMERA_PANELS_AUDIO_VOLUME);
    }

    /** Stretch the clip to the end of its media, counted from the offset it starts playing at. */
    private void extendToMediaDuration()
    {
        Link link = this.clip.audio.get();
        double duration = link == null ? 0D : this.getMediaDuration(link);

        if (duration > 0D)
        {
            this.clip.duration.set((int) ((duration * 20) - this.clip.offset.get()));
            this.fillData();
        }
    }

    /** How long the picked media runs, in seconds — zero when it isn't there or isn't loaded yet. */
    protected double getMediaDuration(Link link)
    {
        SoundBuffer buffer = BBSModClient.getSounds().get(link, true);

        return buffer == null ? 0D : buffer.getDuration();
    }

    protected IKey getMediaTitle()
    {
        return UIKeys.C_CLIP.get("bbs:audio");
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(this.getMediaTitle(),
            UI.row(this.pickAudio, this.extendDuration, this.openFolder),
            UI.labelRow(UIKeys.CAMERA_PANELS_AUDIO_OFFSET, this.offset).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.CAMERA_PANELS_AUDIO_VOLUME, this.volume)
        ));
    }
}