package mchorse.bbs_mod.cubic.jem.functions;

import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.functions.NNFunction;

/**
 * CEM {@code max(x, y, ...)} — the largest of a variadic list of numbers (BBS's built-in {@code max}
 * only takes two arguments).
 */
public class CemMax extends NNFunction
{
    public CemMax(MathBuilder builder, IExpression[] expressions, String name) throws Exception
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
        double max = this.getArg(0).doubleValue();

        for (int i = 1; i < this.args.length; i++)
        {
            max = Math.max(max, this.getArg(i).doubleValue());
        }

        return max;
    }
}
