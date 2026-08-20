package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import io.netty.util.collection.IntObjectMap;
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

    public static void displayActors(UIContext context, IntObjectMap<IEntity> entities, int value, Consumer<Integer> callback)
    {
        List<UIFilmPanel> children = context.menu.main.getChildren(UIFilmPanel.class);
        UIFilmPanel panel = children.isEmpty() ? null : children.get(0);
        List<Replay> replays = panel != null ? panel.getData().replays.getList() : null;

        /* The map is keyed by the replay's index in the film, and disabled replays
         * get no entity, so its size isn't the range of keys: iterating up to it
         * drops the last actor for every disabled one above it */
        int count = replays == null ? 0 : replays.size();

        for (Map.Entry<Integer, IEntity> entry : entities.entrySet())
        {
            count = Math.max(count, entry.getKey() + 1);
        }

        final int total = count;

        context.replaceContextMenu((menu) ->
        {
            menu.action(Icons.CLOSE, UIKeys.GENERAL_NONE, Colors.NEGATIVE, () -> callback.accept(-1));

            for (int i = 0; i < total; i++)
            {
                final int actor = i;
                IEntity entity = entities.get(i);

                if (entity == null)
                {
                    continue;
                }

                Replay replay = replays == null || i >= replays.size() ? null : replays.get(i);
                Form form = entity.getForm();
                String stringLabel = i + (replay != null ? " - " + replay.getName() : (form == null ? "" : " - " + form.getFormIdOrName()));
                IKey label = IKey.constant(stringLabel);

                menu.action(Icons.CLOSE, label, actor == value, () -> callback.accept(actor));
            }
        });
    }

    public static void displayAttachments(UIFilmPanel panel, int index, String value, Consumer<String> consumer)
    {
        IEntity entity = panel.getController().getEntities().get(index);

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

    private void setActor(int actor)
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
        protected void applyToSelection(Consumer<Transform> consumer)
        {
            apply(this.editor.editor, this.editor.keyframe, consumer);
        }

        @Override
        protected void applyDuringRecording(int tick, Consumer<Transform> consumer)
        {
            applyRecording(this.editor.editor, this.editor.keyframe, tick, consumer);
        }

        @Override
        protected Transform getRecordedTransform(int tick)
        {
            UIKeyframeSheet sheet = this.editor.editor.getGraph().getSheet(this.editor.keyframe);
            Keyframe<?> recorded = UIReplaysEditorUtils.ensureKeyframe(sheet, tick);

            return recorded == null ? null : ((Anchor) recorded.getValue()).transform;
        }

        public static void applyRecording(UIKeyframes editor, Keyframe<?> keyframe, int tick, Consumer<Transform> consumer)
        {
            UIReplaysEditorUtils.forEachRecordedKeyframe(editor, keyframe, tick, (recorded) ->
            {
                Anchor anchor = (Anchor) recorded.getValue();

                recorded.preNotify();
                consumer.accept(anchor.transform);
                recorded.postNotify();
            });
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
