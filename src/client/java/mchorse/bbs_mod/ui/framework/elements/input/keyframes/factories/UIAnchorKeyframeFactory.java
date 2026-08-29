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
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIconToggles;
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
    private UIIconToggles inherit;
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
        /* Which components of the target's frame the form rides, as one strip: the same three icons
         * the gizmo and the body part editor use for the same three ideas. The anchor's flags are
         * plain fields rather than values, so the cells are bound by getter and setter. */
        this.inherit = new UIIconToggles(null)
            .add(Icons.ALL_DIRECTIONS, UIKeys.INHERIT_POSITION, () -> this.keyframe.getValue().inheritPosition, (v) -> this.edit((anchor) -> anchor.inheritPosition = v))
            .add(Icons.ORBIT, UIKeys.INHERIT_ROTATION, () -> this.keyframe.getValue().inheritRotation, (v) -> this.edit((anchor) -> anchor.inheritRotation = v))
            .add(Icons.SCALE, UIKeys.INHERIT_SCALE, () -> this.keyframe.getValue().inheritScale, (v) -> this.edit((anchor) -> anchor.inheritScale = v));
        this.transform = new UIAnchorTransforms(this);
        this.transform.enableHotkeys();
        this.transform.setTransform(keyframe.getValue().transform);

        this.scroll.add(this.actor, this.attachment, this.inherit.labelRow(UIKeys.INHERIT_TITLE), this.transform);
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

    private void edit(Consumer<Anchor> consumer)
    {
        BaseValue.edit(this.keyframe, (keyframe) -> consumer.accept(keyframe.getValue()));
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
