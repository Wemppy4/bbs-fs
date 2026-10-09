package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.forms.forms.SplineForm;

import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIconToggles;
import mchorse.bbs_mod.ui.framework.elements.input.UIDeltaPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shared Anchor content; the owner supplies writes, retarget compensation and transform editing. */
public class UIAnchorBinding extends UIElement
{
    private final Supplier<Anchor> read;
    private final Consumer<Consumer<Anchor>> retarget;
    private final UIButton actor;
    private final UIButton attachment;
    private final UIIcon pickActor;
    private final UIIcon pickAttachment;
    private final UIPropTransform transform;
    private final UIPathFields path;
    private final UIElement inheritRow;

    /** Solver binding: edits a world target, so the root form's keep-world-transform does not apply. */
    public static UISection section(IKey label, IKey tooltip, Supplier<Anchor> read, Consumer<Consumer<Anchor>> edit)
    {
        UISection section = new UISection(label);
        section.title.tooltip(tooltip);
        section.fields.add(new UIAnchorBinding(read, edit, edit, offset(read, edit), null));
        section.setExpanded(false);
        return section;
    }

    /** Ordinary Anchor and solver bindings use the same rows and viewport pickers. */
    public UIAnchorBinding(Supplier<Anchor> read, Consumer<Consumer<Anchor>> retarget, Consumer<Consumer<Anchor>> edit,
        UIPropTransform transform, UIElement keepTransform)
    {
        this.read = read;
        this.retarget = retarget;
        this.transform = transform;
        this.actor = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ACTOR, button ->
        {
            UIFilmPanel panel = this.panel();
            if (panel == null) return;
            panel.getController().picker.cancelTargetPick();
            UIAnchorKeyframeFactory.displayActors(this.getContext(), panel.getController().getEntities(), this.read.get().replay,
                id -> this.retarget.accept(anchor ->
                {
                    anchor.replay = id;
                    anchor.attachment = "";
                    anchor.spline = isSpline(panel, id, "");
                }));
        });
        this.attachment = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT, button ->
        {
            UIFilmPanel panel = this.panel();
            if (panel == null) return;
            panel.getController().picker.cancelTargetPick();
            UIAnchorKeyframeFactory.displayAttachments(panel, this.read.get().replay, this.read.get().attachment,
                id -> this.retarget.accept(anchor ->
                {
                    anchor.attachment = id;
                    anchor.spline = isSpline(panel, anchor.replay, id);
                }));
        });
        this.pickActor = this.picker(false, UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ACTOR);
        this.pickAttachment = this.picker(true, UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT);
        UIIconToggles inherit = new UIIconToggles(null)
            .add(Icons.ALL_DIRECTIONS, UIKeys.INHERIT_POSITION, () -> read.get().inheritPosition, value -> retarget.accept(anchor -> anchor.inheritPosition = value))
            .add(Icons.ORBIT, UIKeys.INHERIT_ROTATION, () -> read.get().inheritRotation, value -> retarget.accept(anchor -> anchor.inheritRotation = value))
            .add(Icons.SCALE, UIKeys.INHERIT_SCALE, () -> read.get().inheritScale, value -> retarget.accept(anchor -> anchor.inheritScale = value));
        this.column(UIConstants.MARGIN).vertical().stretch();
        this.add(UI.row(this.actor, this.pickActor), UI.row(this.attachment, this.pickAttachment));
        if (keepTransform != null) this.add(keepTransform);
        this.inheritRow = inherit.labelRow(UIKeys.INHERIT_TITLE);
        UIIconToggles pathInherit = new UIIconToggles(null)
            .add(Icons.ALL_DIRECTIONS, UIKeys.INHERIT_POSITION, () -> read.get().inheritPosition, value -> retarget.accept(anchor -> anchor.inheritPosition = value))
            .add(Icons.SCALE, UIKeys.INHERIT_SCALE, () -> read.get().inheritScale, value -> retarget.accept(anchor -> anchor.inheritScale = value));
        UIElement pathInheritRow = pathInherit.labelRow(UIKeys.INHERIT_TITLE);
        this.path = new UIPathFields(() -> read.get().progress, value -> edit.accept(anchor -> anchor.progress = (float) value),
            () -> !read.get().inheritRotation ? 0 : read.get().horizontal ? 2 : 1,
            mode -> retarget.accept(anchor -> { anchor.inheritRotation = mode != 0; anchor.horizontal = mode == 2; }));
        this.path.add(pathInheritRow);
        this.add(this.inheritRow, this.path, this.transform);
    }

    private static UIPropTransform offset(Supplier<Anchor> read, Consumer<Consumer<Anchor>> edit)
    {
        UIDeltaPropTransform transform = new UIDeltaPropTransform()
        {
            @Override protected void applyToSelection(Consumer<Transform> consumer)
            {
                edit.accept(anchor -> consumer.accept(anchor.transform));
            }
            @Override protected Transform getTargetTransform() { return read.get().transform; }
        };
        return transform;
    }

    private UIIcon picker(boolean bone, IKey tooltip)
    {
        UIIcon icon = new UIIcon(Icons.EYEDROPPER, button -> this.pickTarget(button, bone));
        icon.wh(16, 16);
        icon.tooltip(tooltip);
        icon.highlight(() -> this.panel() != null && this.panel().getController().picker.isPickingTarget(icon), Direction.BOTTOM);
        return icon;
    }

    private void pickTarget(UIIcon owner, boolean bone)
    {
        UIFilmPanel panel = this.panel();
        var replay = panel == null ? null : panel.replayEditor.getReplay();
        if (replay == null) return;
        panel.getController().picker.toggleTargetPick(owner, replay.getId(), (actor, pair) ->
        {
            String attachment = bone || pair.a instanceof SplineForm ? StringUtils.combinePaths(FormUtils.getPath(pair.a), pair.b) : Anchor.NO_ATTACHMENT;
            this.retarget.accept(anchor ->
            {
                anchor.replay = actor;
                anchor.attachment = attachment;
                anchor.spline = isSpline(panel, actor, attachment);
            });
        });
    }

    public static boolean isSpline(UIFilmPanel panel, String replay, String path)
    {
        var entity = panel == null ? null : panel.getController().getEntities().get(replay);
        Form target = entity == null || entity.getForm() == null ? null : FormUtils.getForm(entity.getForm(), path);
        return target instanceof SplineForm && FormUtils.getPath(target).equals(path);
    }

    private UIFilmPanel panel()
    {
        if (this.getContext() == null || this.getContext().menu == null) return null;
        var panels = this.getContext().menu.main.getChildren(UIFilmPanel.class);
        return panels.isEmpty() ? null : panels.get(0);
    }

    @Override
    public void render(UIContext context)
    {
        Anchor anchor = this.read.get();
        UIFilmPanel panel = this.panel();
        if (this.path.isVisible() != anchor.spline || this.inheritRow.isVisible() == anchor.spline)
        {
            this.path.setVisible(anchor.spline);
            this.inheritRow.setVisible(!anchor.spline);
            this.resize();
            if (this.getParent() != null) this.getParent().resize();
        }
        if (!this.transform.isUserEditing()) this.transform.setTransform(anchor.transform);
        this.actor.setEnabled(panel != null);
        this.attachment.setEnabled(panel != null && anchor.hasTarget());
        this.pickActor.setEnabled(panel != null);
        this.pickAttachment.setEnabled(panel != null);
        var replay = panel == null ? null : panel.getData().replays.getById(anchor.replay);
        this.actor.label = anchor.hasTarget() ? IKey.constant(replay == null ? anchor.replay : replay.getName()) : UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ACTOR;
        this.attachment.label = anchor.attachment.isEmpty() ? UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT : IKey.constant(anchor.attachment);
        if (anchor.spline && panel != null)
        {
            var entity = panel.getController().getEntities().get(anchor.replay);
            var target = entity == null ? null : FormUtils.getForm(entity.getForm(), anchor.attachment);
            this.attachment.label = target == null ? UIPathFields.key("missing") : IKey.constant(target.getDisplayName());
            this.path.setClosed(target instanceof SplineForm spline && spline.closed());
        }
        super.render(context);
    }
}
