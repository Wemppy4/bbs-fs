package mchorse.bbs_mod.cubic.jem.functions;

import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.Operation;
import mchorse.bbs_mod.math.functions.NNFunction;

/**
 * CEM {@code in(x, val1, val2, ...)} — 1 if x equals one of the listed values, 0 otherwise.
 */
public class In extends NNFunction
{
    public In(MathBuilder builder, IExpression[] expressions, String name) throws Exception
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

        for (int i = 1; i < this.args.length; i++)
        {
            if (Operation.equals(x, this.getArg(i).doubleValue()))
            {
                return 1;
            }
        }

        return 0;
    }
}
