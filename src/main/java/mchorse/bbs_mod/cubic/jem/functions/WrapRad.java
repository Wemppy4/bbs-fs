package mchorse.bbs_mod.cubic.jem.functions;

import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.functions.NNFunction;

/**
 * CEM {@code wraprad(x)} — wrap an angle (in radians) into the range (-pi, pi].
 */
public class WrapRad extends NNFunction
{
    public WrapRad(MathBuilder builder, IExpression[] expressions, String name) throws Exception
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

        return Math.atan2(Math.sin(x), Math.cos(x));
    }
}
