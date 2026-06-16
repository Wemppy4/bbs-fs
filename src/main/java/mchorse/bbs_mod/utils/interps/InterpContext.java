package mchorse.bbs_mod.utils.interps;

import mchorse.bbs_mod.utils.interps.easings.EasingArgs;

public class InterpContext
{
    public double a;
    public double b;
    public double x;

    /* Hermite */
    public double a0;
    public double b0;
    public boolean isStart;
    public boolean isEnd;

    /* Extra variables */
    public final EasingArgs args = new EasingArgs();

    /* NURBS Weights */
    public double w0;
    public double w1;
    public double w2;
    public double w3;

    public InterpContext set(double a, double b, double x)
    {
        return this.set(a, a, b, b, x);
    }

    public InterpContext set(double a0, double a, double b, double b0, double x)
    {
        this.a0 = a0;
        this.a = a;
        this.b = b;
        this.b0 = b0;
        this.x = x;
        this.isStart = false;
        this.isEnd = false;

        this.args.v1 = this.args.v2 = this.args.v3 = this.args.v4 = 0D;
        this.w0 = this.w1 = this.w2 = this.w3 = 1.0D;

        return this;
    }

    public InterpContext extra(EasingArgs args)
    {
        this.args.v1 = args.v1;
        this.args.v2 = args.v2;
        this.args.v3 = args.v3;
        this.args.v4 = args.v4;

        return this;
    }

    public InterpContext extra(double v1, double v2, double v3, double v4)
    {
        this.args.v1 = v1;
        this.args.v2 = v2;
        this.args.v3 = v3;
        this.args.v4 = v4;

        return this;
    }

    public EasingArgs getArgs()
    {
        return this.args;
    }
}