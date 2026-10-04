package mchorse.bbs_mod.cubic.jem.functions;

import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.functions.NNFunction;

/**
 * CEM {@code min(x, y, ...)} — the smallest of a variadic list of numbers (BBS's built-in {@code min}
 * only takes two arguments).
 */
public class CemMin extends NNFunction
{
    public CemMin(MathBuilder builder, IExpression[] expressions, String name) throws Exception
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
        double min = this.getArg(0).doubleValue();

        for (int i = 1; i < this.args.length; i++)
        {
            min = Math.min(min, this.getArg(i).doubleValue());
        }

        return min;
    }
}
