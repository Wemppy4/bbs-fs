package mchorse.bbs_mod.film.replays.tracks;

import java.util.Objects;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;

import mchorse.bbs_mod.cubic.rig.RigStep;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.EntityState;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.HashMap;
import java.util.Map;

/**
 * What each kind of track looks like in a timeline: its colour, its icon and its name.
 *
 * <p>All three are keyed by the last segment of a track's address — the property's own name — so the same
 * kind of thing wears the same colour and icon wherever it appears, on any form and in any film.
 * These tables used to sit inside the replay editor panel, which made every other timeline ask a
 * panel what a track should look like.</p>
 */
public class TrackStyle
{
    private static final Map<String, Integer> COLORS = new HashMap<>();
    private static final Map<String, Icon> ICONS = new HashMap<>();
    private static final Map<String, IKey> LABELS = new HashMap<>();

    /** The overlays a form can stack: the first is named plainly, the ones the settings add get a number. */
    private static final String[] OVERLAYS = {"pose_overlay", "transform_overlay", "shape_keys_overlay"};

    public static void registerLabel(String property, IKey label)
    {
        LABELS.put(property, Objects.requireNonNull(label));
    }

    /** Labels never change serialized addresses. IKey remains live across language reloads. */
    public static IKey label(TrackId id)
    {
        return switch (id.kind())
        {
            case PROPERTY -> label(id.subject());
            case MATERIAL_PROP -> materialLabel(id.material(), label(id.property()));
            case BONE_CONSTRAINT, IK_TARGET, POLE_TARGET, PHYSICS_TARGET -> kindLabel(id.kind()).format(id.subject());
            case IK_CONTROLS, PHYSICS_CONTROLS, WIND_CONTROLS -> kindLabel(id.kind());
            default -> IKey.constant(id.label());
        };
    }

    /** What a property is called in a timeline, by its own name — the last segment of a track's address. */
    public static IKey label(String property)
    {
        IKey label = LABELS.get(property);

        if (label != null)
        {
            return label;
        }

        for (String overlay : OVERLAYS)
        {
            String number = property.startsWith(overlay) ? property.substring(overlay.length()) : "";

            if (number.matches("[0-9]{1,4}"))
            {
                return L10n.lang("bbs.ui.timeline." + overlay + "_number").format(Integer.parseInt(number) + 1);
            }
        }

        return IKey.constant(property);
    }

    /** A material's own properties keep its name in front; the whole-form level names nothing. */
    private static IKey materialLabel(String material, IKey property)
    {
        return material.isEmpty() ? property : () -> material + FormUtils.PATH_SEPARATOR + property.get();
    }

    private static IKey kindLabel(TrackKind kind)
    {
        return L10n.lang("bbs.ui.timeline.kind." + kind.key);
    }

    /**
     * The model track swaps out the whole thing being animated, so it doesn't belong to any of the
     * families below - it gets a violet of its own, clear of the axes, the items and the armour.
     */
    private static final int MODEL_TRACK = 0x9d6cff;

    /* Item channel families - see setupItemColors() */
    private static final int HOTBAR = Colors.ORANGE;
    private static final int OFF_HAND = 0xff3a74;
    private static final int ARMOR_HEAD = 0x8fd0ff;
    private static final int ARMOR_CHEST = 0x6fb4f0;
    private static final int ARMOR_LEGS = 0x5698db;
    private static final int ARMOR_FEET = 0x407cc0;

    /**
     * Fills the tables. Called by BBS while it initialises, and followed by the event that
     * lets addons add to them.
     */
    public static void setup()
    {
        setupColors();
        setupIcons();
        setupLabels();

        /* The rig's tracks look the way the rig describes them (the shake wears the camera shake
         * clip's icon and colour: the same thing, done to bones). */
        for (RigStep step : RigStep.values())
        {
            for (RigStep.Track track : step.tracks)
            {
                register(track.id(), track.icon(), track.color());
                LABELS.put(track.id(), track.label());
            }
        }

        register("curve", Icons.GRAPH, Colors.BLUE);
    }

    /**
     * Gives a property of an addon's its own look on the timeline.
     *
     * <p>Keyed by the property's own name, the last segment of a track's address — the same
     * way BBS keys its own, so the same thing wears the same colour wherever it appears.</p>
     */
    public static void register(String property, Icon icon, int color)
    {
        ICONS.put(property, icon);
        COLORS.put(property, color & Colors.RGB);
    }

    private static void setupColors()
    {
        putColors(Colors.RED, "x", "vX", "stick_lx", "stick_rx", "extra1_x", "extra2_x", "user1", "user5", "frequency", "offset_x");
        putColors(Colors.GREEN, "y", "vY", "stick_ly", "stick_ry", "trigger_l", "trigger_r", "extra1_y", "extra2_y", "user3", "count", "offset_y", "transform");
        putColors(Colors.BLUE, "z", "vZ", "user4", "offset_z");
        putColors(Colors.YELLOW, "yaw", "lighting");
        putColors(Colors.CYAN, "pitch");
        putColors(Colors.MAGENTA, "bodyYaw", "actions", "settings");
        putColors(Colors.ORANGE, "pose_overlay", "user2", "user6");

        setupItemColors();

        COLORS.put("visible", Colors.WHITE & Colors.RGB);
        COLORS.put("pose", Colors.RED);
        COLORS.put("physics_targets", Colors.MAGENTA);
        COLORS.put("transform_overlay", 0xaaff00);
        COLORS.put("color", Colors.INACTIVE);
        COLORS.put("color_overlay", 0xc46aff);
        COLORS.put("culling", 0x8899bb);
        putColors(0xd9b23f, "smoothness", "metallic", "sss", "pixel_emission", "relief");
        COLORS.put("shape_keys", Colors.PINK);
        COLORS.put("shape_keys_overlay", 0xff9ad5);
        COLORS.put("model", MODEL_TRACK);
    }

    /**
     * Fourteen item rows sat in one shade of orange, which read as one long stripe. They are
     * two things, so they get two families: the hands warm, the armour cool as metal.
     *
     * The hotbar is orange and the off hand right below it raspberry, so the warm pair reads as
     * one thing while a glance still tells row from row. The armour cools downwards, lightest
     * at the helmet.
     */
    private static void setupItemColors()
    {
        COLORS.put("hotbar", HOTBAR & Colors.RGB);
        COLORS.put("item_off_hand", OFF_HAND);

        COLORS.put("item_head", ARMOR_HEAD);
        COLORS.put("item_chest", ARMOR_CHEST);
        COLORS.put("item_legs", ARMOR_LEGS);
        COLORS.put("item_feet", ARMOR_FEET);

        /* Not an item but the pointer at one, so it stays out of both families. Without this it
         * falls through to the default blue, which is now the armour's tone. */
        COLORS.put("selected_slot", Colors.WHITE & Colors.RGB);
    }

    private static void putColors(int color, String... keys)
    {
        for (String key : keys) COLORS.put(key, color);
    }

    private static void putIcons(Icon icon, String... keys)
    {
        for (String key : keys) ICONS.put(key, icon);
    }

    /**
     * The face of each state track. Exhaustive on purpose: a state added to the table has to be
     * given an icon here too, and the compiler is what says so rather than a blank icon column
     * noticed later in a timeline.
     */
    private static Icon stateIcon(EntityState state)
    {
        return switch (state)
        {
            case SNEAKING -> Icons.ARROW_DOWN;
            case SPRINTING -> Icons.ARROW_RIGHT;
            case GROUNDED -> Icons.SLAB;
            case SWIMMING -> Icons.DROP;
            case RIDING -> Icons.CHICKEN;
            case FLYING -> Icons.HELICOPTER;
            case GLIDING -> Icons.PLANE;
        };
    }

    /**
     * Every track carries an icon: an empty slot in the icon column reads as "this row is a lesser
     * kind of thing" when it only ever meant "nobody got around to it". Rows that belong together
     * wear the same icon on purpose - the six particle user values, the label's
     * shadow - so the column groups the timeline at a glance instead of naming each row twice.
     */
    private static void setupIcons()
    {
        /* Axes. Anything that is one component of a vector wears its axis' letter. */
        putIcons(Icons.X, "x", "vX", "offsetX", "offset_x", "anchorX");
        putIcons(Icons.Y, "y", "vY", "offsetY", "offset_y", "anchorY");
        putIcons(Icons.Z, "z", "vZ", "offset_z");

        /* Rotations, by the plane they turn in: every yaw turns sideways, every pitch up and down. */
        putIcons(Icons.VERTICAL, "pitch", "scattering_pitch");
        putIcons(Icons.HORIZONTAL, "yaw", "headYaw", "bodyYaw", "scattering_yaw", "max");
        ICONS.put("rotation", Icons.ORBIT);

        /* Movement and state of the actor */
        for (EntityState state : EntityState.values())
        {
            ICONS.put(state.id, stateIcon(state));
        }

        ICONS.put("damage", Icons.SKULL);
        ICONS.put("velocity", Icons.ARROW_RIGHT);
        ICONS.put("leaning", Icons.ARC);
        ICONS.put("roll", Icons.ORBIT);

        /* The camera: its view cone, and how far it backs off from the point it looks at */
        ICONS.put("fov", Icons.FRUSTUM);
        ICONS.put("distance", Icons.MOVE_TO);

        /* The form itself */
        ICONS.put("visible", Icons.VISIBLE);
        ICONS.put("texture", Icons.MATERIAL);
        ICONS.put("model", Icons.POSE);
        ICONS.put("color", Icons.BUCKET);
        ICONS.put("color_overlay", Icons.COLOR);
        ICONS.put("lighting", Icons.LIGHT);
        ICONS.put("culling", Icons.CONVERT);
        putIcons(Icons.MATERIAL, "smoothness", "metallic", "sss", "pixel_emission", "relief");
        ICONS.put("actions", Icons.CONVERT);
        ICONS.put("shape_keys", Icons.HEART_ALT);
        ICONS.put("anchor", Icons.LINK);
        ICONS.put("billboard", Icons.CAMERA);
        ICONS.put("shading", Icons.SUN);
        ICONS.put("crop", Icons.FULLSCREEN);
        ICONS.put("block_state", Icons.BLOCK);
        ICONS.put("item_stack", Icons.SHARD);
        ICONS.put("modelTransform", Icons.SPACE_LOCAL);
        ICONS.put("scale", Icons.SCALE);
        ICONS.put("mobId", Icons.CHICKEN);
        ICONS.put("mobNbt", Icons.CODE);
        ICONS.put("length", Icons.LINE);
        ICONS.put("loop", Icons.REFRESH);

        /* Pose and transform, and their overlays - see getIcon(), which folds the numbered
         * overlays onto the thing they overlay: an overlay is that thing, layered. */
        ICONS.put("pose", Icons.POSE);
        ICONS.put("transform", Icons.ALL_DIRECTIONS);

        /* The label */
        ICONS.put("text", Icons.FONT);
        ICONS.put("anchorLines", Icons.LIST);
        putIcons(Icons.FADING, "shadowX", "shadowY");
        ICONS.put("shadowColor", Icons.COLOR);
        ICONS.put("background", Icons.SQUARE);
        ICONS.put("offset", Icons.OUTLINE);

        /* The controller. Both axes of a stick, and both triggers, share their side's icon. */
        putIcons(Icons.LEFT_STICK, "stick_lx", "stick_ly");
        putIcons(Icons.RIGHT_STICK, "stick_rx", "stick_ry");
        putIcons(Icons.TRIGGER, "trigger_l", "trigger_r");
        putIcons(Icons.CURVES, "extra1_x", "extra1_y", "extra2_x", "extra2_y");

        setupItemIcons();

        /* Particles */
        putIcons(Icons.PARTICLE, "user1", "user2", "user3", "user4", "user5", "user6");
        ICONS.put("paused", Icons.TIME);
        ICONS.put("frequency", Icons.STOPWATCH);
        ICONS.put("count", Icons.BUCKET);
        ICONS.put("settings", Icons.GEAR);
        ICONS.put("physics_targets", Icons.PHYSICS);
    }

    private static void setupItemIcons()
    {
        ICONS.put("hotbar", Icons.HOTBAR);
        ICONS.put("item_off_hand", Icons.LIMB);
        ICONS.put("item_head", Icons.ARMOR_HELMET);
        ICONS.put("item_chest", Icons.ARMOR_CHESTPLATE);
        ICONS.put("item_legs", Icons.ARMOR_LEGGINGS);
        ICONS.put("item_feet", Icons.ARMOR_BOOTS);

        /* Not an item but the pointer at one */
        ICONS.put("selected_slot", Icons.POINTER);
    }

    /**
     * The built-in names, keyed like the colours and icons so a property is called the same on every
     * form. An addon's {@link #registerLabel(String, IKey)} comes after and may rename any of them.
     */
    private static void setupLabels()
    {
        putLabels("bbs.ui.timeline.properties.",
            /* The recording */
            "x", "y", "z", "vX", "vY", "vZ", "yaw", "pitch", "headYaw", "bodyYaw", "anchor",
            "sneaking", "sprinting", "grounded", "swimming", "riding", "flying", "gliding", "damage",
            "hotbar", "selected_slot", "item_off_hand", "item_head", "item_chest", "item_legs", "item_feet",
            "stick_lx", "stick_ly", "stick_rx", "stick_ry", "trigger_l", "trigger_r",
            "extra1_x", "extra1_y", "extra2_x", "extra2_y",
            /* Forms */
            "texture", "model", "actions", "billboard", "shading", "crop", "block_state", "item_stack",
            "modelTransform", "scale", "mobId", "mobNbt", "slim", "length", "loop", "paused",
            "offsetX", "offsetY", "rotation", "video", "videoOffset", "structure", "biome", "effect",
            "useTarget", "origin", "local", "linear", "mipmap", "additive_color", "width", "height",
            "shaderShadow", "render_last", "render_layer",
            "hitbox", "hitboxEyeHeight", "hitboxHeight", "hitboxWidth", "hitboxSneakMultiplier", "hp",
            "movement_speed", "step_height", "inheritPosition", "inheritRotation", "inheritScale",
            /* The label */
            "text", "font", "fontSize", "lineHeight", "anchorX", "anchorY", "anchorLines",
            "shadowX", "shadowY", "shadowColor", "background", "offset",
            /* Particles */
            "settings", "frequency", "count", "max", "velocity", "scattering_pitch", "scattering_yaw",
            "offset_x", "offset_y", "offset_z", "leaning", "roll",
            "user1", "user2", "user3", "user4", "user5", "user6",
            /* The rig's CEM states; the steps' own tracks are named by the rig itself, see setup() */
            "cem_aggressive", "cem_burning", "cem_climbing", "cem_crawling", "cem_health",
            "cem_in_lava", "cem_on_shoulder", "cem_sitting", "cem_tamed",
            /* Splines */
            "curve", "repeatEnabled", "repeatMode", "repeatCount", "repeatDistance", "repeatRotation",
            "repeatStart", "repeatEnd", "repeatOffset");

        /* The same names the form's own appearance tracks go by. */
        putLabels("bbs.ui.timeline.material.", TrackId.MATERIAL_PROPS_ALL);

        LABELS.put("pose", L10n.lang("bbs.ui.timeline.whole_pose"));
        LABELS.put("transform", L10n.lang("bbs.ui.timeline.base_transform"));
        LABELS.put("shape_keys", L10n.lang("bbs.ui.timeline.shape_keys"));

        for (String overlay : OVERLAYS)
        {
            LABELS.put(overlay, L10n.lang("bbs.ui.timeline." + overlay));
        }
    }

    private static void putLabels(String prefix, String... keys)
    {
        for (String key : keys) LABELS.put(key, L10n.lang(prefix + key));
    }

    public static Icon icon(String key)
    {
        String topLevel = StringUtils.fileName(key);

        if (topLevel.startsWith("pose_overlay")) return ICONS.get("pose");
        if (topLevel.startsWith("transform_overlay")) return ICONS.get("transform");
        if (topLevel.startsWith("shape_keys_overlay")) return ICONS.get("shape_keys");

        return ICONS.getOrDefault(topLevel, Icons.NONE);
    }

    public static int color(String key)
    {
        String topLevel = StringUtils.fileName(key);

        if (topLevel.startsWith("pose_overlay")) return COLORS.get("pose_overlay");
        if (topLevel.startsWith("transform_overlay")) return COLORS.get("transform_overlay");
        if (topLevel.startsWith("shape_keys_overlay")) return COLORS.get("shape_keys_overlay");
        if (COLORS.containsKey(topLevel)) return COLORS.get(topLevel);

        return Colors.BLUE;
    }

}
