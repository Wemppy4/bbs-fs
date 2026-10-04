package mchorse.bbs_mod.utils.keyframes;

import mchorse.bbs_mod.data.types.MapType;

/** A finite repetition of the channel's keys in [start, sourceEnd].
 * Channels belonging to the same block share an id, not mutable object identity. */
public final class KeyframeLoop
{
    private final String id;
    private final float start, sourceEnd, end;
    public KeyframeLoop(String id, float start, float sourceEnd, float end) {
        this.id = id; this.start = start; this.sourceEnd = sourceEnd; this.end = end;
    }
    public String id() { return id; }
    public float start() { return start; }
    public float sourceEnd() { return sourceEnd; }
    public float end() { return end; }
    @Override public boolean equals(Object value) {
        if (!(value instanceof KeyframeLoop)) return false;
        KeyframeLoop other = (KeyframeLoop) value;
        return java.util.Objects.equals(id,other.id) && Float.compare(start,other.start)==0
            && Float.compare(sourceEnd,other.sourceEnd)==0 && Float.compare(end,other.end)==0;
    }
    @Override public int hashCode() { return java.util.Objects.hash(id,start,sourceEnd,end); }
    public boolean isValid()
    {
        return id != null && !id.isEmpty() && Float.isFinite(start)
            && Float.isFinite(sourceEnd) && Float.isFinite(end)
            && sourceEnd > start && end >= sourceEnd && Float.isFinite(period()) && Float.isFinite(end - start);
    }

    public float period()
    {
        return sourceEnd - start;
    }

    public float passes()
    {
        return (end - start) / period();
    }

    public boolean containsSource(float tick)
    {
        return tick >= start && tick <= sourceEnd;
    }

    public float sourceTick(float tick)
    {
        if (tick <= sourceEnd) return Math.max(start, tick);

        double offset = ((double) Math.min(tick, end) - start) % period();

        /* An exact boundary belongs to the completed pass, including the block's end. */
        return offset == 0 ? sourceEnd : (float) (start + offset);
    }

    public KeyframeLoop withEnd(float end)
    {
        return new KeyframeLoop(id, start, sourceEnd, Math.max(sourceEnd, end));
    }

    public KeyframeLoop move(float delta)
    {
        return new KeyframeLoop(id, start + delta, sourceEnd + delta, end + delta);
    }

    public MapType toData()
    {
        MapType data = new MapType();
        data.putString("id", id);
        data.putFloat("start", start);
        data.putFloat("source_end", sourceEnd);
        data.putFloat("end", end);
        return data;
    }

    public static KeyframeLoop fromData(MapType data)
    {
        return new KeyframeLoop(data.getString("id"), data.getFloat("start"), data.getFloat("source_end"), data.getFloat("end"));
    }
}
