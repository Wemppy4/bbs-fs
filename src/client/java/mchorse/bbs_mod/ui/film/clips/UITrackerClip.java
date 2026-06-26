package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.modifiers.TrackerClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.film.clips.widgets.UIBitToggle;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UIConstants;

public class UITrackerClip extends UIClip<TrackerClip>
{
    public UIButton selector;
    public UIButton group;

    public UIPointModule point;
    public UIPointModule angle;
    public UITrackpad fov;
    public UIToggle lookAt;
    public UIToggle relative;
    public UIBitToggle active;

    public UITrackerClip(TrackerClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    public void registerUI()
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
        this.group = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT, (b) ->
        {
            UIAnchorKeyframeFactory.displayAttachments(this.getParent(UIFilmPanel.class), this.clip.selector.get(), this.clip.group.get(), (attachment) -> this.clip.group.set(attachment));
        });

        this.point = new UIPointModule(this.editor, UIKeys.CAMERA_PANELS_OFFSET).contextMenu();
        this.angle = new UIPointModule(this.editor, UIKeys.CAMERA_PANELS_ANGLE).contextMenu();
        this.fov = new UITrackpad((v) -> this.clip.fov.set(v.floatValue()));
        this.fov.tooltip(UIKeys.CAMERA_PANELS_FOV);
        this.lookAt = new UIToggle(UIKeys.CAMERA_PANELS_LOOK_AT, b -> this.clip.lookAt.set(b.getValue()));
        this.relative = new UIToggle(UIKeys.CAMERA_PANELS_RELATIVE, b -> this.clip.relative.set(b.getValue()));
        this.active = new UIBitToggle((value) -> this.clip.active.set(value)).all();
    }

    private UIFilmPanel getPanel()
    {
        return this.getContext().menu.getRoot().getChildren(UIFilmPanel.class).get(0);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_TARGET, this.selector, this.group).marginTop(UIConstants.SECTION_GAP));

        this.panels.add(this.point.marginTop(UIConstants.SECTION_GAP));
        this.panels.add(this.angle.marginTop(UIConstants.SECTION_GAP));
        this.panels.add(this.fov);
        this.panels.add(this.lookAt);
        this.panels.add(this.relative);
        this.panels.add(this.active);
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.point.fill(this.clip.offset);
        this.angle.fill(this.clip.angle);
        this.fov.setValue(this.clip.fov.get());
        this.lookAt.setValue(this.clip.lookAt.get());
        this.relative.setValue(this.clip.relative.get());
        this.active.setValue(this.clip.active.get());
    }
}
