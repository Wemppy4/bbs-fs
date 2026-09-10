package mchorse.bbs_mod.data.migration;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.core.StableIds;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueStableList;

import java.util.Collections;
import java.util.Map;

/** Raw 2.5 save shapes, deliberately independent of Minecraft's registries. */
public class StableIdsMigrationTest
{
    private static int failures;

    public static void main(String[] args)
    {
        run("nested tracks and states", StableIdsMigrationTest::nestedTracks);
        run("anchor target and camera references", StableIdsMigrationTest::references);
        run("independent reads", StableIdsMigrationTest::independentReads);
        run("idempotence", StableIdsMigrationTest::idempotence);
        run("legacy bodyParts anchors", StableIdsMigrationTest::legacyBodyParts);
        run("duplicate saved ids", StableIdsMigrationTest::duplicateIds);
        run("partially converted references", StableIdsMigrationTest::partialReferences);
        run("missing targets", StableIdsMigrationTest::missingTargets);
        run("track rename collision", StableIdsMigrationTest::trackCollision);
        run("replay preset and axes attachment", StableIdsMigrationTest::replayPreset);
        run("binary round trip and list edits", StableIdsMigrationTest::roundTrip);
        run("reserved ids", StableIdsMigrationTest::reservedIds);

        if (failures > 0)
        {
            throw new AssertionError(failures + " migration checks failed");
        }
    }

    private static void nestedTracks()
    {
        MapType form = form();
        MapType nestedState = map("properties", map("0/pose", channel("pose", new MapType())));
        form.getList("parts").getMap(0).getMap("form").put("states", list(nestedState));
        MapType state = map("properties", map("0/0/pose", channel("pose", new MapType())));
        form.put("states", list(state));
        Map<String, String> ids = FormStableIds.ensure(form);
        check(StableIds.isStableId(ids.get("0")), "part id format");
        check(state.getMap("properties").has(ids.get("0/0") + "/pose"), "root state nested track");
        check(nestedState.getMap("properties").has(ids.get("0/0").split("/")[1] + "/pose"), "nested state relative path");
        MapType tracks = map("tracks", list(map("form", "0/0", "kind", "bone", "subject", "Head")));
        FormStableIds.rewriteFormPaths(tracks, ids);
        check(tracks.getList("tracks").getMap(0).getString("form").equals(ids.get("0/0")), "track list path");
        check(FormStableIds.rewriteTrackKey("pose", ids).equals("pose"), "root property unchanged");
        check(FormStableIds.rewriteTrackKey("99/pose", ids).equals("99/pose"), "orphan retained");
    }

    private static void references()
    {
        MapType film = film();
        new FilmStableIds().migrate(film);
        MapType target = film.getList("replays").getMap(1);
        String targetId = target.getString("id");
        String path = path(target.getMap("form"));
        MapType owner = film.getList("replays").getMap(0);
        MapType anchor = owner.getMap("form").getMap("anchor");
        check(anchor.getString("actor").equals(targetId), "static anchor replay");
        check(anchor.getString("attachment").equals(path + "/Head"), "anchor uses target form");
        MapType value = owner.getMap("properties").getMap("anchor").getList("keyframes").getMap(0).getMap("value");
        check(value.equals(anchor), "animated anchor converted");
        MapType clip = film.getList("camera").getMap(0);
        check(clip.getString("selector").equals(targetId), "camera replay");
        check(clip.getString("group").equals(path + "/Head"), "tracker attachment");
    }

    private static void independentReads()
    {
        MapType first = film();
        MapType second = (MapType) first.copy();
        new FilmStableIds().migrate(first);
        new FilmStableIds().migrate(second);
        check(first.equals(second), "two reads of unchanged legacy data must assign identical identities");
    }

    private static void idempotence()
    {
        MapType data = film();
        new FilmStableIds().migrate(data);
        BaseType once = data.copy();
        new FilmStableIds().migrate(data);
        check(once.equals(data), "repeat migration must preserve ids and references");
    }

    private static void legacyBodyParts()
    {
        MapType data = film();
        MapType owner = data.getList("replays").getMap(0).getMap("form");
        MapType nested = owner.getList("parts").getMap(0).getMap("form");
        nested.put("anchor", anchor(1));
        nested.put("states", list(map("properties", map("anchor", channel("anchor", anchor(1))))));
        owner.put("bodyParts", map("parts", owner.getList("parts")));
        owner.remove("parts");
        new FilmStableIds().migrate(data);
        String id = data.getList("replays").getMap(1).getString("id");
        check(nested.getMap("anchor").getString("actor").equals(id), "nested legacy static anchor");
        check(nested.getList("states").getMap(0).getMap("properties").getMap("anchor").getList("keyframes").getMap(0).getMap("value").getString("actor").equals(id), "nested legacy state anchor");
    }

    private static void duplicateIds()
    {
        MapType data = film();
        for (BaseType replay : data.getList("replays")) replay.asMap().putString("id", "abcdef01");
        MapType form = data.getList("replays").getMap(1).getMap("form");
        form.getList("parts").getMap(0).putString("id", "abcdef02");
        form.getList("parts").add(map("id", "abcdef02", "form", new MapType()));
        MapType properties = map("1/pose", channel("pose", new MapType()));
        data.getList("replays").getMap(1).put("properties", properties);
        new FilmStableIds().migrate(data);
        check(!data.getList("replays").getMap(0).getString("id").equals(data.getList("replays").getMap(1).getString("id")), "replay ids unique before references resolve");
        String second = form.getList("parts").getMap(1).getString("id");
        check(!second.equals("abcdef02"), "body part ids unique before tracks resolve");
        check(properties.has(second + "/pose"), "duplicate's positional track uses repaired id");
    }

    private static void partialReferences()
    {
        MapType data = film();
        data.getList("replays").getMap(1).putString("id", "abcdef01");
        MapType owner = data.getList("replays").getMap(0).getMap("form");
        owner.getMap("anchor").putString("actor", "abcdef01");
        data.getList("camera").getMap(0).putString("selector", "abcdef01");
        new FilmStableIds().migrate(data);
        String path = path(data.getList("replays").getMap(1).getMap("form"));
        check(owner.getMap("anchor").getString("attachment").equals(path + "/Head"), "stable actor with legacy attachment");
        check(data.getList("camera").getMap(0).getString("group").equals(path + "/Head"), "stable selector with legacy group");
    }

    private static void missingTargets()
    {
        MapType data = film();
        data.getList("replays").getMap(0).getMap("form").getMap("anchor").putInt("actor", 99);
        data.getList("camera").getMap(0).putInt("selector", -1);
        new FilmStableIds().migrate(data);
        check(data.getList("replays").getMap(0).getMap("form").getMap("anchor").getString("actor").isEmpty(), "dangling anchor cleared");
        check(data.getList("camera").getMap(0).getString("selector").isEmpty(), "unset selector stays unset");
    }

    private static void trackCollision()
    {
        MapType tracks = map("0/pose", channel("pose", map("legacy", true)), "abcdef01/pose", channel("pose", map("current", true)));
        BaseType current = tracks.get("abcdef01/pose").copy();
        FormStableIds.rewriteTrackKeys(tracks, Map.of("0", "abcdef01"));
        check(tracks.size() == 2 && tracks.get("abcdef01/pose").equals(current), "rename must not erase either authored track");
    }

    private static void replayPreset()
    {
        MapType replay = map("form", form(), "properties", map("0/0/pose", channel("pose", new MapType())), "axes_preview_bone", "0/0/Head");
        FormStableIds.ensureReplay(replay);
        String path = path(replay.getMap("form"));
        check(replay.getMap("properties").has(path + "/pose"), "standalone preset's external tracks");
        check(replay.getString("axes_preview_bone").equals(path + "/Head"), "axes attachment");
        BaseType once = replay.copy();
        FormStableIds.ensureReplay(replay);
        check(once.equals(replay), "preset conversion is idempotent");
    }

    private static void roundTrip()
    {
        MapType data = film();
        new FilmStableIds().migrate(data);
        SaveVersion.stamp(data);
        MapType restored = (MapType) DataStorageUtils.readFromBytes(DataStorageUtils.writeToBytes(data));
        check(data.equals(restored), "binary save/load preserves every migrated reference");
        check(SaveVersion.read(restored) == SaveVersion.CURRENT, "format version persists");

        ValueStableList<ValueGroup> values = new ValueStableList<>("replays")
        {
            @Override
            protected ValueGroup create(String id)
            {
                return new ValueGroup(id);
            }
        };
        values.fromData(restored.getList("replays"));
        ValueGroup first = values.getList().get(0);
        String id = first.getId();
        check(id.equals(restored.getList("replays").getMap(0).getString("id")), "runtime preserves migrated id");
        Collections.reverse(values.getAllTyped());
        values.add(0, new ValueGroup("0"));
        check(values.get(id) == first, "insertion and reordering preserve target identity");
        BaseType saved = values.toData();
        values.fromData(saved);
        check(values.get(id) != null && values.toData().equals(saved), "edited list ids survive reload");
    }

    private static void reservedIds()
    {
        MapType form = form();
        String reserved = StableIds.fromLegacyIndex(0, Collections.emptySet());
        form.getList("parts").add(map("id", reserved, "form", new MapType()));
        Map<String, String> ids = FormStableIds.ensure(form);
        check(ids.get("1").equals(reserved), "later saved id is reserved before generating earlier ids");
        check(!ids.get("0").equals(reserved), "generated id avoids saved id");
    }

    private static MapType film()
    {
        MapType owner = form();
        owner.put("anchor", anchor(1));
        return map("replays", list(map("form", owner, "properties", map("anchor", channel("anchor", anchor(1)))), map("form", form())),
            "camera", list(map("selector", 1, "group", "0/0/Head")));
    }

    private static MapType form()
    {
        return map("id", "bbs:model", "parts", list(map("form", map("id", "bbs:model", "parts", list(map("form", map("id", "bbs:model")))))));
    }

    private static String path(MapType form)
    {
        MapType part = form.getList("parts").getMap(0);
        return part.getString("id") + "/" + part.getMap("form").getList("parts").getMap(0).getString("id");
    }

    private static MapType anchor(int actor)
    {
        return map("actor", actor, "attachment", "0/0/Head");
    }

    private static MapType channel(String type, MapType value)
    {
        return map("type", type, "keyframes", list(map("tick", 0, "value", value)));
    }

    private static MapType map(Object... pairs)
    {
        MapType map = new MapType();
        for (int i = 0; i < pairs.length; i += 2)
        {
            String key = (String) pairs[i];
            Object value = pairs[i + 1];
            if (value instanceof BaseType type) map.put(key, type);
            else if (value instanceof Integer number) map.putInt(key, number);
            else if (value instanceof Boolean bool) map.putBool(key, bool);
            else map.putString(key, (String) value);
        }
        return map;
    }

    private static ListType list(BaseType... entries)
    {
        ListType list = new ListType();
        for (BaseType entry : entries) list.add(entry);
        return list;
    }

    private static void run(String name, Runnable test)
    {
        try
        {
            test.run();
            System.out.println("PASS: " + name);
        }
        catch (AssertionError error)
        {
            failures++;
            System.err.println("FAIL: " + name + ": " + error.getMessage());
        }
    }

    private static void check(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
