package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.modifiers.OrbitClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;import mchorse.bbs_mod.ui.utils.UI;

public class UIOrbitClip extends UIClip<OrbitClip>
{
    public UIButton selector;
    public UIToggle absolute;
    public UIToggle copy;
    public UITrackpad yaw;
    public UITrackpad pitch;
    public UIPointModule offset;
    public UITrackpad distance;

    public UIOrbitClip(OrbitClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.selector = new UIButton(UIKeys.CAMERA_PANELS_TARGET_TITLE, (b) ->
        {
            UIFilmPanel panel = this.getParent(UIFilmPanel.class);

            if (panel != null)
            {
                UIAnchorKeyframeFactory.displayActors(this.getContext(), panel.getController().getEntities(), this.clip.selector.get(), (i) -> this.clip.selector.set(i));
            }
        });
        this.selector.tooltip(UIKeys.CAMERA_PANELS_TARGET_TOOLTIP);

        this.absolute = this.toggle(UIKeys.CAMERA_PANELS_ABSOLUTE, this.clip.absolute);
        this.copy = this.toggle(UIKeys.CAMERA_PANELS_COPY_ENTITY, this.clip.copy);
        this.copy.tooltip(UIKeys.CAMERA_PANELS_COPY_ENTITY_TOOLTIP);

        this.yaw = this.trackpad(this.clip.yaw);
        this.yaw.tooltip(UIKeys.CAMERA_PANELS_YAW);

        this.pitch = this.trackpad(this.clip.pitch);
        this.pitch.tooltip(UIKeys.CAMERA_PANELS_PITCH);

        this.offset = this.bind(new UIPointModule(editor, UIKeys.CAMERA_PANELS_OFFSET).contextMenu(), () -> this.offset.fill(this.clip.offset));
        this.distance = this.trackpad(this.clip.distance);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_TARGET, this.selector, this.absolute, this.copy));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_DISTANCE, this.distance));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_ANGLE, UI.row(5, 0, 20, this.yaw, this.pitch)));
        this.panels.add(this.offset);
    }
}