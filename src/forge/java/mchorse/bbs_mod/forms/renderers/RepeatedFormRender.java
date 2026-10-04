package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.RigBone;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.spline.ModelSplineRuntime;
import mchorse.bbs_mod.cubic.spline.SplineArray;
import mchorse.bbs_mod.forms.FormRenderLast;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.SplineForm;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** One source evaluation shared by all copies, including nested arrays and deferred parts. */
public final class RepeatedFormRender implements AutoCloseable
{
    private static RepeatedFormRender current;
    private final RepeatedFormRender parent;
    private final Map<Form, Boolean> states = new IdentityHashMap<>();
    private final List<Form> applied = new ArrayList<>();
    private final Map<ModelFormRenderer, ModelPose> poses = new IdentityHashMap<>();
    private final Map<Form, Matrix4f> sources = new IdentityHashMap<>();
    private final Map<Form, Boolean> visited = new IdentityHashMap<>();
    private final FormRenderLast.LocalScope renderLast;
    private final Map<SplineForm, List<Matrix4f>> layouts = new IdentityHashMap<>();

    public RepeatedFormRender()
    {
        this.parent = current;
        if (this.parent == null)
        {
            current = this;
            this.renderLast = new FormRenderLast.LocalScope();
        }
        else
        {
            this.renderLast = null;
        }
    }

    public static RepeatedFormRender current() { return current; }

    public void apply(Form form, float transition)
    {
        if (this.states.put(form, true) == null)
        {
            form.applyStates(transition);
            this.applied.add(form);
        }
    }

    /** Returns the first copy's frame; simulations keep a single source instead of jumping between copies. */
    public Matrix4f source(Form form, Matrix4f frame)
    {
        return this.sources.computeIfAbsent(form, key -> new Matrix4f(frame));
    }

    public boolean first(Form form) { return this.visited.put(form, true) == null; }

    /** Camera-relative displacement of this copy from the simulated source. */
    public Matrix4f displacement(Form form, Matrix4f frame)
    {
        Matrix4f source = this.source(form, frame);
        if (Math.abs(source.determinant()) < 1E-10F) return new Matrix4f();
        return new Matrix4f(frame).mul(new Matrix4f(source).invert());
    }

    public ModelPose pose(ModelFormRenderer renderer) { return this.poses.get(renderer); }

    public List<Matrix4f> frames(SplineForm form)
    {
        return this.layouts.computeIfAbsent(form, SplineArray::frames);
    }

    public void capture(ModelFormRenderer renderer, ModelInstance model, ModelSplineRuntime.Motion motion)
    {
        this.poses.put(renderer, new ModelPose(model, motion));
    }

    @Override
    public void close()
    {
        if (this.parent != null) return;
        try
        {
            // Flush while source states and evaluated poses are still held.
            this.renderLast.close();
        }
        finally
        {
            try
            {
                for (int i = this.applied.size() - 1; i >= 0; i--) this.applied.get(i).unapplyStates();
            }
            finally { current = null; }
        }
    }

    public static final class ModelPose
    {
        public final ModelSplineRuntime.Motion motion;
        private final ModelInstance model;
        private final List<Runnable> restore = new ArrayList<>();

        private ModelPose(ModelInstance model, ModelSplineRuntime.Motion motion)
        {
            this.model = model;
            this.motion = motion;
            for (RigBone bone : model.model.getRigBones())
            {
                Transform transform = new Transform();
                transform.copy(bone.getBoneTransform());
                Quaternionf orient = bone.getOrient() == null ? null : new Quaternionf(bone.getOrient());
                Vector3f offset = bone.getOffset() == null ? null : new Vector3f(bone.getOffset());
                this.restore.add(() ->
                {
                    bone.getBoneTransform().copy(transform);
                    bone.setOrient(orient == null ? null : new Quaternionf(orient));
                    bone.setOffset(offset == null ? null : new Vector3f(offset));
                });
                if (bone instanceof ModelGroup group)
                {
                    boolean visible = group.poseVisible;
                    float lighting = group.lighting;
                    var color = group.color.copy();
                    var overlay = group.overlay.copy();
                    this.restore.add(() ->
                    {
                        group.poseVisible = visible;
                        group.lighting = lighting;
                        group.color.copy(color);
                        group.overlay.copy(overlay);
                    });
                }
                else if (bone instanceof BOBJBone bobj)
                {
                    boolean visible = bobj.visible;
                    this.restore.add(() -> bobj.visible = visible);
                }
            }
        }

        public void restore()
        {
            this.restore.forEach(Runnable::run);
            // The shared asset may have been posed by another part using the same model.
            this.model.clearChannels();
        }
    }
}
