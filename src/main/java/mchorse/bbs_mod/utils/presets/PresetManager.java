package mchorse.bbs_mod.utils.presets;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PresetManager
{
    public static final PresetManager CLIPS = new PresetManager(BBSMod.getSettingsPath("presets/clips"));
    public static final PresetManager LAYOUTS = new PresetManager(BBSMod.getSettingsPath("presets/layouts"));
    public static final PresetManager PARTICLE_LAYOUTS = new PresetManager(BBSMod.getSettingsPath("presets/particle_layouts"));
    public static final PresetManager BODY_PARTS = new PresetManager(BBSMod.getSettingsPath("presets/body_parts"));
    public static final PresetManager TEXTURES = new PresetManager(BBSMod.getSettingsPath("presets/textures"));
    public static final PresetManager KEYFRAMES = new PresetManager(BBSMod.getSettingsPath("presets/keyframes"));
    public static final PresetManager GUNS = new PresetManager(BBSMod.getSettingsPath("presets/guns"));
    public static final PresetManager ANIMATION_STATES = new PresetManager(BBSMod.getSettingsPath("presets/animation_states"));

    private final File folder;
    private final Path rootPath;

    public PresetManager(File folder)
    {
        this.folder = folder;
        this.folder.mkdirs();

        Path p;

        try
        {
            p = this.folder.getCanonicalFile().toPath();
        }
        catch (IOException e)
        {
            p = this.folder.toPath().toAbsolutePath().normalize();
        }

        this.rootPath = p;
    }

    public File getFolder()
    {
        return this.folder;
    }

    public MapType load(String id)
    {
        File file = this.fileForPresetId(id);

        if (file == null || !file.isFile())
        {
            return null;
        }

        try
        {
            BaseType read = DataToString.read(file);

            if (read.isMap())
            {
                return read.asMap();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public void save(String id, MapType mapType)
    {
        File file = this.fileForPresetId(id);

        if (file == null)
        {
            return;
        }

        File parent = file.getParentFile();

        if (parent != null)
        {
            parent.mkdirs();
        }

        DataToString.writeSilently(file, mapType, true);
    }

    public List<String> listDirectory(String relativePath)
    {
        ArrayList<String> strings = new ArrayList<>();
        File dir = this.directoryForRelative(relativePath);

        if (dir == null || !dir.isDirectory())
        {
            return strings;
        }

        if (relativePath != null && !relativePath.isEmpty())
        {
            strings.add("..");
        }

        File[] files = dir.listFiles();

        if (files == null)
        {
            return strings;
        }

        Arrays.sort(files, (a, b) ->
        {
            if (a.isDirectory() != b.isDirectory())
            {
                return a.isDirectory() ? -1 : 1;
            }

            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File file : files)
        {
            String name = file.getName();

            if (file.isDirectory())
            {
                strings.add(name + "/");
            }
            else if (name.endsWith(".json"))
            {
                strings.add(name.substring(0, name.length() - 5));
            }
        }

        return strings;
    }

    /**
     * Preset ids at repository root only (no subfolders), for legacy call sites.
     */
    public List<String> getKeys()
    {
        List<String> keys = new ArrayList<>();

        for (String s : this.listDirectory(""))
        {
            if (!s.endsWith("/"))
            {
                keys.add(s);
            }
        }

        return keys;
    }

    /**
     * Joins cwd and a segment with {@code /} (preset id path).
     */
    public static String joinRelative(String cwd, String name)
    {
        return cwd.isEmpty() ? name : cwd + "/" + name;
    }

    private File directoryForRelative(String relativePath)
    {
        if (relativePath == null || relativePath.isEmpty())
        {
            return this.folder;
        }

        Path sub = Paths.get(relativePath.replace('/', File.separatorChar));
        Path resolved = this.rootPath.resolve(sub).normalize();

        if (!resolved.startsWith(this.rootPath))
        {
            return null;
        }

        return resolved.toFile();
    }

    private File fileForPresetId(String id)
    {
        if (id == null || id.isEmpty())
        {
            return null;
        }

        Path resolved = this.rootPath.resolve(id + ".json").normalize();

        if (!resolved.startsWith(this.rootPath))
        {
            return null;
        }

        return resolved.toFile();
    }
}
