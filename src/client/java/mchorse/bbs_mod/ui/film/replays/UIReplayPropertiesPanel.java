package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.forms.UINestedEdit;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;

import java.util.function.Consumer;

public class UIReplayPropertiesPanel extends UIElement
{
    private final UIFilmPanel filmPanel;

    public UIElement properties;
    public UINestedEdit pickEdit;
    public UIToggle enabled;
    public UITextbox label;
    public UITextbox nameTag;
    public UIToggle shadow;
    public UITrackpad shadowSize;
    public UIToggle shadowFollow;
    public UITrackpad shadowOffsetX;
    public UITrackpad shadowOffsetY;
    public UITrackpad shadowOffsetZ;
    public UITrackpad looping;
    public UIToggle actor;
    public UIToggle fp;
    public UIToggle relative;
    public UITrackpad relativeOffsetX;
    public UITrackpad relativeOffsetY;
    public UITrackpad relativeOffsetZ;
    public UIToggle axesPreview;
    public UIButton pickAxesPreviewBone;

    private UIReplayList list;

    public UIReplayPropertiesPanel(UIFilmPanel filmPanel)
    {
        this.filmPanel = filmPanel;

        this.pickEdit = new UINestedEdit((editing) ->
        {
            if (this.list == null)
            {
                return;
            }

            Replay r = this.list.getSelectedReplayFirst();

            if (r != null)
            {
                this.list.openFormEditor(r.form, editing, this.pickEdit::setForm);
            }
        });
        this.pickEdit.pick.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PICK_FORM);
        this.pickEdit.edit.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_EDIT_FORM);
        this.enabled = new UIToggle(UIKeys.CAMERA_PANELS_ENABLED, (b) ->
        {
            this.edit((replay) -> replay.enabled.set(b.getValue()));
            filmPanel.getController().createEntities();
        });
        this.label = new UITextbox(1000, (s) -> this.edit((replay) -> replay.label.set(s)));
        this.label.textbox.setPlaceholder(UIKeys.FILM_REPLAY_LABEL);
        this.nameTag = new UITextbox(1000, (s) -> this.edit((replay) -> replay.nameTag.set(s)));
        this.nameTag.textbox.setPlaceholder(UIKeys.FILM_REPLAY_NAME_TAG);
        this.shadow = new UIToggle(UIKeys.CAMERA_PANELS_ENABLED, (b) -> this.edit((replay) -> replay.shadow.set(b.getValue())));
        this.shadowSize = new UITrackpad((v) -> this.edit((replay) -> replay.shadowSize.set(v.floatValue())));
        this.shadowSize.tooltip(UIKeys.FILM_REPLAY_SHADOW_SIZE);
        this.shadowFollow = new UIToggle(UIKeys.FILM_REPLAY_SHADOW_FOLLOW, (b) -> this.edit((replay) -> replay.shadowFollow.set(b.getValue())));
        this.shadowFollow.tooltip(UIKeys.FILM_REPLAY_SHADOW_FOLLOW_TOOLTIP);
        this.shadowOffsetX = new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.shadowOffset, (value) -> value.get().x = v)));
        this.shadowOffsetX.tooltip(UIKeys.FILM_REPLAY_SHADOW_OFFSET);
        this.shadowOffsetY = new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.shadowOffset, (value) -> value.get().y = v)));
        this.shadowOffsetY.tooltip(UIKeys.FILM_REPLAY_SHADOW_OFFSET);
        this.shadowOffsetZ = new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.shadowOffset, (value) -> value.get().z = v)));
        this.shadowOffsetZ.tooltip(UIKeys.FILM_REPLAY_SHADOW_OFFSET);
        this.looping = new UITrackpad((v) -> this.edit((replay) -> replay.looping.set(v.intValue())));
        this.looping.limit(0).integer().tooltip(UIKeys.FILM_REPLAY_LOOPING_TOOLTIP);
        this.actor = new UIToggle(UIKeys.FILM_REPLAY_ACTOR, (b) -> this.edit((replay) -> replay.actor.set(b.getValue())));
        this.actor.tooltip(UIKeys.FILM_REPLAY_ACTOR_TOOLTIP);
        this.fp = new UIToggle(UIKeys.FILM_REPLAY_FP, (b) ->
        {
            if (filmPanel.getData() != null)
            {
                for (Replay replay : filmPanel.getData().replays.getList())
                {
                    if (replay.fp.get())
                    {
                        replay.fp.set(false);
                    }
                }
            }

            Replay first = this.list == null ? null : this.list.getSelectedReplayFirst();

            if (first != null)
            {
                first.fp.set(b.getValue());
            }
        });
        this.relative = new UIToggle(UIKeys.CAMERA_PANELS_RELATIVE, (b) -> this.edit((replay) -> replay.relative.set(b.getValue())));
        this.relative.tooltip(UIKeys.FILM_REPLAY_RELATIVE_TOOLTIP);
        this.relativeOffsetX = new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.relativeOffset, (value) -> value.get().x = v)));
        this.relativeOffsetY = new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.relativeOffset, (value) -> value.get().y = v)));
        this.relativeOffsetZ = new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.relativeOffset, (value) -> value.get().z = v)));
        this.axesPreview = new UIToggle(UIKeys.FILM_REPLAY_AXES_PREVIEW, (b) -> this.edit((replay) -> replay.axesPreview.set(b.getValue())));
        this.pickAxesPreviewBone = new UIButton(UIKeys.FILM_REPLAY_PICK_AXES_PREVIEW, (b) ->
        {
            Replay replay = filmPanel.replayEditor.getReplay();

            if (replay != null && filmPanel.getData() != null)
            {
                UIAnchorKeyframeFactory.displayAttachments(filmPanel, filmPanel.getData().replays.getList().indexOf(replay), replay.axesPreviewBone.get(), (s) ->
                {
                    this.edit((r) -> r.axesPreviewBone.set(s));
                });
            }
        });

        UISection shadowSection = new UISection(UIKeys.FILM_REPLAY_SHADOW);

        shadowSection.fields.add(
            this.shadow, this.shadowSize,
            this.shadowFollow, UI.row(this.shadowOffsetX, this.shadowOffsetY, this.shadowOffsetZ)
        );

        UISection other = new UISection(UIKeys.FILM_REPLAY_SECTION_OTHER);

        other.fields.add(
            this.looping, this.actor, this.fp,
            this.relative, UI.row(this.relativeOffsetX, this.relativeOffsetY, this.relativeOffsetZ),
            this.axesPreview, this.pickAxesPreviewBone
        );

        this.properties = UI.scrollView(5, 6,
            this.pickEdit, this.enabled, this.label, this.nameTag,
            shadowSection.marginTop(UIConstants.SECTION_GAP),
            other.marginTop(UIConstants.SECTION_GAP)
        );
        this.refreshEditPanelOffset();

        this.add(this.properties);
        this.setReplay(null);
    }

    public void refreshEditPanelOffset()
    {
        int top = this.filmPanel.getEditPanelTopOffsetPx();
        this.properties.relative(this).x(0).y(0, top).w(1F).h(1F, -top);
        this.resize();
    }

    public void attachReplayList(UIReplayList list)
    {
        this.list = list;
    }

    public Consumer<Form> getFormConsumer()
    {
        return this.pickEdit::setForm;
    }

    private void edit(Consumer<Replay> consumer)
    {
        if (consumer != null && this.list != null)
        {
            for (Replay replay : this.list.getSelectedReplays())
            {
                consumer.accept(replay);
            }
        }
    }

    public void setReplay(Replay replay)
    {
        this.properties.setVisible(replay != null);

        if (replay != null)
        {
            this.pickEdit.setForm(replay.form.get());
            this.enabled.setValue(replay.enabled.get());
            this.label.setText(replay.label.get());
            this.nameTag.setText(replay.nameTag.get());
            this.shadow.setValue(replay.shadow.get());
            this.shadowSize.setValue(replay.shadowSize.get());
            this.shadowFollow.setValue(replay.shadowFollow.get());
            this.shadowOffsetX.setValue(replay.shadowOffset.get().x);
            this.shadowOffsetY.setValue(replay.shadowOffset.get().y);
            this.shadowOffsetZ.setValue(replay.shadowOffset.get().z);
            this.looping.setValue(replay.looping.get());
            this.actor.setValue(replay.actor.get());
            this.fp.setValue(replay.fp.get());
            this.relative.setValue(replay.relative.get());
            this.relativeOffsetX.setValue(replay.relativeOffset.get().x);
            this.relativeOffsetY.setValue(replay.relativeOffset.get().y);
            this.relativeOffsetZ.setValue(replay.relativeOffset.get().z);
            this.axesPreview.setValue(replay.axesPreview.get());
        }
    }
}
