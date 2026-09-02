package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * The on-screen readout of a running {@link TransformGesture}, drawn over its editor's
 * field rows: the chip row naming the operation, the grabbed target and the editing space,
 * the live value card riding the cursor, and the echo of typed numeric input. Stateless —
 * everything comes from the gesture per call.
 */
public class TransformGestureHud
{
    /** Draw the whole readout for a running gesture; a no-op while nothing is edited. */
    public static void render(UIContext context, TransformGesture gesture, Area area)
    {
        if (!gesture.isEditing())
        {
            return;
        }

        FontRenderer font = context.batcher.getFont();
        TransformOp editOp = gesture.getOp();
        String op = (editOp == TransformOp.TRANSLATE ? UIKeys.TRANSFORMS_TRANSLATE : editOp == TransformOp.SCALE ? UIKeys.TRANSFORMS_SCALE : UIKeys.TRANSFORMS_ROTATE).get();
        String target = targetLabel(gesture);
        String space = spaceLabel(gesture);

        /* Chip row: the operation on the primary color, then what is
         * grabbed (axis letters in their gizmo colors), then the editing
         * space. The 5s account for textCard's box overhang at the
         * default card offset. */
        int gap = 2;
        int rowWidth = font.getWidth(op) + 5 + gap + font.getWidth(target) + 5;

        if (space != null)
        {
            rowWidth += gap + font.getWidth(space) + 5;
        }

        int x = area.mx(rowWidth) + 3;
        int y = area.my(font.getHeight());

        context.batcher.textCard(op, x, y, Colors.WHITE, BBSSettings.primaryColor(Colors.A50));
        x += font.getWidth(op) + 5 + gap;
        context.batcher.textCard(target, x, y, targetColor(gesture), Colors.A50);

        if (space != null)
        {
            x += font.getWidth(target) + 5 + gap;
            context.batcher.textCard(space, x, y, Colors.LIGHTEST_GRAY, Colors.A50);
        }

        /* Label echoed both at the cursor and (when typing) under the info row. */
        String numericLabel = null;
        Axis axis = gesture.getAxis();
        Axis axis2 = gesture.getAxis2();

        if (axis != null)
        {
            Vector3f v = liveValue(gesture);
            float val = axis == Axis.X ? v.x : (axis == Axis.Y ? v.y : v.z);

            if (editOp == TransformOp.ROTATE)
            {
                val = MathUtils.toDeg(val);
            }

            String valueLabel = String.format(Locale.US, "%.2f", val);

            if (axis2 != null)
            {
                float val2 = axis2 == Axis.X ? v.x : (axis2 == Axis.Y ? v.y : v.z);

                if (editOp == TransformOp.ROTATE)
                {
                    val2 = MathUtils.toDeg(val2);
                }

                valueLabel += ", " + String.format(Locale.US, "%.2f", val2);
            }

            /* While typing, lead with the raw input so the user sees exactly
             * what they've entered, with the resulting value in parentheses. */
            String cursorLabel = gesture.isNumericActive()
                ? gesture.numericDisplay() + " (" + valueLabel + ")"
                : valueLabel;

            if (gesture.isNumericActive())
            {
                numericLabel = cursorLabel;
            }

            context.batcher.textCard(cursorLabel, context.mouseX + 12, context.mouseY + 12, Colors.WHITE, Colors.A50);
        }
        else if (gesture.isNumericActive())
        {
            /* The view ring and the sphere have no single axis component to
             * echo, so show the typed angle, plus the aimed direction. */
            DragStrategy strategy = gesture.getStrategy();
            String prefix = strategy == null ? "" : strategy.numericPrefix();

            numericLabel = prefix + gesture.numericDisplay() + "°";

            context.batcher.textCard(numericLabel, context.mouseX + 12, context.mouseY + 12, Colors.WHITE, Colors.A50);
        }

        /* Mirror the live numeric input on its own card right under the info row. */
        if (numericLabel != null)
        {
            int nx = area.mx(font.getWidth(numericLabel));
            int ny = y + font.getHeight() + 8;

            context.batcher.textCard(numericLabel, nx, ny, Colors.WHITE, BBSSettings.primaryColor(Colors.A50));
        }
    }

    /** Short label of what the active drag grabs: axis letters, the screen
     *  plane, the view ring, or one of the sphere's rotations. */
    private static String targetLabel(TransformGesture gesture)
    {
        DragStrategy strategy = gesture.getStrategy();
        String special = strategy == null ? null : strategy.editingTargetLabel();

        if (special != null)
        {
            return special;
        }

        if (gesture.getOp() == TransformOp.SCALE && (gesture.isScaleAll() || Window.isCtrlPressed()))
        {
            return "XYZ";
        }

        Axis axis = gesture.getAxis();
        String label = axis == null ? "" : axis.name();

        if (gesture.getAxis2() != null)
        {
            label += gesture.getAxis2().name();
        }

        return label;
    }

    /** Axis letters tint to their gizmo colors; everything else stays white. */
    private static int targetColor(TransformGesture gesture)
    {
        Axis axis = gesture.getAxis();
        boolean singleAxis = axis != null && gesture.getAxis2() == null
            && !gesture.isScreenTranslate()
            && !(gesture.getOp() == TransformOp.SCALE && (gesture.isScaleAll() || Window.isCtrlPressed()));

        if (!singleAxis)
        {
            return Colors.WHITE;
        }

        if (axis == Axis.X) return Colors.A100 | Colors.RED;
        if (axis == Axis.Y) return Colors.A100 | Colors.GREEN;

        return Colors.A100 | Colors.BLUE;
    }

    /** Space chip; scale ignores the space toggle, so it gets none. */
    private static String spaceLabel(TransformGesture gesture)
    {
        if (gesture.getOp() == TransformOp.SCALE)
        {
            return null;
        }

        return gesture.space().label.get();
    }

    /** The live vector of the edited channel, for the cursor's value card. */
    private static Vector3f liveValue(TransformGesture gesture)
    {
        Transform transform = gesture.transform();

        if (transform == null)
        {
            return new Vector3f();
        }

        TransformOp op = gesture.getOp();

        if (op == TransformOp.SCALE)
        {
            return transform.scale;
        }
        else if (op == TransformOp.ROTATE)
        {
            /* A quaternion bone's channels are stale; show its live rotation. */
            return transform.rotationMode == Transform.RotationMode.QUATERNION
                ? transform.getEulerRotation(new Vector3f())
                : transform.rotate;
        }

        return transform.translate;
    }
}
