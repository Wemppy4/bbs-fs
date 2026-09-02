package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModelIKRuntime
{
    private ModelIKRuntime()
    {
    }

    public static void apply(ModelInstance instance, Map<String, Vector3f> controllerTargets, Map<String, Vector3f> poleTargets)
    {
        if (instance == null || instance.model == null || !(instance.form instanceof ModelForm form))
        {
            return;
        }

        IModel model = instance.model;
        ModelIKCache.Compiled compiled = ModelIKCache.compile(model, form);

        if (compiled == null)
        {
            return;
        }

        List<ModelIKCache.CompiledChain> chains = compiled.chains();

        if (chains == null || chains.isEmpty())
        {
            return;
        }

        /* The chains' animatable scalars, read live from the bone properties: the form's static
         * setting, or the film's `ik` track when one is driving the bone (the property's runtime
         * value). One read per chain per solve — no override map and nothing to clear. */
        Map<String, IKControl> controls = new HashMap<>();

        for (ModelIKCache.CompiledChain chain : chains)
        {
            FormBone bone = form.bones.getBone(chain.tip());

            controls.put(chain.tip(), bone == null ? IKControl.DEFAULT : bone.ik.get());
        }

        ModelIKApplier.apply(model, chains, compiled.bones(), controllerTargets, poleTargets, form.ikTargetWeights, form.poleTargetWeights, controls);
    }

    /**
     * Whether pointing the chain that ends at {@code tip} (spanning {@code
     * chainLength} bones up) at {@code target} is cyclic — the target is one of
     * the chain's own bones, so the solve's variables would move the very point
     * it chases. Such a chain does not compile; the panel uses this to mark the
     * pick loudly.
     */
    public static boolean isCyclicTarget(IModel model, String tip, int chainLength, String target)
    {
        if (model == null || tip == null || tip.isEmpty() || target == null || target.isEmpty())
        {
            return false;
        }

        return ModelIKCache.chainIdsFor(model, tip, chainLength).contains(target);
    }

    /**
     * Whether the chain ending at {@code tip} has the classic two-bone shape:
     * exactly two directed bones after the auto-tail drop. A classic-marked
     * chain of any other shape solves on the core — the panel uses this to mark
     * the toggle so the fallback never surprises silently.
     */
    public static boolean isClassicShape(IModel model, String tip, int chainLength, boolean tipRotation)
    {
        if (model == null || tip == null || tip.isEmpty())
        {
            return false;
        }

        List<String> ids = ModelIKCache.chainIdsFor(model, tip, chainLength);
        int size = ids.size();

        if (tipRotation && ModelIKApplier.autoTailId(model, ids) != null)
        {
            size--;
        }

        return size == 3;
    }

    /**
     * The bones the chain ending at {@code tip} spans, root to tip — the panel's
     * overlap check (overlapping chains merge into one core tree, which kicks a
     * classic chain off its analytic path).
     */
    public static List<String> chainBones(IModel model, String tip, int chainLength)
    {
        if (model == null || tip == null || tip.isEmpty())
        {
            return Collections.emptyList();
        }

        return ModelIKCache.chainIdsFor(model, tip, chainLength);
    }

    /**
     * Opens the IK solve dump for the duration of one transform gesture, so the
     * log holds exactly the drag being investigated — the transform editor calls
     * this when a drag begins and ends. A no-op unless the applier's debug flag
     * is compiled on, so the call sites cost nothing in a normal build.
     */
    public static void logGesture(boolean active)
    {
        ModelIKApplier.setLogging(active);
    }

    /**
     * Whether the bone's ROTATION is owned by an enabled IK chain: the render
     * follows the solved orientation there, so an FK rotation gesture has no
     * honest response to offer — the gizmo refuses those and dims its rings.
     * The FK channels stay editable through the pads (they remain the blend
     * base and the pose IK falls back to when it's off). The chain tip counts
     * only when the chain also solves tip rotation. The scalars are read live
     * from the bone's {@code ik} property, so a film track that disables or
     * zeroes a chain for the current tick frees its bones automatically.
     */
    public static boolean isRotationConstrained(IModel model, ModelForm form, String bone)
    {
        if (model == null || form == null || bone == null || bone.isEmpty())
        {
            return false;
        }

        ModelIKCache.Compiled compiled = ModelIKCache.compile(model, form);

        if (compiled == null || compiled.chains() == null)
        {
            return false;
        }

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            if (chain == null || chain.chainRootToEffector() == null)
            {
                continue;
            }

            FormBone tipBone = form.bones.getBone(chain.tip());
            IKControl control = tipBone == null ? IKControl.DEFAULT : tipBone.ik.get();

            if (!control.enabled || control.weight <= 0F)
            {
                continue;
            }

            if (!chain.chainRootToEffector().contains(bone))
            {
                continue;
            }

            if (!bone.equals(chain.tip()) || chain.tipRotation())
            {
                return true;
            }
        }

        return false;
    }

    public static List<String> getControllers(ModelInstance instance)
    {
        return targetsOf(instance, false);
    }

    /** The pole-target bones of all chains that have one — the film keys a pole anchor sheet off each. */
    public static List<String> getPoleControllers(ModelInstance instance)
    {
        return targetsOf(instance, true);
    }

    private static List<String> targetsOf(ModelInstance instance, boolean pole)
    {
        if (instance == null || instance.model == null || !(instance.form instanceof ModelForm form))
        {
            return Collections.emptyList();
        }

        ModelIKCache.Compiled compiled = ModelIKCache.compile(instance.model, form);

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return Collections.emptyList();
        }

        Set<String> unique = new LinkedHashSet<>();

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            String target = pole ? chain.poleTarget() : chain.target();

            if (target != null && !target.isEmpty())
            {
                unique.add(target);
            }
        }

        return unique.isEmpty() ? Collections.emptyList() : new ArrayList<>(unique);
    }
}
