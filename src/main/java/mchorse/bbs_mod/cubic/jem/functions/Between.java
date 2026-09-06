package mchorse.bbs_mod.cubic.jem.functions;

import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.functions.NNFunction;

/**
 * CEM {@code between(x, min, max)} — 1 if x is within [min, max], 0 otherwise.
 */
public class Between extends NNFunction
{
    public Between(MathBuilder builder, IExpression[] expressions, String name) throws Exception
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
        double x = this.getArg(0).doubleValue();

        return x >= this.getArg(1).doubleValue() && x <= this.getArg(2).doubleValue() ? 1 : 0;
    }
}
