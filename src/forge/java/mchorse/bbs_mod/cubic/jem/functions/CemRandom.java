package mchorse.bbs_mod.cubic.jem.functions;

import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.functions.NNFunction;

/**
 * CEM {@code random(seed)} — a random number from 0 to 1. The optional seed always returns the same
 * result for the same value (useful for per-entity randomness via {@code random(id)}).
 */
public class CemRandom extends NNFunction
{
    private final java.util.Random random = new java.util.Random();

    public CemRandom(MathBuilder builder, IExpression[] expressions, String name) throws Exception
    {
        super(builder, expressions, name);
    }

    @Override
    public double doubleValue()
    {
        if (this.args.length >= 1)
        {
            this.random.setSeed((long) this.getArg(0).doubleValue());

            return this.random.nextDouble();
        }

        return Math.random();
    }
}
