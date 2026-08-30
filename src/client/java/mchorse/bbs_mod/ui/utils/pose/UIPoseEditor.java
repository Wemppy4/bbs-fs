package mchorse.bbs_mod.ui.utils.pose;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UIDeltaPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.BoneSelection;
import mchorse.bbs_mod.ui.utils.IBoneSelectionHost;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.context.MenuIcon;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.presets.UIDataContextMenu;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseManager;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class UIPoseEditor extends UIElement
{
    public static final int WIDE_WIDTH = (UIConstants.VALUE_WIDTH + 80) * 2;

    /** Fold state of the material section, kept across the rebuilds that undo/redo and re-opening
     *  the keyframe popup cause — the same trick the form panels and the texture painter use. */
    private static final Map<String, Boolean> SECTION_FOLDS = new HashMap<>();

    public UIBoneList groups;
    public UISliderTrackpad fix;
    public UIColor color;
    public UIColor overlay;
    public UISliderTrackpad lighting;
    public UIPropTransform transform;
    public UISection material;

    private String group = "";
    private boolean hasBones = true;
    private Pose pose;
    protected IModel model;
    protected Map<String, String> flippedParts;

    public UIPoseEditor()
    {
        this.groups = new UIBoneList(this::pickBones);
        this.groups.onFiltered = this::afterFilter;

        /* The bone list eats whatever room the panel has to spare, down through every layer between
         * it and the scroll view. The height below is what it asks for, i.e. its minimum: when the
         * fields underneath already fill the panel there is nothing to expand into and the list
         * stays exactly this tall. */
        this.groups.list.h(UIStringList.DEFAULT_HEIGHT * 8 - 8).expand();
        this.groups.expand();
        this.expand();
        this.groups.list.context(() ->
        {
            UIDataContextMenu menu = new UIDataContextMenu(PoseManager.INSTANCE, this.group, () -> this.pose.toData(), this::pastePose);

            menu.bar.register(new MenuIcon(Icons.CONVERT, UIKeys.POSE_CONTEXT_FLIP_POSE, MenuVerb.Slot.COMMON, this::flipPose).keepOpen());

            return menu;
        });
        this.fix = new UISliderTrackpad((v) -> this.applyFixToSelection(v.floatValue()));
        this.fix.limit(0D, 1D).increment(0.1D).values(0.1, 0.05D, 0.2D);
        this.fix.tooltip(UIKeys.POSE_CONTEXT_FIX_TOOLTIP);
        this.fix.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.applyChildren((p) -> this.setFix(p, (float) this.fix.getValue()));
            });
        });
        this.color = new UIColor((c) -> this.applyColorToSelection(c));
        this.color.withAlpha();
        this.color.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.applyChildren((p) -> this.setColor(p, this.color.picker.color.getARGBColor()));
            });
        });
        this.overlay = new UIColor((c) -> this.applyOverlayToSelection(c));
        this.overlay.withAlpha();
        this.overlay.tooltip(UIKeys.FORMS_EDITORS_MATERIAL_OVERLAY_TOOLTIP);
        this.overlay.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.applyChildren((p) -> this.setOverlay(p, this.overlay.picker.color.getARGBColor()));
            });
        });
        this.lighting = new UISliderTrackpad((v) -> this.applyLightingToSelection(v.floatValue()));
        this.lighting.limit(0D, 1D);
        this.lighting.tooltip(UIKeys.FORMS_EDITORS_MATERIAL_GLOW_TOOLTIP);
        this.lighting.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.applyChildren((p) -> this.setLighting(p, (float) this.lighting.getValue()));
            });
        });
        this.transform = this.createTransformEditor();
        this.transform.setModel();

        this.keys().register(Keys.TRANSFORMATIONS_TOGGLE_FIX, this::toggleFix).category(UIKeys.TRANSFORMS_KEYS_CATEGORY);

        this.column().vertical().stretch();
        this.buildLayout(false);
    }

    /**
     * A bone's material fields as one folded section. Colour, overlay and glow are the bone's
     * material rather than its pose, so they fold away under the transform instead of pushing it
     * down the panel.
     *
     * <p>Static because the pose-transform keyframe panel is these same fields without a bone list:
     * it builds its section from here, so the two cannot disagree about the rows, and the fold
     * state is shared — closing the section in one place closes it in the other.</p>
     */
    public static UISection materialSection(UIColor color, UIColor overlay, UISliderTrackpad lighting)
    {
        UISection section = new UISection(UIKeys.FORMS_EDITORS_MATERIAL).remember(SECTION_FOLDS, "material", false);

        section.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_COLOR, color),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_OVERLAY, overlay),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_GLOW, lighting)
        );

        return section;
    }

    /**
     * Lays the bone list and the per-bone fields out, either stacked or — when {@code wide} — with
     * the fields to the left of the list. This widget owns its own layout: the film's pose keyframe
     * editor used to tear it down and re-assemble it by hand, which is how the two arrangements
     * drifted apart (the keyframe one lost the overlay field and the field labels entirely).
     *
     * <p>The rows are declared once above the branch, so the two arrangements cannot disagree about
     * which fields exist or in what order.</p>
     */
    public void buildLayout(boolean wide)
    {
        this.removeAll();

        this.material = materialSection(this.color, this.overlay, this.lighting);
        this.material.setVisible(this.hasBones);

        /* Every row rides the same labelRow grid, so the trackpads and colour swatches pin to one
         * divider column and the names never truncate. */
        UIElement[] fields = {
            UI.labelRow(UIKeys.POSE_CONTEXT_FIX, this.fix),
            this.transform,
            this.material
        };

        if (wide)
        {
            /* The row and the list's column are marked too: the leftover height only reaches the
             * list through the layers that asked for it. */
            this.add(UI.row(UI.column(fields), UI.column(this.groups).expand()).expand());
        }
        else
        {
            /* Flat, not wrapped in a column of its own: this is the arrangement the form editor
             * has always had, and its sections below rely on these rows being direct children. */
            this.add(this.groups);
            this.add(fields);
        }
    }

    private void applyChildren(Consumer<PoseTransform> consumer)
    {
        if (this.model == null)
        {
            return;
        }

        /* Snapshot: getCurrent() is a shared, reused buffer and the consumer can
         * re-enter it (film setFix notifies the keyframe) — see forEachSelectedPose. */
        for (String bone : new ArrayList<>(this.groups.list.getCurrent()))
        {
            Collection<String> keys = this.model.getAllChildrenKeys(bone);

            for (String key : keys)
            {
                consumer.accept(this.pose.getOrCreate(key));
            }
        }
    }

    public Pose getPose()
    {
        return this.pose;
    }

    /**
     * First selected bone name (for keyframe paths and legacy callers).
     */
    public String getGroup()
    {
        return this.groups.list.getCurrentFirst();
    }

    protected void pastePose(MapType data)
    {
        this.restoreSelectionAfter(() -> this.pose.fromData(data));
    }

    protected void flipPose()
    {
        this.restoreSelectionAfter(() -> this.pose.flip(this.flippedParts));
    }

    private void restoreSelectionAfter(Runnable action)
    {
        List<String> current = new ArrayList<>(this.groups.list.getCurrent());

        action.run();
        this.groups.list.setCurrent(current);
        this.pickBones(this.groups.list.getCurrent());
    }

    public void setPose(Pose pose, String group)
    {
        this.pose = pose;
        this.group = group;
    }

    public void fillGroups(Collection<String> groups, boolean reset)
    {
        this.model = null;
        this.flippedParts = null;

        this.groups.list.setHierarchy(null, null);
        this.fillInGroups(groups, reset, true);
    }

    public void fillGroups(IModel model, Map<String, String> flippedParts, boolean reset)
    {
        this.fillGroups(model, flippedParts, reset, null);
    }

    public void fillGroups(IModel model, Map<String, String> flippedParts, boolean reset, Collection<String> disabledBones)
    {
        this.model = model;
        this.flippedParts = flippedParts;

        /* The hierarchy metadata rides along so the list renders as a tree; the list
         * contents themselves keep being refilled by UIBoneList's search filter. */
        this.groups.list.setHierarchy(model, (bone) -> PoseBones.isHidden(disabledBones, bone));

        if (model == null)
        {
            this.fillInGroups(Collections.emptyList(), reset, false);
            return;
        }

        List<String> bones = new ArrayList<>(model.getGroupKeysInHierarchyOrder());

        bones.removeIf((bone) -> PoseBones.isHidden(disabledBones, bone));
        this.fillInGroups(bones, reset, false);
    }

    private void fillInGroups(Collection<String> groups, boolean reset, boolean sort)
    {
        this.groups.setSource(groups, sort);
        this.groups.filter(reset);
    }

    private final BoneSelection detachedSelection = new BoneSelection();

    /**
     * The bone the animator is working on, owned by the editor this widget is shown in. Resolved
     * through the widget tree on every use rather than captured once: the panels that ask are
     * rebuilt constantly, and a captured host would outlive the tree it came from.
     *
     * <p>Falls back to a selection of its own when there is no host above — a detached widget then
     * simply remembers its own bone instead of writing into a value shared by the whole mod.</p>
     */
    protected BoneSelection boneSelection()
    {
        IBoneSelectionHost host = this.selectionAnchor().getAncestor(IBoneSelectionHost.class);

        return host == null ? this.detachedSelection : host.getBoneSelection();
    }

    /**
     * Where to start looking for the host. This widget itself, unless a subclass is shown outside
     * of its editor's tree — the keyframe popup is, so it anchors on the timeline instead.
     */
    protected UIElement selectionAnchor()
    {
        return this;
    }

    /**
     * Runs after each filter pass (see {@link UIBoneList#filter}): toggle the dependent editors by
     * whether any bones exist, then re-select a bone &mdash; the first on a reset, otherwise the
     * last edited one if it survived the filter.
     */
    private void afterFilter(boolean reset)
    {
        boolean hasBones = this.groups.hasBones();

        /* Remembered so a relayout (the keyframe popup crossing WIDE_WIDTH) rebuilds the material
         * section in the state it was in, rather than bringing it back on a boneless model. */
        this.hasBones = hasBones;

        this.fix.setVisible(hasBones);
        this.transform.setVisible(hasBones);
        this.material.setVisible(hasBones);

        List<String> list = this.groups.list.getList();
        int i = Math.max(reset ? 0 : list.indexOf(this.boneSelection().get()), 0);

        this.groups.list.setCurrentScroll(CollectionUtils.getSafe(list, i));
        this.pickBones(this.groups.list.getCurrent());
    }

    public void selectBone(String bone)
    {
        this.selectBone(bone, false);
    }

    /** Whether this pose editor lists the given bone (so a viewport pick can target it). */
    public boolean hasBone(String bone)
    {
        return bone != null && !bone.isEmpty() && this.groups.list.getList().contains(bone);
    }

    /**
     * Select a bone, or — when {@code additive} — toggle it in the multi-selection,
     * so the viewport's Ctrl+click builds the same multi-bone selection the bone list
     * does. Never leaves the selection empty (toggling off the last bone keeps it).
     */
    public void selectBone(String bone, boolean additive)
    {
        this.boneSelection().set(bone);

        if (additive)
        {
            int index = this.groups.list.getList().indexOf(bone);

            if (index != -1)
            {
                this.groups.list.toggleIndex(index);

                if (this.groups.list.getCurrent().isEmpty())
                {
                    this.groups.list.toggleIndex(index);
                }
            }
        }
        else
        {
            this.groups.list.setCurrentScroll(bone);
        }

        this.pickBones(this.groups.list.getCurrent());
    }

    /**
     * Restore a previous multi-bone selection. Undo/redo rebuilds the form panel from
     * scratch (which resets the selection to the first bone), so the host re-applies the
     * remembered selection afterwards.
     */
    public void restoreSelection(List<String> bones)
    {
        if (bones == null || bones.isEmpty())
        {
            return;
        }

        this.groups.list.setCurrent(bones);
        this.pickBones(this.groups.list.getCurrent());
    }

    /* Subclass overridable methods */

    protected UIPropTransform createTransformEditor()
    {
        return new UIPosePropTransform();
    }

    /**
     * Applies each transform edit as a per-channel delta to every selected bone,
     * so a multi-selection keeps each bone's own pose instead of collapsing onto
     * the primary's. See {@link UIDeltaPropTransform}.
     */
    private class UIPosePropTransform extends UIDeltaPropTransform
    {
        UIPosePropTransform()
        {
            this.enableHotkeys();
        }

        @Override
        protected boolean supportsMirror()
        {
            return true;
        }

        @Override
        protected void applyToSelection(Consumer<Transform> consumer)
        {
            for (Map.Entry<String, BoneEdit> target : UIPoseEditor.this.resolveBoneEdits(this.isMirrorEdit(), this.isAlternateInvert()).entrySet())
            {
                UIPoseEditor.this.applyToBone(target.getValue(), UIPoseEditor.this.pose.getOrCreate(target.getKey()), consumer);
            }
        }

        @Override
        protected void reset()
        {
            this.preCallback();
            this.applyToTarget((t) ->
            {
                t.translate.set(0F, 0F, 0F);
                t.scale.set(1F, 1F, 1F);
                t.resetRotation();
            });
            this.postCallback();

            this.syncTargetTransform();
        }

    }

    protected void pickBone(String bone)
    {
        if (bone == null || bone.isEmpty())
        {
            this.pickBones(Collections.emptyList());
            return;
        }

        this.pickBones(Collections.singletonList(bone));
    }

    protected void pickBones(List<String> bones)
    {
        if (bones == null || bones.isEmpty())
        {
            this.boneSelection().set("");
            this.fix.setValue(0F);
            this.color.setColor(Colors.WHITE);
            this.overlay.setColor(0x00ffffff);
            this.lighting.setValue(0F);
            this.transform.setTransform(null);

            return;
        }

        String primary = bones.get(0);

        this.boneSelection().set(primary);

        PoseTransform poseTransform = this.pose.getOrCreate(primary);

        this.fix.setValue(poseTransform.fix);
        this.color.setColor(poseTransform.color.getARGBColor());
        this.overlay.setColor(poseTransform.overlay.getARGBColor());
        this.lighting.setValue(poseTransform.lighting);
        this.transform.setTransform(poseTransform);
    }

    private void forEachSelectedPose(Consumer<? super PoseTransform> consumer)
    {
        /* Snapshot the selection: getCurrent() hands back a shared, reused buffer,
         * and the consumer can re-enter it (the film editor's setFix notifies the
         * keyframe, which re-reads the selection) — iterating the live buffer would
         * then throw ConcurrentModificationException. Same guard as flipPose/pastePose. */
        for (String bone : new ArrayList<>(this.groups.list.getCurrent()))
        {
            consumer.accept(this.pose.getOrCreate(bone));
        }
    }

    /** How a single bone should receive an edit: reflected onto its left/right
     *  counterpart ({@link #mirror}) and/or with its rotation flipped ({@link #invert}). */
    public static class BoneEdit
    {
        public final boolean mirror;
        public final boolean invert;

        public BoneEdit(boolean mirror, boolean invert)
        {
            this.mirror = mirror;
            this.invert = invert;
        }
    }

    /**
     * Bones an edit should touch and how. Selected bones are drivers; with
     * {@code invert} on, every second selected bone (2nd, 4th, ... in selection
     * order) has its rotation flipped. With {@code mirror} on, each driver's
     * left/right counterpart is added reflected across the model's symmetry
     * &mdash; even when unselected &mdash; so editing one bone mirrors onto its
     * pair live. A counterpart that is itself selected stays a driver (never
     * double-applied). Shared by the model panel and film pose editors.
     */
    public Map<String, BoneEdit> resolveBoneEdits(boolean mirror, boolean invert)
    {
        Map<String, BoneEdit> edits = new LinkedHashMap<>();
        List<String> selected = this.groups.list.getCurrent();

        for (int i = 0; i < selected.size(); i++)
        {
            edits.put(selected.get(i), new BoneEdit(false, invert && i % 2 == 1));
        }

        if (mirror)
        {
            for (String bone : new ArrayList<>(edits.keySet()))
            {
                String partner = this.mirrorPartner(bone);

                if (partner != null && !edits.containsKey(partner))
                {
                    edits.put(partner, new BoneEdit(true, false));
                }
            }
        }

        return edits;
    }

    /**
     * The opposite-side counterpart of a bone (the model's flip map first, then
     * the left/right name patterns), or null when it has none or the resolved
     * name isn't an actual bone.
     */
    private String mirrorPartner(String bone)
    {
        String partner = null;

        if (this.flippedParts != null && !this.flippedParts.isEmpty())
        {
            partner = this.flippedParts.get(bone);

            if (partner == null)
            {
                for (Map.Entry<String, String> entry : this.flippedParts.entrySet())
                {
                    if (bone.equals(entry.getValue()))
                    {
                        partner = entry.getKey();

                        break;
                    }
                }
            }
        }

        if (partner == null)
        {
            String mirrored = Pose.getMirrorName(bone);

            partner = mirrored.equals(bone) ? null : mirrored;
        }

        return partner != null && this.groups.list.getList().contains(partner) ? partner : null;
    }

    /**
     * Applies the edit to one bone: reflecting it across the model's symmetry when
     * {@code edit.mirror} (the shared {@link Transform#mirrorX} convention, same as
     * {@link Pose#flip}), and/or flipping its rotation when {@code edit.invert}.
     * Both are involutions wrapped around the write, so whatever the edit does to
     * that channel is reflected/inverted.
     */
    public void applyToBone(BoneEdit edit, PoseTransform pt, Consumer<Transform> consumer)
    {
        if (edit.mirror)
        {
            pt.mirrorX();
        }

        if (edit.invert)
        {
            negateRotation(pt);
        }

        consumer.accept(pt);

        if (edit.invert)
        {
            negateRotation(pt);
        }

        if (edit.mirror)
        {
            pt.mirrorX();
        }
    }

    private static void negateRotation(Transform transform)
    {
        /* The invert convention is per-CHANNEL negation (not the true inverse
         * rotation — Rz(-z)·Ry(-y)·Rx(-x) ≠ R⁻¹ on multi-axis poses), so a
         * quaternion bone must run the same convention on its decomposed
         * angles; quat.conjugate() would be a different involution and the
         * inverted partners would diverge from their euler siblings. */
        if (transform.rotationMode == Transform.RotationMode.QUATERNION)
        {
            Vector3f euler = Matrices.toEulerZYXRadians(transform.quat, new Vector3f());

            transform.quat.set(Matrices.toQuaternionZYXRadians(-euler.x, -euler.y, -euler.z));
        }
        else
        {
            transform.rotate.mul(-1F, -1F, -1F);
        }
    }

    private void applyFixToSelection(float value)
    {
        this.forEachSelectedPose((pt) -> this.setFix(pt, value));
        this.fix.setValue(value);
    }

    private void applyColorToSelection(int argb)
    {
        this.forEachSelectedPose((pt) -> this.setColor(pt, argb));
        this.color.setColor(argb);
    }

    private void applyOverlayToSelection(int argb)
    {
        this.forEachSelectedPose((pt) -> this.setOverlay(pt, argb));
        this.overlay.setColor(argb);
    }

    private void applyLightingToSelection(float value)
    {
        this.forEachSelectedPose((pt) -> this.setLighting(pt, value));
        this.lighting.setValue(value);
    }

    private void toggleFix()
    {
        if (this.groups.list.getCurrent().isEmpty())
        {
            return;
        }

        float next = this.fix.getValue() >= 0.5F ? 0F : 1F;

        this.applyFixToSelection(next);
    }

    protected void setFix(PoseTransform transform, float value)
    {
        transform.fix = value;
    }

    protected void setColor(PoseTransform transform, int value)
    {
        transform.color.set(value);
    }

    protected void setOverlay(PoseTransform poseTransform, int value)
    {
        poseTransform.overlay.set(value);
    }

    protected void setLighting(PoseTransform poseTransform, float value)
    {
        poseTransform.lighting = value;
    }
}
