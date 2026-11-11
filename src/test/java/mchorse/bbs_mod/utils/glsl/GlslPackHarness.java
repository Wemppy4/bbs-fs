package mchorse.bbs_mod.utils.glsl;

import io.github.douira.glsl_transformer.parser.EnhancedParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Manual check of {@link GlslDynamicOptions} on real shader packs: resolves includes and finds
 * options roughly like Iris, transforms every program with every option, and checks that what
 * JCPP makes of the result still parses as GLSL wherever the original did.
 *
 * <p>Arguments: shader pack zips or folders; {@code --dump <dir>} writes the transformed sources,
 * {@code --set NAME=value} sets an option as the menu would, {@code --reasons} lists every option
 * that stays as the pack has it.</p>
 */
public class GlslPackHarness
{
    private static final Pattern INCLUDE = Pattern.compile("^\\s*#\\s*include\\s+\"([^\"]+)\"");
    private static final Pattern VALUE_OPTION = Pattern.compile("^\\s*#define\\s+(\\w+)\\s+(-?[\\w.]+)\\s*//(.*)$");
    /* Leading comments repeat once Iris switched an indented "  //#define X" off: "//  //#define X" */
    private static final Pattern BOOLEAN_OPTION = Pattern.compile("^\\s*+((?:/{2,}+\\s*+)++)?#define\\s+(\\w+)\\s*(//.*)?$");
    private static final Pattern IFDEF = Pattern.compile("^\\s*#\\s*(ifdef|ifndef)\\s+(\\w+)\\s*$");
    private static final Pattern LIST = Pattern.compile("\\[([^\\]]*)\\]");
    private static final Pattern NUMBER = Pattern.compile("^-?(\\d+\\.?\\d*|\\.\\d+)f?$");

    /** Option values set like the menu sets them, and whether to list every blocked option */
    static final Map<String, String> values = new HashMap<>();
    private static boolean listReasons;

    public static void main(String[] args) throws Exception
    {
        Path dump = null;
        List<String> packs = new ArrayList<>();

        for (int i = 0; i < args.length; i++)
        {
            if (args[i].equals("--dump"))
            {
                dump = Paths.get(args[++i]);
            }
            else if (args[i].equals("--set"))
            {
                String[] pair = args[++i].split("=", 2);

                values.put(pair[0], pair[1]);
            }
            else if (args[i].equals("--reasons"))
            {
                listReasons = true;
            }
            else
            {
                packs.add(args[i]);
            }
        }

        int failures = 0;

        for (String pack : packs)
        {
            failures += run(Paths.get(pack), dump);
        }

        System.out.println(failures == 0 ? "OK" : failures + " programs broke");
    }

    private static int run(Path pack, Path dump) throws Exception
    {
        Map<String, String> files = read(pack);
        Map<String, GlslOption> options = options(files);
        List<String> programs = new ArrayList<>();

        for (String path : files.keySet())
        {
            if (path.matches("/(world-?\\d+/)?\\w+\\.(vsh|fsh|gsh|csh|tcs|tes)"))
            {
                programs.add(path);
            }
        }

        programs.sort(String::compareTo);

        String[][] environment = environment();
        List<String[]> env = new ArrayList<>();

        for (String[] define : environment)
        {
            env.add(define);
        }

        Map<String, String> blocked = properties(files, options);
        Set<String> animated = new HashSet<>();
        Map<String, Integer> reasons = new TreeMap<>();
        int conditionals = 0;
        int broke = 0;
        int skipped = 0;
        int shown = 0;
        Set<String> shownReasons = new HashSet<>();
        long time = 0;
        long originalSize = 0;
        long transformedSize = 0;

        System.out.println("== " + pack.getFileName() + ": " + programs.size() + " programs, " + options.size() + " options");

        for (String program : programs)
        {
            String source = include(files, program, new HashSet<>());
            Map<String, GlslOption> available = new HashMap<>(options);

            available.keySet().removeAll(blocked.keySet());

            long start = System.nanoTime();
            GlslDynamicOptions.Result result = GlslDynamicOptions.transform(source, env, available, "bbs_");

            time += System.nanoTime() - start;
            conditionals += result.conditionals;
            animated.addAll(result.uniforms.keySet());

            for (Map.Entry<String, String> entry : result.blocked.entrySet())
            {
                if (blocked.putIfAbsent(entry.getKey(), entry.getValue()) == null)
                {
                    reasons.merge(entry.getValue().replaceAll(":.*", "").replaceAll(" \\(line \\d+\\)", ""), 1, Integer::sum);
                }

                Matcher line = Pattern.compile("\\(line (\\d+)\\)").matcher(entry.getValue());

                if (line.find() && shown < 6 && shownReasons.add(program))
                {
                    shown += 1;
                    System.out.println("  " + program + ": " + entry.getValue());

                    String[] lines = source.split("\n", -1);
                    int at = Integer.parseInt(line.group(1)) - 1;

                    for (int i = Math.max(0, at - 6); i <= Math.min(lines.length - 1, at + 2); i++)
                    {
                        System.out.println((i == at ? "  >> " : "     ") + lines[i]);
                    }
                }
            }

            if (result.error != null)
            {
                System.out.println("  " + program + ": not transformed: " + result.error);
            }

            String original = parse(source, environment);
            String transformed = parse(result.source, environment);

            originalSize += preprocessedLength(source, environment);
            transformedSize += preprocessedLength(result.source, environment);

            if (original != null)
            {
                skipped += 1;
            }
            else if (transformed != null)
            {
                broke += 1;
                System.out.println("  BROKE " + program + ": " + transformed);
            }

            if (dump != null)
            {
                Path out = dump.resolve(pack.getFileName().toString()).resolve(program.substring(1));

                Files.createDirectories(out.getParent());
                Files.write(out, result.source.getBytes(StandardCharsets.UTF_8));
                Files.write(out.resolveSibling(out.getFileName() + ".original"), source.getBytes(StandardCharsets.UTF_8));
            }
        }

        animated.removeAll(blocked.keySet());

        System.out.println("  animatable " + animated.size() + ", blocked " + blocked.size() + ", converted conditionals " + conditionals
            + ", transform " + (time / 1_000_000) + " ms, preprocessed size " + originalSize / 1024 + " KB -> " + transformedSize / 1024 + " KB");
        System.out.println("  block reasons " + reasons);
        System.out.println("  originals that don't parse here: " + skipped + ", broken by the transform: " + broke);

        if (listReasons)
        {
            blocked.forEach((name, reason) -> System.out.println("    " + name + ": " + reason));
        }

        return broke;
    }

    /* Pack reading, roughly as Iris does it */

    static Map<String, String> read(Path pack) throws IOException
    {
        Map<String, String> files = new LinkedHashMap<>();

        if (Files.isDirectory(pack))
        {
            Path root = pack.resolve("shaders");

            try (Stream<Path> walk = Files.walk(root))
            {
                for (Path path : (Iterable<Path>) walk::iterator)
                {
                    if (Files.isRegularFile(path) && isShader(path.toString()))
                    {
                        files.put("/" + root.relativize(path).toString().replace('\\', '/'), new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
                    }
                }
            }
        }
        else
        {
            try (ZipFile zip = new ZipFile(pack.toFile()))
            {
                String prefix = null;

                for (ZipEntry entry : java.util.Collections.list(zip.entries()))
                {
                    int index = entry.getName().indexOf("shaders/");

                    if (index >= 0 && (prefix == null || index < prefix.length()))
                    {
                        prefix = entry.getName().substring(0, index + "shaders/".length());
                    }
                }

                for (ZipEntry entry : java.util.Collections.list(zip.entries()))
                {
                    if (!entry.isDirectory() && entry.getName().startsWith(prefix) && isShader(entry.getName()))
                    {
                        try (InputStream stream = zip.getInputStream(entry))
                        {
                            files.put("/" + entry.getName().substring(prefix.length()), new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                        }
                    }
                }
            }
        }

        /* Iris goes over every option line, not just the changed ones */
        files.replaceAll((path, text) -> set(text));

        return files;
    }

    /** Changes the options' values in a file, as Iris does for the ones changed in the menu */
    private static String set(String text)
    {
        StringBuilder builder = new StringBuilder();

        for (String line : text.split("\n", -1))
        {
            String end = line.endsWith("\r") ? "\r" : "";

            line = line.substring(0, line.length() - end.length());

            Matcher value = VALUE_OPTION.matcher(line);
            Matcher bool = BOOLEAN_OPTION.matcher(line);

            if (value.matches() && values.containsKey(value.group(1)) && !line.trim().startsWith("//"))
            {
                line = line.substring(0, value.start(2)) + values.get(value.group(1)) + line.substring(value.end(2));
            }
            else if (bool.matches())
            {
                /* On/off, as Iris' OptionAnnotatedSource does: an off option gets "//" in front of
                 * its whole line, even one already commented ("  //#define X" -> "//  //#define X"),
                 * an on one loses its leading comment */
                String chosen = values.get(bool.group(2));
                boolean commented = bool.start(1) >= 0;
                boolean on = chosen == null || chosen.matches("-?[\\d.]+") ? !commented : chosen.equals("true");

                if (on && commented)
                {
                    line = line.substring(bool.end(1));
                }
                else if (!on)
                {
                    line = "//" + line;
                }
            }

            builder.append(line).append(end).append('\n');
        }

        return builder.substring(0, builder.length() - 1);
    }

    /** Options the properties files read: Iris preprocesses them with the options' values */
    static Map<String, String> properties(Map<String, String> files, Map<String, GlslOption> options)
    {
        Map<String, String> blocked = new TreeMap<>();

        for (Map.Entry<String, String> file : files.entrySet())
        {
            if (file.getKey().matches("/(shaders|block|item|entity)\\.properties"))
            {
                for (String name : GlslProperties.usedOptions(file.getValue(), options::containsKey))
                {
                    blocked.putIfAbsent(name, "the pack's properties use it");
                }
            }
        }

        return blocked;
    }

    private static boolean isShader(String name)
    {
        return name.matches(".*\\.(glsl|vsh|fsh|gsh|csh|tcs|tes|inc|properties)$");
    }

    static String include(Map<String, String> files, String path, Set<String> stack)
    {
        String text = files.get(path);

        if (text == null || !stack.add(path))
        {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        for (String line : text.split("\\R", -1))
        {
            Matcher matcher = INCLUDE.matcher(line);

            if (matcher.find())
            {
                String target = matcher.group(1);

                if (!target.startsWith("/"))
                {
                    target = path.substring(0, path.lastIndexOf('/') + 1) + target;
                }

                builder.append(include(files, Paths.get(target).normalize().toString().replace('\\', '/'), stack));
            }
            else
            {
                builder.append(line).append('\n');
            }
        }

        stack.remove(path);

        return builder.toString();
    }

    /** Options like Iris finds them: value defines with a list, and on/off defines some #ifdef tests */
    static Map<String, GlslOption> options(Map<String, String> files)
    {
        Map<String, GlslOption> options = new HashMap<>();
        Set<String> booleans = new HashSet<>();
        Set<String> settings = new HashSet<>();
        Set<String> referenced = new HashSet<>();

        for (Map.Entry<String, String> file : files.entrySet())
        {
            if (file.getKey().endsWith(".properties"))
            {
                continue;
            }

            /* An on/off define without a comment in a program file marks the program, not a setting */
            boolean program = file.getKey().matches(".*\\.(vsh|fsh|gsh|csh|tcs|tes)$");

            for (String line : file.getValue().split("\\R"))
            {
                Matcher value = VALUE_OPTION.matcher(line);

                if (value.matches() && !line.trim().startsWith("//"))
                {
                    Matcher list = LIST.matcher(value.group(3));

                    if (list.find() && NUMBER.matcher(value.group(2)).matches())
                    {
                        boolean floating = value.group(2).contains(".");
                        boolean numbers = true;

                        for (String allowed : list.group(1).trim().split("\\s+"))
                        {
                            numbers &= allowed.isEmpty() || NUMBER.matcher(allowed).matches();
                            floating |= allowed.contains(".");
                        }

                        if (numbers)
                        {
                            options.put(value.group(1), new GlslOption(value.group(1), floating ? GlslOption.Kind.FLOAT : GlslOption.Kind.INT));
                        }
                    }

                    continue;
                }

                Matcher bool = BOOLEAN_OPTION.matcher(line);

                if (bool.matches())
                {
                    booleans.add(bool.group(2));

                    if (!program || bool.group(3) != null)
                    {
                        settings.add(bool.group(2));
                    }
                }

                Matcher ifdef = IFDEF.matcher(line);

                if (ifdef.matches())
                {
                    referenced.add(ifdef.group(2));
                }
            }
        }

        for (String name : booleans)
        {
            if (referenced.contains(name) && settings.contains(name) && !options.containsKey(name))
            {
                options.put(name, new GlslOption(name, GlslOption.Kind.BOOLEAN));
            }
        }

        return options;
    }

    static String[][] environment()
    {
        List<String[]> defines = new ArrayList<>();

        defines.add(new String[] {"MC_VERSION", "12004"});
        defines.add(new String[] {"MC_GL_VERSION", "460"});
        defines.add(new String[] {"MC_GLSL_VERSION", "460"});
        defines.add(new String[] {"MC_OS_WINDOWS", ""});
        defines.add(new String[] {"MC_GL_VENDOR_NVIDIA", ""});
        defines.add(new String[] {"MC_GL_RENDERER_GEFORCE", ""});
        defines.add(new String[] {"IS_IRIS", ""});
        defines.add(new String[] {"IRIS_TAG_SUPPORT", ""});
        defines.add(new String[] {"MC_NORMAL_MAP", ""});
        defines.add(new String[] {"MC_SPECULAR_MAP", ""});
        defines.add(new String[] {"MC_RENDER_QUALITY", "1.0"});
        defines.add(new String[] {"MC_SHADOW_QUALITY", "1.0"});
        defines.add(new String[] {"MC_HAND_DEPTH", "0.125"});
        defines.add(new String[] {"IRIS_FEATURE_SSBO", ""});
        defines.add(new String[] {"IRIS_FEATURE_CUSTOM_IMAGES", ""});
        defines.add(new String[] {"IRIS_FEATURE_SEPARATE_HARDWARE_SAMPLERS", ""});
        defines.add(new String[] {"IRIS_FEATURE_COMPUTE_SHADERS", ""});
        defines.add(new String[] {"IRIS_FEATURE_ENTITY_TRANSLUCENT", ""});

        String[] stages = {"NONE", "SKY", "SUNSET", "CUSTOM_SKY", "SUN", "MOON", "STARS", "VOID", "TERRAIN_SOLID", "TERRAIN_CUTOUT_MIPPED",
            "TERRAIN_CUTOUT", "ENTITIES", "BLOCK_ENTITIES", "DESTROY", "OUTLINE", "DEBUG", "HAND_SOLID", "TERRAIN_TRANSLUCENT", "TRIPWIRE",
            "PARTICLES", "CLOUDS", "RAIN_SNOW", "WORLD_BORDER", "HAND_TRANSLUCENT"};

        for (int i = 0; i < stages.length; i++)
        {
            defines.add(new String[] {"MC_RENDER_STAGE_" + stages[i], String.valueOf(i)});
        }

        return defines.toArray(new String[0][]);
    }

    /** Null when what JCPP makes of the source parses as GLSL, the error otherwise */
    private static String parse(String source, String[][] environment)
    {
        try
        {
            new EnhancedParser(true).parse(GlslDynamicOptionsTest.preprocess(source, environment));

            return null;
        }
        catch (Throwable e)
        {
            String message = String.valueOf(e.getMessage());

            return e.getClass().getSimpleName() + ": " + message.substring(0, Math.min(300, message.length()));
        }
    }

    private static long preprocessedLength(String source, String[][] environment)
    {
        try
        {
            return GlslDynamicOptionsTest.preprocess(source, environment).length();
        }
        catch (Exception e)
        {
            return 0;
        }
    }
}
