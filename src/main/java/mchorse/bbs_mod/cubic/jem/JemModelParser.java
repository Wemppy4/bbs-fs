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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *     does the same); rotations are taken as-is; X is not mirrored.</li>
 * </ul>
 * Boxes are stored with <b>per-face</b> UV ({@link ModelUV}); CEM's box-format {@code textureOffset}
 * is expanded into the six faces by {@link ModelCube#setupBoxUV}.
 *
 * <p>Animations (the {@code "animations"} block) are collected separately into a {@link CemAnimation}
 * — they are a per-frame procedural expression system, not keyframes.</p>
 */
public class JemModelParser
{
    /** Resolves an external {@code "model"} reference (.jpm filename) to its parsed JSON, or null. */
    public interface JpmResolver extends Function<String, JsonObject>
    {}

    /** Result of parsing a .jem: the geometry model plus its (possibly empty) procedural CEM animation. */
    public record Result(Model model, CemAnimation animation)
    {}

    public static Result parse(JsonObject jem, JpmResolver resolver, MolangParser parser)
    {
        return parse(jem, resolver, parser, null);
    }

    public static Result parse(JsonObject jem, JpmResolver resolver, MolangParser parser, Map<String, String> parentOverrides)
    {
        Model model = new Model(parser);
        CemAnimation animation = new CemAnimation();

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
            return new Result(model, animation);
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
            collectAnimations(entry, animation);
        }

        /* Each model entry is a top-level bone (Blockbench keeps the part list flat). */
        for (GroupInfo info : infos.values())
        {
            JsonObject primary = info.defs.get(0);
            Vector3f pivot = translate(primary).negate();

            setupBone(info.group, primary, pivot);

            for (JsonObject def : info.defs)
            {
                readContent(model, info.group, def, ZERO, pivot, 0);
            }

            model.topGroups.add(info.group);
        }

        reparent(model, parentOverrides);

        model.initialize();
        animation.setup(model);

        return new Result(model, animation);
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
    private static void readContent(Model model, ModelGroup group, JsonObject def, Vector3f boxOffset, Vector3f groupOrigin, int depth)
    {
        if (def.has("boxes"))
        {
            for (JsonElement element : def.getAsJsonArray("boxes"))
            {
                group.cubes.add(parseBox(model, element.getAsJsonObject(), boxOffset));
            }
        }

        if (def.has("sprites"))
        {
            for (JsonElement element : def.getAsJsonArray("sprites"))
            {
                group.cubes.add(parseSprite(model, element.getAsJsonObject(), boxOffset));
            }
        }

        if (def.has("submodels"))
        {
            for (JsonElement element : def.getAsJsonArray("submodels"))
            {
                parseSubmodel(model, group, element.getAsJsonObject(), groupOrigin, depth);
            }
        }

        if (def.has("submodel"))
        {
            parseSubmodel(model, group, def.getAsJsonObject("submodel"), groupOrigin, depth);
        }
    }

    private static void parseSubmodel(Model model, ModelGroup parent, JsonObject def, Vector3f parentOrigin, int depth)
    {
        Vector3f origin = translate(def);

        /* Submodels two or more levels deep accumulate the parent submodel's pivot (Blockbench does
         * this only for depth >= 1, so a part's direct submodels keep their own raw translate). */
        if (depth >= 1)
        {
            origin.add(parentOrigin);
        }

        ModelGroup group = new ModelGroup(uniqueId(model, parent, getString(def, "id", null)));

        parent.children.add(group);
        setupBone(group, def, origin);
        readContent(model, group, def, origin, origin, depth + 1);
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

    private static ModelCube parseBox(Model model, JsonObject object, Vector3f offset)
    {
        JsonArray coords = object.getAsJsonArray("coordinates");

        Vector3f size = new Vector3f(coords.get(3).getAsFloat(), coords.get(4).getAsFloat(), coords.get(5).getAsFloat());

        ModelCube cube = new ModelCube();

        cube.size.set(size);
        cube.origin.set(coords.get(0).getAsFloat() + offset.x, coords.get(1).getAsFloat() + offset.y, coords.get(2).getAsFloat() + offset.z);
        cube.pivot.set(cube.origin);

        if (object.has("sizeAdd"))
        {
            cube.inflate = object.get("sizeAdd").getAsFloat();
        }

        setupBoxUV(cube, object, size);
        cube.generateQuads(model.textureWidth, model.textureHeight);

        return cube;
    }

    /** CEM sprites are flat textured quads (a box of depth 1) using a single texture offset. */
    private static ModelCube parseSprite(Model model, JsonObject object, Vector3f offset)
    {
        JsonArray coords = object.getAsJsonArray("coordinates");

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

        cube.generateQuads(model.textureWidth, model.textureHeight);

        return cube;
    }

    /**
     * Build the six per-face {@link ModelUV}s for a box: either the box-format {@code textureOffset}
     * (the standard MC unwrap, handled by {@link ModelCube#setupBoxUV}) or the individual face UVs.
     */
    private static void setupBoxUV(ModelCube cube, JsonObject object, Vector3f size)
    {
        if (object.has("textureOffset"))
        {
            JsonArray uv = object.getAsJsonArray("textureOffset");

            cube.setupBoxUV(new Vector2f(uv.get(0).getAsFloat(), uv.get(1).getAsFloat()), false);

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

    private static String uniqueId(Model model, ModelGroup parent, String id)
    {
        if (id == null)
        {
            id = parent.id + "_sub" + parent.children.size();
        }

        while (model.getGroup(id) != null)
        {
            id = id + "_";
        }

        return id;
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
}
