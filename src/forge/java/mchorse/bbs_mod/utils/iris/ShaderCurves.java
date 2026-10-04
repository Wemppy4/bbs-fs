package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Original BBS curve names and GLSL uniforms on OptiFine's native source/program
 * lifecycle. Aperture 1.12 supplies the insertion boundaries and its rule that
 * preprocessor, array-size and switch-case constants must remain compile-time. */
public final class ShaderCurves
{
    public static final String BRIGHTNESS = "brightness", SUN_ROTATION = "sun_rotation",
        SUN_HORIZONTAL_ROTATION = "sun_horizontal_rotation", WEATHER = "weather", UNIFORM_IDENTIFIER = "bbs_";
    public static final Map<String, ShaderVariable> variableMap = new LinkedHashMap<>();
    private static final Map<Integer, Map<String, Integer>> locations = new HashMap<>();
    private static final Pattern DEFINE = Pattern.compile("(?m)^\\h*#\\h*define\\h+(\\w+)\\h+([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?[fF]?)\\h*(?://[^\\r\\n]*)?$" );
    private static final Pattern CONSTANT = Pattern.compile("\\bconst\\s+(?:lowp\\s+|mediump\\s+|highp\\s+)?\\w+\\s+(\\w+)\\s*=([^;{}]+);");
    private static int processedSources, uploads;
    private ShaderCurves() {}

    public static void reset()
    {
        variableMap.clear();
        processedSources = uploads = 0;
        clearPrograms();
    }

    public static void clearPrograms() { locations.clear(); }
    public static int getProcessedSources() { return processedSources; }
    public static int getUploads() { return uploads; }
    public static Set<Integer> getPrograms() { return new HashSet<>(locations.keySet()); }

    public static BufferedReader processReader(BufferedReader reader) throws IOException
    {
        StringBuilder source = new StringBuilder();
        try (BufferedReader input = reader)
        {
            String line;
            while ((line = input.readLine()) != null) source.append(line).append('\n');
        }
        processedSources++;
        return new BufferedReader(new StringReader(processSource(source.toString())));
    }

    public static String processSource(String source)
    {
        source = ShaderSunRotation.processSource(source);
        if (BBSSettings.shaderCurvesEnabled != null && !BBSSettings.shaderCurvesEnabled.get()) return source;
        Map<String, OptiFineShaderOptions.Option> options = OptiFineShaderOptions.options();
        Map<String, ShaderVariable> variables = new LinkedHashMap<>();
        Matcher definitions = DEFINE.matcher(source);
        while (definitions.find())
        {
            String name = definitions.group(1), value = definitions.group(2);
            OptiFineShaderOptions.Option option = options.get(name);
            boolean integer = !value.matches(".*[.eEfF].*");
            if (option == null || !option.enabled || (integer && !option.slider) || name.equals("WATER_WAVE_ITERATIONS")) continue;
            variables.put(name, new ShaderVariable(name, option.value, integer));
        }
        return rewrite(source, variables, true);
    }

    /** Pure rewrite entry point used by the CPU regression tests; production
     * eligibility is always read from the active pack's actual ShaderOptions. */
    public static String rewrite(String source, Map<String, ShaderVariable> candidates, boolean register)
    {
        Map<String, ShaderVariable> variables = new LinkedHashMap<>(candidates);
        String withoutComments = source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\\r\\n]*", "");
        Matcher directives = Pattern.compile("(?m)^\\h*#\\h*(if|elif|define)\\b([^\\r\\n]*)").matcher(withoutComments);
        while (directives.find())
        {
            String expression = directives.group(2).trim();
            if (directives.group(1).equals("define")) expression = expression.replaceFirst("^\\w+", "");
            removeReferences(variables, expression);
        }
        Matcher dimensions = Pattern.compile("\\[([^]\\r\\n]*)\\]|\\bcase\\s+([^:]+):").matcher(withoutComments);
        while (dimensions.find()) removeReferences(variables, dimensions.group());
        if (variables.isEmpty()) return source;
        source = replaceReferences(source, variables.keySet());
        Set<String> dynamic = new HashSet<>();
        for (String name : variables.keySet()) dynamic.add(UNIFORM_IDENTIFIER + name);
        dynamic.add("get_luminance_from_exposure");
        dynamic.add("get_exposure_from_luminance");
        /* The original BBS deconst propagation, bounded by declarations rather
         * than substrings so const function parameters and comments stay intact. */
        boolean changed;
        do
        {
            changed = false;
            Matcher constants = CONSTANT.matcher(source);
            StringBuffer result = new StringBuffer();
            while (constants.find())
            {
                boolean depends = false;
                for (String name : dynamic) if (containsIdentifier(constants.group(2), name)) { depends = true; break; }
                if (depends)
                {
                    dynamic.add(constants.group(1));
                    constants.appendReplacement(result, Matcher.quoteReplacement(constants.group().replaceFirst("const\\s+", "")));
                    changed = true;
                }
            }
            constants.appendTail(result);
            source = result.toString();
        }
        while (changed);
        StringBuilder uniforms = new StringBuilder();
        for (ShaderVariable variable : variables.values())
        {
            uniforms.append(variable.toUniformDeclaration()).append('\n');
            if (register) variableMap.putIfAbsent(variable.name, variable);
        }
        int version = source.indexOf("#version"), end = source.indexOf('\n', version);
        if (version < 0 || end < 0) return source;
        return source.substring(0, end + 1) + uniforms + source.substring(end + 1);
    }

    private static void removeReferences(Map<String, ShaderVariable> variables, String expression)
    {
        variables.keySet().removeIf(name -> containsIdentifier(expression, name));
    }

    private static boolean containsIdentifier(String text, String name)
    {
        return Pattern.compile("(?<![\\w])" + Pattern.quote(name) + "(?![\\w])").matcher(text).find();
    }

    private static String replaceReferences(String source, Set<String> names)
    {
        StringBuilder out = new StringBuilder(source.length());
        boolean directive = false, lineComment = false, blockComment = false;
        for (int i = 0; i < source.length();)
        {
            char c = source.charAt(i), next = i + 1 < source.length() ? source.charAt(i + 1) : 0;
            if (c == '\n') { directive = false; lineComment = false; }
            if (!lineComment && !blockComment && c == '#') directive = true;
            if (!lineComment && !blockComment && c == '/' && next == '/') lineComment = true;
            if (!lineComment && !blockComment && c == '/' && next == '*') { blockComment = true; out.append("/*"); i += 2; continue; }
            if (blockComment && c == '*' && next == '/') { blockComment = false; out.append("*/"); i += 2; continue; }
            if (!directive && !lineComment && !blockComment && (Character.isLetter(c) || c == '_'))
            {
                int end = i + 1;
                while (end < source.length() && (Character.isLetterOrDigit(source.charAt(end)) || source.charAt(end) == '_')) end++;
                String word = source.substring(i, end);
                if (names.contains(word)) out.append(UNIFORM_IDENTIFIER);
                out.append(word); i = end;
            }
            else { out.append(c); i++; }
        }
        return out.toString();
    }

    /** Called on EVERY OptiFine useProgram return, including the unchanged-program
     * fast path. Values come from the current clip contexts; leaving a clip or
     * stopping playback therefore restores the pack option immediately. */
    public static void applyUniforms()
    {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (program == 0) return;
        Map<String, Integer> uniforms = locations.computeIfAbsent(program, key -> new HashMap<>());
        for (ShaderVariable variable : variableMap.values())
        {
            int location = uniforms.computeIfAbsent(variable.uniformName, name -> GL20.glGetUniformLocation(program, name));
            if (location < 0) continue;
            float value = variable.getValue();
            if (variable.integer) GL20.glUniform1i(location, (int) value);
            else GL20.glUniform1f(location, value);
            uploads++;
        }
        int sun = uniforms.computeIfAbsent(ShaderSunRotation.UNIFORM, name -> GL20.glGetUniformLocation(program, name));
        if (sun >= 0) GL20.glUniform1f(sun, BBSRendering.getSunHorizontalRotation());
    }

    public static class ShaderVariable
    {
        public final String name, uniformName;
        public final boolean integer;
        public final float defaultValue;
        public ShaderVariable(String name, String defaultValue, boolean integer)
        {
            this.name = name;
            this.uniformName = UNIFORM_IDENTIFIER + name;
            this.defaultValue = Float.parseFloat(defaultValue.replaceAll("[fF]$", ""));
            this.integer = integer;
        }
        public String toUniformDeclaration() { return "uniform " + (integer ? "int" : "float") + " " + uniformName + ";"; }
        public float getValue()
        {
            Double value = BBSRendering.getShaderCurveValue(name);
            return value == null || (BBSSettings.shaderCurvesEnabled != null && !BBSSettings.shaderCurvesEnabled.get()) ? defaultValue : value.floatValue();
        }
    }
}
