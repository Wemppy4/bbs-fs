package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.cubic.constraints.BoneConstraintsIO;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.ModelConstraintsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class UIModelConstraintsFormPanel extends UIBoneListFormPanel
{
    public UIToggle limitX;
    public UIToggle limitY;
    public UIToggle limitZ;
    public UISliderTrackpad minX;
    public UISliderTrackpad minY;
    public UISliderTrackpad minZ;
    public UISliderTrackpad maxX;
    public UISliderTrackpad maxY;
    public UISliderTrackpad maxZ;
    public UIButton applyToChildren;

    /** The angle pairs, shown only for the axes whose switch is on — as in the IK joint. */
    private UIElement limitRowX;
    private UIElement limitRowY;
    private UIElement limitRowZ;

    public UIModelConstraintsFormPanel(UIForm editor)
    {
        super(editor);

        IKey axis = IKey.constant("%s (%s)");

        this.bonePresets(ModelConstraintsManager.INSTANCE, "_CopyModelConstraints",
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_COPY,
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_PASTE,
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_RESET,
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_SAVE,
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_NAME
        );

        this.limitX = this.axisToggle(UIKeys.GENERAL_X, (c, v) -> c.limitX = v);
        this.limitY = this.axisToggle(UIKeys.GENERAL_Y, (c, v) -> c.limitY = v);
        this.limitZ = this.axisToggle(UIKeys.GENERAL_Z, (c, v) -> c.limitZ = v);

        this.minX = axisTrackpad((v) -> this.editConstraint((c) -> c.minX = v.floatValue()), Colors.RED, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_X));
        this.minY = axisTrackpad((v) -> this.editConstraint((c) -> c.minY = v.floatValue()), Colors.GREEN, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_Y));
        this.minZ = axisTrackpad((v) -> this.editConstraint((c) -> c.minZ = v.floatValue()), Colors.BLUE, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_Z));
        this.maxX = axisTrackpad((v) -> this.editConstraint((c) -> c.maxX = v.floatValue()), Colors.RED, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_X));
        this.maxY = axisTrackpad((v) -> this.editConstraint((c) -> c.maxY = v.floatValue()), Colors.GREEN, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_Y));
        this.maxZ = axisTrackpad((v) -> this.editConstraint((c) -> c.maxZ = v.floatValue()), Colors.BLUE, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_Z));
        this.applyToChildren = new UIButton(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_APPLY_TO_CHILDREN, (b) -> this.applySelectedToChildren());

        UISection params = this.section(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_SETTINGS, "constraints.settings", true);

        this.limitRowX = UI.row(this.minX, this.maxX);
        this.limitRowY = UI.row(this.minY, this.maxY);
        this.limitRowZ = UI.row(this.minZ, this.maxZ);

        /* Same shape as the IK joint limits — a named header per axis carrying its
         * switch, the two angles in the row below — minus the parts a bone
         * constraint has no say in: no lock (nothing is being solved here, so
         * there is no channel to freeze) and no stiffness. */
        params.fields.add(
            this.axisHeader(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_AXIS.format(UIKeys.GENERAL_X), this.limitX),
            this.limitRowX,
            this.axisHeader(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_AXIS.format(UIKeys.GENERAL_Y), this.limitY),
            this.limitRowY,
            this.axisHeader(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_AXIS.format(UIKeys.GENERAL_Z), this.limitZ),
            this.limitRowZ,
            this.applyToChildren.marginTop(UIConstants.SECTION_GAP)
        );

        this.options.add(
            this.bonesSearch,
            params
        );
    }

    /** One axis' switch: its own name as a tooltip, the constraint edited like any other field. */
    private UIToggle axisToggle(IKey axis, BiConsumer<BoneConstraint, Boolean> setter)
    {
        UIToggle toggle = new UIToggle(IKey.EMPTY, (b) ->
        {
            this.editConstraint((c) -> setter.accept(c, b.getValue()));
            this.updateFields();
        });

        toggle.tooltip(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LIMIT.format(axis));

        return toggle;
    }

    /** An axis' header: its name on the left, its switch pinned right — the IK joint's row without the lock. */
    private UIElement axisHeader(IKey label, UIToggle limit)
    {
        UIElement row = new UIElement();

        row.row(UIConstants.MARGIN).preferred(0).height(UIConstants.CONTROL_HEIGHT);
        row.add(UI.label(label, UIConstants.CONTROL_HEIGHT).labelAnchor(0, 0.5F), limit.w(26));

        return row;
    }

    /** The selected bone's constraint as stored, or the neutral default when never touched. */
    private BoneConstraint currentConstraint()
    {
        if (this.form != null && !this.selectedBone.isEmpty())
        {
            FormBone bone = this.form.bones.getBone(this.selectedBone);

            if (bone != null)
            {
                return bone.constraints.get();
            }
        }

        return BoneConstraint.DEFAULT;
    }

    /** Edits the selected bone's constraint as a value change (one undo entry, one notification). */
    private void editConstraint(Consumer<BoneConstraint> edit)
    {
        if (this.form == null || this.selectedBone.isEmpty())
        {
            return;
        }

        FormBone bone = this.form.bones.getOrCreate(this.selectedBone);
        BoneConstraint constraint = bone.constraints.get().copy();

        edit.accept(constraint);
        bone.constraints.set(constraint);
    }

    @Override
    protected void updateFields()
    {
        BoneConstraint c = this.currentConstraint();

        this.limitX.setValue(c.limitX);
        this.limitY.setValue(c.limitY);
        this.limitZ.setValue(c.limitZ);
        this.limitRowX.setVisible(c.limitX);
        this.limitRowY.setVisible(c.limitY);
        this.limitRowZ.setVisible(c.limitZ);
        this.options.resize();
        this.minX.setValue(c.minX);
        this.minY.setValue(c.minY);
        this.minZ.setValue(c.minZ);
        this.maxX.setValue(c.maxX);
        this.maxY.setValue(c.maxY);
        this.maxZ.setValue(c.maxZ);

        this.updateFieldsEnabled();
    }

    private void updateFieldsEnabled()
    {
        boolean panelEnabled = this.bones.isEnabled();
        boolean active = panelEnabled && !this.selectedBone.isEmpty();
        boolean hasChildren = active && !this.getDescendantBones(this.selectedBone).isEmpty();

        BoneConstraint c = this.currentConstraint();

        this.applyToChildren.setEnabled(hasChildren);
        this.limitX.setEnabled(active);
        this.limitY.setEnabled(active);
        this.limitZ.setEnabled(active);
        this.minX.setEnabled(active && c.limitX);
        this.minY.setEnabled(active && c.limitY);
        this.minZ.setEnabled(active && c.limitZ);
        this.maxX.setEnabled(active && c.limitX);
        this.maxY.setEnabled(active && c.limitY);
        this.maxZ.setEnabled(active && c.limitZ);
    }

    private void applySelectedToChildren()
    {
        if (this.form == null || this.selectedBone.isEmpty())
        {
            return;
        }

        List<String> descendants = this.getDescendantBones(this.selectedBone);

        if (descendants.isEmpty())
        {
            return;
        }

        BoneConstraint constraint = this.currentConstraint();

        for (String child : descendants)
        {
            this.form.bones.getOrCreate(child).constraints.set(constraint.copy());
        }
    }

    private List<String> getDescendantBones(String bone)
    {
        if (bone == null || bone.isEmpty() || this.modelInstance == null || this.modelInstance.model == null)
        {
            return Collections.emptyList();
        }

        IModel model = this.modelInstance.model;

        List<String> descendants = new ArrayList<>(model.getAllChildrenKeys(bone));

        if (!this.availableBones.isEmpty())
        {
            descendants.removeIf((id) -> !this.availableBones.contains(id));
        }

        return descendants;
    }

    @Override
    protected void setElementsEnabled(boolean enabled)
    {
        this.bonesSearch.setEnabled(enabled);
        this.bones.setEnabled(enabled);
        this.limitX.setEnabled(enabled);
        this.limitY.setEnabled(enabled);
        this.limitZ.setEnabled(enabled);
        this.applyToChildren.setEnabled(enabled);
        this.minX.setEnabled(enabled);
        this.minY.setEnabled(enabled);
        this.minZ.setEnabled(enabled);
        this.maxX.setEnabled(enabled);
        this.maxY.setEnabled(enabled);
        this.maxZ.setEnabled(enabled);
        this.updateFieldsEnabled();
    }

    @Override
    protected MapType toPresetData()
    {
        return this.form == null ? new MapType() : BoneConstraintsIO.write(this.form.bones);
    }

    @Override
    protected void applyPresetData(MapType map)
    {
        if (this.form == null)
        {
            return;
        }

        BoneConstraintsIO.read(map, this.form.bones, true);

        this.updateFields();
    }

}
