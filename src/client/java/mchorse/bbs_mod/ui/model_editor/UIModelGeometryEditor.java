package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.bones.UIBonePickerContextMenu;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The model editor of the model panel: the model itself rather than its configuration. Its groups
 * as a tree — added, duplicated, removed, renamed, dragged among their siblings or into another
 * group — and for the picked one its rest, the pivot it turns about and the rotation it rests at,
 * on the viewport gizmo and in a transform editor. Edits land in the live model (the preview shows
 * them at once), go on the panel's undo stack as snapshots of the model ({@link ModelEditUndo}),
 * and the panel writes the file on save.
 *
 * <p>The transform editor works in radians on a stand-in transform; the group rests in degrees.
 * The stand-in is loaded from the group on every fill and pushed back into the group after every
 * edit — and every frame while the group is picked, since a gizmo drag's sampling nudges the
 * stand-in and re-evaluates the model through it.</p>
 *
 * <p>Several groups can be picked at once (ctrl / shift, as in every list here): the verbs — copy,
 * remove — then work on all of them as one undo step. The fields below the tree, the viewport's
 * marker and the gizmo belong to a single group, so they stand empty and disabled while more than
 * one is picked, rather than pretending to edit the first of them.</p>
 *
 * <p>Bound per fill: the tree keeps its pick across one by name, since a save reloads the model
 * and every group object with it — and so does every edit of the structure, which settles the
 * model again through the panel.</p>
 */
public class UIModelGeometryEditor extends UIElement
{
    /** How close a group's rest already is to the stand-in's numbers to be left alone — the round trip through radians isn't exact. */
    private static final float EPSILON = 1E-4F;

    private final UIModelEditorPanel modelPanel;

    private final UIScrollView page;
    private final UIModelGroupList groups;
    private final UISearchList<String> search;
    private final UIIcon dupe;
    private final UIIcon remove;
    private final UIIcon ikBones;
    private final UIElement body;
    private final UITextbox name;

    /** The picked group's rest, edited through the stand-in. */
    private final UIPropTransform transform;
    private final Transform anchor = new Transform();

    /** The model as it stood before the edit in progress, for the undo step it makes. */
    private MapType before;

    /** The live model the tree is bound to; null with no model open, or one that isn't cubic. */
    private Model model;

    public UIModelGeometryEditor(UIModelEditorPanel panel)
    {
        this.modelPanel = panel;

        this.groups = new UIModelGroupList((list) -> this.fillGroup(), () -> this.model)
            .onMove(this::moveGroup)
            .onReparent(this::reparentGroup);
        this.groups.context(this::fillGroupMenu);
        this.search = new UISearchList<>(this.groups);
        this.search.label(UIKeys.GENERAL_SEARCH);
        this.search.h(UIStringList.DEFAULT_HEIGHT * 8 - 8).expand();

        /* The verbs over the tree, the list idiom of the panel: add goes under the picked group */
        UIIcon add = new UIIcon(Icons.ADD, (b) -> this.addGroup());

        add.tooltip(UIKeys.MODEL_EDITOR_MODEL_GROUP_ADD);
        this.dupe = new UIIcon(Icons.DUPE, (b) -> this.duplicateGroups(this.pickedGroups()));
        this.dupe.tooltip(UIKeys.MODEL_EDITOR_MODEL_GROUP_DUPLICATE);
        this.remove = new UIIcon(Icons.REMOVE, (b) -> this.askRemoveGroups(this.pickedGroups()));
        this.remove.tooltip(UIKeys.MODEL_EDITOR_MODEL_GROUP_REMOVE);
        this.ikBones = new UIIcon(Icons.IK, (b) -> this.pickIKParent());
        this.ikBones.tooltip(UIKeys.MODEL_EDITOR_MODEL_GROUP_IK_BONES);

        /* The name is committed as a whole (enter, leaving the field): every keystroke would be a rename. */
        this.name = new UITextbox(64, this::renameGroup);
        this.name.delayedInput();

        /* A group's rest has no scale. G/R start a gesture on the picked group without touching a
         * handle, the way every transform editor of the panel does. */
        this.transform = new UIPropTransform().noScale();
        this.transform.callbacks(this::beginEdit, this::commitEdit, this::endEdit);
        this.transform.hotkeyDrag(() ->
        {
            ModelSlotTarget target = this.shownTarget();

            return target == null ? null : this.modelPanel.renderer.buildGizmoDrag(target);
        });
        this.transform.enableHotkeys(() -> this.shownTarget() != null);
        this.transform.translateAction(UIKeys.MODEL_EDITOR_MODEL_GROUP_CENTER_ANCHOR, this::centerAnchor);

        this.body = new UIElement();
        this.body.column(UIConstants.MARGIN).vertical().stretch();
        this.body.add(UI.labelRow(UIKeys.MODEL_EDITOR_MODEL_GROUP_NAME, this.name), this.transform);

        this.page = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING, UI.strip(add, this.dupe, this.remove, this.ikBones), this.search, this.body);
        this.page.full(this);
        this.add(this.page);
    }

    /**
     * The picked group, by name — what the viewport marks and the fields edit; null with nothing
     * picked, and null with several, which belong to no single group.
     */
    public String getSelected()
    {
        return this.model == null || this.groups.getCurrent().size() != 1 ? null : this.groups.getCurrentFirst();
    }

    /** What the viewport gizmo is on: the picked group's rest, through the stand-in. */
    public ModelSlotTarget shownTarget()
    {
        String id = this.getSelected();

        return id == null ? null : new ModelSlotTarget(id, ModelSlotKind.ANCHOR, this.transform, this::applyAnchor);
    }

    /** Bind to a model (null for none); the tree keeps its pick by name. */
    public void fill(ModelInstance instance)
    {
        this.model = instance != null && instance.getModel() instanceof Model model ? model : null;

        String picked = this.groups.getCurrentFirst();

        this.groups.fillBones(this.model, null);

        if (picked != null)
        {
            this.groups.setCurrent(picked);
        }

        this.fillGroup();
    }

    /** A bone clicked in the preview picks its group in the tree. */
    public boolean selectBone(String bone)
    {
        if (this.model == null || this.model.getGroup(bone) == null)
        {
            return false;
        }

        this.search.filter("", true);
        this.select(bone);

        return true;
    }

    private void select(String id)
    {
        this.groups.setCurrent(id);
        this.groups.reveal(id);
        this.fillGroup();
    }

    /** Pick several groups at once — what a verb on several leaves behind. */
    private void selectAll(List<String> ids)
    {
        this.groups.setCurrent(ids);

        if (!ids.isEmpty())
        {
            this.groups.reveal(ids.get(0));
        }

        this.fillGroup();
    }

    private ModelGroup picked()
    {
        String id = this.getSelected();

        return id == null ? null : this.model.getGroup(id);
    }

    /** Every picked group, in the order the tree lists them; empty with nothing picked. */
    private List<ModelGroup> pickedGroups()
    {
        List<ModelGroup> groups = new ArrayList<>();

        if (this.model == null)
        {
            return groups;
        }

        for (String id : this.groups.getCurrent())
        {
            ModelGroup group = this.model.getGroup(id);

            if (group != null)
            {
                groups.add(group);
            }
        }

        return groups;
    }

    /**
     * The picked group under the tree: its name and its rest. With nothing picked the fields stand
     * empty and disabled, so the page keeps its height and the scroll doesn't jump on every pick.
     */
    private void fillGroup()
    {
        ModelGroup group = this.picked();

        boolean any = !this.groups.getCurrent().isEmpty();

        this.name.setText(group == null ? "" : group.id);
        this.loadAnchor(group);
        this.transform.setTransform(this.anchor);
        UIUtils.setEnabledDeep(this.body, group != null);
        this.dupe.setEnabled(any);
        this.remove.setEnabled(any);
        this.ikBones.setEnabled(group != null);

        this.page.resize();
        this.page.scroll.clamp();
    }

    /**
     * The row's menu offers the verbs of the strip. A row outside the pick becomes the pick; a row
     * already in it leaves the pick alone, so a menu opened on several groups acts on all of them.
     */
    private void fillGroupMenu(ContextMenuManager menu)
    {
        String id = this.model == null ? null : this.groups.atCursor(this.getContext());
        ModelGroup group = id == null ? null : this.model.getGroup(id);

        if (group == null)
        {
            return;
        }

        if (!this.groups.getCurrent().contains(id))
        {
            this.select(id);
        }

        List<ModelGroup> picked = this.pickedGroups();

        menu.icon(MenuVerb.ADD, this::addGroup).label(UIKeys.MODEL_EDITOR_MODEL_GROUP_ADD);
        menu.action(Icons.DUPE, UIKeys.MODEL_EDITOR_MODEL_GROUP_DUPLICATE, () -> this.duplicateGroups(picked));
        menu.icon(MenuVerb.REMOVE, () -> this.askRemoveGroups(picked)).label(UIKeys.MODEL_EDITOR_MODEL_GROUP_REMOVE);

        if (this.getSelected() != null)
        {
            menu.action(Icons.IK, UIKeys.MODEL_EDITOR_MODEL_GROUP_IK_BONES, this::pickIKParent);
        }
    }

    /* The rest: the stand-in between the transform editor and the group */

    /** The stand-in takes the group's rest: the pivot as it is, the rotation in radians. */
    private void loadAnchor(ModelGroup group)
    {
        this.anchor.identity();

        if (group != null)
        {
            Vector3f rotate = group.initial.rotate;

            this.anchor.translate.set(group.initial.translate);
            this.anchor.rotate.set(MathUtils.toRad(rotate.x), MathUtils.toRad(rotate.y), MathUtils.toRad(rotate.z));
        }
    }

    /**
     * The group takes the stand-in's numbers. A rest already within a hair of them is left alone:
     * the round trip through radians isn't exact, and the file must not pick up the noise.
     */
    private void applyAnchor()
    {
        ModelGroup group = this.picked();

        if (group == null)
        {
            return;
        }

        Vector3f rotate = this.anchor.rotate;
        Vector3f degrees = new Vector3f(MathUtils.toDeg(rotate.x), MathUtils.toDeg(rotate.y), MathUtils.toDeg(rotate.z));

        if (!group.initial.translate.equals(this.anchor.translate, EPSILON))
        {
            group.initial.translate.set(this.anchor.translate);
        }

        if (!group.initial.rotate.equals(degrees, EPSILON))
        {
            group.initial.rotate.set(degrees);
        }
    }

    /**
     * The pivot to the middle of what the group draws, the translate row's icon. Only the pivot
     * moves: cube and mesh coordinates are absolute in the model, so the geometry stays exactly
     * where it stands and what changes is the point the bone turns about.
     *
     * <p>It goes through the stand-in rather than into the group, since the stand-in is what
     * {@link #render} writes back every frame — the group would take the new pivot and lose it
     * again on the very next one.</p>
     */
    private void centerAnchor()
    {
        ModelGroup group = this.picked();
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();

        if (group == null || !group.getGeometryBounds(min, max))
        {
            return;
        }

        Vector3f center = min.add(max).mul(0.5F);

        if (this.anchor.translate.equals(center, EPSILON))
        {
            return;
        }

        MapType before = this.snapshot();

        this.anchor.translate.set(center);
        this.applyAnchor();

        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, UIKeys.MODEL_EDITOR_MODEL_UNDO_CENTER_ANCHOR.format(group.id).get(), null, before, this.snapshot()));
        this.modelPanel.closeModelEdit();
    }

    /** The stand-in is the truth of the picked group's rest for as long as it's picked — see the class. */
    @Override
    public void render(UIContext context)
    {
        this.applyAnchor();

        super.render(context);
    }

    /* An edit of the rest: the model before it, the model after it, one step on the stack — the
     * steps of a single gesture merge, and its end keeps the next one apart. */

    private MapType snapshot()
    {
        return this.model.toData();
    }

    private void beginEdit()
    {
        if (this.model != null)
        {
            this.before = this.snapshot();
        }
    }

    private void commitEdit()
    {
        if (this.model == null || this.before == null)
        {
            return;
        }

        String id = this.getSelected();

        this.applyAnchor();
        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, UIKeys.MODEL_EDITOR_MODEL_UNDO_TRANSFORM.format(id).get(), "transform:" + id, this.before, this.snapshot()));
        this.before = null;
    }

    private void endEdit()
    {
        this.modelPanel.closeModelEdit();
    }

    /* The structure: groups added, copied, removed, renamed, moved — each one undo step, settled
     * and shown through the panel right after. */

    /** An edit of the model's structure: snapshot, change, push, settle. */
    private void edit(IKey label, Runnable mutation)
    {
        MapType before = this.snapshot();

        mutation.run();
        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, label.get(), null, before, this.snapshot()));
        this.modelPanel.modelStructureChanged();
    }

    /** Where a group sits among its siblings: its parent's children, or the model's roots. */
    private List<ModelGroup> siblings(ModelGroup group)
    {
        return group.parent == null ? this.model.topGroups : group.parent.children;
    }

    /** A name no group has, from {@code base}: the base itself, else with a number after it; {@code taken} holds the names given out before the model knows them. */
    private String uniqueName(String base, Set<String> taken)
    {
        String name = base;

        for (int i = 2; this.model.getGroup(name) != null || taken.contains(name); i++)
        {
            name = base + "_" + i;
        }

        taken.add(name);

        return name;
    }

    /** A new, empty group under the picked one (at its pivot), or at the root with nothing picked. */
    private void addGroup()
    {
        if (this.model == null)
        {
            return;
        }

        String first = this.groups.getCurrentFirst();
        ModelGroup parent = first == null ? null : this.model.getGroup(first);
        String name = this.uniqueName("group", new HashSet<>());

        this.edit(UIKeys.MODEL_EDITOR_MODEL_UNDO_ADD.format(name), () -> this.addBone(name, parent, parent == null ? null : parent.initial.translate));
        this.select(name);
    }

    /** A new, empty group under {@code parent} (at the root with none), resting at {@code pivot} — the model's origin for none. */
    private ModelGroup addBone(String name, ModelGroup parent, Vector3f pivot)
    {
        ModelGroup group = new ModelGroup(name);

        if (pivot != null)
        {
            group.initial.translate.set(pivot);
        }

        (parent == null ? this.model.topGroups : parent.children).add(group);

        return group;
    }

    /**
     * The IK shortcut: ask what the controls should hang off, then make the three bones an IK chain
     * wants around the picked one. Bones only — what actually solves lives on the FORM (its bones'
     * IK), which this panel doesn't hold, so the chain is still switched on there; the names are a
     * convention of the rigger's, nothing in BBS reads them.
     *
     * <p>The picked bone and everything under it are refused as the parent: a controller inside the
     * chain it drives is the one arrangement IK can't solve.</p>
     */
    private void pickIKParent()
    {
        String id = this.getSelected();

        if (id == null)
        {
            return;
        }

        Set<String> inside = new HashSet<>(this.model.getAllChildrenKeys(id));
        UIBonePickerContextMenu picker = new UIBonePickerContextMenu((parent) -> this.addIKBones(id, parent));

        inside.add(id);
        picker.bones(this.model, null).none().disabled(inside::contains);
        this.getContext().replaceContextMenu(picker);
    }

    /**
     * The tip inside the picked bone, the controller under {@code parentId} (the root for no bone) and
     * the pole inside the controller, as one undo step. All three rest at the picked bone's pivot — they
     * start on the joint they were asked about and are dragged out from there — and the controller
     * becomes the pick.
     */
    private void addIKBones(String id, String parentId)
    {
        ModelGroup group = this.model == null ? null : this.model.getGroup(id);

        if (group == null)
        {
            return;
        }

        ModelGroup parent = parentId == null || parentId.isEmpty() ? null : this.model.getGroup(parentId);
        Set<String> taken = new HashSet<>();
        String end = this.uniqueName(id + "_end", taken);
        String controller = this.uniqueName("controller_" + id, taken);
        String pole = this.uniqueName("pole_" + id, taken);
        Vector3f pivot = new Vector3f(group.initial.translate);

        this.edit(UIKeys.MODEL_EDITOR_MODEL_UNDO_IK_BONES.format(id), () ->
        {
            this.addBone(end, group, pivot);
            this.addBone(pole, this.addBone(controller, parent, pivot), pivot);
        });
        this.select(controller);
    }

    /**
     * A copy of every picked group and everything in it, each right after its original among the
     * siblings, as one undo step; the copies become the pick. A group inside another picked one is
     * left out: its copy already comes along inside that one.
     */
    private void duplicateGroups(List<ModelGroup> picked)
    {
        List<ModelGroup> groups = outermost(picked);

        if (groups.isEmpty())
        {
            return;
        }

        Set<String> taken = new HashSet<>();
        List<ModelGroup> copies = new ArrayList<>();
        List<String> names = new ArrayList<>();

        for (ModelGroup group : groups)
        {
            ModelGroup copy = this.copy(group, taken);

            copies.add(copy);
            names.add(copy.id);
        }

        this.edit(label(UIKeys.MODEL_EDITOR_MODEL_UNDO_DUPLICATE, UIKeys.MODEL_EDITOR_MODEL_UNDO_DUPLICATE_MANY, groups), () ->
        {
            for (int i = 0; i < groups.size(); i++)
            {
                ModelGroup group = groups.get(i);
                List<ModelGroup> siblings = this.siblings(group);

                siblings.add(siblings.indexOf(group) + 1, copies.get(i));
            }
        });
        this.selectAll(names);
    }

    /** A group and its subtree as new groups under new names, the cubes rebuilt from their data. */
    private ModelGroup copy(ModelGroup group, Set<String> taken)
    {
        ModelGroup copy = new ModelGroup(this.uniqueName(group.id, taken));

        copy.fromData(group.toData());

        for (ModelCube cube : copy.cubes)
        {
            cube.generateQuads(this.model.textureWidth, this.model.textureHeight);
        }

        for (ModelGroup child : group.children)
        {
            copy.children.add(this.copy(child, taken));
        }

        return copy;
    }

    /** Removing takes the subtrees and their cubes with them, so it's asked about first. */
    private void askRemoveGroups(List<ModelGroup> picked)
    {
        List<ModelGroup> groups = outermost(picked);

        if (groups.isEmpty())
        {
            return;
        }

        IKey question = groups.size() == 1
            ? UIKeys.MODEL_EDITOR_MODEL_GROUP_REMOVE_CONFIRM.format(groups.get(0).id)
            : UIKeys.MODEL_EDITOR_MODEL_GROUP_REMOVE_CONFIRM_MANY.format(groups.size());

        UIOverlay.addOverlay(this.getContext(), new UIConfirmOverlayPanel(
            UIKeys.MODEL_EDITOR_MODEL_GROUP_REMOVE,
            question,
            (confirm) ->
            {
                if (confirm)
                {
                    this.removeGroups(groups);
                }
            }
        ));
    }

    private void removeGroups(List<ModelGroup> groups)
    {
        this.groups.deselect();
        this.edit(label(UIKeys.MODEL_EDITOR_MODEL_UNDO_REMOVE, UIKeys.MODEL_EDITOR_MODEL_UNDO_REMOVE_MANY, groups), () ->
        {
            for (ModelGroup group : groups)
            {
                this.siblings(group).remove(group);
            }
        });
        this.fillGroup();
    }

    /**
     * The picked groups with the ones inside another of them left out: a verb on a group is a verb
     * on its whole subtree, so acting on an ancestor and its descendant both would do it twice.
     */
    private static List<ModelGroup> outermost(List<ModelGroup> groups)
    {
        List<ModelGroup> outer = new ArrayList<>();

        for (ModelGroup group : groups)
        {
            boolean inside = false;

            for (ModelGroup parent = group.parent; parent != null && !inside; parent = parent.parent)
            {
                inside = groups.contains(parent);
            }

            if (!inside)
            {
                outer.add(group);
            }
        }

        return outer;
    }

    /** The undo label for a verb on one group (its name) or on several (how many). */
    private static IKey label(IKey one, IKey many, List<ModelGroup> groups)
    {
        return groups.size() == 1 ? one.format(groups.get(0).id) : many.format(groups.size());
    }

    /**
     * Rename the picked group everywhere the model's folder knows it, as one undo step that carries
     * the config along. A name that's empty, unchanged or taken is refused, and the field goes back
     * to the name the group has.
     */
    private void renameGroup(String to)
    {
        ModelGroup group = this.picked();
        String from = group == null ? null : group.id;

        to = to.trim();

        if (from == null || to.isEmpty() || to.equals(from) || this.model.getGroup(to) != null)
        {
            this.name.setText(from == null ? "" : from);

            return;
        }

        MapType modelBefore = this.snapshot();
        MapType configBefore = this.modelPanel.getData().toData().asMap();

        this.modelPanel.renameBone(from, to);
        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, UIKeys.MODEL_EDITOR_MODEL_UNDO_RENAME.format(from, to).get(), null, modelBefore, this.snapshot(), configBefore, this.modelPanel.getData().toData().asMap(), from, to));
        this.modelPanel.modelStructureChanged();
        this.select(to);
    }

    /**
     * A group dropped between rows becomes the sibling right before {@code before} — at whatever
     * depth that row sits, since changing a parent is allowed here; {@code before} null sends it
     * to the end of the roots. A drop inside the group's own subtree is refused: it would take the
     * group out of the model with it.
     */
    private void moveGroup(String id, String before)
    {
        ModelGroup group = this.model == null ? null : this.model.getGroup(id);
        ModelGroup target = before == null || this.model == null ? null : this.model.getGroup(before);

        if (group == null || (before != null && target == null) || (target != null && inside(target, group)))
        {
            return;
        }

        this.edit(UIKeys.MODEL_EDITOR_MODEL_UNDO_MOVE.format(id), () ->
        {
            this.siblings(group).remove(group);

            if (target == null)
            {
                this.model.topGroups.add(group);
            }
            else
            {
                List<ModelGroup> destination = target.parent == null ? this.model.topGroups : target.parent.children;

                destination.add(destination.indexOf(target), group);
            }
        });
        this.select(id);
    }

    /** Whether {@code group} is {@code ancestor} itself or sits somewhere under it. */
    private static boolean inside(ModelGroup group, ModelGroup ancestor)
    {
        for (ModelGroup parent = group; parent != null; parent = parent.parent)
        {
            if (parent == ancestor)
            {
                return true;
            }
        }

        return false;
    }

    /** A group dropped onto another goes inside it, last. */
    private void reparentGroup(String id, String parentId)
    {
        ModelGroup group = this.model == null ? null : this.model.getGroup(id);
        ModelGroup parent = this.model == null ? null : this.model.getGroup(parentId);

        if (group == null || parent == null || group == parent)
        {
            return;
        }

        this.edit(UIKeys.MODEL_EDITOR_MODEL_UNDO_MOVE.format(id), () ->
        {
            this.siblings(group).remove(group);
            parent.children.add(group);
        });
        this.select(id);
    }
}
