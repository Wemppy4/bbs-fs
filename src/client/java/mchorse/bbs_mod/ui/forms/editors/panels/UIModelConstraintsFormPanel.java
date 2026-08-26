package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.cubic.constraints.BoneConstraintsIO;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.utils.bones.UIBoneTreeList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.presets.UIDataContextMenu;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.ModelConstraintsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class UIModelConstraintsFormPanel extends UIFormPanel<ModelForm>
{
    public UIBoneTreeList bones;
    public UISearchList<String> bonesSearch;

    public UIToggle enabled;
    public UISliderTrackpad minX;
    public UISliderTrackpad minY;
    public UISliderTrackpad minZ;
    public UISliderTrackpad maxX;
    public UISliderTrackpad maxY;
    public UISliderTrackpad maxZ;
    public UIButton applyToChildren;

    private List<String> availableBones = Collections.emptyList();
    private String selectedBone = "";
    private ModelInstance modelInstance;
    private String presetGroup = "";

    public UIModelConstraintsFormPanel(UIForm editor)
    {
        super(editor);

        IKey axis = IKey.constant("%s (%s)");

        this.bones = new UIBoneTreeList((l) ->
        {
            this.selectedBone = l.isEmpty() ? "" : l.get(0);

            this.boneSelection().set(this.selectedBone);
            this.updateFields();
        });
        this.bones.background();
        this.bonesSearch = new UISearchList<>(this.bones);
        this.bonesSearch.label(UIKeys.GENERAL_SEARCH);
        this.bonesSearch.h(20 + UIConstants.LIST_ITEM_HEIGHT * 8);
        this.bones.context(() -> new UIDataContextMenu(ModelConstraintsManager.INSTANCE, this.presetGroup, this::toPresetData, this::applyPresetData).tooltips("_CopyModelConstraints",
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_COPY,
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_PASTE,
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_RESET,
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_SAVE,
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_NAME
        ));

        this.enabled = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_ENABLED, (b) ->
        {
            this.editConstraint((c) -> c.enabled = b.getValue());
            this.updateFieldsEnabled();
        });

        this.minX = axisTrackpad((v) -> this.editConstraint((c) -> c.minX = v.floatValue()), Colors.RED, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_X));
        this.minY = axisTrackpad((v) -> this.editConstraint((c) -> c.minY = v.floatValue()), Colors.GREEN, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_Y));
        this.minZ = axisTrackpad((v) -> this.editConstraint((c) -> c.minZ = v.floatValue()), Colors.BLUE, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_Z));
        this.maxX = axisTrackpad((v) -> this.editConstraint((c) -> c.maxX = v.floatValue()), Colors.RED, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_X));
        this.maxY = axisTrackpad((v) -> this.editConstraint((c) -> c.maxY = v.floatValue()), Colors.GREEN, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_Y));
        this.maxZ = axisTrackpad((v) -> this.editConstraint((c) -> c.maxZ = v.floatValue()), Colors.BLUE, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_Z));
        this.applyToChildren = new UIButton(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_APPLY_TO_CHILDREN, (b) -> this.applySelectedToChildren());

        UISection params = this.section(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_SETTINGS, "constraints.settings", true);

        params.fields.add(
            this.enabled,
            UI.label(IKey.constant("%s / %s").format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX)).marginTop(UIConstants.SECTION_GAP),
            UI.label(UIKeys.GENERAL_X),
            UI.row(this.minX, this.maxX),
            UI.label(UIKeys.GENERAL_Y),
            UI.row(this.minY, this.maxY),
            UI.label(UIKeys.GENERAL_Z),
            UI.row(this.minZ, this.maxZ),
            this.applyToChildren.marginTop(UIConstants.SECTION_GAP)
        );

        this.options.add(
            this.bonesSearch,
            params
        );
    }

    private static UISliderTrackpad axisTrackpad(Consumer<Double> c, int color, IKey tooltip)
    {
        UISliderTrackpad t = new UISliderTrackpad(c).angle180();
        t.textbox.setColor(color);
        t.tooltip(tooltip);
        return t;
    }

    @Override
    public void startEdit(ModelForm form)
    {
        super.startEdit(form);

        ModelInstance model = ModelFormRenderer.getModel(form);
        this.modelInstance = model;
        this.presetGroup = this.resolvePresetGroup(form, model);

        this.selectedBone = "";

        if (model == null || model.model == null)
        {
            this.availableBones = Collections.emptyList();
            this.bones.clear();
            this.setElementsEnabled(false);
            this.updateFields();
            this.options.resize();
            return;
        }

        List<String> bones = new ArrayList<>(model.model.getGroupKeysInHierarchyOrder());
        bones.removeIf(model.getDisabledBones()::contains);
        this.availableBones = bones;

        this.bones.fillBones(model.model, model.getDisabledBones());

        /* The fill resets the list's filter state, but the search box keeps its
         * text across startEdit — reapply so what you see matches the query. */
        this.bones.filter(this.bonesSearch.search.getText());
        this.setElementsEnabled(true);

        /* Same as the other bone-list panels: keep the animator on the bone
         * they are working on across a rebuild instead of resetting to the root. */
        if (this.pickBoneInList(this.boneSelection().get()))
        {
            /* Already selected and filled in. */
        }
        else if (!bones.isEmpty())
        {
            this.selectBone(bones.get(0));
        }
        else
        {
            this.updateFields();
        }

        this.options.resize();
    }

    @Override
    public boolean pickBoneInList(String bone)
    {
        if (bone == null || bone.isEmpty() || !this.bones.getList().contains(bone))
        {
            return false;
        }

        this.selectBone(bone);
        this.boneSelection().set(bone);

        return true;
    }

    private void selectBone(String bone)
    {
        this.selectedBone = bone == null ? "" : bone;

        if (!this.selectedBone.isEmpty())
        {
            this.bones.setCurrentScroll(this.selectedBone);
        }

        this.updateFields();
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

    private void updateFields()
    {
        BoneConstraint c = this.currentConstraint();

        this.enabled.setValue(c.enabled);
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
        boolean active = panelEnabled && this.enabled.getValue() && !this.selectedBone.isEmpty();
        boolean hasChildren = active && !this.getDescendantBones(this.selectedBone).isEmpty();

        this.applyToChildren.setEnabled(hasChildren);
        this.minX.setEnabled(active);
        this.minY.setEnabled(active);
        this.minZ.setEnabled(active);
        this.maxX.setEnabled(active);
        this.maxY.setEnabled(active);
        this.maxZ.setEnabled(active);
    }

    private void applySelectedToChildren()
    {
        if (this.form == null || this.selectedBone.isEmpty() || !this.enabled.getValue())
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

    private void setElementsEnabled(boolean enabled)
    {
        this.bonesSearch.setEnabled(enabled);
        this.bones.setEnabled(enabled);
        this.enabled.setEnabled(enabled);
        this.applyToChildren.setEnabled(enabled);
        this.minX.setEnabled(enabled);
        this.minY.setEnabled(enabled);
        this.minZ.setEnabled(enabled);
        this.maxX.setEnabled(enabled);
        this.maxY.setEnabled(enabled);
        this.maxZ.setEnabled(enabled);
        this.updateFieldsEnabled();
    }

    private MapType toPresetData()
    {
        return this.form == null ? new MapType() : BoneConstraintsIO.write(this.form.bones);
    }

    private void applyPresetData(MapType map)
    {
        if (this.form == null)
        {
            return;
        }

        BoneConstraintsIO.read(map, this.form.bones, true);

        this.updateFields();
    }

    private String resolvePresetGroup(ModelForm form, ModelInstance model)
    {
        String group = model != null ? model.getPoseGroup() : "";

        if (group == null || group.isEmpty())
        {
            group = form == null ? "" : form.model.get();
        }

        return group == null ? "" : group;
    }
}
