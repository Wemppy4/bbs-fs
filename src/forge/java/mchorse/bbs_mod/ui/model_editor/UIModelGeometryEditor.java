package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformGesture;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.bones.UIBonePickerContextMenu;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The model editor of the model panel: the model itself rather than its configuration. Its groups
 * and their cubes as one tree ({@link UIModelTree}); rows of either kind added, duplicated,
 * removed, renamed and dragged where they belong — and for the picked one its rest,
 * the pivot it turns about and the rotation it rests at, on the viewport gizmo and in a transform
 * editor. For a picked cube, its numbers: where it starts, how big it is, the pivot it turns
 * about, its rotation and its inflate. Edits land in the live model (the preview shows them at
 * once), go on the panel's undo stack as snapshots of the model ({@link ModelEditUndo}), and the
 * panel writes the file on save.
 *
 * <p>The transform editors work in radians on stand-in transforms; the model rests in degrees.
 * A stand-in is loaded from the group or the cube on every fill and pushed back after every edit
 * — and every frame while it's picked, since a gizmo drag's sampling nudges the stand-in and
 * re-evaluates the model through it. The cube's is read as a CHANGE from where the edit began
 * ({@link ModelCubeEdit}), so a move of the cube's corner takes its pivot along (the cube moves as
 * a whole), the pivot on its own row moves the point alone, and every other cube of the pick takes
 * the same change.</p>
 *
 * <p>A group has one point — the pivot it turns about, which is also where it stands in the model
 * — and moving it means one of two things. The gizmo MOVES THE GROUP: its cubes and its whole
 * subtree's go along, since a cubic model's cubes are absolute and the hierarchy passes down
 * rotations alone. The row under the tree moves THE POINT ALONE, leaving the geometry where it
 * stands, so the group turns about somewhere else. The sphere over the tree puts the gizmo on
 * the point too, for when that is what's wanted. A cube's rows read the same way round: its
 * position row moves the cube as a whole, the pivot row below moves its point alone. Its gizmo
 * stands on that pivot and turns as the cube does — the rings turn the cube about it, and the scale
 * handles grow it about the same point. A group's rest has no scale of its own, so a group's scale
 * handles grow its whole branch about the group's pivot instead ({@link ModelBranchScale}).</p>
 *
 * <p>Several rows can be picked at once (ctrl / shift, as in every list here), and a picked group
 * takes the cubes of its whole branch along ({@link #pickedCubes}): the verbs — copy, remove —
 * then work on the whole pick as one undo step, and so does moving it. The fields and the gizmo
 * sit on the FIRST of the pick, and what it is moved by is given to every other picked row, in the
 * same terms — geometry travels with geometry, points with points, keeping the distances between
 * them. A cube's other rows — its size, rotation, pivot and inflate — change every cube of the pick
 * by the same amount, each from its own numbers, the way the pose editor edits a pick of bones; a
 * paste or a reset puts the same numbers on all of them. On a pick of cubes alone the gizmo scales
 * and turns as well, every cube about its own pivot ({@link ModelCubeEdit}). A name is one row's
 * own, so it goes dead while more than one is picked. The picked cubes light up in the viewport
 * ({@link #outlines}).</p>
 *
 * <p>Changed numbers leave their groups' quads and the model's bake behind; they are rebuilt once
 * the numbers have settled ({@link #settle}) — after a typed edit at once, after a gesture at its
 * end.</p>
 *
 * <p>Bound per fill: the tree keeps its pick across one by address, since a save reloads the
 * model and every group object with it — and so does every edit of the structure, which settles
 * the model again through the panel.</p>
 */
public class UIModelGeometryEditor extends UIElement
{
    /** How close a rest already is to the stand-in's numbers to be left alone — the round trip through radians isn't exact. */
    private static final float EPSILON = 1E-4F;

    /** How big a new cube is, in the model's pixels — a quarter of a block, centred on its group's pivot. */
    private static final float NEW_CUBE_SIDE = 4F;

    /** What one picked group can take: moving, turning, and scaling — which scales its whole branch about its pivot. */
    private static final Gizmo.HandleMask ANCHOR_MASK = Gizmo.HandleMask.of(
        EnumSet.of(Gizmo.Op.MOVE, Gizmo.Op.SCREEN, Gizmo.Op.ROTATE, Gizmo.Op.VIEW, Gizmo.Op.TRACKBALL, Gizmo.Op.SCALE, Gizmo.Op.SCALE_ALL),
        EnumSet.allOf(Axis.class)
    );

    /**
     * What a pick of several with a group in it can take: moving only. One step given to every
     * picked row is exact, whether it moves their shapes or the points they turn about; a rest
     * rotation is each bone's own, and a ring dragged over a pick of them has no one answer — so
     * those handles aren't offered rather than quietly turning only the first. A pick of cubes alone
     * takes every handle: each cube scales and turns about its own pivot.
     */
    private static final Gizmo.HandleMask MANY_MASK = Gizmo.HandleMask.of(
        EnumSet.of(Gizmo.Op.MOVE, Gizmo.Op.SCREEN),
        EnumSet.noneOf(Axis.class)
    );

    private final UIModelEditorPanel modelPanel;

    private final UIScrollView page;
    private final UIModelTree tree;
    private final UISearchList<ModelNode> search;
    private final UIIcon addCube;
    private final UIIcon ikBones;
    private final UIElement body;
    private final UITextbox name;

    /** The picked group's rest, edited through the stand-in. */
    private final UIPropTransform transform;
    private final Transform anchor = new Transform();

    /** The group stand-in as it was last carried into the model, so only its own changes are carried. */
    private final Transform anchorApplied = new Transform();

    /**
     * Set while a change comes from one of the rows' own pads — dragged, typed, scrolled or stepped —
     * rather than from the gizmo, its hotkeys or its probes, which reach the same stand-ins; and
     * whether that pad was typed into. The rows and the gizmo mean different things by the same
     * number: the position row moves the picked cubes, the gizmo the whole pick.
     */
    private boolean rowEdit;
    private boolean typedEdit;

    /** The picked cube's position, size and rotation, edited through a stand-in of their own. */
    private final UIPropTransform cubeTransform;
    private final Transform standin = new Transform();

    /**
     * The stand-in as it was last carried into the model: what a picked group's move is stepped
     * from, and what an edit of the cubes measures its change from when it begins.
     */
    private final Transform applied = new Transform();

    /** The edit of the picked cubes in progress; null while nothing drives their numbers. */
    private ModelCubeEdit cubeEdit;

    /** The scale of the picked group's branch in progress; null while the gizmo isn't scaling one. */
    private ModelBranchScale branchScale;

    /** The cube's pivot and inflate, on rows of their own; the pivot row's icon centres the pivot on the cube. */
    private final UIElement pivotRow;
    private final UIIcon pivotIcon;
    private final UITrackpad[] pivotFields = new UITrackpad[3];
    private final UITrackpad inflate;
    private final UIElement inflateRow;

    /** The picked cube's unwrap and the texture it sits on — the pane of its own, left of the preview. */
    private final UIModelCubeUV cubeUV;

    /** Groups whose cubes changed numbers, waiting for their quads and their bake to be rebuilt. */
    private final Set<ModelGroup> dirty = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Whether changed numbers were baked group by group and the model as a whole still owes a settling. */
    private boolean unsettled;

    /** The model as it stood before the edit in progress, for the undo step it makes. */
    private MapType before;

    /** The live model the tree is bound to; null with no model open, or one that isn't cubic. */
    private Model model;

    /** The instance behind the model, whose bake changed numbers invalidate. */
    private ModelInstance instance;

    /**
     * Whether THE GIZMO moves the pivot alone — the sphere over the tree, or its key. The rows under the
     * tree say what they move on their own and pay it no mind. Kept across picks and across opening
     * the panel, the way the open editor is: it's a mode of working, not a property of a group.
     */
    private static boolean pivotOnly;

    public UIModelGeometryEditor(UIModelEditorPanel panel)
    {
        this.modelPanel = panel;

        this.tree = new UIModelTree((list) -> this.fillSelection())
            .onReorder(this::moveNode)
            .onDrop(this::dropNode);
        this.tree.context(this::fillNodeMenu);
        this.search = new UISearchList<>(this.tree);
        this.search.label(UIKeys.GENERAL_SEARCH);
        this.search.h(20 + UIModelTree.ROW * 6).expand();

        /* The verbs over the tree, the list idiom of the panel: adding goes under the picked row —
         * a group, or a cube in its group. Duplicating and removing work on the pick from the row's
         * menu and their keys, and take no room here. */
        UIIcon add = new UIIcon(Icons.ADD, (b) -> this.addGroup());

        add.tooltip(UIKeys.MODEL_EDITOR_MODEL_GROUP_ADD);
        this.addCube = new UIIcon(Icons.BLOCK, (b) -> this.addCube());
        this.addCube.tooltip(UIKeys.MODEL_EDITOR_MODEL_CUBE_ADD);
        this.ikBones = new UIIcon(Icons.IK, (b) -> this.pickIKParent());
        this.ikBones.tooltip(UIKeys.MODEL_EDITOR_MODEL_GROUP_IK_BONES);

        /* Last in the strip, apart from the verbs: not something done to the pick but what the gizmo
         * moves — a group's or a cube's geometry, or its pivot alone. Drawn active as a bar, like
         * every other toggle. */
        UIIcon pivot = new UIIcon(Icons.SPHERE, (b) -> togglePivotOnly());

        pivot.highlight(UIModelGeometryEditor::isPivotOnly, Direction.BOTTOM);
        pivot.tooltip(UIKeys.MODEL_EDITOR_MODEL_PIVOT_ONLY);

        /* The name is committed as a whole (enter, leaving the field): every keystroke would be a rename. */
        this.name = new UITextbox(64, this::rename);
        this.name.delayedInput();

        /* A group's rest has no scale, so there's no row for one: the gizmo's scale handles scale the
         * group's branch instead, through the stand-in's scale ({@link #applyAnchor}). G/R/S start a
         * gesture on the picked group without touching a handle, the way every transform editor of
         * the panel does. */
        this.transform = new UIPropTransform().noScale();
        /* The translate row of a group's rest is its pivot, and it moves the point alone — while the
         * gizmo beside it moves the whole group — so it says so instead of the generic "Position". */
        this.transform.labels(UIKeys.MODEL_EDITOR_MODEL_PIVOT, UIKeys.TRANSFORMS_SCALE, UIKeys.MODEL_EDITOR_MODEL_ROTATION);
        this.transform.callbacks(this::beginEdit, this::commitGroupEdit, this::endEdit);
        this.transform.hotkeyDrag(() ->
        {
            ModelSlotTarget target = this.shownTarget();

            return target == null ? null : this.modelPanel.renderer.buildGizmoDrag(target);
        });
        /* The hotkeys answer to the same rule as the gizmo's handles: a group only turns and scales
         * while it is picked alone. They are the group rows' only while the gizmo stands on the group
         * — with a group picked together with cubes both editors show, and a key must not reach the
         * one the gizmo isn't on. */
        this.transform.enableHotkeys(() -> this.targetIs(ModelSlotKind.ANCHOR), (op) -> op == TransformOp.TRANSLATE || this.singleGroup());
        /* The group's pivot row wears the sphere, as a cube's pivot row does — in a pick of a group
         * and its cubes it stands right where a cube's pivot row would. */
        this.transform.translateAction(UIKeys.MODEL_EDITOR_MODEL_GROUP_CENTER_ANCHOR, Icons.SPHERE, this::centerAnchor);

        /* A cube's rows: its position (the corner it starts from), its size and its rotation, in
         * the same editor a group's rest sits in — the pads and the write path are the same — plus
         * the pivot it turns about on a row of its own, and the inflate below. The sizes stay three
         * numbers: a cube square on every side is the common case, not a reason to fold the row. */
        this.cubeTransform = new UICubeTransform().noUniformScale();
        this.cubeTransform.labels(UIKeys.MODEL_EDITOR_MODEL_CUBE_POSITION, UIKeys.MODEL_EDITOR_MODEL_CUBE_SIZE, UIKeys.MODEL_EDITOR_MODEL_ROTATION);
        this.cubeTransform.callbacks(this::beginEdit, this::commitCubeEdit, this::endEdit);
        this.cubeTransform.hotkeyDrag(() ->
        {
            ModelSlotTarget target = this.shownTarget();

            return target == null ? null : this.modelPanel.renderer.buildGizmoDrag(target);
        });
        /* A cube takes all three operations — unlike a group's rest it does have a size — and so does
         * a pick of cubes alone; a group in the pick leaves moving only. The hotkeys answer to the
         * same rule as the gizmo's handles. */
        this.cubeTransform.enableHotkeys(() -> this.targetIs(ModelSlotKind.CUBE), (op) -> op == TransformOp.TRANSLATE || this.cubesOnly());

        IKey raw = IKey.constant("%s (%s)");
        IKey[] axes = {UIKeys.GENERAL_X, UIKeys.GENERAL_Y, UIKeys.GENERAL_Z};
        int[] colors = {Colors.RED, Colors.GREEN, Colors.BLUE};

        for (int i = 0; i < 3; i++)
        {
            int axis = i;
            UITrackpad field = new UITrackpad((v) -> this.setCubePivot(axis, v.floatValue())).block().onlyNumbers();

            field.tooltip(raw.format(UIKeys.MODEL_EDITOR_MODEL_PIVOT, axes[i]));
            field.textbox.setColor(colors[i]);
            /* A finished drag of the pad closes its undo step, as the transform's own pads do, and settles the model. */
            field.getEvents().register(UITrackpadDragEndEvent.class, (e) -> this.endEdit());
            this.pivotFields[i] = field;
        }

        /* Drawn like the icons of the rows above it, and a button the way a group's pivot row icon
         * is: the pivot to the middle of the cube. */
        this.pivotIcon = new UIIcon(Icons.SPHERE, (b) -> this.centerCubePivots());
        this.pivotIcon.disabledColor = this.pivotIcon.hoverColor = Colors.WHITE;
        this.pivotIcon.tooltip(UIKeys.MODEL_EDITOR_MODEL_CUBE_CENTER_PIVOT);
        this.pivotRow = this.cubeTransform.addRow(this.pivotIcon, this.pivotFields[0], this.pivotFields[1], this.pivotFields[2]);

        this.inflate = new UITrackpad((v) -> this.setInflate(v.floatValue()));
        this.inflate.getEvents().register(UITrackpadDragEndEvent.class, (e) -> this.endEdit());
        this.inflateRow = UI.labelRow(UIKeys.MODEL_EDITOR_MODEL_CUBE_INFLATE, this.inflate);

        this.cubeUV = new UIModelCubeUV(this);

        this.trackRowEdits(
            this.cubeTransform.tx, this.cubeTransform.ty, this.cubeTransform.tz,
            this.cubeTransform.sx, this.cubeTransform.sy, this.cubeTransform.sz,
            this.cubeTransform.rx, this.cubeTransform.ry, this.cubeTransform.rz,
            this.transform.tx, this.transform.ty, this.transform.tz,
            this.transform.rx, this.transform.ry, this.transform.rz,
            this.pivotFields[0], this.pivotFields[1], this.pivotFields[2],
            this.inflate
        );

        /* One panel, the way Blockbench's is: the cube rows on top, the group rows under them. A
         * pick of cubes shows the cube rows whole; a pick with a group lends its pivot and rotation
         * rows to the group, so a group and its cubes read as position, size, pivot, rotation,
         * inflate — the cubes' position and size, the group's pivot and rotation. */
        this.body = new UIElement();
        this.body.column(UIConstants.MARGIN).vertical().stretch();
        this.body.add(UI.labelRow(UIKeys.MODEL_EDITOR_MODEL_GROUP_NAME, this.name), this.cubeTransform, this.transform, this.inflateRow);

        /* The strip over the tree stands at an icon button's size, as the unwrap pane's side icons do:
         * it is what the page is worked with, not a list's small print. */
        this.page = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING, UI.strip(UIConstants.ICON_SIZE, add, this.addCube, this.ikBones, pivot), this.search, this.body);
        this.page.full(this);
        this.add(this.page);

        this.registerKeybinds();
    }

    /**
     * The tree's verbs on the keyboard. Registered on the tree rather than on the editor, and only
     * while the cursor is over it — the same keys mean other things elsewhere in the panel, and
     * Delete would otherwise reach the tree from anywhere. Each key answers to the same rule as its
     * verb in the strip or the menu, so a verb with nothing to act on simply isn't there.
     */
    private void registerKeybinds()
    {
        IKey category = UIKeys.MODEL_EDITOR_TITLE;
        Supplier<Boolean> open = () -> this.model != null;
        Supplier<Boolean> any = () -> !this.tree.getCurrent().isEmpty();
        Supplier<Boolean> inGroup = () -> this.leaderNode() != null;

        this.tree.keys().register(Keys.MODEL_EDITOR_GROUP_ADD, this::addGroup).inside().active(open).category(category);
        this.tree.keys().register(Keys.MODEL_EDITOR_CUBE_ADD, this::addCube).inside().active(inGroup).category(category);
        this.tree.keys().register(Keys.MODEL_EDITOR_GROUP_DUPE, this::duplicateNodes).inside().active(any).category(category);
        this.tree.keys().register(Keys.DELETE, this::askRemoveNodes).inside().active(any).category(category);
        this.tree.keys().register(Keys.MODEL_EDITOR_GROUP_RENAME, this::editName).inside().active(this::single).category(category);
        this.tree.keys().register(Keys.MODEL_EDITOR_GROUP_IK_BONES, this::pickIKParent).inside().active(this::singleGroup).category(category);

        /* Unfolding is the panel's own key, as it is on the config editor's pages — no need to be
         * over the tree for it. Nor for what the gizmo moves, which is wanted with the cursor over
         * the preview, where the gizmo is. */
        this.keys().register(Keys.MODEL_EDITOR_EXPAND_ALL, () -> this.tree.setAllExpanded(true)).active(open).category(category);
        this.keys().register(Keys.MODEL_EDITOR_COLLAPSE_ALL, () -> this.tree.setAllExpanded(false)).active(open).category(category);
        this.keys().register(Keys.MODEL_EDITOR_PIVOT_ONLY, UIModelGeometryEditor::togglePivotOnly).active(open).category(category);
    }

    /**
     * Mark the changes these pads make as the rows' own ({@link #rowEdit}) — wrapped around their
     * callbacks, so every way a pad changes its number counts, a drag, a typed digit, the wheel or
     * its steppers. Typed is told by the pad's text box having the caret while it isn't dragged.
     */
    private void trackRowEdits(UITrackpad... pads)
    {
        for (UITrackpad pad : pads)
        {
            Consumer<Double> callback = pad.callback;

            pad.callback = (value) ->
            {
                boolean row = this.rowEdit;
                boolean typed = this.typedEdit;

                this.rowEdit = true;
                this.typedEdit = pad.textbox.isFocused() && !pad.isDragging();

                try
                {
                    callback.accept(value);
                }
                finally
                {
                    this.rowEdit = row;
                    this.typedEdit = typed;
                }
            };
        }
    }

    /** F2: the name field takes the caret, since the tree renames through it rather than in place. */
    private void editName()
    {
        this.getContext().focus(this.name);
    }

    /** Whether the gizmo moves the pivot alone — read by the sphere over the tree. */
    public static boolean isPivotOnly()
    {
        return pivotOnly;
    }

    /** Flip what the gizmo moves; the rows under the tree are unaffected. */
    public static void togglePivotOnly()
    {
        pivotOnly = !pivotOnly;
    }

    /* The pick */

    /** The first of the pick — what the fields, the gizmo and the verbs start from; null with nothing picked. */
    private ModelNode leaderNode()
    {
        return this.model == null ? null : this.tree.getCurrentFirst();
    }

    /**
     * The picked group, by name — what the viewport marks and the fields edit; null with nothing
     * picked, with a cube, and with several rows, which belong to no single group.
     */
    public String getSelected()
    {
        ModelNode node = this.leaderNode();

        return node != null && node.isGroup() && this.tree.getCurrent().size() == 1 ? node.group() : null;
    }

    /**
     * What the viewport gizmo is on: the leading row's numbers, through its stand-in — a group's
     * rest, or a cube, which takes all three handles since a cube does have a size.
     */
    public ModelSlotTarget shownTarget()
    {
        ModelNode leader = this.leaderNode();

        if (leader == null)
        {
            return null;
        }

        if (leader.isCube())
        {
            return this.leadCube() == null
                ? null
                : new ModelSlotTarget(leader.group(), ModelSlotKind.CUBE, this.cubeTransform, this::applyCube, this.cubesOnly() ? Gizmo.HandleMask.ALL : MANY_MASK, leader.cube());
        }

        return new ModelSlotTarget(leader.group(), ModelSlotKind.ANCHOR, this.transform, this::applyAnchor, this.single() ? ANCHOR_MASK : MANY_MASK);
    }

    /** The group the fields and the gizmo sit on: the first of the pick, when it is a group, which the rest follows. */
    private String leader()
    {
        ModelNode node = this.leaderNode();

        return node != null && node.isGroup() ? node.group() : null;
    }

    /** Whether the pick is one row, of either kind — what a name, a size and a rotation need to mean anything. */
    private boolean single()
    {
        return this.model != null && this.tree.getCurrent().size() == 1;
    }

    /** Whether every picked row is a cube — a pick the gizmo scales and turns, every cube about its own pivot. */
    private boolean cubesOnly()
    {
        if (this.model == null || this.tree.getCurrent().isEmpty())
        {
            return false;
        }

        for (ModelNode node : this.tree.getCurrent())
        {
            if (!node.isCube())
            {
                return false;
            }
        }

        return true;
    }

    /** Whether the pick is one group — what a rest rotation and the IK verbs need. */
    private boolean singleGroup()
    {
        return this.getSelected() != null;
    }

    private ModelGroup leadGroup()
    {
        String id = this.leader();

        return id == null ? null : this.model.getGroup(id);
    }

    /** The cube the rows sit on, for the unwrap block under them; null with a group or nothing picked. */
    ModelCube pickedCube()
    {
        return this.leadCube();
    }

    /** The model the tree is bound to, for the unwrap pane; null with none. */
    Model pickedModel()
    {
        return this.model;
    }

    /** The instance behind it, whose texture the unwrap pane draws; null with none. */
    ModelInstance pickedInstance()
    {
        return this.instance;
    }

    /** The unwrap pane, which the panel puts left of the preview. */
    public UIElement uvPanel()
    {
        return this.cubeUV;
    }

    /** The cube the fields sit on: the first of the pick, when it is a cube; null otherwise. */
    private ModelCube leadCube()
    {
        ModelNode node = this.leaderNode();
        ModelGroup group = node != null && node.isCube() ? this.model.getGroup(node.group()) : null;

        return group != null && node.cube() < group.cubes.size() ? group.cubes.get(node.cube()) : null;
    }

    /**
     * The group the group rows show: the leading row when it is a group, else the first group of
     * the pick. Null when the pick has no group.
     */
    private ModelGroup sectionGroup()
    {
        ModelGroup lead = this.leadGroup();

        if (lead != null || this.model == null)
        {
            return lead;
        }

        for (ModelNode node : this.tree.getCurrent())
        {
            ModelGroup group = node.isGroup() ? this.model.getGroup(node.group()) : null;

            if (group != null)
            {
                return group;
            }
        }

        return null;
    }

    /**
     * The cube the cube rows show, by address: the leading row when it is a cube — the gizmo stands
     * on it — else the first cube of the pick, which for a picked group is the first cube of its
     * branch. Null when the pick has no cube at all.
     */
    private ModelNode sectionCubeNode()
    {
        ModelNode leader = this.leaderNode();

        if (leader == null)
        {
            return null;
        }

        if (leader.isCube())
        {
            return this.leadCube() == null ? null : leader;
        }

        List<ModelNode> cubes = this.pickedCubes();

        return cubes.isEmpty() ? null : cubes.get(0);
    }

    private ModelCube sectionCube()
    {
        ModelNode node = this.sectionCubeNode();

        return node == null ? null : this.model.getGroup(node.group()).cubes.get(node.cube());
    }

    /** Whether the gizmo stands on a target of this kind — the section whose editor its hotkeys belong to. */
    private boolean targetIs(ModelSlotKind kind)
    {
        ModelSlotTarget target = this.shownTarget();

        return target != null && target.kind() == kind;
    }

    /** Bind to a model (null for none); the tree keeps its pick by address. */
    public void fill(ModelInstance instance)
    {
        this.instance = instance;
        this.model = instance != null && instance.getModel() instanceof Model model ? model : null;
        this.dirty.clear();
        this.unsettled = false;
        this.cubeEdit = null;
        this.branchScale = null;

        List<ModelNode> picked = new ArrayList<>(this.tree.getCurrent());

        this.tree.fill(this.model);
        this.tree.setCurrent(picked);
        this.fillSelection();
    }

    /**
     * A click on the model in the preview: the bone, and the cube of it under the cursor (-1 for
     * the bone itself) — picked in the tree, or added to the pick with ctrl held, as a click on a
     * row would. Whether the click was taken.
     */
    public boolean selectPick(String bone, int cube)
    {
        ModelGroup group = this.model == null ? null : this.model.getGroup(bone);

        if (group == null)
        {
            return false;
        }

        ModelNode node = cube >= 0 && cube < group.cubes.size() ? ModelNode.cube(bone, cube) : ModelNode.group(bone);

        this.search.filter("", true);
        this.tree.reveal(node);

        if (Window.isCtrlPressed())
        {
            this.tree.toggleIndex(this.tree.getList().indexOf(node));
        }
        else
        {
            this.tree.setCurrent(node);
        }

        this.fillSelection();

        return true;
    }

    private void select(String group)
    {
        this.select(ModelNode.group(group));
    }

    private void select(ModelNode node)
    {
        this.tree.reveal(node);
        this.tree.setCurrent(node);
        this.fillSelection();
    }

    /** Pick several groups at once — what a verb on several leaves behind. */
    private void selectGroups(List<String> ids)
    {
        List<ModelNode> nodes = new ArrayList<>();

        for (String id : ids)
        {
            nodes.add(ModelNode.group(id));
        }

        this.selectAll(nodes);
    }

    /** Pick several rows at once — what a verb on several leaves behind. */
    private void selectAll(List<ModelNode> nodes)
    {
        if (!nodes.isEmpty())
        {
            this.tree.reveal(nodes.get(0));
        }

        this.tree.setCurrent(nodes);
        this.fillSelection();
    }

    private ModelGroup picked()
    {
        String id = this.getSelected();

        return id == null ? null : this.model.getGroup(id);
    }

    /** Every picked group, in the order the tree lists them; empty with nothing picked. Picked cubes aren't groups. */
    private List<ModelGroup> pickedGroups()
    {
        List<ModelGroup> groups = new ArrayList<>();

        if (this.model == null)
        {
            return groups;
        }

        for (ModelNode node : this.tree.getCurrent())
        {
            ModelGroup group = node.isGroup() ? this.model.getGroup(node.group()) : null;

            if (group != null)
            {
                groups.add(group);
            }
        }

        return groups;
    }

    /**
     * Every cube in the pick, in the tree's order: the picked cube rows, and every cube under a
     * picked group — its own and its groups' all the way down, the way an outliner reads a picked
     * group. What a group carries is worked out here rather than kept in the tree's pick, so a
     * folded branch still counts and a rebuilt model can't drop it.
     */
    private List<ModelNode> pickedCubes()
    {
        List<ModelNode> cubes = new ArrayList<>();

        if (this.model != null)
        {
            this.collectPickedCubes(this.model.topGroups, false, new HashSet<>(this.tree.getCurrent()), cubes);
        }

        return cubes;
    }

    private void collectPickedCubes(List<ModelGroup> groups, boolean carried, Set<ModelNode> picked, List<ModelNode> out)
    {
        for (ModelGroup group : groups)
        {
            boolean whole = carried || picked.contains(ModelNode.group(group.id));

            for (int i = 0; i < group.cubes.size(); i++)
            {
                ModelNode cube = ModelNode.cube(group.id, i);

                if (whole || picked.contains(cube))
                {
                    out.add(cube);
                }
            }

            this.collectPickedCubes(group.children, whole, picked, out);
        }
    }

    /**
     * What the viewport outlines: every cube in the pick ({@link #pickedCubes}) alike, whether
     * picked itself or carried by a picked group. Built for the frame being drawn; the addresses
     * are looked up on the model as it stands.
     */
    public List<UIModelEditorRenderer.Outline> outlines()
    {
        List<UIModelEditorRenderer.Outline> outlines = new ArrayList<>();

        if (this.model == null)
        {
            return outlines;
        }

        int accent = BBSSettings.primaryColor.get();

        for (ModelNode node : this.pickedCubes())
        {
            outlines.add(new UIModelEditorRenderer.Outline(node, Colors.A100 | accent));
        }

        /* Last, so it draws over the pick: the row under the cursor in the tree lights up the way a
         * cube under the cursor in the viewport does — a group as the cubes it carries. */
        ModelNode hovered = this.getContext() == null ? null : this.tree.atCursor(this.getContext());
        ModelGroup owner = hovered == null ? null : this.model.getGroup(hovered.group());

        if (owner != null && hovered.isGroup())
        {
            this.outlineSubtree(owner, UIModelEditorRenderer.HOVER_COLOR, outlines);
        }
        else if (owner != null && hovered.cube() < owner.cubes.size())
        {
            outlines.add(new UIModelEditorRenderer.Outline(hovered, UIModelEditorRenderer.HOVER_COLOR));
        }

        return outlines;
    }

    private void outlineSubtree(ModelGroup group, int color, List<UIModelEditorRenderer.Outline> outlines)
    {
        for (int i = 0; i < group.cubes.size(); i++)
        {
            outlines.add(new UIModelEditorRenderer.Outline(ModelNode.cube(group.id, i), color));
        }

        for (ModelGroup child : group.children)
        {
            this.outlineSubtree(child, color, outlines);
        }
    }

    /**
     * The rows under the tree — one panel, the way Blockbench lays it out. The cube rows (position,
     * size, rotation, pivot, inflate) while the pick has a cube, its own row or one a picked group
     * carries; the group rows (pivot, rotation) while it has a group. A pick with a group lends its
     * pivot and rotation to the group, so the cube rows keep position, size and inflate and the
     * group rows stand in the middle, sharing the cube rows' space picker: the cubes' position and
     * size, the group's pivot and rotation. With nothing picked the group rows stand empty and
     * disabled, so the page keeps its height and the scroll doesn't jump on every pick. With several
     * picked every row stays live and changes all of them — a drag by the same amount, a typed
     * number to that number; the name belongs to one row and goes dead. The verbs act on the groups
     * of the pick, however the pick is mixed.
     */
    private void fillSelection()
    {
        ModelNode leader = this.leaderNode();
        ModelGroup lead = this.leadGroup();
        ModelCube leadCube = this.leadCube();
        ModelGroup group = this.sectionGroup();
        ModelCube cube = this.sectionCube();

        boolean any = group != null || cube != null;
        boolean single = this.single();
        boolean singleGroup = this.singleGroup();

        /* A new pick is a new set of cubes: an edit of the old one has nothing left to say. */
        this.cubeEdit = null;
        this.branchScale = null;

        /* The name is the leading row's. An unnamed cube shows the name it goes by as a hint, so
         * typing over it names the cube. */
        this.name.setText(lead != null ? lead.id : leadCube != null ? leadCube.name : "");
        this.name.textbox.setPlaceholder(leadCube != null && leadCube.name.isEmpty() ? IKey.constant(UIModelTree.cubeLabel(leadCube, leader.cube())) : IKey.EMPTY);
        this.loadAnchor(group);
        this.transform.setTransform(this.anchor);
        this.loadCube(cube);
        this.cubeTransform.setTransform(this.standin);

        this.cubeTransform.setVisible(cube != null);
        this.cubeTransform.setRotationVisible(group == null);
        this.cubeTransform.setRowVisible(this.pivotRow, group == null);
        this.transform.setVisible(group != null || cube == null);
        this.transform.shareSpace(group != null && cube != null ? this.cubeTransform : null);
        this.inflateRow.setVisible(cube != null);

        UIUtils.setEnabledDeep(this.body, any);
        this.transform.setRotationEnabled(group != null);
        this.cubeTransform.setScaleEnabled(cube != null);
        this.cubeTransform.setRotationEnabled(cube != null);
        UIUtils.setEnabledDeep(this.pivotRow, cube != null);
        this.inflate.setEnabled(cube != null);
        this.name.setEnabled(single);
        this.addCube.setEnabled(leader != null);
        this.ikBones.setEnabled(singleGroup);

        this.cubeUV.fill();

        this.page.resize();
        this.page.scroll.clamp();
    }

    /**
     * The row's menu offers the verbs of the strip, and duplicating and removing, which only live
     * here and on their keys. A row outside the pick becomes the pick; a row already in it leaves
     * the pick alone, so a menu opened on several rows acts on all of them.
     */
    private void fillNodeMenu(ContextMenuManager menu)
    {
        ModelNode node = this.model == null ? null : this.tree.atCursor(this.getContext());

        if (node == null)
        {
            return;
        }

        if (!this.tree.getCurrent().contains(node))
        {
            this.select(node);
        }

        menu.icon(MenuVerb.ADD, this::addGroup).label(UIKeys.MODEL_EDITOR_MODEL_GROUP_ADD);
        menu.action(Icons.BLOCK, UIKeys.MODEL_EDITOR_MODEL_CUBE_ADD, this::addCube);

        if (!this.tree.getCurrent().isEmpty())
        {
            menu.action(Icons.DUPE, UIKeys.MODEL_EDITOR_MODEL_DUPLICATE, this::duplicateNodes);
            menu.icon(MenuVerb.REMOVE, this::askRemoveNodes).label(UIKeys.MODEL_EDITOR_MODEL_REMOVE);
        }

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

        this.anchorApplied.copy(this.anchor);
    }

    /**
     * Whether the group already rests where its stand-in says — within a hair, as the round trip
     * through radians leaves it — and the stand-in's scale is back at one, where a scale of the
     * branch starts from.
     */
    private boolean anchorMatches(ModelGroup group)
    {
        return group.initial.translate.equals(this.anchor.translate, EPSILON)
            && group.initial.rotate.equals(degreesOf(this.anchor, group.initial.rotate), EPSILON)
            && this.anchor.scale.equals(1F, 1F, 1F);
    }

    /**
     * The leading group takes the stand-in's numbers, and the whole pick takes the same STEP rather
     * than the leader's pivot — the picked rows move together and keep the distances between them,
     * which is what makes a controller stay on the tip it was created on.
     *
     * <p>What the step moves — the group's geometry, or its pivot alone — is the gizmo's question,
     * not the call site's: the gizmo pushes the stand-in through {@link ModelSlotTarget#apply()}
     * while sampling its jacobian, but the move itself lands like any other, read back from the
     * stand-in on the frame. So "is a gesture doing this" is answered by the editor
     * ({@link UIPropTransform#isEditing()}), which is on for a gizmo drag and for the G/R hotkeys,
     * and off while the row is typed or dragged — and a typed pivot is a pivot, as it has always
     * been.</p>
     *
     * <p>A rest already within a hair of the numbers is left alone: the round trip through radians
     * isn't exact, and the file must not pick up the noise. The gizmo turns one group only (see
     * {@link #MANY_MASK}); the rotation row turns every picked group, as the pivot row moves every
     * picked group's point — a drag by the same amount, a typed number to that number, as in
     * Blockbench.</p>
     *
     * <p>Only the stand-in's OWN changes are carried — what it moved by since it was last carried
     * in ({@link #anchorApplied}). A cube leading the pick moves the picked groups through the cube
     * rows, and the group rows must not read that as a move of theirs and walk them back; they read
     * the group again once nothing drives ({@link #syncStandins}). The gizmo moves the whole pick,
     * cubes and all; the pivot row moves the groups' points, and the cubes have rows of their own.</p>
     *
     * <p>The stand-in's scale is the gizmo's scale of the group's branch, as a factor of where the
     * gesture began ({@link ModelBranchScale}): it stands at one whenever no gesture runs, so a
     * gesture's scale starts there. Nothing else gets to set it — a transform pasted onto the group
     * rows would otherwise blow the branch up to a cube's size — and it is put back to one.</p>
     */
    private void applyAnchor()
    {
        ModelGroup group = this.sectionGroup();

        if (group == null || sameTransform(this.anchor, this.anchorApplied))
        {
            return;
        }

        boolean gesture = this.transform.isEditing();

        if (!gesture)
        {
            this.anchor.scale.set(1F, 1F, 1F);
        }

        boolean typed = this.rowEdit && this.typedEdit;
        Vector3f degrees = degreesOf(this.anchor, group.initial.rotate);
        Vector3f step = new Vector3f(this.anchor.translate).sub(this.anchorApplied.translate);
        Vector3f turned = new Vector3f(this.anchor.rotate).sub(this.anchorApplied.rotate);
        boolean euler = this.anchor.rotationMode == Transform.RotationMode.EULER && this.anchorApplied.rotationMode == Transform.RotationMode.EULER;
        boolean scaled = !this.anchor.scale.equals(this.anchorApplied.scale);

        this.anchorApplied.copy(this.anchor);

        if (step.x != 0F || step.y != 0F || step.z != 0F)
        {
            if (typed)
            {
                /* A typed pivot is every picked group's pivot on that axis. */
                for (ModelGroup picked : this.pickedGroups())
                {
                    for (int i = 0; i < 3; i++)
                    {
                        if (step.get(i) != 0F)
                        {
                            picked.initial.translate.setComponent(i, this.anchor.translate.get(i));
                        }
                    }
                }
            }
            else
            {
                this.distribute(step, !pivotOnly && gesture, gesture);
            }

            /* The leader's pivot is the number shown, to the bit: the step above is a difference of
             * floats and lands a hair off it, and the row must not drift from the model over a
             * drag. Whether the step reached the group directly or through a picked ancestor
             * carrying it, this is where it was going. */
            group.initial.translate.set(this.anchor.translate);
        }

        if (!group.initial.rotate.equals(degrees, EPSILON))
        {
            group.initial.rotate.set(degrees);
        }

        /* The rotation row turns the other picked groups too — only the row: the gizmo turns one
         * group, and its probes must not nudge the rest. */
        if (this.rowEdit && euler && (turned.x != 0F || turned.y != 0F || turned.z != 0F))
        {
            for (ModelGroup picked : this.pickedGroups())
            {
                if (picked == group)
                {
                    continue;
                }

                for (int i = 0; i < 3; i++)
                {
                    if (turned.get(i) != 0F)
                    {
                        picked.initial.rotate.setComponent(i, typed ? degrees.get(i) : picked.initial.rotate.get(i) + MathUtils.toDeg(turned.get(i)));
                    }
                }
            }
        }

        /* Last: the gizmo's probes move the pivot before a gesture starts, and the step above puts it
         * back — the branch is taken where it stands once it has. */
        if (scaled)
        {
            if (this.branchScale == null)
            {
                this.branchScale = new ModelBranchScale(group);
            }

            if (this.branchScale.scale(this.anchor.scale))
            {
                this.dirty.addAll(this.branchScale.groups());
            }
        }
    }

    /** Whether two stand-ins hold the very same numbers, bit for bit — nothing to carry between them. */
    private static boolean sameTransform(Transform a, Transform b)
    {
        return a.translate.equals(b.translate)
            && a.scale.equals(b.scale)
            && a.rotate.equals(b.rotate)
            && a.quat.equals(b.quat)
            && a.rotationMode == b.rotationMode;
    }

    /**
     * A stand-in's rotation in degrees, however its editor stores it. The model rests in euler
     * angles, but the rotate row can be switched to a quaternion (Shift+Q) for gimbal-free work,
     * and then the eulers it carries are stale. The quaternion is read back on the branch nearest
     * the rotation already stored, so a turn keeps its numbers continuous rather than jumping to
     * a flipped equivalent — and switching the mode alone lands on the same numbers and writes
     * nothing.
     */
    private static Vector3f degreesOf(Transform standin, Vector3f stored)
    {
        Vector3f radians = standin.rotate;

        if (standin.rotationMode == Transform.RotationMode.QUATERNION)
        {
            Vector3f reference = new Vector3f(MathUtils.toRad(stored.x), MathUtils.toRad(stored.y), MathUtils.toRad(stored.z));

            radians = Matrices.toCompatibleEulerZYXRadians(standin.quat, reference, new Vector3f());
        }

        return new Vector3f(MathUtils.toDeg(radians.x), MathUtils.toDeg(radians.y), MathUtils.toDeg(radians.z));
    }

    /**
     * Every picked row takes the step the leader took, in the same terms. With {@code geometry},
     * the shapes move: a group's whole subtree goes with it (a cubic model's cubes are absolute),
     * a cube moves corner and pivot together. Without it, the points do: a pivot is a point and one
     * step added to all of them is exact, so the geometry stays and what changes is where the rows
     * turn about.
     *
     * <p>Moving geometry counts only the rows nothing else in the pick carries — a group inside a
     * picked group, or a cube of one, travels with it already, and moving it again would move it
     * twice. Points don't nest: a parent's pivot doesn't drag its child's, so every picked row
     * takes the step itself.</p>
     *
     * @param cubes whether the picked cubes take the step too; a cube leading the pick hands them
     *              theirs through its edit instead ({@link ModelCubeEdit}), from where they began
     */
    private void distribute(Vector3f step, boolean geometry, boolean cubes)
    {
        for (ModelNode node : geometry ? this.outermostNodes() : this.tree.getCurrent())
        {
            ModelGroup group = this.model.getGroup(node.group());

            if (group == null || (node.isCube() && !cubes))
            {
                continue;
            }

            if (node.isGroup())
            {
                if (geometry)
                {
                    this.model.shiftGroup(group, step);
                    this.dirty.addAll(this.model.collectSubtree(group, new ArrayList<>()));
                }
                else
                {
                    group.initial.translate.add(step);
                }
            }
            else if (node.cube() < group.cubes.size())
            {
                ModelCube cube = group.cubes.get(node.cube());

                if (geometry)
                {
                    cube.shift(step);
                }
                else
                {
                    cube.pivot.add(step);
                }

                /* Either way the bake owes a rebuild: a cube's quads are built about its pivot. */
                this.dirty.add(group);
            }
        }
    }

    /**
     * The picked rows that no other picked row carries: a group's subtree carries the groups and
     * the cubes below it, a cube carries nothing. What is carried comes along on its own, whether
     * the pick is being moved ({@link #distribute}), copied or removed, and doing it again to a
     * carried row would do it twice.
     */
    private List<ModelNode> outermostNodes()
    {
        Set<String> picked = new HashSet<>();

        for (ModelNode node : this.tree.getCurrent())
        {
            if (node.isGroup())
            {
                picked.add(node.group());
            }
        }

        List<ModelNode> outer = new ArrayList<>();

        for (ModelNode node : this.tree.getCurrent())
        {
            ModelGroup group = this.model.getGroup(node.group());

            if (group == null)
            {
                continue;
            }

            /* A cube is carried by its own group; a group is carried only by one above it. */
            boolean carried = false;

            for (ModelGroup above = node.isGroup() ? group.parent : group; above != null && !carried; above = above.parent)
            {
                carried = picked.contains(above.id);
            }

            if (!carried)
            {
                outer.add(node);
            }
        }

        return outer;
    }

    /**
     * Every picked group's pivot to the middle of what that group draws — each on its own geometry,
     * not on the pick's — as the translate row's icon. Only the pivot moves: cube and mesh
     * coordinates are absolute in the model, so the geometry stays exactly where it stands and what
     * changes is the point the bone turns about. A group with no geometry at all is passed over.
     *
     * <p>The leader goes through the stand-in rather than into the group, since the stand-in is what
     * {@link #render} writes back every frame — the group would take the new pivot and lose it
     * again on the very next one. The others have no stand-in and take it directly.</p>
     */
    private void centerAnchor()
    {
        ModelGroup leader = this.sectionGroup();
        List<ModelGroup> picked = this.pickedGroups();

        if (leader == null)
        {
            return;
        }

        MapType before = this.snapshot();
        int centered = 0;

        for (ModelGroup group : picked)
        {
            Vector3f min = new Vector3f();
            Vector3f max = new Vector3f();

            if (!group.getGeometryBounds(min, max))
            {
                continue;
            }

            Vector3f center = min.add(max).mul(0.5F);
            Vector3f pivot = group == leader ? this.anchor.translate : group.initial.translate;

            if (pivot.equals(center, EPSILON))
            {
                continue;
            }

            pivot.set(center);
            centered++;
        }

        if (centered == 0)
        {
            return;
        }

        /* The leader's new pivot is in the stand-in; the step it just took must not drag the others,
         * which have already been centred on their own geometry — so it counts as carried in. */
        leader.initial.translate.set(this.anchor.translate);
        this.anchorApplied.copy(this.anchor);

        IKey label = picked.size() > 1
            ? UIKeys.MODEL_EDITOR_MODEL_UNDO_CENTER_ANCHOR_MANY.format(picked.size())
            : UIKeys.MODEL_EDITOR_MODEL_UNDO_CENTER_ANCHOR.format(leader.id);

        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, label.get(), null, before, this.snapshot()));
        this.modelPanel.closeModelEdit();
    }

    /* The cube: the stand-in between its transform editor and the cube, and its own rows */

    /** The stand-in takes the cube's numbers: its corner, its size, its rotation in radians; the rows below take its pivot and inflate. */
    private void loadCube(ModelCube cube)
    {
        this.standin.identity();

        if (cube != null)
        {
            Vector3f rotate = cube.rotate;

            this.standin.translate.set(cube.origin);
            this.standin.scale.set(cube.size);
            this.standin.rotate.set(MathUtils.toRad(rotate.x), MathUtils.toRad(rotate.y), MathUtils.toRad(rotate.z));
        }

        this.applied.copy(this.standin);
        this.syncCubeRows(cube);
    }

    /** The cube's own rows take its numbers — the pivot and the inflate, which have no stand-in. */
    private void syncCubeRows(ModelCube cube)
    {
        for (int i = 0; i < 3; i++)
        {
            this.pivotFields[i].setValue(cube == null ? 0D : cube.pivot.get(i));
        }

        this.inflate.setValue(cube == null ? 0D : cube.inflate);
    }

    /**
     * The leading cube's stand-in carried into the pick: every cube of it takes the change the
     * stand-in made since the edit began ({@link ModelCubeEdit}) — the leader to the numbers shown,
     * the rest each from its own — and the picked groups take the move step by step, as a group's
     * move always has ({@link #distribute}). With nothing changed and no edit running, nothing is
     * opened; a cube within a hair of its numbers is left alone, as a group's rest is.
     *
     * <p>Three things a gesture does that the rows don't. With the sphere over the tree on, a
     * drag moves the pivots alone, as it does for a group — and then the position row runs ahead of
     * the corner, which stays put, until {@link #endEdit} reads the cube back. The scale handles
     * grow each cube FROM ITS PIVOT, which is where they sit, by the leader's factor — the size row
     * grows them from the corner instead, which is what a corner and a size read as. And the rings
     * turn each cube by the leader's turn about its own pivot, about the axes the ring is drawn
     * about — on a ring of the local frame, each cube about its own — where the rotation row adds to
     * each angle. Everything is taken from where the edit began, so an Escape mid-drag lands every
     * cube back on its numbers exactly.</p>
     */
    private void applyCube()
    {
        ModelCube cube = this.sectionCube();

        if (cube == null)
        {
            return;
        }

        /* Only the stand-in's own changes are carried: a picked group's move from the other section
         * moves these cubes too, and must not be read as a move of theirs. */
        if (this.cubeEdit == null && (sameTransform(this.standin, this.applied) || this.standinMatches(cube)))
        {
            this.applied.copy(this.standin);

            return;
        }

        boolean gesture = this.cubeTransform.isEditing();

        /* The row is the cube's corner, and a corner moved is the cube moved; the toggle only has
         * a say while the gizmo or a hotkey is what's driving. */
        boolean geometry = !pivotOnly || !gesture;
        ModelCubeEdit edit = this.cubeEdit();
        Vector3f step = new Vector3f(this.standin.translate).sub(this.applied.translate);

        /* The picked groups go with a cube's move only when the gizmo moves it — the gizmo moves the
         * whole pick. The position row, as in Blockbench, moves the picked cubes alone; the groups
         * have rows of their own. */
        if (this.cubeLeads() && !this.rowEdit && (step.x != 0F || step.y != 0F || step.z != 0F))
        {
            this.distribute(step, geometry, false);
        }

        ModelCubeEdit.Source source = gesture
            ? this.turnsOwnAxes() ? ModelCubeEdit.Source.GIZMO_OWN_AXES : ModelCubeEdit.Source.GIZMO
            : this.rowEdit && this.typedEdit ? ModelCubeEdit.Source.TYPED : ModelCubeEdit.Source.ROW;

        if (edit.carry(this.standin, geometry, source))
        {
            this.dirty.addAll(edit.groups());
        }

        if (!geometry)
        {
            this.syncCubeRows(cube);
        }

        this.applied.copy(this.standin);
    }

    /**
     * Whether the gesture on the cube rows turns about the leading cube's own axes — a ring, or R
     * with an axis key, in the local frame — so every cube of the pick turns about its own, as
     * Blockbench turns a pick in its local space. The view's ring and the sphere turn about the
     * scene's axes whatever the frame.
     */
    private boolean turnsOwnAxes()
    {
        TransformGesture gesture = this.cubeTransform.getGesture();

        return gesture.getOp() == TransformOp.ROTATE && !gesture.isViewRotate() && !gesture.isSphereRotate() && gesture.space().isLocal();
    }

    /** Whether the cube already has the numbers its stand-in shows — within a hair, as the round trip through radians leaves them. */
    private boolean standinMatches(ModelCube cube)
    {
        return cube.origin.equals(this.standin.translate, EPSILON)
            && cube.size.equals(this.standin.scale, EPSILON)
            && cube.rotate.equals(degreesOf(this.standin, cube.rotate), EPSILON);
    }

    /**
     * The edit of the picked cubes, opened on the first change of their numbers: every cube of the
     * pick as it stands before that change, and the stand-in as it was last carried in
     * ({@link #applied}), which the change is measured from — a gizmo probe opens it having
     * already nudged the stand-in, never the model. Dropped once nothing drives the numbers any
     * more ({@link #render}), at the end of a drag or a gesture, and whenever the pick or the model
     * changes under it.
     */
    private ModelCubeEdit cubeEdit()
    {
        if (this.cubeEdit != null)
        {
            return this.cubeEdit;
        }

        ModelNode shown = this.sectionCubeNode();
        boolean own = this.rowEdit || !this.cubeLeads();
        Set<ModelNode> picked = new HashSet<>(this.tree.getCurrent());
        Set<ModelNode> outermost = new HashSet<>(this.outermostNodes());
        ModelCubeEdit edit = new ModelCubeEdit(this.applied);

        for (ModelNode node : this.pickedCubes())
        {
            ModelGroup group = this.model.getGroup(node.group());

            /* When the gizmo moves a leading cube, the picked groups carry their own cubes along;
             * the rows move the cubes themselves, every one of them its own. */
            edit.add(group, group.cubes.get(node.cube()), own || picked.contains(node), own || outermost.contains(node), node.equals(shown));
        }

        return this.cubeEdit = edit;
    }

    /** Whether the first of the pick is a cube — the gizmo is on the cube rows then, not on the group rows. */
    private boolean cubeLeads()
    {
        ModelNode leader = this.leaderNode();

        return leader != null && leader.isCube();
    }

    /**
     * Every picked cube's pivot to the middle of that cube, as the pivot row's icon — what the
     * translate row's icon does for a group. Only the point moves: the cube stays exactly where it
     * stands, and what changes is where it turns about. The cubes a picked group carries count;
     * the groups' own pivots are left to their own icon.
     */
    private void centerCubePivots()
    {
        if (this.sectionCube() == null)
        {
            return;
        }

        MapType before = this.snapshot();
        ModelNode first = null;
        int centered = 0;

        this.cubeEdit = null;

        for (ModelNode node : this.pickedCubes())
        {
            ModelGroup group = this.model.getGroup(node.group());

            ModelCube cube = group.cubes.get(node.cube());
            Vector3f center = new Vector3f(cube.size).mul(0.5F).add(cube.origin);

            if (cube.pivot.equals(center, EPSILON))
            {
                continue;
            }

            cube.pivot.set(center);
            this.dirty.add(group);

            if (first == null)
            {
                first = node;
            }

            centered++;
        }

        if (centered == 0)
        {
            return;
        }

        ModelGroup group = this.model.getGroup(first.group());
        IKey label = centered > 1
            ? UIKeys.MODEL_EDITOR_MODEL_UNDO_CENTER_ANCHOR_MANY.format(centered)
            : UIKeys.MODEL_EDITOR_MODEL_UNDO_CENTER_ANCHOR.format(UIModelTree.cubeLabel(group.cubes.get(first.cube()), first.cube()));

        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, label.get(), null, before, this.snapshot()));
        this.endEdit();
    }

    /** The pivot alone: the point the cubes turn about, with the cubes left where they stand. */
    private void setCubePivot(int axis, float value)
    {
        ModelNode shown = this.sectionCubeNode();
        ModelCube cube = this.sectionCube();

        if (cube == null || cube.pivot.get(axis) == value)
        {
            return;
        }

        MapType before = this.snapshot();
        ModelCubeEdit edit = this.cubeEdit();

        if (edit.pivot(axis, value, this.rowEdit && this.typedEdit))
        {
            this.dirty.addAll(edit.groups());
        }

        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, this.cubesLabel(UIKeys.MODEL_EDITOR_MODEL_UNDO_CUBE_PIVOT, edit.size()).get(), "pivot:" + shown.key(), before, this.snapshot()));
    }

    /** How far the cubes grow past their corners on every side. */
    private void setInflate(float value)
    {
        ModelNode shown = this.sectionCubeNode();
        ModelCube cube = this.sectionCube();

        if (cube == null || cube.inflate == value)
        {
            return;
        }

        MapType before = this.snapshot();
        ModelCubeEdit edit = this.cubeEdit();

        if (edit.inflate(value, this.rowEdit && this.typedEdit))
        {
            this.dirty.addAll(edit.groups());
        }

        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, this.cubesLabel(UIKeys.MODEL_EDITOR_MODEL_UNDO_CUBE_INFLATE, edit.size()).get(), "inflate:" + shown.key(), before, this.snapshot()));
    }

    /** What an edit of the cubes is called on the undo stack: the shown cube's own label, or how many cubes it took. */
    private IKey cubesLabel(IKey single, int cubes)
    {
        ModelNode shown = this.sectionCubeNode();
        ModelCube cube = this.sectionCube();

        return cubes > 1 || cube == null
            ? UIKeys.MODEL_EDITOR_MODEL_UNDO_CUBE_TRANSFORM_MANY.format(cubes)
            : single.format(UIModelTree.cubeLabel(cube, shown.cube()));
    }

    /**
     * The same numbers on every cube of the pick — a paste or a reset of the cube rows, the one
     * absolute edit of a pick, as in every editor of a pick in the mod. A pasted corner moves each
     * cube there whole, pivot along, the way the position row moves one; a null leaves that row as
     * it is. One undo step, and the rows read the cube back.
     */
    private void pasteCubes(Vector3d corner, Vector3d size, Vector3d rotation)
    {
        if (this.sectionCube() == null)
        {
            return;
        }

        List<ModelNode> nodes = this.pickedCubes();
        MapType before = this.snapshot();

        this.cubeEdit = null;

        for (ModelNode node : nodes)
        {
            ModelGroup group = this.model.getGroup(node.group());
            ModelCube cube = group.cubes.get(node.cube());

            if (corner != null)
            {
                Vector3f to = new Vector3f((float) corner.x, (float) corner.y, (float) corner.z);

                cube.pivot.add(new Vector3f(to).sub(cube.origin));
                cube.origin.set(to);
            }

            if (size != null)
            {
                cube.size.set((float) size.x, (float) size.y, (float) size.z);
            }

            if (rotation != null)
            {
                cube.rotate.set((float) rotation.x, (float) rotation.y, (float) rotation.z);
            }

            this.dirty.add(group);
        }

        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, this.cubesLabel(UIKeys.MODEL_EDITOR_MODEL_UNDO_CUBE_TRANSFORM, nodes.size()).get(), null, before, this.snapshot()));
        this.endEdit();
    }

    /**
     * Rebuild what changed numbers invalidated so far: the quads of the touched groups, and their
     * bake alone — once a frame, however many times the numbers moved in it. A drag of a pad or a
     * gizmo changes the numbers on every step, and re-uploading the whole model each time would be
     * a drag's worth of needless work; the model as a whole is settled when the drag is over.
     */
    private void bake()
    {
        if (this.dirty.isEmpty() || this.model == null)
        {
            return;
        }

        this.model.refreshGeometry(this.dirty);

        if (this.instance != null)
        {
            this.instance.rebakeGroups(this.dirty);
        }

        this.dirty.clear();
        this.unsettled = true;
    }

    /**
     * Settle the model as a whole after changed numbers: whatever is still unbaked, then — through
     * the panel — its bake, its welds and its animator, which a group's own bake leaves behind.
     * Nothing to do while nothing changed.
     */
    private void settle()
    {
        this.bake();

        if (this.unsettled)
        {
            this.unsettled = false;
            this.modelPanel.refresh();
        }
    }

    /** Whether a gizmo or hotkey gesture is driving one of the editors, so the numbers haven't settled yet. */
    private boolean gestureRunning()
    {
        return this.transform.isEditing() || this.cubeTransform.isEditing();
    }

    /**
     * Whether a pad of the rows under the tree is being dragged — the numbers move on every step of
     * it, and the model settles when it's let go. The group's own pivot row is in here too: with a
     * cube picked along with the group, the step it hands out reaches the cube.
     */
    private boolean padDragging()
    {
        for (UITrackpad pad : new UITrackpad[]{this.transform.tx, this.transform.ty, this.transform.tz, this.cubeTransform.tx, this.cubeTransform.ty, this.cubeTransform.tz, this.cubeTransform.sx, this.cubeTransform.sy, this.cubeTransform.sz, this.cubeTransform.rx, this.cubeTransform.ry, this.cubeTransform.rz, this.pivotFields[0], this.pivotFields[1], this.pivotFields[2], this.inflate})
        {
            if (pad.isDragging())
            {
                return true;
            }
        }

        return this.cubeUV.dragging();
    }

    /**
     * The stand-ins are the truth of the picked row's numbers for as long as it's picked — see the
     * class. What they changed is baked once a frame, and the model settles as a whole as soon as
     * nothing is driving the numbers any more — which is also where an edit of the cubes ends: a
     * typed number is an edit of its own.
     */
    @Override
    public void render(UIContext context)
    {
        this.applyAnchor();
        this.applyCube();

        if (this.gestureRunning() || this.padDragging())
        {
            this.bake();
        }
        else
        {
            this.settle();
            this.cubeEdit = null;
            this.branchScale = null;
            this.syncStandins();
        }

        super.render(context);
    }

    /**
     * The rows read their numbers back once nothing drives them, if something else changed those
     * numbers meanwhile — the other section's move of the pick, a group's gizmo carrying the cubes
     * the cube rows show. Only on a real difference, so a stand-in switched to quaternions keeps its
     * mode while nothing touches it.
     */
    private void syncStandins()
    {
        ModelGroup group = this.sectionGroup();

        if (group != null && !this.anchorMatches(group))
        {
            this.loadAnchor(group);
            this.transform.setTransform(this.anchor);
        }

        ModelCube cube = this.sectionCube();

        if (cube != null && !this.standinMatches(cube))
        {
            this.loadCube(cube);
            this.cubeTransform.setTransform(this.standin);
        }
    }

    /* An edit of the numbers: the model before it, the model after it, one step on the stack — the
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

    /** A change of the group rows: their group's rest, and the pick's step with it. */
    private void commitGroupEdit()
    {
        ModelGroup group = this.sectionGroup();

        if (group == null || this.before == null)
        {
            this.before = null;

            return;
        }

        int picked = this.pickedGroups().size();

        this.applyAnchor();

        /* A scale of the branch is a step of its own, by its own name: the steps of a gesture merge
         * under the first one's, and a gesture can switch from scaling to moving on the way. */
        boolean scaling = this.transform.isEditing() && this.transform.getGesture().getOp() == TransformOp.SCALE;
        IKey label = scaling
            ? UIKeys.MODEL_EDITOR_MODEL_UNDO_SCALE.format(group.id)
            : picked > 1
                ? UIKeys.MODEL_EDITOR_MODEL_UNDO_TRANSFORM_MANY.format(picked)
                : UIKeys.MODEL_EDITOR_MODEL_UNDO_TRANSFORM.format(group.id);

        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, label.get(), (scaling ? "scale:" : "transform:") + group.id, this.before, this.snapshot()));
        this.before = null;
    }

    /** A change of the cube rows: every cube of the pick by it. */
    private void commitCubeEdit()
    {
        ModelNode shown = this.sectionCubeNode();

        if (shown == null || this.before == null)
        {
            this.before = null;

            return;
        }

        this.applyCube();
        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, this.cubesLabel(UIKeys.MODEL_EDITOR_MODEL_UNDO_CUBE_TRANSFORM, this.pickedCubes().size()).get(), "transform:" + shown.key(), this.before, this.snapshot()));
        this.before = null;
    }

    /**
     * The end of a gesture or a pad's drag: its undo step closes, and the model settles. The cube's
     * stand-in is read back from the cube afterwards — a gesture can leave it ahead of the numbers
     * it shows, growing the cube from its pivot without the position row, or moving the pivot alone
     * while the row followed the gizmo — and the group rows are read back too if the move they
     * didn't make shifted their group.
     */
    private void endEdit()
    {
        this.cubeEdit = null;
        this.branchScale = null;
        this.modelPanel.closeModelEdit();
        this.settle();

        ModelCube cube = this.sectionCube();

        if (cube != null)
        {
            this.loadCube(cube);
            this.cubeTransform.setTransform(this.standin);
        }

        ModelGroup group = this.sectionGroup();

        if (group != null && !this.anchorMatches(group))
        {
            this.loadAnchor(group);
            this.transform.setTransform(this.anchor);
        }
    }

    /**
     * An edit of the picked cube's own numbers made from outside — the unwrap rows under it:
     * snapshot, change, mark what it draws as for rebuilding, one undo step. {@code key} merges the
     * steps of a drag, with the cube's address added to it, so a drag on one cube never merges into
     * a drag on another; null for a change that stands on its own.
     */
    void editCube(IKey label, String key, Runnable mutation)
    {
        ModelNode leader = this.leaderNode();

        if (this.model == null || leader == null || !leader.isCube())
        {
            return;
        }

        MapType before = this.snapshot();

        mutation.run();
        this.dirty.add(this.model.getGroup(leader.group()));
        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, label.get(), key == null ? null : key + ":" + leader.key(), before, this.snapshot()));
    }

    /** The end of such an edit: its undo step closes and the model settles. */
    void closeCubeEdit()
    {
        this.endEdit();
    }

    /**
     * The sheet every unwrap is measured against. It belongs to the model rather than to any one
     * cube — it is the file's {@code texture}, not the size of the PNG — so the whole model's quads
     * follow it and every group's bake is rebuilt.
     */
    void setTextureSize(int width, int height)
    {
        if (this.model == null || width <= 0 || height <= 0 || (this.model.textureWidth == width && this.model.textureHeight == height))
        {
            return;
        }

        MapType before = this.snapshot();

        this.model.setTextureSize(width, height);
        this.dirty.addAll(this.model.getOrderedGroups());
        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, UIKeys.MODEL_EDITOR_MODEL_UNDO_TEXTURE_SIZE.format(width, height).get(), "texture_size", before, this.snapshot()));
    }

    /* The structure: rows added, copied, removed, renamed, moved — each one undo step, settled and
     * shown through the panel right after. A verb acts on the rows nothing else in the pick carries
     * ({@link #outermostNodes}), so a group and a cube of it never get it twice. */

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

    /** A new, empty group under the picked one (at its pivot) — under a picked cube's group — or at the root with nothing picked. */
    private void addGroup()
    {
        if (this.model == null)
        {
            return;
        }

        ModelNode first = this.leaderNode();
        ModelGroup parent = first == null ? null : this.model.getGroup(first.group());
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
     * start on the joint they were asked about and are dragged out from there.
     *
     * <p>The tip and the controller become the pick, in that order, so the very next drag moves the two
     * of them together: they have to sit on the same point for the chain to switch on without a jump,
     * and picked together they can no longer drift apart. The pole is left out — where it goes is the
     * chain's business, not the tip's.</p>
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
        this.selectGroups(java.util.Arrays.asList(end, controller));
    }

    /**
     * A new cube in the leading row's group — right under a picked cube, else last — sitting on the
     * group's pivot, four pixels on a side. It comes wrapped in the texture's top left corner
     * rather than bare: a cube with no faces at all draws nothing, and a new cube you can't see
     * reads as a bug rather than as a cube waiting to be unwrapped.
     */
    private void addCube()
    {
        ModelNode leader = this.leaderNode();
        ModelGroup group = leader == null ? null : this.model.getGroup(leader.group());

        if (group == null)
        {
            return;
        }

        ModelCube cube = new ModelCube();
        float half = NEW_CUBE_SIDE / 2F;

        cube.size.set(NEW_CUBE_SIDE, NEW_CUBE_SIDE, NEW_CUBE_SIDE);
        cube.pivot.set(group.initial.translate);
        cube.origin.set(cube.pivot).sub(half, half, half);
        cube.setupBoxUV(new Vector2f(0F, 0F), false);
        cube.generateQuads(this.model.textureWidth, this.model.textureHeight);

        int at = leader.isCube() ? Math.min(leader.cube() + 1, group.cubes.size()) : group.cubes.size();

        this.edit(UIKeys.MODEL_EDITOR_MODEL_UNDO_CUBE_ADD.format(group.id), () -> group.cubes.add(at, cube));
        this.select(ModelNode.cube(group.id, at));
    }

    /**
     * A copy of every picked row, each right after its original — a group among its siblings with
     * everything inside it, a cube among its group's cubes — as one undo step; the copies become
     * the pick. A row inside a picked group is left out: its copy already comes along inside that
     * one.
     */
    private void duplicateNodes()
    {
        List<ModelNode> nodes = this.outermostNodes();

        if (this.model == null || nodes.isEmpty())
        {
            return;
        }

        Set<String> taken = new HashSet<>();
        List<ModelGroup> groups = new ArrayList<>();
        List<ModelGroup> groupCopies = new ArrayList<>();
        List<ModelNode> cubes = new ArrayList<>();
        List<ModelCube> cubeCopies = new ArrayList<>();

        for (ModelNode node : nodes)
        {
            ModelGroup group = this.model.getGroup(node.group());

            if (node.isGroup())
            {
                groups.add(group);
                groupCopies.add(this.copy(group, taken));
            }
            else if (node.cube() < group.cubes.size())
            {
                cubes.add(node);
                cubeCopies.add(this.copyCube(group.cubes.get(node.cube())));
            }
        }

        int[] landed = new int[cubes.size()];

        this.edit(this.label(UIKeys.MODEL_EDITOR_MODEL_UNDO_DUPLICATE, UIKeys.MODEL_EDITOR_MODEL_UNDO_DUPLICATE_MANY, nodes), () ->
        {
            for (int i = 0; i < groups.size(); i++)
            {
                ModelGroup group = groups.get(i);
                List<ModelGroup> siblings = this.siblings(group);

                siblings.add(siblings.indexOf(group) + 1, groupCopies.get(i));
            }

            /* Back to front: a copy slipped in after its original pushes every cube behind it along,
             * and the numbers of the ones still to be copied must not move under them. */
            for (int i = cubes.size() - 1; i >= 0; i--)
            {
                ModelNode node = cubes.get(i);

                this.model.getGroup(node.group()).cubes.add(node.cube() + 1, cubeCopies.get(i));
            }

            for (int i = 0; i < cubes.size(); i++)
            {
                landed[i] = this.model.getGroup(cubes.get(i).group()).cubes.indexOf(cubeCopies.get(i));
            }
        });

        List<ModelNode> pick = new ArrayList<>();

        for (ModelGroup copy : groupCopies)
        {
            pick.add(ModelNode.group(copy.id));
        }

        for (int i = 0; i < cubes.size(); i++)
        {
            pick.add(ModelNode.cube(cubes.get(i).group(), landed[i]));
        }

        this.selectAll(pick);
    }

    /** A group and its subtree as new groups under new names, the cubes rebuilt from their data. */
    private ModelGroup copy(ModelGroup group, Set<String> taken)
    {
        ModelGroup copy = new ModelGroup(this.uniqueName(group.id, taken));

        copy.fromData(group.toData());
        copy.generateQuads(this.model.textureWidth, this.model.textureHeight);

        for (ModelGroup child : group.children)
        {
            copy.children.add(this.copy(child, taken));
        }

        return copy;
    }

    /** A cube as a new one with the same numbers, its name along: nothing refers to a cube by name. */
    private ModelCube copyCube(ModelCube cube)
    {
        ModelCube copy = new ModelCube();

        copy.fromData(cube.toData());
        copy.generateQuads(this.model.textureWidth, this.model.textureHeight);

        return copy;
    }

    /**
     * Removing a group takes its subtree and its cubes with it, so a pick with any group in it is
     * asked about first. Cubes go without a question, the way a row of any other list goes.
     */
    private void askRemoveNodes()
    {
        List<ModelNode> nodes = this.outermostNodes();
        int groups = 0;

        for (ModelNode node : nodes)
        {
            groups += node.isGroup() ? 1 : 0;
        }

        if (nodes.isEmpty())
        {
            return;
        }

        if (groups == 0)
        {
            this.removeNodes(nodes);

            return;
        }

        IKey question;

        if (groups < nodes.size())
        {
            question = UIKeys.MODEL_EDITOR_MODEL_REMOVE_CONFIRM_ROWS.format(nodes.size());
        }
        else if (nodes.size() == 1)
        {
            question = UIKeys.MODEL_EDITOR_MODEL_GROUP_REMOVE_CONFIRM.format(nodes.get(0).group());
        }
        else
        {
            question = UIKeys.MODEL_EDITOR_MODEL_GROUP_REMOVE_CONFIRM_MANY.format(nodes.size());
        }

        UIOverlay.addOverlay(this.getContext(), new UIConfirmOverlayPanel(
            UIKeys.MODEL_EDITOR_MODEL_REMOVE,
            question,
            (confirm) ->
            {
                if (confirm)
                {
                    this.removeNodes(nodes);
                }
            }
        ));
    }

    private void removeNodes(List<ModelNode> nodes)
    {
        List<ModelGroup> groups = new ArrayList<>();
        List<ModelGroup> owners = new ArrayList<>();
        List<ModelCube> cubes = new ArrayList<>();
        int highest = Integer.MAX_VALUE;

        for (ModelNode node : nodes)
        {
            ModelGroup group = this.model.getGroup(node.group());
            int row = this.tree.getList().indexOf(node);

            if (row >= 0)
            {
                highest = Math.min(highest, row);
            }

            if (node.isGroup())
            {
                groups.add(group);
            }
            else if (node.cube() < group.cubes.size())
            {
                owners.add(group);
                cubes.add(group.cubes.get(node.cube()));
            }
        }

        int above = highest;

        this.tree.deselect();
        this.edit(this.label(UIKeys.MODEL_EDITOR_MODEL_UNDO_REMOVE, UIKeys.MODEL_EDITOR_MODEL_UNDO_REMOVE_MANY, nodes), () ->
        {
            for (ModelGroup group : groups)
            {
                this.siblings(group).remove(group);
            }

            /* By the cube itself, not by its number: every cube one of them takes moves the rest. */
            for (int i = 0; i < cubes.size(); i++)
            {
                owners.get(i).cubes.remove(cubes.get(i));
            }
        });

        /* The row above what went takes the pick, so a run of deletions carries on from there. */
        List<ModelNode> rows = this.tree.getList();

        if (rows.isEmpty())
        {
            this.fillSelection();
        }
        else
        {
            this.select(rows.get(Math.max(0, Math.min(above - 1, rows.size() - 1))));
        }
    }

    /** The undo label for a verb on one row (what it's called) or on several (how many). */
    private IKey label(IKey one, IKey many, List<ModelNode> nodes)
    {
        return nodes.size() == 1 ? one.format(this.nodeLabel(nodes.get(0))) : many.format(nodes.size());
    }

    /** What a row is called: a group's name, or what the tree calls the cube. */
    private String nodeLabel(ModelNode node)
    {
        ModelGroup group = this.model == null ? null : this.model.getGroup(node.group());
        ModelCube cube = node.isCube() && group != null && node.cube() < group.cubes.size() ? group.cubes.get(node.cube()) : null;

        return cube == null ? node.group() : UIModelTree.cubeLabel(cube, node.cube());
    }

    /** The name field, committed: the picked row takes the name, whichever kind it is. */
    private void rename(String to)
    {
        ModelNode leader = this.leaderNode();

        if (leader != null && leader.isCube() && this.single())
        {
            this.renameCube(leader, to.trim());
        }
        else
        {
            this.renameGroup(to);
        }
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
     * Name the picked cube — or take its name away, so it goes by its number again. Nothing refers
     * to a cube by name, so any name goes, a repeated one included.
     */
    private void renameCube(ModelNode node, String to)
    {
        ModelCube cube = this.leadCube();

        if (cube == null || to.equals(cube.name))
        {
            this.name.setText(cube == null ? "" : cube.name);

            return;
        }

        String from = UIModelTree.cubeLabel(cube, node.cube());
        String named = to.isEmpty() ? UIKeys.MODEL_EDITOR_MODEL_CUBE_LABEL.format(node.cube() + 1).get() : to;

        this.edit(UIKeys.MODEL_EDITOR_MODEL_UNDO_CUBE_RENAME.format(from, named), () -> cube.name = to);
    }

    /* Drops, as the tree reports them */

    /** A row dropped between rows. */
    private void moveNode(ModelNode dragged, ModelNode before)
    {
        if (dragged.isGroup())
        {
            this.moveGroup(dragged.group(), before);
        }
        else
        {
            this.moveCube(dragged, before);
        }
    }

    /** A row dropped onto a group's row: inside it, last. */
    private void dropNode(ModelNode dragged, String parentId)
    {
        if (dragged.isGroup())
        {
            this.reparentGroup(dragged.group(), parentId);
        }
        else if (this.model != null)
        {
            this.moveCubeInto(dragged, this.model.getGroup(parentId), -1);
        }
    }

    /**
     * A cube dropped between rows lands in the group the caret is inside of: above another cube, in
     * its place among that group's cubes; above a group's row, at the end of the cubes of the group
     * that row sits in — which is exactly where the caret is drawn, after the last cube and before
     * the first group inside. A cube has nowhere to live at the root, so a caret there, or past the
     * last row, is the one drop it has no answer for.
     */
    private void moveCube(ModelNode dragged, ModelNode before)
    {
        ModelGroup target = before == null || this.model == null ? null : this.model.getGroup(before.group());

        if (target == null)
        {
            return;
        }

        if (before.isCube())
        {
            this.moveCubeInto(dragged, target, before.cube());
        }
        else
        {
            this.moveCubeInto(dragged, target.parent, -1);
        }
    }

    /** A cube into {@code to} at {@code index} — the end for -1 — as one undo step; it stays picked. */
    private void moveCubeInto(ModelNode dragged, ModelGroup to, int index)
    {
        ModelGroup from = this.model == null ? null : this.model.getGroup(dragged.group());

        if (from == null || to == null || dragged.cube() >= from.cubes.size())
        {
            return;
        }

        ModelCube cube = from.cubes.get(dragged.cube());
        int[] landed = new int[1];

        this.edit(UIKeys.MODEL_EDITOR_MODEL_UNDO_MOVE.format(this.nodeLabel(dragged)), () ->
        {
            from.cubes.remove(dragged.cube());

            /* Taking the cube out first pulls everything behind it down a place — the place it was
             * headed for included, when that is among the cubes it just left. */
            int at = index < 0 ? to.cubes.size() : index - (to == from && dragged.cube() < index ? 1 : 0);

            to.cubes.add(Math.max(0, Math.min(at, to.cubes.size())), cube);
            landed[0] = to.cubes.indexOf(cube);
        });
        this.select(ModelNode.cube(to.id, landed[0]));
    }

    /**
     * A group dropped between rows becomes the sibling right before {@code before} — at whatever
     * depth that row sits, since changing a parent is allowed here — or, above a cube's row, the
     * first group inside that cube's group: that is where the caret sits; {@code before} null sends
     * it to the end of the roots. A drop inside the group's own subtree is refused: it would take
     * the group out of the model with it.
     */
    private void moveGroup(String id, ModelNode before)
    {
        ModelGroup group = this.model == null ? null : this.model.getGroup(id);
        ModelGroup target = before == null || this.model == null ? null : this.model.getGroup(before.group());

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
            else if (before.isCube())
            {
                target.children.add(0, group);
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

    /** Three numbers of a copied transform from {@code offset}; zero where one isn't a number. */
    private static Vector3d copiedVector(ListType list, int offset)
    {
        Vector3d vector = new Vector3d();

        for (int i = 0; i < 3; i++)
        {
            if (list.get(offset + i).isNumeric())
            {
                vector.setComponent(i, list.get(offset + i).asNumeric().doubleValue());
            }
        }

        return vector;
    }

    /**
     * The cube rows' editor. What is typed or dragged into it is a change the whole pick takes
     * ({@link ModelCubeEdit}); a paste or a reset is the one absolute edit of a pick, as in every
     * editor of a pick in the mod — the same numbers on every cube ({@link #pasteCubes}), and a
     * whole paste as one undo step rather than three.
     */
    private class UICubeTransform extends UIPropTransform
    {
        @Override
        public void pasteAll(ListType list)
        {
            UIModelGeometryEditor.this.pasteCubes(copiedVector(list, 0), copiedVector(list, 3), copiedVector(list, 6));
        }

        @Override
        public void pasteTranslation(Vector3d translation)
        {
            UIModelGeometryEditor.this.pasteCubes(translation, null, null);
        }

        @Override
        public void pasteScale(Vector3d scale)
        {
            UIModelGeometryEditor.this.pasteCubes(null, scale, null);
        }

        @Override
        public void pasteRotation(Vector3d rotation)
        {
            UIModelGeometryEditor.this.pasteCubes(null, null, rotation);
        }

        @Override
        protected void reset()
        {
            UIModelGeometryEditor.this.pasteCubes(new Vector3d(), new Vector3d(1D, 1D, 1D), new Vector3d());
        }
    }
}
