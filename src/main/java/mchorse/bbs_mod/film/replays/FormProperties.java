package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.cubic.ik.IKControls;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.tracks.TrackBehaviour;
import mchorse.bbs_mod.film.replays.tracks.TrackBehaviours;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Every track of one replay (or one animation state): a channel of keyframes per address.
 *
 * <p>The address is a {@link TrackId}, not a string — what a track animates is decided once, when
 * the track is made or read, instead of being re-derived from its id by every piece of code that
 * touches it.</p>
 */
public class FormProperties extends ValueGroup
{
    /** Id of the form property holding the whole pose — the track a form's bone tracks hang under. */
    public static final String POSE_PROPERTY = "pose";

    /**
     * Saved-data key of the track list. Its presence is what tells the new format from the old one,
     * which was a map of channel id to channel.
     */
    private static final String TRACKS = "tracks";

    public final Map<TrackId, KeyframeChannel> tracks = new LinkedHashMap<>();

    public FormProperties(String id)
    {
        super(id);
    }

    public void shift(float tick)
    {
        for (KeyframeChannel<?> value : this.tracks.values())
        {
            for (Keyframe<?> keyframe : value.getKeyframes())
            {
                keyframe.setTick(keyframe.getTick() + tick);
            }
        }
    }

    /* Access */

    public KeyframeChannel get(TrackId track)
    {
        return track == null ? null : this.tracks.get(track);
    }

    public boolean has(TrackId track)
    {
        return track != null && this.tracks.containsKey(track);
    }

    /**
     * The track at this address, made if it isn't there yet.
     *
     * @param root  form the addresses are relative to; only a {@link TrackKind#PROPERTY} track needs
     *              it, since a plain property keys whatever its own value keys
     */
    public KeyframeChannel getOrCreate(Form root, TrackId track)
    {
        if (track == null)
        {
            return null;
        }

        KeyframeChannel channel = this.tracks.get(track);

        if (channel != null)
        {
            return channel;
        }

        if (track.is(TrackKind.PROPERTY))
        {
            BaseValue property = FormUtils.getProperty(root, track.toKey());

            return property == null ? null : this.create(property);
        }

        return this.register(track, TrackBehaviours.factory(track));
    }

    /** Every kind but {@link TrackKind#PROPERTY}, whose factory has to be looked up on the form. */
    public KeyframeChannel getOrCreate(TrackId track)
    {
        return this.getOrCreate(null, track);
    }

    public KeyframeChannel getOrCreate(Form root, String key)
    {
        return this.getOrCreate(root, TrackId.parse(key));
    }

    /** Make the track of a form property, keyed by the path the property sits at. */
    public KeyframeChannel create(BaseValue property)
    {
        if (property.isVisible() && property instanceof BaseKeyframeFactoryValue<?> keyframeFactoryValue)
        {
            TrackId track = TrackId.parse(FormUtils.getPropertyPath(property));

            if (track == null)
            {
                return null;
            }

            return this.register(track, keyframeFactoryValue.getFactory());
        }

        return null;
    }

    public KeyframeChannel register(TrackId track, IKeyframeFactory factory)
    {
        if (track == null || factory == null)
        {
            return null;
        }

        KeyframeChannel channel = this.tracks.get(track);

        if (channel == null)
        {
            channel = new KeyframeChannel(track.toKey(), factory);

            this.put(track, channel);
        }

        return channel;
    }

    /** Take a channel as this track, replacing whatever stood there. */
    public void put(TrackId track, KeyframeChannel channel)
    {
        if (track == null || channel == null)
        {
            return;
        }

        KeyframeChannel previous = this.tracks.put(track, channel);

        if (previous != null)
        {
            this.remove(previous);
        }

        this.add(channel);
    }

    public void remove(TrackId track)
    {
        KeyframeChannel channel = this.tracks.remove(track);

        if (channel != null)
        {
            this.remove(channel);
        }
    }

    /* Playback */

    public void applyProperties(Form form, float tick)
    {
        this.applyProperties(form, tick, 1F);
    }

    public void applyProperties(Form form, float tick, float blend)
    {
        this.apply(TrackContext.of(form), tick, blend);
    }

    /**
     * Lay every track over the form at the given tick.
     *
     * <p>Bone tracks go last, and behind a reset: a form whose pose is driven by bone tracks alone
     * gets its runtime pose dropped first, so the bones layer over the form's own pose rather than
     * over last frame's result. A form that also has a whole-pose track keeps it — that track writes
     * the runtime pose the bones add onto.</p>
     */
    public void apply(TrackContext context, float tick, float blend)
    {
        if (context.root() == null)
        {
            return;
        }

        float clampedBlend = MathUtils.clamp(blend, 0F, 1F);
        Map<TrackId, KeyframeChannel> boneTracks = new LinkedHashMap<>();

        for (Map.Entry<TrackId, KeyframeChannel> entry : this.tracks.entrySet())
        {
            if (entry.getKey().is(TrackKind.BONE))
            {
                boneTracks.put(entry.getKey(), entry.getValue());
            }
            else
            {
                apply(context, entry.getKey(), entry.getValue(), tick, clampedBlend);
            }
        }

        Set<String> processedForms = new HashSet<>();

        for (TrackId track : boneTracks.keySet())
        {
            String formPath = track.formPath();

            if (!processedForms.add(formPath))
            {
                continue;
            }

            if (!this.has(TrackId.property(formPath, POSE_PROPERTY)) && FormUtils.getForm(context.root(), formPath) instanceof ModelForm modelForm)
            {
                modelForm.pose.setRuntimeValue(null);
            }
        }

        for (Map.Entry<TrackId, KeyframeChannel> entry : boneTracks.entrySet())
        {
            apply(context, entry.getKey(), entry.getValue(), tick, clampedBlend);
        }
    }

    private static void apply(TrackContext context, TrackId track, KeyframeChannel channel, float tick, float blend)
    {
        TrackBehaviour behaviour = TrackBehaviours.of(track);

        if (behaviour != null)
        {
            behaviour.apply(context, track, channel, tick, blend);
        }
    }

    /**
     * A whole-form {@code ik_controls} channel from an older save: one track whose keyframe held
     * every chain's scalars in a map. Explodes into per-bone {@link TrackKind#BONE_IK} tracks —
     * same ticks, same interpolation, each bone taking its own entry (a keyframe without an entry
     * for the bone contributes the neutral scalars, exactly what the old union interpolation did).
     */
    private void explodeIKControls(TrackId track, KeyframeChannel<?> channel)
    {
        Set<String> tips = new LinkedHashSet<>();

        for (Object o : channel.getKeyframes())
        {
            if (((Keyframe<?>) o).getValue() instanceof IKControls controls)
            {
                tips.addAll(controls.controls.keySet());
            }
        }

        for (String tip : tips)
        {
            if (tip == null || tip.isEmpty())
            {
                continue;
            }

            TrackId id = TrackId.boneIK(track.formPath(), tip);
            KeyframeChannel<IKControl> exploded = new KeyframeChannel<>(id.toKey(), KeyframeFactories.BONE_IK);

            for (Object o : channel.getKeyframes())
            {
                Keyframe<?> keyframe = (Keyframe<?>) o;
                IKControl value = keyframe.getValue() instanceof IKControls controls ? controls.controls.get(tip) : null;
                Keyframe<IKControl> copy = new Keyframe<>(keyframe.getId(), KeyframeFactories.BONE_IK, keyframe.getTick(), value == null ? new IKControl() : value.copy());

                copy.getInterpolation().copy(keyframe.getInterpolation());
                copy.setDuration(keyframe.getDuration());
                exploded.add(copy);
            }

            exploded.sort();
            this.put(id, exploded);
        }
    }

    /** Let go of every track, so the form shows what it shows on its own again. */
    public void resetProperties(Form form)
    {
        if (form == null)
        {
            return;
        }

        for (TrackId track : this.tracks.keySet())
        {
            TrackBehaviour behaviour = TrackBehaviours.of(track);

            if (behaviour != null)
            {
                behaviour.reset(form, track);
            }
        }
    }

    public void cleanUp()
    {
        Iterator<Map.Entry<TrackId, KeyframeChannel>> it = this.tracks.entrySet().iterator();

        while (it.hasNext())
        {
            KeyframeChannel next = it.next().getValue();

            if (next.isEmpty())
            {
                it.remove();
                this.remove(next);
            }
        }
    }

    /* Serialisation */

    @Override
    public BaseType toData()
    {
        ListType list = new ListType();

        for (Map.Entry<TrackId, KeyframeChannel> entry : this.tracks.entrySet())
        {
            KeyframeChannel channel = entry.getValue();

            if (channel.isEmpty())
            {
                continue;
            }

            TrackId track = entry.getKey();
            MapType data = new MapType();

            data.putString("kind", track.kind().key);

            if (!track.formPath().isEmpty())
            {
                data.putString("form", track.formPath());
            }

            if (!track.subject().isEmpty())
            {
                data.putString("subject", track.subject());
            }

            if (!track.property().isEmpty())
            {
                data.putString("prop", track.property());
            }

            data.put("channel", channel.toData());
            list.add(data);
        }

        MapType map = new MapType();

        map.put(TRACKS, list);

        return map;
    }

    @Override
    public void fromData(BaseType data)
    {
        this.removeAll();
        this.tracks.clear();

        if (!data.isMap())
        {
            return;
        }

        MapType map = data.asMap();

        if (map.has(TRACKS) && map.get(TRACKS).isList())
        {
            this.readTracks(map.getList(TRACKS));
        }
        else
        {
            this.readLegacy(map);
        }
    }

    private void readTracks(ListType list)
    {
        for (int i = 0; i < list.size(); i++)
        {
            if (!list.get(i).isMap())
            {
                continue;
            }

            MapType data = list.get(i).asMap();
            TrackKind kind = TrackKind.byKey(data.getString("kind"));

            if (kind == null)
            {
                continue;
            }

            TrackId track = new TrackId(kind, data.getString("form"), data.getString("subject"), data.getString("prop"));
            KeyframeChannel channel = new KeyframeChannel(track.toKey(), null);

            channel.fromData(data.getMap("channel"));

            /* The factory is written next to the keyframes; a channel saved with one this build no
             * longer knows can't be read at all, and is dropped rather than kept half-alive. */
            if (channel.getFactory() == null)
            {
                continue;
            }

            if (kind == TrackKind.IK_CONTROLS)
            {
                this.explodeIKControls(track, channel);
            }
            else
            {
                this.put(track, channel);
            }
        }
    }

    /**
     * Read the pre-track format, where the map key was the channel id and the kind of track had to be
     * parsed back out of it. Kept for every film saved before the kind became part of the data.
     */
    private void readLegacy(MapType map)
    {
        for (String key : map.keys())
        {
            MapType mapType = map.getMap(key);

            if (mapType.isEmpty())
            {
                continue;
            }

            TrackId track = TrackId.parse(key);

            if (track == null)
            {
                continue;
            }

            KeyframeChannel channel = new KeyframeChannel(key, null);

            channel.fromData(mapType);

            /* Patch 1.1.1 changes to lighting property */
            if (key.endsWith("lighting") && channel.getFactory() == KeyframeFactories.BOOLEAN)
            {
                KeyframeChannel newChannel = new KeyframeChannel(key, KeyframeFactories.FLOAT);

                for (Object keyframe : channel.getKeyframes())
                {
                    Keyframe kf = (Keyframe) keyframe;
                    Boolean v = (Boolean) kf.getValue();

                    newChannel.insert(kf.getTick(), v ? 1F : 0F);
                }

                channel = newChannel;
            }

            /* Convert transform to pose_transform for bone tracks */
            if (channel.getFactory() == KeyframeFactories.TRANSFORM && track.is(TrackKind.BONE))
            {
                KeyframeChannel newChannel = new KeyframeChannel(key, KeyframeFactories.POSE_TRANSFORM);

                for (Object o : channel.getKeyframes())
                {
                    Keyframe kf = (Keyframe) o;
                    Object value = kf.getValue();
                    PoseTransform newValue = new PoseTransform();

                    if (value instanceof Transform)
                    {
                        newValue.copy((Transform) value);
                    }

                    Keyframe newKf = new Keyframe(kf.getId(), KeyframeFactories.POSE_TRANSFORM, kf.getTick(), newValue);

                    newKf.getInterpolation().copy(kf.getInterpolation());
                    newChannel.add(newKf);
                }

                newChannel.sort();

                channel = newChannel;
            }

            if (channel.getFactory() == null)
            {
                continue;
            }

            if (track.is(TrackKind.IK_CONTROLS))
            {
                this.explodeIKControls(track, channel);
            }
            else
            {
                this.put(track, channel);
            }
        }
    }
}
