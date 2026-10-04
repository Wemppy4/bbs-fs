package mchorse.bbs_mod.cubic.jem.functions;

import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.functions.NNFunction;

/**
 * CEM {@code frac(x)} — the fractional part of x (what's after the decimal point).
 */
public class Frac extends NNFunction
{
    public Frac(MathBuilder builder, IExpression[] expressions, String name) throws Exception
    {
        super(builder, expressions, name);
    }

    @Override
    public int getRequiredArguments()
    {
        return 1;
    }

    @Override
    public double doubleValue()
    {
        double x = this.getArg(0).doubleValue();

        return x - Math.floor(x);
    }
}
