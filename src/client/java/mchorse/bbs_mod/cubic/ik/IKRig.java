package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * How a solved chain becomes bone rotations, for one kind of skeleton.
 *
 * <p>The classic two-bone solve is backend-agnostic right until the last step — it works in
 * world positions — but writing the answer back is not: a cubic chain distributes stretch along
 * authored pivots, while a BOBJ chain advances rest frames through its bind matrices. Those are
 * two genuinely different procedures, not one procedure typed out twice, so this keeps them
 * apart rather than pretending they are the same.
 *
 * <p>What it removes is the TYPE TEST, which used to be repeated at every call: the question
 * "which skeleton is this" is asked once, in {@link #of}, and the answer is an object that knows
 * its own way. Mirrors {@link mchorse.bbs_mod.cubic.physics.PhysicsRig}, which drew the same
 * line for the physics solver.
 *
 * <p>The procedures themselves stay in {@link ClassicLimbSolver} next to the solve they finish —
 * moving that much numerical code would have been a rewrite, not a rename.
 */
interface IKRig
{
    /** Rest direction from the chain's i-th bone toward the next one, normalised. */
    Vector3f restDirection(List<String> chainIds, int i);

    /**
     * Writes the solved chain back onto the bones as orientations, blended by {@code weight}.
     *
     * @param stretchGap how far the effector fell short of its goal, to distribute along the
     *                   chain, or null when the chain does not stretch.
     * @param bendSeed   which way the chain should bend when the solve leaves it ambiguous.
     */
    void buildChainOrientations(List<String> chainIds, List<Vector3f> solved, Quaternionf rootParentRotation, float weight, Quaternionf tipTarget, Vector3f stretchGap, Vector3f bendSeed);

    /** The rig for that model, or null when it is neither skeleton this solver can pose. */
    static IKRig of(IModel model)
    {
        if (model instanceof Model cubic)
        {
            return new CubicIKRig(cubic);
        }

        if (model instanceof BOBJModel bobj)
        {
            return new BobjIKRig(bobj);
        }

        return null;
    }

    final class CubicIKRig implements IKRig
    {
        private final Model model;

        private CubicIKRig(Model model)
        {
            this.model = model;
        }

        @Override
        public Vector3f restDirection(List<String> chainIds, int i)
        {
            return ClassicLimbSolver.cubicRestDirection(this.model, chainIds, i);
        }

        @Override
        public void buildChainOrientations(List<String> chainIds, List<Vector3f> solved, Quaternionf rootParentRotation, float weight, Quaternionf tipTarget, Vector3f stretchGap, Vector3f bendSeed)
        {
            ClassicLimbSolver.buildChainOrientations(this.model, chainIds, solved, rootParentRotation, weight, tipTarget, stretchGap, bendSeed);
        }
    }

    final class BobjIKRig implements IKRig
    {
        private final BOBJModel model;

        private BobjIKRig(BOBJModel model)
        {
            this.model = model;
        }

        @Override
        public Vector3f restDirection(List<String> chainIds, int i)
        {
            return ClassicLimbSolver.bobjRestDirection(this.model, chainIds, i);
        }

        @Override
        public void buildChainOrientations(List<String> chainIds, List<Vector3f> solved, Quaternionf rootParentRotation, float weight, Quaternionf tipTarget, Vector3f stretchGap, Vector3f bendSeed)
        {
            ClassicLimbSolver.buildChainOrientationsBobj(this.model, chainIds, solved, rootParentRotation, weight, tipTarget, stretchGap, bendSeed);
        }
    }
}
