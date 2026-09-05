package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.cubic.jem.functions.Between;
import mchorse.bbs_mod.cubic.jem.functions.CemLerp;
import mchorse.bbs_mod.cubic.jem.functions.CemMax;
import mchorse.bbs_mod.cubic.jem.functions.CemMin;
import mchorse.bbs_mod.cubic.jem.functions.CemRandom;
import mchorse.bbs_mod.cubic.jem.functions.EqualsEpsilon;
import mchorse.bbs_mod.cubic.jem.functions.Fmod;
import mchorse.bbs_mod.cubic.jem.functions.Frac;
import mchorse.bbs_mod.cubic.jem.functions.If;
import mchorse.bbs_mod.cubic.jem.functions.In;
import mchorse.bbs_mod.cubic.jem.functions.Print;
import mchorse.bbs_mod.cubic.jem.functions.Signum;
import mchorse.bbs_mod.cubic.jem.functions.Tan;
import mchorse.bbs_mod.cubic.jem.functions.ToDeg;
import mchorse.bbs_mod.cubic.jem.functions.ToRad;
import mchorse.bbs_mod.cubic.jem.functions.WrapRad;
import mchorse.bbs_mod.math.Constant;
import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.Variable;
import mchorse.bbs_mod.math.functions.classic.Ln;

/**
 * Expression parser for OptiFine CEM animations.
 *
 * <p>It reuses BBS's {@link MathBuilder} engine (operators, precedence, ternary, function parsing)
 * but is a <b>separate, isolated</b> parser so its CEM-specific semantics never affect the shared
 * {@link mchorse.bbs_mod.math.molang.MolangParser MoLang} parser. The key differences from MoLang:</p>
 * <ul>
 *     <li>Trigonometry is in <b>radians</b> (MoLang overrides it to degrees) — CEM uses radians and
 *     converts with {@code torad}/{@code todeg}.</li>
 *     <li>{@code lerp(k, x, y)} has a different argument order than MoLang's {@code lerp}.</li>
 *     <li>{@code random(seed)} returns 0..1 (MoLang's {@code random} scales by its arguments).</li>
 *     <li>Adds CEM functions: {@code if}/{@code ifb}, {@code between}, {@code equals}, {@code in},
 *     {@code signum}, {@code frac}, {@code fmod}, {@code tan}, {@code torad}, {@code todeg},
 *     {@code log} (natural log), {@code wraprad}, {@code print}/{@code printb}; and the constants
 *     {@code pi}, {@code true}, {@code false}.</li>
 *     <li>Unknown variables (model variables like {@code head.rx}, entity variables {@code var.*}/
 *     {@code varb.*}, render parameters) are auto-created with a default value of 0, so an expression
 *     never fails to resolve a name — the animation runtime fills the values each frame.</li>
 * </ul>
 */
public class CemParser extends MathBuilder
{
    public CemParser()
    {
        super();

        /* CEM constants (lowercase pi, plus boolean literals). */
        this.register("pi", Math.PI);
        this.register("true", 1);
        this.register("false", 0);

        /* CEM functions. The base MathBuilder already provides radian trig (sin/cos/...), abs, clamp,
         * min/max, floor/ceil/round, exp, sqrt, pow with matching semantics. */
        this.functions.put("tan", Tan.class);
        this.functions.put("frac", Frac.class);
        this.functions.put("signum", Signum.class);
        this.functions.put("fmod", Fmod.class);
        this.functions.put("torad", ToRad.class);
        this.functions.put("todeg", ToDeg.class);
        this.functions.put("log", Ln.class);
        this.functions.put("wraprad", WrapRad.class);
        this.functions.put("between", Between.class);
        this.functions.put("equals", EqualsEpsilon.class);
        this.functions.put("in", In.class);
        this.functions.put("if", If.class);
        this.functions.put("ifb", If.class);
        this.functions.put("print", Print.class);
        this.functions.put("printb", Print.class);

        /* Override the functions whose semantics differ from BBS's built-ins. */
        this.functions.put("lerp", CemLerp.class);
        this.functions.put("random", CemRandom.class);
        this.functions.put("max", CemMax.class);
        this.functions.put("min", CemMin.class);
    }

    /**
     * Parse a single CEM expression, returning a constant 0 on failure so a malformed expression
     * degrades gracefully instead of breaking the whole animation.
     */
    public IExpression parseExpression(String expression)
    {
        try
        {
            return this.parse(this.preprocess(expression));
        }
        catch (Exception e)
        {
            System.err.println("Failed to parse CEM expression: " + expression);

            return new Constant(0);
        }
    }

    /**
     * Smooth over two CEM quirks the math engine doesn't handle natively:
     * <ul>
     *     <li>Unary plus (e.g. {@code torad(+12*...)}) — stripped, since the engine only understands
     *     unary minus.</li>
     *     <li>{@code nbt(...)} — OptiFine's entity-NBT query function takes a non-math string DSL
     *     (regex/paths). It is stubbed to 0 for now (proper entity-NBT support is a later stage), so
     *     the rest of the expression still evaluates instead of failing wholesale.</li>
     * </ul>
     */
    private String preprocess(String expression)
    {
        /* nbt(...) -> 0 (the arguments aren't math; assumes no nested parens inside the call). */
        expression = expression.replaceAll("nbt\\s*\\([^()]*\\)", "0");

        /* Drop unary plus right after the start, an opening paren, a comma or another operator. */
        expression = expression.replaceAll("(^|[-+*/%^&|<>=!(,])\\s*\\+", "$1");

        return expression;
    }

    public Variable getOrCreateVariable(String name)
    {
        return this.getVariable(name);
    }

    public void setValue(String name, double value)
    {
        this.getVariable(name).set(value);
    }

    /**
     * CEM has no ternary operator and instead uses {@code :} inside hierarchical model-variable names
     * (e.g. {@code leg4:Hoof.rx}). So {@code :} (and the unused {@code ?}) must NOT be treated as
     * operators here, otherwise such names would be split apart.
     */
    @Override
    protected boolean isOperator(String s)
    {
        if (s.equals(":") || s.equals("?"))
        {
            return false;
        }

        return super.isOperator(s);
    }

    /**
     * Resolve a variable, creating it (default 0) on first reference so any model/entity/render name
     * an expression uses always exists.
     */
    @Override
    protected Variable getVariable(String name)
    {
        Variable variable = this.variables.get(name);

        if (variable == null)
        {
            variable = new Variable(name, 0);

            this.register(variable);
        }

        return variable;
    }
}
