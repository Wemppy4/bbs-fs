package mchorse.bbs_mod.utils.iris;

import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.utils.glsl.GlslDynamicOptions;
import mchorse.bbs_mod.utils.glsl.GlslOption;
import mchorse.bbs_mod.utils.glsl.GlslProperties;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.option.BooleanOption;
import net.irisshaders.iris.shaderpack.option.MergedBooleanOption;
import net.irisshaders.iris.shaderpack.option.MergedStringOption;
import net.irisshaders.iris.shaderpack.option.OptionLocation;
import net.irisshaders.iris.shaderpack.option.OptionSet;
import net.irisshaders.iris.shaderpack.option.OptionType;
import net.irisshaders.iris.shaderpack.option.StringOption;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the options of the shader pack Iris is loading into uniforms ({@link ShaderCurves}
 * keeps them). Every program source goes through {@link GlslDynamicOptions} before Iris
 * preprocesses it: value options become uniforms, and so do the on/off and value options that
 * preprocessor conditionals ({@code #ifdef}, {@code #if X == 2}...) test, their blocks becoming
 * runtime branches. An option that can't follow everywhere stays as the pack has it, in every
 * program.
 *
 * <p>If the pack then fails to build, it's reloaded with conditionals left alone, and failing
 * again, with no options turned into uniforms at all. Only Iris' mixins call this class.</p>
 */
public class IrisShaderCurves
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<String> prohibitedVariables = new HashSet<>();

    /** The loaded pack's options as Iris found them, and the ones that may become uniforms */
    private static OptionSet optionSet;
    private static Map<String, GlslOption> candidates;

    /** How far it goes for a pack that failed to build, by pack name */
    private static final Map<String, Mode> modes = new HashMap<>();
    private static int conditionals;

    /** Iris names the pack only after reading it, so the name is taken when loading starts */
    private static String pack;

    public enum Mode
    {
        /** Value options and conditionals */
        FULL,
        /** Value options used outside of conditionals only */
        VALUES,
        OFF
    }

    static
    {
        /* photon & Hysteria */
        prohibitedVariables.add("WATER_WAVE_ITERATIONS");
    }

    public static synchronized void reset(String name)
    {
        pack = name;
        ShaderCurves.variableMap.clear();
        ShaderCurves.blocked.clear();
        optionSet = null;
        candidates = null;
        conditionals = 0;
        ShaderOptions.changed();
    }

    public static synchronized void finishLoading()
    {
        int animated = ShaderCurves.getCurvable().size();

        ShaderOptions.changed();

        if (animated > 0 || !ShaderCurves.blocked.isEmpty())
        {
            LOGGER.info("Shader curves: {} options can be animated ({} conditionals became runtime code), {} stay as the pack has them", animated, conditionals, ShaderCurves.blocked.size());
            LOGGER.debug("Shader curves: options staying as the pack has them: {}", ShaderCurves.blocked);
        }
    }

    /** Iris found the pack's options (called before any program source is read) */
    public static synchronized void setOptions(OptionSet set)
    {
        optionSet = set;
        candidates = null;
    }

    public static synchronized String processSource(String source, Iterable<StringPair> environment)
    {
        source = ShaderSunRotation.processSource(source);

        Mode mode = mode();

        if (!BBSSettings.shaderCurvesEnabled.get() || mode == Mode.OFF || optionSet == null)
        {
            return source;
        }

        Map<String, GlslOption> options = new HashMap<>(candidates());

        options.keySet().removeAll(ShaderCurves.blocked.keySet());

        if (options.isEmpty())
        {
            return source;
        }

        List<String[]> defines = new ArrayList<>();

        for (StringPair pair : environment)
        {
            defines.add(new String[] {pair.key(), pair.value()});
        }

        GlslDynamicOptions.Result result = GlslDynamicOptions.transform(source, defines, options, ShaderCurves.UNIFORM_IDENTIFIER, mode == Mode.FULL);

        if (result.error != null)
        {
            LOGGER.warn("Shader curves: a program couldn't be read, its options stay as the pack has them: {}", result.error);
        }

        result.blocked.forEach(ShaderCurves.blocked::putIfAbsent);
        conditionals += result.conditionals;

        for (GlslOption option : result.uniforms.values())
        {
            ShaderCurves.variableMap.computeIfAbsent(option.name, (name) -> new ShaderCurves.ShaderVariable(option, result.values.get(name)));
        }

        return result.source;
    }

    /**
     * A properties file of the pack goes through the preprocessor with the options' values:
     * options it reads (outside of the menu layout) choose things a uniform can't change, such
     * as which programs run or the block ids.
     */
    public static synchronized void processProperties(String source)
    {
        if (optionSet == null)
        {
            return;
        }

        Set<String> used = GlslProperties.usedOptions(source, (name) -> optionSet.getBooleanOptions().containsKey(name) || optionSet.getStringOptions().containsKey(name));

        for (String name : used)
        {
            ShaderCurves.blocked.putIfAbsent(name, "the pack's properties use it");
        }
    }

    /**
     * The pack failed to build: with options turned into uniforms, try again going less far
     * (conditionals left alone, then no uniforms at all). True when a reload is coming.
     */
    public static synchronized boolean onPipelineFailed()
    {
        Mode mode = mode();

        if (mode == Mode.OFF || ShaderCurves.variableMap.isEmpty())
        {
            return false;
        }

        Mode next = mode == Mode.FULL && conditionals > 0 ? Mode.VALUES : Mode.OFF;

        modes.put(pack, next);
        LOGGER.warn("Shader curves: {} didn't build with its options as uniforms, reloading it {}", pack, next == Mode.VALUES ? "without converting conditionals" : "without shader curves");

        MinecraftClient mc = MinecraftClient.getInstance();

        /* Not right away: Iris is still handling the failed build, and after it returns it turns
         * shaders off, which would throw away the pipeline a reload made in there */
        ShaderOptions.reload(() ->
        {
            if (mc.player != null)
            {
                mc.player.sendMessage(Text.literal((next == Mode.VALUES ? UIKeys.CAMERA_PANELS_SHADER_FALLBACK_VALUES : UIKeys.CAMERA_PANELS_SHADER_FALLBACK_OFF).get()), false);
            }

            Iris.reload();
        }, () -> LOGGER.error("Shader curves: couldn't reload the shader pack"));

        return true;
    }

    private static Mode mode()
    {
        return pack == null ? Mode.FULL : modes.getOrDefault(pack, Mode.FULL);
    }

    /**
     * Options that may become uniforms: on/off and numeric defines. An on/off define without a
     * comment that only program files set, which no menu shows, marks the program
     * ("#define OVERWORLD" in world0/composite.fsh) rather than being a setting.
     */
    private static Map<String, GlslOption> candidates()
    {
        if (candidates != null)
        {
            return candidates;
        }

        Map<String, GlslOption> map = new HashMap<>();
        Set<String> menu = menuOptions();

        for (MergedBooleanOption merged : optionSet.getBooleanOptions().values())
        {
            BooleanOption option = merged.getOption();

            if (option.getType() != OptionType.DEFINE || prohibitedVariables.contains(option.getName()))
            {
                continue;
            }

            boolean marker = option.getDefaultValue() && option.getComment().isEmpty() && !menu.contains(option.getName());

            for (OptionLocation location : merged.getLocations())
            {
                marker &= isProgram(location.filePath().getPathString());
            }

            if (!marker)
            {
                map.put(option.getName(), new GlslOption(option.getName(), GlslOption.Kind.BOOLEAN));
            }
        }

        for (MergedStringOption merged : optionSet.getStringOptions().values())
        {
            StringOption option = merged.getOption();
            GlslOption.Kind kind = option.getType() == OptionType.DEFINE ? numericKind(option) : null;

            if (kind != null && !prohibitedVariables.contains(option.getName()))
            {
                map.put(option.getName(), new GlslOption(option.getName(), kind));
            }
        }

        candidates = map;

        return map;
    }

    private static Set<String> menuOptions()
    {
        ShaderProperties properties = IrisUtils.getShaderProperties();
        Set<String> menu = new HashSet<>();

        if (properties != null)
        {
            properties.getMainScreenOptions().ifPresent(menu::addAll);
            properties.getSubScreenOptions().values().forEach(menu::addAll);
            menu.addAll(properties.getSliderOptions());
        }

        return menu;
    }

    private static boolean isProgram(String path)
    {
        return path.endsWith(".vsh") || path.endsWith(".fsh") || path.endsWith(".gsh") || path.endsWith(".csh")
            || path.endsWith(".tcs") || path.endsWith(".tes");
    }

    /** INT or FLOAT when every value the option allows is a number, null otherwise */
    private static GlslOption.Kind numericKind(StringOption option)
    {
        List<String> values = new ArrayList<>(option.getAllowedValues());
        boolean floating = false;

        values.add(option.getDefaultValue());

        for (String value : values)
        {
            if (ShaderCurves.parse(value) == null || value.equals("true") || value.equals("false"))
            {
                return null;
            }

            floating |= value.contains(".") || value.contains("e") || value.contains("E");
        }

        return floating ? GlslOption.Kind.FLOAT : GlslOption.Kind.INT;
    }
}
