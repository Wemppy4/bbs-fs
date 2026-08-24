package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ik.JointDoF;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.settings.values.base.BaseValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles the form's IK chain TOPOLOGY against a model: which bones each chain spans, with the
 * dead references validated away. Only the structure is compiled — the animatable scalars are
 * read live from the bone's {@code ik} property at solve time, so a film track or an edit shows
 * up without any cache to invalidate. (The old compiler baked the scalars in and cached by the
 * identity of the config blob; with the blob gone there is nothing to parse and nothing to key
 * a cache by — the walk over a handful of chains is cheap enough to run per frame.)
 */
final class ModelIKCache
{
    private ModelIKCache()
    {
    }

    /** One chain's structure: its tip, what it reaches for, and the bones it spans root-to-tip. */
    public record CompiledChain(String tip, String target, String poleTarget, boolean tipRotation, boolean stretch, boolean classic, List<String> chainRootToEffector)
    {
    }

    public record Compiled(List<CompiledChain> chains, Map<String, JointDoF> bones)
    {
    }

    public static Compiled compile(IModel model, ModelForm form)
    {
        if (model == null || form == null)
        {
            return null;
        }

        List<CompiledChain> chains = null;
        Map<String, JointDoF> joints = null;

        for (BaseValue value : form.bones.getAll())
        {
            if (!(value instanceof FormBone bone))
            {
                continue;
            }

            JointDoF joint = bone.joint.get();

            if (!joint.isFree())
            {
                if (joints == null)
                {
                    joints = new HashMap<>();
                }

                joints.put(bone.getId(), joint);
            }

            if (!bone.hasChain())
            {
                continue;
            }

            String tip = bone.getId();
            String target = bone.ikTarget.get();

            /* An enabled=false chain still compiles: enabled is an animatable scalar now, so a
             * film track may switch the chain on mid-shot — the solve gates on it per frame. */
            if (!model.getAllGroupKeys().contains(tip) || !model.getAllGroupKeys().contains(target))
            {
                continue;
            }

            List<String> chainIds = buildChainIds(model, tip, bone.ikChainLength.get());

            if (chainIds.size() < 2)
            {
                continue;
            }

            /* Cycle validation, loud: a target that is one of the chain's OWN
             * bones is an absurd rig — the solve's variables move the very point
             * it chases — so such a chain does not compile at all (the panel
             * marks it). A target merely HANGING somewhere under a chain bone is
             * legal and deterministic: frames are collected from the FK pose
             * (orient resets every frame), so the goal is the target's FK spot,
             * never last frame's solve — there is no feedback loop to forbid. */
            if (chainIds.contains(target))
            {
                continue;
            }

            /* A pole target that does not resolve to a real bone — or that is a
             * chain bone itself (the same absurdity, steering the bend from a
             * point the bend moves) — falls back to the empty pole target: the
             * rest-side virtual pole. */
            String poleTarget = bone.ikPoleTarget.get();

            if (!poleTarget.isEmpty()
                && (!model.getAllGroupKeys().contains(poleTarget) || chainIds.contains(poleTarget)))
            {
                poleTarget = "";
            }

            if (chains == null)
            {
                chains = new ArrayList<>();
            }

            chains.add(new CompiledChain(tip, target, poleTarget, bone.ikTipRotation.get(), bone.ikStretch.get(), bone.ikClassic.get(), chainIds));
        }

        if (chains == null && joints == null)
        {
            return null;
        }

        return new Compiled(chains == null ? Collections.emptyList() : chains,
            joints == null ? Collections.emptyMap() : joints);
    }

    /** The chain ids the given tip/length setting spans — for the panel's cycle check. */
    public static List<String> chainIdsFor(IModel model, String tip, int chainLength)
    {
        return buildChainIds(model, tip, chainLength);
    }

    /**
     * Walks up the hierarchy from {@code tip}, collecting up to {@code chainLength}
     * bones ({@code 0} = all the way to the root), and returns them ordered
     * root-to-tip.
     */
    private static List<String> buildChainIds(IModel model, String tip, int chainLength)
    {
        List<String> list = new ArrayList<>();
        String group = tip;

        while (group != null && !group.isEmpty())
        {
            list.add(group);

            if (chainLength > 0 && list.size() >= chainLength)
            {
                break;
            }

            String parent = model.getParentGroupKey(group);

            if (parent == null || parent.equals(group))
            {
                break;
            }

            group = parent;
        }

        Collections.reverse(list);

        return list;
    }
}
