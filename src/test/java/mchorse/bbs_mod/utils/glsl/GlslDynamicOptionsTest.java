package mchorse.bbs_mod.utils.glsl;

import io.github.douira.glsl_transformer.parser.EnhancedParser;
import org.anarres.cpp.DefaultPreprocessorListener;
import org.anarres.cpp.Feature;
import org.anarres.cpp.Preprocessor;
import org.anarres.cpp.Source;
import org.anarres.cpp.StringLexerSource;
import org.anarres.cpp.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Standalone checks of {@link GlslDynamicOptions}: the transformed source must contain the
 * expected runtime code, keep comments in place, and still go through JCPP and the GLSL parser
 * Iris uses.
 */
public class GlslDynamicOptionsTest
{
    private static int failures;
    private static int checks;

    public static void main(String[] args)
    {
        lexerRoundTrip();
        booleanOption();
        switchedOffOption();
        switchedOffIndentedOption();
        valueOption();
        floatOptionInCondition();
        hoisting();
        globalDeclarations();
        functionDispatch();
        derivedMacro();
        derivedFlag();
        settledInsideBranch();
        blockedArraySize();
        arrayVariants();
        blockedIrisConst();
        blockedRenderTargets();
        blockedExpression();
        environmentUntouched();
        commentsKept();
        variantVariables();
        writtenVariantBlocked();
        propertiesLayout();

        System.out.println(checks + " checks, " + failures + " failures");

        if (failures > 0)
        {
            System.exit(1);
        }
    }

    private static void lexerRoundTrip()
    {
        String[] sources = {
            "#version 130\r\n#define A 1 // [1 2]\r\nvoid main() { /* a\nb */ x = 1.0e-5f; }\r\n",
            "#define LONG(a, b) \\\n  ((a) + (b))\n  # if defined(X) && Y > 2 /* c */\nfoo\n#endif",
            "/* c */ #define X\n// #define Y\nfloat a = .5 + 0x1F;\n",
        };

        for (String source : sources)
        {
            StringBuilder builder = new StringBuilder();

            for (GlslToken token : GlslLexer.tokenize(source))
            {
                builder.append(token.text);
            }

            check(builder.toString().equals(source), "Tokens give back the source");
        }

        List<GlslToken> tokens = GlslLexer.tokenize("/* c */ #define X\n");

        check(tokens.get(2).type == GlslToken.Type.DIRECTIVE, "A block comment before '#' keeps it a directive");
    }

    private static void booleanOption()
    {
        String source = String.join("\n",
            "#version 130",
            "#define BLOOM // [bloom]",
            "void main() {",
            "    vec3 color = vec3(0.0);",
            "    #ifdef BLOOM",
            "    color += vec3(1.0);",
            "    #endif",
            "    gl_FragColor = vec4(color, 1.0);",
            "}",
            "");
        GlslDynamicOptions.Result result = transform(source, bool("BLOOM"));

        contains(result, "uniform int bbs_BLOOM;", "Uniform declared");
        contains(result, "if ((bbs_BLOOM != 0)) {", "#ifdef became an if");
        check(result.uniforms.containsKey("BLOOM"), "BLOOM is a uniform");
        check("1".equals(result.values.get("BLOOM")), "BLOOM is on in the source");
        check(lines(result.source) == lines(source) + 1, "Only the uniform line was added");
        valid(result, "booleanOption");
    }

    private static void switchedOffOption()
    {
        String source = String.join("\n",
            "#version 130",
            "//#define BLOOM // [bloom]",
            "void main() {",
            "    #ifndef BLOOM",
            "    gl_FragColor = vec4(0.0);",
            "    #else",
            "    gl_FragColor = vec4(1.0);",
            "    #endif",
            "}",
            "");
        GlslDynamicOptions.Result result = transform(source, bool("BLOOM"));

        contains(result, "if (!(bbs_BLOOM != 0)) {", "#ifndef became an if");
        contains(result, "} else {", "#else became an else");
        check("0".equals(result.values.get("BLOOM")), "BLOOM is off in the source");
        valid(result, "switchedOffOption");
    }

    /** Iris puts "//" in front of an off option's whole line, an indented commented one too */
    private static void switchedOffIndentedOption()
    {
        String source = String.join("\n",
            "#version 130",
            "//  //#define DOF",
            "void main() {",
            "    #ifdef DOF",
            "    gl_FragColor = vec4(1.0);",
            "    #else",
            "    gl_FragColor = vec4(0.0);",
            "    #endif",
            "}",
            "");
        GlslDynamicOptions.Result result = transform(source, bool("DOF"));

        contains(result, "if ((bbs_DOF != 0)) {", "#ifdef of an indented off option became an if");
        check(result.uniforms.containsKey("DOF"), "DOF is a uniform");
        check("0".equals(result.values.get("DOF")), "DOF is off in the source");
        valid(result, "switchedOffIndentedOption");
    }

    private static void valueOption()
    {
        String source = String.join("\n",
            "#version 130",
            "#define QUALITY 2 // [1 2 3]",
            "uniform float strength;",
            "void main() {",
            "    float x = QUALITY * strength;",
            "    #if QUALITY == 1",
            "    x *= 0.5;",
            "    #elif QUALITY >= 3",
            "    x *= 2.0;",
            "    #else",
            "    x += 1.0;",
            "    #endif",
            "    gl_FragColor = vec4(x);",
            "}",
            "");
        GlslDynamicOptions.Result result = transform(source, value("QUALITY", false));

        contains(result, "#define QUALITY bbs_QUALITY // [1 2 3]", "The value became the uniform, comment kept");
        contains(result, "if ((bbs_QUALITY == 1)) {", "#if became an if");
        contains(result, "} else if ((bbs_QUALITY >= 3)) {", "#elif became an else if");
        contains(result, "uniform int bbs_QUALITY;", "Integer uniform");
        valid(result, "valueOption");
    }

    private static void floatOptionInCondition()
    {
        String source = String.join("\n",
            "#version 130",
            "#define AMOUNT 1.5 // [0.5 1.5 2.5]",
            "void main() {",
            "    #if AMOUNT > 1",
            "    gl_FragColor = vec4(AMOUNT);",
            "    #endif",
            "}",
            "");
        GlslDynamicOptions.Result result = transform(source, value("AMOUNT", true));

        contains(result, "if ((int(bbs_AMOUNT) > 1)) {", "A float reads as its integer part, like JCPP");
        contains(result, "uniform float bbs_AMOUNT;", "Float uniform");
        valid(result, "floatOptionInCondition");
    }

    private static void hoisting()
    {
        String source = String.join("\n",
            "#version 130",
            "#define SHADOWS",
            "float getShadow() { return 0.5; }",
            "void main() {",
            "    #ifdef SHADOWS",
            "    float shadow = getShadow();",
            "    #else",
            "    float shadow = 1.0;",
            "    #endif",
            "    #ifdef SHADOWS",
            "    float unused = 2.0;",
            "    #endif",
            "    gl_FragColor = vec4(shadow);",
            "}",
            "");
        GlslDynamicOptions.Result result = transform(source, bool("SHADOWS"));

        contains(result, "float shadow; if ((bbs_SHADOWS != 0)) {", "The declaration moved before the if");
        contains(result, "shadow = getShadow();", "Declaration became an assignment");
        contains(result, "shadow = 1.0;", "Both branches assign");
        contains(result, "float unused = 2.0;", "A declaration not used later stays");
        valid(result, "hoisting");
    }

    private static void globalDeclarations()
    {
        String source = String.join("\n",
            "#version 130",
            "#define BLOOM",
            "#ifdef BLOOM",
            "uniform sampler2D colortex5;",
            "vec3 bloom(vec2 uv) { return texture2D(colortex5, uv).rgb; }",
            "#endif",
            "varying vec2 uv;",
            "void main() {",
            "    vec3 color = vec3(0.0);",
            "    #ifdef BLOOM",
            "    color += bloom(uv);",
            "    #endif",
            "    gl_FragColor = vec4(color, 1.0);",
            "}",
            "");
        GlslDynamicOptions.Result result = transform(source, bool("BLOOM"));

        check(!result.source.contains("#ifdef BLOOM"), "Global conditional removed");
        contains(result, "uniform sampler2D colortex5;", "Its declarations stay");
        valid(result, "globalDeclarations");
    }

    private static void functionDispatch()
    {
        String source = String.join("\n",
            "#version 130",
            "#define AO_MODE 1 // [1 2]",
            "#if AO_MODE == 1",
            "float getAO(vec2 uv) { return uv.x; }",
            "#else",
            "float getAO(vec2 uv) { return uv.y; }",
            "#endif",
            "varying vec2 uv;",
            "void main() {",
            "    gl_FragColor = vec4(getAO(uv));",
            "}",
            "");
        GlslDynamicOptions.Result result = transform(source, value("AO_MODE", false));

        contains(result, "float getAO_bbs_0(vec2 uv)", "First definition renamed");
        contains(result, "float getAO_bbs_1(vec2 uv)", "Second definition renamed");
        contains(result, "float getAO(vec2 uv) { if ((bbs_AO_MODE == 1)) return getAO_bbs_0(uv); return getAO_bbs_1(uv); }", "Dispatcher");
        valid(result, "functionDispatch");
    }

    private static void derivedMacro()
    {
        String source = String.join("\n",
            "#version 130",
            "#define QUALITY 2 // [1 2]",
            "#if QUALITY == 1",
            "    #define SAMPLES 4",
            "#else",
            "    #define SAMPLES 8",
            "#endif",
            "void main() {",
            "    float sum = 0.0;",
            "    for (int i = 0; i < SAMPLES; i++) sum += 1.0;",
            "    #if SAMPLES > 4",
            "    sum *= 0.5;",
            "    #endif",
            "    gl_FragColor = vec4(sum);",
            "}",
            "");
        GlslDynamicOptions.Result result = transform(source, value("QUALITY", false));

        contains(result, "#define SAMPLES ((bbs_QUALITY != 1) ? (8) : (4))", "One definition choosing at runtime");
        contains(result, "if ((bbs_QUALITY != 1)) {", "A test of the derived macro reads through it");
        check(result.uniforms.containsKey("QUALITY"), "QUALITY is a uniform");
        valid(result, "derivedMacro");
    }

    private static void derivedFlag()
    {
        String source = String.join("\n",
            "#version 130",
            "#define TAA_DEFINE 1 // [-1 1]",
            "#define DETAIL 2 // [0 2]",
            "#if TAA_DEFINE == 1",
            "    #define TAA",
            "#endif",
            "#if DETAIL >= 2",
            "    #if TAA_DEFINE == -1",
            "        #define TAA",
            "    #endif",
            "#endif",
            "void main() {",
            "    #ifdef TAA",
            "    gl_FragColor = vec4(1.0);",
            "    #endif",
            "}",
            "");
        Map<String, GlslOption> options = new HashMap<>();

        options.putAll(value("TAA_DEFINE", false));
        options.putAll(value("DETAIL", false));

        GlslDynamicOptions.Result result = transform(source, options);

        check(result.uniforms.containsKey("TAA_DEFINE") && result.uniforms.containsKey("DETAIL"), "Both options are uniforms: " + result.blocked);
        contains(result, "bbs_TAA_DEFINE == 1", "TAA's test reads the first option");
        contains(result, "bbs_DETAIL >= 2", "TAA's test reads the second option");
        valid(result, "derivedFlag");
    }

    private static void settledInsideBranch()
    {
        String source = String.join("\n",
            "#version 130",
            "#define BLOOM",
            "#define OVERWORLD",
            "void main() {",
            "    #ifdef BLOOM",
            "        #ifdef OVERWORLD",
            "        gl_FragColor = vec4(1.0);",
            "        #else",
            "        gl_FragColor = vec4(2.0);",
            "        #endif",
            "    #endif",
            "}",
            "");
        GlslDynamicOptions.Result result = transform(source, bool("BLOOM"));

        check(!result.source.contains("vec4(2.0)"), "The settled branch inside was dropped");
        check(!result.source.contains("#ifdef OVERWORLD"), "Its directive lines went too");
        valid(result, "settledInsideBranch");
    }

    private static void blockedArraySize()
    {
        String source = String.join("\n",
            "#version 130",
            "#define SAMPLES 4 // [4 8]",
            "void main() {",
            "    float weights[SAMPLES];",
            "    weights[0] = 1.0;",
            "    gl_FragColor = vec4(weights[0]);",
            "}",
            "");
        GlslDynamicOptions.Result result = transform(source, value("SAMPLES", false));

        check(result.blocked.containsKey("SAMPLES"), "An array size can't follow an option");
        check(result.source.equals(source), "Nothing changes then");
    }

    /** Bliss: the bokeh offsets are a constant array of another size for each quality */
    private static void arrayVariants()
    {
        String source = String.join("\n",
            "#version 130",
            "#define WORLD",
            "#define DOF_QUALITY 0 // [0 1]",
            "#define BLUR 0 // [0 1]",
            "#if DOF_QUALITY == 0",
            "const vec2 offsets[2] = vec2[2](vec2(0.0), vec2(1.0));",
            "#else",
            "const vec2 offsets[3] = vec2[3](vec2(0.0), vec2(1.0), vec2(2.0));",
            "#endif",
            "#if BLUR == 0",
            "const float weights[2] = float[2](0.5, 0.5);",
            "#else",
            "const float weights[2] = float[2](0.25, 0.75);",
            "#endif",
            "varying vec2 uv;",
            "void main() {",
            "    gl_FragColor = vec4(offsets[0] * weights[0], uv);",
            "}",
            "");
        Map<String, GlslOption> options = value("DOF_QUALITY", false);

        options.putAll(value("BLUR", false));

        GlslDynamicOptions.Result result = transform(source, options);

        check(result.blocked.containsKey("DOF_QUALITY"), "Arrays of different sizes can't be chosen at runtime");
        check(result.uniforms.containsKey("BLUR"), "Arrays of one size can: " + result.blocked);
        contains(result, "float weights[2] = ", "The array keeps its size");
        valid(result, "arrayVariants");
    }

    private static void blockedIrisConst()
    {
        String source = String.join("\n",
            "#version 130",
            "#define SHADOW_QUALITY 2 // [1 2]",
            "#define BLOOM 1 // [0 1]",
            "#if SHADOW_QUALITY == 2",
            "const int shadowMapResolution = 2048;",
            "#else",
            "const int shadowMapResolution = 1024;",
            "#endif",
            "void main() {",
            "    #if BLOOM == 1",
            "    gl_FragColor = vec4(1.0);",
            "    #endif",
            "}",
            "");
        Map<String, GlslOption> options = new HashMap<>();

        options.putAll(value("SHADOW_QUALITY", false));
        options.putAll(value("BLOOM", false));

        GlslDynamicOptions.Result result = transform(source, options);

        check(result.blocked.containsKey("SHADOW_QUALITY"), "Iris reads shadowMapResolution from the text");
        check(result.uniforms.containsKey("BLOOM"), "Other options still work: " + result.blocked);
        contains(result, "#if SHADOW_QUALITY == 2", "The blocked conditional stays for the preprocessor");
        valid(result, "blockedIrisConst");
    }

    private static void blockedRenderTargets()
    {
        String source = String.join("\n",
            "#version 130",
            "#define BLOOM",
            "void main() {",
            "    gl_FragData[0] = vec4(1.0);",
            "    #ifdef BLOOM",
            "    /* DRAWBUFFERS:01 */",
            "    gl_FragData[1] = vec4(1.0);",
            "    #endif",
            "}",
            "");
        GlslDynamicOptions.Result result = transform(source, bool("BLOOM"));

        check(result.blocked.containsKey("BLOOM"), "Render targets can't follow an option");
    }

    private static void blockedExpression()
    {
        String source = String.join("\n",
            "#version 130",
            "#define BLOOM",
            "#define FOG",
            "void main() {",
            "    float x = 1.0",
            "    #ifdef BLOOM",
            "    * 2.0",
            "    #endif",
            "    ;",
            "    #ifdef FOG",
            "    x += 1.0;",
            "    #endif",
            "    gl_FragColor = vec4(x);",
            "}",
            "");
        Map<String, GlslOption> options = new HashMap<>();

        options.putAll(bool("BLOOM"));
        options.putAll(bool("FOG"));

        GlslDynamicOptions.Result result = transform(source, options);

        check(result.blocked.containsKey("BLOOM"), "A conditional inside an expression can't be converted");
        check(result.uniforms.containsKey("FOG"), "The rest still is: " + result.blocked);
        contains(result, "#ifdef BLOOM", "The blocked one stays");
        valid(result, "blockedExpression");
    }

    private static void environmentUntouched()
    {
        String source = String.join("\n",
            "#version 130",
            "#define BLOOM",
            "void main() {",
            "    #ifdef IS_IRIS",
            "    gl_FragColor = vec4(0.0);",
            "    #endif",
            "    #ifdef BLOOM",
            "    gl_FragColor = vec4(1.0);",
            "    #endif",
            "}",
            "");
        GlslDynamicOptions.Result result = transform(source, bool("BLOOM"));

        contains(result, "#ifdef IS_IRIS", "Conditionals on the environment stay for the preprocessor");
        valid(result, "environmentUntouched");
    }

    private static void commentsKept()
    {
        String source = String.join("\n",
            "#version 130",
            "#define BLOOM // [on]",
            "void main() {",
            "    #ifdef BLOOM // keep me",
            "    gl_FragColor = vec4(1.0); /* and me */",
            "    #endif /* end */",
            "}",
            "");
        GlslDynamicOptions.Result result = transform(source, bool("BLOOM"));

        contains(result, "if ((bbs_BLOOM != 0)) { // keep me", "Comment on the directive line stays on its line");
        contains(result, "gl_FragColor = vec4(1.0); /* and me */", "Code comments untouched");
        contains(result, "} /* end */", "#endif comment stays");
        contains(result, "#define BLOOM // [on]", "The option line is untouched");
        valid(result, "commentsKept");
    }

    /** Complementary's depth of field focus: Iris' uniform when automatic, a variable of the same name otherwise */
    private static void variantVariables()
    {
        String source = String.join("\n",
            "#version 130",
            "#define FOCUS 8 // [-1 0 1 2 4 8]",
            "uniform float far, near;",
            "#if FOCUS >= 0",
            "    #if FOCUS == 0",
            "        uniform float centerDepthSmooth;",
            "    #else",
            "        float centerDepthSmooth = (far * (FOCUS - near)) / (FOCUS * (far - near));",
            "    #endif",
            "#endif",
            "varying vec2 uv;",
            "void main() {",
            "    #if FOCUS >= 0",
            "    float coc = abs(uv.x - centerDepthSmooth);",
            "    #else",
            "    float coc = 0.0;",
            "    #endif",
            "    gl_FragColor = vec4(coc);",
            "}",
            "");
        GlslDynamicOptions.Result result = transform(source, value("FOCUS", false));

        check(result.uniforms.containsKey("FOCUS"), "FOCUS is a uniform: " + result.blocked);
        contains(result, "uniform float centerDepthSmooth;", "The uniform keeps its name");
        contains(result, "float centerDepthSmooth_bbs_1 = ", "The variable is renamed");
        contains(result, "abs(uv.x - ((bbs_FOCUS == 0) ? centerDepthSmooth : centerDepthSmooth_bbs_1))", "The read picks one at runtime");
        valid(result, "variantVariables");
    }

    private static void writtenVariantBlocked()
    {
        String source = String.join("\n",
            "#version 130",
            "#define FOCUS 8 // [0 8]",
            "#if FOCUS == 0",
            "uniform float depth;",
            "#else",
            "float depth = 1.0;",
            "#endif",
            "void main() {",
            "    #if FOCUS != 0",
            "    depth += 1.0;",
            "    #endif",
            "    gl_FragColor = vec4(depth);",
            "}",
            "");
        GlslDynamicOptions.Result result = transform(source, value("FOCUS", false));

        check(result.blocked.containsKey("FOCUS"), "A variant that gets written can't be chosen at runtime");
    }

    private static void propertiesLayout()
    {
        String source = String.join("\r\n",
            "screen=[MAIN] BLOOM \\",
            "    FOG SHADOWS",
            "screen.MAIN.columns=2",
            "sliders=FOG",
            "profile.LOW=!BLOOM",
            "# a comment about DOF",
            "program.composite.enabled=DOF",
            "#ifdef TAA",
            "size.buffer.colortex1=0.5 0.5",
            "#endif",
            "");
        java.util.Set<String> used = GlslProperties.usedOptions(source, (name) -> name.equals("BLOOM") || name.equals("FOG") || name.equals("SHADOWS") || name.equals("DOF") || name.equals("TAA"));

        check(used.equals(new java.util.HashSet<>(java.util.Arrays.asList("DOF", "TAA"))), "Only what isn't menu layout or a comment counts: " + used);
    }

    /* Helpers */

    private static GlslDynamicOptions.Result transform(String source, Map<String, GlslOption> options)
    {
        List<String[]> environment = new ArrayList<>();

        environment.add(new String[] {"IS_IRIS", ""});
        environment.add(new String[] {"MC_VERSION", "12004"});

        return GlslDynamicOptions.transform(source, environment, options, "bbs_");
    }

    private static Map<String, GlslOption> bool(String name)
    {
        Map<String, GlslOption> map = new HashMap<>();

        map.put(name, new GlslOption(name, GlslOption.Kind.BOOLEAN));

        return map;
    }

    private static Map<String, GlslOption> value(String name, boolean floating)
    {
        Map<String, GlslOption> map = new HashMap<>();

        map.put(name, new GlslOption(name, floating ? GlslOption.Kind.FLOAT : GlslOption.Kind.INT));

        return map;
    }

    private static int lines(String text)
    {
        return text.split("\n", -1).length;
    }

    private static void contains(GlslDynamicOptions.Result result, String expected, String message)
    {
        boolean ok = result.source.contains(expected);

        check(ok, message);

        if (!ok)
        {
            System.out.println("  expected to find: " + expected);
            System.out.println("  in:\n" + result.source + "\n  blocked: " + result.blocked + (result.error != null ? "\n  error: " + result.error : ""));
        }
    }

    /** The result must preprocess with JCPP the way Iris does and parse as GLSL */
    private static void valid(GlslDynamicOptions.Result result, String name)
    {
        try
        {
            String processed = preprocess(result.source, new String[][] {{"IS_IRIS", ""}, {"MC_VERSION", "12004"}});

            new EnhancedParser(true).parse(processed);
            check(true, name + " parses");
        }
        catch (Exception e)
        {
            check(false, name + " parses: " + e);
            System.out.println(result.source);
        }
    }

    /** What Iris' JcppProcessor does: #version and #extension go to the top, the rest through JCPP */
    static String preprocess(String source, String[][] environment) throws Exception
    {
        StringBuilder lines = new StringBuilder();
        Preprocessor pp = new Preprocessor();

        for (String[] define : environment)
        {
            pp.addMacro(define[0], define[1]);
        }

        pp.setListener(new DefaultPreprocessorListener()
        {
            @Override
            public void handleWarning(Source source, int line, int column, String msg)
            {
                if (msg.startsWith("#warning IRIS_JCPP_GLSL_VERSION"))
                {
                    lines.append(msg.replace("#warning IRIS_JCPP_GLSL_VERSION", "#version ")).append('\n');
                }
                else if (msg.startsWith("#warning IRIS_JCPP_GLSL_EXTENSION"))
                {
                    lines.append(msg.replace("#warning IRIS_JCPP_GLSL_EXTENSION", "#extension ")).append('\n');
                }
            }

            @Override
            public void handleError(Source source, int line, int column, String msg)
            {}
        });

        source = source.replace("#version", "#warning IRIS_JCPP_GLSL_VERSION").replace("#extension", "#warning IRIS_JCPP_GLSL_EXTENSION");
        pp.addInput(new StringLexerSource(source, true));
        pp.addFeature(Feature.KEEPCOMMENTS);

        StringBuilder builder = new StringBuilder();

        for (Token token = pp.token(); token != null && token.getType() != Token.EOF; token = pp.token())
        {
            builder.append(token.getText());
        }

        return lines + builder.toString() + "\n";
    }

    private static void check(boolean condition, String message)
    {
        checks += 1;

        if (!condition)
        {
            failures += 1;
            System.out.println("FAIL: " + message);
        }
    }
}
