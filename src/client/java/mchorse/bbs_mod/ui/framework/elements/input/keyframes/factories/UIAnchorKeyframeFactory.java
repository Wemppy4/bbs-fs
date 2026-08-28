package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.bones.UIBonePickerContextMenu;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class UIAnchorKeyframeFactory extends UIKeyframeFactory<Anchor>
{
    private UIButton actor;
    private UIButton attachment;
    private UIToggle translate;
    private UIToggle scale;
    public UIPropTransform transform;

    /**
     * Pick a replay by its stable id. The rows still show the replay's list position — that is
     * how the animator counts actors — but what the choice hands back (and what the data stores)
     * is the id, so the reference survives reordering.
     */
    public static void displayActors(UIContext context, Map<String, IEntity> entities, String value, Consumer<String> callback)
    {
        List<UIFilmPanel> children = context.menu.main.getChildren(UIFilmPanel.class);
        UIFilmPanel panel = children.isEmpty() ? null : children.get(0);
        List<Replay> replays = panel != null ? panel.getData().replays.getList() : List.of();

        context.replaceContextMenu((menu) ->
        {
            menu.action(Icons.CLOSE, UIKeys.GENERAL_NONE, Colors.NEGATIVE, () -> callback.accept(Anchor.NO_ATTACHMENT));

            for (int i = 0; i < replays.size(); i++)
            {
                Replay replay = replays.get(i);
                String actor = replay.getId();
                IEntity entity = entities.get(actor);

                if (entity == null)
                {
                    continue;
                }

                IKey label = IKey.constant(i + " - " + replay.getName());

                menu.action(Icons.CLOSE, label, actor.equals(value), () -> callback.accept(actor));
            }
        });
    }

    public static void displayAttachments(UIFilmPanel panel, String replayId, String value, Consumer<String> consumer)
    {
        IEntity entity = panel.getController().getEntities().get(replayId);

        if (entity == null || entity.getForm() == null)
        {
            return;
        }

        Form form = entity.getForm();
        Set<String> attachments = FormUtilsClient.getRenderer(form).collectMatrices(entity, 0F).keySet();

        if (attachments.isEmpty())
        {
            return;
        }

        /* The picker groups attachments by their form (body part tree) instead of the
         * old alphabetical strip that shuffled every part's bones together. */
        UIBonePickerContextMenu picker = new UIBonePickerContextMenu(consumer);

        picker.attachments(form, attachments).set(value);
        panel.getContext().replaceContextMenu(picker);
    }

    public UIAnchorKeyframeFactory(Keyframe<Anchor> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.actor = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ACTOR, (b) -> this.displayActors());
        this.attachment = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT, (b) ->
        {
            displayAttachments(this.getPanel(), this.keyframe.getValue().replay, this.keyframe.getValue().attachment, this::setAttachment);
        });
        this.translate = new UIToggle(UIKeys.TRANSFORMS_TRANSLATE, (b) -> this.setTranslate(b.getValue()));
        this.translate.setValue(keyframe.getValue().translate);
        this.scale = new UIToggle(UIKeys.TRANSFORMS_SCALE, (b) -> this.setScale(b.getValue()));
        this.scale.setValue(keyframe.getValue().scale);
        this.transform = new UIAnchorTransforms(this);
        this.transform.enableHotkeys();
        this.transform.setTransform(keyframe.getValue().transform);

        this.scroll.add(this.actor, this.attachment, this.translate, this.scale, this.transform);
    }

    private void displayActors()
    {
        UIFilmPanel panel = this.getPanel();

        displayActors(this.getContext(), panel.getController().getEntities(), this.keyframe.getValue().replay, this::setActor);
    }

    private void setActor(String actor)
    {
        BaseValue.edit(this.keyframe, (value) -> value.getValue().replay = actor);
    }

    private void setAttachment(String attachment)
    {
        BaseValue.edit(this.keyframe, (value) -> value.getValue().attachment = attachment);
    }

    private void setTranslate(boolean translate)
    {
        BaseValue.edit(this.keyframe, (value) -> value.getValue().translate = translate);
    }

    private void setScale(boolean scale)
    {
        BaseValue.edit(this.keyframe, (value) -> value.getValue().scale = scale);
    }

    private UIFilmPanel getPanel()
    {
        return this.getParent(UIFilmPanel.class);
    }

    public static class UIAnchorTransforms extends UIKeyframePropTransform
    {
        private final UIAnchorKeyframeFactory editor;

        public UIAnchorTransforms(UIAnchorKeyframeFactory editor)
        {
            this.editor = editor;
        }

        @Override
        protected UIKeyframes getKeyframes()
        {
            return this.editor.editor;
        }

        @Override
        protected void applyToSelection(Consumer<Transform> consumer)
        {
            apply(this.editor.editor, this.editor.keyframe, consumer);
        }

        @Override
        protected Transform getAutoKeyTransform(int tick)
        {
            UIKeyframeSheet sheet = this.editor.editor.getGraph().getSheet(this.editor.keyframe);
            Keyframe<?> target = sheet == null ? null : sheet.ensureKeyframe(tick);

            return target == null ? null : ((Anchor) target.getValue()).transform;
        }

        public static void apply(UIKeyframes editor, Keyframe<?> keyframe, Consumer<Transform> consumer)
        {
            UIReplaysEditorUtils.forEachSelectedKeyframe(editor, keyframe, (selected) ->
            {
                Anchor anchor = (Anchor) selected.getValue();

                selected.preNotify();
                consumer.accept(anchor.transform);
                selected.postNotify();
            });
        }
    }
}
