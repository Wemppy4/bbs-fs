package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsConfig.BoneConstraint;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.cubic.render.ModelRotationBlender;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ModelIKApplier
{
    private static final int MAX_ITERATIONS = 12;
    private static final float TOLERANCE = 1.0e-4f;
    private static final float EPS = 1.0e-6f;

    private ModelIKApplier()
    {
    }

    public static void apply(IModel model, List<ModelIKCache.CompiledChain> chains, Map<String, Vector3f> controllerTargets, Map<String, Vector3f> poleTargets, Map<String, Float> targetWeights, Map<String, Float> poleWeights, Map<String, IKControl> controlOverrides, Map<String, BoneConstraint> boneLimits)
    {
        if (model == null || chains == null || chains.isEmpty())
        {
            return;
        }

        /* Apply ancestor chains (shallower root) first, and re-collect frames per
         * chain, so a child chain (e.g. an arm) sees the pose its parent chain
         * (e.g. the body) already produced and rides along with it. */
        List<ModelIKCache.CompiledChain> ordered = new ArrayList<>(chains);
        ordered.sort(Comparator.comparingInt((ModelIKCache.CompiledChain chain) -> rootDepth(model, chain)));

        for (ModelIKCache.CompiledChain chain : ordered)
        {
            Set<String> wanted = new HashSet<>();
            wanted.add(chain.target());
            wanted.addAll(chain.chainRootToEffector());

            if (chain.poleTarget() != null && !chain.poleTarget().isEmpty())
            {
                wanted.add(chain.poleTarget());
            }

            Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);
            ModelPivotFrames.collect(model, wanted, frames);

            applyChain(model, chain, frames, controllerTargets, poleTargets, targetWeights, poleWeights, controlOverrides, boneLimits);
        }
    }

    /** Depth of the chain's root bone from the model root, for ancestor-first ordering. */
    private static int rootDepth(IModel model, ModelIKCache.CompiledChain chain)
    {
        List<String> ids = chain.chainRootToEffector();
        String group = ids.isEmpty() ? chain.tip() : ids.get(0);
        int depth = 0;

        while (group != null && !group.isEmpty() && depth < 256)
        {
            String parent = model.getParentGroupKey(group);

            if (parent == null || parent.equals(group))
            {
                break;
            }

            group = parent;
            depth++;
        }

        return depth;
    }

    private static void applyChain(IModel model, ModelIKCache.CompiledChain chain, Map<String, PivotFrame> frames, Map<String, Vector3f> controllerTargets, Map<String, Vector3f> poleTargets, Map<String, Float> targetWeights, Map<String, Float> poleWeights, Map<String, IKControl> controlOverrides, Map<String, BoneConstraint> boneLimits)
    {
        /* The film's `ik` track may override the chain's static config scalars.
         * IK weight is independent of pose `fix` — freezing a bone pins it to rest
         * (changing the FK pose IK reads from) but no longer gates IK weight, which
         * comes only from the config and the `ik` track. */
        IKControl control = controlOverrides == null ? null : controlOverrides.get(chain.tip());

        if (control != null && !control.enabled)
        {
            return;
        }

        boolean pole = control != null ? control.pole : chain.pole();
        float softness = control != null ? control.softness : chain.softness();
        float weight = control != null ? control.weight : chain.weight();
        boolean stretch = chain.stretch();

        float poleAngle = (float) Math.toRadians(control != null ? control.poleAngle : chain.poleAngle());

        if (weight <= 0F)
        {
            return;
        }

        PivotFrame targetFrame = frames.get(chain.target());

        if (targetFrame == null)
        {
            return;
        }

        List<String> chainIds = chain.chainRootToEffector();

        /* Auto-tail (foot IK): with "tip follows target" on, a chain ending in a bare
         * marker bone (no geometry, no children) treats that marker as the EFFECTOR's tail
         * — the bone before it becomes the orientable end, and the IK reaches the tail. So
         * the foot turns to the controller while the leg above bends to plant the tail (the
         * foot's bottom) on the target. Off, or no marker: the chain is used as-is. */
        boolean tipRotation = chain.tipRotation();
        String tailId = tipRotation ? autoTailId(model, chainIds) : null;
        List<String> workIds = tailId == null ? chainIds : chainIds.subList(0, chainIds.size() - 1);

        List<Vector3f> currentPositions = new ArrayList<>(workIds.size());
        Quaternionf rootParentRotation = null;

        for (String id : workIds)
        {
            PivotFrame frame = frames.get(id);

            if (frame == null)
            {
                return;
            }

            currentPositions.add(new Vector3f(frame.position()));

            if (rootParentRotation == null)
            {
                rootParentRotation = new Quaternionf(frame.parentRotation());
            }
        }

        if (rootParentRotation == null)
        {
            return;
        }

        /* The film's target/pole overrides ride a 0..1 weight that eases them in/out across
         * a "None" keyframe, so fading a target glides from the bone's own frame, not origin. */
        Vector3f override = controllerTargets == null ? null : controllerTargets.get(chain.target());
        Vector3f target = new Vector3f(targetFrame.position());

        if (override != null)
        {
            target.lerp(override, weightOf(targetWeights, chain.target()));
        }

        /* "Tip follows target": the effector copies the controller's orientation. Null = keep FK. */
        Quaternionf tipTarget = tipRotation && targetFrame.worldRotation() != null ? new Quaternionf(targetFrame.worldRotation()) : null;

        /* Foot IK: back the reach off so the effector's TAIL (the marker), not its pivot,
         * lands on the target once the effector is turned to the controller's orientation. */
        if (tailId != null && tipTarget != null)
        {
            shiftTargetForTail(target, tipTarget, workIds.get(workIds.size() - 1), tailId, frames);
        }

        Vector3f polePoint = resolvePolePoint(pole, chain.poleTarget(), frames, poleTargets, poleWeights);
        IKSolver.Limit[] limits = buildLimits(model, workIds, boneLimits);
        Vector3f restHinge = restBendNormal(model, workIds, rootParentRotation);

        List<Vector3f> solved = IKSolver.solve(currentPositions, target, pole, polePoint, poleAngle, softness, MAX_ITERATIONS, TOLERANCE, limits, limits == null ? null : rootParentRotation, restHinge);

        /* IK stretch: when the controller is pulled past the chain's reach, the solver lands the tip
         * on the reach sphere, short of the target. The gap (tip -> target, eased by softness so it
         * grows in smoothly) is distributed down the chain as per-bone translations — every bone
         * slides out along the limb, the joints between them spread to fill the space, and the tip
         * reaches the controller. No bone is scaled. Weighted so it fades with the IK. */
        Vector3f stretchGap = null;

        if (stretch && solved.size() >= 3)
        {
            Vector3f gap = new Vector3f(target).sub(solved.get(solved.size() - 1));

            if (gap.lengthSquared() > EPS * EPS)
            {
                stretchGap = gap.mul(weight);
            }
        }

        /* A chain of two or more bones gets the Blender treatment: the pole owns the full
         * orientation (swing and roll), written raw to orient so it bypasses euler
         * entirely — no gimbal, no 180-degree flip, no FK-twist mismatch. A single bone
         * (size 2) keeps the euler reconstruction. */
        if (model instanceof Model cubic && workIds.size() >= 3)
        {
            buildChainOrientations(cubic, workIds, solved, rootParentRotation, weight, tipTarget, stretchGap);
        }
        else if (model instanceof BOBJModel bobj && workIds.size() >= 3)
        {
            buildChainOrientationsBobj(bobj, workIds, solved, rootParentRotation, weight, tipTarget, stretchGap);
        }
        else
        {
            Vector3f[] solvedArray = solved.toArray(new Vector3f[solved.size()]);
            ModelRotationBlender.applyWeightedRotations(model, rootParentRotation, workIds, solvedArray, weight);
        }
    }

    /**
     * The chain's trailing tail marker: a bare cubic end bone — no geometry, no children —
     * that stands in for the effector's tail (the reach point a pivot-based bone can't
     * express itself). Returns its id so the bone before it becomes the orientable end;
     * {@code null} when there is none, the model is not cubic, or the chain is too short to
     * keep a bendable run after dropping the tail.
     */
    private static String autoTailId(IModel model, List<String> chainIds)
    {
        if (chainIds.size() < 4 || !(model instanceof Model cubic))
        {
            return null;
        }

        String lastId = chainIds.get(chainIds.size() - 1);
        ModelGroup last = cubic.getGroup(lastId);

        if (last == null || !last.cubes.isEmpty() || !last.meshes.isEmpty() || !last.children.isEmpty())
        {
            return null;
        }

        return lastId;
    }

    /**
     * Backs the IK target off by the effector's tail offset, turned to the controller's
     * orientation, so the tail (the marker) — not the effector's pivot — lands on the
     * original target once the effector is oriented to the controller. The offset is the
     * tail's rest position in the effector's local frame (constant geometry).
     */
    private static void shiftTargetForTail(Vector3f target, Quaternionf tipTarget, String effectorId, String tailId, Map<String, PivotFrame> frames)
    {
        PivotFrame eff = frames.get(effectorId);
        PivotFrame tail = frames.get(tailId);

        if (eff == null || tail == null || eff.worldRotation() == null)
        {
            return;
        }

        Vector3f offsetLocal = new Quaternionf(eff.worldRotation()).conjugate().transform(new Vector3f(tail.position()).sub(eff.position()));
        Vector3f shift = new Quaternionf(tipTarget).transform(offsetLocal);

        target.sub(shift);
    }

    /**
     * Gives each cubic IK bone the full local orientation of the solved chain, written
     * raw to {@link ModelGroup#orient} — the renderer applies it in place of the
     * bone's euler rotate, so the pole owns the whole orientation. Never touches
     * {@code bone.current.rotate}: IK lives entirely in the transient field, so the FK
     * pose (read by the gizmo, saved, and blended below) stays intact.
     *
     * <p>Each bone's local rotation maps its rest frame to its solved frame, both built
     * from a segment direction and a roll-reference normal (see {@link
     * Matrices#orientMirroredX}). The normal is carried along the chain by parallel
     * transport — a minimal-twist frame, the way a bone inherits its parent's roll in
     * Blender — seeded from the bend of the first joint, so the pole sets the roll and
     * the rest of the chain follows it without per-joint flips. Rest and solved frames
     * are built the SAME way, so at rest they coincide (identity, no baseline twist);
     * for a two-bone chain the transport is a no-op and this is exactly the single bend
     * normal. The parent world frame is walked root-to-tip as the renderer would,
     * advancing by each bone's rendered (blended) orientation so children inherit the
     * same frame the renderer establishes.
     */
    private static void buildChainOrientations(Model model, List<String> chainIds, List<Vector3f> solved, Quaternionf rootParentRotation, float weight, Quaternionf tipTarget, Vector3f stretchGap)
    {
        int bones = chainIds.size() - 1;
        Vector3f[] restDir = new Vector3f[bones];
        Vector3f[] segWorld = new Vector3f[bones];

        for (int i = 0; i < bones; i++)
        {
            Vector3f seg = new Vector3f(solved.get(i + 1)).sub(solved.get(i));

            restDir[i] = restDirection(model, chainIds, i);

            if (restDir[i] == null || seg.lengthSquared() < EPS * EPS)
            {
                return;
            }

            segWorld[i] = seg.normalize();
        }

        /* Distribute the gap only up to the last bone that actually has geometry: a chain ending in a
         * bare end-marker (the reach point, like the tip-rotation tail) would otherwise open a seam
         * BEFORE the marker, leaving the last VISIBLE bone short of the controller. The marker carries
         * no offset and rides the reach bone's full shift, so the visible chain ends on the controller. */
        int reach = stretchGap == null ? -1 : lastGeometryIndex(model, chainIds);
        float reachTotal = 0F;

        for (int i = 0; i < reach; i++)
        {
            reachTotal += solved.get(i).distance(solved.get(i + 1));
        }

        boolean doStretch = stretchGap != null && reach >= 1 && reachTotal > EPS;

        Vector3f[] restNormal = transportNormals(restDir);
        Vector3f[] solvedNormal = transportNormals(segWorld);

        Quaternionf parentWorld = new Quaternionf(rootParentRotation);

        for (int i = 0; i < bones; i++)
        {
            ModelGroup bone = model.getGroup(chainIds.get(i));

            if (bone == null)
            {
                return;
            }

            Quaternionf invParent = new Quaternionf(parentWorld).conjugate();
            Vector3f segLocal = invParent.transform(new Vector3f(segWorld[i]));
            Vector3f normalLocal = invParent.transform(new Vector3f(solvedNormal[i]));

            Quaternionf localRot = Matrices.orientMirroredX(restDir[i], restNormal[i], segLocal, normalLocal);
            Quaternionf oriented = weight >= 1F - EPS ? new Quaternionf(localRot) : fkLocal(bone).slerp(localRot, weight);

            bone.orient = oriented;

            /* Stretch: open the gap before this bone (the segment from its parent), pushing it
             * and everything below out along the limb. parentWorld is still this bone's parent
             * frame here, so the world gap maps into the local translate the renderer applies. */
            if (doStretch && i >= 1 && i <= reach)
            {
                bone.offset = stretchOffset(stretchGap, solved.get(i - 1).distance(solved.get(i)), reachTotal, parentWorld);
            }

            /* Advance by the orientation the renderer will actually apply (the blended
             * one), so a child bone decomposes its segment against the SAME parent frame
             * the renderer establishes. At weight 1 this is the full IK rotation. */
            parentWorld.mul(oriented);
        }

        ModelGroup tip = model.getGroup(chainIds.get(chainIds.size() - 1));

        if (tip == null)
        {
            return;
        }

        /* The tip carries the last gap only when it is itself the reach bone (no trailing marker):
         * its share then completes the cumulative shift so the tip lands on the controller. A bare
         * end-marker beyond the reach bone gets nothing and rides the reach bone's full shift. */
        if (doStretch && bones <= reach)
        {
            tip.offset = stretchOffset(stretchGap, solved.get(bones - 1).distance(solved.get(bones)), reachTotal, parentWorld);
        }

        /* Tip follows target: the effector (last id, not in the directed loop) copies the
         * controller's world orientation. parentWorld is now the tip's parent frame. */
        if (tipTarget != null)
        {
            Quaternionf tipLocal = new Quaternionf(parentWorld).conjugate().mul(tipTarget);

            tip.orient = weight >= 1F - EPS ? tipLocal : fkLocal(tip).slerp(tipLocal, weight);
        }
    }

    /**
     * One bone's share of the stretch gap as a local translation: the world gap scaled by the
     * bone's segment length over the chain length (so longer bones open wider gaps, the chain
     * telescopes evenly), turned into {@code parentWorld}'s frame — the frame the renderer's
     * pre-translate runs in, so {@code ModelGroup.offset} lands the bone in the right world spot.
     */
    private static Vector3f stretchOffset(Vector3f gap, float segLength, float total, Quaternionf parentWorld)
    {
        Vector3f share = new Vector3f(gap).mul(segLength / total);

        return new Quaternionf(parentWorld).conjugate().transform(share);
    }

    /**
     * The deepest chain bone that carries geometry — the bone whose far end should land on the
     * controller when stretching. Trailing bones with no cubes or meshes are bare reach markers
     * (the tip-rotation tail and the like); they ride the reach bone rather than opening a seam
     * before themselves. Falls back to the last bone when nothing in the chain has geometry.
     */
    private static int lastGeometryIndex(Model model, List<String> chainIds)
    {
        for (int i = chainIds.size() - 1; i >= 0; i--)
        {
            ModelGroup bone = model.getGroup(chainIds.get(i));

            if (bone != null && (!bone.cubes.isEmpty() || !bone.meshes.isEmpty()))
            {
                return i;
            }
        }

        return chainIds.size() - 1;
    }

    /**
     * Carries a roll-reference normal along a chain of unit directions by parallel
     * transport: seeded from the bend of the first two segments (a stable perpendicular
     * when they are collinear), then rotated minimally from each segment to the next.
     * The result is a per-bone normal that twists as little as possible along the chain
     * — the same frame inheritance Blender gives a bone from its parent — and never
     * flips the way a per-joint bend normal does when a joint straightens.
     */
    private static Vector3f[] transportNormals(Vector3f[] dirs)
    {
        int m = dirs.length;
        Vector3f[] normals = new Vector3f[m];
        Vector3f seed = m >= 2 ? new Vector3f(dirs[0]).cross(dirs[1]) : new Vector3f();

        normals[0] = seed.lengthSquared() < 1.0e-10f ? stablePerpendicular(dirs[0]) : seed.normalize();

        for (int i = 1; i < m; i++)
        {
            Vector3f n = new Quaternionf().rotationTo(dirs[i - 1], dirs[i]).transform(new Vector3f(normals[i - 1]));

            normals[i] = n.normalize();
        }

        return normals;
    }

    /** The bone's FK local rotation (its euler rotate as a quaternion), the blend base when IK weight is below one. */
    private static Quaternionf fkLocal(ModelGroup bone)
    {
        Vector3f r = bone.current.rotate;

        return Matrices.toQuaternionZYXDegrees(r.x, r.y, r.z);
    }

    /**
     * The BOBJ analogue of {@link #buildChainOrientations}: gives each BOBJ IK bone a
     * full local orientation written to {@link BOBJBone#orient}, which the armature
     * applies in place of the euler rotate. Unlike the cubic chain, BOBJ bones carry a
     * per-bone REST rotation (their {@code relBoneMat}), so the rest and solved frames
     * are walked separately: the rest frame advances by {@code relBoneMat} alone, the
     * solved frame by each bone's applied orientation then {@code relBoneMat}. Both build
     * the roll reference by parallel transport in world, so at rest the two frames
     * coincide and the orientation is identity — no baseline twist. Same X-mirror as
     * cubic ({@link Matrices#orientMirroredX}).
     */
    private static void buildChainOrientationsBobj(BOBJModel model, List<String> chainIds, List<Vector3f> solved, Quaternionf rootParentRotation, float weight, Quaternionf tipTarget, Vector3f stretchGap)
    {
        int bones = chainIds.size() - 1;
        Map<String, BOBJBone> bonesMap = model.getArmature().bones;
        BOBJBone[] chainBones = new BOBJBone[bones];
        Vector3f[] restDir = new Vector3f[bones];
        Quaternionf[] relRot = new Quaternionf[bones];
        Vector3f[] segWorld = new Vector3f[bones];

        for (int i = 0; i < bones; i++)
        {
            BOBJBone bone = bonesMap.get(chainIds.get(i));
            Vector3f seg = new Vector3f(solved.get(i + 1)).sub(solved.get(i));

            restDir[i] = restDirection(model, chainIds, i);

            if (bone == null || restDir[i] == null || seg.lengthSquared() < EPS * EPS)
            {
                return;
            }

            chainBones[i] = bone;
            relRot[i] = bone.relBoneMat.getNormalizedRotation(new Quaternionf());
            segWorld[i] = seg.normalize();
        }

        /* Rest-pose world frames advance by relBoneMat alone (geometry rest, no bone
         * rotation); the root's own relBoneMat is already baked into rootParentRotation. */
        Quaternionf[] restFrame = new Quaternionf[bones];
        restFrame[0] = new Quaternionf(rootParentRotation);

        for (int i = 1; i < bones; i++)
        {
            restFrame[i] = new Quaternionf(restFrame[i - 1]).mul(relRot[i]);
        }

        Vector3f[] restDirWorld = new Vector3f[bones];

        for (int i = 0; i < bones; i++)
        {
            restDirWorld[i] = restFrame[i].transform(new Vector3f(restDir[i]));
        }

        Vector3f[] restNormalWorld = transportNormals(restDirWorld);
        Vector3f[] solvedNormalWorld = transportNormals(segWorld);

        /* Solved-pose world frame advances by each bone's applied orientation, then the
         * next bone's relBoneMat — so a child decomposes against the frame the armature
         * actually establishes (blended orientation at weight < 1). */
        Quaternionf originRot = new Quaternionf(rootParentRotation);

        for (int i = 0; i < bones; i++)
        {
            Quaternionf invOrigin = new Quaternionf(originRot).conjugate();
            Vector3f segLocal = invOrigin.transform(new Vector3f(segWorld[i]));
            Vector3f normalLocal = invOrigin.transform(new Vector3f(solvedNormalWorld[i]));
            Vector3f restNormalLocal = new Quaternionf(restFrame[i]).conjugate().transform(new Vector3f(restNormalWorld[i]));

            Quaternionf localRot = Matrices.orientMirroredX(restDir[i], restNormalLocal, segLocal, normalLocal);
            Quaternionf oriented = weight >= 1F - EPS ? new Quaternionf(localRot) : bobjFkLocal(chainBones[i]).slerp(localRot, weight);

            chainBones[i].orient = oriented;

            if (i + 1 < bones)
            {
                originRot.mul(oriented).mul(relRot[i + 1]);
            }
        }

        /* Tip follows target: the effector copies the controller's world orientation. Its
         * parent frame is the last directed bone's frame advanced by its applied
         * orientation and the tip's own relBoneMat. */
        if (tipTarget != null)
        {
            BOBJBone tip = bonesMap.get(chainIds.get(chainIds.size() - 1));

            if (tip != null)
            {
                Quaternionf tipRelRot = tip.relBoneMat.getNormalizedRotation(new Quaternionf());
                Quaternionf tipParent = new Quaternionf(originRot).mul(chainBones[bones - 1].orient).mul(tipRelRot);
                Quaternionf tipLocal = tipParent.conjugate().mul(tipTarget);

                tip.orient = weight >= 1F - EPS ? new Quaternionf(tipLocal) : bobjFkLocal(tip).slerp(tipLocal, weight);
            }
        }

        if (stretchGap != null)
        {
            stretchBobj(model, bonesMap, chainIds, solved, stretchGap);
        }
    }

    /**
     * Telescopes a BOBJ chain past its reach: each bone gets the CUMULATIVE world shift that carries
     * its head joint towards the controller — the gap scaled by how far along the chain the bone sits,
     * so the last DEFORMING bone lands the skin on the target and the mesh stretches smoothly between
     * bones (vertices blend the per-bone shifts). Unlike the cubic rigid telescope this opens no hard
     * seams; the continuous skin just follows. The distribution stops at the last bone with skin — a
     * trailing bare end-marker carries no vertices, so reaching it instead would leave the visible
     * mesh short of the controller (its share capped to the full gap so any stray child still rides).
     * Written to {@link BOBJBone#offset}, which the armature folds into the skinning matrix only,
     * leaving the skeleton frames nominal.
     */
    private static void stretchBobj(BOBJModel model, Map<String, BOBJBone> bonesMap, List<String> chainIds, List<Vector3f> solved, Vector3f gap)
    {
        int joints = chainIds.size();
        int reach = lastInfluenceIndex(model, bonesMap, chainIds);
        float reachTotal = 0F;

        for (int i = 0; i < reach; i++)
        {
            reachTotal += solved.get(i).distance(solved.get(i + 1));
        }

        if (reach < 1 || reachTotal <= EPS)
        {
            return;
        }

        float arclen = 0F;

        for (int i = 1; i < joints; i++)
        {
            arclen += solved.get(i - 1).distance(solved.get(i));

            BOBJBone bone = bonesMap.get(chainIds.get(i));

            if (bone != null)
            {
                bone.offset = new Vector3f(gap).mul(Math.min(arclen / reachTotal, 1F));
            }
        }
    }

    /**
     * The deepest chain bone that deforms mesh — the BOBJ analogue of {@link #lastGeometryIndex}.
     * Trailing bones with no skin are bare reach markers (the end-bone pattern); the stretch ends on
     * the bone before them so the visible mesh lands on the controller. Falls back to the last bone.
     */
    private static int lastInfluenceIndex(BOBJModel model, Map<String, BOBJBone> bonesMap, List<String> chainIds)
    {
        for (int i = chainIds.size() - 1; i >= 0; i--)
        {
            BOBJBone bone = bonesMap.get(chainIds.get(i));

            if (bone != null && model.boneDeformsMesh(bone.index))
            {
                return i;
            }
        }

        return chainIds.size() - 1;
    }

    /** A BOBJ bone's FK local rotation (its radian euler rotate as a quaternion), the blend base when IK weight is below one. */
    private static Quaternionf bobjFkLocal(BOBJBone bone)
    {
        Vector3f r = bone.transform.rotate;

        return new Quaternionf().rotationZYX(r.z, r.y, r.x);
    }

    /** A deterministic unit perpendicular to {@code dir}, cross with world Z (falling back to world Y when parallel). */
    private static Vector3f stablePerpendicular(Vector3f dir)
    {
        Vector3f perp = new Vector3f(dir).cross(0F, 0F, 1F);

        if (perp.lengthSquared() < EPS * EPS)
        {
            perp.set(dir).cross(0F, 1F, 0F);
        }

        return perp.normalize();
    }

    /**
     * Resolves the pole target into a model-space point the bend aims at: the
     * film override position if the chain's pole bone is being driven, otherwise
     * the pole bone's current position. Returns {@code null} (automatic hinge)
     * when the chain has no pole or no pole target.
     */
    private static Vector3f resolvePolePoint(boolean pole, String poleTarget, Map<String, PivotFrame> frames, Map<String, Vector3f> poleTargets, Map<String, Float> poleWeights)
    {
        if (!pole || poleTarget == null || poleTarget.isEmpty())
        {
            return null;
        }

        Vector3f override = poleTargets == null ? null : poleTargets.get(poleTarget);
        PivotFrame frame = frames.get(poleTarget);
        Vector3f config = frame == null ? null : new Vector3f(frame.position());

        if (override == null)
        {
            return config;
        }

        /* Slide the pole from its config bone to the keyframed target by the fade
         * weight, so fading a pole in/out glides from the config pole, not origin. */
        return config == null ? new Vector3f(override) : config.lerp(override, weightOf(poleWeights, poleTarget));
    }

    /** The override's 0..1 fade weight (1 = full override) — 1 when the chain has no weighted fade this frame. */
    private static float weightOf(Map<String, Float> weights, String id)
    {
        return weights == null ? 1F : weights.getOrDefault(id, 1F);
    }

    /**
     * Builds per-bone rotation limits for the chain's directed bones (root..tip-1),
     * matching the renderer's reconstruction so the clamp is exact. The solver math
     * is in degrees and the {@link BoneConstraint} limits are degrees, so the same
     * path serves cubic {@link Model} and {@link BOBJModel} — only the rest
     * direction differs (each taken the way that model's renderer takes it).
     * Returns {@code null} when no bone in the chain is constrained (fast path) or
     * the model is neither type. Every entry carries the bone's local rest
     * direction (needed to advance the parent frame during the clamp pass);
     * {@code enabled} is set only where a constraint exists.
     */
    private static IKSolver.Limit[] buildLimits(IModel model, List<String> chainIds, Map<String, BoneConstraint> boneLimits)
    {
        if (boneLimits == null || boneLimits.isEmpty())
        {
            return null;
        }

        int directed = chainIds.size() - 1;

        if (directed < 1)
        {
            return null;
        }

        boolean any = false;

        for (int i = 0; i < directed; i++)
        {
            BoneConstraint c = boneLimits.get(chainIds.get(i));

            if (c != null && c.enabled())
            {
                any = true;
                break;
            }
        }

        if (!any)
        {
            return null;
        }

        IKSolver.Limit[] limits = new IKSolver.Limit[directed];

        for (int i = 0; i < directed; i++)
        {
            String id = chainIds.get(i);
            Vector3f restDir = restDirection(model, chainIds, i);

            if (restDir == null)
            {
                return null;
            }

            BoneConstraint c = boneLimits.get(id);
            boolean enabled = c != null && c.enabled();

            limits[i] = enabled
                ? new IKSolver.Limit(true, restDir, c.minX(), c.minY(), c.minZ(), c.maxX(), c.maxY(), c.maxZ())
                : new IKSolver.Limit(false, restDir, 0F, 0F, 0F, 0F, 0F, 0F);
        }

        return limits;
    }

    /**
     * The bone's local rest direction towards its child, taken exactly as that
     * model's renderer takes it (cubic: pivot difference; BOBJ: the renderer's
     * own {@link ModelRotationBlender#getBobjRestDirection}), so the limit clamp
     * reconstructs the same swing the renderer applies.
     */
    private static Vector3f restDirection(IModel model, List<String> chainIds, int i)
    {
        String id = chainIds.get(i);
        String childId = chainIds.get(i + 1);

        if (model instanceof Model cubic)
        {
            ModelGroup bone = cubic.getGroup(id);
            ModelGroup child = cubic.getGroup(childId);

            if (bone == null || child == null)
            {
                return null;
            }

            return normalizeRest(new Vector3f(child.initial.translate).sub(bone.initial.translate));
        }

        if (model instanceof BOBJModel bobj)
        {
            BOBJBone bone = bobj.getArmature().bones.get(id);
            BOBJBone child = bobj.getArmature().bones.get(childId);

            if (bone == null)
            {
                return null;
            }

            return normalizeRest(ModelRotationBlender.getBobjRestDirection(bobj, bone, child, chainIds, i));
        }

        return null;
    }

    /**
     * The chain's authored bend side, in model space: the normal of the rest bend
     * plane {@code restDir[0] x restDir[1]} — the side the limb was modelled bent
     * towards (knee forward, elbow back) — taken in the root's local frame and
     * lifted by the root's current world rotation, so it tracks the limb as the
     * shoulder/hip turns. The two-bone solve falls back to this when the live FK
     * pose is straight, so a limb bends the way it was built with no pole target.
     * Returns {@code null} when the chain is shorter than two bones or the rest
     * pose is dead straight (no plane — then only a pole or limit can pick a side).
     */
    private static Vector3f restBendNormal(IModel model, List<String> chainIds, Quaternionf rootParentRotation)
    {
        if (chainIds.size() < 3)
        {
            return null;
        }

        Vector3f a = restDirection(model, chainIds, 0);
        Vector3f b = restDirection(model, chainIds, 1);

        if (a == null || b == null)
        {
            return null;
        }

        Vector3f normal = new Vector3f(a).cross(b);

        if (normal.lengthSquared() < EPS * EPS)
        {
            return null;
        }

        return rootParentRotation.transform(normal.normalize());
    }

    private static Vector3f normalizeRest(Vector3f restDir)
    {
        if (restDir.lengthSquared() < 1.0e-12f)
        {
            restDir.set(0F, -1F, 0F);
        }

        restDir.normalize();

        return restDir;
    }
}
