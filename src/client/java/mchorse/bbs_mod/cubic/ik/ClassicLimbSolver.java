package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelRotationBlender;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The CLASSIC two-bone limb path — the pre-channel-core IK, resurrected as an
 * opt-in per-chain mode next to the tree solver, because for the plain limb
 * case it is simply the better tool: the elbow is placed analytically (exact,
 * one step, no iteration), and each bone's orientation is assembled FROM the
 * solved positions entirely in quaternions — swing via a from-to rotation,
 * roll by parallel-transporting the bend normal along the chain. No euler
 * parametrization ever enters the solve, so there is no configuration where it
 * sticks or flips: the target can orbit 360° freely, pole or no pole.
 *
 * <p>The price, and why it is opt-in rather than the default: it knows nothing
 * of per-bone joint freedom (locks/limits/stiffness are ignored) and cannot
 * negotiate a shared bone with another chain — a classic chain that overlaps
 * one solves on the core instead (the panel marks this statically).
 *
 * <p>Position solve and orientation pass are lifted from the legacy
 * {@code IKSolver}/{@code ModelIKApplier} (pre-M6), trimmed to the two-segment
 * case; both bone flavours write the result to {@code orient}, the same
 * constraint-stack contract the core path honours.
 */
final class ClassicLimbSolver
{
    private static final float EPS = 1.0e-6f;

    /* Cap the goal at exactly full reach (never past it). A dead-straight chain
     * IS allowed — an authored-straight limb straightened by the target must land
     * perfectly straight, not a hair bent. Full extension has no defined bend
     * plane, but the orientation pass handles that gracefully: the roll reference
     * is seeded from the solve's bend normal (defined even at full extension), so
     * a straight chain is stable rather than rolling. */
    private static final float REACH_LIMIT = 1F;

    private ClassicLimbSolver()
    {
    }

    /** Whether the work chain has the classic shape: exactly two directed bones. */
    static boolean eligible(List<String> workIds)
    {
        return workIds != null && workIds.size() == 3;
    }

    /**
     * Solves and applies one classic chain. Returns {@code false} — with no bone
     * touched — when the chain cannot run here (wrong shape, missing frames,
     * degenerate geometry), so the caller can hand it to the core solver.
     */
    static boolean apply(IModel model, List<String> workIds, Map<String, PivotFrame> frames, Vector3f target, Quaternionf tipTarget, Vector3f polePoint, float poleAngle, float softness, float weight, boolean stretch)
    {
        IKRig rig = IKRig.of(model);

        if (!eligible(workIds) || rig == null)
        {
            return false;
        }

        List<Vector3f> positions = new ArrayList<>(workIds.size());
        Quaternionf rootParentRotation = null;

        for (String id : workIds)
        {
            PivotFrame frame = frames.get(id);

            if (frame == null)
            {
                return false;
            }

            positions.add(new Vector3f(frame.position()));

            if (rootParentRotation == null)
            {
                rootParentRotation = new Quaternionf(frame.parentRotation());
            }
        }

        float total = positions.get(0).distance(positions.get(1)) + positions.get(1).distance(positions.get(2));

        if (total <= EPS)
        {
            return false;
        }

        Vector3f root = new Vector3f(positions.get(0));
        Vector3f goal = clampReach(root, target, total, softness);

        /* Bend direction: the live posed bend when the limb is actually bent,
         * else the authored REST bend (knee forward, elbow back) carried into
         * this frame, else a stable side axis — so the bend plane always exists
         * and never lands on an arbitrary side. The pole, when present,
         * overrides either (and poleAngle rolls on top). */
        Vector3f hinge = liveBendNormal(positions);

        if (hinge == null)
        {
            hinge = restBendNormal(model, workIds, rootParentRotation);
        }

        if (hinge == null)
        {
            Vector3f limb = new Vector3f(positions.get(2)).sub(positions.get(0));

            hinge = normalize(limb) ? sideAxis(limb) : null;
        }

        solveTwoBone(positions, root, goal);

        /* The bend-plane normal the solve settled on (null when undefined) seeds
         * the orientation pass's roll reference, so a straightened limb keeps a
         * stable twist through the reach boundary instead of jittering. */
        Vector3f bendSeed = orientBend(positions, hinge, polePoint, poleAngle);

        /* IK stretch, the legacy in-pass flavour: the gap the rotation solve could
         * not close is split among the bones as translations (see the orientation
         * pass), weighted so it fades with the IK. */
        Vector3f stretchGap = null;

        if (stretch)
        {
            Vector3f gap = new Vector3f(target).sub(positions.get(2));

            if (gap.lengthSquared() > EPS * EPS)
            {
                stretchGap = gap.mul(weight);
            }
        }

        rig.buildChainOrientations(workIds, positions, rootParentRotation, weight, tipTarget, stretchGap, bendSeed);

        return true;
    }

    /* ------------------------------------------------------------------ */
    /* Position solve (legacy IKSolver, two-segment subset)                */
    /* ------------------------------------------------------------------ */

    /**
     * Maps the target onto an effective reach distance. With {@code softness > 0}
     * this is "soft IK": near full extension the effective distance approaches the
     * chain length asymptotically (and C1-continuously), so the limb never snaps
     * dead straight when the target is pulled out of reach. With softness 0 it is
     * a hard clamp at full reach.
     */
    private static Vector3f clampReach(Vector3f root, Vector3f target, float total, float softness)
    {
        Vector3f goal = new Vector3f(target);
        float dist = root.distance(target);

        if (dist < EPS)
        {
            return goal;
        }

        Vector3f dir = new Vector3f(target).sub(root).div(dist);

        if (softness > EPS)
        {
            float soft = Math.min(softness, 1F) * total;
            float da = total - soft;

            if (dist > da)
            {
                float eff = total - soft * (float) Math.exp(-(dist - da) / soft);

                goal.set(root).fma(Math.min(eff, total * REACH_LIMIT), dir);
            }
        }
        else if (dist > total * REACH_LIMIT)
        {
            goal.set(root).fma(total * REACH_LIMIT, dir);
        }

        return goal;
    }

    /** The analytic two-bone solution: law of cosines places the elbow, the tip lands on the goal. */
    private static void solveTwoBone(List<Vector3f> p, Vector3f root, Vector3f goal)
    {
        float l1 = root.distance(p.get(1));
        float l2 = p.get(1).distance(p.get(2));
        Vector3f dir = new Vector3f(goal).sub(root);
        float dist = dir.length();

        if (dist < EPS)
        {
            return;
        }

        dir.div(dist);

        float cosA = (l1 * l1 + dist * dist - l2 * l2) / (2F * l1 * dist);
        cosA = Math.max(-1F, Math.min(1F, cosA));
        float sinA = (float) Math.sqrt(Math.max(0F, 1F - cosA * cosA));

        /* Seed the bend on any valid plane; orientBend fixes the direction. */
        Vector3f bend = perpendicular(root, p.get(1), goal);

        if (bend == null)
        {
            bend = new Vector3f();
            anyPerpendicular(dir, bend);
        }

        p.get(1).set(root).fma(l1 * cosA, dir).fma(l1 * sinA, bend);
        p.get(2).set(goal);
    }

    /**
     * Aims the bend about the root-to-tip axis: at the pole point when there is a
     * pole target (the elbow points at a stable external reference, so it can't
     * flip as the target swings), otherwise at {@code axis x hinge} — the bend
     * direction matching the captured hinge so the limb behaves like a hinge and
     * never inverts. POSITION-level only: it moves where the elbow points, setting
     * the bend plane the orientation pass then rolls each bone to. The root and
     * tip lie on the axis, so reach is preserved. {@code poleAngle} (radians) then
     * rolls that aimed bend about the limb axis — Blender's pole angle, an offset
     * baked into the elbow position, so it is stable (no twist singularity).
     *
     * <p>Returns the world bend-plane normal the solve settled on, sign-matched to
     * {@code dir0 x dir1} — well-defined even at full extension (it comes from the
     * stable pole/hinge, not the vanishing elbow offset), so the orientation pass
     * can seed a continuous roll reference. {@code null} when the bend plane is
     * undefined (no pole, no hinge, or degenerate geometry).
     */
    private static Vector3f orientBend(List<Vector3f> p, Vector3f hinge, Vector3f polePoint, float poleAngle)
    {
        Vector3f root = p.get(0);
        Vector3f axis = new Vector3f(p.get(2)).sub(root);

        if (!normalize(axis))
        {
            return null;
        }

        Vector3f desired = new Vector3f();

        if (polePoint != null)
        {
            desired.set(polePoint).sub(root);

            if (!project(desired, axis))
            {
                return null;
            }
        }
        else if (hinge != null)
        {
            desired.set(axis).cross(hinge);

            if (!project(desired, axis))
            {
                return null;
            }
        }
        else
        {
            return null;
        }

        /* Pole angle: roll the aimed bend about the limb axis. desired is already
         * perpendicular to axis and rotating about axis keeps it so, so the signed
         * angle below stays valid. Applies to the pole and auto-hinge bends alike. */
        if (poleAngle != 0F)
        {
            new Quaternionf().fromAxisAngleRad(axis.x, axis.y, axis.z, poleAngle).transform(desired);
        }

        Vector3f current = new Vector3f(p.get(1)).sub(root);

        if (project(current, axis))
        {
            float theta = signedAngle(current, desired, axis);

            if (Math.abs(theta) >= EPS)
            {
                Quaternionf q = new Quaternionf().fromAxisAngleRad(axis.x, axis.y, axis.z, theta);
                Vector3f rel = new Vector3f(p.get(1)).sub(root);

                q.transform(rel);
                p.get(1).set(root).add(rel);
            }
        }

        Vector3f normal = new Vector3f(desired).cross(axis);

        return normalize(normal) ? normal : null;
    }

    /**
     * The normal of the limb's posed bend plane, {@code (elbow-root) x (tip-root)}
     * — the side the limb is currently bent towards. Null when the limb is straight
     * (no posed plane), letting the caller fall back to the authored rest bend or a
     * fixed side axis.
     */
    private static Vector3f liveBendNormal(List<Vector3f> p)
    {
        Vector3f a = p.get(0);
        Vector3f normal = new Vector3f(p.get(1)).sub(a).cross(new Vector3f(p.get(2)).sub(a));

        return normalize(normal) ? normal : null;
    }

    /**
     * The chain's authored bend side, in model space: the normal of the rest bend
     * plane {@code restDir[0] x restDir[1]} — the side the limb was modelled bent
     * towards (knee forward, elbow back) — taken in the root's local frame and
     * lifted by the root's current parent rotation, so it tracks the limb as the
     * shoulder/hip turns. {@code null} when the rest pose is dead straight (no
     * plane — then only a pole or the side-axis fallback can pick a side).
     */
    private static Vector3f restBendNormal(IModel model, List<String> chainIds, Quaternionf rootParentRotation)
    {
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

    /**
     * A fixed side direction perpendicular to {@code axis}, derived from the axis
     * alone ({@code axis x worldForward}, falling back to {@code worldUp}). Depends
     * only on the limb direction, so — unlike the posed hinge — it never breathes
     * with the animation. The straight-limb hinge fallback.
     */
    private static Vector3f sideAxis(Vector3f axis)
    {
        Vector3f side = new Vector3f(axis).cross(0F, 0F, 1F);

        if (normalize(side))
        {
            return side;
        }

        side = new Vector3f(axis).cross(0F, 1F, 0F);

        return normalize(side) ? side : null;
    }

    /* ------------------------------------------------------------------ */
    /* Orientation pass (legacy applier): positions -> quaternion orients  */
    /* ------------------------------------------------------------------ */

    /**
     * Gives each cubic IK bone the full local orientation of the solved chain,
     * written raw to {@link ModelGroup#orient} — the renderer applies it in place
     * of the bone's euler rotate, so the solve owns the whole orientation. Never
     * touches {@code bone.current.rotate}: IK lives entirely in the transient
     * field, so the FK pose (read by the gizmo, saved, and blended below) stays
     * intact.
     *
     * <p>Each bone's local rotation maps its rest frame to its solved frame, both
     * built from a segment direction and a roll-reference normal (see {@link
     * Matrices#orientMirroredX}). The normal is carried along the chain by parallel
     * transport — a minimal-twist frame, the way a bone inherits its parent's roll
     * in Blender — seeded from the solve's bend normal, so the bend sets the roll
     * and the chain follows it without per-joint flips. Rest and solved frames are
     * built the SAME way, so at rest they coincide (identity, no baseline twist).
     * The parent world frame is walked root-to-tip as the renderer would,
     * advancing by each bone's rendered (blended) orientation so children inherit
     * the same frame the renderer establishes.
     */
    static void buildChainOrientations(Model model, List<String> chainIds, List<Vector3f> solved, Quaternionf rootParentRotation, float weight, Quaternionf tipTarget, Vector3f stretchGap, Vector3f bendSeed)
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

        /* Distribute the stretch gap only up to the last bone that actually has
         * geometry: a chain ending in a bare end-marker (the reach point, like the
         * tip-rotation tail) would otherwise open a seam BEFORE the marker, leaving
         * the last VISIBLE bone short of the controller. The marker carries no
         * offset and rides the reach bone's full shift. */
        int reach = stretchGap == null ? -1 : lastGeometryIndex(model, chainIds);
        float reachTotal = 0F;

        for (int i = 0; i < reach; i++)
        {
            reachTotal += solved.get(i).distance(solved.get(i + 1));
        }

        boolean doStretch = stretchGap != null && reach >= 1 && reachTotal > EPS;

        Vector3f[] restNormal = transportNormals(restDir, null);
        Vector3f[] solvedNormal = transportNormals(segWorld, bendSeed);

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
            Quaternionf oriented = weight >= 1F - EPS ? new Quaternionf(localRot) : bone.evaluatedRotation().slerp(localRot, weight);

            bone.orient = oriented;

            /* Stretch: open the gap before this bone (the segment from its parent),
             * pushing it and everything below out along the limb. parentWorld is
             * still this bone's parent frame here, so the world gap maps into the
             * local translate the renderer applies. */
            if (doStretch && i >= 1 && i <= reach)
            {
                bone.offset = stretchOffset(stretchGap, solved.get(i - 1).distance(solved.get(i)), reachTotal, parentWorld);
            }

            /* Advance by the orientation the renderer will actually apply (the
             * blended one), so a child bone decomposes its segment against the SAME
             * parent frame the renderer establishes. */
            parentWorld.mul(oriented);
        }

        ModelGroup tip = model.getGroup(chainIds.get(chainIds.size() - 1));

        if (tip == null)
        {
            return;
        }

        /* The tip carries the last gap only when it is itself the reach bone (no
         * trailing marker): its share then completes the cumulative shift so the tip
         * lands on the controller. */
        if (doStretch && bones <= reach)
        {
            tip.offset = stretchOffset(stretchGap, solved.get(bones - 1).distance(solved.get(bones)), reachTotal, parentWorld);
        }

        /* Tip follows target: the effector (last id, not in the directed loop) copies
         * the controller's world orientation. parentWorld is now the tip's parent frame. */
        if (tipTarget != null)
        {
            Quaternionf tipLocal = new Quaternionf(parentWorld).conjugate().mul(tipTarget);

            tip.orient = weight >= 1F - EPS ? tipLocal : tip.evaluatedRotation().slerp(tipLocal, weight);
        }
    }

    /**
     * The BOBJ analogue of {@link #buildChainOrientations}: gives each BOBJ IK bone
     * a full local orientation written to {@link BOBJBone#orient}, which the
     * armature applies in place of the euler rotate. Unlike the cubic chain, BOBJ
     * bones carry a per-bone REST rotation (their {@code relBoneMat}), so the rest
     * and solved frames are walked separately: the rest frame advances by
     * {@code relBoneMat} alone, the solved frame by each bone's applied orientation
     * then {@code relBoneMat}. Both build the roll reference by parallel transport
     * in world, so at rest the two frames coincide and the orientation is identity
     * — no baseline twist. Same X-mirror as cubic ({@link Matrices#orientMirroredX}).
     */
    static void buildChainOrientationsBobj(BOBJModel model, List<String> chainIds, List<Vector3f> solved, Quaternionf rootParentRotation, float weight, Quaternionf tipTarget, Vector3f stretchGap, Vector3f bendSeed)
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

        Vector3f[] restNormalWorld = transportNormals(restDirWorld, null);
        Vector3f[] solvedNormalWorld = transportNormals(segWorld, bendSeed);

        /* Solved-pose world frame advances by each bone's applied orientation, then
         * the next bone's relBoneMat — so a child decomposes against the frame the
         * armature actually establishes (blended orientation at weight < 1). */
        Quaternionf originRot = new Quaternionf(rootParentRotation);

        for (int i = 0; i < bones; i++)
        {
            Quaternionf invOrigin = new Quaternionf(originRot).conjugate();
            Vector3f segLocal = invOrigin.transform(new Vector3f(segWorld[i]));
            Vector3f normalLocal = invOrigin.transform(new Vector3f(solvedNormalWorld[i]));
            Vector3f restNormalLocal = new Quaternionf(restFrame[i]).conjugate().transform(new Vector3f(restNormalWorld[i]));

            Quaternionf localRot = Matrices.orientMirroredX(restDir[i], restNormalLocal, segLocal, normalLocal);
            Quaternionf oriented = weight >= 1F - EPS ? new Quaternionf(localRot) : chainBones[i].evaluatedRotation().slerp(localRot, weight);

            chainBones[i].orient = oriented;

            if (i + 1 < bones)
            {
                originRot.mul(oriented).mul(relRot[i + 1]);
            }
        }

        /* Tip follows target: the effector copies the controller's world orientation.
         * Its parent frame is the last directed bone's frame advanced by its applied
         * orientation and the tip's own relBoneMat. */
        if (tipTarget != null)
        {
            BOBJBone tip = bonesMap.get(chainIds.get(chainIds.size() - 1));

            if (tip != null)
            {
                Quaternionf tipRelRot = tip.relBoneMat.getNormalizedRotation(new Quaternionf());
                Quaternionf tipParent = new Quaternionf(originRot).mul(chainBones[bones - 1].orient).mul(tipRelRot);
                Quaternionf tipLocal = tipParent.conjugate().mul(tipTarget);

                tip.orient = weight >= 1F - EPS ? new Quaternionf(tipLocal) : tip.evaluatedRotation().slerp(tipLocal, weight);
            }
        }

        if (stretchGap != null)
        {
            stretchBobj(model, bonesMap, chainIds, solved, stretchGap);
        }
    }

    /**
     * Carries a roll-reference normal along a chain of unit directions by parallel
     * transport: seeded from the bend of the first two segments, then rotated
     * minimally from each segment to the next — the same frame inheritance Blender
     * gives a bone from its parent, and it never flips the way a per-joint bend
     * normal does when a joint straightens. {@code seedHint} (when non-null) is a
     * stable bend-plane normal used to seed the roll reference where the first two
     * segments are collinear — a straightened chain — keeping the roll continuous
     * through full extension; absent both, a deterministic perpendicular.
     */
    private static Vector3f[] transportNormals(Vector3f[] dirs, Vector3f seedHint)
    {
        int m = dirs.length;
        Vector3f[] normals = new Vector3f[m];
        Vector3f seed = m >= 2 ? new Vector3f(dirs[0]).cross(dirs[1]) : new Vector3f();

        if (seed.lengthSquared() < 1.0e-10f)
        {
            Vector3f hint = seedHint == null ? null : perpendicularTo(seedHint, dirs[0]);

            normals[0] = hint != null ? hint : stablePerpendicular(dirs[0]);
        }
        else
        {
            normals[0] = seed.normalize();
        }

        for (int i = 1; i < m; i++)
        {
            Vector3f n = new Quaternionf().rotationTo(dirs[i - 1], dirs[i]).transform(new Vector3f(normals[i - 1]));

            normals[i] = n.normalize();
        }

        return normals;
    }

    /**
     * One bone's share of the stretch gap as a local translation: the world gap
     * scaled by the bone's segment length over the chain length (so longer bones
     * open wider gaps, the chain telescopes evenly), turned into {@code
     * parentWorld}'s frame — the frame the renderer's pre-translate runs in, so
     * {@link ModelGroup#offset} lands the bone in the right world spot.
     */
    private static Vector3f stretchOffset(Vector3f gap, float segLength, float total, Quaternionf parentWorld)
    {
        Vector3f share = new Vector3f(gap).mul(segLength / total);

        return new Quaternionf(parentWorld).conjugate().transform(share);
    }

    /**
     * The deepest chain bone that carries geometry — the bone whose far end should
     * land on the controller when stretching. Trailing bones with no cubes or
     * meshes are bare reach markers; they ride the reach bone rather than opening a
     * seam before themselves. Falls back to the last bone.
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
     * Telescopes a BOBJ chain past its reach: each bone gets the CUMULATIVE world
     * shift that carries its head joint towards the controller, so the last
     * DEFORMING bone lands the skin on the target and the mesh stretches smoothly
     * between bones (vertices blend the per-bone shifts). The distribution stops at
     * the last bone with skin — a trailing bare end-marker carries no vertices.
     * Written to {@link BOBJBone#offset}, which the armature folds into the
     * skinning matrix only, leaving the skeleton frames nominal.
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

    /** The deepest chain bone that deforms mesh — the BOBJ analogue of {@link #lastGeometryIndex}. */
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

    /**
     * The bone's local rest direction towards its child, taken exactly as that
     * model's renderer takes it (cubic: pivot difference; BOBJ: the renderer's own
     * {@link ModelRotationBlender#getBobjRestDirection}), so the orientation pass
     * reconstructs the same swing the renderer applies.
     */
    private static Vector3f restDirection(IModel model, List<String> chainIds, int i)
    {
        IKRig rig = IKRig.of(model);

        return rig == null ? null : rig.restDirection(chainIds, i);
    }

    /** See {@link IKRig.CubicIKRig#restDirection}. */
    static Vector3f cubicRestDirection(Model model, List<String> chainIds, int i)
    {
        ModelGroup bone = model.getGroup(chainIds.get(i));
        ModelGroup child = model.getGroup(chainIds.get(i + 1));

        if (bone == null || child == null)
        {
            return null;
        }

        return normalizeRest(new Vector3f(child.initial.translate).sub(bone.initial.translate));
    }

    /** See {@link IKRig.BobjIKRig#restDirection}. */
    static Vector3f bobjRestDirection(BOBJModel model, List<String> chainIds, int i)
    {
        BOBJBone bone = model.getArmature().bones.get(chainIds.get(i));
        BOBJBone child = model.getArmature().bones.get(chainIds.get(i + 1));

        if (bone == null)
        {
            return null;
        }

        return normalizeRest(ModelRotationBlender.getBobjRestDirection(model, bone, child, chainIds, i));
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

    /* ------------------------------------------------------------------ */
    /* Small vector helpers (legacy IKSolver)                              */
    /* ------------------------------------------------------------------ */

    /** A deterministic unit perpendicular to {@code dir}, cross with world Z (falling back to world Y). */
    private static Vector3f stablePerpendicular(Vector3f dir)
    {
        Vector3f perp = new Vector3f(dir).cross(0F, 0F, 1F);

        if (perp.lengthSquared() < EPS * EPS)
        {
            perp.set(dir).cross(0F, 1F, 0F);
        }

        return perp.normalize();
    }

    /** {@code v} projected onto the plane perpendicular to unit {@code axis}, normalized; {@code null} if degenerate. */
    private static Vector3f perpendicularTo(Vector3f v, Vector3f axis)
    {
        Vector3f out = new Vector3f(v);
        float dot = out.dot(axis);

        out.x -= axis.x * dot;
        out.y -= axis.y * dot;
        out.z -= axis.z * dot;

        return out.lengthSquared() < EPS * EPS ? null : out.normalize();
    }

    /** The component of {@code b - a} perpendicular to the line {@code a -> c}, normalized; {@code null} if degenerate. */
    private static Vector3f perpendicular(Vector3f a, Vector3f b, Vector3f c)
    {
        Vector3f axis = new Vector3f(c).sub(a);

        if (!normalize(axis))
        {
            return null;
        }

        Vector3f out = new Vector3f(b).sub(a);

        return project(out, axis) ? out : null;
    }

    private static void anyPerpendicular(Vector3f axis, Vector3f out)
    {
        Vector3f ref = Math.abs(axis.x) < 0.9F ? new Vector3f(1F, 0F, 0F) : new Vector3f(0F, 1F, 0F);

        out.set(axis).cross(ref);

        if (!normalize(out))
        {
            out.set(0F, 1F, 0F);
        }
    }

    private static float signedAngle(Vector3f from, Vector3f to, Vector3f axis)
    {
        Vector3f cross = new Vector3f(from).cross(to);
        float sin = axis.dot(cross);
        float cos = from.dot(to);

        return (float) Math.atan2(sin, cos);
    }

    private static boolean project(Vector3f v, Vector3f axis)
    {
        float dot = v.dot(axis);

        v.x -= axis.x * dot;
        v.y -= axis.y * dot;
        v.z -= axis.z * dot;

        return normalize(v);
    }

    private static boolean normalize(Vector3f v)
    {
        float lenSq = v.lengthSquared();

        if (lenSq <= EPS * EPS)
        {
            return false;
        }

        v.mul(1F / (float) Math.sqrt(lenSq));

        return true;
    }
}
