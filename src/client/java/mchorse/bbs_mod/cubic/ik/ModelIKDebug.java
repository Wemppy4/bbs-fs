package mchorse.bbs_mod.cubic.ik;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.cubic.rig.RigHandles;
import mchorse.bbs_mod.cubic.rig.RigMarkup;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rig's overlay: each IK chain's goal and pole as dots (the goal's the bigger) bridged to the
 * chain by dashed relationship lines, and the form's controller bones as rounded squares in their own
 * colour (gold unless set). A spline's points are the spline overlay's ({@code SplineOverlay}). Nothing is drawn
 * here: the handles are captured into the editors' markup ({@link RigMarkup}) while the model
 * renders, and the viewport draws them on its screen layer and picks their bones off it. It reads
 * the model-local pivot frames the renderer already produced — IK is applied to the rig before
 * this runs, so the frames are the solved chain (re-solving would double-apply the pole angle).
 *
 * <p>The chains show with the rig's debug switch ({@code BBSSettings.debugOverlay}, also F5); the
 * handles — goals, poles and controllers — show with it too, and on their own while the form is
 * open on the rig tab ({@link RigHandles}), to be clicked without the debug.
 */
public final class ModelIKDebug
{
    private static final int POLE_COLOR = 0xff8c26;
    /* How solid the dashed lines draw next to their handles */
    private static final int LINE_ALPHA = 0xd9000000;

    /** A marker on a bone: a dot at a chain's goal or pole, a rounded square at a controller. */
    private record Handle(String bone, RigMarkup.Kind kind, int color)
    {}

    private ModelIKDebug()
    {
    }

    /**
     * Captures the solved chains off the rig now, into the markup scope that is open. The rig is
     * shared by every form of the same model, so a body part of that model re-poses it: the
     * frames are read here, at the form's own turn.
     */
    public static void capture(MatrixStack stack, IModel model, ModelForm form)
    {
        RigMarkup.Capture capture = RigMarkup.current();
        boolean overlay = BBSSettings.debugOverlay.get();

        if (capture == null || model == null || form == null || (!overlay && !RigHandles.shown(form)))
        {
            return;
        }

        List<ModelIKCache.CompiledChain> enabled = enabledChains(model, form);
        Map<String, Handle> handles = handles(model, form, enabled);
        List<ModelIKCache.CompiledChain> chains = overlay ? enabled : List.of();

        if ((chains.isEmpty() && handles.isEmpty()) || !capture.claim(form))
        {
            return;
        }

        Map<String, PivotFrame> frames = collectFrames(model, chains, handles.keySet());
        Matrix4f mvp = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(stack.peek().getPositionMatrix());

        if (model.isFacingFlipped())
        {
            mvp.rotateY(MathUtils.PI);
        }

        for (ModelIKCache.CompiledChain chain : chains)
        {
            captureChain(capture, mvp, frames, handles, chain);
        }

        for (Handle handle : handles.values())
        {
            capture.handle(form, handle.bone(), handle.kind(), handle.color(), mvp, position(frames, handle.bone()));
        }
    }

    /** The compiled chains whose IK is switched on. */
    private static List<ModelIKCache.CompiledChain> enabledChains(IModel model, ModelForm form)
    {
        List<ModelIKCache.CompiledChain> chains = new ArrayList<>();
        ModelIKCache.Compiled compiled = ModelIKCache.compile(model, form);

        if (compiled == null)
        {
            return chains;
        }

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            if (form.bones.getBone(chain.tip()) != null && form.ik.get().get(chain.tip()).enabled)
            {
                chains.add(chain);
            }
        }

        return chains;
    }

    /**
     * Every handle, one per bone: the chains' goals (in the colour each chain gives its goal) and
     * poles, then the controllers — a bone that is both a controller and a goal or a pole is the
     * controller's square.
     */
    private static Map<String, Handle> handles(IModel model, ModelForm form, List<ModelIKCache.CompiledChain> chains)
    {
        Map<String, Handle> handles = new LinkedHashMap<>();

        for (ModelIKCache.CompiledChain chain : chains)
        {
            handles.put(chain.target(), new Handle(chain.target(), RigMarkup.Kind.GOAL, Colors.A100 | targetColor(form, chain.tip())));

            if (chain.poleTarget() != null && !chain.poleTarget().isEmpty())
            {
                handles.putIfAbsent(chain.poleTarget(), new Handle(chain.poleTarget(), RigMarkup.Kind.POLE, Colors.A100 | POLE_COLOR));
            }
        }

        for (BaseValue value : form.bones.getAll())
        {
            if (value instanceof FormBone bone && bone.boneController.get() && model.getAllGroupKeys().contains(bone.getId()))
            {
                handles.put(bone.getId(), new Handle(bone.getId(), RigMarkup.Kind.CONTROLLER, Colors.A100 | bone.boneControllerColor.get()));
            }
        }

        return handles;
    }

    /** The colour the chain on the tip gives the marker at its goal. */
    private static int targetColor(ModelForm form, String tip)
    {
        FormBone formBone = form.bones.getBone(tip);

        return formBone == null ? FormBone.TARGET_COLOR : formBone.ikTargetColor.get();
    }

    private static Map<String, PivotFrame> collectFrames(IModel model, List<ModelIKCache.CompiledChain> chains, Set<String> bones)
    {
        Set<String> wanted = new HashSet<>(bones);

        for (ModelIKCache.CompiledChain chain : chains)
        {
            wanted.add(chain.target());
            wanted.addAll(chain.chainRootToEffector());

            if (chain.poleTarget() != null && !chain.poleTarget().isEmpty())
            {
                wanted.add(chain.poleTarget());
            }
        }

        Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);

        ModelPivotFrames.collect(model, wanted, frames, null, true);

        return frames;
    }

    /** Dashed bridges from the chain to its goal and pole, in their handles' colours. */
    private static void captureChain(RigMarkup.Capture capture, Matrix4f mvp, Map<String, PivotFrame> frames, Map<String, Handle> handles, ModelIKCache.CompiledChain chain)
    {
        List<String> ids = chain.chainRootToEffector();
        int n = ids.size();

        if (n < 2)
        {
            return;
        }

        List<Vector3f> pts = new ArrayList<>(n);

        for (String id : ids)
        {
            Vector3f p = position(frames, id);

            if (p == null)
            {
                return;
            }

            pts.add(p);
        }

        Vector3f target = position(frames, chain.target());

        if (target == null)
        {
            return;
        }

        Vector3f pole = chain.poleTarget() == null || chain.poleTarget().isEmpty() ? null : position(frames, chain.poleTarget());

        capture.line(mvp, pts.get(n - 1), target, lineColor(handles.get(chain.target())));

        if (pole != null)
        {
            capture.line(mvp, pts.get(1), pole, lineColor(handles.get(chain.poleTarget())));
        }
    }

    private static int lineColor(Handle handle)
    {
        return LINE_ALPHA | (handle.color() & 0xffffff);
    }

    private static Vector3f position(Map<String, PivotFrame> frames, String bone)
    {
        PivotFrame frame = frames.get(bone);

        return frame == null ? null : new Vector3f(frame.position());
    }
}
