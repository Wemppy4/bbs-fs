package mchorse.bbs_mod.cubic.jem.functions;

import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.functions.NNFunction;

/**
 * CEM {@code lerp(k, x, y)} — linear interpolation between x and y by k. Note the argument order
 * differs from MoLang's {@code lerp(start, end, t)}, which is why CEM uses its own function.
 */
public class CemLerp extends NNFunction
{
    public CemLerp(MathBuilder builder, IExpression[] expressions, String name) throws Exception
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
        double k = this.getArg(0).doubleValue();
        double x = this.getArg(1).doubleValue();
        double y = this.getArg(2).doubleValue();

        return x + (y - x) * k;
    }
}
