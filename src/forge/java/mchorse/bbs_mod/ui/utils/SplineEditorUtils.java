package mchorse.bbs_mod.ui.utils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import mchorse.bbs_mod.settings.values.base.BaseValue;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.RigBone;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.spline.SplineIK;
import mchorse.bbs_mod.cubic.spline.SplineSource;
import mchorse.bbs_mod.forms.forms.SplineForm;
import mchorse.bbs_mod.cubic.spline.ModelSplineRuntime;
import mchorse.bbs_mod.cubic.spline.SplinePoint;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.ui.framework.UIContext;
import java.util.Set;

/** Point addresses and frames shared by all three editors. No synthetic bone names. */
public class SplineEditorUtils
{
    /** The real keyframe panel supplies its current compound editor and selected sheet. */
    public interface Editor
    {
        Selection getSplineSelection();
        String getSelectedSheetId();
    }

    /** Shared point-selection contract implemented by both original compound spline editors. */
    public interface Selection
    {
        Set<String> selectedPoints(SplineSource chain);
        String hoveredPoint(UIContext context, SplineSource chain);
        void viewportHover(SplineSource chain, String point);
        String pointPath();
        void selectPoint(String path);
    }

    /** The viewport overlay's actual hit, without depending on its drawing implementation. */
    public interface Hit
    {
        SplineSource chain();
        SplinePoint point();
    }
    /** A standalone spline owns its controls; an IK chain belongs to its enclosing model. */
    public static Form owner(SplineSource source)
    {
        return source instanceof Form form ? form : FormUtils.getForm((BaseValue) source);
    }

    public static List<SplineSource> sources(Form form)
    {
        if (form instanceof SplineForm spline) return java.util.Arrays.asList(spline);
        return form instanceof ModelForm model ? new ArrayList<>(model.splines.getAllTyped()) : java.util.Arrays.asList();
    }

    public static List<SplineSource> sourcesInTree(Form form)
    {
        List<SplineSource> result = new ArrayList<>(sources(form));
        if (form != null) for (var part : form.parts.getAllTyped())
            if (part.getForm() != null) result.addAll(sourcesInTree(part.getForm()));
        return result;
    }

    public static final Gizmo.HandleMask HANDLES = Gizmo.HandleMask.of(
        EnumSet.of(Gizmo.Op.MOVE, Gizmo.Op.SCREEN), EnumSet.noneOf(Axis.class));

    @com.github.bsideup.jabel.Desugar
    public record Point(Form form, SplineSource chain, SplinePoint point, TrackId track) {}

    public static Set<String> selectedPoints(Editor editor, SplineSource chain)
    {
        if (editor == null) return java.util.Collections.emptySet();
        if (editor.getSplineSelection() != null) return editor.getSplineSelection().selectedPoints(chain);
        TrackId id = TrackId.parse(selectedPath(editor));
        if (!isPoint(id)) return java.util.Collections.emptySet();
        String[] parts = id.subject().split("/");
        return parts.length == 5 && chain instanceof SplineIK ik && FormUtils.getPath(owner(chain)).equals(id.formPath()) && ik.getId().equals(parts[1]) ? java.util.Collections.singleton(parts[3]) : java.util.Collections.emptySet();
    }

    public static String hoveredPoint(Editor editor, UIContext context, SplineSource chain)
    {
        return editor != null && editor.getSplineSelection() != null ? editor.getSplineSelection().hoveredPoint(context, chain) : "";
    }

    public static void viewportHover(Editor editor, Hit hit)
    {
        if (editor != null && editor.getSplineSelection() != null)
            editor.getSplineSelection().viewportHover(hit == null ? null : hit.chain(), hit == null ? "" : hit.point().getId());
    }

    public static String selectedPath(Editor editor)
    {
        if (editor == null) return null;
        if (editor.getSplineSelection() != null)
            return editor.getSplineSelection().pointPath();
        String path = editor.getSelectedSheetId();
        TrackId id = TrackId.parse(path);
        return isPoint(id) ? path : null;
    }

    public static String compoundPath(String pointPath)
    {
        TrackId id = TrackId.parse(pointPath);
        return isPoint(id) ? TrackId.property(id.formPath(), id.subject().startsWith("points/") ? "curve" : "spline_ik").toKey() : pointPath;
    }

    public static void selectPoint(Editor editor, String pointPath)
    {
        TrackId id = TrackId.parse(pointPath);
        if (isPoint(id) && editor != null && editor.getSplineSelection() != null)
        {
            editor.getSplineSelection().selectPoint(pointPath);
        }
    }

    public static boolean isPoint(TrackId id)
    {
        if (id == null || id.kind() != TrackKind.PROPERTY) return false;
        String[] parts = id.subject().split("/");
        return (parts.length == 3 && parts[0].equals("points") && parts[2].equals("position")) || parts.length == 5 && parts[0].equals("splines") && parts[2].equals("points") && parts[4].equals("position");
    }

    public static Point resolve(Form root, String path)
    {
        if (root == null || path == null) return null;
        TrackId id = TrackId.parse(path);
        if (!isPoint(id)) return null;
        var value = FormUtils.getProperty(root, path);
        if (value == null) return null;
        if (FormUtils.getForm(value) instanceof SplineForm spline)
        {
            SplinePoint point = spline.points.get(id.subject().split("/")[1]);
            return point == null ? null : new Point(spline, spline, point, id);
        }
        if (!(FormUtils.getForm(value) instanceof ModelForm form)) return null;
        String[] parts = id.subject().split("/");
        SplineIK chain = form.splines.get(parts[1]);
        if (chain == null) return null;
        SplinePoint point = chain.points.get(parts[3]);
        return point == null ? null : new Point(form, chain, point, id);
    }

    public static Matrix4f parentMatrix(Form root, IEntity entity, float transition, Form owner, SplineSource source)
    {
        MatrixCache cache = FormUtilsClient.getRenderer(root).collectMatrices(entity, transition);
        if (owner instanceof SplineForm)
        {
            Matrix4f matrix = cache.get(FormUtils.getPath(owner)).matrix();
            return matrix == null ? null : new Matrix4f(matrix);
        }
        if (!(owner instanceof ModelForm form) || !(source instanceof SplineIK chain)) return null;
        ModelInstance instance = ModelFormRenderer.getModel(form);
        RigBone bone = instance == null ? null : instance.model.getBone(ModelSplineRuntime.getRoot(form, chain));
        if (bone == null) return null;
        String path = FormUtils.getPath(form);
        var renderer = (ModelFormRenderer) FormUtilsClient.getRenderer(form);
        var motion = renderer.getSplineMotion();
        if (motion != null && motion.chainId().equals(chain.getId()))
        {
            /* The driver path stays in the original model frame while the model travels.
             * Bone attachment matrices already contain that travel and cannot anchor it. */
            Matrix4f formMatrix = cache.get(path).matrix();
            return formMatrix == null ? null : new Matrix4f(formMatrix).rotateY((float) Math.PI).mul(motion.parentFrame());
        }
        RigBone parent = bone.getParentBone();
        String key = parent == null ? path : path.isEmpty() ? parent.getBoneName() : path + "/" + parent.getBoneName();
        Matrix4f matrix = cache.get(key).matrix();
        if (matrix == null) return null;
        Matrix4f result = parentFrame(matrix, parent);
        if (parent == null && motion != null) result.mul(motion.matrix());
        return result;
    }

    /** Convert the attachment cache to the cubic frame in which spline controls are stored. */
    public static Matrix4f parentFrame(Matrix4f attachmentMatrix, RigBone parent)
    {
        Matrix4f result = new Matrix4f(attachmentMatrix);
        if (parent == null)
        {
            return result.rotateY((float) Math.PI);
        }
        if (parent instanceof ModelGroup group)
        {
            /* captureMatrices appends T(pivot) * Ry(PI) for bone attachments.
             * Controls use the render frame before that suffix, including the parent's scale. */
            result.rotateY(-(float) Math.PI).translate(
                -group.initial.translate.x / 16F,
                -group.initial.translate.y / 16F,
                -group.initial.translate.z / 16F);
        }
        return result;
    }

    public static Matrix4f pointMatrix(Form root, IEntity entity, float transition, Point point)
    {
        if (point == null) return null;
        Matrix4f parent = parentMatrix(root, entity, transition, point.form, point.chain);
        return parent == null ? null : parent.translate(point.chain.position(point.point.getId()).translate);
    }

    /** Root pivot in the same parent frame as the authored control points. */
    public static Vector3f rootPosition(Form root, IEntity entity, float transition, ModelForm form, SplineIK chain)
    {
        Matrix4f parent = parentMatrix(root, entity, transition, form, chain);
        if (parent == null || Math.abs(parent.determinant()) < 1E-8F) return null;
        String bone = ModelSplineRuntime.getRoot(form, chain);
        String path = FormUtils.getPath(form);
        Matrix4f matrix = FormUtilsClient.getRenderer(root).collectMatrices(entity, transition)
            .get(path.isEmpty() ? bone : path + "/" + bone).matrix();
        if (matrix == null) return null;
        Vector3f position = parent.invert().transformPosition(matrix.getTranslation(new Vector3f()));
        return position.isFinite() ? position : null;
    }
}
