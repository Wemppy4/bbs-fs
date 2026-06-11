package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Single-chain IK modeled after Blender: the positions are solved to reach the
 * target (analytic for a two-bone limb, CCD otherwise), then the whole bend
 * plane is rotated about the root-to-tip axis towards the pole and offset by the
 * pole angle. With no pole the chain keeps the side it was posed towards.
 *
 * <p>When per-joint {@link Limit}s are supplied the solve runs as constrained
 * CCD: every sweep is followed by a clamp pass that reconstructs each bone's
 * local rotation exactly as the renderer does ({@link Matrices#fromToMirroredX}
 * then ZYX euler) and clamps it to the bone's rotation limits, donating the
 * remainder to the free joints — so the chain honours the limits and still
 * reaches as far as they allow.
 */
final class IKSolver
{
    private static final float EPS = 1.0e-6f;

    /* Keep the goal a hair inside reach so the chain never locks dead straight
     * (a fully extended chain has an undefined bend plane and rolls). */
    private static final float REACH_LIMIT = 0.999F;

    private IKSolver()
    {
    }

    /**
     * Per-bone rotation limit for constrained CCD. {@code restDir} is the bone's
     * local rest direction towards its child (the same vector the renderer uses
     * to reconstruct the bone's local rotation); min/max are euler ZYX degrees.
     */
    public record Limit(boolean enabled, Vector3f restDir, float minX, float minY, float minZ, float maxX, float maxY, float maxZ)
    {
    }

    public static List<Vector3f> solve(List<Vector3f> positions, Vector3f target, boolean applyPole, Vector3f polePoint, float softness, int maxIterations, float tolerance)
    {
        return solve(positions, target, applyPole, polePoint, softness, maxIterations, tolerance, null, null);
    }

    public static List<Vector3f> solve(List<Vector3f> positions, Vector3f target, boolean applyPole, Vector3f polePoint, float softness, int maxIterations, float tolerance, Limit[] limits, Quaternionf rootParentRotation)
    {
        int n = positions.size();

        if (n < 2)
        {
            return positions;
        }

        float total = 0F;

        for (int i = 0; i < n - 1; i++)
        {
            total += positions.get(i).distance(positions.get(i + 1));
        }

        if (total <= EPS)
        {
            return positions;
        }

        Vector3f root = new Vector3f(positions.get(0));
        Vector3f goal = clampReach(root, target, total, softness);
        Vector3f hinge = applyPole ? captureHingeAxis(positions) : null;

        boolean constrained = limits != null && rootParentRotation != null;

        if (n == 3)
        {
            /* Analytic is ideal for a two-bone limb — full reach, no flip, clean
             * pole control. The pole defines the hinge; limits ride on top as
             * range clamps (e.g. stop the elbow hyperextending). */
            solveTwoBone(positions, root, goal);
            orientBend(positions, hinge, polePoint);

            if (constrained)
            {
                solveBendForLimits(positions, limits, rootParentRotation);
            }
        }
        else if (constrained)
        {
            /* Longer chain: angle-space CCD keeps each joint in its DOF, then the
             * pole rotates the whole chain about root->tip — that preserves every
             * joint's local rotation, so it can't break the limits. */
            solveCCD(positions, root, goal, maxIterations, tolerance, limits, rootParentRotation);
            orientBend(positions, hinge, polePoint);
        }
        else
        {
            solveCCD(positions, root, goal, maxIterations, tolerance, null, null);
            orientBend(positions, hinge, polePoint);
        }

        return positions;
    }

    /**
     * Maps the target onto an effective reach distance. With {@code softness > 0}
     * this is "soft IK": near full extension the effective distance approaches the
     * chain length asymptotically (and C1-continuously), so the limb never snaps
     * dead straight when the target is pulled out of reach. With softness 0 it is
     * a hard clamp at {@code total * REACH_LIMIT}.
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
                goal.set(root).fma(eff, dir);
            }
        }
        else if (dist > total * REACH_LIMIT)
        {
            goal.set(root).fma(total * REACH_LIMIT, dir);
        }

        return goal;
    }

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
     * Cyclic Coordinate Descent: each sweep rotates every joint (tip-to-root) so
     * the effector aims at the goal. Rotations are rigid, so bone lengths are
     * preserved. When {@code limits} are present, each joint's rotation is
     * restricted to its allowed local DOF DURING the sweep (not clamped after),
     * so a hinge only ever moves on its free axis and the free joints naturally
     * orient the chain to reach — instead of a post-clamp fighting the solve.
     */
    private static void solveCCD(List<Vector3f> p, Vector3f root, Vector3f goal, int maxIterations, float tolerance, Limit[] limits, Quaternionf rootParentRotation)
    {
        int n = p.size();
        float tolSq = tolerance * tolerance;
        Quaternionf[] parentWorld = limits == null ? null : new Quaternionf[n];

        for (int iter = 0; iter < maxIterations; iter++)
        {
            if (p.get(n - 1).distanceSquared(goal) <= tolSq)
            {
                break;
            }

            if (limits != null)
            {
                computeParentFrames(p, limits, rootParentRotation, parentWorld);
            }

            ccdSweep(p, goal, limits, parentWorld);
            p.get(0).set(root);
        }
    }

    /**
     * Forward pass (root-to-tip) building each joint's parent world frame from
     * the current positions. During the following tip-to-root sweep a joint's
     * ancestors have not moved yet, so these frames stay valid when the joint is
     * reached — letting the per-joint limit be expressed in the correct local space.
     */
    private static void computeParentFrames(List<Vector3f> p, Limit[] limits, Quaternionf rootParentRotation, Quaternionf[] parentWorld)
    {
        int n = p.size();
        Vector3f dirWorld = new Vector3f();
        Vector3f dirLocal = new Vector3f();
        parentWorld[0] = new Quaternionf(rootParentRotation);

        for (int i = 0; i < n - 1; i++)
        {
            Quaternionf local = localRotation(p, i, limits[i], parentWorld[i], dirWorld, dirLocal);
            parentWorld[i + 1] = new Quaternionf(parentWorld[i]);

            if (local != null)
            {
                Vector3f euler = Matrices.toEulerZYXDegrees(local);
                parentWorld[i + 1].mul(Matrices.toQuaternionZYXDegrees(euler.x, euler.y, euler.z));
            }
        }
    }

    /** Reconstructs joint {@code i}'s local rotation (relative to rest) from its current world direction, the renderer's way. */
    private static Quaternionf localRotation(List<Vector3f> p, int i, Limit lim, Quaternionf parentWorld, Vector3f dirWorld, Vector3f dirLocal)
    {
        if (lim == null || lim.restDir() == null)
        {
            return null;
        }

        dirWorld.set(p.get(i + 1)).sub(p.get(i));

        if (!normalize(dirWorld))
        {
            return null;
        }

        dirLocal.set(dirWorld);
        new Quaternionf(parentWorld).conjugate().transform(dirLocal);

        if (!normalize(dirLocal))
        {
            return null;
        }

        return Matrices.fromToMirroredX(lim.restDir(), dirLocal);
    }

    private static void ccdSweep(List<Vector3f> p, Vector3f goal, Limit[] limits, Quaternionf[] parentWorld)
    {
        int n = p.size();
        Vector3f toEff = new Vector3f();
        Vector3f toGoal = new Vector3f();
        Vector3f dirWorld = new Vector3f();
        Vector3f dirLocal = new Vector3f();
        Vector3f rel = new Vector3f();

        for (int j = n - 2; j >= 0; j--)
        {
            Vector3f pj = p.get(j);

            toEff.set(p.get(n - 1)).sub(pj);
            toGoal.set(goal).sub(pj);

            if (toEff.lengthSquared() < EPS * EPS || toGoal.lengthSquared() < EPS * EPS)
            {
                continue;
            }

            Quaternionf free = new Quaternionf().rotationTo(toEff, toGoal);
            Quaternionf q = free;
            Limit lim = limits == null ? null : limits[j];

            if (lim != null && lim.enabled())
            {
                q = restrictToLimit(p, j, lim, parentWorld[j], free, dirWorld, dirLocal);
            }

            if (q == null)
            {
                continue;
            }

            for (int k = j + 1; k < n; k++)
            {
                rel.set(p.get(k)).sub(pj);
                q.transform(rel);
                p.get(k).set(pj).add(rel);
            }
        }
    }

    /**
     * Restricts a joint's free CCD rotation to its allowed local DOF: takes the
     * candidate local rotation (in the parent's frame), clamps each euler axis to
     * the bone's limits, and returns the WORLD rotation that brings the bone from
     * its current to that clamped orientation (to apply to the downstream chain).
     * A hinge (two axes locked to 0) thus only ever rotates on its free axis.
     */
    private static Quaternionf restrictToLimit(List<Vector3f> p, int j, Limit lim, Quaternionf parentWorld, Quaternionf free, Vector3f dirWorld, Vector3f dirLocal)
    {
        Quaternionf curLocal = localRotation(p, j, lim, parentWorld, dirWorld, dirLocal);

        if (curLocal == null)
        {
            return free;
        }

        Quaternionf invParent = new Quaternionf(parentWorld).conjugate();
        Quaternionf candLocal = new Quaternionf(invParent).mul(free).mul(parentWorld).mul(curLocal);
        Vector3f euler = Matrices.toEulerZYXDegrees(candLocal);

        float cx = clamp(euler.x, lim.minX(), lim.maxX());
        float cy = clamp(euler.y, lim.minY(), lim.maxY());
        float cz = clamp(euler.z, lim.minZ(), lim.maxZ());

        Quaternionf clampedLocal = Matrices.toQuaternionZYXDegrees(cx, cy, cz);
        Quaternionf curBoneWorld = new Quaternionf(parentWorld).mul(curLocal);
        Quaternionf clampedBoneWorld = new Quaternionf(parentWorld).mul(clampedLocal);

        return clampedBoneWorld.mul(curBoneWorld.conjugate());
    }

    /**
     * Range-limit refinement applied on top of a GOOD solve (analytic + pole):
     * reconstructs each bone's local rotation the renderer's way, clamps its euler
     * to the bone's limits, and rigidly carries the downstream chain. Because the
     * solve already produced a clean, reaching bend, this only bites at the limit
     * extremes (e.g. a hyperextending elbow) — it enforces the range instead of
     * fighting the reach.
     */
    /**
     * Constrained two-bone bend: instead of clamping the elbow's angle after the
     * solve (which would drag the tip off the goal), this searches the BEND
     * DIRECTION — the rotation of the interior joints about the root-to-tip axis,
     * which keeps the tip exactly on the goal — for the orientation where the
     * joints best obey their euler limits. A locked axis (hinge) thus lands the
     * bend in its allowed plane while the hand stays on target; a range limit only
     * nudges the bend when it is actually exceeded, otherwise the pose/pole bend is
     * kept (the cost tie-breaks toward no change). Reach is preserved either way.
     */
    private static void solveBendForLimits(List<Vector3f> p, Limit[] limits, Quaternionf rootParentRotation)
    {
        int n = p.size();

        if (n < 3)
        {
            return;
        }

        Vector3f root = new Vector3f(p.get(0));
        Vector3f axis = new Vector3f(p.get(n - 1)).sub(root);

        if (!normalize(axis))
        {
            return;
        }

        float bestPhi = 0F;
        float bestCost = bendCost(p, limits, rootParentRotation, root, axis, 0F);

        if (bestCost > EPS)
        {
            /* Search ONLY the half-circle on the pole side (|phi| <= 90deg). The
             * two limit-compliant bends are 180deg apart, so exactly one falls in
             * this window — the one on the side the pole intended. The inverted
             * bend (~180deg away) is excluded, so the solution can't snap to it
             * frame-to-frame (that flicker was the bug). */
            int half = 24;
            float window = (float) (Math.PI / 2.0);

            for (int s = -half; s <= half; s++)
            {
                if (s == 0)
                {
                    continue;
                }

                float phi = window * s / half;
                float cost = bendCost(p, limits, rootParentRotation, root, axis, phi);

                if (cost < bestCost)
                {
                    bestCost = cost;
                    bestPhi = phi;
                }
            }

            /* Refine within one coarse step of the best, staying in the window. */
            float step = window / half;
            float center = bestPhi;

            for (int s = -5; s <= 5; s++)
            {
                float phi = center + step * s / 5F;

                if (Math.abs(phi) > window)
                {
                    continue;
                }

                float cost = bendCost(p, limits, rootParentRotation, root, axis, phi);

                if (cost < bestCost)
                {
                    bestCost = cost;
                    bestPhi = phi;
                }
            }
        }

        if (Math.abs(bestPhi) > EPS)
        {
            Quaternionf q = new Quaternionf().fromAxisAngleRad(axis.x, axis.y, axis.z, bestPhi);
            Vector3f rel = new Vector3f();

            for (int i = 1; i < n - 1; i++)
            {
                rel.set(p.get(i)).sub(root);
                q.transform(rel);
                p.get(i).set(root).add(rel);
            }
        }
    }

    /** Limit violation (degrees) at bend angle {@code phi}, plus a tiny penalty for moving (keeps the pole bend when limits already hold). */
    private static float bendCost(List<Vector3f> p, Limit[] limits, Quaternionf rootParentRotation, Vector3f root, Vector3f axis, float phi)
    {
        return bendViolation(p, limits, rootParentRotation, root, axis, phi) + 0.01F * Math.abs((float) Math.toDegrees(phi));
    }

    /** Sum of how far each constrained joint's euler is outside its limits, if the bend were rotated by {@code phi} about {@code axis}. */
    private static float bendViolation(List<Vector3f> p, Limit[] limits, Quaternionf rootParentRotation, Vector3f root, Vector3f axis, float phi)
    {
        int n = p.size();
        Quaternionf q = new Quaternionf().fromAxisAngleRad(axis.x, axis.y, axis.z, phi);
        Quaternionf parentWorld = new Quaternionf(rootParentRotation);
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f dirWorld = new Vector3f();
        Vector3f dirLocal = new Vector3f();
        Vector3f rel = new Vector3f();
        float violation = 0F;

        for (int i = 0; i < n - 1; i++)
        {
            Limit lim = i < limits.length ? limits[i] : null;

            if (lim == null || lim.restDir() == null)
            {
                continue;
            }

            rotatedJoint(p, i, root, q, rel, a);
            rotatedJoint(p, i + 1, root, q, rel, b);
            dirWorld.set(b).sub(a);

            if (!normalize(dirWorld))
            {
                continue;
            }

            dirLocal.set(dirWorld);
            new Quaternionf(parentWorld).conjugate().transform(dirLocal);

            if (!normalize(dirLocal))
            {
                continue;
            }

            Vector3f euler = Matrices.toEulerZYXDegrees(Matrices.fromToMirroredX(lim.restDir(), dirLocal));

            if (lim.enabled())
            {
                violation += overflow(euler.x, lim.minX(), lim.maxX());
                violation += overflow(euler.y, lim.minY(), lim.maxY());
                violation += overflow(euler.z, lim.minZ(), lim.maxZ());
            }

            parentWorld.mul(Matrices.toQuaternionZYXDegrees(euler.x, euler.y, euler.z));
        }

        return violation;
    }

    private static void rotatedJoint(List<Vector3f> p, int i, Vector3f root, Quaternionf q, Vector3f tmp, Vector3f out)
    {
        if (i == 0 || i == p.size() - 1)
        {
            out.set(p.get(i));

            return;
        }

        tmp.set(p.get(i)).sub(root);
        q.transform(tmp);
        out.set(root).add(tmp);
    }

    private static float overflow(float value, float min, float max)
    {
        if (min > max)
        {
            float t = min;
            min = max;
            max = t;
        }

        if (value < min)
        {
            return min - value;
        }

        return value > max ? value - max : 0F;
    }

    /**
     * Rotates the whole chain about the root-to-tip axis so the bend points at
     * the pole target. With no pole target it instead keeps the captured hinge
     * orientation (the limb behaves like a hinge and never inverts). Either way
     * the bend DIRECTION is aimed: at the pole point's perpendicular projection,
     * or — for the hinge — at {@code axis x hinge}, which is the bend direction
     * matching the bend-plane normal the hinge represents (so it stays
     * perpendicular to the limb as it swings and can't flip). The root and tip
     * lie on the axis, so reach is preserved.
     */
    private static void orientBend(List<Vector3f> p, Vector3f hinge, Vector3f polePoint)
    {
        int n = p.size();

        if (n < 3)
        {
            return;
        }

        Vector3f root = p.get(0);
        Vector3f axis = new Vector3f(p.get(n - 1)).sub(root);

        if (!normalize(axis))
        {
            return;
        }

        /* Current bend direction = where the elbow points off the limb axis. */
        Vector3f current = new Vector3f(p.get(1)).sub(root);

        if (!project(current, axis))
        {
            return;
        }

        Vector3f desired = new Vector3f();

        if (polePoint != null)
        {
            /* Aim the elbow at the pole point — a stable external reference, so
             * the bend can't flip as the target swings (the whole point of a
             * pole). Degenerate only when the pole lies on the limb axis. */
            desired.set(polePoint).sub(root);

            if (!project(desired, axis))
            {
                return;
            }
        }
        else if (hinge != null)
        {
            /* No pole target: bend direction derived from the captured hinge
             * normal. Degenerate only when the limb points along the hinge
             * (rare) — then we hold the current bend instead of flipping. */
            desired.set(axis).cross(hinge);

            if (!project(desired, axis))
            {
                return;
            }
        }
        else
        {
            return;
        }

        float theta = signedAngle(current, desired, axis);

        if (Math.abs(theta) < EPS)
        {
            return;
        }

        Quaternionf q = new Quaternionf().fromAxisAngleRad(axis.x, axis.y, axis.z, theta);
        Vector3f rel = new Vector3f();

        for (int i = 1; i < n - 1; i++)
        {
            rel.set(p.get(i)).sub(root);
            q.transform(rel);
            p.get(i).set(root).add(rel);
        }
    }

    /**
     * The hinge axis: the side direction the limb bends around. When the limb is
     * posed bent, it is the normal of that bend plane, {@code (elbow-root) x
     * (tip-root)}. When the limb is straight (no posed plane — common for rest
     * rigs), it falls back to the limb's side axis {@code limbDir x worldForward}
     * (or x worldUp), which is a fixed direction independent of the target, so
     * locking the bend to it never flips. Null only for a degenerate chain.
     */
    private static Vector3f captureHingeAxis(List<Vector3f> p)
    {
        int n = p.size();

        if (n < 3)
        {
            return null;
        }

        Vector3f a = p.get(0);
        Vector3f normal = new Vector3f(p.get(1)).sub(a).cross(new Vector3f(p.get(2)).sub(a));

        if (normalize(normal))
        {
            return normal;
        }

        /* Straight limb: derive a stable side axis from the limb direction. */
        Vector3f limb = new Vector3f(p.get(n - 1)).sub(a);

        if (!normalize(limb))
        {
            return null;
        }

        Vector3f hinge = new Vector3f(limb).cross(0F, 0F, 1F);

        if (normalize(hinge))
        {
            return hinge;
        }

        hinge = new Vector3f(limb).cross(0F, 1F, 0F);

        return normalize(hinge) ? hinge : null;
    }

    private static float clamp(float value, float min, float max)
    {
        if (min > max)
        {
            float t = min;
            min = max;
            max = t;
        }

        return value < min ? min : Math.min(value, max);
    }

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
