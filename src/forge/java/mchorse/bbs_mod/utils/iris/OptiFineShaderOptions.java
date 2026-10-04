package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.BBSMod;
import java.lang.reflect.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native OptiFine option/menu snapshot. Uses its public API, without distributing
 * compile-time net.optifine stubs or linking an optional dependency into BBS. */
public final class OptiFineShaderOptions
{
    private static Class<?> shaders;
    private static boolean discovered;
    private OptiFineShaderOptions() {}

    private static Class<?> api()
    {
        if (!discovered)
        {
            discovered = true;
            try { shaders = Class.forName("net.optifine.shaders.Shaders"); }
            catch (ClassNotFoundException absent) { /* Optional dependency. */ }
        }
        return shaders;
    }

    public static boolean available()
    {
        try { return api() != null && api().getField("shaderPackLoaded").getBoolean(null); }
        catch (ReflectiveOperationException error) { throw failure(error); }
    }

    public static Map<String, Option> options()
    {
        Map<String, Option> result = new LinkedHashMap<>();
        if (api() == null) return result;
        try
        {
            Object[] options = (Object[]) api().getMethod("getShaderPackOptions").invoke(null);
            if (options != null) for (Object option : options)
            {
                if (option != null)
                {
                    Option value = new Option(option);
                    result.put(value.name, value);
                }
            }
        }
        catch (ReflectiveOperationException error) { throw failure(error); }
        return result;
    }

    public static Map<String, String> language(String language)
    {
        Map<String, String> result = new HashMap<>();
        if (!available()) return result;
        readLanguageVariants(result, "en_us");
        if (!"en_us".equalsIgnoreCase(language)) readLanguageVariants(result, language);
        return result;
    }

    private static void readLanguageVariants(Map<String, String> result, String language)
    {
        String normalized = language.toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf('_');
        if (separator > 0) readLanguage(result, normalized.substring(0, separator + 1) + normalized.substring(separator + 1).toUpperCase(Locale.ROOT));
        readLanguage(result, normalized);
    }

    private static void readLanguage(Map<String, String> result, String language)
    {
        try
        {
            Method resource = api().getMethod("getShaderPackResourceStream", String.class);
            try (InputStream stream = (InputStream) resource.invoke(null, "/shaders/lang/" + language + ".lang"))
            {
                if (stream == null) return;
                Properties properties = new Properties();
                properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                for (String key : properties.stringPropertyNames()) result.put(key, properties.getProperty(key));
            }
        }
        catch (ReflectiveOperationException | IOException error)
        {
            BBSMod.LOGGER.warn("Unable to read shader-pack language " + language, error);
        }
    }

    public static ShaderMenu menu()
    {
        if (!available()) return null;
        try
        {
            Set<String> inMenu = new HashSet<>();
            Map<String, ShaderMenu.Screen> screens = new LinkedHashMap<>();
            ShaderMenu.Screen main = screen(null, inMenu);
            ArrayDeque<String> pending = new ArrayDeque<>();
            collectLinks(main, pending);
            while (!pending.isEmpty())
            {
                String id = pending.removeFirst();
                if (screens.containsKey(id)) continue;
                ShaderMenu.Screen screen = screen(id, inMenu);
                screens.put(id, screen);
                collectLinks(screen, pending);
            }
            Set<String> animated = new HashSet<>(inMenu);
            animated.retainAll(ShaderCurves.variableMap.keySet());
            Set<String> fixed = new HashSet<>(inMenu);
            fixed.removeAll(animated);
            Set<String> hidden = new HashSet<>(ShaderCurves.variableMap.keySet());
            hidden.removeAll(inMenu);
            return new ShaderMenu(main, screens, animated, fixed, hidden);
        }
        catch (ReflectiveOperationException error) { throw failure(error); }
    }

    private static void collectLinks(ShaderMenu.Screen screen, Deque<String> links)
    {
        for (ShaderMenu.Cell cell : screen.cells)
            if (cell.type == ShaderMenu.CellType.LINK) links.addLast(cell.targetScreenId);
    }

    private static ShaderMenu.Screen screen(String id, Set<String> inMenu) throws ReflectiveOperationException
    {
        Object[] options = (Object[]) api().getMethod("getShaderPackOptions", String.class).invoke(null, id);
        int columns = (Integer) api().getMethod("getShaderPackColumns", String.class, int.class).invoke(null, id, 2);
        List<ShaderMenu.Cell> cells = new ArrayList<>();
        if (options != null) for (Object object : options)
        {
            if (object == null) { cells.add(ShaderMenu.Cell.empty()); continue; }
            Option option = new Option(object);
            if (!option.visible) continue;
            String type = object.getClass().getSimpleName();
            if (type.equals("ShaderOptionScreen")) cells.add(ShaderMenu.Cell.link(option.name));
            else if (type.equals("ShaderOptionProfile")) cells.add(ShaderMenu.Cell.profile(option.value, option.values.size()));
            else
            {
                inMenu.add(option.name);
                cells.add(ShaderMenu.Cell.option(option.name, option.slider, option.booleanOption,
                    option.enabled && ShaderCurves.variableMap.containsKey(option.name), option.value, option.values, option.values.indexOf(option.value)));
            }
        }
        return new ShaderMenu.Screen(id == null ? ShaderMenu.MAIN_SCREEN : id, columns, cells);
    }

    public static void reload()
    {
        if (api() == null) return;
        try
        {
            api().getMethod("loadShaderPack").invoke(null);
            api().getMethod("uninit").invoke(null);
        }
        catch (ReflectiveOperationException error) { throw failure(error); }
    }

    private static IllegalStateException failure(Exception error)
    {
        return new IllegalStateException("OptiFine shader option API failed", error);
    }

    public static final class Option
    {
        public final String name, value;
        public final boolean enabled, visible, slider, booleanOption;
        public final List<String> values;
        private Option(Object option) throws ReflectiveOperationException
        {
            Class<?> type = option.getClass();
            name = (String) type.getMethod("getName").invoke(option);
            value = (String) type.getMethod("getValue").invoke(option);
            enabled = (Boolean) type.getMethod("isEnabled").invoke(option);
            visible = (Boolean) type.getMethod("isVisible").invoke(option);
            String[] entries = (String[]) type.getMethod("getValues").invoke(option);
            values = entries == null ? Collections.emptyList() : Arrays.asList(entries);
            slider = (Boolean) api().getMethod("isShaderPackOptionSlider", String.class).invoke(null, name);
            booleanOption = values.size() == 2 && values.contains("true") && values.contains("false");
        }
    }
}
