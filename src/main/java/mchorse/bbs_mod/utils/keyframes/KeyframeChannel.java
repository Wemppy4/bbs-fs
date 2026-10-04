package mchorse.bbs_mod.utils.keyframes;

import org.apache.logging.log4j.LogManager;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Keyframe channel
 *
 * This class is responsible for storing individual keyframes and also
 * interpolating between them.
 */
public class KeyframeChannel <T> extends ValueList<Keyframe<T>>
{
    private static final Logger LOGGER = LogManager.getLogger();

    private IKeyframeFactory<T> factory;
    private final List<KeyframeLoop> loops = new ArrayList<>();
    private final List<KeyframeLoop> loopsView = Collections.unmodifiableList(this.loops);

    public List<KeyframeLoop> getLoops()
    {
        return this.loopsView;
    }

    public KeyframeLoop getLoop(String id)
    {
        for (KeyframeLoop loop : this.loops)
        {
            if (loop.id().equals(id)) return loop;
        }
        return null;
    }

    /** Mutations are enclosed in the caller's channel notification transaction. */
    public void putLoop(KeyframeLoop loop)
    {
        if (!loop.isValid()) return;
        this.loops.removeIf(existing -> existing.id().equals(loop.id()));
        this.loops.add(loop);
        this.loops.sort((a, b) -> Float.compare(a.start(), b.start()));
    }

    public void removeLoop(String id)
    {
        this.loops.removeIf(loop -> loop.id().equals(id));
    }

    /** Existing keys always win over a repetition, including an edit made after creation. */
    public float getLoopEnd(KeyframeLoop loop)
    {
        float end = loop.end();
        Keyframe<T> next = this.get(this.nextEnabledIndex(this.upperBound(loop.sourceEnd())));
        if (next != null) end = Math.min(end, next.getTick());
        for (KeyframeLoop other : this.loops)
        {
            if (other.start() > loop.start()) end = Math.min(end, other.start());
        }
        return end;
    }

    /** Editing a ghost edits its source instead of inserting an invisible key under the loop. */
    public float getSourceTick(float tick)
    {
        for (KeyframeLoop loop : this.loops)
        {
            if (tick > loop.sourceEnd() && tick < this.getLoopEnd(loop)) return loop.sourceTick(tick);
        }
        return tick;
    }

    /** Timeline edits keep originals inside their source pass and other keys outside blocks. */
    public float constrainKeyframeTick(Keyframe<?> key, float tick)
    {
        for (KeyframeLoop loop : this.loops)
        {
            if (loop.containsSource(key.getTick())) tick = Math.max(loop.start(), Math.min(loop.sourceEnd(), tick));
            else if (key.getTick() < loop.start()) tick = Math.min(tick, Math.nextDown(loop.start()));
            else tick = Math.max(tick, this.getLoopEnd(loop));
        }
        return tick;
    }

    public KeyframeChannel(String id, IKeyframeFactory<T> factory)
    {
        super(id);

        this.factory = factory;
    }

    public IKeyframeFactory<T> getFactory()
    {
        return this.factory;
    }

    /* Read only */

    public double getLength()
    {
        double length = this.list.isEmpty() ? 0 : this.list.get(this.list.size() - 1).getTick();
        for (KeyframeLoop loop : this.loops) length = Math.max(length, this.getLoopEnd(loop));
        return length;
    }

    public boolean isEmpty()
    {
        return this.list.isEmpty();
    }

    /* The backing list is final in ValueList, so one unmodifiable view serves forever —
     * getKeyframes() sits in every per-frame interpolation path and used to wrap anew each call. */
    private List<Keyframe<T>> keyframesView;

    public List<Keyframe<T>> getKeyframes()
    {
        if (this.keyframesView == null)
        {
            this.keyframesView = Collections.unmodifiableList(this.list);
        }

        return this.keyframesView;
    }

    /** Stored keys remain editable even when none of them affect playback. */
    public boolean hasEnabledKeyframes()
    {
        return this.nextEnabledIndex(0) < this.list.size();
    }

    public boolean hasDisabledKeyframes()
    {
        for (Keyframe<T> keyframe : this.list)
        {
            if (!keyframe.isEnabled()) return true;
        }
        return false;
    }

    public int nextEnabledIndex(int index)
    {
        while (index < this.list.size() && !this.list.get(index).isEnabled()) index++;
        return index;
    }

    public int previousEnabledIndex(int index)
    {
        while (index >= 0 && !this.list.get(index).isEnabled()) index--;
        return index;
    }

    public int indexOf(Keyframe<T> keyframe)
    {
        return this.list.indexOf(keyframe);
    }

    public boolean has(int index)
    {
        return index >= 0 && index < this.list.size();
    }

    public Keyframe<T> get(int index)
    {
        return this.has(index) ? this.list.get(index) : null;
    }

    public KeyframeSegment<T> find(float ticks)
    {
        KeyframeSegment<T> segment = this.findSegment(ticks);

        if (segment == null)
        {
            return null;
        }

        segment.setup(ticks);

        return segment;
    }

    public T interpolate(float ticks)
    {
        T orDefault = null;

        if (this.factory == KeyframeFactories.FLOAT) orDefault = (T) Float.valueOf(0F);
        else if (this.factory == KeyframeFactories.DOUBLE) orDefault = (T) Double.valueOf(0D);
        else if (this.factory == KeyframeFactories.INTEGER) orDefault = (T) Integer.valueOf(0);

        return this.interpolate(ticks, orDefault);
    }

    public T interpolate(float ticks, T orDefault)
    {
        KeyframeSegment<T> segment = this.findSegment(ticks);

        if (segment == null)
        {
            return orDefault;
        }

        segment.setup(ticks);

        return segment.createInterpolated();
    }

    /**
     * Find a keyframe segment at given ticks
     */
    public KeyframeSegment<T> findSegment(float ticks)
    {
        BBSProfiler.count(BBSProfiler.Section.KEYFRAME_FIND_SEGMENT);

        for (KeyframeLoop loop : this.loops)
        {
            if (ticks < loop.start()) break;

            float end = this.getLoopEnd(loop);
            int first = this.nextEnabledIndex(this.lowerBound(loop.start()));
            int after = this.upperBound(loop.sourceEnd());

            if (first >= after) continue;

            Keyframe<T> next = this.get(this.nextEnabledIndex(after));

            if (ticks > end && (next == null || ticks < next.getTick()))
            {
                float local = loop.sourceTick(end);
                float passOffset = end - local;
                KeyframeSegment<T> source = this.findSourceSegment(local, first, after, loop, passOffset, end);
                if (next == null)
                {
                    source.timeOffset = ticks - local;
                    source.setup(ticks);
                    return source;
                }
                Keyframe<T> endpoint = this.loopEndpoint(source, end);
                KeyframeSegment<T> result = new KeyframeSegment<>(endpoint, next, -1);
                result.preA = this.shiftLoopNeighbour(source.a.getTick() < local ? source.a : source.preA, passOffset);
                Keyframe<T> post = this.get(this.nextEnabledIndex(this.nextEnabledIndex(after) + 1));
                result.postB = post == null ? next : post;
                result.setup(ticks);
                return result;
            }

            if (ticks <= end && (next == null || ticks < next.getTick()))
            {
                float local = loop.sourceTick(ticks);
                KeyframeSegment<T> result = this.findSourceSegment(local, first, after, loop, ticks - local, end);
                result.timeOffset = ticks - local;
                result.setup(ticks);
                return result;
            }
        }

        KeyframeSegment<T> result = this.findRawSegment(ticks);
        if (result != null) this.fillLoopPredecessor(result);
        return result;
    }

    /** The first real key after a loop follows its virtual endpoint, not the source pass. */
    private void fillLoopPredecessor(KeyframeSegment<T> segment)
    {
        for (KeyframeLoop loop : this.loops)
        {
            if (loop.sourceEnd() >= segment.a.getTick()) break;

            int after = this.upperBound(loop.sourceEnd());
            if (this.get(this.nextEnabledIndex(after)) != segment.a) continue;
            int first = this.nextEnabledIndex(this.lowerBound(loop.start()));
            if (first >= after) continue;

            float end = this.getLoopEnd(loop);
            float local = loop.sourceTick(end);
            float passOffset = end - local;
            KeyframeSegment<T> source = this.findSourceSegment(local, first, after, loop, passOffset, end);
            segment.preA = end < segment.a.getTick() ? this.loopEndpoint(source, end)
                : this.shiftLoopNeighbour(source.a.getTick() < local ? source.a : source.preA, passOffset);
            return;
        }
    }

    private Keyframe<T> loopEndpoint(KeyframeSegment<T> source, float end)
    {
        Keyframe<T> endpoint = new Keyframe<>("", this.factory, end, source.createInterpolated());
        endpoint.copyOverExtra(source.a);
        endpoint.setParent(this);
        return endpoint;
    }

    private int lowerBound(float tick)
    {
        int low = 0, high = this.list.size();
        while (low < high)
        {
            int mid = (low + high) >>> 1;
            if (this.list.get(mid).getTick() < tick) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    private int upperBound(float tick)
    {
        int low = this.lowerBound(tick);
        while (low < this.list.size() && this.list.get(low).getTick() == tick) low++;
        return low;
    }

    private KeyframeSegment<T> findSourceSegment(float tick, int first, int after, KeyframeLoop loop, float passOffset, float end)
    {
        int last = this.previousEnabledIndex(after - 1);
        int right = Math.min(last, Math.max(first, this.nextEnabledIndex(this.upperBound(tick))));
        int left = tick >= this.list.get(last).getTick() ? last : Math.max(first, this.previousEnabledIndex(right - 1));
        KeyframeSegment<T> segment = new KeyframeSegment<>(this.list.get(left), this.list.get(right), left);
        /* Neighbours live on the repeated timeline, in the same local time as a/b.
         * Skip a coincident seam key: Auto needs a neighbour at a distinct tick. */
        if (left == first && passOffset > 0)
        {
            int previous = this.lowerBound(segment.a.getTick() + loop.period()) - 1;
            previous = this.previousEnabledIndex(Math.min(last, previous));
            segment.preA = previous < first ? segment.a : this.shiftLoopNeighbour(this.get(previous), -loop.period());
        }
        else if (left == first)
        {
            this.fillLoopPredecessor(segment);
        }

        if (right == last)
        {
            int following = Math.max(first, this.nextEnabledIndex(this.upperBound(segment.b.getTick() - loop.period())));
            Keyframe<T> next = this.get(this.nextEnabledIndex(after));
            if (next != null && next.getTick() <= segment.b.getTick() + passOffset)
            {
                next = this.get(this.nextEnabledIndex(this.upperBound(segment.b.getTick() + passOffset)));
            }
            Keyframe<T> repeated = following < after ? this.get(following) : null;
            float repeatedTick = repeated == null ? Float.POSITIVE_INFINITY : repeated.getTick() + loop.period() + passOffset;

            if (repeatedTick <= end && (next == null || repeatedTick < next.getTick()))
            {
                segment.postB = this.shiftLoopNeighbour(repeated, loop.period());
                Keyframe<T> repeatedStart = following > first ? this.get(this.previousEnabledIndex(following - 1)) : null;

                if (repeatedStart != null && repeatedStart.getTick() + loop.period() == segment.b.getTick())
                {
                    segment.nextStart = this.shiftLoopNeighbour(repeatedStart, loop.period());
                }
            }
            else
            {
                segment.postB = next == null ? segment.b : this.shiftLoopNeighbour(next, -passOffset);
            }
        }
        segment.setup(tick);
        return segment;
    }

    /** A transient neighbour keeps the source value live without moving or copying stored keys. */
    private Keyframe<T> shiftLoopNeighbour(Keyframe<T> key, float offset)
    {
        if (offset == 0) return key;

        Keyframe<T> shifted = new Keyframe<>("", this.factory, key.getTick() + offset, key.getValue());
        shifted.copyOverExtra(key);
        shifted.setParent(this);
        return shifted;
    }

    private KeyframeSegment<T> findRawSegment(float ticks)
    {
        int first = this.nextEnabledIndex(0);
        if (first >= this.list.size()) return null;

        int last = this.previousEnabledIndex(this.list.size() - 1);
        if (first == last || ticks < this.list.get(first).getTick())
        {
            return new KeyframeSegment<>(this.list.get(first), this.list.get(first), first);
        }
        if (ticks >= this.list.get(last).getTick())
        {
            return new KeyframeSegment<>(this.list.get(last), this.list.get(last), last);
        }

        int right = this.nextEnabledIndex(this.lowerBound(ticks));
        if (this.list.get(right).getTick() == Math.floor(ticks) && right < last)
        {
            right = this.nextEnabledIndex(right + 1);
        }
        int left = this.previousEnabledIndex(right - 1);
        if (left < first) left = right;
        KeyframeSegment<T> segment = new KeyframeSegment<>(this.list.get(left), this.list.get(right), left);
        segment.setup(ticks);
        return segment;
    }

    /* Write only */

    public void removeAll()
    {
        this.preNotify();
        this.list.clear();
        this.loops.clear();
        this.postNotify();
    }

    public void remove(int index)
    {
        if (index < 0 || index > this.list.size() - 1)
        {
            return;
        }

        this.preNotify();
        this.list.remove(index);
        this.loops.removeIf(loop -> this.lowerBound(loop.start()) >= this.upperBound(loop.sourceEnd()));
        this.sync();
        this.postNotify();
    }

    public void insertSpace(int where, int ticks)
    {
        if (this.loops.stream().anyMatch(loop -> where > loop.start() && where <= loop.end()))
        {
            /* Inserting time into a block extends it; its source tempo stays unchanged.
             * Before a block, both its keys and bounds move together. */
            this.preNotify();
            for (Keyframe<T> key : this.list)
            {
                boolean inSource = false;
                for (KeyframeLoop loop : this.loops)
                {
                    if (loop.containsSource(key.getTick()) && where > loop.start()) inSource = true;
                }
                if (!inSource && key.getTick() >= where) key.setTick(key.getTick() + ticks);
            }
            this.loops.replaceAll(loop -> where <= loop.start() ? loop.move(ticks)
                : where <= loop.end() ? loop.withEnd(loop.end() + ticks) : loop);
            this.sort();
            this.postNotify();
            return;
        }
        KeyframeSegment<T> segment = this.findSegment(where);

        if (segment == null || where > segment.b.getTick())
        {
            return;
        }

        if (where < segment.a.getTick())
        {
            this.moveX(ticks);
        }
        else
        {
            BaseValue.edit(this, (__) ->
            {
                T copy = this.factory.copy(segment.createInterpolated());
                List<Keyframe<T>> keyframes = this.getKeyframes();

                for (int i = keyframes.indexOf(segment.b); i < keyframes.size(); i++)
                {
                    Keyframe<T> kf = keyframes.get(i);

                    kf.setTick(kf.getTick() + ticks);
                }

                Keyframe<T> kfA = keyframes.get(this.insertRaw(where, copy));
                Keyframe<T> kfB = keyframes.get(this.insertRaw(where + ticks, this.factory.copy(copy)));

                kfA.getInterpolation().setInterp(Interpolations.CONST);
                kfB.getInterpolation().copy(segment.a.getInterpolation());
                this.loops.replaceAll(loop -> where <= loop.start() ? loop.move(ticks) : loop);
            });
        }
    }

    /**
     * {@link #insert}, and a keyframe born between two others takes the left one's interpolation,
     * style, duration and handles — the way a hand-placed keyframe does, so a value laid onto a
     * shaped curve keeps the curve's shape. Returns the keyframe at the tick.
     */
    public Keyframe<T> insertInheriting(float tick, T value)
    {
        KeyframeSegment<T> segment = this.find(tick);
        Keyframe<T> template = segment == null ? null : segment.a;
        Keyframe<T> keyframe = this.get(this.insert(tick, value));

        if (template != null && template != keyframe)
        {
            keyframe.copyOverExtra(template);
        }

        return keyframe;
    }

    /**
     * Insert a keyframe at given tick with given value
     *
     * This method is useful as it's not creating keyframes every time you
     * need to add some value, but rather inserts in correct order or
     * overwrites existing keyframe.
     *
     * Also, it returns index at which it was inserted.
     */
    public int insert(float tick, T value)
    {
        return this.insertRaw(this.getSourceTick(tick), value);
    }

    private int insertRaw(float tick, T value)
    {
        this.preNotify();

        Keyframe<T> prev;

        if (!this.list.isEmpty())
        {
            prev = this.list.get(0);

            if (tick < prev.getTick())
            {
                this.add(0, new Keyframe<>("", this.factory, tick, value));
                this.sort();

                this.postNotify();

                return 0;
            }
        }

        prev = null;
        int index = 0;

        for (Keyframe<T> frame : this.list)
        {
            if (frame.getTick() == tick)
            {
                frame.setValue(value);
                this.postNotify();

                return index;
            }

            if (prev != null && tick > prev.getTick() && tick < frame.getTick())
            {
                break;
            }

            index++;
            prev = frame;
        }

        this.add(index, new Keyframe<T>("", this.factory, tick, value));
        this.sort();
        this.postNotify();

        return index;
    }

    public void sort()
    {
        /* Fractional ticks: an (int) cast of the difference reads anything under 1 as "equal",
         * which can leave the channel unsorted after a Shift-drag — and findSegment binary-searches
         * over it. */
        this.list.sort((a, b) -> Float.compare(a.getTick(), b.getTick()));

        this.sync();
    }

    /** Sort after a move, keeping the moved keyframe at each occupied tick.
     * The caller records the whole edit through its before/after snapshots, as with sort(). */
    public void sort(List<Keyframe<T>> moved)
    {
        Map<Float, Keyframe<T>> replacements = new HashMap<>();

        for (Keyframe<T> keyframe : moved)
        {
            replacements.put(keyframe.getTick(), keyframe);
        }

        this.list.removeIf(keyframe -> replacements.containsKey(keyframe.getTick())
            && replacements.get(keyframe.getTick()) != keyframe);
        this.sort();
    }

    public void simplify()
    {
        if (this.list.size() <= 2)
        {
            return;
        }

        this.preNotify();

        for (int i = 1; i < this.list.size() - 1; i++)
        {
            Keyframe<T> prev = this.list.get(i - 1);
            Keyframe<T> current = this.list.get(i);
            Keyframe<T> next = this.list.get(i + 1);

            if (this.factory.compare(current.getValue(), prev.getValue()) && this.factory.compare(current.getValue(), next.getValue()))
            {
                this.list.remove(i);

                i -= 1;
            }
        }

        int size = this.list.size();

        if (this.factory.compare(this.list.get(size - 1).getValue(), this.list.get(size - 2).getValue()))
        {
            this.list.remove(size - 1);
        }

        this.sync();
        this.postNotify();
    }

    public void moveX(float offset)
    {
        this.preNotify();

        for (Keyframe<T> keyframe : this.list)
        {
            keyframe.setTick(keyframe.getTick() + offset);
        }

        this.loops.replaceAll(loop -> loop.move(offset));

        this.postNotify();
    }

    @Override
    protected Keyframe<T> create(String id)
    {
        return new Keyframe<>(id, this.factory);
    }

    @Override
    public BaseType toData()
    {
        MapType data = new MapType();
        String type = CollectionUtils.getKey(KeyframeFactories.FACTORIES, this.factory);

        if (type == null)
        {
            /* A factory outside the registry has no name to write, so the channel goes out with a
             * null type and cannot be read back — the lookup on load finds nothing. There is no
             * value to substitute here, but it must not happen quietly. */
            LOGGER.error("Keyframe channel \"" + this.getId() + "\" holds a factory that isn't registered (" + this.factory + "); it is being saved with no value type and won't read back!");
        }

        data.put("keyframes", super.toData());
        data.putString("type", type);

        if (!this.loops.isEmpty())
        {
            ListType loops = new ListType();
            for (KeyframeLoop loop : this.loops) loops.add(loop.toData());
            data.put("loops", loops);
        }

        return data;
    }

    @Override
    public void fromData(BaseType data)
    {
        if (!data.isMap())
        {
            return;
        }

        MapType map = data.asMap();
        String type = map.getString("type");
        IKeyframeFactory<T> factory = KeyframeFactories.FACTORIES.get(type);

        if (factory == null)
        {
            /* An unknown value type used to be assigned regardless, which left the channel holding
             * a null factory: reading the first keyframe then threw, and wherever that throw was
             * swallowed the whole channel disappeared without a trace. Keep the factory the channel
             * was constructed with, say so out loud, and leave the keyframes unread — they are
             * written in a shape this build has no way to interpret. */
            LOGGER.error("Keyframe channel \"" + this.getId() + "\" has unknown value type \"" + type + "\"; its keyframes are left out.");

            return;
        }

        this.factory = factory;

        if (factory == null)
        {
            /* A channel saved with a factory this build no longer has (bone_anchor, physics_data,
             * spline_points... — types that outlived their feature). Reading its keyframes would ask
             * the missing factory to parse their values, which threw and took the whole film's load
             * down with it. Left empty instead, for the owner to drop. */
            this.list.clear();

            return;
        }

        super.fromData(map.getList("keyframes"));

        this.sort();
        this.loops.clear();
        for (BaseType entry : map.getList("loops"))
        {
            if (entry.isMap()) this.putLoop(KeyframeLoop.fromData(entry.asMap()));
        }
    }

    public void copyKeyframes(KeyframeChannel<T> channel)
    {
        this.list.clear();

        for (Keyframe<T> keyframe : channel.getKeyframes())
        {
            Keyframe<T> value = new Keyframe<>(keyframe.getId(), keyframe.getFactory());

            value.copy(keyframe);
            this.add(value);
        }

        this.sort();
        this.loops.clear();
        this.loops.addAll(channel.loops);
    }

    @Override
    public void reset()
    {
        if (!this.list.isEmpty() || !this.loops.isEmpty()) this.removeAll();
    }

    @Override
    public boolean equals(Object object)
    {
        return object instanceof KeyframeChannel<?> && super.equals(object) && this.loops.equals(((KeyframeChannel<?>) object).loops);
    }

    /**
     * Drop keys that repeat the value of the key before them.
     *
     * Only sound where interpolation is stepped, as it is for item stacks: there a key holding
     * what the previous one already holds changes nothing, so dropping it plays back the same
     * frame for frame. On a numeric channel the same key is a corner of the curve and carries
     * real shape, so this must not be pointed at one.
     */
    public void dropRepeats()
    {
        for (int i = this.list.size() - 1; i > 0; i--)
        {
            if (this.factory.compare(this.list.get(i).getValue(), this.list.get(i - 1).getValue()))
            {
                this.list.remove(i);
            }
        }
    }

    public void copyOver(KeyframeChannel channel, int tick)
    {
        if (this.factory != channel.factory || channel.isEmpty())
        {
            return;
        }

        this.preNotify();

        double start = tick + ((Keyframe) channel.getKeyframes().get(0)).getTick();

        this.list.removeIf((next) -> next.getTick() >= start);
        this.loops.removeIf(loop -> loop.sourceEnd() >= start);
        this.loops.replaceAll(loop -> loop.withEnd(Math.min(loop.end(), (float) start)));

        for (Object o : channel.getKeyframes())
        {
            Keyframe keyframe = (Keyframe) o;
            Keyframe value = new Keyframe<>(keyframe.getId(), keyframe.getFactory());

            value.fromData(keyframe.toData());
            value.setTick(tick + value.getTick());
            this.list.add(value);
        }

        this.sync();
        for (Object entry : channel.getLoops())
        {
            this.putLoop(((KeyframeLoop) entry).move(tick));
        }
        this.postNotify();
    }
}
