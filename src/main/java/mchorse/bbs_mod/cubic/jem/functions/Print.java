package mchorse.bbs_mod.cubic.jem.functions;

import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.functions.NNFunction;

/**
 * CEM {@code print(id, n, x)} / {@code printb(id, n, x)} — a debugging passthrough that returns x.
 * The actual periodic console printing is intentionally omitted to avoid log spam; the value still
 * flows through so expressions relying on it keep working.
 */
public class Print extends NNFunction
{
    public Print(MathBuilder builder, IExpression[] expressions, String name) throws Exception
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
        return this.getArg(this.args.length - 1).doubleValue();
    }
}
