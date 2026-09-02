package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.physics.BonePhysicsIO;
import mchorse.bbs_mod.cubic.physics.PhysicsControl;
import mchorse.bbs_mod.cubic.physics.WindControl;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.utils.UIDebugOverlayContextMenu;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.bones.UIBonePicker;
import mchorse.bbs_mod.ui.utils.bones.UIBonePickerContextMenu;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.ModelPhysicsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class UIModelPhysicsFormPanel extends UIBoneListFormPanel
{
    public UIToggle debug;
    public UIBonePicker end;
    public UIBonePicker targetBone;
    public UIToggle enabled;
    public UISliderTrackpad gravity;
    public UIToggle relativeGravity;
    public UISliderTrackpad relativeGravityRotateX;
    public UISliderTrackpad relativeGravityRotateY;
    public UISliderTrackpad relativeGravityRotateZ;
    public UISliderTrackpad stiffness;
    public UISliderTrackpad damping;
    public UITrackpad iterations;
    public UIToggle collisions;
    public UISliderTrackpad radius;
    public UISliderTrackpad windStrength;
    public UITrackpad windX;
    public UITrackpad windY;
    public UITrackpad windZ;
    public UISliderTrackpad windTurbulence;
    public UISliderTrackpad windTurbulenceSpeed;
    public UISliderTrackpad windTurbulenceScale;
    public UIToggle windLocal;

    public UIModelPhysicsFormPanel(UIForm editor)
    {
        super(editor);

        IKey axis = IKey.constant("%s (%s)");

        this.bonePresets(ModelPhysicsManager.INSTANCE, "_CopyModelPhysics",
            UIKeys.FORMS_EDITORS_MODEL_PHYSICS_CONTEXT_COPY,
            UIKeys.FORMS_EDITORS_MODEL_PHYSICS_CONTEXT_PASTE,
            UIKeys.FORMS_EDITORS_MODEL_PHYSICS_CONTEXT_RESET,
            UIKeys.FORMS_EDITORS_MODEL_PHYSICS_CONTEXT_SAVE,
            UIKeys.FORMS_EDITORS_MODEL_PHYSICS_CONTEXT_NAME
        );

        this.debug = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_DEBUG, (b) -> BBSSettings.physicsDebug.enabled.set(b.getValue()));
        this.debug.setValue(BBSSettings.physicsDebug.enabled.get());
        this.debug.context(() -> new UIDebugOverlayContextMenu(BBSSettings.physicsDebug));

        this.enabled = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ENABLED, (b) ->
        {
            if (this.form == null || this.selectedBone.isEmpty())
            {
                return;
            }

            FormBone bone = this.form.bones.getOrCreate(this.selectedBone);

            /* Switching a chain on seeds its end with the bone itself, like it always did;
             * switching it off only flips the scalar — the chain's setup stays put, so
             * toggling no longer wipes what the animator tuned. */
            if (b.getValue() && !bone.hasPhysicsChain())
            {
                bone.physicsEnd.set(this.selectedBone);
            }

            this.editControl((c) -> c.enabled = b.getValue());
            this.updateFields();
        });

        this.gravity = new UISliderTrackpad((v) -> this.editControl((c) -> c.gravity = v.floatValue()));
        this.gravity.onlyNumbers().values(0.1D, 0.01D, 0.5D).increment(0.25D).limit(0D, 10D);
        this.gravity.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_GRAVITY);

        this.relativeGravity = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY, (b) -> this.editBone((bone) -> bone.physicsRelativeGravity.set(b.getValue())));

        this.relativeGravityRotateX = axisTrackpad((v) -> this.editBone((bone) -> bone.physicsGravityRotateX.set(v.floatValue())), Colors.RED, axis.format(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY_ROTATION, UIKeys.GENERAL_X));
        this.relativeGravityRotateY = axisTrackpad((v) -> this.editBone((bone) -> bone.physicsGravityRotateY.set(v.floatValue())), Colors.GREEN, axis.format(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY_ROTATION, UIKeys.GENERAL_Y));
        this.relativeGravityRotateZ = axisTrackpad((v) -> this.editBone((bone) -> bone.physicsGravityRotateZ.set(v.floatValue())), Colors.BLUE, axis.format(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY_ROTATION, UIKeys.GENERAL_Z));

        this.stiffness = new UISliderTrackpad((v) -> this.editControl((c) -> c.stiffness = v.floatValue()));
        this.stiffness.normalized();
        this.stiffness.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_STIFFNESS);

        this.damping = new UISliderTrackpad((v) -> this.editControl((c) -> c.damping = v.floatValue()));
        this.damping.normalized();
        this.damping.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_DAMPING);

        this.iterations = new UITrackpad((v) -> this.editBone((bone) -> bone.physicsIterations.set(v.intValue())));
        this.iterations.onlyNumbers().integer().values(1D).increment(1D).limit(1D, 20D, true);
        this.iterations.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ITERATIONS);
        this.resetBone(this.iterations, (bone) -> bone.physicsIterations);

        this.collisions = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_COLLISIONS, (b) -> this.editBone((bone) -> bone.physicsCollisions.set(b.getValue())));

        this.radius = new UISliderTrackpad((v) -> this.editBone((bone) -> bone.physicsRadius.set(v.floatValue())));
        this.radius.normalized();
        this.radius.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RADIUS);

        this.resetBone(this.relativeGravity, (bone) -> bone.physicsRelativeGravity);
        this.resetBone(this.relativeGravityRotateX, (bone) -> bone.physicsGravityRotateX);
        this.resetBone(this.relativeGravityRotateY, (bone) -> bone.physicsGravityRotateY);
        this.resetBone(this.relativeGravityRotateZ, (bone) -> bone.physicsGravityRotateZ);
        this.resetBone(this.collisions, (bone) -> bone.physicsCollisions);
        this.resetBone(this.radius, (bone) -> bone.physicsRadius);

        this.windStrength = new UISliderTrackpad((v) -> this.editWind((w) -> w.strength = v.floatValue()));
        this.windStrength.onlyNumbers().values(0.1D, 0.01D, 0.5D).increment(0.25D).limit(0D, 10D);
        this.windStrength.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_STRENGTH);

        this.windX = windAxisTrackpad((v) -> this.editWind((w) -> w.x = v.floatValue()), Colors.RED);
        this.windY = windAxisTrackpad((v) -> this.editWind((w) -> w.y = v.floatValue()), Colors.GREEN);
        this.windZ = windAxisTrackpad((v) -> this.editWind((w) -> w.z = v.floatValue()), Colors.BLUE);

        this.windTurbulence = new UISliderTrackpad((v) -> this.editWind((w) -> w.turbulence = v.floatValue()));
        this.windTurbulence.normalized();
        this.windTurbulence.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE);

        this.windTurbulenceSpeed = new UISliderTrackpad((v) -> this.editWind((w) -> w.turbulenceSpeed = v.floatValue()));
        this.windTurbulenceSpeed.onlyNumbers().values(0.1D, 0.05D, 0.5D).increment(0.1D).limit(0D, 10D);
        this.windTurbulenceSpeed.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE_SPEED);

        this.windTurbulenceScale = new UISliderTrackpad((v) -> this.editWind((w) -> w.turbulenceScale = v.floatValue()));
        this.windTurbulenceScale.onlyNumbers().values(0.1D, 0.05D, 0.5D).increment(0.1D).limit(0D, 10D);
        this.windTurbulenceScale.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE_SCALE);

        this.windLocal = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_LOCAL, (b) -> this.editWind((w) -> w.local = b.getValue()));
        this.windLocal.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_LOCAL_TOOLTIP);

        this.end = new UIBonePicker((bone) ->
        {
            /* The eyedropper bypasses the popup's candidate subtree, so the chain
             * gate sits on the shared callback — only a bone the chain can end at. */
            if (this.form == null || this.selectedBone.isEmpty() || !this.isEndCandidate(bone))
            {
                return;
            }

            this.editBone((formBone) -> formBone.physicsEnd.set(bone));
            this.updateFields();
        });
        this.end.menu(this::fillEndMenu);
        this.end.viewport(this.viewportBonePicking());

        this.targetBone = new UIBonePicker((bone) ->
        {
            if (this.form == null || this.selectedBone.isEmpty())
            {
                return;
            }

            this.editBone((formBone) -> formBone.physicsTargetBone.set(bone));
            this.updateFields();
        });
        this.targetBone.menu((picker) ->
        {
            if (this.selectedBone.isEmpty() || this.modelInstance == null || this.modelInstance.model == null)
            {
                return;
            }

            picker.bones(this.modelInstance.model, this.modelInstance.getDisabledBones()).none().set(this.readBone((b) -> b.physicsTargetBone.get(), ""));
        });
        this.targetBone.viewport(this.viewportBonePicking());

        UISection settings = this.section(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_SETTINGS, "physics.settings", true);

        settings.fields.add(
            this.enabled,
            this.end,
            this.targetBone,
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_GRAVITY, this.gravity),
            this.relativeGravity,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY_ROTATION),
            UI.row(this.relativeGravityRotateX, this.relativeGravityRotateY, this.relativeGravityRotateZ),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_STIFFNESS, this.stiffness),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_DAMPING, this.damping),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ITERATIONS, this.iterations)
        );

        UISection collisionsSection = this.section(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_COLLISIONS, "physics.collisions", false);

        collisionsSection.fields.add(
            this.collisions,
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RADIUS, this.radius)
        );

        /* Wind is the form's own `wind` property, not bound to any bone, so the section is always
         * editable and does not depend on which bone is selected in the list. */
        UISection windSection = this.section(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND, "physics.wind", false);

        windSection.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_STRENGTH, this.windStrength),
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_DIRECTION),
            UI.row(this.windX, this.windY, this.windZ),
            this.windLocal,
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE, this.windTurbulence),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE_SPEED, this.windTurbulenceSpeed),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE_SCALE, this.windTurbulenceScale)
        );

        this.options.add(
            this.debugRow(this.debug, BBSSettings.physicsDebug),
            this.bonesSearch,
            settings,
            collisionsSection,
            windSection
        );
    }

    @Override
    public void startEdit(ModelForm form)
    {
        this.debug.setValue(BBSSettings.physicsDebug.enabled.get());

        super.startEdit(form);

        this.updateWindFields();
    }

    @Override
    protected void setElementsEnabled(boolean enabled)
    {
        this.bonesSearch.setEnabled(enabled);
        this.bones.setEnabled(enabled);
        this.enabled.setEnabled(enabled);
        this.end.setEnabled(enabled);
        this.targetBone.setEnabled(enabled);
        this.gravity.setEnabled(enabled);
        this.relativeGravity.setEnabled(enabled);
        this.relativeGravityRotateX.setEnabled(enabled);
        this.relativeGravityRotateY.setEnabled(enabled);
        this.relativeGravityRotateZ.setEnabled(enabled);
        this.stiffness.setEnabled(enabled);
        this.damping.setEnabled(enabled);
        this.iterations.setEnabled(enabled);
        this.collisions.setEnabled(enabled);
        this.radius.setEnabled(enabled);
        this.windStrength.setEnabled(enabled);
        this.windX.setEnabled(enabled);
        this.windY.setEnabled(enabled);
        this.windZ.setEnabled(enabled);
        this.windTurbulence.setEnabled(enabled);
        this.windTurbulenceSpeed.setEnabled(enabled);
        this.windTurbulenceScale.setEnabled(enabled);
        this.windLocal.setEnabled(enabled);
    }

    /** Edits the selected bone's physics scalars as one value change (one undo entry). */
    private void editControl(Consumer<PhysicsControl> edit)
    {
        this.editBone((bone) ->
        {
            PhysicsControl control = bone.physics.get().copy();

            edit.accept(control);
            bone.physics.set(control);
        });
    }

    /** Edits the form's wind as one value change (one undo entry). */
    private void editWind(Consumer<WindControl> edit)
    {
        if (this.form == null)
        {
            return;
        }

        WindControl wind = this.form.wind.get().copy();

        edit.accept(wind);
        this.form.wind.set(wind);
    }

    @Override
    protected void updateFields()
    {
        boolean panelEnabled = this.bones.isEnabled();
        boolean boneSelected = !this.selectedBone.isEmpty();
        FormBone bone = this.selectedFormBone();
        PhysicsControl control = bone == null ? PhysicsControl.DEFAULT : bone.physics.get();
        boolean hasChain = bone != null && bone.hasPhysicsChain();
        boolean active = panelEnabled && boneSelected && hasChain && control.enabled;

        this.enabled.setEnabled(panelEnabled && boneSelected);
        this.enabled.setValue(hasChain && control.enabled);

        this.end.setEnabled(active);
        this.targetBone.setEnabled(active);
        this.gravity.setEnabled(active);
        this.relativeGravity.setEnabled(active);
        this.relativeGravityRotateX.setEnabled(active);
        this.relativeGravityRotateY.setEnabled(active);
        this.relativeGravityRotateZ.setEnabled(active);
        this.stiffness.setEnabled(active);
        this.damping.setEnabled(active);
        this.iterations.setEnabled(active);
        this.collisions.setEnabled(active);
        this.radius.setEnabled(active);

        String end = bone == null ? "" : bone.physicsEnd.get();
        String target = bone == null ? "" : bone.physicsTargetBone.get();

        this.end.setLabel(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_END.format(end.isEmpty() ? "-" : end));
        this.targetBone.setLabel(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_TARGET.format(target.isEmpty() ? "-" : target));
        this.gravity.setValue(control.gravity);
        this.relativeGravity.setValue(bone != null && bone.physicsRelativeGravity.get());
        this.relativeGravityRotateX.setValue(bone == null ? 0D : bone.physicsGravityRotateX.get());
        this.relativeGravityRotateY.setValue(bone == null ? 0D : bone.physicsGravityRotateY.get());
        this.relativeGravityRotateZ.setValue(bone == null ? 0D : bone.physicsGravityRotateZ.get());
        this.stiffness.setValue(control.stiffness);
        this.damping.setValue(control.damping);
        this.iterations.setValue(bone == null ? 4 : bone.physicsIterations.get());
        this.collisions.setValue(bone != null && bone.physicsCollisions.get());
        this.radius.setValue(bone == null ? 0.1D : bone.physicsRadius.get());
    }

    private void updateWindFields()
    {
        WindControl wind = this.form == null ? WindControl.DEFAULT : this.form.wind.get();

        this.windStrength.setValue(wind.strength);
        this.windX.setValue(wind.x);
        this.windY.setValue(wind.y);
        this.windZ.setValue(wind.z);
        this.windTurbulence.setValue(wind.turbulence);
        this.windTurbulenceSpeed.setValue(wind.turbulenceSpeed);
        this.windTurbulenceScale.setValue(wind.turbulenceScale);
        this.windLocal.setValue(wind.local);
    }

    private void fillEndMenu(UIBonePickerContextMenu picker)
    {
        if (this.selectedBone.isEmpty() || this.availableBones.isEmpty() || this.modelInstance == null || this.modelInstance.model == null)
        {
            return;
        }

        List<String> candidates = this.getEndCandidates(this.selectedBone);

        if (candidates.isEmpty())
        {
            candidates = this.availableBones;
        }

        /* The picker shows only the candidate branch (the subtree under the selected
         * root) — everything else is hidden, not grayed, so the short valid list
         * doesn't drown in the full skeleton. */
        Set<String> hidden = new HashSet<>(this.modelInstance.model.getAllGroupKeys());

        candidates.forEach(hidden::remove);
        picker.bones(this.modelInstance.model, hidden).set(this.readBone((b) -> b.physicsEnd.get(), ""));
    }

    /** Whether the bone is a chain end the selected root accepts — the same set the popup offers. */
    private boolean isEndCandidate(String bone)
    {
        List<String> candidates = this.getEndCandidates(this.selectedBone);

        return candidates.isEmpty() ? this.availableBones.contains(bone) : candidates.contains(bone);
    }

    private boolean isValidChain(String rootId, String endId)
    {
        if (this.modelInstance == null || this.modelInstance.model == null)
        {
            return true;
        }

        IModel model = this.modelInstance.model;

        if (rootId == null || rootId.isEmpty() || endId == null || endId.isEmpty())
        {
            return false;
        }

        if (!model.getAllGroupKeys().contains(rootId) || !model.getAllGroupKeys().contains(endId))
        {
            return false;
        }

        String group = endId;

        while (group != null && !group.isEmpty())
        {
            if (group.equals(rootId))
            {
                return true;
            }

            String parent = model.getParentGroupKey(group);

            if (parent == null || parent.equals(group))
            {
                break;
            }

            group = parent;
        }

        return false;
    }

    private List<String> getEndCandidates(String rootId)
    {
        if (rootId == null || rootId.isEmpty() || this.availableBones.isEmpty())
        {
            return Collections.emptyList();
        }

        List<String> out = new ArrayList<>();

        for (String bone : this.availableBones)
        {
            if (this.isValidChain(rootId, bone))
            {
                out.add(bone);
            }
        }

        return out;
    }

    @Override
    protected MapType toPresetData()
    {
        return this.form == null ? new MapType() : BonePhysicsIO.write(this.form.bones, this.form.wind);
    }

    @Override
    protected void applyPresetData(MapType map)
    {
        if (this.form == null)
        {
            return;
        }

        BonePhysicsIO.read(map, this.form.bones, this.form.wind, true);

        this.updateFields();
        this.updateWindFields();
    }

    private static UITrackpad windAxisTrackpad(Consumer<Double> callback, int color)
    {
        UITrackpad t = new UITrackpad(callback).onlyNumbers().values(0.1D, 0.5D, 1D).increment(0.1D);
        t.textbox.setColor(color);
        return t;
    }

}
