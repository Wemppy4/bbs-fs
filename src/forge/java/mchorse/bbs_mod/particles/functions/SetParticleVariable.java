package mchorse.bbs_mod.particles.functions;

import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.functions.NNFunction;
import mchorse.bbs_mod.particles.ParticleMolangParser;
import mchorse.bbs_mod.particles.emitter.Particle;

public class SetParticleVariable extends NNFunction
{
    public SetParticleVariable(MathBuilder builder, IExpression[] expressions, String name) throws Exception
    {
        super(builder, expressions, name);
    }

    @Override
    protected void verifyArgument(int index, IExpression expression)
    {}

    @Override
    public int getRequiredArguments()
    {
        return 2;
    }

    @Override
    public double doubleValue()
    {
        if (this.builder instanceof ParticleMolangParser parser && parser.scheme.particle != null)
        {
            int offset = this.args.length > 2 ? 1 : 0;
            String name = this.args[offset].stringValue();
            double value = this.args[offset + 1].doubleValue();

            Particle particle = parser.scheme.particle;

            if (this.args.length > 2)
            {
                particle = parser.scheme.emitter.getParticleByIndex((int) this.args[0].doubleValue());
            }

            if (particle == null)
            {
                return 0D;
            }

            particle.localValues.put(name, value);

            return value;
        }

        return 0;
    }
}