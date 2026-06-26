package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsConfig;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsIO;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.presets.UIDataContextMenu;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.ModelConstraintsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class UIModelConstraintsFormPanel extends UIFormPanel<ModelForm>
{
    private static final float DEFAULT_MIN = -180F;
    private static final float DEFAULT_MAX = 180F;

    public UIStringList bones;

    public UIToggle enabled;
    public UITrackpad minX;
    public UITrackpad minY;
    public UITrackpad minZ;
    public UITrackpad maxX;
    public UITrackpad maxY;
    public UITrackpad maxZ;
    public UIButton applyToChildren;

    private List<String> availableBones = Collections.emptyList();
    private String selectedBone = "";
    private final Map<String, ModelConstraintsConfig.BoneConstraint> data = new HashMap<>();
    private ModelInstance modelInstance;
    private String presetGroup = "";
    private boolean syncingUI;

    public UIModelConstraintsFormPanel(UIForm editor)
    {
        super(editor);

        IKey axis = IKey.constant("%s (%s)");

        this.bones = new UIStringList((l) ->
        {
            this.selectedBone = l.isEmpty() ? "" : l.get(0);
            this.updateFields();
        });
        this.bones.background().h(UIConstants.LIST_ITEM_HEIGHT * 8);
        this.bones.context(() -> new UIDataContextMenu(ModelConstraintsManager.INSTANCE, this.presetGroup, this::toPresetData, this::applyPresetData).tooltips("_CopyModelConstraints",
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_COPY,
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_PASTE,
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_RESET,
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_SAVE,
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_NAME
        ));

        this.enabled = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_ENABLED, (b) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            if (b.getValue())
            {
                this.data.put(this.selectedBone, this.readFromFields(true));
            }
            else
            {
                this.data.remove(this.selectedBone);
            }

            this.updateFieldsEnabled();
            this.commitChanges();
        });

        this.minX = axisTrackpad((v) -> this.onFieldChanged(), Colors.RED, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_X));
        this.minY = axisTrackpad((v) -> this.onFieldChanged(), Colors.GREEN, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_Y));
        this.minZ = axisTrackpad((v) -> this.onFieldChanged(), Colors.BLUE, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_Z));
        this.maxX = axisTrackpad((v) -> this.onFieldChanged(), Colors.RED, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_X));
        this.maxY = axisTrackpad((v) -> this.onFieldChanged(), Colors.GREEN, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_Y));
        this.maxZ = axisTrackpad((v) -> this.onFieldChanged(), Colors.BLUE, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_Z));
        this.applyToChildren = new UIButton(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_APPLY_TO_CHILDREN, (b) -> this.applySelectedToChildren());

        UISection bonesSection = new UISection(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_BONES);

        bonesSection.fields.add(this.bones);

        UISection params = new UISection(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_SETTINGS);

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
            bonesSection,
            params.marginTop(UIConstants.SECTION_GAP)
        );
    }

    private static UITrackpad axisTrackpad(Consumer<Double> c, int color, IKey tooltip)
    {
        UITrackpad t = new UITrackpad(c).degrees().onlyNumbers().limit(-180D, 180D);
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

        this.data.clear();
        this.selectedBone = "";

        if (model == null || model.model == null)
        {
            this.availableBones = Collections.emptyList();
            this.bones.setList(Collections.emptyList());
            this.bones.deselect();
            this.setElementsEnabled(false);
            this.setDefaults();
            this.enabled.setValue(false);
            this.options.resize();
            return;
        }

        List<String> bones = new ArrayList<>(model.model.getGroupKeysInHierarchyOrder());
        bones.removeIf(model.disabledBones::contains);
        this.availableBones = bones;

        this.bones.setList(bones);
        this.setElementsEnabled(true);

        ModelConstraintsConfig config = null;
        if (this.form != null && this.form.constraints.get() instanceof MapType map)
        {
            config = ModelConstraintsIO.fromData(map);
        }

        if (config != null && config.bones() != null)
        {
            this.load(config);
        }

        if (!bones.isEmpty())
        {
            this.selectBone(bones.get(0));
        }
        else
        {
            this.setDefaults();
            this.enabled.setValue(false);
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

    private void updateFields()
    {
        if (this.selectedBone.isEmpty())
        {
            this.syncingUI = true;
            this.enabled.setValue(false);
            this.setDefaults();
            this.syncingUI = false;
            this.updateFieldsEnabled();
            return;
        }

        ModelConstraintsConfig.BoneConstraint c = this.data.get(this.selectedBone);

        this.syncingUI = true;

        try
        {
            if (c == null || !c.enabled())
            {
                this.enabled.setValue(false);
                this.setDefaults();
            }
            else
            {
                this.enabled.setValue(true);
                this.minX.setValue(c.minX());
                this.minY.setValue(c.minY());
                this.minZ.setValue(c.minZ());
                this.maxX.setValue(c.maxX());
                this.maxY.setValue(c.maxY());
                this.maxZ.setValue(c.maxZ());
            }
        }
        finally
        {
            this.syncingUI = false;
        }

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

    private void setDefaults()
    {
        this.minX.setValue(DEFAULT_MIN);
        this.minY.setValue(DEFAULT_MIN);
        this.minZ.setValue(DEFAULT_MIN);
        this.maxX.setValue(DEFAULT_MAX);
        this.maxY.setValue(DEFAULT_MAX);
        this.maxZ.setValue(DEFAULT_MAX);
    }

    private void onFieldChanged()
    {
        if (this.syncingUI || !this.enabled.getValue() || this.selectedBone.isEmpty())
        {
            return;
        }

        this.data.put(this.selectedBone, this.readFromFields(true));
        this.commitChanges();
    }

    private ModelConstraintsConfig.BoneConstraint readFromFields(boolean enabled)
    {
        return new ModelConstraintsConfig.BoneConstraint(
            enabled,
            (float) this.minX.getValue(),
            (float) this.minY.getValue(),
            (float) this.minZ.getValue(),
            (float) this.maxX.getValue(),
            (float) this.maxY.getValue(),
            (float) this.maxZ.getValue()
        );
    }

    private void applySelectedToChildren()
    {
        if (this.syncingUI || this.selectedBone.isEmpty() || !this.enabled.getValue())
        {
            return;
        }

        List<String> descendants = this.getDescendantBones(this.selectedBone);

        if (descendants.isEmpty())
        {
            return;
        }

        ModelConstraintsConfig.BoneConstraint constraint = this.readFromFields(true);

        for (String child : descendants)
        {
            this.data.put(child, constraint);
        }

        this.commitChanges();
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

    private void commitChanges()
    {
        if (this.form == null)
        {
            return;
        }

        MapType map = this.toPresetData();
        this.form.constraints.set(map.isEmpty() ? null : map);
    }

    private void setElementsEnabled(boolean enabled)
    {
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

    private void load(ModelConstraintsConfig config)
    {
        this.data.clear();

        if (config == null || config.bones() == null)
        {
            return;
        }

        for (Map.Entry<String, ModelConstraintsConfig.BoneConstraint> entry : config.bones().entrySet())
        {
            String bone = entry.getKey();
            ModelConstraintsConfig.BoneConstraint constraint = entry.getValue();

            if (bone == null || bone.isEmpty() || constraint == null || !constraint.enabled())
            {
                continue;
            }

            if (!this.availableBones.isEmpty() && !this.availableBones.contains(bone))
            {
                continue;
            }

            this.data.put(bone, constraint);
        }
    }

    private MapType toPresetData()
    {
        Map<String, ModelConstraintsConfig.BoneConstraint> out = new HashMap<>();

        for (Map.Entry<String, ModelConstraintsConfig.BoneConstraint> entry : this.data.entrySet())
        {
            String bone = entry.getKey();
            ModelConstraintsConfig.BoneConstraint constraint = entry.getValue();

            if (bone == null || bone.isEmpty() || constraint == null || !constraint.enabled())
            {
                continue;
            }

            if (!this.availableBones.isEmpty() && !this.availableBones.contains(bone))
            {
                continue;
            }

            out.put(bone, constraint);
        }

        if (out.isEmpty())
        {
            return new MapType();
        }

        return ModelConstraintsIO.toData(new ModelConstraintsConfig(out));
    }

    private void applyPresetData(MapType map)
    {
        String current = this.selectedBone;

        this.load(ModelConstraintsIO.fromData(map));

        if (current == null || current.isEmpty() || !this.availableBones.contains(current))
        {
            current = this.availableBones.isEmpty() ? "" : this.availableBones.get(0);
        }

        if (current.isEmpty())
        {
            this.selectedBone = "";
            this.bones.deselect();
            this.updateFields();
        }
        else
        {
            this.selectBone(current);
        }

        this.commitChanges();
    }

    private String resolvePresetGroup(ModelForm form, ModelInstance model)
    {
        String group = model != null ? model.poseGroup : "";

        if (group == null || group.isEmpty())
        {
            group = form == null ? "" : form.model.get();
        }

        return group == null ? "" : group;
    }
}
