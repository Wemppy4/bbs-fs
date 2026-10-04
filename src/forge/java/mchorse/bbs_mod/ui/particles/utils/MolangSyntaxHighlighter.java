package mchorse.bbs_mod.ui.particles.utils;

import mchorse.bbs_mod.ui.framework.elements.input.text.highlighting.BaseSyntaxHighlighter;

import java.util.Arrays;
import java.util.HashSet;

public class MolangSyntaxHighlighter extends BaseSyntaxHighlighter
{
    public MolangSyntaxHighlighter()
    {
        super();

        this.primaryKeywords = new HashSet<>(Arrays.asList("return"));
        this.secondaryKeywords = new HashSet<>(Arrays.asList("variable", "v", "query", "q", "temp", "t", "particle_index", "particle_age", "particle_lifetime", "particle_random_1", "particle_random_2", "particle_random_3", "particle_random_4", "particle_x", "particle_y", "particle_z", "emitter_age", "emitter_lifetime", "emitter_random_1", "emitter_random_2", "emitter_random_3", "emitter_random_4", "emitter_user_1", "emitter_user_2", "emitter_user_3", "emitter_user_4", "emitter_user_5", "emitter_user_6"));
        this.identifierKeywords = new HashSet<>(Arrays.asList("math", "abs", "sin", "cos", "clamp", "ceil", "floor", "trunc", "round", "mod", "pow", "sqrt", "exp", "pi", "max", "min", "asin", "acos", "atan", "atan2", "random", "random_integer", "die_roll", "die_roll_integer", "hermite_blend", "lerp", "lerprotate", "ln"));
        this.typeKeyswords = new HashSet<>(Arrays.asList("true", "false", "null", "undefined"));
        this.identifyFunctions = false;
    }
}