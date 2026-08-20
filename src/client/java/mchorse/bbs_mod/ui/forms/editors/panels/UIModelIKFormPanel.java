package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.ModelIKConfig;
import mchorse.bbs_mod.cubic.ik.ModelIKIO;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.utils.UIDebugOverlayContextMenu;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.utils.PickedBone;
import mchorse.bbs_mod.ui.utils.bones.UIBonePicker;
import mchorse.bbs_mod.ui.utils.bones.UIBonePickerContextMenu;
import mchorse.bbs_mod.ui.utils.bones.UIBoneTreeList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.presets.UIDataContextMenu;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.ModelIKManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class UIModelIKFormPanel extends UIFormPanel<ModelForm>
{
    /* Bone list role dots — the same yellow the film's IK sheet uses for a chain. */
    private static final int MARKER_CHAIN = Colors.A100 | Colors.YELLOW;
    private static final int MARKER_TARGET = Colors.A100 | Colors.CYAN;
    private static final int MARKER_POLE = Colors.A100 | Colors.MAGENTA;
    private static final int MARKER_JOINT = Colors.A100 | Colors.ORANGE;
    private static final int MARKER_OFF = Colors.GRAY;

    public UIBoneTreeList bones;
    public UISearchList<String> bonesSearch;

    public UIToggle debug;
    public UIToggle enabled;
    public UIBonePicker target;
    public UITrackpad chainLength;
    public UILabel chainPreview;
    public UIToggle pole;
    public UIBonePicker poleTarget;
    public UISliderTrackpad poleAngle;
    public UISliderTrackpad softness;
    public UISliderTrackpad weight;
    public UIToggle tipRotation;
    public UIToggle classic;

    public UIIcon lockX;
    public UIIcon lockY;
    public UIIcon lockZ;
    public UIToggle limitX;
    public UIToggle limitY;
    public UIToggle limitZ;
    public UISliderTrackpad limitMinX;
    public UISliderTrackpad limitMaxX;
    public UISliderTrackpad limitMinY;
    public UISliderTrackpad limitMaxY;
    public UISliderTrackpad limitMinZ;
    public UISliderTrackpad limitMaxZ;
    public UISliderTrackpad stiffnessX;
    public UISliderTrackpad stiffnessY;
    public UISliderTrackpad stiffnessZ;
    public UIToggle stretch;

    private String selectedBone = "";
    private Map<String, IKData> ikData = new HashMap<>();
    private Map<String, JointData> jointData = new HashMap<>();
    private final Map<String, UIBoneTreeList.Marker[]> boneMarkers = new HashMap<>();
    private ModelInstance model;
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
        public boolean stretch = ModelIKConfig.DEFAULT_STRETCH;
        public boolean classic = ModelIKConfig.DEFAULT_CLASSIC;
    }

    /** Mutable UI shadow of {@link ModelIKConfig.JointDoF} — the selected bone's joint freedom. */
    private static class JointData
    {
        public boolean lockX, lockY, lockZ;
        public boolean limitX, limitY, limitZ;
        public float minX = ModelIKConfig.JointDoF.DEFAULT_MIN;
        public float maxX = ModelIKConfig.JointDoF.DEFAULT_MAX;
        public float minY = ModelIKConfig.JointDoF.DEFAULT_MIN;
        public float maxY = ModelIKConfig.JointDoF.DEFAULT_MAX;
        public float minZ = ModelIKConfig.JointDoF.DEFAULT_MIN;
        public float maxZ = ModelIKConfig.JointDoF.DEFAULT_MAX;
        public float stiffnessX, stiffnessY, stiffnessZ;

        public static JointData from(ModelIKConfig.JointDoF dof)
        {
            JointData data = new JointData();

            data.lockX = dof.lockX();
            data.lockY = dof.lockY();
            data.lockZ = dof.lockZ();
            data.limitX = dof.limitX();
            data.limitY = dof.limitY();
            data.limitZ = dof.limitZ();
            data.minX = dof.minX();
            data.maxX = dof.maxX();
            data.minY = dof.minY();
            data.maxY = dof.maxY();
            data.minZ = dof.minZ();
            data.maxZ = dof.maxZ();
            data.stiffnessX = dof.stiffnessX();
            data.stiffnessY = dof.stiffnessY();
            data.stiffnessZ = dof.stiffnessZ();

            return data;
        }

        public ModelIKConfig.JointDoF toDoF()
        {
            return new ModelIKConfig.JointDoF(this.lockX, this.lockY, this.lockZ,
                this.limitX, this.minX, this.maxX,
                this.limitY, this.minY, this.maxY,
                this.limitZ, this.minZ, this.maxZ,
                this.stiffnessX, this.stiffnessY, this.stiffnessZ);
        }
    }

    public UIModelIKFormPanel(UIForm editor)
    {
        super(editor);

        this.bones = new UIBoneTreeList((l) ->
        {
            this.selectedBone = l.isEmpty() ? "" : l.get(0);

            PickedBone.set(this.selectedBone);
            this.updateLabels();
        });
        this.bones.background();
        this.bones.markers(this.boneMarkers::get, UIKeys.FORMS_EDITORS_MODEL_IK_BONES_TOOLTIP);
        this.bonesSearch = new UISearchList<>(this.bones);
        this.bonesSearch.label(UIKeys.GENERAL_SEARCH);
        this.bonesSearch.h(20 + UIConstants.LIST_ITEM_HEIGHT * 8);
        this.bones.context(() -> new UIDataContextMenu(ModelIKManager.INSTANCE, this.presetGroup, this::toPresetData, this::applyPresetData).tooltips("_CopyModelIK",
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_COPY,
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_PASTE,
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_RESET,
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_SAVE,
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_NAME
        ));

        this.debug = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_DEBUG, (b) -> BBSSettings.ikDebug.enabled.set(b.getValue()));
        this.debug.setValue(BBSSettings.ikDebug.enabled.get());
        this.debug.context(() -> new UIDebugOverlayContextMenu(BBSSettings.ikDebug));

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
        this.enabled.h(UIConstants.CONTROL_HEIGHT);

        this.target = new UIBonePicker((bone) ->
        {
            if (this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);

            /* The eyedropper bypasses the popup's graying, so the cycle gate sits
             * on the shared callback — a cyclic pick is refused outright. */
            if (this.isCyclic(data, bone))
            {
                return;
            }

            data.target = bone;
            this.updateLabels();
            this.commitChanges();
        });

        /* A target the chain itself drives never compiles — gray it out in the
         * picker instead of letting the pick happen and flagging it after. */
        this.target.menu((picker) ->
        {
            if (this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);

            this.fillBoneMenu(picker, data.target, (bone) -> this.isCyclic(data, bone));
        });
        this.target.viewport(this.viewportBonePicking());
        this.target.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_TARGET);

        this.chainLength = new UITrackpad((v) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.chainLength = Math.max(0, (int) v.floatValue());
            this.updateLabels();
            this.commitChanges();
        });
        this.chainLength.limit(0).integer();
        this.chainLength.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_CHAIN_LENGTH);

        /* The live meaning of the chain length number: the bones the chain
         * actually spans, root to tip — so "0 = up to the root" stops being
         * folklore and the animator sees exactly what the solve will move. */
        this.chainPreview = UI.label(IKey.EMPTY, UIConstants.LIST_ITEM_HEIGHT, Colors.LIGHTER_GRAY);
        this.chainPreview.labelAnchor(0F, 0.5F);

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
        this.pole.h(UIConstants.CONTROL_HEIGHT);

        this.poleTarget = new UIBonePicker((bone) ->
        {
            if (this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);

            data.poleTarget = bone;
            this.updateLabels();
            this.commitChanges();
        });

        /* A pole on a chain bone is not fatal (the compiler falls back to the
         * auto pole), so nothing is grayed out here. */
        this.poleTarget.menu((picker) ->
        {
            if (this.selectedBone.isEmpty())
            {
                return;
            }

            this.fillBoneMenu(picker, this.getOrCreateData(this.selectedBone).poleTarget, null);
        });
        this.poleTarget.viewport(this.viewportBonePicking());
        this.poleTarget.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_POLE_TARGET);

        this.poleAngle = new UISliderTrackpad((v) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.poleAngle = v.floatValue();
            this.commitChanges();
        });
        this.poleAngle.angle180();
        this.poleAngle.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_POLE_ANGLE);

        this.softness = new UISliderTrackpad((v) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.softness = v.floatValue();
            this.commitChanges();
        });
        this.softness.normalized();
        this.softness.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_SOFTNESS);

        this.weight = new UISliderTrackpad((v) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.weight = v.floatValue();
            this.commitChanges();
        });
        this.weight.normalized();
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

        this.stretch = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_STRETCH, (b) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.stretch = b.getValue();
            this.commitChanges();
        });

        this.classic = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_CLASSIC, (b) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.classic = b.getValue();
            this.updateLabels();
            this.commitChanges();
        });
        this.classic.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_CLASSIC_TOOLTIP);

        UISection settings = this.section(UIKeys.FORMS_EDITORS_MODEL_IK_SETTINGS, "ik.chain", true);

        /* The base covers 90% of chain authoring: target, pole, chain span.
         * enabled+target and pole+poleTarget each pair into one labelRow — the
         * toggle names itself in the label slot, the bone picker pins to the
         * shared value column (same grid as the pose editor's lighting+colour
         * row). Everything the animator touches rarely lives in the collapsed
         * "Advanced" section below, so the panel reads in one glance. */
        settings.fields.add(
            UI.labelRow(this.enabled, this.target),
            UI.labelRow(this.pole, this.poleTarget),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_CHAIN_LENGTH, this.chainLength),
            this.chainPreview
        );

        UISection advanced = this.section(UIKeys.FORMS_EDITORS_MODEL_IK_ADVANCED, "ik.advanced", false);

        advanced.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_POLE_ANGLE, this.poleAngle),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_SOFTNESS, this.softness),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_WEIGHT, this.weight),
            this.tipRotation,
            this.stretch,
            this.classic
        );

        /* The selected bone's JOINT freedom — per axis: lock, limit (degrees), stiffness.
         * Per BONE, not per chain: a bone shared by several chains has one set of joints. */
        this.lockX = this.jointLock(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_LOCK.format("X"), (d) -> d.lockX, (d, v) -> d.lockX = v);
        this.lockY = this.jointLock(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_LOCK.format("Y"), (d) -> d.lockY, (d, v) -> d.lockY = v);
        this.lockZ = this.jointLock(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_LOCK.format("Z"), (d) -> d.lockZ, (d, v) -> d.lockZ = v);

        this.limitX = this.jointToggle(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_LIMIT.format("X"), (d, v) -> d.limitX = v);
        this.limitY = this.jointToggle(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_LIMIT.format("Y"), (d, v) -> d.limitY = v);
        this.limitZ = this.jointToggle(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_LIMIT.format("Z"), (d, v) -> d.limitZ = v);

        this.limitMinX = this.jointDegrees(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_MIN.format("X"), Colors.RED, (d, v) -> d.minX = v);
        this.limitMaxX = this.jointDegrees(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_MAX.format("X"), Colors.RED, (d, v) -> d.maxX = v);
        this.limitMinY = this.jointDegrees(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_MIN.format("Y"), Colors.GREEN, (d, v) -> d.minY = v);
        this.limitMaxY = this.jointDegrees(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_MAX.format("Y"), Colors.GREEN, (d, v) -> d.maxY = v);
        this.limitMinZ = this.jointDegrees(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_MIN.format("Z"), Colors.BLUE, (d, v) -> d.minZ = v);
        this.limitMaxZ = this.jointDegrees(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_MAX.format("Z"), Colors.BLUE, (d, v) -> d.maxZ = v);

        this.stiffnessX = this.jointStiffness(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_STIFFNESS.format("X"), Colors.RED, (d, v) -> d.stiffnessX = v);
        this.stiffnessY = this.jointStiffness(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_STIFFNESS.format("Y"), Colors.GREEN, (d, v) -> d.stiffnessY = v);
        this.stiffnessZ = this.jointStiffness(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_STIFFNESS.format("Z"), Colors.BLUE, (d, v) -> d.stiffnessZ = v);

        UISection joint = this.section(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT, "ik.joint", false);

        /* One row per axis: lock switch, limit switch, min, max, stiffness —
         * same freedom as the old 15-widget stack at a third of the height. The
         * switches carry their names as tooltips; the limit's min/max sit right
         * next to their switch and light up when it flips, so the columns teach
         * themselves in one click. */
        joint.fields.add(
            this.jointAxisRow(this.lockX, this.limitX, this.limitMinX, this.limitMaxX, this.stiffnessX),
            this.jointAxisRow(this.lockY, this.limitY, this.limitMinY, this.limitMaxY, this.stiffnessY),
            this.jointAxisRow(this.lockZ, this.limitZ, this.limitMinZ, this.limitMaxZ, this.stiffnessZ)
        );

        UIIcon debugSettings = new UIIcon(Icons.GEAR, (b) -> this.getContext().replaceContextMenu(new UIDebugOverlayContextMenu(BBSSettings.ikDebug)));

        debugSettings.tooltip(UIKeys.MODEL_DEBUG_CONFIGURE);
        debugSettings.wh(20, 14);

        UIElement debugRow = new UIElement();

        debugRow.row(0).preferred(0).height(14);
        debugRow.add(this.debug, debugSettings);

        this.options.add(
            debugRow,
            this.bonesSearch,
            settings,
            advanced,
            joint
        );
    }

    /**
     * One joint axis as a single row: the lock icon, the limit switch, then
     * min/max/stiffness sharing the remaining width. No axis letter — the axis
     * lives in the value colors (X red, Y green, Z blue, like the transform
     * trackpads) and in every control's tooltip.
     */
    private UIElement jointAxisRow(UIIcon lock, UIToggle limit, UISliderTrackpad min, UISliderTrackpad max, UISliderTrackpad stiffness)
    {
        UIElement row = new UIElement();

        row.row(UIConstants.MARGIN).height(UIConstants.CONTROL_HEIGHT);
        row.add(lock, limit.w(26), min, max, stiffness);

        return row;
    }

    /**
     * The per-axis lock as a padlock icon: open when the axis solves freely,
     * closed when it is frozen at its FK value. The glyph IS the state, read
     * live from the selected bone's joint data — no value syncing; a locked
     * axis additionally gets the standard selection highlight behind the icon.
     * A plain square icon button, rendered the way every other icon button is —
     * glyph centered in its cell, highlight over the whole cell.
     */
    private UIIcon jointLock(IKey tooltip, Predicate<JointData> getter, BiConsumer<JointData, Boolean> setter)
    {
        UIIcon icon = new UIIcon(() ->
        {
            JointData data = this.jointData.get(this.selectedBone);

            return data != null && getter.test(data) ? Icons.LOCKED : Icons.UNLOCKED;
        }, (b) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            JointData data = this.getOrCreateJoint(this.selectedBone);

            setter.accept(data, !getter.test(data));
            this.updateLabels();
            this.commitChanges();
        })
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                JointData data = UIModelIKFormPanel.this.jointData.get(UIModelIKFormPanel.this.selectedBone);

                if (data != null && getter.test(data))
                {
                    UIDashboardPanels.renderHighlight(context.batcher, this.area, Direction.BOTTOM);
                }

                super.renderSkin(context);
            }
        };

        icon.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT);
        icon.tooltip(tooltip);

        return icon;
    }

    private UIToggle jointToggle(IKey label, BiConsumer<JointData, Boolean> setter)
    {
        UIToggle toggle = new UIToggle(IKey.EMPTY, (b) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            setter.accept(this.getOrCreateJoint(this.selectedBone), b.getValue());
            this.updateLabels();
            this.commitChanges();
        });

        toggle.tooltip(label);

        return toggle;
    }

    private UISliderTrackpad jointDegrees(IKey tooltip, int color, BiConsumer<JointData, Float> setter)
    {
        UISliderTrackpad pad = new UISliderTrackpad(this.jointCallback(setter));

        pad.angle180();
        pad.tooltip(tooltip);
        pad.textbox.setColor(color);

        return pad;
    }

    private UISliderTrackpad jointStiffness(IKey tooltip, int color, BiConsumer<JointData, Float> setter)
    {
        UISliderTrackpad pad = new UISliderTrackpad(this.jointCallback(setter));

        pad.normalized();
        pad.tooltip(tooltip);
        pad.textbox.setColor(color);

        return pad;
    }

    private Consumer<Double> jointCallback(BiConsumer<JointData, Float> setter)
    {
        return (v) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            setter.accept(this.getOrCreateJoint(this.selectedBone), v.floatValue());
            this.commitChanges();
        };
    }

    private JointData getOrCreateJoint(String bone)
    {
        return this.jointData.computeIfAbsent(bone, k -> new JointData());
    }

    @Override
    protected float getDefaultOptionsWidth()
    {
        /* The per-axis joint rows and the chain preview want more air than the
         * generic 20% column; the divider drag still overrides per session. */
        return 0.3F;
    }

    @Override
    public void startEdit(ModelForm form)
    {
        super.startEdit(form);

        this.debug.setValue(BBSSettings.ikDebug.enabled.get());

        ModelInstance model = ModelFormRenderer.getModel(form);
        this.model = model;
        this.presetGroup = this.resolvePresetGroup(form, model);

        if (model == null || model.model == null)
        {
            this.bones.clear();
            this.selectedBone = "";
            this.ikData.clear();
            this.jointData.clear();

            this.setElementsEnabled(false);
        }
        else
        {
            this.bones.fillBones(model.model, model.getDisabledBones());

            /* The fill resets the list's filter state, but the search box keeps its
             * text across startEdit — reapply so what you see matches the query. */
            this.bones.filter(this.bonesSearch.search.getText());
            this.setElementsEnabled(true);

            this.load();

            /* Land on the bone the animator is working on — the panel is rebuilt
             * on many editor actions, and the bone they came from another tab
             * with is the one they mean here too. */
            this.pickBoneInList(PickedBone.get());
        }

        this.updateLabels();
        this.options.resize();
    }

    private void setElementsEnabled(boolean enabled)
    {
        this.bonesSearch.setEnabled(enabled);
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
        this.stretch.setEnabled(enabled);
        this.classic.setEnabled(enabled);
        this.setJointEnabled(enabled);
    }

    private void setJointEnabled(boolean enabled)
    {
        this.lockX.setEnabled(enabled);
        this.lockY.setEnabled(enabled);
        this.lockZ.setEnabled(enabled);
        this.limitX.setEnabled(enabled);
        this.limitY.setEnabled(enabled);
        this.limitZ.setEnabled(enabled);
        this.limitMinX.setEnabled(enabled);
        this.limitMaxX.setEnabled(enabled);
        this.limitMinY.setEnabled(enabled);
        this.limitMaxY.setEnabled(enabled);
        this.limitMinZ.setEnabled(enabled);
        this.limitMaxZ.setEnabled(enabled);
        this.stiffnessX.setEnabled(enabled);
        this.stiffnessY.setEnabled(enabled);
        this.stiffnessZ.setEnabled(enabled);
    }

    @Override
    public boolean pickBoneInList(String bone)
    {
        if (bone == null || bone.isEmpty() || !this.bones.getList().contains(bone))
        {
            return false;
        }

        this.selectedBone = bone;

        PickedBone.set(bone);
        this.bones.setCurrentScroll(bone);
        this.updateLabels();

        return true;
    }

    private void fillBoneMenu(UIBonePickerContextMenu picker, String current, Predicate<String> disabled)
    {
        if (this.model == null || this.model.model == null)
        {
            return;
        }

        picker.bones(this.model.model, this.model.getDisabledBones()).none().disabled(disabled).set(current);
    }

    /**
     * Rebuilds the bone list's role dots, so the rig's IK reads off the list
     * itself instead of one click per bone. Three fixed slots, right to left:
     * chain (big = the chain lives on this bone, small = the chain drives it),
     * controller (its target, or the pole the bend aims at), joint freedom.
     * A disabled chain fades to gray everywhere — it drives nothing this tick.
     */
    private void updateMarkers()
    {
        this.boneMarkers.clear();

        IModel model = this.model == null ? null : this.model.model;

        if (model == null)
        {
            return;
        }

        Set<String> touched = new HashSet<>();
        Set<String> driven = new HashSet<>();
        Set<String> targets = new HashSet<>();
        Set<String> poles = new HashSet<>();
        Set<String> offControllers = new HashSet<>();

        for (Map.Entry<String, IKData> entry : this.ikData.entrySet())
        {
            String tip = entry.getKey();
            IKData data = entry.getValue();

            /* A bone with no target carries no chain at all — the same rule the
             * config's serialization filter uses. */
            if (tip == null || tip.isEmpty() || data == null || data.target == null || data.target.isEmpty())
            {
                continue;
            }

            touched.add(tip);

            if (data.enabled)
            {
                driven.addAll(ModelIKRuntime.chainBones(model, tip, data.chainLength));
                targets.add(data.target);

                if (data.pole && data.poleTarget != null && !data.poleTarget.isEmpty())
                {
                    poles.add(data.poleTarget);
                }
            }
            else
            {
                offControllers.add(data.target);
            }
        }

        touched.addAll(driven);
        touched.addAll(targets);
        touched.addAll(poles);
        touched.addAll(offControllers);

        for (Map.Entry<String, JointData> entry : this.jointData.entrySet())
        {
            if (entry.getValue() != null && !entry.getValue().toDoF().isFree())
            {
                touched.add(entry.getKey());
            }
        }

        for (String bone : touched)
        {
            IKData chain = this.ikData.get(bone);
            boolean hasChain = chain != null && chain.target != null && !chain.target.isEmpty();
            UIBoneTreeList.Marker slotChain = null;
            UIBoneTreeList.Marker slotController = null;
            UIBoneTreeList.Marker slotJoint = null;

            if (hasChain)
            {
                slotChain = new UIBoneTreeList.Marker(chain.enabled ? MARKER_CHAIN : MARKER_OFF, false);
            }
            else if (driven.contains(bone))
            {
                slotChain = new UIBoneTreeList.Marker(MARKER_CHAIN, true);
            }

            if (targets.contains(bone))
            {
                slotController = new UIBoneTreeList.Marker(MARKER_TARGET, false);
            }
            else if (poles.contains(bone))
            {
                slotController = new UIBoneTreeList.Marker(MARKER_POLE, false);
            }
            else if (offControllers.contains(bone))
            {
                slotController = new UIBoneTreeList.Marker(MARKER_OFF, true);
            }

            JointData joint = this.jointData.get(bone);

            if (joint != null && !joint.toDoF().isFree())
            {
                slotJoint = new UIBoneTreeList.Marker(MARKER_JOINT, false);
            }

            this.boneMarkers.put(bone, new UIBoneTreeList.Marker[] {slotChain, slotController, slotJoint});
        }
    }

    private void updateLabels()
    {
        if (this.target == null || this.enabled == null)
        {
            return;
        }

        this.updateMarkers();

        IKData data = this.ikData.get(this.selectedBone);
        JointData joint = this.jointData.get(this.selectedBone);

        String targetLabel = data == null ? "" : data.target;
        boolean active = data != null && data.enabled;
        boolean poleOn = data != null && data.pole;
        boolean canEdit = !this.selectedBone.isEmpty() && this.bones.isEnabled() && active;

        /* Cycle validation, but the two cases differ. A TARGET the chain itself drives
         * closes a feedback loop and the chain does NOT compile — loud "(CYCLE!)".
         * A POLE on a chain bone is not fatal: the compiler quietly drops it and the
         * chain solves with the rest-side auto pole instead, so it gets a softer
         * "on chain → auto pole" hint, not the does-not-compile marker. */
        boolean cyclicTarget = data != null && this.isCyclic(data, targetLabel);
        boolean cyclicPole = data != null && this.isCyclic(data, data.poleTarget);

        this.syncingUI = true;

        try
        {
            String chain = this.chainPreviewText(data, targetLabel);

            /* The pickers show the bare bone name (what they hold), not a
             * prefixed sentence — the row label and tooltip already say what
             * the picker means. */
            this.target.setLabel(IKey.constant(this.formatBone(targetLabel) + (cyclicTarget ? UIKeys.FORMS_EDITORS_MODEL_IK_CYCLE.get() : "")));
            this.chainLength.setValue(data == null ? ModelIKConfig.DEFAULT_CHAIN_LENGTH : data.chainLength);
            this.chainPreview.label = chain.isEmpty() ? UIKeys.FORMS_EDITORS_MODEL_IK_CHAIN_EMPTY : IKey.constant(chain);
            this.pole.setValue(poleOn);
            this.poleTarget.setLabel(IKey.constant(this.formatBone(data == null ? "" : data.poleTarget) + (cyclicPole ? UIKeys.FORMS_EDITORS_MODEL_IK_POLE_CYCLE.get() : "")));
            this.poleAngle.setValue(data == null ? ModelIKConfig.DEFAULT_POLE_ANGLE : data.poleAngle);
            this.softness.setValue(data == null ? ModelIKConfig.DEFAULT_SOFTNESS : data.softness);
            this.weight.setValue(data == null ? ModelIKConfig.DEFAULT_WEIGHT : data.weight);
            this.tipRotation.setValue(data != null && data.tipRotation);
            this.stretch.setValue(data != null && data.stretch);
            this.classic.setValue(data != null && data.classic);

            /* The classic toggle is loud about its fallback: a classic chain that
             * is not exactly two bones, or shares a bone with another enabled
             * chain, solves on the core instead — the label says so right where
             * the box was ticked, no runtime surprise. */
            boolean classicFallsBack = data != null && data.classic && this.classicFallsBack(data);

            this.classic.label = classicFallsBack ? UIKeys.FORMS_EDITORS_MODEL_IK_CLASSIC_FALLBACK : UIKeys.FORMS_EDITORS_MODEL_IK_CLASSIC;
            this.enabled.setEnabled(this.bones.isEnabled() && !this.selectedBone.isEmpty());
            this.enabled.setValue(active);

            this.limitX.setValue(joint != null && joint.limitX);
            this.limitY.setValue(joint != null && joint.limitY);
            this.limitZ.setValue(joint != null && joint.limitZ);
            this.limitMinX.setValue(joint == null ? ModelIKConfig.JointDoF.DEFAULT_MIN : joint.minX);
            this.limitMaxX.setValue(joint == null ? ModelIKConfig.JointDoF.DEFAULT_MAX : joint.maxX);
            this.limitMinY.setValue(joint == null ? ModelIKConfig.JointDoF.DEFAULT_MIN : joint.minY);
            this.limitMaxY.setValue(joint == null ? ModelIKConfig.JointDoF.DEFAULT_MAX : joint.maxY);
            this.limitMinZ.setValue(joint == null ? ModelIKConfig.JointDoF.DEFAULT_MIN : joint.minZ);
            this.limitMaxZ.setValue(joint == null ? ModelIKConfig.JointDoF.DEFAULT_MAX : joint.maxZ);
            this.stiffnessX.setValue(joint == null ? 0D : joint.stiffnessX);
            this.stiffnessY.setValue(joint == null ? 0D : joint.stiffnessY);
            this.stiffnessZ.setValue(joint == null ? 0D : joint.stiffnessZ);
        }
        finally
        {
            this.syncingUI = false;
        }

        /* The joint is a property of the BONE, editable regardless of whether a chain
         * ends here — it affects every chain running through this bone. */
        boolean canEditJoint = !this.selectedBone.isEmpty() && this.bones.isEnabled();

        this.setJointEnabled(canEditJoint);
        this.limitMinX.setEnabled(canEditJoint && joint != null && joint.limitX);
        this.limitMaxX.setEnabled(canEditJoint && joint != null && joint.limitX);
        this.limitMinY.setEnabled(canEditJoint && joint != null && joint.limitY);
        this.limitMaxY.setEnabled(canEditJoint && joint != null && joint.limitY);
        this.limitMinZ.setEnabled(canEditJoint && joint != null && joint.limitZ);
        this.limitMaxZ.setEnabled(canEditJoint && joint != null && joint.limitZ);

        this.target.setEnabled(canEdit);
        this.chainLength.setEnabled(canEdit);
        this.pole.setEnabled(canEdit);
        this.poleTarget.setEnabled(canEdit && poleOn);
        this.poleAngle.setEnabled(canEdit && poleOn);
        this.softness.setEnabled(canEdit);
        this.weight.setEnabled(canEdit);
        this.tipRotation.setEnabled(canEdit);
        this.stretch.setEnabled(canEdit);
        this.classic.setEnabled(canEdit);
    }

    /**
     * The bones the selected bone's chain spans, root to tip, as a readable
     * arrow path — the live meaning of the chain length number. Empty when the
     * bone has no chain (no target) or the model is missing.
     */
    private String chainPreviewText(IKData data, String target)
    {
        IModel model = this.model == null ? null : this.model.model;

        if (data == null || target == null || target.isEmpty() || model == null || this.selectedBone.isEmpty())
        {
            return "";
        }

        return String.join(" → ", ModelIKRuntime.chainBones(model, this.selectedBone, data.chainLength));
    }

    /**
     * Whether the selected bone's classic-marked chain would actually solve on
     * the core: wrong shape (not exactly two directed bones) or a bone shared
     * with another enabled chain (overlapping chains merge into one core tree).
     * Mirrors the applier's routing, computed statically from the config.
     */
    private boolean classicFallsBack(IKData data)
    {
        IModel model = this.model == null ? null : this.model.model;

        if (model == null)
        {
            return false;
        }

        if (!ModelIKRuntime.isClassicShape(model, this.selectedBone, data.chainLength, data.tipRotation))
        {
            return true;
        }

        List<String> mine = ModelIKRuntime.chainBones(model, this.selectedBone, data.chainLength);

        for (Map.Entry<String, IKData> entry : this.ikData.entrySet())
        {
            String tip = entry.getKey();
            IKData other = entry.getValue();

            if (tip.equals(this.selectedBone) || other == null || !other.enabled || other.target == null || other.target.isEmpty())
            {
                continue;
            }

            for (String bone : ModelIKRuntime.chainBones(model, tip, other.chainLength))
            {
                if (mine.contains(bone))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private IKData getOrCreateData(String bone)
    {
        return this.ikData.computeIfAbsent(bone, k -> new IKData());
    }

    private String formatBone(String bone)
    {
        return bone == null || bone.isEmpty() ? "-" : bone;
    }

    /** Whether pointing the selected bone's chain at {@code bone} would close a feedback loop. */
    private boolean isCyclic(IKData data, String bone)
    {
        if (bone == null || bone.isEmpty() || this.model == null || this.model.model == null)
        {
            return false;
        }

        return ModelIKRuntime.isCyclicTarget(this.model.model, this.selectedBone, data.chainLength, bone);
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
        this.jointData.clear();

        if (config == null)
        {
            return;
        }

        List<String> bones = this.bones.getList();
        boolean filterByBones = bones != null && !bones.isEmpty();

        if (config.chains() != null)
        {
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
                data.stretch = chain.stretch();
                data.classic = chain.classic();
                this.ikData.put(chain.tip(), data);
            }
        }

        for (Map.Entry<String, ModelIKConfig.JointDoF> entry : config.bones().entrySet())
        {
            String bone = entry.getKey();

            if (bone == null || bone.isEmpty() || entry.getValue() == null)
            {
                continue;
            }

            if (filterByBones && !bones.contains(bone))
            {
                continue;
            }

            this.jointData.put(bone, JointData.from(entry.getValue()));
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

            out.add(new ModelIKConfig.Chain(tip, data.target, data.chainLength, data.pole, data.poleTarget, data.poleAngle, data.softness, data.weight, data.enabled, data.tipRotation, data.stretch, data.classic));
        }

        Map<String, ModelIKConfig.JointDoF> joints = new HashMap<>();

        for (Map.Entry<String, JointData> entry : this.jointData.entrySet())
        {
            String bone = entry.getKey();
            JointData data = entry.getValue();

            if (bone == null || bone.isEmpty() || data == null)
            {
                continue;
            }

            if (filterByBones && !bones.contains(bone))
            {
                continue;
            }

            ModelIKConfig.JointDoF dof = data.toDoF();

            if (!dof.isFree())
            {
                joints.put(bone, dof);
            }
        }

        if (out.isEmpty() && joints.isEmpty())
        {
            return new MapType();
        }

        return ModelIKIO.toData(new ModelIKConfig(out, joints));
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

        /* Not every edit runs through updateLabels (a lone toggle just commits),
         * and the dots must follow what the list now describes. */
        this.updateMarkers();
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
