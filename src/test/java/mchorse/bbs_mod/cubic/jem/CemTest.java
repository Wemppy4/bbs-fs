package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.math.Variable;

import java.util.Collections;
import java.util.List;

/**
 * Standalone manual sanity test for the CEM expression engine ({@link CemParser}) and the instance
 * clock ({@link CemState}).
 *
 * <p>It needs no Minecraft — run {@link #main(String[])} from your IDE, or after a build:
 * {@code java -cp "build/classes/java/main;build/classes/java/test" mchorse.bbs_mod.cubic.jem.CemTest}.
 * It lives in the test source set so it never ships in the jar. Add value checks with {@link #check},
 * parse-only checks with {@link #parses}, and set variables (as the animation runtime will each frame)
 * with {@code parser.setValue(name, value)}.</p>
 */
public class CemTest
{
    private static final String SHARED_HEADER = System.lineSeparator() + "--- entity variables are shared between models (CemVariables) ---";

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

        System.out.println("\n--- nbt() is cut out whole, parentheses balanced ---");
        check("1 + nbt(SelectedItem.id, raw:iregex:.*(shield).*) * 5", 1);
        check("nbt(a, (b, (c))) + 2", 2);
        check("if(nbt(x), 7, 3)", 3);
        check("nbt(unterminated, (oops", 0);

        System.out.println("\n--- real Fresh Animations expressions (must parse, finite) ---");
        parses("if(varb.21_2_plus,var.root_ty-(root.sy-1)*24,0)");
        parses("torad( +12*sin( var.headpitch_drag/180*pi ) +sin(var.Bt) )*var.idle");
        parses("clamp( if( varb.fcc, var.root_angle, is_swimming || is_gliding, var.root_angle +1.6 *frame_time, if( var.gliding>0, 0, var.root_angle -1.6 *frame_time ) ), 0, 1 )");
        parses("is_swinging_right_arm && is_using_item &&( is_blocking || ( is_right_handed&&nbt(SelectedItem.id, raw:iregex:.*shield.*)) )");
        parses("var.r +age/(11.5-2*random(id))");

        System.out.println("\n--- instance clock (CemState.advance) ---");
        CemVariables variables = new CemVariables();
        CemState state = new CemState(variables);
        List<Variable> slots = Collections.singletonList(new Variable("var.drag", 0));

        put(state, slots, 7);
        clock("first sight", state.advance(10.0), 0);
        clock("one tick forward", state.advance(11.0), 0.05);
        clock("same frame again", state.advance(11.0), 0);
        clock("same tick, earlier partial (a resample)", state.advance(10.5), 0);
        flag("resample keeps the state", get(state, slots) == 7 && state.frameCounter == 2);
        clock("next tick, stepped from the real last frame", state.advance(12.0), 0.05);
        clock("scrub back", state.advance(5.0), 0);
        flag("scrub back re-seeds", get(state, slots) == 0 && state.frameCounter == 1);
        put(state, slots, 3);
        clock("jump forward past the catch-up limit", state.advance(20.0), 0);
        flag("jump re-seeds", get(state, slots) == 0 && state.frameCounter == 1);
        clock("quarter tick", state.advance(20.25), 0.0125);
        clock("a whole catch-up window is still stepped", state.advance(24.25), 0.2);

        System.out.println(SHARED_HEADER);

        CemVariables entity = new CemVariables();
        CemState bodyClock = new CemState(entity);
        CemState capeClock = new CemState(entity);

        /* Two programs, each with its own Variable object for the same name: a body publishing a value
         * and a cape of the same entity reading it, which is all Fresh Moves' cape has to go on. */
        List<Variable> body = Collections.singletonList(new Variable("var.player_body_rx", 0));
        List<Variable> cape = Collections.singletonList(new Variable("var.player_body_rx", 0));

        put(bodyClock, body, 0.42);
        capeClock.load(cape);
        flag("a model reads what another model of the entity wrote", cape.get(0).doubleValue() == 0.42);

        CemState alone = new CemState(new CemVariables());
        List<Variable> stranger = Collections.singletonList(new Variable("var.player_body_rx", 0));

        alone.load(stranger);
        flag("a store of its own stays empty", stranger.get(0).doubleValue() == 0);

        bodyClock.advance(5.0);
        bodyClock.advance(1.0);
        capeClock.load(cape);
        flag("a re-seed clears the whole entity, cape included", cape.get(0).doubleValue() == 0);

        System.out.println(fails == 0 ? "\n=== ALL PASS ===" : "\n=== " + fails + " FAILED ===");
    }

    /** Write a value into the state's store, the way a frame's statements leave one behind. */
    private static void put(CemState state, List<Variable> slots, double value)
    {
        slots.get(0).set(value);
        state.store(slots);
    }

    private static double get(CemState state, List<Variable> slots)
    {
        state.load(slots);

        return slots.get(0).doubleValue();
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
}
