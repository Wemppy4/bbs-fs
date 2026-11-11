package mchorse.bbs_mod.utils.glsl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * First stage: walks the source like the preprocessor would, with the chosen options left
 * symbolic. Conditionals that don't depend on them are decided here (and those outside option
 * blocks are left to JCPP as they are), the rest become {@link Dyn} nodes holding every branch.
 * Macros keep a definition per option values ({@link PpMacro.State}), and the code that JCPP
 * will see after the transformation is planned: a macro that differs between option values gets
 * one definition choosing at runtime. The result is the code as a {@link Seq} of token indices
 * and {@link Dyn} nodes for the second stage.
 */
class PpSimulation
{
    /** Iris switches an option off by putting "//" in front of its whole line, so an indented
     * "  //#define NAME" that's off arrives as "//  //#define NAME" */
    private static final Pattern COMMENTED_DEFINE = Pattern.compile("^(?:/{2,}+\\s*+)++#define\\s+(\\w+)\\s*(//.*)?$", Pattern.DOTALL);

    final List<GlslToken> tokens;
    final GlslDirective[] directives;
    final Map<String, GlslOption> dynamic;
    final String prefix;
    final boolean conditionals;

    /* State */

    final Map<String, PpMacro.State> macros;

    /** Options found to stay constant during this pass, at their value */
    final Map<String, Long> pins = new HashMap<>();
    private int version;
    private final Map<String, Taint> taintMemo = new HashMap<>();

    /** Macros whose definition seen by JCPP can't stand for every option value in code */
    private final Map<String, Set<String>> unsafeInCode = new HashMap<>();

    /* Output */

    final Map<String, String> blocks = new LinkedHashMap<>();
    final Map<PpTree.If, IfPlan> plans = new HashMap<>();
    final Map<Integer, String> lineEdits = new HashMap<>();
    final Map<Integer, Set<String>> taint = new HashMap<>();
    final Map<String, Long> defaults = new HashMap<>();
    final Map<String, String> defaultTexts = new HashMap<>();
    final Set<String> seen = new LinkedHashSet<>();
    final Seq root = new Seq();

    /** Macros JCPP sees as a runtime choice between definitions: their bodies, and the options choosing */
    final Map<String, Set<String>> selects = new HashMap<>();
    final Map<String, Set<String>> selectSymbols = new HashMap<>();

    /** Names a macro used in code expands to, by the index of the token naming it */
    final Map<Integer, Set<String>> expansions = new HashMap<>();
    private final Map<String, Taint> expansionMemo = new HashMap<>();

    /** Every name defined as a macro somewhere along the walk, the environment's included */
    final Set<String> definedNames = new HashSet<>();

    /** Code tokens naming a macro that expands to whole statements (index -> function-like) */
    final Map<Integer, Boolean> statementMacros = new HashMap<>();

    /**
     * Macros defined for some option values only. Where they aren't, the name may mean something
     * else in code (a variable of the same name), which no single definition seen by JCPP can do.
     */
    final Map<String, Set<String>> partial = new HashMap<>();

    PpSimulation(List<GlslToken> tokens, GlslDirective[] directives, Map<String, PpMacro.State> environment, Map<String, GlslOption> dynamic, String prefix, boolean conditionals)
    {
        this.conditionals = conditionals;
        this.tokens = tokens;
        this.directives = directives;
        this.macros = new HashMap<>(environment);
        this.definedNames.addAll(environment.keySet());
        this.dynamic = dynamic;
        this.prefix = prefix;
    }

    void run(List<PpTree.Node> tree) throws PpException
    {
        Presence top = new Presence(PpExpr.TRUE, new PpFacts(this.pins));

        this.walk(tree, top, this.root);
    }

    /* Walking */

    private void walk(List<PpTree.Node> nodes, Presence presence, Seq out) throws PpException
    {
        for (PpTree.Node node : nodes)
        {
            if (node instanceof PpTree.Text)
            {
                this.walkText((PpTree.Text) node, presence, out);
            }
            else if (node instanceof PpTree.Line)
            {
                this.walkLine((PpTree.Line) node, presence);
            }
            else
            {
                this.walkIf((PpTree.If) node, presence, out);
            }
        }
    }

    private void walkText(PpTree.Text text, Presence presence, Seq out)
    {
        for (int i = text.from; i < text.to; i++)
        {
            GlslToken token = this.tokens.get(i);

            switch (token.type)
            {
                case SPACE:
                case NEWLINE:
                    break;
                case LINE_COMMENT:
                    this.commentedDefine(i, presence);
                    this.renderTargets(token, presence);
                    break;
                case BLOCK_COMMENT:
                    this.renderTargets(token, presence);
                    break;
                case IDENTIFIER:
                {
                    Set<String> symbols = this.codeTaint(token.text);

                    if (symbols != null)
                    {
                        this.taint.put(i, symbols);
                    }

                    this.statementMacro(i, token.text);

                    Set<String> names = this.expansion(token.text);

                    if (names != null)
                    {
                        this.expansions.put(i, names);
                    }

                    out.items.add(i);
                    break;
                }
                default:
                    out.items.add(i);
            }
        }
    }

    private void statementMacro(int index, String name)
    {
        PpMacro.State state = this.macros.get(name);
        PpMacro macro = state == null ? null : state.single();

        if (macro == null)
        {
            return;
        }

        for (GlslToken token : macro.body)
        {
            if (token.is(";") || token.is("{") || token.is("}"))
            {
                this.statementMacros.put(index, macro.isFunction());

                return;
            }
        }
    }

    /** Iris reads the render targets from a comment, so it can't move between option values */
    private void renderTargets(GlslToken token, Presence presence)
    {
        if (presence.isDynamic() && (token.text.contains("DRAWBUFFERS:") || token.text.contains("RENDERTARGETS:")))
        {
            this.block(presence.symbols(), "render targets are chosen by it");
        }
    }

    /** A switched off on/off option: "//#define NAME" alone on its line */
    private void commentedDefine(int index, Presence presence)
    {
        GlslOption option = null;
        GlslToken token = this.tokens.get(index);

        if (!token.text.contains("#define") || !this.startsLine(index))
        {
            return;
        }

        Matcher matcher = COMMENTED_DEFINE.matcher(token.text);

        if (matcher.matches())
        {
            option = this.dynamic.get(matcher.group(1));
        }

        if (option != null && option.isBoolean())
        {
            this.defineFlag(option, presence, false);
        }
    }

    private boolean startsLine(int index)
    {
        int i = index - 1;

        while (i >= 0 && this.tokens.get(i).type == GlslToken.Type.SPACE)
        {
            i -= 1;
        }

        return i < 0 || this.tokens.get(i).type == GlslToken.Type.NEWLINE;
    }

    private void walkLine(PpTree.Line line, Presence presence) throws PpException
    {
        GlslDirective directive = line.directive;

        switch (directive.name)
        {
            case "define":
            {
                if (directive.macro == null)
                {
                    break;
                }

                GlslOption option = this.dynamic.get(directive.macro);

                if (option != null && isOptionLine(directive, option))
                {
                    if (option.isBoolean())
                    {
                        this.defineFlag(option, presence, true);
                    }
                    else
                    {
                        this.defineValue(line.index, directive, option, presence);
                    }
                }
                else
                {
                    this.define(line.index, directive.macro, PpMacro.of(directive), presence);
                }

                break;
            }
            case "undef":
                if (!directive.payload.isEmpty() && directive.payload.get(0).isIdentifier())
                {
                    this.define(line.index, directive.payload.get(0).text, null, presence);
                }

                break;
            case "extension":
            case "version":
                if (presence.isDynamic())
                {
                    this.block(presence.symbols(), "#" + directive.name + " depends on it");
                }

                break;
            case "error":
            case "warning":
                /* JCPP only prints them; inside an option's block they'd print every time */
                if (presence.isDynamic())
                {
                    this.lineEdits.put(line.index, "");
                }

                break;
            case "include":
                throw new PpException("#include left in the source", null);
        }
    }

    /** Whether a #define of this name is where Iris put the option's value */
    private static boolean isOptionLine(GlslDirective directive, GlslOption option)
    {
        if (directive.params != null)
        {
            return false;
        }

        if (option.isBoolean())
        {
            return directive.body.isEmpty();
        }

        List<GlslToken> body = directive.body;
        boolean number = (body.size() == 1 && body.get(0).type == GlslToken.Type.NUMBER)
            || (body.size() == 2 && body.get(0).is("-") && body.get(1).type == GlslToken.Type.NUMBER);

        return number && !directive.comments.isEmpty();
    }

    private void defineFlag(GlslOption option, Presence presence, boolean on)
    {
        PpExpr when = PpExpr.and(presence.condition, PpExpr.flag(option.name)).simplify(presence.facts);
        PpMacro.State state = this.state(option.name).override(when, new PpMacro(option.name, null, new ArrayList<GlslToken>(), null));

        this.set(option.name, state);
        this.seen.add(option.name);
        this.defaults.putIfAbsent(option.name, on ? 1L : 0L);
        this.defaultTexts.putIfAbsent(option.name, on ? "1" : "0");

        /* The line stays as it is, so JCPP's view of the name doesn't follow the option */
        if (!state.isStatic())
        {
            Set<String> symbols = new HashSet<>();

            when.symbols(symbols);
            this.unsafeInCode.put(option.name, symbols);
        }
    }

    private void defineValue(int index, GlslDirective directive, GlslOption option, Presence presence)
    {
        List<GlslToken> body = directive.body;
        String text = body.size() == 2 ? "-" + body.get(1).text : body.get(0).text;
        long value = PpEvaluator.jcppLong(body.get(body.size() - 1).text);

        this.defaults.putIfAbsent(option.name, body.size() == 2 ? -value : value);
        this.defaultTexts.putIfAbsent(option.name, text);
        this.seen.add(option.name);

        PpMacro macro = PpMacro.option(option);
        PpMacro.State state = this.state(option.name).override(presence.condition, macro);

        this.set(option.name, state);

        if (state.isStatic())
        {
            this.lineEdits.put(index, directive.replaceBody(this.prefix + option.name));
            this.unsafeInCode.remove(option.name);
        }
        else
        {
            this.linearize(index, option.name, state, directive);
        }
    }

    private void define(int index, String name, PpMacro macro, Presence presence)
    {
        PpMacro.State state = this.state(name).override(presence.condition, macro);

        this.set(name, state);

        if (presence.isDynamic())
        {
            this.linearize(index, name, state, this.directives[index]);
        }
        else
        {
            this.unsafeInCode.remove(name);
        }
    }

    private PpMacro.State state(String name)
    {
        PpMacro.State state = this.macros.get(name);

        return state == null ? PpMacro.State.UNDEFINED : state;
    }

    private void set(String name, PpMacro.State state)
    {
        this.definedNames.add(name);

        if (state == PpMacro.State.UNDEFINED)
        {
            this.macros.remove(name);
        }
        else
        {
            this.macros.put(name, state);
        }

        this.version += 1;
    }

    /**
     * Replace a #define/#undef inside an option's block with one JCPP can read without the block:
     * the name's definition for every option value at once. Where that can't be a single
     * expression, using the name in code later blocks the options it depends on.
     */
    private void linearize(int index, String name, PpMacro.State state, GlslDirective directive)
    {
        List<PpMacro.Alternative> defined = new ArrayList<>();
        Set<String> symbols = new HashSet<>();

        for (PpMacro.Alternative alternative : state.alternatives)
        {
            alternative.when.symbols(symbols);

            if (alternative.macro != null)
            {
                defined.add(alternative);
            }
        }

        String comments = directive == null || directive.comments.isEmpty() ? "" : " " + String.join(" ", directive.comments);

        if (defined.isEmpty())
        {
            this.lineEdits.put(index, "#undef " + name + comments);
            this.unsafeInCode.remove(name);

            return;
        }

        PpMacro first = defined.get(0).macro;
        boolean same = true;

        for (PpMacro.Alternative alternative : defined)
        {
            same &= alternative.macro.sameAs(first);
        }

        String params = first.params == null ? "" : "(" + String.join(", ", first.params) + ")";

        /* Undefined for some option values is fine: code may only use the name where it's
         * defined, and the name's own tests go through the uniforms */
        if (same)
        {
            this.lineEdits.put(index, "#define " + name + params + " " + this.body(first) + comments);
            this.unsafeInCode.remove(name);

            if (defined.size() < state.alternatives.size())
            {
                this.partial.computeIfAbsent(name, (k) -> new HashSet<>()).addAll(symbols);
            }

            return;
        }

        boolean valid = true;

        for (PpMacro.Alternative alternative : defined)
        {
            valid &= Objects.equals(alternative.macro.params, first.params) && isExpression(alternative.macro);
        }

        StringBuilder select = new StringBuilder();
        int last = defined.size() - 1;

        for (int i = 0; i < last; i++)
        {
            PpMacro.Alternative alternative = defined.get(i);

            select.append("(").append(alternative.when.glsl(this.prefix, true)).append(" ? (").append(this.body(alternative.macro)).append(") : ");
        }

        select.append("(").append(this.body(defined.get(last).macro)).append(")");

        for (int i = 0; i < last; i++)
        {
            select.append(")");
        }

        this.lineEdits.put(index, "#define " + name + params + " " + select + comments);

        if (valid)
        {
            this.unsafeInCode.remove(name);

            Set<String> bodies = this.selects.computeIfAbsent(name, (k) -> new HashSet<>());

            for (PpMacro.Alternative alternative : defined)
            {
                bodies.add(this.body(alternative.macro));
            }

            this.selectSymbols.computeIfAbsent(name, (k) -> new HashSet<>()).addAll(symbols);
        }
        else
        {
            this.unsafeInCode.put(name, symbols);
        }
    }

    private String body(PpMacro macro)
    {
        return macro.option != null ? this.prefix + macro.option.name : macro.bodyText();
    }

    /** Whether a replacement reads as one expression, so it can be one of the choices */
    private static boolean isExpression(PpMacro macro)
    {
        if (macro.option != null)
        {
            return true;
        }

        if (macro.body.isEmpty())
        {
            return false;
        }

        int depth = 0;

        for (GlslToken token : macro.body)
        {
            if (token.is(";") || token.is("{") || token.is("}") || token.is("#") || token.is("##"))
            {
                return false;
            }

            if (token.is("(") || token.is("["))
            {
                depth += 1;
            }
            else if (token.is(")") || token.is("]"))
            {
                depth -= 1;

                if (depth < 0)
                {
                    return false;
                }
            }
        }

        return depth == 0 && !GlslSkeleton.isTypeOrQualifier(macro.body.get(0).text, macro.body.size());
    }

    /* Conditionals */

    private void walkIf(PpTree.If node, Presence presence, Seq out) throws PpException
    {
        IfPlan plan = new IfPlan(node);
        PpExpr remaining = PpExpr.TRUE;

        for (PpTree.Branch branch : node.branches)
        {
            BranchPlan branchPlan = new BranchPlan(branch);

            plan.branches.add(branchPlan);

            if (remaining.isFalse())
            {
                continue;
            }

            PpExpr own = this.condition(branch, presence);
            PpExpr taken = PpExpr.and(remaining, own).simplify(presence.facts);

            remaining = PpExpr.and(remaining, PpExpr.not(own)).simplify(presence.facts);

            if (!taken.isFalse())
            {
                branchPlan.live = true;
                branchPlan.own = own;
                branchPlan.taken = taken;
                plan.dynamic |= !taken.isConst();
            }
        }

        if (!plan.dynamic)
        {
            /* Settled here. Inside an option's block the decision is written out, since JCPP
             * won't see the definitions this branch saw */
            if (presence.isDynamic())
            {
                this.plans.put(node, plan);
            }

            for (BranchPlan branchPlan : plan.branches)
            {
                if (branchPlan.live)
                {
                    this.walk(branchPlan.branch.body, presence, out);
                }
            }

            return;
        }

        Dyn dyn = new Dyn(plan, presence.condition);

        if (!this.conditionals)
        {
            Set<String> symbols = new HashSet<>();

            for (BranchPlan branchPlan : plan.branches)
            {
                if (branchPlan.live)
                {
                    branchPlan.own.symbols(symbols);
                }
            }

            this.block(symbols, "conditionals are left alone for this pack");
        }

        plan.dyn = dyn;
        this.plans.put(node, plan);
        out.items.add(dyn);

        for (BranchPlan branchPlan : plan.branches)
        {
            if (!branchPlan.live)
            {
                continue;
            }

            DynBranch dynBranch = new DynBranch(dyn, branchPlan.branch, branchPlan.own, branchPlan.taken);

            branchPlan.own.symbols(dyn.symbols);
            dyn.branches.add(dynBranch);
            this.walk(branchPlan.branch.body, presence.and(branchPlan.taken), dynBranch.body);
        }

        if (!remaining.isFalse())
        {
            dyn.branches.add(new DynBranch(dyn, null, PpExpr.TRUE, remaining));
        }
    }

    private PpExpr condition(PpTree.Branch branch, Presence presence) throws PpException
    {
        GlslDirective directive = branch.directive;

        try
        {
            return this.evaluate(directive, presence);
        }
        catch (PpException e)
        {
            if (e.symbols.isEmpty())
            {
                throw e;
            }

            /* Can't be followed with these options symbolic: keep them at their value */
            this.block(e.symbols, "#" + directive.name + ": " + e.getMessage());

            return this.evaluate(directive, presence);
        }
    }

    private PpExpr evaluate(GlslDirective directive, Presence presence) throws PpException
    {
        switch (directive.name)
        {
            case "else":
                return PpExpr.TRUE;
            case "ifdef":
            case "ifndef":
            {
                if (directive.payload.isEmpty() || !directive.payload.get(0).isIdentifier())
                {
                    throw new PpException("#" + directive.name + " without a name", null);
                }

                PpMacro.State state = this.macros.get(directive.payload.get(0).text);
                PpExpr defined = state == null ? PpExpr.FALSE : state.defined(presence.facts);

                return directive.name.equals("ifdef") ? defined : PpExpr.not(defined);
            }
            default:
                return new PpEvaluator(this.macros, presence.facts).evaluate(directive.payload);
        }
    }

    /* Taint */

    /**
     * The options a name in code depends on through macros, or null. Using a macro whose
     * definition seen by JCPP can't follow the options blocks those options.
     */
    private Set<String> codeTaint(String name)
    {
        if (!this.macros.containsKey(name))
        {
            return null;
        }

        Taint memo = this.taintMemo.get(name);

        if (memo == null || memo.version != this.version)
        {
            Set<String> symbols = new HashSet<>();
            Set<String> unsafe = new HashSet<>();

            this.collectTaint(name, symbols, unsafe, new HashSet<String>());
            memo = new Taint(this.version, symbols.isEmpty() ? null : symbols, unsafe);
            this.taintMemo.put(name, memo);
        }

        if (!memo.unsafe.isEmpty())
        {
            this.block(memo.unsafe, "a macro that differs with it can't be one expression in code");
        }

        return memo.symbols;
    }

    /** Identifiers a macro may expand to (for any option values), or null */
    private Set<String> expansion(String name)
    {
        if (!this.macros.containsKey(name))
        {
            return null;
        }

        Taint memo = this.expansionMemo.get(name);

        if (memo == null || memo.version != this.version)
        {
            Set<String> names = new HashSet<>();

            this.collectNames(name, names, new HashSet<String>());
            memo = new Taint(this.version, names.isEmpty() ? null : names, null);
            this.expansionMemo.put(name, memo);
        }

        return memo.symbols;
    }

    private void collectNames(String name, Set<String> names, Set<String> visited)
    {
        PpMacro.State state = this.macros.get(name);

        if (state == null || !visited.add(name))
        {
            return;
        }

        for (PpMacro.Alternative alternative : state.alternatives)
        {
            PpMacro macro = alternative.macro;

            if (macro == null || macro.option != null)
            {
                continue;
            }

            for (GlslToken token : macro.body)
            {
                if (!token.isIdentifier() || (macro.params != null && macro.params.contains(token.text)))
                {
                    continue;
                }

                if (this.macros.containsKey(token.text))
                {
                    names.add(token.text);
                    this.collectNames(token.text, names, visited);
                }
                else
                {
                    names.add(token.text);
                }
            }
        }
    }

    private void collectTaint(String name, Set<String> symbols, Set<String> unsafe, Set<String> visited)
    {
        PpMacro.State state = this.macros.get(name);

        if (state == null || !visited.add(name))
        {
            return;
        }

        Set<String> unsafeSymbols = this.unsafeInCode.get(name);

        if (unsafeSymbols != null)
        {
            unsafe.addAll(unsafeSymbols);
        }

        for (PpMacro.Alternative alternative : state.alternatives)
        {
            alternative.when.symbols(symbols);

            if (alternative.macro == null)
            {
                continue;
            }

            if (alternative.macro.option != null)
            {
                symbols.add(alternative.macro.option.name);
            }

            for (GlslToken token : alternative.macro.body)
            {
                if (token.isIdentifier())
                {
                    this.collectTaint(token.text, symbols, unsafe, visited);
                }
            }
        }
    }

    /* Blocking */

    void block(Set<String> symbols, String reason)
    {
        for (String symbol : symbols)
        {
            if (this.blocks.containsKey(symbol))
            {
                continue;
            }

            this.blocks.put(symbol, reason);

            Long value = this.defaults.get(symbol);

            this.pins.put(symbol, value == null ? 0L : value);
        }
    }

    /* Structures */

    static class Presence
    {
        final PpExpr condition;
        final PpFacts facts;

        Presence(PpExpr condition, PpFacts facts)
        {
            this.condition = condition;
            this.facts = facts;
        }

        /** Inside a branch some option values skip (the facts would settle the condition itself) */
        boolean isDynamic()
        {
            return !this.condition.isTrue();
        }

        Set<String> symbols()
        {
            Set<String> symbols = new HashSet<>();

            this.condition.symbols(symbols);

            return symbols;
        }

        Presence and(PpExpr taken)
        {
            PpFacts facts = this.facts.copy();

            facts.assume(taken);

            return new Presence(PpExpr.and(this.condition, taken), facts);
        }
    }

    /** How a conditional comes out */
    static class IfPlan
    {
        final PpTree.If node;
        final List<BranchPlan> branches = new ArrayList<>();

        /** Some branch depends on options; otherwise one branch (or none) was settled */
        boolean dynamic;
        Dyn dyn;

        IfPlan(PpTree.If node)
        {
            this.node = node;
        }
    }

    static class BranchPlan
    {
        final PpTree.Branch branch;

        /** Taken for some option values (or always, when the conditional is settled) */
        boolean live;
        PpExpr own;
        PpExpr taken;

        BranchPlan(PpTree.Branch branch)
        {
            this.branch = branch;
        }
    }

    /** Code in order: token indices (Integer) and conditionals depending on options (Dyn) */
    static class Seq
    {
        final List<Object> items = new ArrayList<>();
    }

    static class Dyn
    {
        final IfPlan plan;

        /** Where the conditional itself sits */
        final PpExpr presence;
        final List<DynBranch> branches = new ArrayList<>();

        /** Options its branches are chosen by */
        final Set<String> symbols = new LinkedHashSet<>();

        Dyn(IfPlan plan, PpExpr presence)
        {
            this.plan = plan;
            this.presence = presence;
        }
    }

    static class DynBranch
    {
        final Dyn dyn;

        /** Null for the missing #else of a conditional none of whose branches may run */
        final PpTree.Branch branch;
        final PpExpr own;
        final PpExpr taken;
        final Seq body = new Seq();

        DynBranch(Dyn dyn, PpTree.Branch branch, PpExpr own, PpExpr taken)
        {
            this.dyn = dyn;
            this.branch = branch;
            this.own = own;
            this.taken = taken;
        }

        Dyn dyn()
        {
            return this.dyn;
        }
    }

    private static class Taint
    {
        final int version;
        final Set<String> symbols;
        final Set<String> unsafe;

        Taint(int version, Set<String> symbols, Set<String> unsafe)
        {
            this.version = version;
            this.symbols = symbols;
            this.unsafe = unsafe;
        }
    }
}
