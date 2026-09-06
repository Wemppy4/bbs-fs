package mchorse.bbs_mod.cubic.jem.functions;

import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.functions.NNFunction;

/**
 * CEM {@code fmod(x, y)} — like the {@code %} operator, but the result always takes the sign of the
 * divisor y.
 */
public class Fmod extends NNFunction
{
    public Fmod(MathBuilder builder, IExpression[] expressions, String name) throws Exception
    {
        super(builder, expressions, name);
    }

    @Override
    public int getRequiredArguments()
    {
        return 2;
    }

    @Override
    public double doubleValue()
    {
        double x = this.getArg(0).doubleValue();
        double y = this.getArg(1).doubleValue();

        if (y == 0)
        {
            return 0;
        }

        return x - y * Math.floor(x / y);
    }
}
