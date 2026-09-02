package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.clips.UIVideoClip;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.values.UIValues;

public class UIVideoFormPanel extends UIFormPanel<VideoForm>
{
    public UIButton pickVideo;
    public UIToggle loop;
    public UITrackpad speed;
    public UITrackpad offset;
    public UIToggle billboard;

    public UIVideoFormPanel(UIForm editor)
    {
        super(editor);

        this.pickVideo = new UIButton(UIKeys.CAMERA_PANELS_VIDEO_PICK, (b) ->
        {
            UIStringOverlayPanel panel = UIStringOverlayPanel.links(UIKeys.CAMERA_PANELS_VIDEO_PICK, UIVideoClip.getVideoLinks(), (l) -> this.form.video.set(l));

            UIOverlay.addOverlay(this.getContext(), panel.set(this.form.video.get()));
        });
        this.loop = UIValues.toggle(UIKeys.CAMERA_PANELS_VIDEO_LOOP, () -> this.form.loop);
        this.speed = UIValues.trackpad(() -> this.form.speed);
        this.speed.tooltip(UIKeys.FORMS_EDITORS_VIDEO_SPEED);
        this.offset = UIValues.trackpad(() -> this.form.videoOffset);
        this.offset.tooltip(UIKeys.FORMS_EDITORS_VIDEO_OFFSET);
        this.billboard = UIValues.toggle(UIKeys.FORMS_EDITORS_VIDEO_BILLBOARD, () -> this.form.billboard);

        this.options.add(this.pickVideo);
        this.options.add(this.loop, this.billboard);
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_VIDEO_PLAYBACK).marginTop(UIConstants.SECTION_GAP), UI.row(this.speed, this.offset));
    }

    @Override
    public void startEdit(VideoForm form)
    {
        super.startEdit(form);

        this.loop.setValue(form.loop.get());
        this.speed.setValue(form.speed.get());
        this.offset.setValue(form.videoOffset.get());
        this.billboard.setValue(form.billboard.get());
    }
}
