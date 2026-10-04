package mchorse.bbs_mod.cubic.jem.functions;

import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.functions.NNFunction;

/**
 * CEM {@code equals(x, y, epsilon)} — 1 if x and y are within epsilon of each other, 0 otherwise.
 */
public class EqualsEpsilon extends NNFunction
{
    public EqualsEpsilon(MathBuilder builder, IExpression[] expressions, String name) throws Exception
    {
        super(builder, expressions, name);
    }

    @Override
    public int getRequiredArguments()
    {
        return 3;
    }

    @Override
    public double doubleValue()
    {
        double diff = this.getArg(0).doubleValue() - this.getArg(1).doubleValue();

        return Math.abs(diff) <= this.getArg(2).doubleValue() ? 1 : 0;
    }
}
