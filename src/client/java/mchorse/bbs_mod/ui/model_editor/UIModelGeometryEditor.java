package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.bones.UIBoneTreeList;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;

/**
 * The model editor of the model panel: the model itself rather than its configuration. Its groups
 * as a tree, and for the picked one what the file says about it — the pivot it turns about and the
 * rotation it rests at. Edits go straight into the live model, so the preview shows them at once
 * (a pivot and a rest rotation are matrices, nothing is re-baked), and the panel writes the file
 * on save.
 *
 * <p>Bound per fill: the tree keeps its pick across one by name, since a save reloads the model
 * and every group object with it.</p>
 *
 * <p>The first cut: the tree and the two fields. In order after it: undo, the pivot on the gizmo,
 * adding, removing, renaming and dragging groups, moving a group with its geometry.</p>
 */
public class UIModelGeometryEditor extends UIElement
{
    private final UIModelEditorPanel modelPanel;

    private final UIScrollView page;
    private final UIBoneTreeList groups;
    private final UISearchList<String> search;
    private final UIElement body;

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

        this.body = new UIElement();
        this.body.column(UIConstants.MARGIN).vertical().stretch();

        this.page = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING, this.search, this.body);
        this.page.full(this);
        this.add(this.page);
    }

    /** The picked group, by name — what the viewport marks; null with nothing picked. */
    public String getSelected()
    {
        return this.model == null ? null : this.groups.getCurrentFirst();
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
     * The picked group's settings under the tree: its pivot and its rest rotation, straight off the
     * group. With nothing picked the same fields stand empty and disabled, so the page keeps its
     * height and the scroll doesn't jump on every pick.
     */
    private void fillGroup()
    {
        ModelGroup group = this.picked();
        Transform initial = group == null ? new Transform() : group.initial;

        this.body.removeAll();
        this.body.add(
            UI.label(UIKeys.MODEL_EDITOR_MODEL_PIVOT),
            UI.row(this.component(initial.translate, 0, false), this.component(initial.translate, 1, false), this.component(initial.translate, 2, false)),
            UI.label(UIKeys.TRANSFORMS_ROTATE),
            UI.row(this.component(initial.rotate, 0, true), this.component(initial.rotate, 1, true), this.component(initial.rotate, 2, true))
        );
        UIUtils.setEnabledDeep(this.body, group != null);

        this.page.resize();
        this.page.scroll.clamp();
    }

    /** One component of a group's vector; an edit lands in the model, and the panel is told there is something to save. */
    private UITrackpad component(Vector3f vector, int axis, boolean degrees)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            float value = v.floatValue();

            if (axis == 0) vector.x = value;
            else if (axis == 1) vector.y = value;
            else vector.z = value;

            this.modelPanel.markModelEdited();
        });

        trackpad.setValue(axis == 0 ? vector.x : axis == 1 ? vector.y : vector.z);
        trackpad.delayedInput();

        if (degrees)
        {
            trackpad.degrees();
        }

        return trackpad;
    }
}
