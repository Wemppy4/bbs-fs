package mchorse.bbs_mod.cubic.spline;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cubic Spline IK constraint stage. All writes are transient orient/offset; channels stay FK. */
public final class ModelSplineRuntime
{
    private static final float EPS = 1E-5F;

    private ModelSplineRuntime()
    {}

    /** Model-space prefix for one evaluated pose; the caller owns it, never the shared asset. */
    @com.github.bsideup.jabel.Desugar
    public record Motion(String chainId, Matrix4f matrix, Matrix4f parentFrame)
    {}

    public static Motion apply(ModelInstance instance, ModelForm form)
    {
        if (instance == null || !(instance.model instanceof Model model) || form == null || form.splines.getAllTyped().isEmpty()) return null;

        Set<String> occupied = new HashSet<>();
        ModelGroup motionRoot = null;
        SplineIK motionSpline = null;
        Matrix4f fkRoot = null;
        Quaternionf rootOrientation = null;
        Vector3f rootOffset = null;

        for (SplineIK spline : form.splines.getAllTyped())
        {
            SplineControl control = form.splineIK.get().get(spline.getId());
            if (!Float.isFinite(control.influence) || control.influence <= 0F) continue;

            List<ModelGroup> chain = chain(model, spline);
            if (validate(model, form, chain) != null || chain.stream().anyMatch(bone -> occupied.contains(bone.id))) continue;

            ModelGroup root = chain.get(0);
            boolean movesModel = motionRoot == null && spline.moveModel.get();
            Matrix4f before = movesModel ? new Matrix4f() : null;
            Quaternionf orientation = root.orient;
            Vector3f offset = root.offset;
            if (movesModel) localFrame(before, root);

            if (!solve(chain, spline, control, true)) continue;
            for (ModelGroup bone : chain) occupied.add(bone.id);
            if (movesModel)
            {
                motionRoot = root;
                motionSpline = spline;
                fkRoot = before;
                rootOrientation = orientation;
                rootOffset = offset;
            }
        }

        if (motionRoot == null) return null;

        /* Keep the solved root until every constraint has evaluated, so this pass has the
         * same local pose as chain-only mode. Extract its motion afterwards. For parent P,
         * solved root S and original root F, D = P S F^-1 P^-1 gives D P F = P S.
         * This must remain a full affine matrix: a scaled parent can introduce shear. */
        Matrix4f parent = parentFrame(motionRoot);
        Matrix4f solvedRoot = new Matrix4f();
        localFrame(solvedRoot, motionRoot);
        Matrix4f motion = new Matrix4f(parent).mul(solvedRoot)
            .mul(new Matrix4f(fkRoot).invert()).mul(new Matrix4f(parent).invert());
        if (!motion.isFinite()) return null;

        motionRoot.orient = rootOrientation;
        motionRoot.offset = rootOffset;
        return new Motion(motionSpline.getId(), motion, parent);
    }

    /** Same admission rules as the runtime, without changing the shared model's pose. */
    public static boolean isRotationConstrained(IModel source, ModelForm form, String bone)
    {
        if (!(source instanceof Model model) || form == null) return false;
        Set<String> occupied = new HashSet<>();
        for (SplineIK spline : form.splines.getAllTyped())
        {
            SplineControl control = form.splineIK.get().get(spline.getId());
            if (!Float.isFinite(control.influence) || control.influence <= 0F) continue;
            List<ModelGroup> chain = chain(model, spline);
            if (validate(model, form, chain) != null || chain.stream().anyMatch(group -> occupied.contains(group.id))) continue;
            if (!solve(chain, spline, control, false)) continue;
            for (ModelGroup group : chain) occupied.add(group.id);
        }
        return occupied.contains(bone);
    }

    /** Short stable error keys for the editor; null is a supported chain. */
    public static String validate(ModelForm form, SplineIK selected)
    {
        ModelInstance instance = ModelFormRenderer.getModel(form);
        if (instance == null || !(instance.model instanceof Model model)) return "cubic_only";
        String error = validate(model, form, chain(model, selected));
        if (error != null) return error;
        Set<String> ids = new HashSet<>(getChain(form, selected));
        for (SplineIK spline : form.splines.getAllTyped())
        {
            SplineControl control = form.splineIK.get().get(spline.getId());
            if (spline == selected) break;
            if (control.influence > 0F && !Collections.disjoint(ids, getChain(form, spline))) return "spline_conflict";
        }
        return null;
    }

    private static String validate(Model model, ModelForm form, List<ModelGroup> chain)
    {
        if (chain.size() < 2) return "invalid_chain";
        if (chain.size() > 128) return "long_chain";

        Set<String> ids = new HashSet<>();
        for (ModelGroup group : chain)
        {
            ids.add(group.id);
            Vector3f scale = group.current.scale;

            /* The parent of the chain may have any invertible affine transform: the entire
             * solve is in that parent's frame. Within the chain, shear/reflection does not
             * have a unique quaternion frame, so reject it instead of producing broken roll. */
            if (!scale.isFinite() || scale.x <= EPS || Math.abs(scale.x - scale.y) > EPS || Math.abs(scale.x - scale.z) > EPS) return "chain_scale";
        }

        for (Map.Entry<String, List<String>> ik : ModelIKRuntime.getChains(model, form).entrySet())
        {
            FormBone bone = form.bones.getBone(ik.getKey());
            if (bone != null && form.ik.get().get(ik.getKey()).enabled && form.ik.get().get(ik.getKey()).weight > 0F && !Collections.disjoint(ids, ik.getValue())) return "ik_conflict";
        }

        for (BaseValue value : form.bones.getAll())
        {
            if (!(value instanceof FormBone bone)) continue;
            if (ids.contains(bone.getId()) && bone.constraints.get().isActive()) return "limits_conflict";
            if (!bone.hasPhysicsChain() || !form.physics.get().get(bone.getId()).enabled || form.physics.get().get(bone.getId()).weight <= 0F) continue;
            for (ModelGroup physics : chain(model, bone.getId(), bone.physicsEnd.get())) if (ids.contains(physics.id)) return "physics_conflict";
        }

        return null;
    }

    public static List<String> getChain(ModelForm form, SplineIK spline)
    {
        ModelInstance instance = ModelFormRenderer.getModel(form);
        if (instance == null || !(instance.model instanceof Model model)) return java.util.Collections.emptyList();
        return chain(model, spline).stream().map(bone -> bone.id).collect(java.util.stream.Collectors.toList());
    }

    public static String getRoot(ModelForm form, SplineIK spline)
    {
        List<String> bones = getChain(form, spline);
        return bones.isEmpty() ? "" : bones.get(0);
    }

    private static List<ModelGroup> chain(Model model, SplineIK spline)
    {
        if (model.getGroup(spline.tip.get()) == null) return java.util.Collections.emptyList();
        return ModelIKRuntime.chainBones(model, spline.tip.get(), spline.chainLength.get()).stream()
            .map(model::getGroup).collect(java.util.stream.Collectors.toList());
    }

    private static List<ModelGroup> chain(Model model, String root, String tip)
    {
        ModelGroup first = model.getGroup(root);
        ModelGroup cursor = model.getGroup(tip);
        List<ModelGroup> result = new ArrayList<>();

        if (first == null || cursor == null) return result;
        while (cursor != null && result.size() <= 128)
        {
            result.add(cursor);
            if (cursor == first)
            {
                Collections.reverse(result);
                return result;
            }
            cursor = cursor.parent;
        }

        return java.util.Collections.emptyList();
    }

    /**
     * Model-space render matrix of the root's parent, including its pivot conjugation and
     * scale. The collectMatrices bone cache additionally appends T(parent pivot) * Ry(PI)
     * for attachments; remove that suffix before applying spline controls. For a top-level
     * root use the form matrix() followed by rotateY(PI). Point translations have NO cubic X flip.
     * Read only immediately after evaluating this form: ModelInstance is a shared asset.
     */
    public static Matrix4f getParentFrame(ModelForm form, String root)
    {
        ModelInstance instance = ModelFormRenderer.getModel(form);
        if (instance == null || !(instance.model instanceof Model model)) return new Matrix4f();
        return parentFrame(model.getGroup(root));
    }

    private static Matrix4f parentFrame(ModelGroup group)
    {
        List<ModelGroup> parents = new ArrayList<>();
        for (ModelGroup parent = group == null ? null : group.parent; parent != null; parent = parent.parent) parents.add(parent);
        Collections.reverse(parents);
        Matrix4f result = new Matrix4f();
        for (ModelGroup parent : parents) localFrame(result, parent);
        return result;
    }

    private static boolean solve(List<ModelGroup> chain, SplineIK spline, SplineControl control, boolean apply)
    {
        List<Vector3f> controls = new ArrayList<>();
        for (SplinePoint point : spline.points.getAllTyped()) controls.add(new Vector3f(control.point(point.getId()).translate));
        int count = chain.size();
        Vector3f[] fkPositions = new Vector3f[count];
        Vector3f[] fkTangents = new Vector3f[count];
        Quaternionf[] fkWorld = new Quaternionf[count];
        Quaternionf[] fkLocal = new Quaternionf[count];
        Matrix4f matrix = new Matrix4f();
        float[] lengths = new float[count - 1];

        for (int i = 0; i < count; i++)
        {
            ModelGroup bone = chain.get(i);
            fkPositions[i] = matrix.transformPosition(pivotPosition(bone));
            fkLocal[i] = bone.evaluatedRotation();
            localFrame(matrix, bone);
            fkWorld[i] = matrix.getUnnormalizedRotation(new Quaternionf()).normalize();
            if (i > 0) lengths[i - 1] = fkPositions[i].distance(fkPositions[i - 1]);
        }

        SplineIKSolver.Result result = SplineIKSolver.solve(controls, lengths, spline.fit.get(), control.progress / 100F);
        if (result == null || !Float.isFinite(control.twist)) return false;

        for (int i = 0; i < count - 1; i++) fkTangents[i] = new Vector3f(fkPositions[i + 1]).sub(fkPositions[i]).normalize();
        /* No artificial child is needed: the terminal's axis and length come from the final
         * rest segment, but its own FK rotation still matters when orienting its geometry. */
        Vector3f terminalAxis = new Vector3f(chain.get(count - 1).initial.translate).sub(chain.get(count - 2).initial.translate);
        if (terminalAxis.lengthSquared() <= EPS * EPS) return false;
        fkTangents[count - 1] = fkWorld[count - 1].transform(terminalAxis.normalize());

        Vector3f fkNormal = SplineMath.perpendicular(fkTangents[0]);
        Vector3f normal = SplineMath.transport(fkNormal, fkTangents[0], result.tangents()[0]);
        float weight = Math.min(1F, control.influence);
        float twist = (float) Math.toRadians(control.twist);
        Quaternionf[] orientations = new Quaternionf[count];
        Vector3f[] offsets = new Vector3f[count];
        matrix.identity();

        for (int i = 0; i < count; i++)
        {
            ModelGroup bone = chain.get(i);
            if (i > 0)
            {
                fkNormal = SplineMath.transport(fkNormal, fkTangents[i - 1], fkTangents[i]);
                normal = SplineMath.transport(normal, result.tangents()[i - 1], result.tangents()[i]);
            }

            Quaternionf reference = SplineMath.frame(fkTangents[i], fkNormal);
            Quaternionf target = SplineMath.frame(result.tangents()[i], normal);
            target.mul(reference.invert()).mul(fkWorld[i]);

            Quaternionf parentRotation = matrix.getUnnormalizedRotation(new Quaternionf()).normalize();
            Quaternionf local = parentRotation.invert().mul(target).normalize();
            Quaternionf orientation = new Quaternionf(fkLocal[i]).slerp(local, weight).normalize();
            /* Blend the authored roll as an angle after the swing. Slerping a quaternion that
             * already includes twist would wrap to the shortest arc and pop at 180 degrees
             * when influence is partial. The local axis is the FK segment's actual direction. */
            Vector3f localAxis = new Quaternionf(fkWorld[i]).invert().transform(new Vector3f(fkTangents[i]));
            orientation.rotateAxis(twist * weight * i / (count - 1F), localAxis);
            Vector3f desiredPosition = new Vector3f(fkPositions[i]).lerp(result.joints()[i], weight);
            Vector3f localPosition = new Matrix4f(matrix).invert().transformPosition(desiredPosition);
            Vector3f shift = localPosition.sub(pivotWithoutOffset(bone));

            if (!orientation.isFinite() || !shift.isFinite()) return false;
            orientations[i] = orientation;
            offsets[i] = shift;
            Vector3f pivot = bone.initial.translate;
            matrix.translate(pivotWithoutOffset(bone).add(shift)).rotate(orientation).scale(bone.current.scale)
                .translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);
        }

        /* Atomic writeback: malformed animation data must never leave half a solved chain. */
        for (int i = 0; apply && i < count; i++)
        {
            chain.get(i).orient = orientations[i];
            chain.get(i).offset = offsets[i];
        }

        return true;
    }

    private static Vector3f pivotWithoutOffset(ModelGroup bone)
    {
        Vector3f pivot = bone.initial.translate;
        Vector3f current = bone.current.translate;
        return new Vector3f(2F * pivot.x - current.x, current.y, current.z).mul(1F / 16F);
    }

    private static Vector3f pivotPosition(ModelGroup bone)
    {
        Vector3f result = pivotWithoutOffset(bone);
        if (bone.offset != null) result.add(bone.offset);
        return result;
    }

    /** Same transform order as ICubicRenderer, without MatrixStack or an OpenGL dependency. */
    private static void localFrame(Matrix4f matrix, ModelGroup bone)
    {
        Vector3f pivot = bone.initial.translate;
        matrix.translate(pivotPosition(bone)).rotate(bone.evaluatedRotation()).scale(bone.current.scale)
            .translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);
    }
}
