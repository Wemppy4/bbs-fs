package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.resources.Link;
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
        this.loop = new UIToggle(UIKeys.CAMERA_PANELS_VIDEO_LOOP, (b) -> this.form.loop.set(b.getValue()));
        this.speed = new UITrackpad((v) -> this.form.speed.set(v.floatValue()));
        this.speed.tooltip(UIKeys.FORMS_EDITORS_VIDEO_SPEED);
        this.offset = new UITrackpad((v) -> this.form.videoOffset.set(v.floatValue()));
        this.offset.tooltip(UIKeys.FORMS_EDITORS_VIDEO_OFFSET);
        this.billboard = new UIToggle(UIKeys.FORMS_EDITORS_VIDEO_BILLBOARD, (b) -> this.form.billboard.set(b.getValue()));

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
