package mchorse.bbs_mod.cubic.physics;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Per-chain simulation state: the Verlet particle arrays plus the bookkeeping the {@link ChainSolver}
 * carries between ticks. Owned by an {@link ModelPhysicsRuntime.InstanceState}, keyed by chain id.
 */
class ChainState
{
    public int lastAge = Integer.MIN_VALUE;
    public Vector3f anchor = new Vector3f();
    public Quaternionf anchorRotation = new Quaternionf();
    public float renderAlpha;
    public Vector3f[] pos;
    public Vector3f[] prev;

    /** Settled shapes of the two latest simulation ticks, each stored in its own tick's anchor frame. */
    public Vector3f[] settledLocal;
    public Vector3f[] settledPrevLocal;

    public Vector3f[] render;

    /** The animated pose the chain springs toward, stored relative to the live anchor frame. */
    public Vector3f[] poseLocal;

    /**
     * Deep copy of the whole simulation state — every particle array included, so the copy and the
     * original share nothing. Used by {@link ModelPhysicsRuntime#checkpoint()} to hold the sim as it
     * stood before an authoring gesture began.
     */
    public ChainState copy()
    {
        ChainState copy = new ChainState();

        copy.lastAge = this.lastAge;
        copy.anchor.set(this.anchor);
        copy.anchorRotation.set(this.anchorRotation);
        copy.renderAlpha = this.renderAlpha;
        copy.pos = copyPoints(this.pos);
        copy.prev = copyPoints(this.prev);
        copy.settledLocal = copyPoints(this.settledLocal);
        copy.settledPrevLocal = copyPoints(this.settledPrevLocal);
        copy.render = copyPoints(this.render);
        copy.poseLocal = copyPoints(this.poseLocal);

        return copy;
    }

    private static Vector3f[] copyPoints(Vector3f[] points)
    {
        if (points == null)
        {
            return null;
        }

        Vector3f[] copy = new Vector3f[points.length];

        for (int i = 0; i < points.length; i++)
        {
            copy[i] = new Vector3f(points[i]);
        }

        return copy;
    }
}
