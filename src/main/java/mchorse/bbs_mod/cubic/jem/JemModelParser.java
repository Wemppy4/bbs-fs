package mchorse.bbs_mod.cubic.jem;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelUV;
import mchorse.bbs_mod.math.molang.MolangParser;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Parser for OptiFine CEM models (.jem entity model + .jpm part models).
 *
 * <p>It converts the CEM geometry into BBS's existing {@link Model} system. The coordinate handling
 * mirrors Blockbench's own OptiFine JEM importer ({@code js/formats/optifine/optifine_jem.js}) — the
 * authoritative reference — so models look the same as they do in Blockbench:</p>
 * <ul>
 *     <li>A top-level part's pivot is {@code -translate} (all three axes negated); its boxes use
 *     {@code coordinates} directly (no offset).</li>
 *     <li>A submodel's pivot is its own {@code translate} (plus the parent submodel's pivot, for
 *     submodels nested two or more levels deep); its boxes use {@code coordinates + the submodel's
 *     pivot}. A submodel does NOT inherit its parent PART's translate.</li>
 *     <li>{@code invertAxis} is assumed to be the standard {@code "xy"} and is not applied (Blockbench
 *     does the same) — anything else is reported as a warning; rotations are taken as-is; X is not
 *     mirrored.</li>
 * </ul>
 * Boxes are stored with <b>per-face</b> UV ({@link ModelUV}); CEM's box-format {@code textureOffset}
 * is expanded into the six faces by {@link ModelCube#setupBoxUV}, honouring the part's
 * {@code mirrorTexture "u"}. A uniform {@code sizeAdd} becomes the cube's inflate; a per-axis
 * {@code sizesAdd} is baked into the geometry.
 *
 * <p>Animations (the {@code "animations"} block) are collected separately into a {@link CemAnimation}
 * — they are a per-frame procedural expression system, not keyframes.</p>
 *
 * <p>Whatever the parser had to work around (an unsupported attribute, a duplicate bone id, a box
 * without coordinates) is reported once per quirk in {@link Result#warnings()}, for the loader to
 * print against the model's name.</p>
 */
public class JemModelParser
{
    /** Resolves an external {@code "model"} reference (.jpm filename) to its parsed JSON, or null. */
    public interface JpmResolver extends Function<String, JsonObject>
    {}

    /** Result of parsing a .jem: the geometry model, its (possibly empty) procedural CEM animation, and the quirks met on the way. */
    public record Result(Model model, CemAnimation animation, Collection<String> warnings)
    {}

    public static Result parse(JsonObject jem, JpmResolver resolver, MolangParser parser)
    {
        return parse(jem, resolver, parser, null);
    }

    public static Result parse(JsonObject jem, JpmResolver resolver, MolangParser parser, Map<String, String> parentOverrides)
    {
        Parse parse = new Parse(new Model(parser), new CemAnimation());
        Model model = parse.model;

        model.textureWidth = 64;
        model.textureHeight = 64;

        if (jem.has("textureSize"))
        {
            JsonArray size = jem.getAsJsonArray("textureSize");

            model.textureWidth = size.get(0).getAsInt();
            model.textureHeight = size.get(1).getAsInt();
        }

        if (!jem.has("models"))
        {
            return parse.result();
        }

        /* Collect entries, merging the multiple model entries that target the same bone (e.g. the
         * five "root" entries of player.jem) into a single bone with several definitions. */
        Map<String, GroupInfo> infos = new LinkedHashMap<>();

        for (JsonElement element : jem.getAsJsonArray("models"))
        {
            if (!element.isJsonObject())
            {
                continue;
            }

            JsonObject entry = resolveEntry(element.getAsJsonObject(), resolver);
            String id = getString(entry, "id", getString(entry, "part", null));

            if (id == null)
            {
                continue;
            }

            infos.computeIfAbsent(id, GroupInfo::new).defs.add(entry);
            collectAnimations(entry, parse.animation);
        }

        /* The parts' ids are taken before any submodel is named, so a submodel can never shadow a part. */
        parse.ids.addAll(infos.keySet());

        /* Each model entry is a top-level bone (Blockbench keeps the part list flat). */
        for (GroupInfo info : infos.values())
        {
            JsonObject primary = info.defs.get(0);
            Vector3f pivot = translate(primary).negate();

            setupBone(info.group, primary, pivot);

            for (JsonObject def : info.defs)
            {
                readContent(parse, info.group, def, ZERO, pivot, 0);
            }

            model.topGroups.add(info.group);
        }

        reparent(model, parentOverrides);

        model.initialize();
        parse.animation.setup(model);

        return parse.result();
    }

    /**
     * Reparent flat top-level parts onto their vanilla parent (see {@link CemHierarchy}). Geometry is
     * unaffected — BBS composes child bones from their absolute pivots, and a parent with no rest
     * rotation contributes nothing at rest — so a part keeps its rest position while now following the
     * parent's animation and dropping the top-level entity-origin offset.
     */
    private static void reparent(Model model, Map<String, String> parentOverrides)
    {
        if (parentOverrides == null || parentOverrides.isEmpty())
        {
            return;
        }

        Map<String, ModelGroup> byId = new LinkedHashMap<>();

        for (ModelGroup group : model.topGroups)
        {
            byId.put(group.id, group);
        }

        for (Map.Entry<String, String> entry : parentOverrides.entrySet())
        {
            ModelGroup child = byId.get(entry.getKey());
            ModelGroup parent = byId.get(entry.getValue());

            if (child != null && parent != null && child != parent)
            {
                model.topGroups.remove(child);
                parent.children.add(child);
            }
        }
    }

    private static final Vector3f ZERO = new Vector3f();

    /**
     * Add a definition's boxes/sprites to a bone and recurse into its submodels (as child bones).
     *
     * @param boxOffset   offset added to this definition's box coordinates (zero for a top-level part,
     *                    the submodel's own pivot for a submodel).
     * @param groupOrigin this bone's pivot, accumulated into its submodels' translates when depth >= 1.
     */
    private static void readContent(Parse parse, ModelGroup group, JsonObject def, Vector3f boxOffset, Vector3f groupOrigin, int depth)
    {
        boolean geometry = def.has("boxes") || def.has("sprites");

        if (geometry)
        {
            checkInvertAxis(parse, def);
        }

        boolean mirror = mirrorTexture(parse, def);

        if (def.has("boxes"))
        {
            for (JsonElement element : def.getAsJsonArray("boxes"))
            {
                ModelCube cube = parseBox(parse, element.getAsJsonObject(), boxOffset, mirror);

                if (cube != null)
                {
                    group.cubes.add(cube);
                }
            }
        }

        if (def.has("sprites"))
        {
            for (JsonElement element : def.getAsJsonArray("sprites"))
            {
                ModelCube cube = parseSprite(parse, element.getAsJsonObject(), boxOffset);

                if (cube != null)
                {
                    group.cubes.add(cube);
                }
            }
        }

        if (def.has("submodels"))
        {
            for (JsonElement element : def.getAsJsonArray("submodels"))
            {
                parseSubmodel(parse, group, element.getAsJsonObject(), groupOrigin, depth);
            }
        }

        if (def.has("submodel"))
        {
            parseSubmodel(parse, group, def.getAsJsonObject("submodel"), groupOrigin, depth);
        }
    }

    private static void parseSubmodel(Parse parse, ModelGroup parent, JsonObject def, Vector3f parentOrigin, int depth)
    {
        Vector3f origin = translate(def);

        /* Submodels two or more levels deep accumulate the parent submodel's pivot (Blockbench does
         * this only for depth >= 1, so a part's direct submodels keep their own raw translate). */
        if (depth >= 1)
        {
            origin.add(parentOrigin);
        }

        ModelGroup group = new ModelGroup(uniqueId(parse, parent, getString(def, "id", null)));

        parent.children.add(group);
        setupBone(group, def, origin);
        readContent(parse, group, def, origin, origin, depth + 1);
    }

    /**
     * Merge an external .jpm part model (referenced via {@code "model"}) into the entry, letting the
     * entry's own keys win.
     */
    private static JsonObject resolveEntry(JsonObject entry, JpmResolver resolver)
    {
        if (!entry.has("model") || resolver == null)
        {
            return entry;
        }

        JsonObject jpm = resolver.apply(entry.get("model").getAsString());

        if (jpm == null)
        {
            return entry;
        }

        JsonObject merged = jpm.deepCopy();

        for (Map.Entry<String, JsonElement> e : entry.entrySet())
        {
            merged.add(e.getKey(), e.getValue());
        }

        return merged;
    }

    private static void setupBone(ModelGroup group, JsonObject def, Vector3f pivot)
    {
        group.initial.translate.set(pivot);

        if (def.has("rotate"))
        {
            JsonArray r = def.getAsJsonArray("rotate");

            group.initial.rotate.set(r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat());
        }

        group.current.copy(group.initial);
    }

    /**
     * The coordinate handling assumes the standard {@code "xy"} inversion (see the class javadoc). A
     * definition that declares anything else — or nothing at all, which OptiFine reads as no inversion
     * — is laid out in a different convention and may come out flipped; say so instead of guessing.
     */
    private static void checkInvertAxis(Parse parse, JsonObject def)
    {
        String invert = getString(def, "invertAxis", "");

        if (!invert.equalsIgnoreCase("xy"))
        {
            parse.warn("invertAxis \"" + invert + "\" is read as the standard \"xy\" - the part may come out flipped");
        }
    }

    /** Whether the definition mirrors its box UV horizontally ({@code mirrorTexture "u"}); a vertical mirror has no counterpart here. */
    private static boolean mirrorTexture(Parse parse, JsonObject def)
    {
        String mirror = getString(def, "mirrorTexture", "").toLowerCase();

        if (mirror.contains("v"))
        {
            parse.warn("mirrorTexture \"" + mirror + "\": only \"u\" is mirrored, the vertical mirror is ignored");
        }

        return mirror.contains("u");
    }

    private static ModelCube parseBox(Parse parse, JsonObject object, Vector3f offset, boolean mirror)
    {
        JsonArray coords = coordinates(parse, object, "box");

        if (coords == null)
        {
            return null;
        }

        Vector3f size = new Vector3f(coords.get(3).getAsFloat(), coords.get(4).getAsFloat(), coords.get(5).getAsFloat());

        ModelCube cube = new ModelCube();

        cube.size.set(size);
        cube.origin.set(coords.get(0).getAsFloat() + offset.x, coords.get(1).getAsFloat() + offset.y, coords.get(2).getAsFloat() + offset.z);
        cube.pivot.set(cube.origin);

        /* The UV is laid out from the box's authored size - growth never changes it. */
        setupBoxUV(cube, object, mirror);

        if (object.has("sizeAdd"))
        {
            cube.inflate = object.get("sizeAdd").getAsFloat();
        }

        if (object.has("sizesAdd"))
        {
            grow(cube, object.getAsJsonArray("sizesAdd"));
        }

        cube.generateQuads(parse.model.textureWidth, parse.model.textureHeight);

        return cube;
    }

    /**
     * Per-axis growth ({@code sizesAdd}) has no native counterpart - the cube's inflate is uniform - so
     * it is baked into the geometry: the box grows by the amount on both sides of every axis.
     */
    private static void grow(ModelCube cube, JsonArray sizesAdd)
    {
        float x = sizesAdd.get(0).getAsFloat();
        float y = sizesAdd.get(1).getAsFloat();
        float z = sizesAdd.get(2).getAsFloat();

        cube.origin.sub(x, y, z);
        cube.pivot.set(cube.origin);
        cube.size.add(x * 2, y * 2, z * 2);
    }

    /** CEM sprites are flat textured quads (a box of depth 1) using a single texture offset. */
    private static ModelCube parseSprite(Parse parse, JsonObject object, Vector3f offset)
    {
        JsonArray coords = coordinates(parse, object, "sprite");

        if (coords == null)
        {
            return null;
        }

        Vector3f size = new Vector3f(coords.get(3).getAsFloat(), coords.get(4).getAsFloat(), coords.get(5).getAsFloat());

        ModelCube cube = new ModelCube();

        cube.size.set(size);
        cube.origin.set(coords.get(0).getAsFloat() + offset.x, coords.get(1).getAsFloat() + offset.y, coords.get(2).getAsFloat() + offset.z);
        cube.pivot.set(cube.origin);

        if (object.has("textureOffset"))
        {
            JsonArray uv = object.getAsJsonArray("textureOffset");
            float u = uv.get(0).getAsFloat();
            float v = uv.get(1).getAsFloat();

            cube.front = ModelUV.fromXY(u, v, u + size.x, v + size.y);
            cube.back = ModelUV.fromXY(u + size.x, v, u, v + size.y);
        }

        cube.generateQuads(parse.model.textureWidth, parse.model.textureHeight);

        return cube;
    }

    /** A box's/sprite's six {@code coordinates}, or null (with a warning) when they are missing or short - one bad box must not cost the model. */
    private static JsonArray coordinates(Parse parse, JsonObject object, String what)
    {
        JsonArray coords = object.has("coordinates") && object.get("coordinates").isJsonArray() ? object.getAsJsonArray("coordinates") : null;

        if (coords == null || coords.size() < 6)
        {
            parse.warn("a " + what + " without six coordinates was skipped");

            return null;
        }

        return coords;
    }

    /**
     * Build the six per-face {@link ModelUV}s for a box: either the box-format {@code textureOffset}
     * (the standard MC unwrap, handled by {@link ModelCube#setupBoxUV}, mirrored on request) or the
     * individual face UVs, which are taken as authored.
     */
    private static void setupBoxUV(ModelCube cube, JsonObject object, boolean mirror)
    {
        if (object.has("textureOffset"))
        {
            JsonArray uv = object.getAsJsonArray("textureOffset");

            cube.setupBoxUV(new Vector2f(uv.get(0).getAsFloat(), uv.get(1).getAsFloat()), mirror);

            return;
        }

        cube.front = parseFaceUV(object, "uvNorth", "uvFront");
        cube.back = parseFaceUV(object, "uvSouth", "uvBack");
        cube.left = parseFaceUV(object, "uvWest", "uvLeft");
        cube.right = parseFaceUV(object, "uvEast", "uvRight");
        cube.top = parseFaceUV(object, "uvUp", null);
        cube.bottom = parseFaceUV(object, "uvDown", null);
    }

    private static ModelUV parseFaceUV(JsonObject object, String key, String alias)
    {
        JsonArray uv = null;

        if (object.has(key))
        {
            uv = object.getAsJsonArray(key);
        }
        else if (alias != null && object.has(alias))
        {
            uv = object.getAsJsonArray(alias);
        }

        if (uv == null || uv.size() < 4)
        {
            return null;
        }

        return ModelUV.fromXY(uv.get(0).getAsFloat(), uv.get(1).getAsFloat(), uv.get(2).getAsFloat(), uv.get(3).getAsFloat());
    }

    /** Append every {@code "animations"} statement of a model entry, in order, to the CEM animation. */
    private static void collectAnimations(JsonObject entry, CemAnimation animation)
    {
        if (!entry.has("animations"))
        {
            return;
        }

        for (JsonElement element : entry.getAsJsonArray("animations"))
        {
            if (!element.isJsonObject())
            {
                continue;
            }

            for (Map.Entry<String, JsonElement> e : element.getAsJsonObject().entrySet())
            {
                if (e.getValue().isJsonPrimitive())
                {
                    animation.addStatement(e.getKey(), e.getValue().getAsString());
                }
            }
        }
    }

    private static Vector3f translate(JsonObject def)
    {
        if (!def.has("translate"))
        {
            return new Vector3f();
        }

        JsonArray t = def.getAsJsonArray("translate");

        return new Vector3f(t.get(0).getAsFloat(), t.get(1).getAsFloat(), t.get(2).getAsFloat());
    }

    /**
     * A bone id no other bone of this parse has: a duplicate gets underscores appended. Animations
     * address bones by id, so a renamed duplicate is out of their reach - hence the warning.
     */
    private static String uniqueId(Parse parse, ModelGroup parent, String id)
    {
        if (id == null)
        {
            id = parent.id + "_sub" + parent.children.size();
        }

        String unique = id;

        while (!parse.ids.add(unique))
        {
            unique = unique + "_";
        }

        if (!unique.equals(id))
        {
            parse.warn("duplicate bone id \"" + id + "\" renamed to \"" + unique + "\" - animations addressing it drive the first one");
        }

        return unique;
    }

    private static String getString(JsonObject object, String key, String fallback)
    {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    /** A bone being assembled: its {@link ModelGroup} and its merged definitions. */
    private static class GroupInfo
    {
        public final ModelGroup group;
        public final List<JsonObject> defs = new ArrayList<>();

        public GroupInfo(String id)
        {
            this.group = new ModelGroup(id);
        }
    }

    /** One parse's working state: the model being built, its animation, the bone ids taken so far and the quirks met. */
    private static class Parse
    {
        public final Model model;
        public final CemAnimation animation;
        public final Set<String> ids = new HashSet<>();
        public final Set<String> warnings = new LinkedHashSet<>();

        public Parse(Model model, CemAnimation animation)
        {
            this.model = model;
            this.animation = animation;
        }

        /** Note a quirk once - a pack repeats the same one on every part. */
        public void warn(String message)
        {
            this.warnings.add(message);
        }

        public Result result()
        {
            return new Result(this.model, this.animation, this.warnings);
        }
    }
}
