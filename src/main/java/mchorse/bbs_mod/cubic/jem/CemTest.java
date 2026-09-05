package mchorse.bbs_mod.cubic.jem;

/**
 * Standalone manual sanity test for the CEM expression engine ({@link CemParser}).
 *
 * <p>It needs no Minecraft — just run this class's {@link #main(String[])} from your IDE. It is a
 * throwaway developer aid (not used by the mod at runtime); feel free to add your own expressions or
 * delete the file. Add value checks with {@link #check}, parse-only checks with {@link #parses}, and
 * set variables (as the animation runtime will each frame) with {@code parser.setValue(name, value)}.</p>
 */
public class CemTest
{
    private static final CemParser PARSER = new CemParser();
    private static int fails = 0;

    public static void main(String[] args)
    {
        PARSER.setValue("limb_swing", 0);
        PARSER.setValue("part.rx", -100);
        PARSER.setValue("leg4:Hoof.rx", 120);

        System.out.println("--- value checks ---");
        check("3 * sin(limb_swing / 4) - 2", -2);
        check("clamp(-0.5 * part.rx, 0, 90)", 50);
        check("if(leg4:Hoof.rx > 90, leg4:Hoof.rx - 90, 0)", 30);
        check("torad(180)", Math.PI);
        check("todeg(pi)", 180);
        check("lerp(0.5, 10, 20)", 15);
        check("fmod(-1, 3)", 2);
        check("in(2, 0, 1, 2)", 1);
        check("between(0.5, 0, 1)", 1);
        check("abs(-4) + max(1,2,3) + min(5,2)", 9);
        check("signum(-3.2)", -1);
        check("frac(2.75)", 0.75);
        check("equals(1.0, 1.00001, 0.001)", 1);
        check("!false && true", 1);
        check("2 ^ 10", 1024);
        check("wraprad(pi*3)", Math.PI);

        System.out.println("\n--- real Fresh Animations expressions (must parse, finite) ---");
        parses("if(varb.21_2_plus,var.root_ty-(root.sy-1)*24,0)");
        parses("torad( +12*sin( var.headpitch_drag/180*pi ) +sin(var.Bt) )*var.idle");
        parses("clamp( if( varb.fcc, var.root_angle, is_swimming || is_gliding, var.root_angle +1.6 *frame_time, if( var.gliding>0, 0, var.root_angle -1.6 *frame_time ) ), 0, 1 )");
        parses("is_swinging_right_arm && is_using_item &&( is_blocking || ( is_right_handed&&nbt(SelectedItem.id, raw:iregex:.*shield.*)) )");
        parses("var.r +age/(11.5-2*random(id))");

        System.out.println("\n--- instance clock (CemState.advance) ---");
        CemState state = new CemState(1);

        state.values[0] = 7;
        clock("first sight", state.advance(10.0), 0);
        clock("one tick forward", state.advance(11.0), 0.05);
        clock("same frame again", state.advance(11.0), 0);
        clock("same tick, earlier partial (a resample)", state.advance(10.5), 0);
        flag("resample keeps the state", state.values[0] == 7 && state.frameCounter == 2);
        clock("next tick, stepped from the real last frame", state.advance(12.0), 0.05);
        clock("scrub back", state.advance(5.0), 0);
        flag("scrub back re-seeds", state.values[0] == 0 && state.frameCounter == 1);
        state.values[0] = 3;
        clock("jump forward past the catch-up limit", state.advance(20.0), 0);
        flag("jump re-seeds", state.values[0] == 0 && state.frameCounter == 1);
        clock("quarter tick", state.advance(20.25), 0.0125);
        clock("a whole catch-up window is still stepped", state.advance(24.25), 0.2);

        System.out.println(fails == 0 ? "\n=== ALL PASS ===" : "\n=== " + fails + " FAILED ===");
    }

    private static void clock(String label, double frameTime, double expected)
    {
        flag(String.format("%s: frame_time %.4f (want %.4f)", label, frameTime, expected), Math.abs(frameTime - expected) < 1e-9);
    }

    private static void flag(String label, boolean ok)
    {
        if (!ok)
        {
            fails += 1;
        }

        System.out.printf("%s  %s%n", ok ? "OK  " : "FAIL", label);
    }

    private static void check(String expr, double expected)
    {
        double value = PARSER.parseExpression(expr).get().doubleValue();
        boolean ok = Math.abs(value - expected) < 1e-4;

        if (!ok)
        {
            fails += 1;
        }

        System.out.printf("%-50s = %-11.5f (want %-9.5f) %s%n", expr, value, expected, ok ? "OK" : "FAIL");
    }

    private static void parses(String expr)
    {
        double value = PARSER.parseExpression(expr).get().doubleValue();
        boolean ok = !Double.isNaN(value) && !Double.isInfinite(value);

        if (!ok)
        {
            fails += 1;
        }

        System.out.printf("%s  %.4f  %s%n", ok ? "OK  " : "FAIL", value, expr.length() > 60 ? expr.substring(0, 60) + "..." : expr);
    }
}
