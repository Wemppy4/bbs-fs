package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ik.BoneIKIO;
import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.cubic.ik.JointDoF;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.utils.UIDebugOverlayContextMenu;
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
import mchorse.bbs_mod.ui.utils.bones.UIBonePicker;
import mchorse.bbs_mod.ui.utils.bones.UIBonePickerContextMenu;
import mchorse.bbs_mod.ui.utils.bones.UIBoneTreeList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.ModelIKManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class UIModelIKFormPanel extends UIBoneListFormPanel
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

    private final Map<String, UIBoneTreeList.Marker[]> boneMarkers = new HashMap<>();

    public UIModelIKFormPanel(UIForm editor)
    {
        super(editor);

        this.bones.markers(this.boneMarkers::get, UIKeys.FORMS_EDITORS_MODEL_IK_BONES_TOOLTIP);
        this.bonePresets(ModelIKManager.INSTANCE, "_CopyModelIK",
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_COPY,
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_PASTE,
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_RESET,
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_SAVE,
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_NAME
        );

        this.debug = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_DEBUG, (b) -> BBSSettings.ikDebug.enabled.set(b.getValue()));
        this.debug.setValue(BBSSettings.ikDebug.enabled.get());
        this.debug.context(() -> new UIDebugOverlayContextMenu(BBSSettings.ikDebug));

        this.enabled = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_ENABLED, (b) ->
        {
            this.editControl((c) -> c.enabled = b.getValue());
            this.updateFields();
        });
        this.enabled.h(UIConstants.CONTROL_HEIGHT);

        this.target = new UIBonePicker((bone) ->
        {
            if (this.form == null || this.selectedBone.isEmpty())
            {
                return;
            }

            /* The eyedropper bypasses the popup's graying, so the cycle gate sits
             * on the shared callback — a cyclic pick is refused outright. */
            if (this.isCyclic(bone))
            {
                return;
            }

            this.form.bones.getOrCreate(this.selectedBone).ikTarget.set(bone);
            this.updateFields();
        });

        /* A target the chain itself drives never compiles — gray it out in the
         * picker instead of letting the pick happen and flagging it after. */
        this.target.menu((picker) ->
        {
            if (this.selectedBone.isEmpty())
            {
                return;
            }

            this.fillBoneMenu(picker, this.readBone((b) -> b.ikTarget.get(), ""), this::isCyclic);
        });
        this.target.viewport(this.viewportBonePicking());
        this.target.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_TARGET);
        this.resetBone(this.target, (bone) -> bone.ikTarget);

        this.chainLength = new UITrackpad((v) ->
        {
            this.editBone((bone) -> bone.ikChainLength.set(Math.max(0, (int) v.floatValue())));
            this.updateFields();
        });
        this.chainLength.limit(0).integer();
        this.chainLength.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_CHAIN_LENGTH);
        this.resetBone(this.chainLength, (bone) -> bone.ikChainLength);

        /* The live meaning of the chain length number: the bones the chain
         * actually spans, root to tip — so "0 = up to the root" stops being
         * folklore and the animator sees exactly what the solve will move. */
        this.chainPreview = UI.label(IKey.EMPTY, UIConstants.LIST_ITEM_HEIGHT, Colors.LIGHTER_GRAY);
        this.chainPreview.labelAnchor(0F, 0.5F);

        this.pole = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_POLE, (b) ->
        {
            this.editControl((c) -> c.pole = b.getValue());
            this.updateFields();
        });
        this.pole.h(UIConstants.CONTROL_HEIGHT);

        this.poleTarget = new UIBonePicker((bone) ->
        {
            if (this.form == null || this.selectedBone.isEmpty())
            {
                return;
            }

            this.form.bones.getOrCreate(this.selectedBone).ikPoleTarget.set(bone);
            this.updateFields();
        });

        /* A pole on a chain bone is not fatal (the compiler falls back to the
         * auto pole), so nothing is grayed out here. */
        this.poleTarget.menu((picker) ->
        {
            if (this.selectedBone.isEmpty())
            {
                return;
            }

            this.fillBoneMenu(picker, this.readBone((b) -> b.ikPoleTarget.get(), ""), null);
        });
        this.poleTarget.viewport(this.viewportBonePicking());
        this.poleTarget.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_POLE_TARGET);
        this.resetBone(this.poleTarget, (bone) -> bone.ikPoleTarget);

        this.poleAngle = new UISliderTrackpad((v) -> this.editControl((c) -> c.poleAngle = v.floatValue()));
        this.poleAngle.angle180();
        this.poleAngle.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_POLE_ANGLE);

        this.softness = new UISliderTrackpad((v) -> this.editControl((c) -> c.softness = v.floatValue()));
        this.softness.normalized();
        this.softness.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_SOFTNESS);

        this.weight = new UISliderTrackpad((v) -> this.editControl((c) -> c.weight = v.floatValue()));
        this.weight.normalized();
        this.weight.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_WEIGHT);

        this.tipRotation = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_TIP_ROTATION, (b) -> this.editBone((bone) -> bone.ikTipRotation.set(b.getValue())));
        this.stretch = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_STRETCH, (b) -> this.editBone((bone) -> bone.ikStretch.set(b.getValue())));

        this.classic = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_CLASSIC, (b) ->
        {
            this.editBone((bone) -> bone.ikClassic.set(b.getValue()));
            this.updateFields();
        });
        this.classic.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_CLASSIC_TOOLTIP);

        this.resetBone(this.tipRotation, (bone) -> bone.ikTipRotation);
        this.resetBone(this.stretch, (bone) -> bone.ikStretch);
        this.resetBone(this.classic, (bone) -> bone.ikClassic);

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
        this.lockX = this.jointLock(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_LOCK.format("X"), (j) -> j.lockX, (j, v) -> j.lockX = v);
        this.lockY = this.jointLock(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_LOCK.format("Y"), (j) -> j.lockY, (j, v) -> j.lockY = v);
        this.lockZ = this.jointLock(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_LOCK.format("Z"), (j) -> j.lockZ, (j, v) -> j.lockZ = v);

        this.limitX = this.jointToggle(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_LIMIT.format("X"), (j, v) -> j.limitX = v);
        this.limitY = this.jointToggle(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_LIMIT.format("Y"), (j, v) -> j.limitY = v);
        this.limitZ = this.jointToggle(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_LIMIT.format("Z"), (j, v) -> j.limitZ = v);

        this.limitMinX = this.jointDegrees(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_MIN.format("X"), Colors.RED, (j, v) -> j.minX = v);
        this.limitMaxX = this.jointDegrees(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_MAX.format("X"), Colors.RED, (j, v) -> j.maxX = v);
        this.limitMinY = this.jointDegrees(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_MIN.format("Y"), Colors.GREEN, (j, v) -> j.minY = v);
        this.limitMaxY = this.jointDegrees(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_MAX.format("Y"), Colors.GREEN, (j, v) -> j.maxY = v);
        this.limitMinZ = this.jointDegrees(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_MIN.format("Z"), Colors.BLUE, (j, v) -> j.minZ = v);
        this.limitMaxZ = this.jointDegrees(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_MAX.format("Z"), Colors.BLUE, (j, v) -> j.maxZ = v);

        this.stiffnessX = this.jointStiffness(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_STIFFNESS.format("X"), Colors.RED, (j, v) -> j.stiffnessX = v);
        this.stiffnessY = this.jointStiffness(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_STIFFNESS.format("Y"), Colors.GREEN, (j, v) -> j.stiffnessY = v);
        this.stiffnessZ = this.jointStiffness(UIKeys.FORMS_EDITORS_MODEL_IK_JOINT_STIFFNESS.format("Z"), Colors.BLUE, (j, v) -> j.stiffnessZ = v);

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

        this.options.add(
            this.debugRow(this.debug, BBSSettings.ikDebug),
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
     * live from the selected bone's joint property — no value syncing; a locked
     * axis additionally gets the standard selection highlight behind the icon.
     */
    private UIIcon jointLock(IKey tooltip, Predicate<JointDoF> getter, BiConsumer<JointDoF, Boolean> setter)
    {
        UIIcon icon = new UIIcon(() -> getter.test(this.currentJoint()) ? Icons.LOCKED : Icons.UNLOCKED, (b) ->
        {
            this.editJoint((j) -> setter.accept(j, !getter.test(j)));
            this.updateFields();
        })
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                if (getter.test(UIModelIKFormPanel.this.currentJoint()))
                {
                    context.batcher.highlight(this.area, Direction.BOTTOM);
                }

                super.renderSkin(context);
            }
        };

        icon.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT);
        icon.tooltip(tooltip);

        return icon;
    }

    private UIToggle jointToggle(IKey label, BiConsumer<JointDoF, Boolean> setter)
    {
        UIToggle toggle = new UIToggle(IKey.EMPTY, (b) ->
        {
            this.editJoint((j) -> setter.accept(j, b.getValue()));
            this.updateFields();
        });

        toggle.tooltip(label);

        return toggle;
    }

    private UISliderTrackpad jointDegrees(IKey tooltip, int color, BiConsumer<JointDoF, Float> setter)
    {
        UISliderTrackpad pad = new UISliderTrackpad((v) -> this.editJoint((j) -> setter.accept(j, v.floatValue())));

        pad.angle180();
        pad.tooltip(tooltip);
        pad.textbox.setColor(color);

        return pad;
    }

    private UISliderTrackpad jointStiffness(IKey tooltip, int color, BiConsumer<JointDoF, Float> setter)
    {
        UISliderTrackpad pad = new UISliderTrackpad((v) -> this.editJoint((j) -> setter.accept(j, v.floatValue())));

        pad.normalized();
        pad.tooltip(tooltip);
        pad.textbox.setColor(color);

        return pad;
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
        this.debug.setValue(BBSSettings.ikDebug.enabled.get());

        super.startEdit(form);
    }

    @Override
    protected void setElementsEnabled(boolean enabled)
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

    private void fillBoneMenu(UIBonePickerContextMenu picker, String current, Predicate<String> disabled)
    {
        if (this.modelInstance == null || this.modelInstance.model == null)
        {
            return;
        }

        picker.bones(this.modelInstance.model, this.modelInstance.getDisabledBones()).none().disabled(disabled).set(current);
    }

    /* Value access: the panel holds no data of its own — every read and write
     * goes to the form's bone properties, and undo picks the writes up itself. */

    /** The selected bone's properties, or null when it was never touched. */
    private IKControl currentControl()
    {
        FormBone bone = this.selectedFormBone();

        return bone == null ? IKControl.DEFAULT : bone.ik.get();
    }

    private JointDoF currentJoint()
    {
        FormBone bone = this.selectedFormBone();

        return bone == null ? JointDoF.FREE : bone.joint.get();
    }

    @Override
    protected void editBone(Consumer<FormBone> edit)
    {
        super.editBone(edit);
        this.updateMarkers();
    }

    /** Edits the selected bone's IK scalars as one value change (one undo entry). */
    private void editControl(Consumer<IKControl> edit)
    {
        this.editBone((bone) ->
        {
            IKControl control = bone.ik.get().copy();

            edit.accept(control);
            bone.ik.set(control);
        });
    }

    /** Edits the selected bone's joint freedom as one value change (one undo entry). */
    private void editJoint(Consumer<JointDoF> edit)
    {
        this.editBone((bone) ->
        {
            JointDoF joint = bone.joint.get().copy();

            edit.accept(joint);
            bone.joint.set(joint);
        });
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

        IModel model = this.modelInstance == null ? null : this.modelInstance.model;

        if (model == null || this.form == null)
        {
            return;
        }

        Set<String> touched = new HashSet<>();
        Set<String> driven = new HashSet<>();
        Set<String> targets = new HashSet<>();
        Set<String> poles = new HashSet<>();
        Set<String> offControllers = new HashSet<>();

        for (BaseValue value : this.form.bones.getAll())
        {
            if (!(value instanceof FormBone bone))
            {
                continue;
            }

            String tip = bone.getId();

            if (bone.hasChain())
            {
                IKControl control = bone.ik.get();

                touched.add(tip);

                if (control.enabled)
                {
                    driven.addAll(ModelIKRuntime.chainBones(model, tip, bone.ikChainLength.get()));
                    targets.add(bone.ikTarget.get());

                    if (control.pole && !bone.ikPoleTarget.get().isEmpty())
                    {
                        poles.add(bone.ikPoleTarget.get());
                    }
                }
                else
                {
                    offControllers.add(bone.ikTarget.get());
                }
            }

            if (!bone.joint.get().isFree())
            {
                touched.add(tip);
            }
        }

        touched.addAll(driven);
        touched.addAll(targets);
        touched.addAll(poles);
        touched.addAll(offControllers);

        for (String name : touched)
        {
            FormBone bone = this.form.bones.getBone(name);
            boolean hasChain = bone != null && bone.hasChain();
            UIBoneTreeList.Marker slotChain = null;
            UIBoneTreeList.Marker slotController = null;
            UIBoneTreeList.Marker slotJoint = null;

            if (hasChain)
            {
                slotChain = new UIBoneTreeList.Marker(bone.ik.get().enabled ? MARKER_CHAIN : MARKER_OFF, false);
            }
            else if (driven.contains(name))
            {
                slotChain = new UIBoneTreeList.Marker(MARKER_CHAIN, true);
            }

            if (targets.contains(name))
            {
                slotController = new UIBoneTreeList.Marker(MARKER_TARGET, false);
            }
            else if (poles.contains(name))
            {
                slotController = new UIBoneTreeList.Marker(MARKER_POLE, false);
            }
            else if (offControllers.contains(name))
            {
                slotController = new UIBoneTreeList.Marker(MARKER_OFF, true);
            }

            if (bone != null && !bone.joint.get().isFree())
            {
                slotJoint = new UIBoneTreeList.Marker(MARKER_JOINT, false);
            }

            this.boneMarkers.put(name, new UIBoneTreeList.Marker[] {slotChain, slotController, slotJoint});
        }
    }

    @Override
    protected void updateFields()
    {
        if (this.target == null || this.enabled == null)
        {
            return;
        }

        this.updateMarkers();

        FormBone formBone = this.selectedFormBone();
        IKControl control = this.currentControl();
        JointDoF joint = this.currentJoint();

        String targetLabel = formBone == null ? "" : formBone.ikTarget.get();
        boolean hasChain = formBone != null && formBone.hasChain();
        boolean active = formBone != null && control.enabled;
        boolean poleOn = formBone != null && control.pole;
        boolean canEdit = !this.selectedBone.isEmpty() && this.bones.isEnabled() && active;
        int chainLength = formBone == null ? 0 : formBone.ikChainLength.get();

        /* Cycle validation, but the two cases differ. A TARGET the chain itself drives
         * closes a feedback loop and the chain does NOT compile — loud "(CYCLE!)".
         * A POLE on a chain bone is not fatal: the compiler quietly drops it and the
         * chain solves with the rest-side auto pole instead, so it gets a softer
         * "on chain → auto pole" hint, not the does-not-compile marker. */
        boolean cyclicTarget = this.isCyclic(targetLabel);
        boolean cyclicPole = formBone != null && this.isCyclic(formBone.ikPoleTarget.get());

        String chain = hasChain ? this.chainPreviewText(chainLength) : "";

        /* The pickers show the bare bone name (what they hold), not a
         * prefixed sentence — the row label and tooltip already say what
         * the picker means. */
        this.target.setLabel(IKey.constant(this.formatBone(targetLabel) + (cyclicTarget ? UIKeys.FORMS_EDITORS_MODEL_IK_CYCLE.get() : "")));
        this.chainLength.setValue(chainLength);
        this.chainPreview.label = chain.isEmpty() ? UIKeys.FORMS_EDITORS_MODEL_IK_CHAIN_EMPTY : IKey.constant(chain);
        this.pole.setValue(poleOn);
        this.poleTarget.setLabel(IKey.constant(this.formatBone(formBone == null ? "" : formBone.ikPoleTarget.get()) + (cyclicPole ? UIKeys.FORMS_EDITORS_MODEL_IK_POLE_CYCLE.get() : "")));
        this.poleAngle.setValue(control.poleAngle);
        this.softness.setValue(control.softness);
        this.weight.setValue(control.weight);
        this.tipRotation.setValue(formBone != null && formBone.ikTipRotation.get());
        this.stretch.setValue(formBone != null && formBone.ikStretch.get());
        this.classic.setValue(formBone != null && formBone.ikClassic.get());

        /* The classic toggle is loud about its fallback: a classic chain that
         * is not exactly two bones, or shares a bone with another enabled
         * chain, solves on the core instead — the label says so right where
         * the box was ticked, no runtime surprise. */
        boolean classicFallsBack = formBone != null && formBone.ikClassic.get() && this.classicFallsBack(formBone);

        this.classic.label = classicFallsBack ? UIKeys.FORMS_EDITORS_MODEL_IK_CLASSIC_FALLBACK : UIKeys.FORMS_EDITORS_MODEL_IK_CLASSIC;
        this.enabled.setEnabled(this.bones.isEnabled() && !this.selectedBone.isEmpty());
        this.enabled.setValue(active);

        this.limitX.setValue(joint.limitX);
        this.limitY.setValue(joint.limitY);
        this.limitZ.setValue(joint.limitZ);
        this.limitMinX.setValue(joint.minX);
        this.limitMaxX.setValue(joint.maxX);
        this.limitMinY.setValue(joint.minY);
        this.limitMaxY.setValue(joint.maxY);
        this.limitMinZ.setValue(joint.minZ);
        this.limitMaxZ.setValue(joint.maxZ);
        this.stiffnessX.setValue(joint.stiffnessX);
        this.stiffnessY.setValue(joint.stiffnessY);
        this.stiffnessZ.setValue(joint.stiffnessZ);

        /* The joint is a property of the BONE, editable regardless of whether a chain
         * ends here — it affects every chain running through this bone. */
        boolean canEditJoint = !this.selectedBone.isEmpty() && this.bones.isEnabled();

        this.setJointEnabled(canEditJoint);
        this.limitMinX.setEnabled(canEditJoint && joint.limitX);
        this.limitMaxX.setEnabled(canEditJoint && joint.limitX);
        this.limitMinY.setEnabled(canEditJoint && joint.limitY);
        this.limitMaxY.setEnabled(canEditJoint && joint.limitY);
        this.limitMinZ.setEnabled(canEditJoint && joint.limitZ);
        this.limitMaxZ.setEnabled(canEditJoint && joint.limitZ);

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
    private String chainPreviewText(int chainLength)
    {
        IModel model = this.modelInstance == null ? null : this.modelInstance.model;

        if (model == null || this.selectedBone.isEmpty())
        {
            return "";
        }

        return String.join(" → ", ModelIKRuntime.chainBones(model, this.selectedBone, chainLength));
    }

    /**
     * Whether the selected bone's classic-marked chain would actually solve on
     * the core: wrong shape (not exactly two directed bones) or a bone shared
     * with another enabled chain (overlapping chains merge into one core tree).
     * Mirrors the applier's routing, computed statically from the form.
     */
    private boolean classicFallsBack(FormBone formBone)
    {
        IModel model = this.modelInstance == null ? null : this.modelInstance.model;

        if (model == null || this.form == null)
        {
            return false;
        }

        if (!ModelIKRuntime.isClassicShape(model, this.selectedBone, formBone.ikChainLength.get(), formBone.ikTipRotation.get()))
        {
            return true;
        }

        List<String> mine = ModelIKRuntime.chainBones(model, this.selectedBone, formBone.ikChainLength.get());

        for (BaseValue value : this.form.bones.getAll())
        {
            if (!(value instanceof FormBone other) || other.getId().equals(this.selectedBone))
            {
                continue;
            }

            if (!other.hasChain() || !other.ik.get().enabled)
            {
                continue;
            }

            for (String bone : ModelIKRuntime.chainBones(model, other.getId(), other.ikChainLength.get()))
            {
                if (mine.contains(bone))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private String formatBone(String bone)
    {
        return bone == null || bone.isEmpty() ? "-" : bone;
    }

    /** Whether pointing the selected bone's chain at {@code bone} would close a feedback loop. */
    private boolean isCyclic(String bone)
    {
        if (bone == null || bone.isEmpty() || this.modelInstance == null || this.modelInstance.model == null)
        {
            return false;
        }

        int chainLength = this.readBone((b) -> b.ikChainLength.get(), 0);

        return ModelIKRuntime.isCyclicTarget(this.modelInstance.model, this.selectedBone, chainLength, bone);
    }

    @Override
    protected MapType toPresetData()
    {
        return this.form == null ? new MapType() : BoneIKIO.write(this.form.bones);
    }

    @Override
    protected void applyPresetData(MapType map)
    {
        if (this.form == null)
        {
            return;
        }

        BoneIKIO.read(map, this.form.bones, true);

        this.updateFields();
    }

}
