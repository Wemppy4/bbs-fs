package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.ModelIKConfig;
import mchorse.bbs_mod.cubic.ik.ModelIKIO;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.presets.UIDataContextMenu;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.ModelIKManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UIModelIKFormPanel extends UIFormPanel<ModelForm>
{
    public UIStringList bones;

    public UIToggle enabled;
    public UIButton locator;
    public UIButton root;
    public UITrackpad poleX;
    public UITrackpad poleY;
    public UITrackpad poleZ;

    private String selectedBone = "";
    private Map<String, IKData> ikData = new HashMap<>();
    private String presetGroup = "";
    private boolean syncingUI;

    private static class IKData
    {
        public String locator = "";
        public String root = "";
        public boolean enabled = true;
        public float poleX;
        public float poleY;
        public float poleZ;
    }

    public UIModelIKFormPanel(UIForm editor)
    {
        super(editor);

        IKey raw = IKey.constant("%s (%s)");

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

        this.locator = new UIButton(IKey.EMPTY, (b) ->
        {
            if (this.selectedBone.isEmpty()) return;
            
            IKData data = this.getOrCreateData(this.selectedBone);
            this.openBoneMenu(data.locator, (bone) ->
            {
                data.locator = bone;
                this.updateLabels();
                this.commitChanges();
            });
        });

        this.root = new UIButton(IKey.EMPTY, (b) ->
        {
            if (this.selectedBone.isEmpty()) return;

            IKData data = this.getOrCreateData(this.selectedBone);
            this.openBoneMenu(data.root, (bone) ->
            {
                data.root = bone;
                this.updateLabels();
                this.commitChanges();
            });
        });

        this.poleX = new UITrackpad((v) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.poleX = v.floatValue();
            this.commitChanges();
        });
        this.poleX.block().onlyNumbers();
        this.poleX.tooltip(raw.format(UIKeys.FORMS_EDITORS_MODEL_IK_POLE, UIKeys.GENERAL_X));
        this.poleX.textbox.setColor(Colors.RED);
        this.poleY = new UITrackpad((v) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.poleY = v.floatValue();
            this.commitChanges();
        });
        this.poleY.block().onlyNumbers();
        this.poleY.tooltip(raw.format(UIKeys.FORMS_EDITORS_MODEL_IK_POLE, UIKeys.GENERAL_Y));
        this.poleY.textbox.setColor(Colors.GREEN);
        this.poleZ = new UITrackpad((v) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.poleZ = v.floatValue();
            this.commitChanges();
        });
        this.poleZ.block().onlyNumbers();
        this.poleZ.tooltip(raw.format(UIKeys.FORMS_EDITORS_MODEL_IK_POLE, UIKeys.GENERAL_Z));
        this.poleZ.textbox.setColor(Colors.BLUE);

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_MODEL_IK_BONES),
            this.bones,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_IK_SETTINGS).background().marginTop(UIConstants.SECTION_GAP),
            this.enabled,
            this.root,
            this.locator,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_IK_POLE).marginTop(UIConstants.SECTION_GAP),
            UI.row(2, 0, UIConstants.CONTROL_HEIGHT, this.poleX, this.poleY, this.poleZ)
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
        this.locator.setEnabled(enabled);
        this.root.setEnabled(enabled);
        this.poleX.setEnabled(enabled);
        this.poleY.setEnabled(enabled);
        this.poleZ.setEnabled(enabled);
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

    private void openBoneMenu(String current, java.util.function.Consumer<String> callback)
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
        if (this.locator == null || this.root == null || this.enabled == null)
        {
            return;
        }

        IKData data = this.ikData.get(this.selectedBone);
        
        String locatorLabel = data == null ? "" : data.locator;
        String rootLabel = data == null ? "" : data.root;
        boolean active = data != null && data.enabled;
        boolean canEdit = !this.selectedBone.isEmpty() && this.bones.isEnabled() && active;

        this.syncingUI = true;

        try
        {
            this.locator.label = UIKeys.FORMS_EDITORS_MODEL_IK_LOCATOR.format(this.formatBone(locatorLabel));
            this.root.label = UIKeys.FORMS_EDITORS_MODEL_IK_ROOT.format(this.formatBone(rootLabel));
            this.poleX.setValue(data == null ? 0F : data.poleX);
            this.poleY.setValue(data == null ? 0F : data.poleY);
            this.poleZ.setValue(data == null ? 0F : data.poleZ);
            this.enabled.setEnabled(this.bones.isEnabled() && !this.selectedBone.isEmpty());
            this.enabled.setValue(active);
        }
        finally
        {
            this.syncingUI = false;
        }

        this.locator.setEnabled(canEdit);
        this.root.setEnabled(canEdit);
        this.poleX.setEnabled(canEdit);
        this.poleY.setEnabled(canEdit);
        this.poleZ.setEnabled(canEdit);
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
            if (chain == null || chain.controller() == null || chain.controller().isEmpty())
            {
                continue;
            }

            if (filterByBones && (!bones.contains(chain.controller()) || !bones.contains(chain.locator()) || !bones.contains(chain.root())))
            {
                continue;
            }

            IKData data = new IKData();
            data.locator = chain.locator();
            data.root = chain.root();
            data.enabled = chain.enabled();
            data.poleX = chain.poleX();
            data.poleY = chain.poleY();
            data.poleZ = chain.poleZ();
            this.ikData.put(chain.controller(), data);
        }
    }

    private MapType toPresetData()
    {
        List<String> bones = this.bones.getList();
        boolean filterByBones = bones != null && !bones.isEmpty();
        List<ModelIKConfig.Chain> out = new ArrayList<>();

        for (Map.Entry<String, IKData> entry : this.ikData.entrySet())
        {
            String controller = entry.getKey();
            IKData data = entry.getValue();

            if (controller == null || controller.isEmpty() || data == null)
            {
                continue;
            }

            if (data.locator == null || data.locator.isEmpty() || data.root == null || data.root.isEmpty())
            {
                continue;
            }

            if (filterByBones && (!bones.contains(controller) || !bones.contains(data.locator) || !bones.contains(data.root)))
            {
                continue;
            }

            out.add(new ModelIKConfig.Chain(controller, data.locator, data.root, data.enabled, data.poleX, data.poleY, data.poleZ, ModelIKConfig.PoleSpace.ROOT, ModelIKConfig.DEFAULT_WEIGHT));
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
