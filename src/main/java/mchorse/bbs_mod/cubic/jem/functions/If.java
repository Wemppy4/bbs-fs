package mchorse.bbs_mod.cubic.jem.functions;

import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.functions.NNFunction;

/**
 * CEM {@code if(cond, val, [cond2, val2, ...], val_else)} — selects a value based on one or more
 * conditions, falling back to the trailing else value. Also serves CEM's {@code ifb} (boolean
 * variant); both just return the selected value.
 */
public class If extends NNFunction
{
    public If(MathBuilder builder, IExpression[] expressions, String name) throws Exception
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
        int length = this.args.length;

        for (int i = 0; i + 1 < length; i += 2)
        {
            if (this.getArg(i).booleanValue())
            {
                return this.getArg(i + 1).doubleValue();
            }
        }

        return this.getArg(length - 1).doubleValue();
    }
}
