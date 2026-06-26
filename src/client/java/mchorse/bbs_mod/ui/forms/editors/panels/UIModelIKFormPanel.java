package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.ModelIKConfig;
import mchorse.bbs_mod.cubic.ik.ModelIKDebug;
import mchorse.bbs_mod.cubic.ik.ModelIKIO;
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
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.pose.ModelIKManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class UIModelIKFormPanel extends UIFormPanel<ModelForm>
{
    public UIStringList bones;

    public UIToggle debug;
    public UIToggle enabled;
    public UIButton target;
    public UITrackpad chainLength;
    public UIToggle pole;
    public UIButton poleTarget;
    public UITrackpad poleAngle;
    public UITrackpad softness;
    public UITrackpad weight;
    public UIToggle tipRotation;

    private String selectedBone = "";
    private Map<String, IKData> ikData = new HashMap<>();
    private String presetGroup = "";
    private boolean syncingUI;

    private static class IKData
    {
        public String target = "";
        public int chainLength = ModelIKConfig.DEFAULT_CHAIN_LENGTH;
        public boolean pole = true;
        public String poleTarget = ModelIKConfig.DEFAULT_POLE_TARGET;
        public float poleAngle = ModelIKConfig.DEFAULT_POLE_ANGLE;
        public float softness = ModelIKConfig.DEFAULT_SOFTNESS;
        public float weight = ModelIKConfig.DEFAULT_WEIGHT;
        public boolean enabled = true;
        public boolean tipRotation = ModelIKConfig.DEFAULT_TIP_ROTATION;
    }

    public UIModelIKFormPanel(UIForm editor)
    {
        super(editor);

        this.bones = new UIStringList((l) ->
        {
            this.selectedBone = l.isEmpty() ? "" : l.get(0);
            this.updateLabels();
        });
        this.bones.background().h(UIConstants.LIST_ITEM_HEIGHT * 8);
        this.bones.context(() -> new UIDataContextMenu(ModelIKManager.INSTANCE, this.presetGroup, this::toPresetData, this::applyPresetData).tooltips("_CopyModelIK",
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_COPY,
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_PASTE,
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_RESET,
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_SAVE,
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_NAME
        ));

        this.debug = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_DEBUG, (b) -> ModelIKDebug.enabled = b.getValue());
        this.debug.setValue(ModelIKDebug.enabled);

        this.enabled = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_ENABLED, (b) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.enabled = b.getValue();
            this.updateLabels();
            this.commitChanges();
        });

        this.target = new UIButton(IKey.EMPTY, (b) ->
        {
            if (this.selectedBone.isEmpty()) return;

            IKData data = this.getOrCreateData(this.selectedBone);
            this.openBoneMenu(data.target, (bone) ->
            {
                data.target = bone;
                this.updateLabels();
                this.commitChanges();
            });
        });

        this.chainLength = new UITrackpad((v) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.chainLength = Math.max(0, (int) v.floatValue());
            this.commitChanges();
        });
        this.chainLength.limit(0).integer();
        this.chainLength.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_CHAIN_LENGTH);

        this.pole = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_POLE, (b) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.pole = b.getValue();
            this.updateLabels();
            this.commitChanges();
        });

        this.poleTarget = new UIButton(IKey.EMPTY, (b) ->
        {
            if (this.selectedBone.isEmpty()) return;

            IKData data = this.getOrCreateData(this.selectedBone);
            this.openBoneMenu(data.poleTarget, (bone) ->
            {
                data.poleTarget = bone;
                this.updateLabels();
                this.commitChanges();
            });
        });

        this.poleAngle = new UITrackpad((v) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.poleAngle = v.floatValue();
            this.commitChanges();
        });
        this.poleAngle.limit(-180D, 180D).increment(5D).values(1D, 0.5D, 5D);
        this.poleAngle.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_POLE_ANGLE);

        this.softness = new UITrackpad((v) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.softness = v.floatValue();
            this.commitChanges();
        });
        this.softness.limit(0D, 1D).increment(0.05D).values(0.05D, 0.01D, 0.1D);
        this.softness.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_SOFTNESS);

        this.weight = new UITrackpad((v) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.weight = v.floatValue();
            this.commitChanges();
        });
        this.weight.limit(0D, 1D).increment(0.1D).values(0.1D, 0.05D, 0.2D);
        this.weight.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_WEIGHT);

        this.tipRotation = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_TIP_ROTATION, (b) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.tipRotation = b.getValue();
            this.commitChanges();
        });

        UISection settings = new UISection(UIKeys.FORMS_EDITORS_MODEL_IK_SETTINGS);

        settings.fields.add(
            this.enabled,
            this.target,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_IK_CHAIN_LENGTH).marginTop(UIConstants.SECTION_GAP),
            this.chainLength,
            this.pole,
            this.poleTarget,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_IK_POLE_ANGLE).marginTop(UIConstants.SECTION_GAP),
            this.poleAngle,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_IK_SOFTNESS).marginTop(UIConstants.SECTION_GAP),
            this.softness,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_IK_WEIGHT).marginTop(UIConstants.SECTION_GAP),
            this.weight,
            this.tipRotation
        );

        UISection bonesSection = new UISection(UIKeys.FORMS_EDITORS_MODEL_IK_BONES);

        bonesSection.fields.add(this.bones);

        this.options.add(
            this.debug,
            bonesSection,
            settings
        );
    }

    @Override
    public void startEdit(ModelForm form)
    {
        super.startEdit(form);

        ModelInstance model = ModelFormRenderer.getModel(form);
        this.presetGroup = this.resolvePresetGroup(form, model);

        if (model == null || model.model == null)
        {
            this.bones.setList(Collections.emptyList());
            this.bones.deselect();
            this.selectedBone = "";
            this.ikData.clear();

            this.setElementsEnabled(false);
        }
        else
        {
            List<String> bones = new ArrayList<>(model.model.getGroupKeysInHierarchyOrder());
            bones.removeIf(model.disabledBones::contains);

            this.bones.setList(bones);
            this.setElementsEnabled(true);

            this.load();
        }

        this.updateLabels();
        this.options.resize();
    }

    private void setElementsEnabled(boolean enabled)
    {
        this.bones.setEnabled(enabled);
        this.enabled.setEnabled(enabled);
        this.target.setEnabled(enabled);
        this.chainLength.setEnabled(enabled);
        this.pole.setEnabled(enabled);
        this.poleTarget.setEnabled(enabled);
        this.poleAngle.setEnabled(enabled);
        this.softness.setEnabled(enabled);
        this.weight.setEnabled(enabled);
        this.tipRotation.setEnabled(enabled);
    }

    @Override
    public boolean pickBoneInList(String bone)
    {
        if (bone == null || bone.isEmpty() || !this.bones.getList().contains(bone))
        {
            return false;
        }

        this.selectedBone = bone;
        this.bones.setCurrentScroll(bone);
        this.updateLabels();

        return true;
    }

    private void openBoneMenu(String current, Consumer<String> callback)
    {
        if (this.bones.getList().isEmpty())
        {
            return;
        }

        this.getContext().replaceContextMenu((menu) ->
        {
            boolean none = current == null || current.isEmpty();

            menu.action(Icons.REMOVE, UIKeys.GENERAL_NONE, none, () -> callback.accept(""));

            for (String bone : this.bones.getList())
            {
                boolean selected = bone.equals(current);

                menu.action(Icons.LIMB, IKey.constant(bone), selected, () -> callback.accept(bone));
            }
        });
    }

    private void updateLabels()
    {
        if (this.target == null || this.enabled == null)
        {
            return;
        }

        IKData data = this.ikData.get(this.selectedBone);

        String targetLabel = data == null ? "" : data.target;
        boolean active = data != null && data.enabled;
        boolean poleOn = data != null && data.pole;
        boolean canEdit = !this.selectedBone.isEmpty() && this.bones.isEnabled() && active;

        this.syncingUI = true;

        try
        {
            this.target.label = UIKeys.FORMS_EDITORS_MODEL_IK_TARGET.format(this.formatBone(targetLabel));
            this.chainLength.setValue(data == null ? ModelIKConfig.DEFAULT_CHAIN_LENGTH : data.chainLength);
            this.pole.setValue(poleOn);
            this.poleTarget.label = UIKeys.FORMS_EDITORS_MODEL_IK_POLE_TARGET.format(this.formatBone(data == null ? "" : data.poleTarget));
            this.poleAngle.setValue(data == null ? ModelIKConfig.DEFAULT_POLE_ANGLE : data.poleAngle);
            this.softness.setValue(data == null ? ModelIKConfig.DEFAULT_SOFTNESS : data.softness);
            this.weight.setValue(data == null ? ModelIKConfig.DEFAULT_WEIGHT : data.weight);
            this.tipRotation.setValue(data != null && data.tipRotation);
            this.enabled.setEnabled(this.bones.isEnabled() && !this.selectedBone.isEmpty());
            this.enabled.setValue(active);
        }
        finally
        {
            this.syncingUI = false;
        }

        this.target.setEnabled(canEdit);
        this.chainLength.setEnabled(canEdit);
        this.pole.setEnabled(canEdit);
        this.poleTarget.setEnabled(canEdit && poleOn);
        this.poleAngle.setEnabled(canEdit && poleOn);
        this.softness.setEnabled(canEdit);
        this.weight.setEnabled(canEdit);
        this.tipRotation.setEnabled(canEdit);
    }

    private IKData getOrCreateData(String bone)
    {
        return this.ikData.computeIfAbsent(bone, k -> new IKData());
    }

    private String formatBone(String bone)
    {
        return bone == null || bone.isEmpty() ? "-" : bone;
    }

    private void load()
    {
        ModelIKConfig config = null;
        if (this.form != null && this.form.ik.get() instanceof MapType map)
        {
            config = ModelIKIO.fromData(map);
        }

        this.load(config);
    }

    private void load(ModelIKConfig config)
    {
        this.ikData.clear();

        if (config == null || config.chains() == null)
        {
            return;
        }

        List<String> bones = this.bones.getList();
        boolean filterByBones = bones != null && !bones.isEmpty();

        for (ModelIKConfig.Chain chain : config.chains())
        {
            if (chain == null || chain.tip() == null || chain.tip().isEmpty())
            {
                continue;
            }

            if (filterByBones && !bones.contains(chain.tip()))
            {
                continue;
            }

            IKData data = new IKData();
            data.target = chain.target();
            data.chainLength = chain.chainLength();
            data.pole = chain.pole();
            data.poleTarget = chain.poleTarget();
            data.poleAngle = chain.poleAngle();
            data.softness = chain.softness();
            data.weight = chain.weight();
            data.enabled = chain.enabled();
            data.tipRotation = chain.tipRotation();
            this.ikData.put(chain.tip(), data);
        }
    }

    private MapType toPresetData()
    {
        List<String> bones = this.bones.getList();
        boolean filterByBones = bones != null && !bones.isEmpty();
        List<ModelIKConfig.Chain> out = new ArrayList<>();

        for (Map.Entry<String, IKData> entry : this.ikData.entrySet())
        {
            String tip = entry.getKey();
            IKData data = entry.getValue();

            if (tip == null || tip.isEmpty() || data == null)
            {
                continue;
            }

            if (data.target == null || data.target.isEmpty())
            {
                continue;
            }

            if (filterByBones && (!bones.contains(tip) || !bones.contains(data.target)))
            {
                continue;
            }

            out.add(new ModelIKConfig.Chain(tip, data.target, data.chainLength, data.pole, data.poleTarget, data.poleAngle, data.softness, data.weight, data.enabled, data.tipRotation));
        }

        if (out.isEmpty())
        {
            return new MapType();
        }

        return ModelIKIO.toData(new ModelIKConfig(out));
    }

    private void applyPresetData(MapType map)
    {
        String current = this.selectedBone;

        this.load(ModelIKIO.fromData(map));

        if (current == null || current.isEmpty() || !this.bones.getList().contains(current))
        {
            current = this.bones.getList().isEmpty() ? "" : this.bones.getList().get(0);
        }

        this.selectedBone = current;

        if (current.isEmpty())
        {
            this.bones.deselect();
        }
        else
        {
            this.bones.setCurrentScroll(current);
        }

        this.updateLabels();
        this.commitChanges();
    }

    private void commitChanges()
    {
        if (this.form == null)
        {
            return;
        }

        MapType map = this.toPresetData();
        this.form.ik.set(map.isEmpty() ? null : map);
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
