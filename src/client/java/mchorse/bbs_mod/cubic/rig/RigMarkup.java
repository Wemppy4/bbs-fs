package mchorse.bbs_mod.cubic.rig;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.ScreenOverlay;
import mchorse.bbs_mod.utils.Pair;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * The rig's part of the editors' markup: each model form's IK goals, poles and controllers and
 * the dashed lines from its chains to them, captured while the form renders — the renderer holds
 * the solved pose right there, so nothing is evaluated twice — as points on the screen of the
 * pass that drew them (normalized device coordinates). The viewport then draws them on its
 * {@link ScreenOverlay}, and a click on one picks its bone.
 *
 * <p>A capture is a scope. The world frame opens one for the film viewport ({@link #beginWorld}
 * / {@link #endWorld}), a form preview opens its own around its render ({@link #open} /
 * {@link #close}). Nothing is captured outside a scope, in the Iris shadow pass, or inside a
 * framebuffer form, whose parts draw under a projection of their own ({@link #suspend}).</p>
 */
public final class RigMarkup
{
    public enum Kind
    {
        GOAL, POLE, CONTROLLER
    }

    public record Handle(ModelForm form, String bone, Kind kind, int color, Vector3f ndc)
    {}

    public record Line(Vector3f a, Vector3f b, int color)
    {}

    /** What one scope captured. */
    public static final class Capture
    {
        public static final Capture EMPTY = new Capture();

        private final List<Handle> handles = new ArrayList<>();
        private final List<Line> lines = new ArrayList<>();
        private final Set<ModelForm> forms = Collections.newSetFromMap(new IdentityHashMap<>());

        /** Claim the form for this scope: a form drawn twice in it (a translucent replay) is captured once. */
        public boolean claim(ModelForm form)
        {
            return this.forms.add(form);
        }

        public void handle(ModelForm form, String bone, Kind kind, int color, Matrix4f mvp, Vector3f point)
        {
            Vector3f ndc = ndc(mvp, point);

            if (ndc != null)
            {
                this.handles.add(new Handle(form, bone, kind, color, ndc));
            }
        }

        public void line(Matrix4f mvp, Vector3f a, Vector3f b, int color)
        {
            Vector3f na = ndc(mvp, a);
            Vector3f nb = ndc(mvp, b);

            if (na != null && nb != null)
            {
                this.lines.add(new Line(na, nb, color));
            }
        }
    }

    private static Capture capture;
    private static Capture world = Capture.EMPTY;

    private RigMarkup()
    {}

    /* Scopes */

    /** The world frame begins: the film viewport's capture starts over. */
    public static void beginWorld()
    {
        capture = new Capture();
    }

    /** The world frame ended: what it captured is what the film viewport draws. */
    public static void endWorld()
    {
        world = capture == null ? Capture.EMPTY : capture;
        capture = null;
    }

    public static Capture world()
    {
        return world;
    }

    /** Open a preview's own scope; hand the result to {@link #close}. */
    public static Capture open()
    {
        Capture previous = capture;

        capture = new Capture();

        return previous;
    }

    /** Close the scope {@link #open} opened, returning what it captured. */
    public static Capture close(Capture previous)
    {
        Capture done = capture == null ? Capture.EMPTY : capture;

        capture = previous;

        return done;
    }

    /** Stop capturing for a nested render under another projection; {@link #restore} with the result. */
    public static Capture suspend()
    {
        Capture previous = capture;

        capture = null;

        return previous;
    }

    public static void restore(Capture previous)
    {
        capture = previous;
    }

    /** The scope a form that renders now captures into, or null: no scope, or the Iris shadow pass. */
    public static Capture current()
    {
        return BBSRendering.isIrisShadowPass() ? null : capture;
    }

    /* Drawing */

    /** The captured handles and lines on the viewport's markup; {@code selected} tells the bone the editor has picked. */
    public static void draw(ScreenOverlay overlay, Capture capture, BiPredicate<ModelForm, String> selected)
    {
        if (capture == null)
        {
            return;
        }

        for (Line line : capture.lines)
        {
            overlay.dashed(overlay.fromNDC(line.a()), overlay.fromNDC(line.b()), line.color());
        }

        for (Handle handle : capture.handles)
        {
            Vector3f point = overlay.fromNDC(handle.ndc());

            if (point == null)
            {
                continue;
            }

            ScreenOverlay.Shape shape = handle.kind() == Kind.CONTROLLER ? ScreenOverlay.Shape.SQUARE : ScreenOverlay.Shape.DIAMOND;
            float size = handle.kind() == Kind.GOAL ? ScreenOverlay.HANDLE_LARGE : ScreenOverlay.HANDLE;

            overlay.handle(point, shape, size, handle.color(), handle)
                .label(label(handle))
                .selected(selected != null && selected.test(handle.form(), handle.bone()));
        }
    }

    /** The form and bone of the rig handle nearest to the point, if the nearest handle of the markup is one. */
    public static Pair<Form, String> pick(ScreenOverlay overlay, int x, int y)
    {
        return pair(overlay.pick(x, y));
    }

    public static Pair<Form, String> hovered(ScreenOverlay overlay)
    {
        return pair(overlay.getHovered());
    }

    private static Pair<Form, String> pair(ScreenOverlay.Handle hit)
    {
        return hit != null && hit.data instanceof Handle handle ? new Pair<>(handle.form(), handle.bone()) : null;
    }

    private static String label(Handle handle)
    {
        return switch (handle.kind())
        {
            case GOAL -> UIKeys.MARKUP_IK_GOAL.format(handle.bone()).get();
            case POLE -> UIKeys.MARKUP_IK_POLE.format(handle.bone()).get();
            case CONTROLLER -> UIKeys.MARKUP_CONTROLLER.format(handle.bone()).get();
        };
    }

    private static Vector3f ndc(Matrix4f mvp, Vector3f point)
    {
        if (point == null)
        {
            return null;
        }

        Vector4f clip = mvp.transform(new Vector4f(point, 1F));

        if (!Float.isFinite(clip.w) || clip.w <= 0.00001F)
        {
            return null;
        }

        return new Vector3f(clip.x / clip.w, clip.y / clip.w, clip.z / clip.w);
    }
}
