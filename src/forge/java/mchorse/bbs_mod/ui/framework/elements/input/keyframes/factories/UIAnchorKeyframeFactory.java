package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.AnchorRebase;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.ReplayContextAction;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import org.joml.Vector3d;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.bones.UIBonePickerContextMenu;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.UIAnchorBinding;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class UIAnchorKeyframeFactory extends UIKeyframeFactory<Anchor>
{
    @Override public Transform getGizmoTransform(Anchor value) { return value.transform; }

    private UIToggle keepTransform;
    private UIIcon detach;
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
        List<Replay> replays = panel != null ? panel.getData().replays.getList() : java.util.Collections.emptyList();

        UISimpleContextMenu replayMenu = new UISimpleContextMenu();

        replayMenu.actions.scroll.scrollItemSize = 30;

        context.replaceContextMenu((menu) ->
        {
            menu.custom(replayMenu);
            menu.autoKeys();
            menu.action(Icons.CLOSE, UIKeys.GENERAL_NONE, Colors.NEGATIVE, () -> callback.accept(Anchor.NO_ATTACHMENT));

            for (Replay replay : replays)
            {
                String actor = replay.getId();
                IEntity entity = entities.get(actor);

                if (entity == null)
                {
                    continue;
                }

                int color = actor.equals(value) ? BBSSettings.primaryColor(0) : 0;

                menu.action(new ReplayContextAction(replay, IKey.raw(replay.getName()), () -> callback.accept(actor), color));
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

    public UIAnchorKeyframeFactory(UITrackValue<Anchor> track, UIKeyframes editor)
    {
        super(track, editor);

        this.keepTransform = new UIToggle(UIKeys.GENERIC_KEYFRAMES_ANCHOR_KEEP_TRANSFORM, BBSSettings.anchorKeepTransform.get(), (b) -> BBSSettings.anchorKeepTransform.set(b.getValue()));
        this.keepTransform.tooltip(UIKeys.GENERIC_KEYFRAMES_ANCHOR_KEEP_TRANSFORM_TOOLTIP);
        this.transform = new UIAnchorTransforms(this);
        this.transform.enableHotkeys();
        this.transform.setTransform(track.getValue().transform);

        this.detach = new UIIcon(Icons.CUT, button -> this.detach());
        this.detach.wh(16, 16);
        this.detach.tooltip(UIKeys.GENERIC_KEYFRAMES_ANCHOR_DETACH_TOOLTIP);

        this.scroll.add(new UIAnchorBinding(
            () -> this.track.getValue(), this::retarget, change -> this.track.edit(change), this.transform,
            editor instanceof UIFilmKeyframes ? UI.row(this.keepTransform, this.detach) : this.keepTransform));
    }

    private void detach()
    {
        UIFilmPanel panel = this.getPanel();
        Replay replay = panel == null ? null : panel.replayEditor.getReplay();

        if (replay == null || replay.relative.get()) return;

        this.editor.endValueGesture();
        Map<String, IEntity> entities = panel.getController().getEntities();
        IEntity entity = entities.get(replay.getId());
        float cursor = this.editor.getTick();
        float tick = replay.getTick((int) cursor) + cursor - (int) cursor;
        float transition = panel.getRunner().getTransition(0F);
        Anchor from = (Anchor) this.track.sheet.sample(tick);
        Vector3d position = new Vector3d();
        Anchor anchor = AnchorRebase.detach(entities, entity, replay, transition, from, position);

        if (anchor == null) return;

        BaseValue.edit(replay, IValueListener.FLAG_UNMERGEABLE, value ->
        {
            this.insertKeyframe(replay.keyframes.x, tick, position.x);
            this.insertKeyframe(replay.keyframes.y, tick, position.y);
            this.insertKeyframe(replay.keyframes.z, tick, position.z);
            this.insertKeyframe(this.track.sheet.channel, tick, anchor);
        });

        panel.setCursor(cursor);
        this.editor.triggerChange();
        this.requestUpdate();
    }

    private <T> void insertKeyframe(KeyframeChannel<T> channel, float tick, T value)
    {
        for (UIKeyframeSheet sheet : this.editor.getSheets())
        {
            if (sheet.channel != channel) continue;
            Keyframe<T> keyframe = sheet.ensureKeyframe(tick);
            keyframe.setValue(value);
            sheet.selection.add(keyframe);
            return;
        }

        channel.insertInheriting(tick, value);
    }

    @Override
    public void render(UIContext context)
    {
        UIFilmPanel panel = this.getPanel();
        Replay replay = panel == null ? null : panel.replayEditor.getReplay();
        this.detach.setEnabled(replay != null && !replay.relative.get());
        super.render(context);
    }

    /**
     * Change what this anchor hangs off. Every such change goes through here so
     * {@link BBSSettings#anchorKeepTransform} can compensate for it: the anchor's transform is
     * rebased onto the new target ({@link AnchorRebase}) so the form stays where it is instead of
     * being thrown into whichever frame the new target happens to sit in.
     *
     * <p>The rebase is measured on copies before the edit and written inside it, so the retarget
     * and its compensation are one undo step — an undo that put back the target but kept the
     * compensating transform would leave the form somewhere neither value ever meant.</p>
     */
    private void retarget(Consumer<Anchor> change)
    {
        Anchor from = this.track.getValue().copy();
        Anchor to = from.copy();

        change.accept(to);

        boolean rebased = BBSSettings.anchorKeepTransform.get() && this.rebase(from, to);

        this.track.edit(anchor ->
        {

            change.accept(anchor);

            if (rebased)
            {
                anchor.transform.copy(to.transform);
            }
        });

        if (rebased)
        {
            /* The fields hold the same transform object the rebase wrote through, but they were
             * filled from its old numbers. */
            this.transform.setTransform(this.track.getValue().transform);
        }
    }

    /**
     * Rebase against the selected replay that owns this track —
     * and the entity is what carries the live pose everything is measured from, which is why
     * there is nothing to compensate against when the replay isn't in the scene right now.
     */
    private boolean rebase(Anchor from, Anchor to)
    {
        UIFilmPanel panel = this.getPanel();
        Replay replay = panel == null ? null : panel.replayEditor.getReplay();

        if (replay == null)
        {
            return false;
        }

        Map<String, IEntity> entities = panel.getController().getEntities();

        return AnchorRebase.keepWorldTransform(entities, entities.get(replay.getId()), replay, 0F, from, to);
    }

    private UIFilmPanel getPanel()
    {
        return this.getParent(UIFilmPanel.class);
    }

    @Override
    public void update()
    {
        if (!this.transform.isUserEditing()) this.transform.setTransform(this.getDisplayValue().transform);
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
            apply(this.editor.track, consumer);
        }

        public static void apply(UITrackValue<Anchor> track, Consumer<Transform> consumer)
        {
            track.edit((selected) ->
            {
                Anchor anchor = (Anchor) selected;
                consumer.accept(anchor.transform);
            });
        }
    }
}
