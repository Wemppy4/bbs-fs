package mchorse.bbs_mod.film.replays.tracks;

import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.HashMap;
import java.util.Map;

/**
 * What each kind of track looks like in a timeline: its colour and its icon.
 *
 * <p>Both are keyed by the last segment of a track's address — the property's own name — so the same
 * kind of thing wears the same colour and icon wherever it appears, on any form and in any film.
 * These tables used to sit inside the replay editor panel, which made every other timeline ask a
 * panel what a track should look like.</p>
 */
public class TrackStyle
{
    private static final Map<String, Integer> COLORS = new HashMap<>();
    private static final Map<String, Icon> ICONS = new HashMap<>();

    /**
     * The model track swaps out the whole thing being animated, so it doesn't belong to any of the
     * families below - it gets a violet of its own, clear of the axes, the items and the armour.
     */
    private static final int MODEL_TRACK = 0x9d6cff;

    /* Item channel families - see setupItemColors() */
    private static final int HOTBAR_FIRST = Colors.ORANGE;
    private static final int HOTBAR_LAST = 0xe0245e;
    private static final int OFF_HAND = 0xff3a74;
    private static final int ARMOR_HEAD = 0x8fd0ff;
    private static final int ARMOR_CHEST = 0x6fb4f0;
    private static final int ARMOR_LEGS = 0x5698db;
    private static final int ARMOR_FEET = 0x407cc0;

    static
    {
        setupColors();
        setupIcons();
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
        COLORS.put("model", MODEL_TRACK);
    }

    /**
     * Fourteen item rows sat in one shade of orange, which read as one long stripe. They are
     * two things, so they get two families: the hands warm, the armour cool as metal.
     *
     * The hotbar drifts from orange to raspberry down its nine rows, and the off hand - which
     * comes right after them - carries on where they end, a shade brighter. So the warm run
     * reads as one thing with an order, while a glance still tells row from row. The armour
     * cools downwards the same way, lightest at the helmet.
     */
    private static void setupItemColors()
    {
        int last = ReplayKeyframes.HOTBAR_SIZE - 1;

        for (int i = 0; i <= last; i++)
        {
            COLORS.put(ReplayKeyframes.hotbarChannelId(i), Colors.lerp(HOTBAR_FIRST, HOTBAR_LAST, i / (float) last) & Colors.RGB);
        }

        /* Sits right below the hotbar in the list, so it picks the run up where it ends */
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
     * Every track carries an icon: an empty slot in the icon column reads as "this row is a lesser
     * kind of thing" when it only ever meant "nobody got around to it". Rows that belong together
     * wear the same icon on purpose - the nine hotbar slots, the six particle user values, the label's
     * shadow - so the column groups the timeline at a glance instead of naming each row twice.
     */
    private static void setupIcons()
    {
        /* Axes. Anything that is one component of a vector wears its axis' letter. */
        putIcons(Icons.X, "x", "vX", "offsetX", "offset_x", "anchorX");
        putIcons(Icons.Y, "y", "vY", "offsetY", "offset_y", "anchorY");
        putIcons(Icons.Z, "z", "vZ", "offset_z");

        /* Rotations, by the plane they turn in */
        /* Two pairs, each pair alike: the actor's own yaw and pitch, then the head's and the body's. */
        putIcons(Icons.VERTICAL, "yaw", "pitch", "scattering_pitch");
        putIcons(Icons.HORIZONTAL, "headYaw", "bodyYaw", "scattering_yaw", "max");
        ICONS.put("rotation", Icons.ORBIT);

        /* Movement and state of the actor */
        ICONS.put("sneaking", Icons.ARROW_DOWN);
        ICONS.put("grounded", Icons.SLAB);
        ICONS.put("damage", Icons.SKULL);
        putIcons(Icons.ARROW_RIGHT, "sprinting", "velocity");

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
        /* The whole hotbar wears one icon: nine rows of the same thing, which is what they are.
         * The row's number is in its name, and its shade already walks down the run. */
        for (int i = 0; i < ReplayKeyframes.HOTBAR_SIZE; i++)
        {
            ICONS.put(ReplayKeyframes.hotbarChannelId(i), Icons.HOTBAR);
        }

        ICONS.put("item_off_hand", Icons.LIMB);
        ICONS.put("item_head", Icons.ARMOR_HELMET);
        ICONS.put("item_chest", Icons.ARMOR_CHESTPLATE);
        ICONS.put("item_legs", Icons.ARMOR_LEGGINGS);
        ICONS.put("item_feet", Icons.ARMOR_BOOTS);

        /* Not an item but the pointer at one */
        ICONS.put("selected_slot", Icons.POINTER);
    }

    public static Icon icon(String key)
    {
        String topLevel = StringUtils.fileName(key);

        if (topLevel.startsWith("pose_overlay")) return ICONS.get("pose");
        if (topLevel.startsWith("transform_overlay")) return ICONS.get("transform");

        return ICONS.getOrDefault(topLevel, Icons.NONE);
    }

    public static int color(String key)
    {
        String topLevel = StringUtils.fileName(key);

        if (topLevel.startsWith("pose_overlay")) return COLORS.get("pose_overlay");
        if (topLevel.startsWith("transform_overlay")) return COLORS.get("transform_overlay");
        if (COLORS.containsKey(topLevel)) return COLORS.get(topLevel);

        return Colors.BLUE;
    }

}
