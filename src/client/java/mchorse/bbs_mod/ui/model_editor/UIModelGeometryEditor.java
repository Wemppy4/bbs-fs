package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.bones.UIBoneTreeList;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;

/**
 * The model editor of the model panel: the model itself rather than its configuration. Its groups
 * as a tree, and for the picked one its rest — the pivot it turns about and the rotation it rests
 * at — on the viewport gizmo and in a transform editor. Edits land in the live model (the preview
 * shows them at once: a rest is a matrix, nothing is re-baked), go on the panel's undo stack as
 * snapshots of the model ({@link ModelEditUndo}), and the panel writes the file on save.
 *
 * <p>The transform editor works in radians on a stand-in transform; the group rests in degrees.
 * The stand-in is loaded from the group on every fill and pushed back into the group after every
 * edit — and every frame while the group is picked, since a gizmo drag's sampling nudges the
 * stand-in and re-evaluates the model through it.</p>
 *
 * <p>Bound per fill: the tree keeps its pick across one by name, since a save reloads the model
 * and every group object with it.</p>
 */
public class UIModelGeometryEditor extends UIElement
{
    /** How close a group's rest already is to the stand-in's numbers to be left alone — the round trip through radians isn't exact. */
    private static final float EPSILON = 1E-4F;

    private final UIModelEditorPanel modelPanel;

    private final UIScrollView page;
    private final UIBoneTreeList groups;
    private final UISearchList<String> search;
    private final UIElement body;

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

        this.groups = new UIBoneTreeList((list) -> this.fillGroup());
        this.groups.background();
        this.search = new UISearchList<>(this.groups);
        this.search.label(UIKeys.GENERAL_SEARCH);
        this.search.h(UIStringList.DEFAULT_HEIGHT * 8 - 8).expand();

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

        this.body = new UIElement();
        this.body.column(UIConstants.MARGIN).vertical().stretch();
        this.body.add(this.transform);

        this.page = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING, this.search, this.body);
        this.page.full(this);
        this.add(this.page);
    }

    /** The picked group, by name — what the viewport marks; null with nothing picked. */
    public String getSelected()
    {
        return this.model == null ? null : this.groups.getCurrentFirst();
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
        this.groups.setCurrentScroll(bone);
        this.fillGroup();

        return true;
    }

    private ModelGroup picked()
    {
        String id = this.getSelected();

        return id == null ? null : this.model.getGroup(id);
    }

    /**
     * The picked group's rest in the transform editor. With nothing picked the editor stands empty
     * and disabled, so the page keeps its height and the scroll doesn't jump on every pick.
     */
    private void fillGroup()
    {
        ModelGroup group = this.picked();

        this.loadAnchor(group);
        this.transform.setTransform(this.anchor);
        UIUtils.setEnabledDeep(this.body, group != null);

        this.page.resize();
        this.page.scroll.clamp();
    }

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
}
