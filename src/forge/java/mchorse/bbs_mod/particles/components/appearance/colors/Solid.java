package mchorse.bbs_mod.particles.components.appearance.colors;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.interps.Lerps;

/**
 * Solid color (not necessarily static)
 */
public class Solid extends Tint
{
    public MolangExpression r;
    public MolangExpression g;
    public MolangExpression b;
    public MolangExpression a;

    public Solid()
    {
        this.r = MolangParser.ONE;
        this.g = MolangParser.ONE;
        this.b = MolangParser.ONE;
        this.a = MolangParser.ONE;
    }

    public Solid(MolangExpression r, MolangExpression g, MolangExpression b, MolangExpression a)
    {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    public boolean isConstant()
    {
        return MolangExpression.isExpressionConstant(this.r) && MolangExpression.isExpressionConstant(this.g)
            && MolangExpression.isExpressionConstant(this.b) && MolangExpression.isExpressionConstant(this.a);
    }

    @Override
    public void compute(Particle particle)
    {
        particle.r = (float) this.r.get();
        particle.g = (float) this.g.get();
        particle.b = (float) this.b.get();
        particle.a = (float) this.a.get();
    }

    @Override
    public BaseType toData()
    {
        ListType list = new ListType();

        if (MolangExpression.isOne(this.r) && MolangExpression.isOne(this.g) && MolangExpression.isOne(this.b) && MolangExpression.isOne(this.a))
        {
            return list;
        }

        list.add(this.r.toData());
        list.add(this.g.toData());
        list.add(this.b.toData());
        list.add(this.a.toData());

        return list;
    }

    public BaseType toHexData()
    {
        int r = (int) (this.r.get() * 255) & 0xff;
        int g = (int) (this.g.get() * 255) & 0xff;
        int b = (int) (this.b.get() * 255) & 0xff;
        int a = (int) (this.a.get() * 255) & 0xff;

        String hex = "#";

        if (a < 255)
        {
            hex += StringUtils.leftPad(Integer.toHexString(a), 2, "0").toUpperCase();
        }

        hex += StringUtils.leftPad(Integer.toHexString(r), 2, "0").toUpperCase();
        hex += StringUtils.leftPad(Integer.toHexString(g), 2, "0").toUpperCase();
        hex += StringUtils.leftPad(Integer.toHexString(b), 2, "0").toUpperCase();

        return new StringType(hex);
    }

    public void lerp(Particle particle, float factor)
    {
        particle.r = Lerps.lerp(particle.r, (float) this.r.get(), factor);
        particle.g = Lerps.lerp(particle.g, (float) this.g.get(), factor);
        particle.b = Lerps.lerp(particle.b, (float) this.b.get(), factor);
        particle.a = Lerps.lerp(particle.a, (float) this.a.get(), factor);
    }
}