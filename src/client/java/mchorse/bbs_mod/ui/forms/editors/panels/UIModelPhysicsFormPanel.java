package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsConfig;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsIO;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.utils.UIDebugOverlayContextMenu;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.presets.UIDataContextMenu;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.ModelPhysicsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class UIModelPhysicsFormPanel extends UIFormPanel<ModelForm>
{
    private static final float DEFAULT_GRAVITY = 1F;
    private static final float DEFAULT_STIFFNESS = ModelPhysicsConfig.DEFAULT_STIFFNESS;
    private static final float DEFAULT_DAMPING = 0.15F;
    private static final int DEFAULT_ITERATIONS = 4;
    private static final float DEFAULT_RADIUS = 0.1F;

    public UIToggle debug;
    public UIButton end;
    public UIButton targetBone;
    public UIStringList bones;
    public UIToggle enabled;
    public UITrackpad gravity;
    public UIToggle relativeGravity;
    public UITrackpad relativeGravityRotateX;
    public UITrackpad relativeGravityRotateY;
    public UITrackpad relativeGravityRotateZ;
    public UITrackpad stiffness;
    public UITrackpad damping;
    public UITrackpad iterations;
    public UIToggle collisions;
    public UITrackpad radius;
    public UITrackpad windStrength;
    public UITrackpad windX;
    public UITrackpad windY;
    public UITrackpad windZ;
    public UITrackpad windTurbulence;
    public UITrackpad windTurbulenceSpeed;
    public UITrackpad windTurbulenceScale;

    private List<String> availableBones = Collections.emptyList();
    private String selectedBone = "";
    private final Map<String, BoneData> data = new HashMap<>();
    private final WindData wind = new WindData();
    private ModelInstance modelInstance;
    private String presetGroup = "";
    private boolean syncingUI;

    private static class BoneData
    {
        public String end = "";
        public String targetBone = "";
        public float gravity = DEFAULT_GRAVITY;
        public boolean relativeGravity;
        public float relativeGravityRotateX;
        public float relativeGravityRotateY;
        public float relativeGravityRotateZ;
        public float stiffness = DEFAULT_STIFFNESS;
        public float damping = DEFAULT_DAMPING;
        public int iterations = DEFAULT_ITERATIONS;
        public boolean collisions;
        public float radius = DEFAULT_RADIUS;
    }

    private static class WindData
    {
        public float strength = ModelPhysicsConfig.Wind.NONE.strength();
        public float x = ModelPhysicsConfig.Wind.NONE.x();
        public float y = ModelPhysicsConfig.Wind.NONE.y();
        public float z = ModelPhysicsConfig.Wind.NONE.z();
        public float turbulence = ModelPhysicsConfig.Wind.NONE.turbulence();
        public float turbulenceSpeed = ModelPhysicsConfig.Wind.NONE.turbulenceSpeed();
        public float turbulenceScale = ModelPhysicsConfig.Wind.NONE.turbulenceScale();

        public ModelPhysicsConfig.Wind toWind()
        {
            return new ModelPhysicsConfig.Wind(this.strength, this.x, this.y, this.z, this.turbulence, this.turbulenceSpeed, this.turbulenceScale);
        }

        public void set(ModelPhysicsConfig.Wind wind)
        {
            this.strength = wind.strength();
            this.x = wind.x();
            this.y = wind.y();
            this.z = wind.z();
            this.turbulence = wind.turbulence();
            this.turbulenceSpeed = wind.turbulenceSpeed();
            this.turbulenceScale = wind.turbulenceScale();
        }
    }

    public UIModelPhysicsFormPanel(UIForm editor)
    {
        super(editor);

        IKey axis = IKey.constant("%s (%s)");

        this.bones = new UIStringList((l) ->
        {
            this.selectedBone = l.isEmpty() ? "" : l.get(0);
            this.updateFields();
        });
        this.bones.background().h(UIConstants.LIST_ITEM_HEIGHT * 8);
        this.bones.context(() -> new UIDataContextMenu(ModelPhysicsManager.INSTANCE, this.presetGroup, this::toPresetData, this::applyPresetData).tooltips("_CopyModelPhysics",
            UIKeys.FORMS_EDITORS_MODEL_PHYSICS_CONTEXT_COPY,
            UIKeys.FORMS_EDITORS_MODEL_PHYSICS_CONTEXT_PASTE,
            UIKeys.FORMS_EDITORS_MODEL_PHYSICS_CONTEXT_RESET,
            UIKeys.FORMS_EDITORS_MODEL_PHYSICS_CONTEXT_SAVE,
            UIKeys.FORMS_EDITORS_MODEL_PHYSICS_CONTEXT_NAME
        ));

        this.debug = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_DEBUG, (b) -> BBSSettings.physicsDebug.enabled.set(b.getValue()));
        this.debug.setValue(BBSSettings.physicsDebug.enabled.get());
        this.debug.context(() -> new UIDebugOverlayContextMenu(BBSSettings.physicsDebug));

        this.enabled = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ENABLED, (b) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            if (b.getValue())
            {
                BoneData d = this.data.computeIfAbsent(this.selectedBone, (k) -> new BoneData());

                if (d.end == null || d.end.isEmpty())
                {
                    d.end = this.selectedBone;
                }
            }
            else
            {
                this.data.remove(this.selectedBone);
            }

            this.updateFields();
            this.commitChanges();
        });

        this.gravity = new UITrackpad((v) ->
        {
            if (this.syncingUI)
            {
                return;
            }

            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.gravity = v.floatValue();
                this.commitChanges();
            }
        });
        this.gravity.onlyNumbers().values(0.1D, 0.01D, 0.5D).increment(0.25D).limit(0D, 10D);
        this.gravity.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_GRAVITY);

        this.relativeGravity = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY, (b) ->
        {
            if (this.syncingUI)
            {
                return;
            }

            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.relativeGravity = b.getValue();
                this.commitChanges();
            }
        });

        this.relativeGravityRotateX = axisTrackpad((v) ->
        {
            if (this.syncingUI)
            {
                return;
            }

            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.relativeGravityRotateX = v.floatValue();
                this.commitChanges();
            }
        }, Colors.RED, axis.format(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY_ROTATION, UIKeys.GENERAL_X));
        this.relativeGravityRotateY = axisTrackpad((v) ->
        {
            if (this.syncingUI)
            {
                return;
            }

            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.relativeGravityRotateY = v.floatValue();
                this.commitChanges();
            }
        }, Colors.GREEN, axis.format(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY_ROTATION, UIKeys.GENERAL_Y));
        this.relativeGravityRotateZ = axisTrackpad((v) ->
        {
            if (this.syncingUI)
            {
                return;
            }

            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.relativeGravityRotateZ = v.floatValue();
                this.commitChanges();
            }
        }, Colors.BLUE, axis.format(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY_ROTATION, UIKeys.GENERAL_Z));

        this.stiffness = new UITrackpad((v) ->
        {
            if (this.syncingUI)
            {
                return;
            }

            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.stiffness = v.floatValue();
                this.commitChanges();
            }
        });
        this.stiffness.onlyNumbers().values(0.05D, 0.01D, 0.2D).increment(0.01D).limit(0D, 1D);
        this.stiffness.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_STIFFNESS);

        this.damping = new UITrackpad((v) ->
        {
            if (this.syncingUI)
            {
                return;
            }

            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.damping = v.floatValue();
                this.commitChanges();
            }
        });
        this.damping.onlyNumbers().values(0.05D, 0.01D, 0.2D).increment(0.01D).limit(0D, 1D);
        this.damping.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_DAMPING);

        this.iterations = new UITrackpad((v) ->
        {
            if (this.syncingUI)
            {
                return;
            }

            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.iterations = v.intValue();
                this.commitChanges();
            }
        });
        this.iterations.onlyNumbers().integer().values(1D).increment(1D).limit(1D, 20D, true);
        this.iterations.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ITERATIONS);

        this.collisions = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_COLLISIONS, (b) ->
        {
            if (this.syncingUI)
            {
                return;
            }

            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.collisions = b.getValue();
                this.commitChanges();
            }
        });

        this.radius = new UITrackpad((v) ->
        {
            if (this.syncingUI)
            {
                return;
            }

            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.radius = v.floatValue();
                this.commitChanges();
            }
        });
        this.radius.onlyNumbers().values(0.05D, 0.01D, 0.2D).increment(0.01D).limit(0D, 1D);
        this.radius.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RADIUS);

        this.windStrength = new UITrackpad((v) ->
        {
            if (this.syncingUI)
            {
                return;
            }

            this.wind.strength = v.floatValue();
            this.commitChanges();
        });
        this.windStrength.onlyNumbers().values(0.1D, 0.01D, 0.5D).increment(0.25D).limit(0D, 10D);
        this.windStrength.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_STRENGTH);

        this.windX = windAxisTrackpad((v) ->
        {
            if (this.syncingUI)
            {
                return;
            }

            this.wind.x = v.floatValue();
            this.commitChanges();
        }, Colors.RED);
        this.windY = windAxisTrackpad((v) ->
        {
            if (this.syncingUI)
            {
                return;
            }

            this.wind.y = v.floatValue();
            this.commitChanges();
        }, Colors.GREEN);
        this.windZ = windAxisTrackpad((v) ->
        {
            if (this.syncingUI)
            {
                return;
            }

            this.wind.z = v.floatValue();
            this.commitChanges();
        }, Colors.BLUE);

        this.windTurbulence = new UITrackpad((v) ->
        {
            if (this.syncingUI)
            {
                return;
            }

            this.wind.turbulence = v.floatValue();
            this.commitChanges();
        });
        this.windTurbulence.onlyNumbers().values(0.05D, 0.01D, 0.2D).increment(0.05D).limit(0D, 1D);
        this.windTurbulence.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE);

        this.windTurbulenceSpeed = new UITrackpad((v) ->
        {
            if (this.syncingUI)
            {
                return;
            }

            this.wind.turbulenceSpeed = v.floatValue();
            this.commitChanges();
        });
        this.windTurbulenceSpeed.onlyNumbers().values(0.1D, 0.05D, 0.5D).increment(0.1D).limit(0D, 10D);
        this.windTurbulenceSpeed.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE_SPEED);

        this.windTurbulenceScale = new UITrackpad((v) ->
        {
            if (this.syncingUI)
            {
                return;
            }

            this.wind.turbulenceScale = v.floatValue();
            this.commitChanges();
        });
        this.windTurbulenceScale.onlyNumbers().values(0.1D, 0.05D, 0.5D).increment(0.1D).limit(0D, 10D);
        this.windTurbulenceScale.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE_SCALE);

        this.end = new UIButton(IKey.EMPTY, (b) ->
        {
            BoneData d = this.getSelectedData();

            if (d == null)
            {
                return;
            }

            this.openEndMenu(d.end, (bone) ->
            {
                d.end = bone;
                this.updateFields();
                this.commitChanges();
            });
        });

        this.targetBone = new UIButton(IKey.EMPTY, (b) ->
        {
            BoneData d = this.getSelectedData();

            if (d == null)
            {
                return;
            }

            this.getContext().replaceContextMenu((menu) ->
            {
                menu.action(Icons.CLOSE, UIKeys.GENERAL_NONE, d.targetBone.isEmpty(), () ->
                {
                    d.targetBone = "";
                    this.updateFields();
                    this.commitChanges();
                });

                for (String bone : this.availableBones)
                {
                    boolean selected = bone.equals(d.targetBone);
                    menu.action(Icons.LIMB, IKey.constant(bone), selected, () ->
                    {
                        d.targetBone = bone;
                        this.updateFields();
                        this.commitChanges();
                    });
                }
            });
        });

        UISection settings = new UISection(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_SETTINGS);

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

        UISection collisionsSection = new UISection(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_COLLISIONS);

        collisionsSection.fields.add(
            this.collisions,
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RADIUS, this.radius)
        );

        /* Wind is one field for the whole model's physics, not bound to any bone, so the section is always
         * editable and does not depend on which bone is selected in the list. */
        UISection windSection = new UISection(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND);

        windSection.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_STRENGTH, this.windStrength),
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_DIRECTION),
            UI.row(this.windX, this.windY, this.windZ),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE, this.windTurbulence),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE_SPEED, this.windTurbulenceSpeed),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE_SCALE, this.windTurbulenceScale)
        );

        UIIcon debugSettings = new UIIcon(Icons.GEAR, (b) -> this.getContext().replaceContextMenu(new UIDebugOverlayContextMenu(BBSSettings.physicsDebug)));

        debugSettings.tooltip(UIKeys.MODEL_DEBUG_CONFIGURE);
        debugSettings.wh(20, 14);

        UIElement debugRow = new UIElement();

        debugRow.row(0).preferred(0).height(14);
        debugRow.add(this.debug, debugSettings);

        this.options.add(
            debugRow,
            this.bones,
            settings,
            collisionsSection,
            windSection
        );
    }

    @Override
    public void startEdit(ModelForm form)
    {
        super.startEdit(form);

        this.debug.setValue(BBSSettings.physicsDebug.enabled.get());

        ModelInstance model = ModelFormRenderer.getModel(form);
        this.modelInstance = model;
        this.presetGroup = this.resolvePresetGroup(form, model);

        if (model == null || model.model == null)
        {
            this.availableBones = Collections.emptyList();
            this.data.clear();
            this.wind.set(ModelPhysicsConfig.Wind.NONE);
            this.bones.setList(Collections.emptyList());
            this.bones.deselect();
            this.selectedBone = "";
            this.setElementsEnabled(false);
            this.updateWindFields();
        }
        else
        {
            List<String> bones = new ArrayList<>(model.model.getGroupKeysInHierarchyOrder());
            bones.removeIf(model.getDisabledBones()::contains);
            this.availableBones = bones;

            this.setElementsEnabled(true);
            this.load();
            this.bones.setList(this.availableBones);
            this.updateWindFields();

            if (!this.availableBones.isEmpty())
            {
                this.selectBone(this.availableBones.get(0));
            }
        }

        this.options.resize();
    }

    private void setElementsEnabled(boolean enabled)
    {
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
    }

    private BoneData getSelectedData()
    {
        return this.selectedBone.isEmpty() ? null : this.data.get(this.selectedBone);
    }

    private void selectBone(String bone)
    {
        this.selectedBone = bone == null ? "" : bone;
        this.bones.setCurrentScroll(this.selectedBone);
        this.updateFields();
    }

    @Override
    public boolean pickBoneInList(String bone)
    {
        if (bone == null || bone.isEmpty() || !this.availableBones.contains(bone))
        {
            return false;
        }

        this.selectBone(bone);

        return true;
    }

    private void updateFields()
    {
        boolean panelEnabled = this.bones.isEnabled();
        boolean boneSelected = !this.selectedBone.isEmpty();
        BoneData d = this.getSelectedData();
        boolean active = panelEnabled && boneSelected && d != null;

        this.enabled.setEnabled(panelEnabled && boneSelected);
        this.enabled.setValue(d != null);

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

        this.syncingUI = true;

        try
        {
            if (d == null)
            {
                this.end.label = UIKeys.FORMS_EDITORS_MODEL_PHYSICS_END.format("-");
                this.targetBone.label = UIKeys.FORMS_EDITORS_MODEL_PHYSICS_TARGET.format("-");
                this.gravity.setValue(DEFAULT_GRAVITY);
                this.relativeGravity.setValue(false);
                this.relativeGravityRotateX.setValue(0);
                this.relativeGravityRotateY.setValue(0);
                this.relativeGravityRotateZ.setValue(0);
                this.stiffness.setValue(DEFAULT_STIFFNESS);
                this.damping.setValue(DEFAULT_DAMPING);
                this.iterations.setValue(DEFAULT_ITERATIONS);
                this.collisions.setValue(false);
                this.radius.setValue(DEFAULT_RADIUS);
            }
            else
            {
                this.end.label = UIKeys.FORMS_EDITORS_MODEL_PHYSICS_END.format(d.end == null || d.end.isEmpty() ? "-" : d.end);
                this.targetBone.label = UIKeys.FORMS_EDITORS_MODEL_PHYSICS_TARGET.format(d.targetBone == null || d.targetBone.isEmpty() ? "-" : d.targetBone);
                this.gravity.setValue(d.gravity);
                this.relativeGravity.setValue(d.relativeGravity);
                this.relativeGravityRotateX.setValue(d.relativeGravityRotateX);
                this.relativeGravityRotateY.setValue(d.relativeGravityRotateY);
                this.relativeGravityRotateZ.setValue(d.relativeGravityRotateZ);
                this.stiffness.setValue(d.stiffness);
                this.damping.setValue(d.damping);
                this.iterations.setValue(d.iterations);
                this.collisions.setValue(d.collisions);
                this.radius.setValue(d.radius);
            }
        }
        finally
        {
            this.syncingUI = false;
        }
    }

    private void updateWindFields()
    {
        this.syncingUI = true;

        try
        {
            this.windStrength.setValue(this.wind.strength);
            this.windX.setValue(this.wind.x);
            this.windY.setValue(this.wind.y);
            this.windZ.setValue(this.wind.z);
            this.windTurbulence.setValue(this.wind.turbulence);
            this.windTurbulenceSpeed.setValue(this.wind.turbulenceSpeed);
            this.windTurbulenceScale.setValue(this.wind.turbulenceScale);
        }
        finally
        {
            this.syncingUI = false;
        }
    }

    private void openEndMenu(String current, Consumer<String> callback)
    {
        if (this.availableBones.isEmpty() || this.selectedBone.isEmpty())
        {
            return;
        }

        this.getContext().replaceContextMenu((menu) ->
        {
            List<String> candidates = this.getEndCandidates(this.selectedBone);

            if (candidates.isEmpty())
            {
                candidates = this.availableBones;
            }

            for (String bone : candidates)
            {
                boolean selected = bone.equals(current);
                menu.action(Icons.LIMB, IKey.constant(bone), selected, () -> callback.accept(bone));
            }
        });
    }

    private void load()
    {
        ModelPhysicsConfig config = null;
        if (this.form != null && this.form.physics.get() instanceof MapType map)
        {
            config = ModelPhysicsIO.fromData(map);
        }

        this.load(config);
    }

    private void load(ModelPhysicsConfig config)
    {
        this.data.clear();
        this.wind.set(config == null ? ModelPhysicsConfig.Wind.NONE : config.wind());

        if (config == null || config.bones() == null)
        {
            return;
        }

        for (Map.Entry<String, ModelPhysicsConfig.Bone> entry : config.bones().entrySet())
        {
            String root = entry.getKey();
            ModelPhysicsConfig.Bone bone = entry.getValue();

            if (root == null || root.isEmpty() || bone == null || bone.end() == null || bone.end().isEmpty())
            {
                continue;
            }

            if (!this.availableBones.isEmpty() && (!this.availableBones.contains(root) || !this.availableBones.contains(bone.end())))
            {
                continue;
            }

            if (!this.isValidChain(root, bone.end()))
            {
                continue;
            }

            BoneData d = new BoneData();
            d.end = bone.end();
            d.targetBone = bone.targetBone() == null ? "" : bone.targetBone();
            d.gravity = bone.gravity();
            d.relativeGravity = bone.relativeGravity();
            d.relativeGravityRotateX = bone.relativeGravityRotateX();
            d.relativeGravityRotateY = bone.relativeGravityRotateY();
            d.relativeGravityRotateZ = bone.relativeGravityRotateZ();
            d.stiffness = bone.stiffness();
            d.damping = bone.damping();
            d.iterations = bone.iterations();
            d.collisions = bone.collisions();
            d.radius = bone.radius();

            if (!d.targetBone.isEmpty() && !this.availableBones.isEmpty() && !this.availableBones.contains(d.targetBone))
            {
                d.targetBone = "";
            }

            this.data.put(root, d);
        }
    }

    private MapType toPresetData()
    {
        Map<String, ModelPhysicsConfig.Bone> bones = new HashMap<>();

        for (Map.Entry<String, BoneData> entry : this.data.entrySet())
        {
            String root = entry.getKey();
            BoneData d = entry.getValue();

            if (d == null || root == null || root.isEmpty() || d.end == null || d.end.isEmpty())
            {
                continue;
            }

            if (!this.availableBones.isEmpty() && (!this.availableBones.contains(root) || !this.availableBones.contains(d.end)))
            {
                continue;
            }

            if (!this.isValidChain(root, d.end))
            {
                continue;
            }

            String target = d.targetBone == null ? "" : d.targetBone;

            if (!target.isEmpty() && !this.availableBones.isEmpty() && !this.availableBones.contains(target))
            {
                target = "";
            }

            bones.put(root, new ModelPhysicsConfig.Bone(d.end, target, d.gravity, d.damping, d.stiffness, d.iterations, d.relativeGravity, d.relativeGravityRotateX, d.relativeGravityRotateY, d.relativeGravityRotateZ, d.collisions, d.radius, ModelPhysicsConfig.DEFAULT_WEIGHT));
        }

        ModelPhysicsConfig.Wind wind = this.wind.toWind();

        if (bones.isEmpty() && wind.isDefault())
        {
            return new MapType();
        }

        return ModelPhysicsIO.toData(new ModelPhysicsConfig(bones, wind));
    }

    private void applyPresetData(MapType map)
    {
        String current = this.selectedBone;

        this.load(ModelPhysicsIO.fromData(map));
        this.updateWindFields();

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

    private void commitChanges()
    {
        this.save(false);
    }

    private void save(boolean notify)
    {
        if (this.form == null)
        {
            if (notify)
            {
                this.getContext().notifyError(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_SAVE_ERROR);
            }

            return;
        }

        for (Map.Entry<String, BoneData> entry : this.data.entrySet())
        {
            String root = entry.getKey();
            BoneData d = entry.getValue();

            if (d == null || root == null || root.isEmpty() || d.end == null || d.end.isEmpty())
            {
                continue;
            }

            if (!this.isValidChain(root, d.end))
            {
                if (notify)
                {
                    this.getContext().notifyError(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_INVALID_CHAIN.format(root, d.end));
                }

                return;
            }
        }

        MapType map = this.toPresetData();
        this.form.physics.set(map.isEmpty() ? null : map);

        if (notify)
        {
            this.getContext().notifySuccess(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_SAVED);
        }
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

    private static UITrackpad axisTrackpad(Consumer<Double> callback, int color, IKey tooltip)
    {
        UITrackpad t = new UITrackpad(callback).degrees().onlyNumbers().limit(-180D, 180D);
        t.textbox.setColor(color);
        t.tooltip(tooltip);
        return t;
    }

    private static UITrackpad windAxisTrackpad(Consumer<Double> callback, int color)
    {
        UITrackpad t = new UITrackpad(callback).onlyNumbers().values(0.1D, 0.5D, 1D).increment(0.1D);
        t.textbox.setColor(color);
        return t;
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
