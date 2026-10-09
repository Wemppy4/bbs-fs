package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.constraints.BoneConstraintsIO;
import mchorse.bbs_mod.cubic.ik.BoneIKIO;
import mchorse.bbs_mod.cubic.physics.BonePhysicsIO;
import mchorse.bbs_mod.cubic.physics.WindControl;
import mchorse.bbs_mod.cubic.spline.ValueSplineIKs;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.utils.ValueBones;
import mchorse.bbs_mod.forms.forms.utils.ValueMaterials;
import mchorse.bbs_mod.forms.values.ValueActionsConfig;
import mchorse.bbs_mod.forms.values.ValueShapeKeys;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueLinks;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.core.ValueWindControl;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Pose;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.cubic.ik.IKControls;
import mchorse.bbs_mod.cubic.jem.CemStatus;
import mchorse.bbs_mod.cubic.physics.PhysicsControl;
import mchorse.bbs_mod.cubic.physics.PhysicsControls;
import mchorse.bbs_mod.cubic.shake.ShakeControl;
import mchorse.bbs_mod.cubic.shake.ShakeControls;
import mchorse.bbs_mod.cubic.spline.SplineControl;
import mchorse.bbs_mod.cubic.spline.SplineControls;
import mchorse.bbs_mod.settings.values.core.ValueChainControls;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueStableList;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public class ModelForm extends Form implements IPosedForm
{
    /** Also what its main tab in the form editor wears — see {@link Form#getIcon()}. */
    public static final Icon ICON = Icons.POSE;

    public final ValueLink texture = new ValueLink("texture", null);
    public final ValueLinks materialTextures = new ValueLinks("material_textures");
    public final ValueString model = new ValueString("model", "");
    public final ValuePose pose = new ValuePose("pose", new Pose());
    public final ValuePose poseOverlay = new ValuePose("pose_overlay", new Pose());
    public final ValueActionsConfig actions = new ValueActionsConfig("actions", new ActionsConfig());
    public final ValueColor color = new ValueColor("color", Color.white());
    public final ValueMaterials materials = new ValueMaterials("materials");
    public final ValueShapeKeys shapeKeys = new ValueShapeKeys("shape_keys", new ShapeKeys());
    public final ValueBoolean boneTracks = new ValueBoolean("bone_tracks", true);
    public final ValueBones bones = new ValueBones("bones");
    public final ValueChainControls<IKControl, IKControls> ik =
        new ValueChainControls<>("ik", KeyframeFactories.IK);
    public final ValueChainControls<PhysicsControl, PhysicsControls> physics =
        new ValueChainControls<>("physics", KeyframeFactories.PHYSICS);
    public final ValueSplineIKs splines = new ValueSplineIKs("splines");
    public final ValueChainControls<SplineControl, SplineControls> splineIK =
        new ValueChainControls<>("spline_ik", KeyframeFactories.SPLINE);

    /** The global wind of the form's physics — one compound animatable property, not bound to a bone. */
    public final ValueWindControl wind = new ValueWindControl("wind", new WindControl());

    /** How strongly each shaking bone shakes; what makes it shake is configured on the bone. */
    public final ValueChainControls<ShakeControl, ShakeControls> shake =
        new ValueChainControls<>("shake", KeyframeFactories.SHAKE);

    /**
     * The entity states an OptiFine CEM model asks about and a form cannot know — see
     * {@link CemStatus}.
     * Every one of them lies over what the entity says, so their defaults change nothing, and they only
     * appear in the editor for a model that carries a CEM program.
     */
    public final ValueBoolean cemSitting = new ValueBoolean("cem_sitting", false);
    public final ValueBoolean cemTamed = new ValueBoolean("cem_tamed", false);
    public final ValueBoolean cemAggressive = new ValueBoolean("cem_aggressive", false);
    public final ValueBoolean cemOnShoulder = new ValueBoolean("cem_on_shoulder", false);
    public final ValueBoolean cemBurning = new ValueBoolean("cem_burning", false);
    public final ValueBoolean cemInLava = new ValueBoolean("cem_in_lava", false);
    public final ValueBoolean cemClimbing = new ValueBoolean("cem_climbing", false);
    public final ValueBoolean cemCrawling = new ValueBoolean("cem_crawling", false);
    public final ValueFloat cemHealth = new ValueFloat("cem_health", 1F, 0F, 1F).slider();

    /**
     * Runtime per-material texture overrides driven by the per-material animation tracks
     * (keyed by material name). Set each frame by {@code FormProperties} during playback and
     * read first by the renderer's texture resolver; empty means "no track override, use the
     * material's default / the form's default texture".
     */
    public final transient Map<String, Link> materialTextureOverrides = new HashMap<>();

    /**
     * Runtime per-material appearance overrides driven by the material animation tracks
     * (keyed by material name), same lifecycle as {@link #materialTextureOverrides}: set
     * each frame by {@code FormProperties} during playback, read by the renderer over the
     * static {@link #materials} values.
     */
    public final transient Map<String, Color> materialColorOverrides = new HashMap<>();
    public final transient Map<String, Color> materialOverlayOverrides = new HashMap<>();
    public final transient Map<String, Float> materialLightingOverrides = new HashMap<>();
    public final transient Map<String, Integer> materialCullingOverrides = new HashMap<>();
    public final transient Map<String, Boolean> materialVisibilityOverrides = new HashMap<>();

    /** PBR slider overrides (keyed by material name, then by the slider's property name). */
    public final transient Map<String, Map<String, Float>> materialPbrOverrides = new HashMap<>();

    public final transient Map<String, Vector3f> ikTargetOverrides = new HashMap<>();
    public final transient Map<String, Vector3f> poleTargetOverrides = new HashMap<>();
    public final transient Map<String, Float> ikTargetWeights = new HashMap<>();
    public final transient Map<String, Float> poleTargetWeights = new HashMap<>();
    public final transient Map<String, Vector3f> physicsTargetOverrides = new HashMap<>();
    public final transient Map<String, Float> physicsTargetWeights = new HashMap<>();

    public ModelForm()
    {
        super();

        this.add(this.texture);
        this.materialTextures.invisible();
        this.add(this.materialTextures);
        this.add(this.model);
        this.add(this.pose);
        this.add(this.poseOverlay);

        this.syncOverlayTracks();

        this.add(this.actions);
        this.add(this.color);
        this.materials.invisible();
        this.add(this.materials);
        this.add(this.shapeKeys);
        this.boneTracks.animatable(false).invisible();
        this.add(this.boneTracks);

        this.bones.invisible();
        this.add(this.bones);
        this.bones.bind(this);
        this.add(this.ik);
        this.add(this.physics);
        this.add(this.splines);
        this.add(this.splineIK);
        this.wind.invisible();
        this.wind.animatable(true);
        this.add(this.wind);
        this.add(this.shake);

        /* Visible, so each is a track of its own: a cat that sits down mid-take is a keyframe like
         * any other. */
        this.add(this.cemSitting);
        this.add(this.cemTamed);
        this.add(this.cemAggressive);
        this.add(this.cemOnShoulder);
        this.add(this.cemBurning);
        this.add(this.cemInLava);
        this.add(this.cemClimbing);
        this.add(this.cemCrawling);
        this.add(this.cemHealth);
    }

    @Override
    public ValuePose getPose()
    {
        return this.pose;
    }

    @Override
    public ValuePose getPoseOverlay()
    {
        return this.poseOverlay;
    }

    @Override
    public boolean hasBoneTracks()
    {
        return this.boneTracks.get();
    }

    @Override
    public void fromData(BaseType data)
    {
        this.ik.fromData(new MapType());
        this.physics.fromData(new MapType());
        this.splineIK.fromData(new MapType());
        this.shake.fromData(new MapType());
        super.fromData(data);

        /* Forms saved before the bones group kept the constraints and the IK setup as opaque
         * blobs in the exchange formats; unpack them into the per-bone properties. */
        if (data instanceof MapType map)
        {
            if (map.has("constraints", BaseType.TYPE_MAP))
            {
                BoneConstraintsIO.read(map.getMap("constraints"), this.bones, false);
            }

            if (map.has("ik", BaseType.TYPE_MAP) && !map.getMap("ik").has("ik"))
            {
                BoneIKIO.read(map.getMap("ik"), this.bones, false);
            }

            if (map.has("physics", BaseType.TYPE_MAP) && !map.getMap("physics").has("physics"))
            {
                BonePhysicsIO.read(map.getMap("physics"), this.bones, this.wind, false);
            }

            /* Canonical state wins over legacy per-bone payloads regardless of map order. */
            if (map.getMap("ik").has("ik")) this.ik.fromData(map.getMap("ik"));
            if (map.getMap("physics").has("physics")) this.physics.fromData(map.getMap("physics"));
            if (map.has("spline_ik")) this.splineIK.fromData(map.getMap("spline_ik"));
        }
    }

    @Override
    protected BaseType serializeChild(BaseValue value)
    {
        return value == this.bones || value == this.splines ? rigData(value) : super.serializeChild(value);
    }

    /** Form saves contain rig structure; standalone snapshots still include editable field values. */
    private static BaseType rigData(BaseValue value)
    {
        if (value instanceof BaseValueBasic<?> field && field.isBound()) return null;
        if (value instanceof ValueGroup group) return group.toData(ModelForm::rigData);
        if (value instanceof ValueStableList<?> list) return list.toData(ModelForm::rigData);
        return value.toData();
    }

    @Override
    public String getDefaultDisplayName()
    {
        return this.model.get();
    }

    @Override
    public Icon getIcon()
    {
        return ICON;
    }

}
