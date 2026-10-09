package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.cubic.shake.BoneShake;
import mchorse.bbs_mod.cubic.shake.ShakeControl;
import mchorse.bbs_mod.cubic.shake.ShakeControls;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIShakeControlFields;
import mchorse.bbs_mod.ui.utils.bones.UIBoneTreeList;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.ModelShakeManager;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The shake tab: pick a bone, switch its shake on and give it a character. How strongly it shakes
 * over time is the form's shake track, which keys the shaking bones the way the solver tracks key
 * their chains.
 */
public class UIModelShakeFormPanel extends UIBoneListFormPanel
{
    /** The shake track's own green — the camera shake clip's. */
    private static final int MARKER_SHAKE = Colors.A100 | 0x159e64;

    public UIToggle enabled;
    public UISliderTrackpad strength;
    public UISliderTrackpad frequency;
    public UISliderTrackpad roughness;
    public UITrackpad seed;
    public UITrackpad rotateX;
    public UITrackpad rotateY;
    public UITrackpad rotateZ;
    public UITrackpad translateX;
    public UITrackpad translateY;
    public UITrackpad translateZ;
    public UITrackpad scaleX;
    public UITrackpad scaleY;
    public UITrackpad scaleZ;

    /* Hidden while the bone doesn't shake: off means gone, as on the solver tabs. */
    private UIElement fields;
    private UISection amplitude;

    /** Shown when IK or Spline IK owns the bone's rotation: the solve would take the shake out of it. */
    private UILabel driven;

    private final Map<String, UIBoneTreeList.Marker[]> boneMarkers = new HashMap<>();

    public UIModelShakeFormPanel(UIForm editor)
    {
        super(editor);

        this.bones.markers(this.boneMarkers::get, UIKeys.FORMS_EDITORS_MODEL_SHAKE_BONES_TOOLTIP);
        this.bonePresets(ModelShakeManager.INSTANCE, "_CopyModelShake",
            UIKeys.FORMS_EDITORS_MODEL_SHAKE_CONTEXT_COPY,
            UIKeys.FORMS_EDITORS_MODEL_SHAKE_CONTEXT_PASTE,
            UIKeys.FORMS_EDITORS_MODEL_SHAKE_CONTEXT_RESET,
            UIKeys.FORMS_EDITORS_MODEL_SHAKE_CONTEXT_SAVE,
            UIKeys.FORMS_EDITORS_MODEL_SHAKE_CONTEXT_NAME,
            this::toPresetData, this::applyPresetData
        );

        this.enabled = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_SHAKE_ENABLED, (b) -> this.setShakeEnabled(b.getValue()));

        this.strength = new UIShakeControlFields(this::editControl).strength;

        this.frequency = new UISliderTrackpad((v) -> this.editShake((s) -> s.frequency = v.floatValue()));
        this.frequency.onlyNumbers().values(0.5D, 0.1D, 2D).increment(0.5D).limit(0.1D, 20D);
        this.frequency.tooltip(UIKeys.FORMS_EDITORS_MODEL_SHAKE_FREQUENCY_TOOLTIP);

        this.roughness = new UISliderTrackpad((v) -> this.editShake((s) -> s.roughness = v.floatValue()));
        this.roughness.normalized().tooltip(UIKeys.FORMS_EDITORS_MODEL_SHAKE_ROUGHNESS_TOOLTIP);

        this.seed = new UITrackpad((v) -> this.editShake((s) -> s.seed = v.intValue()));
        this.seed.onlyNumbers().integer().values(1D).increment(1D).limit(0D);
        this.seed.tooltip(UIKeys.FORMS_EDITORS_MODEL_SHAKE_SEED_TOOLTIP);

        this.rotateX = this.amplitude(UIKeys.TRANSFORMS_ROTATE, UIKeys.GENERAL_X, Colors.RED, (s, v) -> s.rotate.x = v).degrees();
        this.rotateY = this.amplitude(UIKeys.TRANSFORMS_ROTATE, UIKeys.GENERAL_Y, Colors.GREEN, (s, v) -> s.rotate.y = v).degrees();
        this.rotateZ = this.amplitude(UIKeys.TRANSFORMS_ROTATE, UIKeys.GENERAL_Z, Colors.BLUE, (s, v) -> s.rotate.z = v).degrees();
        this.translateX = this.amplitude(UIKeys.TRANSFORMS_TRANSLATE, UIKeys.GENERAL_X, Colors.RED, (s, v) -> s.translate.x = v).block();
        this.translateY = this.amplitude(UIKeys.TRANSFORMS_TRANSLATE, UIKeys.GENERAL_Y, Colors.GREEN, (s, v) -> s.translate.y = v).block();
        this.translateZ = this.amplitude(UIKeys.TRANSFORMS_TRANSLATE, UIKeys.GENERAL_Z, Colors.BLUE, (s, v) -> s.translate.z = v).block();
        this.scaleX = this.amplitude(UIKeys.TRANSFORMS_SCALE, UIKeys.GENERAL_X, Colors.RED, (s, v) -> s.scale.x = v).factor();
        this.scaleY = this.amplitude(UIKeys.TRANSFORMS_SCALE, UIKeys.GENERAL_Y, Colors.GREEN, (s, v) -> s.scale.y = v).factor();
        this.scaleZ = this.amplitude(UIKeys.TRANSFORMS_SCALE, UIKeys.GENERAL_Z, Colors.BLUE, (s, v) -> s.scale.z = v).factor();

        this.driven = UI.label(UIKeys.FORMS_EDITORS_MODEL_SHAKE_DRIVEN, UIConstants.CONTROL_HEIGHT, Colors.ORANGE);
        this.driven.labelAnchor(0, 0.5F);
        this.driven.tooltip(UIKeys.FORMS_EDITORS_MODEL_SHAKE_DRIVEN_TOOLTIP);

        this.fields = UI.column(UIConstants.MARGIN,
            this.driven,
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_SHAKE_STRENGTH, this.strength),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_SHAKE_FREQUENCY, this.frequency),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_SHAKE_ROUGHNESS, this.roughness),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_SHAKE_SEED, this.seed)
        );

        UISection settings = this.section(UIKeys.FORMS_EDITORS_MODEL_SHAKE_SETTINGS, "shake.settings", true);

        settings.fields.add(this.enabled, this.fields);

        /* Rotation first: it is what a bone shakes with most of the time. */
        this.amplitude = this.section(UIKeys.FORMS_EDITORS_MODEL_SHAKE_AMPLITUDE, "shake.amplitude", true);
        this.amplitude.fields.add(
            UI.label(UIKeys.TRANSFORMS_ROTATE),
            UI.row(this.rotateX, this.rotateY, this.rotateZ),
            UI.label(UIKeys.TRANSFORMS_TRANSLATE),
            UI.row(this.translateX, this.translateY, this.translateZ),
            UI.label(UIKeys.TRANSFORMS_SCALE),
            UI.row(this.scaleX, this.scaleY, this.scaleZ)
        );

        this.options.add(this.bonesSearch, settings, this.amplitude);
    }

    /** One axis' amplitude: none below zero, an axis at zero simply doesn't shake. */
    private UITrackpad amplitude(IKey kind, IKey axis, int color, BiConsumer<BoneShake, Float> setter)
    {
        UITrackpad pad = new UITrackpad((v) -> this.editShake((s) -> setter.accept(s, v.floatValue())));

        pad.onlyNumbers().limit(0D);
        pad.textbox.setColor(color);
        pad.tooltip(IKey.constant("%s (%s)").format(kind, axis));

        return pad;
    }

    @Override
    protected void setElementsEnabled(boolean enabled)
    {
        this.bonesSearch.setEnabled(enabled);
        this.bones.setEnabled(enabled);
        this.enabled.setEnabled(enabled);
    }

    private void setShakeEnabled(boolean enabled)
    {
        if (this.form == null || this.selectedBone.isEmpty())
        {
            return;
        }

        if (enabled)
        {
            this.editBone((bone) -> bone.shake.set(BoneShake.starter(), IValueListener.FLAG_UNMERGEABLE));
        }
        else
        {
            FormBone bone = this.selectedFormBone();

            /* Off means gone: the character and the strength both go, the bone is neutral again. */
            if (bone != null)
            {
                bone.shake.set(new BoneShake(), IValueListener.FLAG_UNMERGEABLE);
                BaseValue.edit(this.form.shake, IValueListener.FLAG_UNMERGEABLE, (value) -> value.getOriginalValue().controls.remove(bone.getId()));
            }
        }

        this.updateFields();
    }

    /** Edits the selected bone's shake as one value change (one undo entry). */
    private void editShake(Consumer<BoneShake> edit)
    {
        this.editBone((bone) ->
        {
            BoneShake shake = bone.shake.getOriginalValue().copy();

            edit.accept(shake);
            bone.shake.set(shake);
        });
    }

    /** Edits the selected bone's strength in the form's shake controls as one value change. */
    private void editControl(Consumer<ShakeControl> edit)
    {
        this.editBone((bone) ->
        {
            ShakeControl current = this.form.shake.getOriginalValue().controls.get(bone.getId());
            ShakeControl control = current == null ? new ShakeControl() : current.copy();

            edit.accept(control);
            BaseValue.edit(this.form.shake, (value) -> value.getOriginalValue().controls.put(bone.getId(), control));
        });
    }

    private float currentStrength()
    {
        ShakeControl control = this.form == null ? null : this.form.shake.get().controls.get(this.selectedBone);

        return control == null ? ShakeControl.DEFAULT_STRENGTH : control.strength;
    }

    private boolean isRotationDriven()
    {
        IModel model = this.modelInstance == null ? null : this.modelInstance.model;

        return model != null && ModelIKRuntime.isRotationConstrained(model, this.form, this.selectedBone);
    }

    @Override
    protected void updateFields()
    {
        if (this.amplitude == null)
        {
            return;
        }

        this.updateMarkers();

        FormBone bone = this.selectedFormBone();
        BoneShake shake = bone == null ? BoneShake.DEFAULT : bone.shake.get();
        boolean on = shake.enabled;

        this.enabled.setEnabled(this.bones.isEnabled() && !this.selectedBone.isEmpty());
        this.enabled.setValue(on);
        this.fields.setVisible(on);
        this.amplitude.setVisible(on);
        this.driven.setVisible(on && this.isRotationDriven());

        this.strength.setValue(this.currentStrength());
        this.frequency.setValue(shake.frequency);
        this.roughness.setValue(shake.roughness);
        this.seed.setValue(shake.seed);
        this.rotateX.setValue(shake.rotate.x);
        this.rotateY.setValue(shake.rotate.y);
        this.rotateZ.setValue(shake.rotate.z);
        this.translateX.setValue(shake.translate.x);
        this.translateY.setValue(shake.translate.y);
        this.translateZ.setValue(shake.translate.z);
        this.scaleX.setValue(shake.scale.x);
        this.scaleY.setValue(shake.scale.y);
        this.scaleZ.setValue(shake.scale.z);

        this.options.resize();
    }

    /** A dot on every shaking bone, so the rig's shake reads off the list itself. */
    private void updateMarkers()
    {
        this.boneMarkers.clear();

        if (this.form == null)
        {
            return;
        }

        for (BaseValue value : this.form.bones.getAll())
        {
            if (value instanceof FormBone bone && bone.hasShake())
            {
                this.boneMarkers.put(bone.getId(), new UIBoneTreeList.Marker[] {new UIBoneTreeList.Marker(MARKER_SHAKE, false)});
            }
        }
    }

    /** A preset is every shaking bone's shake with its strength, keyed by the bone. */
    protected MapType toPresetData()
    {
        MapType data = new MapType();

        if (this.form == null)
        {
            return data;
        }

        MapType bones = new MapType();
        ShakeControls controls = this.form.shake.getOriginalValue();

        for (BaseValue value : this.form.bones.getAll())
        {
            if (!(value instanceof FormBone bone) || !bone.hasShake())
            {
                continue;
            }

            MapType entry = bone.shake.getOriginalValue().toData();
            ShakeControl control = controls.controls.get(bone.getId());

            if (control != null)
            {
                entry.putFloat("strength", control.strength);
            }

            bones.put(bone.getId(), entry);
        }

        if (!bones.isEmpty())
        {
            data.put("bones", bones);
        }

        return data;
    }

    /** A preset is a complete state: a bone it doesn't mention stops shaking. */
    protected void applyPresetData(MapType data)
    {
        if (this.form == null)
        {
            return;
        }

        MapType bones = data.getMap("bones");

        BaseValue.edit(this.form, IValueListener.FLAG_UNMERGEABLE, (form) ->
        {
            for (BaseValue value : form.bones.getAll())
            {
                if (value instanceof FormBone bone && !bone.shake.getOriginalValue().isDefault())
                {
                    bone.shake.set(new BoneShake());
                }
            }

            form.shake.getOriginalValue().controls.clear();

            for (String id : bones.keys())
            {
                if (id.isEmpty() || !bones.has(id, BaseType.TYPE_MAP))
                {
                    continue;
                }

                MapType entry = bones.getMap(id);
                BoneShake shake = new BoneShake();

                shake.fromData(entry);

                if (!shake.enabled)
                {
                    continue;
                }

                form.bones.getOrCreate(id).shake.set(shake);

                if (entry.has("strength"))
                {
                    ShakeControl control = new ShakeControl();

                    control.strength = entry.getFloat("strength", ShakeControl.DEFAULT_STRENGTH);
                    form.shake.getOriginalValue().controls.put(id, control);
                }
            }
        });

        this.updateFields();
    }
}
