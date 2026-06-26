package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.modifiers.OrbitClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;

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

        this.absolute = new UIToggle(UIKeys.CAMERA_PANELS_ABSOLUTE, false, (b) -> this.clip.absolute.set(b.getValue()));
        this.copy = new UIToggle(UIKeys.CAMERA_PANELS_COPY_ENTITY, false, (b) -> this.clip.copy.set(b.getValue()));
        this.copy.tooltip(UIKeys.CAMERA_PANELS_COPY_ENTITY_TOOLTIP);

        this.yaw = new UITrackpad((value) -> this.clip.yaw.set(value.floatValue()));
        this.yaw.tooltip(UIKeys.CAMERA_PANELS_YAW);

        this.pitch = new UITrackpad((value) -> this.clip.pitch.set(value.floatValue()));
        this.pitch.tooltip(UIKeys.CAMERA_PANELS_PITCH);

        this.offset = new UIPointModule(editor, UIKeys.CAMERA_PANELS_OFFSET).contextMenu();
        this.distance = new UITrackpad((value) -> this.clip.distance.set(value.floatValue()));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_TARGET, this.selector, this.absolute, this.copy).marginTop(UIConstants.SECTION_GAP));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_DISTANCE, this.distance).marginTop(UIConstants.SECTION_GAP));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_ANGLE, UI.row(5, 0, 20, this.yaw, this.pitch)).marginTop(UIConstants.SECTION_GAP));
        this.panels.add(this.offset.marginTop(UIConstants.SECTION_GAP));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.absolute.setValue(this.clip.absolute.get());
        this.copy.setValue(this.clip.copy.get());
        this.yaw.setValue(this.clip.yaw.get());
        this.pitch.setValue(this.clip.pitch.get());
        this.offset.fill(this.clip.offset);
        this.distance.setValue(this.clip.distance.get());
    }
}