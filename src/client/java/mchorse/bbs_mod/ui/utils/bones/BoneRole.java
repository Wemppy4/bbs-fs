package mchorse.bbs_mod.ui.utils.bones;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * One mark a bone list can put next to a bone name, and the one place every list takes its
 * marks from — so a colour means the same thing on every tab: yellow is a chain, cyan a target,
 * magenta a pole, whichever solver's list it is.
 *
 * <p>The colour says what the bone is to the rig; the shape says how it takes part:</p>
 *
 * <ul>
 *     <li>{@link Shape#FULL} — the setting lives on this bone (a chain's tip, a physics root);</li>
 *     <li>{@link Shape#SMALL} — something else drives this bone (a link of a chain);</li>
 *     <li>{@link Shape#HOLLOW} — this bone drives something else (a target, a pole).</li>
 * </ul>
 *
 * <p>A switched-off setting keeps its shape and turns gray: it is still there, it just does
 * nothing right now.</p>
 */
public final class BoneRole
{
    public static final BoneRole CHAIN = new BoneRole(Colors.A100 | Colors.YELLOW, Shape.FULL, UIKeys.BONES_ROLE_CHAIN);
    public static final BoneRole LINK = new BoneRole(Colors.A100 | Colors.YELLOW, Shape.SMALL, UIKeys.BONES_ROLE_LINK);
    public static final BoneRole TARGET = new BoneRole(Colors.A100 | Colors.CYAN, Shape.HOLLOW, UIKeys.BONES_ROLE_TARGET);
    public static final BoneRole POLE = new BoneRole(Colors.A100 | Colors.MAGENTA, Shape.HOLLOW, UIKeys.BONES_ROLE_POLE);
    public static final BoneRole LIMIT = new BoneRole(Colors.A100 | Colors.ORANGE, Shape.FULL, UIKeys.BONES_ROLE_LIMIT);
    public static final BoneRole MIRROR = new BoneRole(Colors.A100 | 0x5599ff, Shape.FULL, UIKeys.MODEL_EDITOR_BONE_MIRROR);
    public static final BoneRole PICKING = new BoneRole(Colors.A100 | Colors.GREEN, Shape.FULL, UIKeys.MODEL_EDITOR_BONE_PICKING);

    private static final int OFF_COLOR = Colors.GRAY;

    /* Motion path marks, one per colour: the mark wears its path's colour, not a role's. */
    private static final Map<Integer, BoneRole> MOTION_PATHS = new HashMap<>();

    public final int color;
    public final Shape shape;
    public final IKey label;

    private BoneRole off;
    private Map<IKey, BoneRole> named;

    private BoneRole(int color, Shape shape, IKey label)
    {
        this.color = color;
        this.shape = shape;
        this.label = label;
    }

    /** A bone that has a motion path in the film: a full mark in the colour of its line. */
    public static BoneRole motionPath(int color)
    {
        return MOTION_PATHS.computeIfAbsent(color, (c) -> new BoneRole(Colors.A100 | c, Shape.FULL, UIKeys.FILM_CONTROLLER_MOTION_PATH_TITLE));
    }

    /** The same mark for a setting that is switched off: gray, same shape. */
    public BoneRole off()
    {
        if (this.off == null)
        {
            this.off = this.isOff() ? this : new BoneRole(OFF_COLOR, this.shape, UIKeys.BONES_ROLE_OFF.format(this.label));
            this.off.off = this.off;
        }

        return this.off;
    }

    /** The same mark under another name, for a list that says whose mark it is. */
    public BoneRole named(IKey label)
    {
        if (this.named == null)
        {
            this.named = new IdentityHashMap<>();
        }

        return this.named.computeIfAbsent(label, (key) -> new BoneRole(this.color, this.shape, key));
    }

    public boolean isOff()
    {
        return this.color == OFF_COLOR;
    }

    /**
     * Which of two marks wins a slot: a working setting over a switched-off one, and the bone
     * a setting lives on over a bone it merely drives.
     */
    public int strength()
    {
        return (this.isOff() ? 0 : 2) + (this.shape == Shape.SMALL ? 0 : 1);
    }

    public enum Shape
    {
        FULL, SMALL, HOLLOW
    }
}
