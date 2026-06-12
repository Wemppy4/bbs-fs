package mchorse.bbs_mod.utils.keyframes.factories;

import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public class KeyframeFactories
{
    public static final Map<String, IKeyframeFactory> FACTORIES = new HashMap<>();

    public static final ColorKeyframeFactory COLOR = new ColorKeyframeFactory();
    public static final TransformKeyframeFactory TRANSFORM = new TransformKeyframeFactory();
    public static final PoseTransformKeyframeFactory POSE_TRANSFORM = new PoseTransformKeyframeFactory();
    public static final PoseKeyframeFactory POSE = new PoseKeyframeFactory();
    public static final IKKeyframeFactory IK = new IKKeyframeFactory();
    public static final BooleanKeyframeFactory BOOLEAN = new BooleanKeyframeFactory();
    public static final StringKeyframeFactory STRING = new StringKeyframeFactory();
    public static final FloatKeyframeFactory FLOAT = new FloatKeyframeFactory();
    public static final DoubleKeyframeFactory DOUBLE = new DoubleKeyframeFactory();
    public static final IntegerKeyframeFactory INTEGER = new IntegerKeyframeFactory();
    public static final LongKeyframeFactory LONG = new LongKeyframeFactory();
    public static final LinkKeyframeFactory LINK = new LinkKeyframeFactory();
    public static final Vector3fKeyframeFactory VECTOR3F = new Vector3fKeyframeFactory(new Vector3f());
    public static final Vector3fKeyframeFactory VECTOR3F_SCALE = new Vector3fKeyframeFactory(new Vector3f(1F, 1F, 1F));
    public static final Vector4fKeyframeFactory VECTOR4F = new Vector4fKeyframeFactory();
    public static final AnchorKeyframeFactory ANCHOR = new AnchorKeyframeFactory();
    public static final BlockStateKeyframeFactory BLOCK_STATE = new BlockStateKeyframeFactory();
    public static final ItemStackKeyframeFactory ITEM_STACK = new ItemStackKeyframeFactory();
    public static final ActionsConfigKeyframeFactory ACTIONS_CONFIG = new ActionsConfigKeyframeFactory();
    public static final ShapeKeysKeyframeFactory SHAPE_KEYS = new ShapeKeysKeyframeFactory();
    public static final ParticleSettingsKeyframeFactory PARTICLE_SETTINGS = new ParticleSettingsKeyframeFactory();

    public static boolean isNumeric(IKeyframeFactory factory)
    {
        return factory instanceof DoubleKeyframeFactory
            || factory instanceof FloatKeyframeFactory
            || factory instanceof IntegerKeyframeFactory
            || factory instanceof LongKeyframeFactory
            || factory instanceof Vector3fKeyframeFactory;
    }

    static
    {
        FACTORIES.put("color", COLOR);
        FACTORIES.put("transform", TRANSFORM);
        FACTORIES.put("pose_transform", POSE_TRANSFORM);
        FACTORIES.put("pose", POSE);
        FACTORIES.put("ik", IK);
        FACTORIES.put("boolean", BOOLEAN);
        FACTORIES.put("string", STRING);
        FACTORIES.put("float", FLOAT);
        FACTORIES.put("double", DOUBLE);
        FACTORIES.put("integer", INTEGER);
        FACTORIES.put("long", LONG);
        FACTORIES.put("link", LINK);
        FACTORIES.put("vector3f", VECTOR3F);
        FACTORIES.put("vector3f_scale", VECTOR3F_SCALE);
        FACTORIES.put("vector4f", VECTOR4F);
        FACTORIES.put("anchor", ANCHOR);
        FACTORIES.put("block_state", BLOCK_STATE);
        FACTORIES.put("item_stack", ITEM_STACK);
        FACTORIES.put("actions_config", ACTIONS_CONFIG);
        FACTORIES.put("shape_keys", SHAPE_KEYS);
        FACTORIES.put("particle_settings", PARTICLE_SETTINGS);
    }
}
