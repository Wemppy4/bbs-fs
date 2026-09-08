package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.clips.UIVideoClip;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.values.UIValues;

public class UIVideoFormPanel extends UIFormPanel<VideoForm>
{
    public UIVideoFormPanel(UIForm editor)
    {
        super(editor);

        UIButton pickVideo = new UIButton(UIKeys.CAMERA_PANELS_VIDEO_PICK, (b) ->
        {
            UIStringOverlayPanel panel = UIStringOverlayPanel.links(UIKeys.CAMERA_PANELS_VIDEO_PICK, UIVideoClip.getVideoLinks(), (l) -> this.form.video.set(l));

            UIOverlay.addOverlay(this.getContext(), panel.set(this.form.video.get()));
        });

        this.options.add(pickVideo);
        this.options.add(
            UIValues.toggle(UIKeys.CAMERA_PANELS_VIDEO_LOOP, () -> this.form.loop),
            UIValues.toggle(UIKeys.FORMS_EDITORS_VIDEO_BILLBOARD, () -> this.form.billboard)
        );
        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_VIDEO_PLAYBACK).marginTop(UIConstants.SECTION_GAP),
            UI.row(
                UIValues.trackpad(() -> this.form.speed).tooltip(UIKeys.FORMS_EDITORS_VIDEO_SPEED),
                UIValues.trackpad(() -> this.form.videoOffset).tooltip(UIKeys.FORMS_EDITORS_VIDEO_OFFSET)
            )
        );
    }
}
