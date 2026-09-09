package mchorse.bbs_mod.cubic.jem;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.math.molang.MolangParser;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Parse every .jem of an installed pack the way the loader does, and report what the vanilla rig
 * did to it. Run with -Dcem.dir=&lt;folder of .jem files&gt; (defaults to run/resourcepacks/cem).
 *
 * <p>The rig itself is read off a running game ({@code VanillaRigs}), which needs a client; here the
 * hierarchy is supplied from a dump instead (-Dcem.rig=&lt;file&gt;, lines "entity part parent"), or
 * left empty, in which case only parsing and the pack's own structure are checked.</p>
 */
public class CemPackCheck
{
    public static void main(String[] args) throws Exception
    {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));

        Path dir = Path.of(System.getProperty("cem.dir", "run/resourcepacks/cem"));
        Map<String, Map<String, String>> rigs = readRigs(System.getProperty("cem.rig"));

        List<Path> files = new ArrayList<>();

        try (var stream = Files.walk(dir))
        {
            stream.filter((p) -> p.toString().endsWith(".jem")).sorted().forEach(files::add);
        }

        System.out.println("Pack: " + dir.toAbsolutePath() + " (" + files.size() + " models, rig entries for " + rigs.size() + " entities)");

        int failed = 0;
        int reparented = 0;
        int spared = 0;
        List<String> problems = new ArrayList<>();
        Map<String, List<String>> sparedByModel = new TreeMap<>();

        for (Path file : files)
        {
            String name = file.getFileName().toString().replace(".jem", "");
            Map<String, String> parents = rigs.getOrDefault(CemNames.entity(name), Map.of());
            CemHierarchy hierarchy = parents.isEmpty() ? CemHierarchy.NONE : new CemHierarchy(parents, Map.of());

            JsonObject json;

            try
            {
                json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            catch (Exception e)
            {
                failed++;
                problems.add(name + ": unreadable JSON - " + e);

                continue;
            }

            try
            {
                JemModelParser.Result result = JemModelParser.parse(json, (id) -> null, new MolangParser(), hierarchy);
                Model model = result.model();
                CemAnimation program = result.animation();

                /* The program has to run: an expression that throws would take the model down in game. */
                program.setup(model);
                program.apply(program.createState(), null, 0F);

                for (Map.Entry<String, String> entry : parents.entrySet())
                {
                    ModelGroup child = model.getGroup(entry.getKey());

                    if (child == null || model.getGroup(entry.getValue()) == null)
                    {
                        continue;
                    }

                    if (child.parent != null)
                    {
                        reparented++;
                    }
                    else if (program.drivesPlacement(entry.getKey()))
                    {
                        spared++;
                        sparedByModel.computeIfAbsent(name, (k) -> new ArrayList<>())
                            .add(entry.getKey() + " -> " + entry.getValue());
                    }
                }

                for (ModelGroup group : model.getAllGroups())
                {
                    if (!finite(group.current.translate.x) || !finite(group.current.translate.y) || !finite(group.current.translate.z)
                        || !finite(group.current.rotate.x) || !finite(group.current.rotate.y) || !finite(group.current.rotate.z)
                        || !finite(group.current.scale.x) || !finite(group.current.scale.y) || !finite(group.current.scale.z))
                    {
                        problems.add(name + ": bone \"" + group.id + "\" is not finite after one frame");

                        break;
                    }
                }
            }
            catch (Throwable e)
            {
                failed++;
                problems.add(name + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        }

        System.out.println();
        System.out.println("Parsed and ran: " + (files.size() - failed) + " / " + files.size());
        System.out.println("Rig reparented: " + reparented + " bones");
        System.out.println("Rig SPARED by drivesPlacement: " + spared + " bones in " + sparedByModel.size() + " models");

        if (!sparedByModel.isEmpty())
        {
            System.out.println();
            System.out.println("--- bones the pack drives itself, so the rig left them at the top level ---");

            for (Map.Entry<String, List<String>> entry : sparedByModel.entrySet())
            {
                System.out.println("  " + entry.getKey() + ": " + String.join(", ", entry.getValue()));
            }
        }

        if (!problems.isEmpty())
        {
            System.out.println();
            System.out.println("--- problems ---");
            problems.stream().sorted(Comparator.naturalOrder()).forEach((p) -> System.out.println("  " + p));
        }

        System.out.println();
        System.out.println(problems.isEmpty() ? "=== no problems ===" : "=== " + problems.size() + " PROBLEMS ===");
    }

    private static boolean finite(float value)
    {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    /** "entity part parent" per line, as dumped from a running client. */
    private static Map<String, Map<String, String>> readRigs(String path) throws Exception
    {
        Map<String, Map<String, String>> rigs = new LinkedHashMap<>();

        if (path == null || !Files.exists(Path.of(path)))
        {
            return rigs;
        }

        for (String line : Files.readAllLines(Path.of(path), StandardCharsets.UTF_8))
        {
            String[] parts = line.trim().split("\\s+");

            /* "P <entity> <part> <parent>" from the dump; other kinds (pivots) are skipped. */
            if (parts.length == 4 && parts[0].equals("P"))
            {
                rigs.computeIfAbsent(parts[1], (k) -> new LinkedHashMap<>()).put(parts[2], parts[3]);
            }
        }

        return rigs;
    }
}
