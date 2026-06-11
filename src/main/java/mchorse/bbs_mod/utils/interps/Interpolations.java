package mchorse.bbs_mod.utils.interps;

import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.easings.Easings;
import mchorse.bbs_mod.utils.interps.types.BaseInterp;
import mchorse.bbs_mod.utils.interps.types.EasingInterp;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;

import java.util.LinkedHashMap;
import java.util.Map;

public class Interpolations
{
    public static final Map<String, IInterp> MAP = new LinkedHashMap<>();

    public static final IInterp LINEAR = new EasingInterp("linear", GLFW.GLFW_KEY_L, Easings.LINEAR);
    public static final IInterp CONST = new EasingInterp("constant", GLFW.GLFW_KEY_T, Easings.CONST);
    public static final IInterp STEP = new BaseInterp("step", GLFW.GLFW_KEY_P)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double steps = Math.floor(Math.max(1, context.args.v1));

            if (steps <= 1)
            {
                return context.a;
            }

            double x = context.x;
            double easing = MathUtils.clamp(context.args.v2, -1D, 1D);
            double function = context.args.v3;

            if (easing > 0) x = Lerps.lerp(x, Easings.EXP.calculate(null, x), easing);
            if (easing < 0) x = Lerps.lerp(x, 1F - Easings.EXP.calculate(null, 1F - x), -easing);

            if (function > 0) x = Math.ceil(x * steps) / steps;
            else if (function < 0) x = Math.round(x * steps) / steps;
            else x = Math.floor(x * steps) / steps;

            return Lerps.lerp(context.a, context.b, x);
        }
    };

    public static final IInterp SINE_IN = new EasingInterp("sine_in", GLFW.GLFW_KEY_I, Easings.SINE);
    public static final IInterp SINE_OUT = new EasingInterp("sine_out", GLFW.GLFW_KEY_I, Easings.out(Easings.SINE));
    public static final IInterp SINE_INOUT = new EasingInterp("sine_inout", GLFW.GLFW_KEY_I, Easings.inOut(Easings.SINE));

    public static final IInterp CIRCLE_IN = new EasingInterp("circle_in", GLFW.GLFW_KEY_R, Easings.CIRCLE);
    public static final IInterp CIRCLE_OUT = new EasingInterp("circle_out", GLFW.GLFW_KEY_R, Easings.out(Easings.CIRCLE));
    public static final IInterp CIRCLE_INOUT = new EasingInterp("circle_inout", GLFW.GLFW_KEY_R, Easings.inOut(Easings.CIRCLE));

    public static final IInterp QUAD_IN = new EasingInterp("quad_in", GLFW.GLFW_KEY_Q, Easings.QUADRATIC);
    public static final IInterp QUAD_OUT = new EasingInterp("quad_out", GLFW.GLFW_KEY_Q, Easings.out(Easings.QUADRATIC));
    public static final IInterp QUAD_INOUT = new EasingInterp("quad_inout", GLFW.GLFW_KEY_Q, Easings.inOut(Easings.QUADRATIC));

    public static final IInterp CUBIC_IN = new EasingInterp("cubic_in", GLFW.GLFW_KEY_C, Easings.CUBIC);
    public static final IInterp CUBIC_OUT = new EasingInterp("cubic_out", GLFW.GLFW_KEY_C, Easings.out(Easings.CUBIC));
    public static final IInterp CUBIC_INOUT = new EasingInterp("cubic_inout", GLFW.GLFW_KEY_C, Easings.inOut(Easings.CUBIC));

    public static final IInterp QUART_IN = new EasingInterp("quart_in", GLFW.GLFW_KEY_U, Easings.QUARTIC);
    public static final IInterp QUART_OUT = new EasingInterp("quart_out", GLFW.GLFW_KEY_U, Easings.out(Easings.QUARTIC));
    public static final IInterp QUART_INOUT = new EasingInterp("quart_inout", GLFW.GLFW_KEY_U, Easings.inOut(Easings.QUARTIC));

    public static final IInterp QUINT_IN = new EasingInterp("quint_in", GLFW.GLFW_KEY_N, Easings.QUINTIC);
    public static final IInterp QUINT_OUT = new EasingInterp("quint_out", GLFW.GLFW_KEY_N, Easings.out(Easings.QUINTIC));
    public static final IInterp QUINT_INOUT = new EasingInterp("quint_inout", GLFW.GLFW_KEY_N, Easings.inOut(Easings.QUINTIC));

    public static final IInterp EXP_IN = new EasingInterp("exp_in", GLFW.GLFW_KEY_E, Easings.EXP);
    public static final IInterp EXP_OUT = new EasingInterp("exp_out", GLFW.GLFW_KEY_E, Easings.out(Easings.EXP));
    public static final IInterp EXP_INOUT = new EasingInterp("exp_inout", GLFW.GLFW_KEY_E, Easings.inOut(Easings.EXP));

    public static final IInterp BACK_IN = new EasingInterp("back_in", GLFW.GLFW_KEY_B, Easings.BACK);
    public static final IInterp BACK_OUT = new EasingInterp("back_out", GLFW.GLFW_KEY_B, Easings.out(Easings.BACK));
    public static final IInterp BACK_INOUT = new EasingInterp("back_inout", GLFW.GLFW_KEY_B, Easings.inOut(Easings.BACK));

    public static final IInterp ELASTIC_IN = new EasingInterp("elastic_in", GLFW.GLFW_KEY_S, Easings.ELASTIC);
    public static final IInterp ELASTIC_OUT = new EasingInterp("elastic_out", GLFW.GLFW_KEY_S, Easings.out(Easings.ELASTIC));
    public static final IInterp ELASTIC_INOUT = new EasingInterp("elastic_inout", GLFW.GLFW_KEY_S, Easings.inOut(Easings.ELASTIC));

    public static final IInterp BOUNCE_IN = new EasingInterp("bounce_in", GLFW.GLFW_KEY_O, Easings.BOUNCE);
    public static final IInterp BOUNCE_OUT = new EasingInterp("bounce_out", GLFW.GLFW_KEY_O, Easings.out(Easings.BOUNCE));
    public static final IInterp BOUNCE_INOUT = new EasingInterp("bounce_inout", GLFW.GLFW_KEY_O, Easings.inOut(Easings.BOUNCE));

    public static final IInterp CUBIC = new BaseInterp("cubic", GLFW.GLFW_KEY_K)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            return Lerps.cubic(context.a0 + context.args.v1, context.a, context.b, context.b0 + context.args.v2, context.x);
        }
    };

    public static final IInterp HERMITE = new BaseInterp("hermite", GLFW.GLFW_KEY_H)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            return Lerps.cubicHermite(context.a0, context.a, context.b, context.b0, context.x);
        }
    };

    public static final IInterp BEZIER = new BaseInterp("bezier", GLFW.GLFW_KEY_Z)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            return Lerps.cubicHermite(context.a0, context.a, context.b, context.b0, context.x);
        }
    };

    public static final IInterp AUTO = new BaseInterp("auto", GLFW.GLFW_KEY_A)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            return AutoBezier.get(context.a0, context.a, context.b, context.b0, 0, 1, 2, 3, false, (float) context.x);
        }
    };

    public static final IInterp AUTO_CLAMPED = new BaseInterp("auto_clamped", GLFW.GLFW_KEY_D)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            return AutoBezier.get(context.a0, context.a, context.b, context.b0, 0, 1, 2, 3, true, (float) context.x);
        }
    };

    public static final IInterp BSPLINE = new BaseInterp("bspline", GLFW.GLFW_KEY_J)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double a0 = context.a0;
            double b0 = context.b0;

            if (context.isStart) a0 = 2 * context.a - context.b;
            if (context.isEnd) b0 = 2 * context.b - context.a;

            return Lerps.bSpline(a0, context.a, context.b, b0, context.x);
        }
    };

    static
    {
        MAP.put(LINEAR.getKey(), LINEAR);
        MAP.put(CONST.getKey(), CONST);
        MAP.put(STEP.getKey(), STEP);
        MAP.put(SINE_IN.getKey(), SINE_IN);
        MAP.put(SINE_OUT.getKey(), SINE_OUT);
        MAP.put(SINE_INOUT.getKey(), SINE_INOUT);
        MAP.put(CIRCLE_IN.getKey(), CIRCLE_IN);
        MAP.put(CIRCLE_OUT.getKey(), CIRCLE_OUT);
        MAP.put(CIRCLE_INOUT.getKey(), CIRCLE_INOUT);
        MAP.put(QUAD_IN.getKey(), QUAD_IN);
        MAP.put(QUAD_OUT.getKey(), QUAD_OUT);
        MAP.put(QUAD_INOUT.getKey(), QUAD_INOUT);
        MAP.put(CUBIC_IN.getKey(), CUBIC_IN);
        MAP.put(CUBIC_OUT.getKey(), CUBIC_OUT);
        MAP.put(CUBIC_INOUT.getKey(), CUBIC_INOUT);
        MAP.put(QUART_IN.getKey(), QUART_IN);
        MAP.put(QUART_OUT.getKey(), QUART_OUT);
        MAP.put(QUART_INOUT.getKey(), QUART_INOUT);
        MAP.put(QUINT_IN.getKey(), QUINT_IN);
        MAP.put(QUINT_OUT.getKey(), QUINT_OUT);
        MAP.put(QUINT_INOUT.getKey(), QUINT_INOUT);
        MAP.put(EXP_IN.getKey(), EXP_IN);
        MAP.put(EXP_OUT.getKey(), EXP_OUT);
        MAP.put(EXP_INOUT.getKey(), EXP_INOUT);
        MAP.put(BACK_IN.getKey(), BACK_IN);
        MAP.put(BACK_OUT.getKey(), BACK_OUT);
        MAP.put(BACK_INOUT.getKey(), BACK_INOUT);
        MAP.put(ELASTIC_IN.getKey(), ELASTIC_IN);
        MAP.put(ELASTIC_OUT.getKey(), ELASTIC_OUT);
        MAP.put(ELASTIC_INOUT.getKey(), ELASTIC_INOUT);
        MAP.put(BOUNCE_IN.getKey(), BOUNCE_IN);
        MAP.put(BOUNCE_OUT.getKey(), BOUNCE_OUT);
        MAP.put(BOUNCE_INOUT.getKey(), BOUNCE_INOUT);
        MAP.put(CUBIC.getKey(), CUBIC);
        MAP.put(HERMITE.getKey(), HERMITE);
        MAP.put(BEZIER.getKey(), BEZIER);
        MAP.put(AUTO.getKey(), AUTO);
        MAP.put(AUTO_CLAMPED.getKey(), AUTO_CLAMPED);
        MAP.put(BSPLINE.getKey(), BSPLINE);
    }

    public static IInterp get(String name)
    {
        return MAP.getOrDefault(name, LINEAR);
    }
}