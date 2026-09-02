package mchorse.bbs_mod.data.migration;

import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.core.StableIds;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Format 1 &rarr; 2: everything in a film that used to address a replay or a body part by its list
 * position addresses it by a {@link StableIds stable id} instead. The legacy index is resolved by
 * the old rules one last time, right here, and never again:
 *
 * <ul>
 * <li>every replay and body part is stamped with its id;</li>
 * <li>replay property track keys ({@code "0/2/pose"}) become id paths;</li>
 * <li>anchors — the {@code "actor"} inside anchor keyframe values and the forms' static anchor
 * fields — carry the target replay's id, and their {@code "attachment"} (a path into the target's
 * matrix tree, which could start with body part indices) is rewritten against the target's form;</li>
 * <li>camera clip selectors (look/orbit/tracker) do the same, including the tracker's
 * {@code "group"} attachment path.</li>
 * </ul>
 *
 * <p>An index that doesn't resolve to a replay (a target deleted long ago) becomes an explicit
 * "no target": that is what it already meant, the file just stored a dangling number.
 *
 * <p>Two passes: ids are stamped on every replay and every body part of every form first, so that
 * by the time anchors are rewritten the id mapping of any <em>target</em> replay's form exists —
 * an anchor's attachment path is relative to the target, not to the anchor's owner.
 */
public class FilmStableIds implements IDataMigration
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The keyframe channel type whose values are anchors — the marker for actor rewriting. */
    private static final String ANCHOR_CHANNEL_TYPE = "anchor";

    @Override
    public int getVersion()
    {
        return 1;
    }

    @Override
    public void migrate(MapType data)
    {
        ListType replays = data.has("replays") ? data.getList("replays") : new ListType();

        /* Pass 1: identity. */
        List<String> replayIds = stampReplayIds(replays);
        List<Map<String, String>> formMappings = new ArrayList<>();

        for (BaseType replayType : replays)
        {
            MapType replay = replayType.isMap() ? replayType.asMap() : null;

            formMappings.add(FormStableIds.ensure(replay != null && replay.has("form") ? replay.getMap("form") : null));
        }

        /* Pass 2: references. */
        for (int i = 0; i < replays.size(); i++)
        {
            if (!replays.get(i).isMap())
            {
                continue;
            }

            MapType replay = replays.get(i).asMap();

            if (replay.has("properties"))
            {
                FormStableIds.rewriteFormPaths(replay.getMap("properties"), formMappings.get(i));
                convertAnchorChannels(replay.getMap("properties"), replayIds, formMappings);
            }

            if (replay.has("form"))
            {
                convertFormAnchors(replay.getMap("form"), replayIds, formMappings);
            }
        }

        if (data.has("camera"))
        {
            convertCameraSelectors(data.getList("camera"), replayIds, formMappings);
        }
    }

    /** Give every replay its id; the list index is the id's meaning up until this very point. */
    private static List<String> stampReplayIds(ListType replays)
    {
        List<String> taken = new ArrayList<>();

        for (BaseType type : replays)
        {
            if (type.isMap() && StableIds.isStableId(type.asMap().getString(StableIds.KEY)))
            {
                taken.add(type.asMap().getString(StableIds.KEY));
            }
        }

        List<String> ids = new ArrayList<>();

        for (BaseType type : replays)
        {
            if (!type.isMap())
            {
                ids.add(null);

                continue;
            }

            MapType replay = type.asMap();
            String id = replay.getString(StableIds.KEY);

            if (!StableIds.isStableId(id))
            {
                do
                {
                    id = StableIds.generate();
                }
                while (taken.contains(id));

                taken.add(id);
                replay.putString(StableIds.KEY, id);
            }

            ids.add(id);
        }

        return ids;
    }

    /** Static anchor fields and state anchor channels, of the form and every nested body part form. */
    private static void convertFormAnchors(MapType form, List<String> replayIds, List<Map<String, String>> formMappings)
    {
        if (form.has("anchor"))
        {
            convertAnchor(form.getMap("anchor"), replayIds, formMappings);
        }

        for (BaseType stateType : form.has("states") ? form.getList("states") : new ListType())
        {
            if (stateType.isMap() && stateType.asMap().has("properties"))
            {
                convertAnchorChannels(stateType.asMap().getMap("properties"), replayIds, formMappings);
            }
        }

        for (BaseType partType : form.has("parts") ? form.getList("parts") : new ListType())
        {
            if (partType.isMap() && partType.asMap().has("form"))
            {
                convertFormAnchors(partType.asMap().getMap("form"), replayIds, formMappings);
            }
        }
    }

    /** Anchor keyframe channels inside a track map, recognized by their channel type. */
    private static void convertAnchorChannels(MapType properties, List<String> replayIds, List<Map<String, String>> formMappings)
    {
        for (MapType channel : channelsOf(properties))
        {
            if (!channel.getString("type").equals(ANCHOR_CHANNEL_TYPE))
            {
                continue;
            }

            for (BaseType keyframe : channel.getList("keyframes"))
            {
                if (keyframe.isMap() && keyframe.asMap().has("value") && keyframe.asMap().get("value").isMap())
                {
                    convertAnchor(keyframe.asMap().getMap("value"), replayIds, formMappings);
                }
            }
        }
    }

    /** Every keyframe channel inside a properties map, whichever of the two shapes it is in. */
    private static List<MapType> channelsOf(MapType properties)
    {
        List<MapType> channels = new ArrayList<>();

        if (FormStableIds.isTrackList(properties))
        {
            for (BaseType entryType : properties.getList(FormStableIds.TRACKS))
            {
                if (entryType.isMap() && entryType.asMap().has("channel") && entryType.asMap().get("channel").isMap())
                {
                    channels.add(entryType.asMap().getMap("channel"));
                }
            }
        }
        else
        {
            for (String key : properties.keys())
            {
                BaseType channelType = properties.get(key);

                if (channelType.isMap())
                {
                    channels.add(channelType.asMap());
                }
            }
        }

        return channels;
    }

    /**
     * One anchor map: {@code "actor"} legacy int index &rarr; the target replay's id, and
     * {@code "attachment"} rewritten against the <em>target's</em> form (its leading segments are
     * body part indices of that form's tree). A string actor means the map is already converted; a
     * dangling or negative index means "no target" and becomes the explicit empty id it always
     * meant.
     */
    private static void convertAnchor(MapType anchor, List<String> replayIds, List<Map<String, String>> formMappings)
    {
        BaseType actor = anchor.get("actor");

        if (actor == null || BaseType.isString(actor))
        {
            return;
        }

        int index = anchor.getInt("actor", -1);
        String id = index >= 0 && index < replayIds.size() ? replayIds.get(index) : null;

        if (index >= 0 && id == null)
        {
            LOGGER.warn("Anchor points at replay [" + index + "] which does not exist; unanchoring");
        }

        anchor.putString("actor", id == null ? "" : id);

        if (id != null && anchor.has("attachment"))
        {
            anchor.putString("attachment", rewriteAttachment(anchor.getString("attachment"), formMappings.get(index)));
        }
    }

    /** Camera clips carrying a {@code selector} — look, orbit and tracker target a replay by index. */
    private static void convertCameraSelectors(ListType camera, List<String> replayIds, List<Map<String, String>> formMappings)
    {
        for (BaseType clipType : camera)
        {
            if (!clipType.isMap())
            {
                continue;
            }

            MapType clip = clipType.asMap();
            BaseType selector = clip.get("selector");

            if (selector == null || BaseType.isString(selector))
            {
                continue;
            }

            int index = clip.getInt("selector", -1);
            String id = index >= 0 && index < replayIds.size() ? replayIds.get(index) : null;

            if (index >= 0 && id == null)
            {
                LOGGER.warn("Camera clip selector points at replay [" + index + "] which does not exist; clearing");
            }

            clip.putString("selector", id == null ? "" : id);

            /* The tracker's attachment path into the tracked replay's matrix tree. */
            if (id != null && clip.has("group"))
            {
                clip.putString("group", rewriteAttachment(clip.getString("group"), formMappings.get(index)));
            }
        }
    }

    /**
     * An attachment path is {@code [<part indices>/]<bone>} or just a part path (the whole part);
     * the leading index segments are rewritten exactly like track keys are.
     */
    private static String rewriteAttachment(String attachment, Map<String, String> mapping)
    {
        return attachment.isEmpty() ? attachment : FormStableIds.rewriteTrackKey(attachment, mapping);
    }
}
