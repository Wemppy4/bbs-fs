package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.values.ui.ValueOrder;
import mchorse.bbs_mod.utils.Axis;

import java.util.ArrayList;
import java.util.List;

/**
 * A step of a transform hotkey's walk: pressing the same hotkey again moves to the next one.
 * Tokens match the entries of the translate/scale/rotate hotkey order settings, which is
 * where the walk itself is configured.
 */
public enum HotkeyTarget
{
    VIEW("view", null, true),
    SPHERE("sphere", null, true),
    SCREEN("screen", null, true),
    /** Scale's non-axis step: one lever drives all three axes (Blender's plain S). */
    ALL("all", null, false),
    X("x", Axis.X, false),
    Y("y", Axis.Y, false),
    Z("z", Axis.Z, false);

    public final String token;
    public final Axis axis;
    /** Whether the step is driven by the 3D ray and so needs a rendered gizmo. */
    public final boolean needsRay;

    HotkeyTarget(String token, Axis axis, boolean needsRay)
    {
        this.token = token;
        this.axis = axis;
        this.needsRay = needsRay;
    }

    public static HotkeyTarget byToken(String token)
    {
        for (HotkeyTarget target : values())
        {
            if (target.token.equals(token))
            {
                return target;
            }
        }

        return null;
    }

    /**
     * The walk configured for that operation, with the steps this situation cannot offer
     * dropped: the ray-driven ones without a gizmo to aim at.
     *
     * <p>An element hidden from the gizmo ({@link mchorse.bbs_mod.ui.utils.Gizmo.Element})
     * keeps its step — visibility is about the screen and the cursor, and dropping the step
     * too would let a stripped-bare gizmo take a whole operation away from the keyboard as
     * well. Trimming the walk is what the order settings are for.
     */
    public static List<HotkeyTarget> steps(TransformOp op, boolean ray)
    {
        ValueOrder order = op == TransformOp.TRANSLATE
            ? BBSSettings.translateHotkeyOrder
            : (op == TransformOp.SCALE ? BBSSettings.scaleHotkeyOrder : BBSSettings.rotateHotkeyOrder);

        List<HotkeyTarget> steps = new ArrayList<>();

        for (String token : order.get())
        {
            HotkeyTarget target = byToken(token);

            if (target == null || (target.needsRay && !ray))
            {
                continue;
            }

            steps.add(target);
        }

        return steps;
    }

    /**
     * The step after {@code current} in that operation's walk, wrapping around. A
     * {@code current} of {@code null} — nothing running yet — lands on the first step, and a
     * walk configured down to nothing falls back to {@link #X}.
     */
    public static HotkeyTarget next(TransformOp op, boolean ray, HotkeyTarget current)
    {
        List<HotkeyTarget> steps = steps(op, ray);

        if (steps.isEmpty())
        {
            return X;
        }

        return steps.get((steps.indexOf(current) + 1) % steps.size());
    }
}
